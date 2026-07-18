# Thirai (திரை) — Requirements Document

## Problem Statement

The user's mother uses a Sony TV and a OnePlus TV (both Android TV) with a Disney+ Hotstar subscription. She is not comfortable navigating smart TV apps — she only knows how to use a basic TV remote to switch channels. She watches a set of 4-5 programs regularly and needs someone to help her start playback. The goal is to build a solution that lets her start watching her shows independently with minimal interaction.

---

## Core Requirements

### R1 — One-Tap Playback from Phone Home Screen

The primary interaction must be a **home screen widget** on the mother's Android phone. She should never need to open an app.

- Widget displays 4-5 show posters in a horizontal grid
- Tapping a poster starts playback on the TV immediately
- Widget shows visual feedback (loading spinner, playing indicator) without leaving the home screen
- Tapping a currently-playing poster sends pause/resume command
- Behavior mirrors her existing mental model: swipe to the widget screen, tap an icon (like speed dial for contacts)

### R2 — Direct Phone-to-TV Communication (No Local Server)

The phone communicates directly with the TV over the local Wi-Fi network. No Raspberry Pi, no always-on computer, no local bridge server.

- Embedded ADB (Android Debug Bridge) client inside the Android app
- Uses `com.tananaev:adblib:1.3` library for ADB over TCP/IP
- One-time pairing: TV shows "Allow network debugging?" prompt, user checks "Always allow"
- Commands sent via `am start -a android.intent.action.VIEW -d "<deep_link>" <package>`

### R3 — Automatic TV Discovery (No Hardcoded IP)

TV IP addresses change when the router reboots (DHCP). The app must handle this transparently.

- **Hybrid Auto-Discovery approach:**
  - **Fast path (99%):** Cached IP from `SharedPreferences`, connection in milliseconds
  - **Fallback (1%):** Android `NsdManager` scans for `_adb._tcp.` mDNS service broadcast
  - **Self-healing:** Newly discovered IP is saved to cache for next use
- Rare fallback adds ~2 second delay; user experience is otherwise instant

### R4 — Remote Show Management via Cloud Config

Shows (poster images, deep links, titles) must be manageable remotely without touching the mother's phone.

- **Cloud config source:** GitHub Gist (free, globally accessible)
- JSON format with show list:
  ```json
  {
    "shows": [
      {
        "id": "1",
        "title": "Show Name",
        "image_url": "https://example.com/poster.jpg",
        "deep_link": "https://www.hotstar.com/in/shows/..."
      }
    ]
  }
  ```
- Widget fetches config periodically (daily or on widget refresh)
- When shows change (every ~3 months), user edits the Gist from anywhere in the world
- Widget auto-updates posters and deep links on next sync

### R5 — Multi-Platform Deep Link Support

The system must work with any streaming app that supports Android deep links, not just Hotstar.

- **Supported platforms:**
  - Disney+ Hotstar: `https://www.hotstar.com/in/shows/...`
  - YouTube: `https://www.youtube.com/watch?v=...`
  - Netflix: `http://www.netflix.com/watch/...`
  - Amazon Prime Video: `https://app.primevideo.com/detail?gti=...`
- Uses universal Android `VIEW` intent — TV automatically opens the correct app
- Future-proof: swapping a Hotstar show for a YouTube show requires only updating the Gist URL

### R6 — Minimal Dependencies & Lightweight

The app must be extremely lightweight since it runs primarily as a background widget.

- **Required dependencies:**
  - `com.tananaev:adblib:1.3` — ADB protocol implementation
  - `okhttp` — HTTP client for Gist fetching
  - `kotlinx-coroutines-android` — Background execution
  - `kotlinx-serialization-json` — JSON parsing
  - `androidx.core.ktx`, `androidx.lifecycle.runtime.ktx` — Android essentials
- **Removed (from puraa template):** Room DB, WorkManager, Zxing, Jetpack Compose, KSP
- No UI Activity needed (widget-only app, admin screen is optional future work)

---

## Non-Functional Requirements

### NF1 — Battery Efficiency
- ADB connection is opened, command sent, and socket closed immediately
- No persistent network connections from the widget
- Background work runs on `Dispatchers.IO` and completes in seconds

### NF2 — Reliability
- Hybrid caching ensures 99% instant playback
- Auto-discovery self-heals when IP changes
- Graceful error handling with user-visible feedback on widget (loading/error states)

### NF3 — Maintainability
- Show configuration lives in a single GitHub Gist JSON file
- No code changes needed when shows rotate (every ~3 months)
- Android project follows standard Gradle/Kotlin conventions

### NF4 — Compatibility
- Target: Android TV (Sony, OnePlus) with Hotstar app installed
- Phone app: Android 8.0+ (API 26+) for widget support
- ADB over TCP/IP must be enabled on TV (one-time developer setup)

---

## Architecture

