package com.thirai

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.RemoteViews
import com.bumptech.glide.Glide
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ThiraiWidgetProvider : AppWidgetProvider() {

    companion object {
        private const val TAG = "ThiraiWidget"
        const val ACTION_PLAY_SHOW = "com.thirai.PLAY_SHOW"
        const val EXTRA_SHOW_URL = "com.thirai.SHOW_URL"
        const val EXTRA_SHOW_INDEX = "com.thirai.SHOW_INDEX"
    }

    private data class Slot(val posterId: Int, val titleId: Int)

    private val slots = listOf(
        Slot(R.id.show_poster_1, R.id.show_title_1),
        Slot(R.id.show_poster_2, R.id.show_title_2),
        Slot(R.id.show_poster_3, R.id.show_title_3),
        Slot(R.id.show_poster_4, R.id.show_title_4),
    )

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        CoroutineScope(Dispatchers.IO).launch {
            val shows = ThiraiConfigFetcher.fetchShows(context)

            withContext(Dispatchers.Main) {
                for (appWidgetId in appWidgetIds) {
                    updateWidget(context, appWidgetManager, appWidgetId, shows)
                }
            }
        }
    }

    private fun updateWidget(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        shows: List<Show>
    ) {
        val views = RemoteViews(context.packageName, R.layout.widget_thirai)

        for (i in slots.indices) {
            val slot = slots[i]

            if (i < shows.size) {
                val show = shows[i]

                // Set title text
                views.setTextViewText(slot.titleId, show.title)

                // Set up click intent
                val intent = Intent(context, ThiraiWidgetProvider::class.java).apply {
                    action = ACTION_PLAY_SHOW
                    putExtra(EXTRA_SHOW_URL, show.deep_link)
                    putExtra(EXTRA_SHOW_INDEX, i)
                }
                val pendingIntent = PendingIntent.getBroadcast(
                    context, i, intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                views.setOnClickPendingIntent(slot.posterId, pendingIntent)

                // Load poster image using Glide
                if (show.image_url.isNotEmpty()) {
                    try {
                        val bitmap = Glide.with(context.applicationContext)
                            .asBitmap()
                            .load(show.image_url)
                            .override(300, 300)
                            .centerCrop()
                            .submit()
                            .get()
                        views.setImageViewBitmap(slot.posterId, bitmap)
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to load image for show ${show.title}: ${e.message}")
                        views.setImageViewResource(slot.posterId, R.drawable.show_placeholder)
                    }
                } else {
                    views.setImageViewResource(slot.posterId, R.drawable.show_placeholder)
                }
            } else {
                // Empty slot
                views.setImageViewResource(slot.posterId, R.drawable.show_empty)
                views.setTextViewText(slot.titleId, "")
            }
        }

        appWidgetManager.updateAppWidget(appWidgetId, views)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)

        if (intent.action == ACTION_PLAY_SHOW) {
            val deepLinkUrl = intent.getStringExtra(EXTRA_SHOW_URL) ?: return

            CoroutineScope(Dispatchers.IO).launch {
                val adbController = AdbController(context.applicationContext)
                adbController.triggerTvPlayback(deepLinkUrl)
            }
        }
    }
}
