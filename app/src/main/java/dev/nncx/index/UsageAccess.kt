package dev.nncx.index

import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Process
import android.provider.Settings

/**
 * Usage access is a special-access permission — no runtime dialog, only a
 * Settings page. Since NNCx is installed over adb anyway, the install script
 * can just grant it and skip the trip.
 */
object UsageAccess {

    const val ADB_GRANT = "adb shell appops set dev.nncx GET_USAGE_STATS allow"

    fun granted(context: Context): Boolean = runCatching {
        val ops = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        ops.unsafeCheckOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName,
        ) == AppOpsManager.MODE_ALLOWED
    }.getOrDefault(false)

    fun settingsIntent(context: Context): Intent =
        Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            .setData(Uri.fromParts("package", context.packageName, null))
}
