# Homelab — Native Android Dashboard Design

Date: 2026-09-11
Status: Approved (Approach B — native reinterpretation)

## Goal

A native Android app (Kotlin + Jetpack Compose) that fetches the Homer
`config.yml` from `https://home.appolon.dev/assets/config.yml` at runtime and
renders the homelab dashboard with native UI. Content comes from the YAML;
look & feel is Material 3 (the YAML's color palettes and web-only options are
deliberately ignored). The app must feel native and fast, run full-screen
edge-to-edge, and handle the camera punch-hole cutout.

## Decisions

| Decision | Choice |
|---|---|
| Platform | Native Android, Kotlin + Jetpack Compose, single module |
| Config source | Runtime fetch of `config.yml` (public, verified), cached locally |
| Fidelity | Approach B: content from YAML, theming from Material 3 |
| Opening services | Chrome Custom Tabs (shares browser auth sessions) |
| Search | In-memory filter over name + subtitle + keywords |
| Message banner | Poll `/message` (JSON, public) every `refreshInterval` while foregrounded |
| minSdk / target | 29 / 36 |
| Distribution | APK built on this machine; release-signed with a local keystore |
| Testing on desktop | Android emulator (KVM available) with a Pixel-profile AVD |

## UX

Single-screen app, no tabs/drawer:

- **LargeTopAppBar**: title = `config.title` ("Homelab"), subtitle beneath in
  secondary style = `config.subtitle` ("La Plateforme"). Collapses on scroll.
- **Actions**: search icon; overflow menu (⋮) with the YAML `links` entries.
- **Search**: tapping search replaces the top bar with a back-arrow +
  text field; the list filters instantly (case- and diacritic-insensitive)
  against `name`, `subtitle`, `keywords`. Purely in-memory.
- **List**: one `LazyColumn`. Group name = sticky section header. Service =
  Material 3 list item — service logo (leading, Coil), name (headline),
  subtitle (supporting). Tap → Chrome Custom Tab.
- **Message banner**: first list item, from `/message` (`mapping.title` →
  title, `mapping.content` → body). Dismissible; dismissal remembered per
  message content while the app lives. Polls only while foregrounded.
- **Pull-to-refresh** refetches the config.
- **Offline**: on failed fetch with cache, show cached config + subtle
  "offline" indicator.

## Config mapping

| YAML | Used for |
|---|---|
| `title`, `subtitle` | Top bar text |
| `services[].name/icon` | Sticky group headers |
| `services[].items[]. name/logo/subtitle/keywords/url` | List rows, search, launch target |
| `links[].name/icon/url` | Overflow menu entries |
| `message.{url,mapping,refreshInterval}` | Banner polling |
| `colors`, `theme`, `defaults`, `tag`, `header`, `footer`, `logo`, `target` | Ignored (theming/web-only) |

Relative `logo` paths (e.g. `assets/icons/kavita.svg`) resolve against
`https://home.appolon.dev/`. Logos are mostly SVG/PNG from CDNs → Coil with
the SVG decoder; loading/error fallback is a monogram of the service name.

## Architecture

```
data/
  HomerConfig.kt        @Serializable models + Yaml lenient decoding
  ConfigRepository.kt   OkHttp fetch → kaml parse → DataStore cache
  MessagePoller.kt      periodic JSON fetch, lifecycle-scoped
  IconUrlResolver.kt    relative → absolute URL
  Search.kt             normalized filtering
ui/
  HomeViewModel.kt      StateFlow<HomeUiState>; starts/stops poller
  HomeScreen.kt         Scaffold + top bar + list + search + banner
  components/           ServiceRow, GroupHeader, MessageBanner, Monogram
  theme/Theme.kt        Material 3, dynamic color (Android 12+), day/night
MainActivity.kt         enableEdgeToEdge + splash; insets = safeDrawing
```

- State: `HomeUiState(config, query, message, messageDismissed, offline)`
- Launch path: render cached config instantly → background refresh → swap.
- Polling: screen lifecycle notifies VM on start/stop; VM runs a
  `while(isActive) { fetch; delay(interval) }` job.

## Native feel specifics

- `enableEdgeToEdge()`; Scaffold content insets = `safeDrawing` so the
  punch-hole cutout, status bar, and gesture nav never overlap content.
- Material 3 ripples, sticky headers, pull-to-refresh, splash screen
  (flask adaptive icon, monochrome layer for themed icons).
- Dynamic color on Android 12+, otherwise a neutral dark-friendly scheme;
  follows system dark mode.

## Build

- Gradle 9.7.1 wrapper, AGP 8.13.x, Kotlin 2.2.x, Java 25 (system).
- SDK at `~/Android/Sdk` (cmdline-tools, platform-tools, emulator,
  platforms;android-36, build-tools;36.0.0, system image google_apis x86_64).
- Debug + release builds; release keystore generated locally, gitignored.

## Testing

- Unit tests (JUnit4 + kotlin.test) with a trimmed fixture of the real YAML:
  parsing (unknown keys tolerated), relative-icon resolution, search
  normalization (diacritics), message JSON mapping.
- Manual verification on the emulator via `adb shell screencap` and logcat.

## Out of scope (v1)

PWA/web build, in-app WebViews, service health checks, widgets, editing the
config from the app, authentication handling in-app.
