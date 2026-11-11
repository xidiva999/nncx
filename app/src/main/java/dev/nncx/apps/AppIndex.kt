package dev.nncx.apps

import android.content.ComponentName
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.LauncherApps
import android.os.Process
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * One row per launchable target. `kind` is deliberately absent for now — when
 * contacts and ssh hosts arrive they join the same index rather than getting
 * their own.
 */
data class AppEntry(
    val label: String,
    val packageName: String,
    val activity: String,
    val debuggable: Boolean,
    val system: Boolean,
    val firstInstall: Long,
) {
    val sortKey: String = label.lowercase()
    val component: ComponentName get() = ComponentName(packageName, activity)
}

object AppIndex {

    /**
     * LauncherApps, not queryIntentActivities.
     *
     * The obvious call — queryIntentActivities(MAIN/LAUNCHER, MATCH_DEFAULT_ONLY)
     * — silently drops any app whose launcher activity does not also declare
     * CATEGORY_DEFAULT, which is most of them. That flag is for resolving
     * implicit intents, not for enumerating a drawer. LauncherApps is the API
     * launchers are meant to use, and it carries work profiles for free later.
     */
    suspend fun load(context: Context): List<AppEntry> = withContext(Dispatchers.IO) {
        val la = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
        val self = context.packageName
        val user = Process.myUserHandle()

        runCatching { la.getActivityList(null, user) }
            .getOrDefault(emptyList())
            .asSequence()
            .filter { it.applicationInfo.packageName != self }
            .map { info ->
                val ai: ApplicationInfo = info.applicationInfo
                AppEntry(
                    label = runCatching { info.label.toString() }
                        .getOrDefault(ai.packageName),
                    packageName = ai.packageName,
                    activity = info.componentName.className,
                    debuggable = ai.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0,
                    system = ai.flags and ApplicationInfo.FLAG_SYSTEM != 0,
                    firstInstall = runCatching { info.firstInstallTime }.getOrDefault(0L),
                )
            }
            .distinctBy { it.packageName }
            .sortedBy { it.sortKey }
            .toList()
    }

    fun launch(context: Context, entry: AppEntry) {
        val la = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
        runCatching {
            la.startMainActivity(entry.component, Process.myUserHandle(), null, null)
        }
    }
}
