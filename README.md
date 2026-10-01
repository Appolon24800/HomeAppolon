# Homelab

A native Android dashboard for the Homer instance at
[home.appolon.dev](https://home.appolon.dev). The app fetches
`https://home.appolon.dev/assets/config.yml` at launch and renders the
services, links and info banner with Material 3 — content comes from the
YAML, look & feel is native Android.

## Features

- Runtime YAML config: edit Homer's `config.yml`, relaunch the app, done
- Instant paint from the cached config, refreshed in the background
- Pull-to-refresh, sticky group headers, search over names/subtitles/keywords
- Info banner polled from the `/message` endpoint while foregrounded
- Chrome Custom Tabs for opening services (shares browser logins)
- Edge-to-edge with punch-hole cutout handling, dynamic color, dark mode
- Offline fallback to the last saved config

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
ANDROID_KEY_ALIAS=your-key-alias
ANDROID_KEY_PASSWORD=your-key-password
```

The entire `keystore/` directory is ignored by Git. Environment variables with
these names override local properties. `ANDROID_KEYSTORE_FILE` can override
the key's path. CI sets `appVersionCode` and `appVersionName` through Gradle
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
- `app/src/test/` — unit tests over a trimmed fixture of the real YAML

Design doc: `docs/superpowers/specs/2026-09-11-homer-native-app-design.md`
