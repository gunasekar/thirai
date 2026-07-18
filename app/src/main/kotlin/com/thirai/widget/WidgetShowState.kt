package com.thirai.widget

import android.content.Context
import com.thirai.config.Show

/**
 * Per-show, per-phone control over which shows appear on the home-screen widget.
 *
 * Each show's config `status` provides the *default* (enabled unless parked with
 * "disabled"). The app then lets the user toggle each show for the widget, and
 * that local choice is stored here and wins from then on — so editing `status`
 * in the config later only affects shows the user hasn't touched. The app itself
 * always lists every show regardless; this only governs the widget.
 */
object WidgetShowState {
    private const val PREFS = "thirai_prefs"
    private fun key(id: String) = "widget_show_$id"

    /** Effective widget state: the local override if the user has set one, else the config default. */
    fun isEnabled(context: Context, show: Show): Boolean {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val k = key(show.id)
        return if (prefs.contains(k)) prefs.getBoolean(k, true) else show.enabled
    }

    fun setEnabled(context: Context, showId: String, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(key(showId), enabled).apply()
    }
}
