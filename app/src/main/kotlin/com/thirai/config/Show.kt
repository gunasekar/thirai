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
    // Display title in the viewer's own language, managed in the config. Shown in
    // the app and widget when set; falls back to [title]. Keeps the app chrome in
    // English while the show names read in the viewer's native language.
    val title_native: String = "",
    val image_url: String = "",
    val deep_link: String = "",
    // The streaming app this show opens in (e.g. "in.startv.hotstar"). Optional
    // and set per-show in the config, so the app itself stays platform-agnostic:
    // playback waits for this package to reach the TV foreground before nudging
    // it into play. Leave empty to skip that wait (a blind timed launch).
    val app_package: String = ""
) {
    /** The title to show the viewer — the native-language name when provided, else the English one. */
    val displayTitle: String get() = title_native.ifBlank { title }
}
