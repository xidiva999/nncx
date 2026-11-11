package dev.nncx.settings

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import dev.nncx.BuildConfig
import kotlin.reflect.KProperty

/**
 * What used to live in gradle.properties.
 *
 * The build values stay as the defaults — a fresh clone still boots with the
 * builder's choices — but nothing needs a rebuild to change any more. Backed by
 * SharedPreferences and exposed as Compose state, so a write recomposes the
 * launcher immediately and survives a restart.
 */
class Settings private constructor(private val prefs: SharedPreferences) {

    var accent: String by StringPref("accent", BuildConfig.ACCENT)
    var dockSize: Int by IntPref("dock.size", BuildConfig.DOCK_SIZE)
    var dockOnRight: Boolean by BoolPref(
        "dock.right",
        !BuildConfig.DOCK_SIDE.equals("left", ignoreCase = true),
    )
    /** Folded, the dock can sit along the side instead of across the bottom. */
    var coverRail: Boolean by BoolPref("dock.cover.rail", false)
    var vitalsOnLock: Boolean by BoolPref("vitals.lock", false)
    var pins: List<String> by ListPref("dock.pins", BuildConfig.DOCK)
    var workPrefixes: List<String> by ListPref("work.prefixes", BuildConfig.WORK_PREFIXES)

    fun resetToBuildDefaults() {
        accent = BuildConfig.ACCENT
        dockSize = BuildConfig.DOCK_SIZE
        dockOnRight = !BuildConfig.DOCK_SIDE.equals("left", ignoreCase = true)
        vitalsOnLock = false
        coverRail = false
        pins = split(BuildConfig.DOCK)
        workPrefixes = split(BuildConfig.WORK_PREFIXES)
    }

    // ---- plumbing ---------------------------------------------------------

    private fun split(csv: String) =
        csv.split(",").map { it.trim() }.filter { it.isNotEmpty() }

    private inner class StringPref(val key: String, default: String) {
        private val state: MutableState<String> =
            mutableStateOf(prefs.getString(key, null) ?: default)

        operator fun getValue(ref: Any?, p: KProperty<*>) = state.value
        operator fun setValue(ref: Any?, p: KProperty<*>, v: String) {
            state.value = v
            prefs.edit().putString(key, v).apply()
        }
    }

    private inner class IntPref(val key: String, default: Int) {
        private val state = mutableStateOf(prefs.getInt(key, default))
        operator fun getValue(ref: Any?, p: KProperty<*>) = state.value
        operator fun setValue(ref: Any?, p: KProperty<*>, v: Int) {
            state.value = v
            prefs.edit().putInt(key, v).apply()
        }
    }

    private inner class BoolPref(val key: String, default: Boolean) {
        private val state = mutableStateOf(prefs.getBoolean(key, default))
        operator fun getValue(ref: Any?, p: KProperty<*>) = state.value
        operator fun setValue(ref: Any?, p: KProperty<*>, v: Boolean) {
            state.value = v
            prefs.edit().putBoolean(key, v).apply()
        }
    }

    private inner class ListPref(val key: String, defaultCsv: String) {
        private val state = mutableStateOf(
            split(prefs.getString(key, null) ?: defaultCsv)
        )
        operator fun getValue(ref: Any?, p: KProperty<*>) = state.value
        operator fun setValue(ref: Any?, p: KProperty<*>, v: List<String>) {
            state.value = v
            prefs.edit().putString(key, v.joinToString(",")).apply()
        }
    }

    companion object {
        @Volatile private var instance: Settings? = null

        fun get(context: Context): Settings =
            instance ?: synchronized(this) {
                instance ?: Settings(
                    context.applicationContext
                        .getSharedPreferences("nncx", Context.MODE_PRIVATE)
                ).also { instance = it }
            }
    }
}
