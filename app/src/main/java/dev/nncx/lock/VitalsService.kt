package dev.nncx.lock

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import dev.nncx.HomeActivity
import dev.nncx.R
import dev.nncx.devtools.Perf
import dev.nncx.settings.Settings
import androidx.compose.ui.graphics.toArgb
import dev.nncx.theme.Accent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * NNCx on the lock screen — as far as Android actually allows.
 *
 * The keyguard cannot be replaced: there is no lock-screen role to claim, and
 * the only real override is device-owner, which needs a factory reset. What a
 * normal app CAN own is an ongoing notification, and a colorized one is the
 * single place the system lets your own colour onto that surface. So this is
 * the vitals panel, in plum, above the clock.
 */
class VitalsService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var ticker: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        ensureChannel()
        startForeground(
            NOTIFICATION_ID,
            build(Perf.sample(this)),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
        )

        ticker?.cancel()
        ticker = scope.launch {
            while (true) {
                // Half a minute is plenty for a glance surface, and keeps this
                // from being the reason the battery readout gets worse.
                delay(30_000)
                val note = build(Perf.sample(this@VitalsService))
                getSystemService(NotificationManager::class.java)
                    .notify(NOTIFICATION_ID, note)
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        ticker?.cancel()
        scope.cancel()
        super.onDestroy()
    }

    private fun build(perf: Perf): Notification {
        val accent = Accent.ground(Settings.get(this).accent)
        val open = PendingIntent.getActivity(
            this, 0,
            Intent(this, HomeActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE,
        )

        val draw = if (perf.currentMa == 0) "—" else "${perf.currentMa} mA"

        return Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_nncx)
            .setContentTitle("${perf.batteryPct}%  ·  ${"%.1f".format(perf.batteryTempC)}°C  ·  $draw")
            .setContentText(
                "up ${perf.uptime}  ·  " +
                    "%.1f / %.1f GB  ·  ".format(perf.memUsedMb / 1024f, perf.memTotalMb / 1024f) +
                    perf.thermal
            )
            // Colorized only works for a foreground service — which is exactly
            // why this is one. It paints the whole notification in your hex.
            .setColor(accent.toArgb())
            .setColorized(true)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setVisibility(Notification.VISIBILITY_PUBLIC)   // readable while locked
            .setContentIntent(open)
            .build()
    }

    private fun ensureChannel() {
        val nm = getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL) != null) return
        // DEFAULT, not LOW. MagicOS files low-importance notifications under
        // "silent" and keeps them off the lock screen entirely — which is the
        // one surface this exists for. Sound and vibration are cleared instead,
        // so it is still quiet without being invisible.
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "Vitals", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Device vitals on the lock screen"
                setShowBadge(false)
                setSound(null, null)
                enableVibration(false)
                enableLights(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
        )
    }

    companion object {
        private const val CHANNEL = "nncx.vitals.v2"
        private const val NOTIFICATION_ID = 1

        fun sync(context: Context) {
            val on = Settings.get(context).vitalsOnLock
            val intent = Intent(context, VitalsService::class.java)
            runCatching {
                if (on) context.startForegroundService(intent) else context.stopService(intent)
            }
        }
    }
}
