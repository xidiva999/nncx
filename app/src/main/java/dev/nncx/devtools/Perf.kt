package dev.nncx.devtools

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Environment
import android.os.PowerManager
import android.os.StatFs
import android.os.SystemClock

/**
 * What the left pane shows once the skin is off.
 *
 * Everything here is readable without a single permission — uptime, memory,
 * battery, thermal state and storage are all public APIs. The one thing
 * conspicuously missing is CPU load: /proc/stat has been off limits to apps
 * since Android 8, and there is no supported replacement.
 */
data class Perf(
    val uptimeMs: Long,
    val memUsedMb: Long,
    val memTotalMb: Long,
    val batteryPct: Int,
    val batteryTempC: Float,
    /** Negative while discharging, positive while charging. Zero if unknown. */
    val currentMa: Int,
    val thermal: String,
    val storageFreeGb: Float,
    val storageTotalGb: Float,
) {
    val uptime: String
        get() {
            val s = uptimeMs / 1000
            val d = s / 86_400
            val h = (s % 86_400) / 3600
            val m = (s % 3600) / 60
            return if (d > 0) "%dd %02d:%02d".format(d, h, m) else "%02d:%02d".format(h, m)
        }

    companion object {
        fun sample(context: Context): Perf {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val mem = ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }

            val battery: Intent? = runCatching {
                context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            }.getOrNull()
            val level = battery?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale = battery?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
            val tenthsC = battery?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0

            val bm = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
            val microAmps = runCatching {
                bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
            }.getOrDefault(0)

            val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            val thermal = runCatching {
                when (pm.currentThermalStatus) {
                    PowerManager.THERMAL_STATUS_NONE -> "none"
                    PowerManager.THERMAL_STATUS_LIGHT -> "light"
                    PowerManager.THERMAL_STATUS_MODERATE -> "moderate"
                    PowerManager.THERMAL_STATUS_SEVERE -> "severe"
                    PowerManager.THERMAL_STATUS_CRITICAL -> "critical"
                    PowerManager.THERMAL_STATUS_EMERGENCY -> "emergency"
                    PowerManager.THERMAL_STATUS_SHUTDOWN -> "shutdown"
                    else -> "?"
                }
            }.getOrDefault("?")

            val stat = runCatching { StatFs(Environment.getDataDirectory().path) }.getOrNull()
            val free = stat?.let { it.availableBlocksLong * it.blockSizeLong } ?: 0L
            val total = stat?.let { it.blockCountLong * it.blockSizeLong } ?: 0L
            val gb = 1024f * 1024f * 1024f

            return Perf(
                uptimeMs = SystemClock.elapsedRealtime(),
                memUsedMb = (mem.totalMem - mem.availMem) / (1024 * 1024),
                memTotalMb = mem.totalMem / (1024 * 1024),
                batteryPct = if (level >= 0) level * 100 / scale else -1,
                batteryTempC = tenthsC / 10f,
                // µA on most devices, mA on a few; the magnitude tells us which.
                currentMa = if (microAmps > 100_000 || microAmps < -100_000) microAmps / 1000 else microAmps,
                thermal = thermal,
                storageFreeGb = free / gb,
                storageTotalGb = total / gb,
            )
        }
    }
}
