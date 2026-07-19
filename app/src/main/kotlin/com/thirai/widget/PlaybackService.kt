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
        val homeLink = intent?.getStringExtra(EXTRA_HOME_LINK)
        val restartOnly = intent?.getBooleanExtra(EXTRA_RESTART_ONLY, false) ?: false
        val stopOnly = intent?.getBooleanExtra(EXTRA_STOP_ONLY, false) ?: false
        val playPause = intent?.getBooleanExtra(EXTRA_PLAY_PAUSE, false) ?: false
        val title = intent?.getStringExtra(EXTRA_TITLE) ?: "your show"

        val (notifTitle, notifText) = when {
            stopOnly -> "Stopping on TV" to "Returning to the app's home…"
            playPause -> "Play/Pause on TV" to "Toggling playback…"
            restartOnly -> "Restarting on TV" to "Playing “$title” from the start…"
            else -> "Starting on TV" to "Playing “$title”…"
        }
        startForeground(NOTIFICATION_ID, buildNotification(notifTitle, notifText))

        if (!restartOnly && !stopOnly && !playPause && deepLink.isNullOrBlank()) {
            stopSelf(startId)
            return START_NOT_STICKY
        }

        scope.launch {
            try {
                val tv = TvController(applicationContext)
                when {
                    stopOnly -> tv.stopToHome()
                    playPause -> tv.playPause()
                    restartOnly -> tv.restartCurrent()
                    else -> tv.play(deepLink!!, appPackage, homeLink)
                }
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

    private fun buildNotification(contentTitle: String, contentText: String): Notification {
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
            .setContentTitle(contentTitle)
            .setContentText(contentText)
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
        const val EXTRA_HOME_LINK = "com.thirai.HOME_LINK"
        const val EXTRA_RESTART_ONLY = "com.thirai.RESTART_ONLY"
        const val EXTRA_STOP_ONLY = "com.thirai.STOP_ONLY"
        const val EXTRA_PLAY_PAUSE = "com.thirai.PLAY_PAUSE"
    }
}
