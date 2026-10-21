# Native *arr client architecture

This document is the contract for every agent working on the native Radarr, Sonarr,
Lidarr, Prowlarr, and Bazarr screens inside the Homelab app. Read it fully before
writing code. It outranks personal preference and prior habits from other projects.

## Mission

The existing Homelab dashboard (module `:app`) stays the entry point. Today, tapping
a service opens it in a WebView (`WebScreen`). For the five *arr apps, tapping instead
opens fully native Material 3 screens that talk to the server's REST API directly.
Full feature parity with each app's web UI, including admin settings. Everything
works offline-first with Room caching and a replaying action queue. No Google Play
Services dependency anywhere in the arr modules.

## Source of truth

- API specs: `docs/arr-api/{radarr,sonarr,lidarr,prowlarr,bazarr}.openapi.json`
- Endpoint inventory grouped by tag: `docs/arr-api/ENDPOINTS.md`
- Screen parity definition: `docs/ARR_FEATURE_MATRIX.md`
- Live server credentials (gitignored, never referenced from code or committed):
  `local.arr-test.properties` at the repo root. Only integration and smoke test
  tasks may read it, via the `ArrTestServers` JUnit rule described below.

## Module map and dependency rules

```
:app                       Homelab dashboard. Owns MainActivity, HomeScreen, WebScreen.
  └─ integration point ONLY (see Integration contract). No arr business logic here.
:arr-core                  Result types, time, paging, shared value objects, test fixtures util.
:arr-net                   OkHttp engine: per-server client factory, proxy resolution,
                           self-signed trust, DNS pinning, base URL normalization.
:arr-data                  Server registry (DataStore + Keystore crypto), Room database,
                           offline action queue, connection manager building Retrofit instances.
:arr-ui-core               Theme tokens, navigation shell, LCE scaffolding, shared list and
                           detail frameworks, shared design components.
:arr-api-{radarr,sonarr,lidarr,prowlarr,bazarr}
                           Retrofit interfaces + DTOs + fakes + unit tests. One module per app.
:arr-feature-{radarr,sonarr,lidarr,prowlarr,bazarr}
                           ViewModels, repositories, Compose screens per app.
:arr-feature-servers       Server list, add/edit sheet, API key setup, proxy config UI,
                           notification preferences.
:arr-sync                  SignalR live connections, adaptive WorkManager polling,
                           notification emission.
```

Dependency direction: `app -> features -> {api module, arr-data, arr-ui-core, arr-core}`.
`api -> {arr-net, arr-core}`. `data -> {arr-net, arr-core}`. `sync -> {arr-data, arr-net, arr-core}`.
Never invert these. Never make `arr-ui-core` depend on an api or feature module.

## Hard rules for every task

1. Touch only the files your task owns. The brief names the exact module and file
   prefixes. Other modules are read-only reference.
2. No new Gradle dependencies. The version catalog is final. No Hilt, no RxJava,
   no Ktor, no Accompanist, no Firebase.
3. API keys, URLs from `local.arr-test.properties`, and credentials never appear in
   source files, test files, build files, or commit messages.
4. Kotlin style: 4 space indent, 100 column limit, wildcard imports only for
   `androidx.compose.material3.*` inside composables-heavy files. Match the style
   of `:app` sources (see `HomeScreen.kt`).
5. Every public type gets KDoc when non-obvious. Comments explain why, not what.
6. Strings go in each feature module's own `strings.xml`, English only, written for
   translation (no concatenation, full sentences, placeholders).
7. Min SDK 26. Any API guarded above 26 must carry a runtime check with graceful
   fallback. `java.time` is allowed everywhere.
8. Errors: network failures map to `ArrError` (sealed interface in `:arr-core`) with
   user-presentable message resources. Screens render LCE states, never crash on IO.
9. Build commands you may run (from repo root, your module only):
   `./gradlew :<module>:compileDebugKotlin`, `./gradlew :<module>:testDebugUnitTest`.
   Do not run `assembleDebug` on `:app`, do not run `lint`, do not commit, do not
   touch `git` at all. The orchestrator handles git.
10. Report at the end: files created or changed (paths), public API surface added,
    tests written and their pass status, open questions. Keep it under 40 lines.

## API client conventions (all `:arr-api-*` modules)

- Retrofit interface per OpenAPI tag group, named `{Tag}Api` (for example
  `MoviesApi`, `QueueApi`, `IndexerApi`). Operation methods take `@Path`, `@Query`
  and return `Call`-free suspend functions with `Response<T>` only when the status
  code carries meaning.
- DTOs: `@Serializable data class`, field names exactly as the spec's camelCase,
  `Json { ignoreUnknownKeys = true; explicitNulls = false; coerceInputValues = true }`
  shared instance from `:arr-net` (`ArrJson`). Never use Gson.
- Suffix DTOs with `Resource` only when the spec does. `@SerialName` whenever the
  wire name differs.
- The connection layer (in `:arr-data`) supplies: base URL with path prefix
  preserved (example `http://host:30025/radarr/`), `X-Api-Key` header, optional
  basic auth, optional user agent `HomelabArr/1.0 (android)`.
