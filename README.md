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

## Build

Requirements: JDK 17+, Android SDK (path in `local.properties` or
`ANDROID_HOME`). The Gradle wrapper handles the rest.

```sh
./gradlew assembleDebug          # app/build/outputs/apk/debug/app-debug.apk
./gradlew assembleRelease        # signed with keystore/homelab.jks (store/key
                                 # password "homelab", not committed)
./gradlew testDebugUnitTest      # unit tests
```

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
