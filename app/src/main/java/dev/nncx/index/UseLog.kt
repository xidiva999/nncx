package dev.nncx.index

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/** What a row counts. Apps today; contacts, ssh hosts and links join the same table. */
enum class Kind(val key: String) {
    APP("app"),
    /** Rebuilt from the call log on every pass. */
    CONTACT("contact"),
    /**
     * What you tapped inside NNCx. Kept apart because harvesting replaces a
     * day's rows wholesale, and the call log has no idea these happened —
     * it is also the only way WhatsApp ever counts.
     */
    CONTACT_TAP("contact_tap"),
    HOST("host"), LINK("link")
}

enum class Trend { RISING, FLAT, FALLING }

/**
 * 14 days of launches, plus the two halves so trend is free.
 *
 * A count is legible; a smoothed score is not. x-ray prints this verbatim,
 * which is the whole reason the ranking function is a sum and not an EWMA.
 */
data class Rank(
    val total14: Int,
    val recent7: Int,
    val prior7: Int,
    /** Launches between 06:00 and 09:59. Morning is a lens, not a separate store. */
    val morning: Int = 0,
) {
    val trend: Trend
        get() {
            val projected = recent7 * 2
            return when {
                prior7 == 0 && recent7 == 0 -> Trend.FLAT
                prior7 == 0 -> Trend.RISING
                projected > prior7 * 1.3 -> Trend.RISING
                projected < prior7 * 0.7 -> Trend.FALLING
                else -> Trend.FLAT
            }
        }

    fun perDay(spanDays: Long): Double =
        if (spanDays <= 0) 0.0 else total14.toDouble() / spanDays

    /** Share of launches that happen in the morning, 0..1. */
    val morningShare: Double
        get() = if (total14 == 0) 0.0 else morning.toDouble() / total14

    operator fun plus(other: Rank) = Rank(
        total14 = total14 + other.total14,
        recent7 = recent7 + other.recent7,
        prior7 = prior7 + other.prior7,
        morning = morning + other.morning,
    )

    companion object {
        val NONE = Rank(0, 0, 0, 0)
    }
}

/**
 * One table, one ranking function.
 *
 *   target_id | kind | day | count
 *
 * `day` is a local epoch day, so "today" means what it means to you, not to UTC.
 * Raw SQLite rather than Room: it is four columns and three queries, and this
 * way the build has no annotation processor in it.
 */
