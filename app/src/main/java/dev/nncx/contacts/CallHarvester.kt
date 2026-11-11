package dev.nncx.contacts

import android.content.Context
import android.provider.CallLog
import dev.nncx.index.Kind
import dev.nncx.index.UseLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Where contact recurrence comes from.
 *
 * NOT Contacts.TIMES_CONTACTED — that column and CONTENT_STREQUENT_URI with it
 * have not been updated since Android 10. They look like exactly the API you
 * want and they return stale or zero.
 *
 * The call log is the only reliable signal, and unlike UsageStats it keeps
 * months, so the whole window can be rebuilt on every pass with no bookkeeping.
 *
 * It cannot see WhatsApp — no API, and notification access was ruled out on
 * purpose. NNCx makes up the difference by counting what you tap inside it.
 */
object CallHarvester {

    suspend fun harvest(context: Context, today: Long) = withContext(Dispatchers.IO) {
        val log = UseLog.get(context)
        val zone = ZoneId.systemDefault()
        val from = LocalDate.ofEpochDay(today - (UseLog.WINDOW_DAYS - 1))
            .atStartOfDay(zone).toInstant().toEpochMilli()

        // day -> key -> hour -> count
        val buckets = HashMap<Long, HashMap<String, HashMap<Int, Int>>>()

        runCatching {
            context.contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                arrayOf(CallLog.Calls.NUMBER, CallLog.Calls.DATE),
                "${CallLog.Calls.DATE} >= ?",
                arrayOf(from.toString()),
                null,
            )?.use { c ->
                while (c.moveToNext()) {
                    val key = ContactIndex.bucket(c.getString(0)) ?: continue
                    val at = Instant.ofEpochMilli(c.getLong(1)).atZone(zone)
                    val day = at.toLocalDate().toEpochDay()
                    buckets
                        .getOrPut(day) { HashMap() }
                        .getOrPut(key) { HashMap() }
                        .merge(at.hour, 1, Int::plus)
                }
            }
        }

        for ((day, byTarget) in buckets) {
            log.putDay(Kind.CONTACT, day, byTarget)
        }
    }

    /** NNCx's own ledger: what you tapped here, which the call log cannot see. */
    fun record(context: Context, key: String) {
        val now = java.time.LocalDateTime.now()
        UseLog.get(context).bump(
            kind = Kind.CONTACT_TAP,
            target = key,
            day = now.toLocalDate().toEpochDay(),
            hour = now.hour,
        )
    }
}
