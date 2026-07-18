# Thirai (திரை)

**One tap on a home-screen widget starts a show on the TV.** No menus, no remote,
no smart-TV navigation. Built for someone who just wants to watch — and the person
who sets it up for them.

Thirai is an Android phone app whose real interface is a **home-screen widget** of
show posters. Tap a poster and the show plays on your Android TV. It talks to the
TV over the built-in **Android TV Remote** service (the same one your phone's
Google TV remote uses) — so there is **no developer mode, no ADB, no extra
hardware**.

→ Website & config builder: **https://gunasekar.github.io/thirai/**

## How it works

```
Phone widget  ──tap──▶  PlaybackService  ──Android TV Remote v2 (TLS)──▶  Android TV
                              │                                              │
   show list ◀── shows.json ──┘   (from a URL you control: gist, repo, …)   opens the
                                                                             deep link
```

- **Shows** come from a JSON file at a URL you set (a GitHub gist works well). The
  app caches it, so a brief network blip never blanks the widget.
- **The TV** is found by name over Wi-Fi ("Find my TV"), paired once with the
  6-character code it shows, then controlled over a trusted TLS channel.
- **Playback** opens the show's deep link and nudges it into playing; a "Play from
  start" button on the widget restarts the current show.

## Setup

1. **Point at your shows.** Build a `shows.json` (use the
   [config builder](https://gunasekar.github.io/thirai/)) and host it anywhere
   reachable. Set its URL as the build default in `.env` (see below), or leave it
   empty and set it in-app.
2. **Build & install** (`./gradlew :app:assembleDebug`, then install the APK).
3. **In the app:** Show source → Scan QR (or paste the URL) → **Find my TV** →
   pick it → enter the code shown on the TV.
4. **Add the widget** to the home screen. Every tap plays a show.

### `.env`

The default show-source URL is not hardcoded — it's injected from a gitignored
`.env` at build time:

```sh
# .env  (copy from .env.example)
export THIRAI_SHOWS_URL="https://…/shows.json"
```

A clone without `.env` builds with an empty default; the source is then set
entirely in-app.

## `shows.json`

```json
{
  "config": { "default_app_package": "in.startv.hotstar" },
  "shows": [
    {
      "id": "1",
      "title": "Siragadikka Aasai",
      "title_ta": "சிறகடிக்க ஆசை",
      "image_url": "https://…/poster.png",
      "deep_link": "https://www.hotstar.com/in/shows/…"
    }
  ]
}
```

- `title_ta` — Tamil title shown to the viewer (falls back to `title`).
- `image_url` — any reachable image; poster shown in the app and widget.
- `deep_link` — the streaming app URL to open.
- `app_package` (per-show, optional) — the app the deep link opens; used to time
  playback. Defaults to `config.default_app_package`.

Everything is remote-managed: edit the JSON at your source URL and every phone
picks it up on the next refresh.

## Architecture

The phone speaks the **Android TV Remote protocol v2** directly — TLS + protobuf,
a one-time PIN pairing, then a control channel. See
[`docs/`](docs/) for intent and requirements; the protocol client lives in
`app/src/main/kotlin/com/thirai/tv/`.

## Modules at a glance

| Package | What |
|---------|------|
| `com.thirai` | `MainActivity` (Compose host) |
| `com.thirai.ui` | Setup screen, shared components, theme |
| `com.thirai.widget` | Home-screen widget + playback foreground service |
| `com.thirai.config` | Show models + config fetcher |
| `com.thirai.tv` | Android TV Remote v2 client (pairing, control, TLS, framing) |
