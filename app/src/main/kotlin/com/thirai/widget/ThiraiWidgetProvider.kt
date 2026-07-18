package com.thirai.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.util.Log
import android.view.View
import android.widget.RemoteViews
import com.bumptech.glide.Glide
import com.bumptech.glide.load.MultiTransformation
import com.bumptech.glide.load.resource.bitmap.CenterCrop
import com.bumptech.glide.load.resource.bitmap.RoundedCorners
import com.thirai.R
import com.thirai.config.Show
import com.thirai.config.ThiraiConfigFetcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ThiraiWidgetProvider : AppWidgetProvider() {

    companion object {
        private const val TAG = "ThiraiWidget"
    }

    private data class Slot(val tileId: Int, val posterId: Int, val titleId: Int)

    private val slots = listOf(
        Slot(R.id.show_tile_1, R.id.show_poster_1, R.id.show_title_1),
        Slot(R.id.show_tile_2, R.id.show_poster_2, R.id.show_title_2),
        Slot(R.id.show_tile_3, R.id.show_poster_3, R.id.show_title_3),
        Slot(R.id.show_tile_4, R.id.show_poster_4, R.id.show_title_4),
        Slot(R.id.show_tile_5, R.id.show_poster_5, R.id.show_title_5),
        Slot(R.id.show_tile_6, R.id.show_poster_6, R.id.show_title_6),
    )

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        CoroutineScope(Dispatchers.IO).launch {
            val shows = ThiraiConfigFetcher.fetchShows(context)

            // Pre-load all bitmaps on background thread
            // Round the poster corners to match the app's card language. Corner
            // radius is in pixels here (RemoteViews can't apply an outline), so
            // scale by density to hold ~12dp across screens.
            val cornerPx = (12 * context.resources.displayMetrics.density).toInt()
            val bitmaps = shows.map { show ->
                if (show.image_url.isNotEmpty()) {
                    try {
                        Glide.with(context.applicationContext)
                            .asBitmap()
                            .load(show.image_url)
                            .override(300, 400)
                            .transform(MultiTransformation(CenterCrop(), RoundedCorners(cornerPx)))
                            .submit()
                            .get()
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to load image for ${show.title}: ${e.message}")
                        null
                    }
                } else null
            }

            withContext(Dispatchers.Main) {
                for (appWidgetId in appWidgetIds) {
                    updateWidget(context, appWidgetManager, appWidgetId, shows, bitmaps)
                }
            }
        }
    }

    private fun updateWidget(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        shows: List<Show>,
        bitmaps: List<Bitmap?>
    ) {
        val views = RemoteViews(context.packageName, R.layout.widget_thirai)

        for (i in slots.indices) {
            val slot = slots[i]

            if (i < shows.size) {
                val show = shows[i]

                views.setViewVisibility(slot.tileId, View.VISIBLE)
                views.setTextViewText(slot.titleId, show.displayTitle)

                // Tapping a poster starts the playback service directly. A widget
                // tap is an FGS-start exemption, so this needs no BroadcastReceiver
                // middleman (which would risk an ANR / a blocked background start).
                val serviceIntent = Intent(context, PlaybackService::class.java).apply {
                    putExtra(PlaybackService.EXTRA_DEEP_LINK, show.deep_link)
                    putExtra(PlaybackService.EXTRA_TITLE, show.displayTitle)
                    putExtra(PlaybackService.EXTRA_PACKAGE, show.app_package)
                    putExtra(PlaybackService.EXTRA_HOME_LINK, show.home_link)
                }
                val pendingIntent = PendingIntent.getForegroundService(
                    context, i, serviceIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                // The whole tile is the tap target, not just the image.
                views.setOnClickPendingIntent(slot.tileId, pendingIntent)

                val bitmap = bitmaps.getOrElse(i) { null }
                if (bitmap != null) {
                    views.setImageViewBitmap(slot.posterId, bitmap)
                } else {
                    views.setImageViewResource(slot.posterId, R.drawable.show_placeholder)
                }
            } else {
                // No show for this slot — collapse it so the row never shows
                // empty boxes.
                views.setViewVisibility(slot.tileId, View.GONE)
            }
        }

        // "Play from start" button — restarts whatever is currently playing on
        // the TV from the beginning. Uses a foreground-service PendingIntent like
        // the tiles (a widget tap is FGS-exempt).
        val restartIntent = Intent(context, PlaybackService::class.java).apply {
            putExtra(PlaybackService.EXTRA_RESTART_ONLY, true)
        }
        val restartPending = PendingIntent.getForegroundService(
            context, 999, restartIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.widget_restart, restartPending)

        // Otherwise nothing in the widget opens the app — the poster tiles play,
        // this button restarts. The app is opened from its own launcher icon.
        appWidgetManager.updateAppWidget(appWidgetId, views)
    }
}
