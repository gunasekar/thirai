# Releasing Thirai

How to build a signed, installable release APK — locally or from CI.

## One command (local)

```sh
make release        # (alias: make dist)
```

Runs `assembleRelease` and copies the result to `dist/thirai-<version>.apk`
(the version comes from the git tag — see [Versioning](#versioning)). `dist/`
is gitignored.

Install it on any phone by sideloading — no Developer Options or USB debugging
needed: transfer the `.apk` (Drive/email/USB), tap it, allow the opening app to
"install unknown apps", tap **Install**.

## Signing

Release signing is read from a **gitignored** `keystore.properties` at the repo
root (see `app/build.gradle.kts`). If it's absent, the release build is produced
**unsigned** and cannot be installed — `make release` guards against this.

Keep the keystore and `keystore.properties` **outside the repo** (e.g. an
encrypted location) and symlink `keystore.properties` into the repo root. Both
are gitignored, so they're never committed.

`keystore.properties` fields:

```properties
storeFile=/abs/path/to/thirai-release.keystore
storePassword=…
keyAlias=thirai
keyPassword=…
```

Create a keystore once:

```sh
keytool -genkeypair -v -keystore thirai-release.keystore \
  -alias thirai -keyalg RSA -keysize 2048 -validity 10000
```

Verify a built APK's signature:

```sh
$ANDROID_HOME/build-tools/<ver>/apksigner verify --print-certs dist/thirai-<version>.apk
```

### ⚠️ Back up the keystore

The keystore is Thirai's **permanent signing identity**. Lose it and you can
never ship an update that installs over an existing `com.thirai` — users would
have to uninstall and reinstall. Keep a copy somewhere safe and independent of
this machine.

## Versioning

**The git tag is the single source of truth.** There are no version numbers to
edit in `app/build.gradle.kts` — the build derives them from git:

- `versionName` — `git describe --tags` with the leading `v` stripped
  (`v0.2.0` → `0.2.0`; an untagged commit gets `0.2.0-3-gabc123`).
- `versionCode` — the commit count (`git rev-list --count HEAD`), so it always
  increases.

The Makefile's `dist/` filename and the CI release artifact are named from the
same `git describe`, so tag, app version, and APK name can never drift.

## Cutting a release (CI)

```sh
git tag v0.1.0      # SemVer, prefixed with v
git push --tags
```

Pushing the tag triggers [`.github/workflows/release.yml`](../.github/workflows/release.yml),
which builds the signed APK and publishes it to a GitHub Release named for the
tag. `workflow_dispatch` builds one without tagging (download from the run's
artifacts).

### CI secrets (once)

The workflow needs the keystore as repository secrets:

| Secret | Value |
|--------|-------|
| `KEYSTORE_BASE64` | `base64 -i thirai-release.keystore` |
| `KEYSTORE_PASSWORD` | the keystore password |
| `KEY_ALIAS` | e.g. `thirai` |
| `KEY_PASSWORD` | the key password |
| `THIRAI_SHOWS_URL` | *(optional)* default show-source URL baked into the build |

Set them with:

```sh
gh secret set KEYSTORE_BASE64 < <(base64 -i thirai-release.keystore)
gh secret set KEYSTORE_PASSWORD
gh secret set KEY_ALIAS
gh secret set KEY_PASSWORD
gh secret set THIRAI_SHOWS_URL
```
