# Thirai — Product Requirements (PRD)

Intent, design, and product requirements for Thirai, in one place. For usage and
setup, see the [README](../README.md).

---

## Intent

### Why it exists

Some people watch the same few shows every day but can't — or would rather not —
navigate a smart TV: the apps, menus, search, and profile screens are a wall.
They need someone to start playback for them, every single time. Thirai removes
that dependency.

It was built first for one such viewer (a parent who watches a handful of Tamil
serials daily), but the shape is general: **quick, fixed shortcuts to a few shows
on the TV, for anyone who wants one-tap access instead of app navigation.**

### What it does

A home-screen widget shows posters of the chosen shows. Tap one, and it plays on
the TV. That's the whole interaction.

- No app to open
- No menus to navigate
- No settings to touch day to day
- Just: **tap → watch**

### What it is not

- Not a media player — the TV plays the content.
- Not a general remote — it does one thing.
- Not a smart-home system — no voice, automation, or scheduling.
- Not a general-purpose app — it lives on the home-screen widget.

### Success criteria

Tap a poster; the show plays on the TV within a few seconds, every time, without
help.

---

## Design

### Principles

1. **One tap, nothing else.** The widget is the entire day-to-day interface; the
   app screen only exists to set things up.
2. **Remotely maintained.** The show list is a JSON file at a URL the owner
   controls. Edit it once and every phone picks it up on the next refresh — no
   rebuild, no touching the viewer's phone.
3. **No extra hardware.** Just the phone and the TV on the same Wi-Fi.
4. **Platform-agnostic.** Hotstar today, YouTube or Netflix tomorrow — the same
   widget and the same tap, because the transport is generic.

### How it works

```
Phone widget ──tap──▶ PlaybackService ──Android TV Remote v2 (TLS)──▶ Android TV
                            │                                             │
 show list ◀── shows.json ──┘  (fetched from a URL the owner controls)   opens the
                                                                         deep link
```

- **Transport — Android TV Remote v2.** Thirai speaks the same protocol the
  phone's built-in Google TV remote uses: a one-time PIN pairing over TLS
  (certificate-based), then a trusted control channel. It needs **no developer
  mode and no ADB** — only the Android TV Remote service, which every Android TV
  runs. (An earlier version used ADB over port 5555; modern TVs don't reliably
  expose it, which is why the protocol client replaced it.)
- **TV discovery.** The TV is found by name over mDNS ("Find my TV"), paired
  once, and remembered. Its address can change; pairing (a certificate) does not.
- **Playback.** Opens the show's deep link, waits for the streaming app to reach
  the foreground, then nudges it into playing. A widget button restarts the
  current show from the beginning on demand.
- **Config source.** The show list URL is a user setting — seeded at build time
  from `.env` (`THIRAI_SHOWS_URL` → `BuildConfig`), shareable to another phone by
  QR, and cached locally so a network blip never blanks the widget.

### Code layout

| Package | Responsibility |
|---------|----------------|
| `com.thirai` | `MainActivity` (Compose host) |
| `com.thirai.ui` | Setup screen, shared components, theme |
| `com.thirai.widget` | Home-screen widget + playback foreground service |
| `com.thirai.config` | Show models + config fetcher |
| `com.thirai.tv` | Android TV Remote v2 client (pairing, control, TLS, framing) |

---

## Requirements

### R1 — One-tap playback from a home-screen widget
The primary interaction is a home-screen widget; the viewer never opens an app.
The widget shows a grid of posters; tapping one starts playback on the TV.

### R2 — Direct phone-to-TV control, no extra hardware
The phone controls the TV over the local Wi-Fi via the Android TV Remote v2
protocol. No local server, no Raspberry Pi, no always-on computer. Pairing is a
one-time on-screen PIN; no developer mode or ADB.

### R3 — Automatic TV discovery
TVs are discovered by name over mDNS (`_androidtvremote2._tcp`) and selected from
a list. A manual address is available as a fallback. Trust is tied to the paired
certificate, so a changed IP does not require re-pairing.

### R4 — Remote, shareable show management
Shows (title, Tamil title, poster, deep link, app package) live in a JSON file at
a URL the owner controls (a GitHub gist works well). The URL is configurable
in-app and shareable by QR. The app caches the last good copy.

### R5 — Multi-platform deep links
Any streaming app that handles Android deep links works — Hotstar, YouTube,
Netflix, Prime — by setting the show's `deep_link` (and optional `app_package`).
No app change needed to add a new platform.

### R6 — Lightweight
No always-on service. The app is dormant between taps; a widget tap runs a short
foreground service only for the few seconds a launch takes.

### Non-functional
- **Reliability.** Cached config + certificate-based pairing that survives IP
  changes; graceful, legible status in the app.
- **Compatibility.** Android TV with the Remote service (effectively all);
  phone on Android 8.0+ (API 26+) for widget support.
