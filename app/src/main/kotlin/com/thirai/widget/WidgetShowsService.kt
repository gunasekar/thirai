package com.thirai.widget

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.util.Log
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import com.bumptech.glide.Glide
import com.bumptech.glide.load.MultiTransformation
import com.bumptech.glide.load.resource.bitmap.CenterCrop
import com.bumptech.glide.load.resource.bitmap.RoundedCorners
import com.thirai.R
import com.thirai.config.Show
import com.thirai.config.ThiraiConfigFetcher
import kotlinx.coroutines.runBlocking

/**
 * Backs the scrolling widget's GridView. The factory runs on a binder thread, so
 * it can (and does) fetch the show list and decode posters synchronously.
 */
class WidgetShowsService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory =
        ShowsFactory(applicationContext)
}

private class ShowsFactory(private val context: Context) : RemoteViewsService.RemoteViewsFactory {

    private var shows: List<Show> = emptyList()
    private var bitmaps: List<Bitmap?> = emptyList()

    override fun onCreate() {}

    override fun onDataSetChanged() {
        shows = runBlocking { ThiraiConfigFetcher.fetchShows(context) }
            .filter { WidgetShowState.isEnabled(context, it) }
        val cornerPx = (12 * context.resources.displayMetrics.density).toInt()
        bitmaps = shows.map { show ->
            if (show.image_url.isEmpty()) return@map null
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
        }
    }

    override fun onDestroy() {
        shows = emptyList()
        bitmaps = emptyList()
    }

    override fun getCount(): Int = shows.size

    override fun getViewAt(position: Int): RemoteViews {
        val show = shows[position]
        val rv = RemoteViews(context.packageName, R.layout.widget_show_item)
        rv.setTextViewText(R.id.show_item_title, show.displayTitle)

        val bitmap = bitmaps.getOrNull(position)
        if (bitmap != null) {
            rv.setImageViewBitmap(R.id.show_item_poster, bitmap)
        } else {
            rv.setImageViewResource(R.id.show_item_poster, R.drawable.show_placeholder)
        }

        // The tap carries only this show's data; it's merged into the grid's
        // PendingIntent template (a getForegroundService to PlaybackService).
        val fillIn = Intent().apply {
            putExtra(PlaybackService.EXTRA_DEEP_LINK, show.deep_link)
            putExtra(PlaybackService.EXTRA_TITLE, show.displayTitle)
            putExtra(PlaybackService.EXTRA_PACKAGE, show.app_package)
            putExtra(PlaybackService.EXTRA_HOME_LINK, show.home_link)
        }
        rv.setOnClickFillInIntent(R.id.show_item_root, fillIn)
        return rv
    }

    override fun getLoadingView(): RemoteViews? = null
    override fun getViewTypeCount(): Int = 1
    override fun getItemId(position: Int): Long = position.toLong()
    override fun hasStableIds(): Boolean = true

    companion object {
        private const val TAG = "ThiraiWidgetSvc"
    }
}
