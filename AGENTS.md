# AGENTS.md — working notes for Thirai

Conventions an agent (or contributor) should follow when changing this repo.

## Show config (the gist)

Shows are **not** hardcoded in the app. They live in a JSON file the app fetches
at runtime (the "Show source" URL, shared to phones via a setup QR). The current
list is a GitHub **gist**, which is itself a git repo — clone it over HTTPS to
edit files (including binary posters, which `gh gist edit` can't upload):

```
git clone https://gist.github.com/<gist-id>.git
# edit shows.json / add posters, then:
git add . && git commit -m "…" && git push \
  "https://x-access-token:$(gh auth token)@gist.github.com/<gist-id>.git"
```

Always reference the **unpinned** raw URL (no commit hash), which always serves
the latest and never changes:

```
https://gist.githubusercontent.com/<user>/<gist-id>/raw/<file>
```

The pinned URL (`…/raw/<hash>/<file>`) that the gist's "Raw" button gives you
changes on every edit — don't use it. Note the unpinned URL is CDN-cached for a
few minutes, so edits take ~5 min to reach the app.

### shows.json shape

A flat list; every show is self-describing (no app-level defaults):

```json
{
  "shows": [
    {
      "id": "1",
      "title": "Siragadikka Aasai",
      "title_native": "சிறகடிக்க ஆசை",
      "image_url": "https://gist.githubusercontent.com/…/raw/siragadikka-aasai.png",
      "deep_link": "https://www.hotstar.com/in/shows/siragadikka-aasai/1260129356",
      "app_package": "in.startv.hotstar",
      "home_link": "https://www.hotstar.com/in",
      "status": "enabled"
    }
  ]
}
```

- `title_native` is what the viewer sees; `title` is a fallback/label.
- `app_package` + `home_link` are **per-show**, so one list can mix apps
  (Hotstar, Netflix, Prime…). `home_link` is the app's home URL — Thirai opens it
  before a show to reset the back stack (see the player back-stack note below).
- `status`: `enabled` (default; `active` also accepted) shows the entry; anything
  else (e.g. `disabled`) parks it — kept in the JSON but hidden from app + widget.

## Posters

Every poster must be the **same shape and size** so the app list and the
home-screen widget (whose tiles are locked to a 3:4 ratio) render uniformly.

**Standard: 600×800 PNG, centre-cropped to 3:4.**

Use the script — never hand-resize:

```
scripts/make-poster.sh <input-image> <name>.png
```

It scales to fill and centre-crops to exactly 600×800, then strips metadata.
600px is 2× the app's display size (~300px), so it stays crisp without bloating
the gist. Name files by show slug (e.g. `neeya-naana.png`).

## Player back stack

Opening a show sends only a deep link to the TV; the Android TV Remote protocol
gives no control over the launch intent's flags, so shows would stack as new
player activities. Thirai works around this by launching the show's `home_link`
first (the app's single-task home clears players above it), then the show — see
`TvController.play`. Keep `home_link` set per show for this to work.

## Releases

Version is the git tag (no hardcoded version numbers). Tagging `vX.Y.Z` and
pushing triggers the signed-APK GitHub release:

```
git tag vX.Y.Z && git push origin vX.Y.Z
```

Public releases build with an empty default show-source (the `THIRAI_SHOWS_URL`
CI secret is intentionally unset) so no personal list is baked in. Local builds
read `.env` (`THIRAI_SHOWS_URL`) for convenience.
