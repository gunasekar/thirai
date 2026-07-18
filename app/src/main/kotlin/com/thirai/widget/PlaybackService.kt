package com.thirai.widget

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.thirai.R
import com.thirai.tv.TvController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Sends a show to the TV on behalf of the widget.
 *
 * A widget tap arrives as a broadcast, which Android only lets run for ~10s
 * before an ANR. Launching a show — connecting, opening the deep link, then
 * nudging it into playback — takes longer than that, so the widget hands the
 * work to this short-lived foreground service, which keeps the process alive
 * until the launch completes and then stops itself.
 */
class PlaybackService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val deepLink = intent?.getStringExtra(EXTRA_DEEP_LINK)
        val appPackage = intent?.getStringExtra(EXTRA_PACKAGE)
        val restartOnly = intent?.getBooleanExtra(EXTRA_RESTART_ONLY, false) ?: false
        val title = intent?.getStringExtra(EXTRA_TITLE)
            ?: if (restartOnly) "the current show" else "your show"
        startForeground(NOTIFICATION_ID, buildNotification(title, restartOnly))

        if (!restartOnly && deepLink.isNullOrBlank()) {
            stopSelf(startId)
            return START_NOT_STICKY
        }

        scope.launch {
            try {
                val tv = TvController(applicationContext)
                if (restartOnly) tv.restartCurrent() else tv.play(deepLink!!, appPackage)
            } finally {
                stopSelf(startId)
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun buildNotification(title: String, restartOnly: Boolean): Notification {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Playing on TV",
                NotificationManager.IMPORTANCE_LOW,
            ).apply { description = "Shown briefly while Thirai controls the TV." }
            manager.createNotificationChannel(channel)
        }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_logo)
            .setContentTitle(if (restartOnly) "Restarting on TV" else "Starting on TV")
            .setContentText(if (restartOnly) "Playing “$title” from the start…" else "Playing “$title”…")
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "thirai_playback"
        private const val NOTIFICATION_ID = 1
        const val EXTRA_DEEP_LINK = "com.thirai.DEEP_LINK"
        const val EXTRA_TITLE = "com.thirai.TITLE"
        const val EXTRA_PACKAGE = "com.thirai.PACKAGE"
        const val EXTRA_RESTART_ONLY = "com.thirai.RESTART_ONLY"
    }
}
