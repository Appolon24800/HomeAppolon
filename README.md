# Homelab

A native Android dashboard for the Homer instance at
[home.appolon.dev](https://home.appolon.dev). The app fetches
`https://home.appolon.dev/assets/config.yml` at launch and renders the
services, links and info banner with Material 3. Content comes from the
YAML, look & feel is native Android.

## Features

- Runtime YAML config: edit Homer's `config.yml`, relaunch the app, done
- Instant paint from the cached config, refreshed in the background
- Pull-to-refresh, sticky group headers, search over names/subtitles/keywords
- Tap a service's star to pin it in Favorites. Pins persist on this device.
- Info banner polled from the `/message` endpoint while foregrounded
- Chrome Custom Tabs for opening services (shares browser logins)
- Edge-to-edge with punch-hole cutout handling, dynamic color, dark mode
- Offline fallback to the last saved config

## Native *arr client

The app also ships a native client for five *arr apps: Radarr, Sonarr,
Lidarr, Prowlarr, and Bazarr. Tap one of their services on the dashboard and
the app opens native Material 3 screens that talk to the server's REST API
directly, instead of loading the web UI in the WebView. Back navigation at
the root of the arr screens returns to the dashboard. The WebView remains
for every other service.

Highlights:

- Offline caching with queued writes. Screens read from an on-device cache
  first, so libraries open without a connection and keep their last synced
  state. Changes made offline are stored and sent, oldest first, when the
  connection returns or on pull-to-refresh. A change that fails five times
  moves to a review list instead of being dropped.
- Per-server proxy, including Orbot. A global proxy (HTTP or SOCKS5, with
  optional user and password) covers every server, and each server can
  override it. An Orbot toggle routes that server's traffic through Tor as
  SOCKS5 on 127.0.0.1:9050, with DNS resolved through the proxy. Servers
  behind self-signed certificates can be trusted per server, optionally by
  pinning the certificate's SHA-256 fingerprint.
- Background notifications without Google services. The app polls each server
  through WorkManager on an adaptive schedule: every 30 minutes by default,
  15 minutes when a release is due within six hours or a download is active,
  and up to 6 hours when nothing is pending. Notifications post through five
  channels (grabs, imports, failures, health, disk space), each togglable per
  server. No Firebase or Play Services anywhere in this path.
- Live updates while open. Each server keeps a SignalR connection (socket.io
  events for Bazarr) while its native screens are visible, so libraries,
  queues, and calendars update without a refresh. Connections close when you
  leave and reopen on return.

Setup takes three steps:

1. Copy the API key from the app's web UI, under Settings > General.
2. Tap the service on the dashboard. The first tap opens a setup sheet where
   you pick the app type, paste the base URL and API key, and run a
   connection test that reports the server's version. Base URLs keep their
   path prefix, such as `http://host:30025/radarr`.
3. Save, and the same tap opens the native screens from then on.

Several servers of the same type can be registered, with a chip row on every
arr screen for switching between them. API keys are stored encrypted with
the Android Keystore.

`docs/ARR_SETUP.md` is the step-by-step guide, and
`docs/ARR_FEATURE_MATRIX.md` lists the covered screens per app.

## Install with Obtainium

Add this repository URL in Obtainium:

```text
https://github.com/Appolon24800/HomeAppolon
```

The [latest release](https://github.com/Appolon24800/HomeAppolon/releases/latest)
contains a signed APK. Obtainium checks GitHub releases for updates. All releases
use the same signing key, so updates can install over an existing release build.
A debug build uses a different key and must be uninstalled first.

## Automatic releases

Every push to `main` runs unit tests, builds a signed release APK, and publishes
a GitHub release with the APK and its SHA-256 checksum. You can also run
`Build and release APK` manually from the repository's Actions tab on `main`.
Failed tests or builds do not publish a release.

Release versions use `1.0.<workflow run number>`. Android's version code uses
the workflow run number plus one, starting above the original version code of 1.
Rerunning a workflow keeps its version. Keep this workflow's run counter when
changing the release setup, or choose a higher version-code baseline.

The workflow needs these repository Actions secrets:

- `ANDROID_KEYSTORE_BASE64`, the base64-encoded contents of `keystore/homelab.jks`.
- `ANDROID_KEYSTORE_PASSWORD`, the keystore password.
- `ANDROID_KEY_ALIAS`, the release key's alias.
- `ANDROID_KEY_PASSWORD`, the release key's password.

Keep a private backup of the signing key. Do not commit it or replace it when
publishing updates. The repository's `well-known/assetlinks.json` also references
the existing key for passkey authentication.

## Build

Requirements: JDK 17+, Android SDK (path in `local.properties` or
`ANDROID_HOME`). The Gradle wrapper handles the rest.

```sh
./gradlew assembleDebug          # app/build/outputs/apk/debug/app-debug.apk
./gradlew assembleRelease        # requires the private release signing key
./gradlew testDebugUnitTest      # unit tests
```

For local release builds, put the signing key at `keystore/homelab.jks` and
create `keystore/signing.properties` with the following entries:

```properties
ANDROID_KEYSTORE_PASSWORD=your-keystore-password
ANDROID_KEYSTORE_ALIAS=your-key-alias
ANDROID_KEY_PASSWORD=your-key-password
```

The entire `keystore/` directory is ignored by Git. Environment variables with
these names override local properties. `ANDROID_KEYSTORE_FILE` can override the
key's path. CI sets `appVersionCode` and `appVersionName` through Gradle
properties; local builds default to version code 1 and version name `1.0`.

## Install & run on the emulator

```sh
~/Android/Sdk/emulator/emulator -avd homelab_test &
./gradlew installDebug           # or: adb install app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n dev.appolon.homelab/.MainActivity
```

## Project layout

- `app/src/main/kotlin/dev/appolon/homelab/data/` — YAML models & parser,
  repository (fetch + DataStore cache), message poller, search, icon URL
  resolution
- `app/src/main/kotlin/dev/appolon/homelab/ui/` — Compose UI (theme, home
  screen, components) and view model
- `app/src/main/kotlin/dev/appolon/homelab/arr/` — the seam that routes
  dashboard taps to the native arr shell (`ArrIntegration.kt`)
- `arr-*` modules — the native *arr client: networking, encrypted server
  registry, SQLite cache, action queue, shared UI shell, sync, and one
  feature module per app (`docs/ARR_ARCHITECTURE.md` has the module map)
- `app/src/test/` — unit tests over a trimmed fixture of the real YAML

Design doc: `docs/superpowers/specs/2026-09-11-homer-native-app-design.md`