class UseLog private constructor(context: Context) :
    SQLiteOpenHelper(context.applicationContext, DB, null, VERSION) {

    companion object {
        private const val DB = "nncx-use.db"
        private const val VERSION = 2
        const val WINDOW_DAYS = 14
        const val HALF_DAYS = 7
        private const val RETAIN_DAYS = 45

        @Volatile private var instance: UseLog? = null

        fun get(context: Context): UseLog =
            instance ?: synchronized(this) {
                instance ?: UseLog(context).also { instance = it }
            }
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE use_log (
                kind   TEXT    NOT NULL,
                target TEXT    NOT NULL,
                day    INTEGER NOT NULL,
                hour   INTEGER NOT NULL,
                count  INTEGER NOT NULL,
                PRIMARY KEY (kind, target, day, hour)
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX idx_use_log_day ON use_log(kind, day)")
        db.execSQL("CREATE TABLE meta (key TEXT PRIMARY KEY, value TEXT NOT NULL)")

        // Hysteresis needs to remember who was already in. Without it an app
        // sitting exactly on a threshold pops in and out on every recompute,
        // which reads as a bug rather than as a habit changing.
        db.execSQL(
            """
            CREATE TABLE folder_member (
                folder TEXT NOT NULL,
                target TEXT NOT NULL,
                PRIMARY KEY (folder, target)
            )
            """.trimIndent()
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, old: Int, new: Int) {
        db.execSQL("DROP TABLE IF EXISTS use_log")
        db.execSQL("DROP TABLE IF EXISTS meta")
        db.execSQL("DROP TABLE IF EXISTS folder_member")
        onCreate(db)
    }

    // ---- writes -----------------------------------------------------------

    /** Replaces the day's rows outright — today gets re-harvested while it is still moving. */
    fun putDay(kind: Kind, day: Long, counts: Map<String, Map<Int, Int>>) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.delete("use_log", "kind = ? AND day = ?", arrayOf(kind.key, day.toString()))
            for ((target, byHour) in counts) {
                for ((hour, count) in byHour) {
                    if (count <= 0) continue
                    db.insertWithOnConflict(
                        "use_log", null,
                        ContentValues().apply {
                            put("kind", kind.key)
                            put("target", target)
                            put("day", day)
                            put("hour", hour)
                            put("count", count)
                        },
                        SQLiteDatabase.CONFLICT_REPLACE
                    )
                }
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    /** For targets NNCx observes itself — contacts tapped here, hosts opened here. */
    fun bump(kind: Kind, target: String, day: Long, hour: Int, by: Int = 1) {
        writableDatabase.execSQL(
            """
            INSERT INTO use_log(kind, target, day, hour, count) VALUES (?, ?, ?, ?, ?)
            ON CONFLICT(kind, target, day, hour) DO UPDATE SET count = count + ?
            """.trimIndent(),
            arrayOf<Any>(kind.key, target, day, hour, by, by)
        )
    }

    fun prune(today: Long) {
        writableDatabase.delete(
            "use_log", "day < ?", arrayOf((today - RETAIN_DAYS).toString())
        )
    }

    // ---- reads ------------------------------------------------------------

    fun ranks(kind: Kind, today: Long): Map<String, Rank> {
        val windowFrom = today - (WINDOW_DAYS - 1)   // inclusive
        val recentFrom = today - (HALF_DAYS - 1)     // inclusive
        val out = HashMap<String, Rank>()

        readableDatabase.rawQuery(
            """
            SELECT target,
                   SUM(count)                                              AS total14,
                   SUM(CASE WHEN day >= ? THEN count ELSE 0 END)           AS recent7,
                   SUM(CASE WHEN day <  ? THEN count ELSE 0 END)           AS prior7,
                   SUM(CASE WHEN hour BETWEEN 6 AND 9 THEN count ELSE 0 END) AS morning
            FROM use_log
            WHERE kind = ? AND day >= ?
            GROUP BY target
            """.trimIndent(),
            arrayOf(
                recentFrom.toString(),
                recentFrom.toString(),
                kind.key,
                windowFrom.toString(),
            )
        ).use { c ->
            while (c.moveToNext()) {
                out[c.getString(0)] = Rank(
                    total14 = c.getInt(1),
                    recent7 = c.getInt(2),
                    prior7 = c.getInt(3),
                    morning = c.getInt(4),
                )
            }
        }
        return out
    }

    // ---- harvest bookkeeping ---------------------------------------------

    fun completedThrough(): Long =
        readableDatabase.rawQuery(
            "SELECT value FROM meta WHERE key = 'completed_through'", null
        ).use { if (it.moveToFirst()) it.getString(0).toLongOrNull() ?: -1L else -1L }

    fun setCompletedThrough(day: Long) {
        writableDatabase.insertWithOnConflict(
            "meta", null,
            ContentValues().apply {
                put("key", "completed_through")
                put("value", day.toString())
            },
            SQLiteDatabase.CONFLICT_REPLACE
        )
    }

    /**
     * Oldest day held FOR ONE KIND, or null when empty.
     *
     * Must be per-kind. Unfiltered, the call log — which retains months and
     * backfills the whole window on first run — silently vouches for the app
     * log, and gates like "Dead" start answering on evidence they do not have.
     */
    fun oldestDay(kind: Kind): Long? =
        readableDatabase.rawQuery(
            "SELECT MIN(day) FROM use_log WHERE kind = ?", arrayOf(kind.key)
        ).use { if (it.moveToFirst() && !it.isNull(0)) it.getLong(0) else null }

    /**
     * What share of THIS phone's launches happen 06:00–09:59.
     *
     * An absolute "morning" threshold encodes an assumption about when the
     * owner gets up. Measuring against the device's own distribution means the
     * rule works for an early riser and for someone whose day starts at ten.
     */
    fun morningBaseline(kind: Kind, today: Long): Double {
        val windowFrom = today - (WINDOW_DAYS - 1)
        return readableDatabase.rawQuery(
            """
            SELECT SUM(CASE WHEN hour BETWEEN 6 AND 9 THEN count ELSE 0 END),
                   SUM(count)
            FROM use_log WHERE kind = ? AND day >= ?
            """.trimIndent(),
            arrayOf(kind.key, windowFrom.toString())
        ).use { c ->
            if (!c.moveToFirst()) return 0.0
            val morning = c.getDouble(0)
            val total = c.getDouble(1)
            if (total <= 0.0) 0.0 else morning / total
        }
    }

    fun members(folder: String): Set<String> =
        readableDatabase.rawQuery(
            "SELECT target FROM folder_member WHERE folder = ?", arrayOf(folder)
        ).use { c ->
            val out = HashSet<String>()
            while (c.moveToNext()) out.add(c.getString(0))
            out
        }

    fun setMembers(folder: String, targets: Set<String>) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.delete("folder_member", "folder = ?", arrayOf(folder))
            for (t in targets) {
                db.insertWithOnConflict(
                    "folder_member", null,
                    ContentValues().apply {
                        put("folder", folder)
                        put("target", t)
                    },
                    SQLiteDatabase.CONFLICT_REPLACE
                )
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun totalRows(): Int =
        readableDatabase.rawQuery("SELECT COUNT(*) FROM use_log", null)
            .use { if (it.moveToFirst()) it.getInt(0) else 0 }
}
