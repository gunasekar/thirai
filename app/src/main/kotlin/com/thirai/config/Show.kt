package com.thirai.config

import kotlinx.serialization.Serializable

@Serializable
data class ShowConfig(
    val shows: List<Show> = emptyList(),
    // Optional app-level settings, managed in the same gist. Everything here has
    // a sane default so an older/simpler config (just a `shows` array) still
    // parses. Extend this as new global knobs are needed.
    val config: AppConfig = AppConfig()
)

@Serializable
data class AppConfig(
    // The streaming app used for shows that don't name their own. Set this once
    // in the gist (e.g. "in.startv.hotstar") so playback can wait for the app to
    // foreground without the package being baked into the app.
    val default_app_package: String = ""
)

@Serializable
data class Show(
    val id: String = "",
    // Canonical (English) title — used as a stable identifier and fallback.
    val title: String = "",
    // Display title in Tamil, managed in the gist. Shown in the app and widget
    // when set; falls back to [title]. Keeps the UI chrome English while the
    // show names read in Tamil for the viewer.
    val title_ta: String = "",
    val image_url: String = "",
    val deep_link: String = "",
    // The streaming app this show opens in (e.g. "in.startv.hotstar"). Optional
    // and set per-show in the config, so the app itself stays platform-agnostic:
    // playback waits for this package to reach the TV foreground before nudging
    // it into play. Leave empty to skip that wait (a blind timed launch).
    val app_package: String = ""
) {
    /** The title to show the viewer — Tamil when provided, else the English name. */
    val displayTitle: String get() = title_ta.ifBlank { title }
}
