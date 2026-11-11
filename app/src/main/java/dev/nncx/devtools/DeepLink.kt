package dev.nncx.devtools

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri

/**
 * Paste a URI, see who claims it, fire it.
 *
 * Normally this is `adb shell am start -a android.intent.action.VIEW -d "…"`
 * from a laptop you are not always holding. It needs no permission at all,
 * which makes it the cheapest real tool in the box.
 */
object DeepLink {

    data class Match(val label: String, val component: String)

    fun resolve(context: Context, raw: String): Match? {
        val uri = parse(raw) ?: return null
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_VIEW, uri)
        val info = pm.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY) ?: return null
        val activity = info.activityInfo ?: return null
        return Match(
            label = runCatching { info.loadLabel(pm).toString() }.getOrDefault(activity.packageName),
            component = activity.packageName + "/" + activity.name.substringAfterLast('.'),
        )
    }

    /** Returns null on success, or why it failed. */
    fun fire(context: Context, raw: String): String? {
        val uri = parse(raw) ?: return "not a uri"
        return runCatching {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            null
        }.getOrElse { it.javaClass.simpleName }
    }

    private fun parse(raw: String): Uri? {
        val s = raw.trim()
        if (s.isEmpty() || !s.contains(":")) return null
        return runCatching { Uri.parse(s) }.getOrNull()
    }
}
