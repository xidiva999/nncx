package dev.nncx.devtools

import android.Manifest
import android.content.ContentResolver
import android.content.Context
import android.content.pm.PackageManager
import android.provider.Settings

/**
 * Real developer toggles, not a shortcut to the Settings page.
 *
 * Writing `adb_enabled` and friends needs WRITE_SECURE_SETTINGS, which is
 * signature|privileged — no Play Store app can hold it. But NNCx is installed
 * over adb anyway, and `pm grant` lets that permission through for a package
 * that declares it. One line in the install script, once:
 *
 *   adb shell pm grant dev.nncx android.permission.WRITE_SECURE_SETTINGS
 */
class Toggle(
    val id: String,
    val label: String,
    val detail: String,
    val read: (ContentResolver) -> Boolean,
    val write: (ContentResolver, Boolean) -> Unit,
)

object DevToggles {

    const val ADB_GRANT =
        "adb shell pm grant dev.nncx android.permission.WRITE_SECURE_SETTINGS"

    fun granted(context: Context): Boolean =
        context.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) ==
            PackageManager.PERMISSION_GRANTED

    private fun globalInt(key: String, on: Int = 1) = { cr: ContentResolver ->
        Settings.Global.getInt(cr, key, 0) != 0
    } to { cr: ContentResolver, v: Boolean ->
        Settings.Global.putInt(cr, key, if (v) on else 0)
    }

    val all: List<Toggle> = listOf(
        Toggle(
            id = "adb",
            label = "USB debugging",
            detail = "global adb_enabled",
            read = { Settings.Global.getInt(it, Settings.Global.ADB_ENABLED, 0) != 0 },
            write = { cr, v -> Settings.Global.putInt(cr, Settings.Global.ADB_ENABLED, if (v) 1 else 0) },
        ),

        // Three scales move together — nobody wants two of them off.
        Toggle(
            id = "anim",
            label = "Animations off",
            detail = "window / transition / animator scale",
            read = {
                Settings.Global.getFloat(it, Settings.Global.WINDOW_ANIMATION_SCALE, 1f) == 0f
            },
            write = { cr, v ->
                val s = if (v) 0f else 1f
                Settings.Global.putFloat(cr, Settings.Global.WINDOW_ANIMATION_SCALE, s)
                Settings.Global.putFloat(cr, Settings.Global.TRANSITION_ANIMATION_SCALE, s)
                Settings.Global.putFloat(cr, Settings.Global.ANIMATOR_DURATION_SCALE, s)
            },
        ),

        Toggle(
            id = "nokeep",
            label = "Don't keep activities",
            detail = "global always_finish_activities",
            read = { Settings.Global.getInt(it, "always_finish_activities", 0) != 0 },
            write = { cr, v -> Settings.Global.putInt(cr, "always_finish_activities", if (v) 1 else 0) },
        ),

        Toggle(
            id = "touches",
            label = "Show taps",
            detail = "system show_touches",
            read = { Settings.System.getInt(it, "show_touches", 0) != 0 },
            write = { cr, v -> Settings.System.putInt(cr, "show_touches", if (v) 1 else 0) },
        ),

        Toggle(
            id = "pointer",
            label = "Pointer location",
            detail = "system pointer_location",
            read = { Settings.System.getInt(it, "pointer_location", 0) != 0 },
            write = { cr, v -> Settings.System.putInt(cr, "pointer_location", if (v) 1 else 0) },
        ),

        Toggle(
            id = "stayawake",
            label = "Stay awake while charging",
            detail = "global stay_on_while_plugged_in",
            read = { Settings.Global.getInt(it, Settings.Global.STAY_ON_WHILE_PLUGGED_IN, 0) != 0 },
            // 7 = AC | USB | wireless
            write = { cr, v ->
                Settings.Global.putInt(cr, Settings.Global.STAY_ON_WHILE_PLUGGED_IN, if (v) 7 else 0)
            },
        ),

        Toggle(
            id = "viewattrs",
            label = "Debug view attributes",
            detail = "global debug_view_attributes",
            read = { Settings.Global.getInt(it, "debug_view_attributes", 0) != 0 },
            write = { cr, v -> Settings.Global.putInt(cr, "debug_view_attributes", if (v) 1 else 0) },
        ),
    )

    fun read(context: Context, toggle: Toggle): Boolean =
        runCatching { toggle.read(context.contentResolver) }.getOrDefault(false)

    /** Returns false when the write was refused — i.e. the grant is missing. */
    fun write(context: Context, toggle: Toggle, value: Boolean): Boolean =
        runCatching { toggle.write(context.contentResolver, value); true }.getOrDefault(false)
}
