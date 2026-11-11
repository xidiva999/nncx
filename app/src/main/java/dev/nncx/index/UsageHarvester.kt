package dev.nncx.index

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * UsageStatsManager will NOT hand back 14 days on demand — raw events are kept
 * for only a few days, and quietly fewer on some OEMs. Ask for a fortnight and
 * you get a partial answer that looks correct.
 *
 * So NNCx keeps its own buckets: read each day once, write a row, own the data.
 * The window then survives reboots and weeks of not opening the launcher.
 */
object UsageHarvester {

    /** How far back the system can still be trusted to remember. */
    private const val SYSTEM_RETENTION_DAYS = 7L

    data class Result(val daysWritten: Int, val targets: Int, val today: Long)

    /**
     * Last time each package was in the foreground, as a wall clock.
     *
     * The daily buckets are deliberately coarse — they answer "how often",
     * and an hour is plenty for that. "Within the last 24 hours" is a
     * different question and needs a real timestamp, which UsageStats gives
     * directly. It also covers apps opened from a notification, which a
     * launcher-side ledger never sees.
     */
    suspend fun lastUsed(context: Context): Map<String, Long> = withContext(Dispatchers.IO) {
        if (!UsageAccess.granted(context)) return@withContext emptyMap()
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val now = System.currentTimeMillis()

        runCatching {
            usm.queryUsageStats(
                UsageStatsManager.INTERVAL_BEST,
                now - 3L * 24 * 60 * 60 * 1000,
                now,
            ).orEmpty()
                .groupBy { it.packageName }
                .mapValues { (_, list) -> list.maxOf { it.lastTimeUsed } }
        }.getOrDefault(emptyMap())
    }

    suspend fun harvest(context: Context): Result = withContext(Dispatchers.IO) {
        val log = UseLog.get(context)
        val today = LocalDate.now().toEpochDay()

        if (!UsageAccess.granted(context)) return@withContext Result(0, 0, today)

        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val completed = log.completedThrough()
        val from = maxOf(completed + 1, today - SYSTEM_RETENTION_DAYS)

        var days = 0
        var targets = 0
        val ignore = homePackages(context) + context.packageName

        for (day in from..today) {
            val counts = launchesOn(usm, ignore, day)
            log.putDay(Kind.APP, day, counts)
            days++
            targets = maxOf(targets, counts.size)
        }

        // Today is still moving, so only yesterday and earlier are "complete".
        if (today - 1 >= from) log.setCompletedThrough(today - 1)
        log.prune(today)

        Result(days, targets, today)
    }

    /**
     * A "launch" is a package becoming foreground when a *different* package
     * was foreground. Counting every ACTIVITY_RESUMED would count every screen
     * inside an app, which measures navigation, not launching.
     */
    private fun launchesOn(
        usm: UsageStatsManager,
        ignore: Set<String>,
        day: Long,
    ): Map<String, Map<Int, Int>> {
        val zone = ZoneId.systemDefault()
        val start = LocalDate.ofEpochDay(day).atStartOfDay(zone).toInstant().toEpochMilli()
        val dayEnd = LocalDate.ofEpochDay(day + 1).atStartOfDay(zone).toInstant().toEpochMilli()
        val end = minOf(dayEnd, System.currentTimeMillis())
        if (end <= start) return emptyMap()

        val counts = HashMap<String, HashMap<Int, Int>>()
        var lastForeground: String? = null

        val events = usm.queryEvents(start, end)
        val event = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (event.eventType != UsageEvents.Event.ACTIVITY_RESUMED) continue
            val pkg = event.packageName ?: continue
            if (pkg == lastForeground) continue
            lastForeground = pkg
            if (pkg in ignore) continue

            // Hour of day is kept so "Morning" can be a lens over the same
            // rows instead of a second store.
            val hour = Instant.ofEpochMilli(event.timeStamp).atZone(zone).hour
            val byHour = counts.getOrPut(pkg) { HashMap() }
            byHour[hour] = (byHour[hour] ?: 0) + 1
        }
        return counts
    }

    /**
     * Every home press registers as the launcher being resumed, which would
     * make whatever launcher is installed the most-used app on the phone by a
     * factor of two. Returning home is not launching something.
     */
    private fun homePackages(context: Context): Set<String> = runCatching {
        val probe = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        context.packageManager
            .queryIntentActivities(probe, PackageManager.MATCH_DEFAULT_ONLY)
            .mapNotNull { it.activityInfo?.packageName }
            .toSet()
    }.getOrDefault(emptySet())
}
