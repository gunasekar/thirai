package com.thirai.ui.theme

import androidx.compose.ui.graphics.Color

// Brand accent — cinematic amber/marigold. Thirai means "screen / curtain"
// in Tamil; the accent is the warm glow of stage light against a darkened
// theatre. A bright marigold with dark text for the dark theme, a deeper
// burnt tone with white text for light, so it reads as a confident accent on
// either ground.
//
// The SATURATED accent is reserved for the one primary action per screen and
// true selection. Everything else that wants to feel "amber" — an emphasised
// stat, a live status — uses the muted *container* tone below, so the loud
// fill only ever means "the action".
val AccentDark = Color(0xFFE8A33D)
val OnAccentDark = Color(0xFF2A1B04)
val AccentLight = Color(0xFF9A5E10)
val OnAccentLight = Color(0xFFFFFFFF)

// Tonal accent — a soft, low-chroma amber that sits between surface and the
// saturated accent. Used for accent emphasis that is NOT the primary action.
val AccentContainerDark = Color(0xFF3C2C12)
val OnAccentContainerDark = Color(0xFFF4CE8E)
val AccentContainerLight = Color(0xFFF6E3C2)
val OnAccentContainerLight = Color(0xFF422C08)

// Dark theme — a warm near-black, biased faintly toward the amber accent so
// the app feels like a dimmed screening room rather than a cold slab.
val DarkBackground = Color(0xFF0E0B07)
val DarkSurface = Color(0xFF17130C)
val DarkSurfaceVariant = Color(0xFF241E14)
val DarkOnSurface = Color(0xFFF3EEE4)
val DarkOnSurfaceVariant = Color(0xFFA79E8C)
val DarkOutline = Color(0xFF3A3225)

// Light theme — warm off-white with clean white cards.
val LightBackground = Color(0xFFFAF7F1)
val LightSurface = Color(0xFFFFFFFF)
val LightSurfaceVariant = Color(0xFFEFE9DD)
val LightOnSurface = Color(0xFF171310)
val LightOnSurfaceVariant = Color(0xFF5E574B)
val LightOutline = Color(0xFFDAD2C4)

val SoftRed = Color(0xFFE5484D)
val SoftGreen = Color(0xFF46A758)
