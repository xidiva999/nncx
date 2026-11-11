package dev.nncx.devtools

import android.app.ActivityManager
import android.content.Context
import android.content.pm.ApplicationInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * Free what can be freed.
 *
 * killBackgroundProcesses is a normal permission — no grant, no Shizuku. It
 * cannot touch foreground apps or running services, which is the honest
 * caveat: Android is already good at this, and on a healthy device the number
 * will often be small. The before/after is reported either way rather than a
 * reassuring animation.
 */
object Memory {

    data class Freed(val beforeMb: Long, val afterMb: Long, val apps: Int) {
        val gainedMb: Long get() = (afterMb - beforeMb).coerceAtLeast(0)
    }

    suspend fun free(context: Context): Freed = withContext(Dispatchers.IO) {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val before = available(am)

        var touched = 0
        runCatching {
            context.packageManager.getInstalledApplications(0).forEach { app ->
                val isSystem = app.flags and ApplicationInfo.FLAG_SYSTEM != 0
                if (app.packageName == context.packageName || isSystem) return@forEach
                runCatching {
                    am.killBackgroundProcesses(app.packageName)
                    touched++
                }
            }
        }

        // Let the kernel actually hand the pages back before measuring.
        delay(600)
        Freed(before, available(am), touched)
    }

    private fun available(am: ActivityManager): Long =
        ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }.availMem / (1024 * 1024)
}