```
┌─────────────────────┐         ┌──────────────────┐         ┌─────────────────┐
│   Mother's Phone    │         │   GitHub Gist    │         │    Android TV   │
│                     │         │   (Cloud Config) │         │  (Sony/OnePlus) │
│  ┌───────────────┐  │  fetch  │                  │         │                 │
│  │ Home Screen   │  │◄────────│  shows.json      │         │  ┌───────────┐  │
│  │ Widget        │  │         │  (poster URLs,   │         │  │ Hotstar   │  │
│  │               │  │         │   deep links)    │         │  │ App       │  │
│  │ [Poster] [Post│  │         └──────────────────┘         │  │           │  │
│  │  er] [Poster] │  │                                      │  │ Receives  │  │
│  └───────┬───────┘  │         ┌──────────────────┐         │  │ deep link │  │
│          │          │  ADB    │   Local Wi-Fi    │  ADB    │  │ intent    │  │
│  ┌───────┴───────┐  │◄────────│   Network        │────────►│  └───────────┘  │
│  │ AdbController │  │  TCP    │                  │  TCP    │                 │
│  │               │──│────────►│  NsdManager      │  :5555  │  am start VIEW  │
│  │ - Cache (IP)  │  │         │  mDNS discovery  │         │  -d <deep_link> │
│  │ - NsdManager  │  │         └──────────────────┘         │  com.hotstar.tv │
│  │ - adblib      │  │                                      │                 │
│  └───────────────┘  │                                      └─────────────────┘
└─────────────────────┘
```

**Data Flow:**
1. Widget periodically fetches `shows.json` from GitHub Gist → updates poster images and deep links
2. Mother taps poster on widget → `ThiraiWidgetProvider.onReceive()` fires
3. Widget updates UI to "Loading..." state
4. `AdbController.triggerTvPlayback()` runs on `Dispatchers.IO`:
   - Tries cached IP from `SharedPreferences`
   - If fails → `NsdManager` scans for `_adb._tcp.` service
   - Opens socket to TV on port 5555
   - Sends `am start -a android.intent.action.VIEW -d "<deep_link>" <package>`
   - Closes socket immediately
5. Widget updates UI to "Playing" state
6. Mother taps again → same flow sends `input keyevent 85` (pause/play toggle)

---

## Files Created / Modified

| File | Purpose |
|------|---------|
| `app/src/main/kotlin/com/thirai/ThiraiWidgetProvider.kt` | Widget click handler, UI state updates |
| `app/src/main/kotlin/com/thirai/AdbController.kt` | ADB connection, mDNS discovery, command execution |
| `app/src/main/res/layout/widget_thirai.xml` | 4-poster horizontal grid layout |
| `app/src/main/res/xml/thirai_widget_info.xml` | Widget metadata (size, refresh rate) |
| `app/src/main/AndroidManifest.xml` | Permissions + widget receiver registration |
| `app/build.gradle.kts` | Dependencies (adblib, okhttp, coroutines, serialization) |
| `settings.gradle.kts` | Project name "Thirai" |
| `app/src/main/res/values/strings.xml` | App name "Thirai" |

---

## Remaining Work

| # | Task | Priority | Status |
|---|------|----------|--------|
| 1 | Fix build errors (Compose compiler residue, missing git repo) | High | Blocked |
| 2 | Initialize git repo (`git init`) for version calculation | High | Not started |
| 3 | Create `ThiraiConfigFetcher.kt` — fetch shows JSON from GitHub Gist | High | Not started |
| 4 | Update `ThiraiWidgetProvider.kt` to load shows from config instead of hardcoded URLs | High | Not started |
| 5 | Add image loading library (Glide) for widget poster images | High | Not started |
| 6 | Create GitHub Gist with shows JSON | Medium | Not started |
| 7 | Build and install APK on phone for testing | Medium | Blocked by #1 |
| 8 | Test ADB connection with actual TV | Medium | Blocked by #7 |
| 9 | Optional: Admin Activity UI for managing shows locally | Low | Not started |
| 10 | Replace default launcher icons with Thirai branding | Low | Not started |

---

## Setup Requirements (TV Side)

One-time setup on each Android TV:

1. Enable **Developer Options**: Settings → About → tap "Build Number" 7 times
2. Enable **ADB debugging**: Settings → Developer Options → USB Debugging → ON
3. Enable **Network debugging**: Settings → Developer Options → Network Debugging → ON
4. Note the TV appears as `_adb._tcp.` service on the local network

---

## Design Decisions

| Decision | Choice | Rationale |
|----------|--------|-----------|
| Widget vs App | Widget | Mother's mental model is home screen tapping (like speed dial), not app navigation |
| Local bridge vs Direct | Direct ADB | Eliminates need for extra hardware at mother's house |
| Static IP vs Auto-discovery | Hybrid (cache + mDNS) | Auto-discovery handles DHCP changes; cache keeps it fast |
| Cloud config source | GitHub Gist | Free, globally accessible, no server needed, instantly updatable |
| ADB library | `com.tananaev:adblib:1.3` | Lightweight, pure Java, well-maintained, available on Maven Central |
| Project template | Copy from `puraa` | Preserves familiar Gradle build environment and signing setup |
| App name | Thirai (திரை) | Tamil for "Screen" — short, elegant, culturally meaningful |