- Each api module exposes `object {App}Apis { fun movies(retrofit: Retrofit): MoviesApi ... }`
  factory plus a `Fake{App}` in-memory implementation of every interface for UI tests.
- Unit tests: MockWebServer serves JSON fixtures recorded from the spec examples,
  placed under `src/test/resources/fixtures/{app}/{tag}/`. One test per endpoint
  happy path plus one error path per tag group. Fixture JSON must not contain
  anything from the live server beyond what the public spec examples show.
- Live smoke rule for integration tasks only: a JUnit rule reading
  `local.arr-test.properties` (skip silently when the file is absent) named
  `ArrTestServers` in `:arr-core`. Never hardcode host, port, or key.

## Server registry and security (`:arr-data`)

- `ArrServer` model: id, app type (RADARR, SONARR, LIDARR, PROWLARR, BAZARR),
  name, base URL, API key (stored encrypted), optional basic auth, optional proxy
  config, flags for self-signed acceptance and Orbot routing, display order.
- API keys encrypt with AndroidKeyStore AES-GCM: a random key file in the app's
  private directory, wrapped by a Keystore key alias `arr_secrets`. No third
  party crypto library.
- Registry persistence: DataStore preferences, schema-versioned field prefixing
  (`arr_server_v1_`) so future migrations are additive. Offline cache uses the
  SQLite entity store described below, not Room (KSP is unavailable under AGP 9
  built-in Kotlin; do not add annotation processors).

## Proxy engine (`:arr-net`)

- `ProxyConfig`: type NONE, HTTP, SOCKS5; host, port, optional username and
  password. Resolution order per server: server config overrides global config
  overrides direct. Orbot toggle resolves to SOCKS5 on 127.0.0.1:9050 after an
  intent probe that Orbot is installed.
- Proxying must proxy DNS too (OkHttp `Proxy` plus a resolver that connects
  through the proxy; for SOCKS use `SocksProxy`-aware DNS resolution, for HTTP
  use the CONNECT behavior). Self-signed acceptance uses a per-server trust
  manager; optional SHA-256 fingerprint pinning.
- Unit tests with MockWebServer over an in-process SOCKS5 and HTTP proxy.

## Offline design (`:arr-data` + features)

- Storage is a hand-rolled SQLite entity store on `androidx.sqlite`, no
  annotation processing (AGP 9's built-in Kotlin forbids KSP). One database
  file `arr.db` with a generic table: `(serverId, entityType, entityId, json,
  fetchedAt)` plus a small set of typed index columns for hot filters. Entities
  serialize with the project `ArrJson`. `:arr-data` owns the store; feature
  modules register entity types and mappers at startup. Versioned migrations
  live in one file with a strict append-only migration list.
- Repositories read from the store, refresh from network, and write through.
- Action queue: `QueuedAction` persisted through the same store, holding a
  serialized request descriptor. Enqueued while offline or on failure, replayed
  oldest first when connectivity returns or on pull-to-refresh. Idempotency:
  actions carry client-side dedupe ids. Conflicts (409, 404 on replay) surface
  in a sync status screen, never silent data loss.

## Live updates and notifications (`:arr-sync`)

- Foreground: one SignalR hub connection per server (`{base}/signalr` for the
  four Servarr apps; Bazarr uses its socket.io events endpoint). Events fan out
  on a `SharedFlow<ArrEvent>`. Auto-reconnect with exponential backoff and a
  `BroadcastReceiver`-driven connectivity trigger.
- Background: WorkManager periodic work, default 30 minutes, never FCM. Interval
  adapts: 15 minutes when the calendar shows a release within 6 hours or a queue
  item is actively downloading, up to 6 hours when nothing is pending. Emits
  notifications through channels per event type (grab, import, fail, health,
  disk), fully configurable per server in `:arr-feature-servers`.
- Microsoft SignalR client is allowed as the only new runtime dependency set
  (already in the catalog with SLF4J). Exclude its transitive OkHttp so the
  project's OkHttp 5 wins.

## Integration contract with `:app`

One file, `app/src/main/kotlin/dev/appolon/homelab/arr/ArrIntegration.kt`, plus a
two-line wiring inside `HomeScreen.kt` at the existing `onServiceClick` call site:
when the tapped service URL matches a registered arr server (host plus path
prefix match, configurable per service card later), navigate to the arr shell
instead of `vm.openWeb`. Long press on a service always keeps the WebView option.
The arr shell hosts `:arr-feature-servers`' server home and per-app feature
navigation graphs. Back behavior: back from an arr shell returns to the dashboard.

## Definition of done for a task

1. Code compiles: `./gradlew :<module>:compileDebugKotlin` passes.
2. Tests pass: `./gradlew :<module>:testDebugUnitTest` passes, all green.
3. No TODO markers left in delivered files unless the brief allows them.
4. Report format followed, files listed with full paths.
5. No edits outside the owned file set. The orchestrator diffs every task.
