package com.thirai.config

import kotlinx.serialization.Serializable

@Serializable
data class ShowConfig(
    val shows: List<Show> = emptyList()
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
    // Whether the show is live. "enabled" (the default; "active" is also accepted)
    // shows it in the app and widget; "disabled" (or any other value) keeps the
    // entry in the config but drops it from the list — a way to park a show
    // off-season without deleting it. See [enabled].
    val status: String = "enabled",
    // The streaming app's home URL (e.g. "https://www.hotstar.com/in"). Before a
    // show opens, Thirai launches this to reset the app: the home screen is
    // single-task, so it clears any players stacked on top, and the show then
    // opens as the only entry above home — so the TV's Back button returns to the
    // app home instead of walking back through every show opened before it. Blank
    // skips the reset (the show opens directly and may stack). Per-show so each
    // show can live in a different app (Hotstar, Netflix, Prime, …).
    val home_link: String = "",
    // The streaming app this show opens in (e.g. "in.startv.hotstar"). Set
    // per-show so the app stays platform-agnostic and a single config can mix
    // apps: playback waits for this package to reach the TV foreground before
    // nudging it into play. Leave empty to skip that wait (a blind timed launch).
    val app_package: String = ""
) {
    /** The title to show the viewer — the native-language name when provided, else the English one. */
    val displayTitle: String get() = title_native.ifBlank { title }

    /** True unless the config explicitly parks this show (status not active/enabled). */
    val enabled: Boolean
        get() = status.isBlank() || status.equals("active", true) || status.equals("enabled", true)
}
