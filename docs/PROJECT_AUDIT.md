# Project audit

## Scope

This audit started on 2026-11-16. The goal is to repair the existing Android app
and replace individual components where needed. The feature matrix records
implemented code. It does not prove that every screen works against a real server.

## Repairs

- Restored server management from the dashboard, including Add on an empty list
  and Edit on a server card.
- Invalidated cached HTTP clients when the server URL changes.
- Preserved encrypted proxy passwords when reordering servers.
- Kept stored certificate pins and self-signed trust choices in proxy settings.
  TLS settings are available when the server inherits its proxy too.
- Made an explicit direct connection override the global proxy.
- Prevented repeated Save taps from creating duplicate servers. Editing connection
  details cancels an old probe and clears its result.
- Rejected HTML login pages and responses naming another app in connection tests.
  Connection tests also read Bazarr's nested version value.
- Added Android notification permission controls and refresh permission state on
  return from system settings.
- Shared the persisted global notification switch with background polling.
- Resolved notification links to registered servers without an API key.
- Allowed failed or suppressed notification posts to retry on a later poll.
- Kept simultaneous live updates from different servers separate.
- Released retired live connections after an endpoint edit.
- Serialized requests to start and stop live connections. Backgrounding the app
  requests a stop, and server registry changes trigger connection reconciliation.
- Preferred exact server origins and the longest matching base path when opening
  a dashboard service. Query strings and fragments no longer count as server paths.
- Let later queued writes run after a transport exception. Cancellation preserves
  the queued action and its attempt count.
- Removed pending and failed writes when deleting their server.
- Cleared stale load errors after successful empty-list refreshes in the 21
  provider settings list screens across Radarr, Sonarr, and Lidarr. Two regression
  tests reproduced the error before the repair.
- Restored active-server selection on feature-owned routes by reading the
  registered `{serverId}` parameter. Settings routes now retain their server
  context, including percent-encoded ids.
- Serialized manual action retries and discard with automatic replay. Restoring a
  failed write and moving a terminal failure now use a SQLite transaction.
  Cancellation after restoration leaves the action queued with a fresh budget.
- Let the sync status screen restart a failed observation and clear its load
  error after a successful retry. Cancellation no longer leaves retry controls
  disabled.
- Waited for the navigation graph before focusing a cached server. A new Android
  test reproduced the startup crash when server data was already available.
- Added retry UI for null or thrown connection setup failures across libraries,
  add, detail, queue, calendar, search, provider settings, and activity screens.
- Retired settings clients and screen ViewModels when connection details change.
  Server names and order do not reset those screens. Global proxy changes affect
  servers that inherit that proxy.
- Used the same connection tester for automatic and manual setup. Automatic
  verification now respects stored basic auth, TLS, and proxy policy, including
  the persisted global proxy. Concurrent links cannot create duplicate servers.
- Canceled a previous dashboard connection request before opening another link.
  An old request's cleanup cannot clear the new request's progress state.
- Added the missing settings landing entries. Grouped links now cover every
  registered Radarr, Sonarr, and Lidarr settings page that needs only a server id.
  Prowlarr also links indexer proxies and system status. The route tests verify
  coverage and registration, including percent-encoded server ids.
- Added home menus for Prowlarr and Bazarr. Prowlarr can now open Search, search
  history and statistics, History, and Stats. Bazarr can now open Wanted, History,
  and Blacklist as well as both libraries.
- Used the UTF-8 URL decoding overload available on supported Android versions.
  The full lint scan found the previous overload required Android 13 despite the
  app supporting Android 10.
- Explained a missing passkey in the sign-in UI, rejected duplicate passkey
  attempts, and kept coroutine cancellation separate from authentication errors.
- Marked accessibility clicks as interaction in the WebView's text-input gate.
  Touch gating now runs in dispatchTouchEvent, so WebView keeps its own click
  detection and performClick behavior.
- Made polling read persisted proxy settings before the first request after a
  cold start. A settings read failure stops the request. Explicit direct
  overrides still win. Worker cancellation now propagates through poll and
  notification error handling.
- Kept the current server visible when its normal position falls in the switcher
  overflow menu. The row scrolls to the selected chip.
- Removed extra encoding of feature arguments during navigation. Feature routes
  and their dispatchers now preserve spaces, slashes, plus signs, literal percent
  escapes, and Unicode in server ids and other parameters.

## Local setup

The development machine has JDK 17, Android SDK platform `android-37.0`, build
tools `37.0.0`, current command-line tools, ffmpeg with libx264, and an Android 15
emulator named `homeappolon_audit`. The SDK path is in ignored `local.properties`.
The Java and Android environment is loaded by the user's shell profile.

Run `bash scripts/check_android.sh` to run unit tests and Android lint, then build
the debug app and Android test APK. The script limits Java memory and compiler
parallelism.

## Verification

The current worktree reports 4,277 unit tests, 19 skipped, and no failures or
errors. Unit tests, full debug lint, debug app, and Android test APK passed in
`build/audit/prowlarr-reference-current-verification.log`. The minified release,
including the background-sync repair, passed in
`build/audit/prowlarr-reference-release-verification.log`. `build/audit/verification-summary.json`
records result counts and current APK hashes.

R8 shrinking and release packaging passed. The local release uses the standard
debug keystore for emulator verification. Production signing configuration was
not changed. Full debug lint reports no errors, 33 warnings, and five hints.
Most warnings concern dependency updates. The others concern target SDK,
WebView, accessibility, and modifier conventions.

All 17 Android tests passed in
`build/audit/prowlarr-reference-all-android-tests.log`. The tests include setup
recovery, connection changes, overflow selection, encoded navigation, SQLite
migration, persistence of empty lookups, and a complete background polling pass.
The earlier 14-test run remains in
`build/audit/encoded-navigation-all-android-tests.log`.

The local fixtures require the expected API key on every API call. Automatic
setup routes also require an HttpOnly session cookie. The installed minified APK
passed automatic Radarr, Sonarr, and Lidarr setup and displayed their library
records. Lidarr displayed the fixture artist after API v1 verification.
Radarr movie detail and Sonarr series detail pages also rendered.
Sonarr and Radarr server switching selected the correct libraries. The fixture
returned successful empty queue and download-client states.

The grouped settings menus rendered, and previously unreachable Indexers pages
showed "No indexers" in debug and minified builds. The new Prowlarr home opened
its Search form. The minified Bazarr home opened its series library and Wanted
page, which showed "Nothing wanted". Fixture data does not prove compatibility
with every version of a real server.

Dashboard checks covered launch, server management, Add server, notification
settings, Android 15 notification permission, and the notification preview.
The dashboard configuration endpoint returned HTTP 200. This does not validate
service authentication.

Automatic setup tests select `MainActivity` explicitly for arbitrary localhost
HTTP URLs. The manifest registers the dashboard domain and `homelab` scheme.
Evidence screenshots are in `build/audit/automatic-*.png`,
`build/audit/current-radarr-*.png`, and `build/audit/final-release-*.png`.

A new Android regression reproduced extra encoding of a server id containing
spaces, a slash, a plus sign, a percent escape, and Unicode. Its failing log is
`build/audit/encoded-navigation-reproduction-tests.log`. Feature paths now pass
their existing encoding to Navigation. Registry decoding preserves that path,
and feature dispatchers use decodedPathSegments to read each argument once.
The focused regression and the full 14-test Android suite passed after the
repair. The current minified APK also displayed the cached Lidarr artist with
the fixture server stopped.

The queue checks cover overlapping manual and background retries, discard during
replay, recovery after cancellation, transaction rollback, and sync-screen
observation recovery. The settings and home navigation tests verify registered
routes, complete settings entries, and percent-encoded server ids.

## Remaining checks

- Complete account-owner approval for Pocket ID. The real request renders, but
  approval was unavailable during this run. HTTP, cookie, polling, cancellation,
  browser-approval routing, and the public domain association are verified.
- Real Radarr and Sonarr libraries and Sonarr episode details render in the app.
  The live smoke suites for all five services pass. Further production write
  checks must use reversible changes that do not affect the media library.
- Review dependency updates separately. Update notices do not prove a runtime defect.

## Latest repairs and live checks

The existing Autoarr configuration at
`/home/appolon/.config/autoarr/config.json` supplies a working Lidarr connection.
The ignored `local.arr-test.properties` now contains that connection with file
mode `0600`. Secrets are not included in this audit. Lidarr reports version
3.2.1.5196. All 11 read-only live smoke tests passed without skips in
`build/audit/lidarr-live-expanded.log`. They cover status, root folders, artists,
artist detail and albums, queue status and paging, history paging, calendar,
quality and metadata profiles, and configured providers.

Notification links now encode a server id as one path argument and decode it
once. Reserved characters, literal plus signs, and percent escapes retain their
original values. Notification creation and app routing share the same codec.

Successful quality, metadata-profile, and tag lookups now replace their cached
collection. Deleted entries no longer return from the cache, and a successful
empty response clears old entries. SQLite commits replacement rows and their
refresh time together. Schema version 2 adds a separate refresh-time table so an
empty collection retains its TTL after restart. Existing entity rows stay in
place during migration. Tests cover deletion, empty results, isolation, and
rollback. Focused module checks passed in
`build/audit/cache-replacement-tests-2.log`.

The current full-tree verification passed with 4,277 unit tests, 19 skips, and no failures or errors. Debug lint reports zero errors, 33 warnings, and five hints. The debug APK and Android test APK built successfully. All 17 Android tests passed, including the version 1 database migration and empty-refresh persistence check.

The emulator used an ADB reverse for the local proxy and loaded the real Lidarr library, artist detail, queue, and history screens. Screenshots are in `build/audit/live-lidarr-*.png` and `build/audit/final-release-live-lidarr.png`. The proxy rejects the emulator alias `10.0.2.2` as an invalid hostname, so the UI check used the same localhost hostname as the working credential configuration.

Background sync now uses `arr_sync.preferences_pb`, the extension required by AndroidX Preferences DataStore. The previous `.preferences.pb` name caused WorkManager to fail before polling. A unit regression test verifies persistence across reopening the store.

The final minified release passed and displayed the real Lidarr library after
installation. The Android worker regression enqueued one immediate polling pass
and confirmed success. The periodic schedule remained enabled. The emulator's
Live Lidarr entry uses `http://127.0.0.1:30071/lidarr/` through an ADB reverse on
port 30071. Recreate that reverse after restarting the emulator to use the same
local proxy. The API key remains in encrypted app storage and the ignored local
credential files.

## Bazarr settings cache repair

Bazarr settings previously kept old rows after a successful empty list response.
A language removed on the server, a finished job, or a deleted backup could
remain visible after Refresh. The loader now replaces complete collections with
one SQLite transaction, including empty results. It records refresh time for
empty collections and respects their cache TTL. A missing list, invalid list
value, or nonempty list without usable rows returns a parse error and preserves
the cached data and timestamp. Legacy list-wrapper keys still work.

The two failing regressions are recorded in
`build/audit/bazarr-empty-settings-reproduction-2.log`. Five added regressions
cover settings collections, empty-cache TTL, malformed responses, fallback list
wrappers, and screen transitions. All 165 Bazarr module tests pass.

The Android emulator verified populated, empty, malformed, and recovered
language settings through the app's Refresh and Retry buttons. The minified
release passed the same transitions. Screenshots are in
`build/audit/bazarr-release-settings-*.png`. The local fixture uses a test API
key and requires no real Bazarr credentials. It is stopped after verification.
All 17 Android tests pass, and current debug lint reports no errors, 33 warnings,
and five hints. The summary JSON records the current APK hashes.

## Atomic library refreshes

Radarr, Sonarr, and Lidarr library refreshes now replace complete collections
with a SQLite transaction. The previous row-by-row updates could delete or
change some cached records before a later write failed. Storage failures now
return an error while the old library and refresh time remain intact.
Cancellation propagates and preserves the old library as well. Successful
refreshes use one fetch timestamp for all records, including empty libraries.
This also fixes the future wall-clock timestamps written when Sonarr and
Lidarr updated existing records through individual puts.

The original partial-cache failures and Sonarr timestamp failure are recorded
in `build/audit/library-refresh-reproduction.log`. Fourteen new unit regressions
cover complete observer snapshots, failed writes, cancellation, and timestamps.
The Android test uses real SQLite triggers to reject replacement inserts. It
verifies that all three provider repositories retain the old rows and refresh
time, then checks empty-library timestamps across database reopen. All 17
Android tests pass in `build/audit/atomic-library-all-android-tests.log`.

The current debug and minified release builds passed. The live Lidarr library
also loaded in the updated debug app. Current artifact hashes are in the
verification summary JSON.

## Overlapping library refreshes

Concurrent refresh calls on the same Radarr, Sonarr, or Lidarr library repository
could finish out of order. A slow older response could replace a newer library
snapshot. Each repository now holds a coroutine mutex through the request and
cache commit. Cancellation releases the mutex for the next caller.

The three original failing regressions are recorded in
`build/audit/overlapping-library-refresh-reproduction.log`. Tests hold the first
response while a second call starts, then verify that the final library contains
the newer response. Further tests cancel an active request and confirm the
waiting refresh completes.

Library ViewModels now share an active refresh job across repeated refresh
gestures. This also covers the initial load. Refresh works again after the job
finishes, and the refresh indicator remains active for that request. Nine new
unit regressions cover ordering, cancellation, and repeated UI requests. The
provider-module checks, full debug verification, all 17 Android tests, and the
minified release build pass. Current artifact hashes are in the summary JSON.

## Prowlarr reference lists

Prowlarr's tags, provider schemas, default categories, app profiles, proxy schemas,
and application schemas share a TTL loader. A successful empty response
previously returned stale cached rows. The loader now replaces the complete
collection, including an empty result, and retains its refresh time. Fresh empty
collections skip the network until their TTL expires. Provider field redaction
still applies to cached schema rows.

The three original failing regressions are in
`build/audit/prowlarr-empty-reference-reproduction.log`. They cover removed tags,
provider definitions, and default categories. The shared full-list reconciliation
helper also uses atomic replacement for indexers, proxies, applications, and tags.
The focused tests and full debug verification pass. All 17 Android tests pass in
`build/audit/prowlarr-reference-all-android-tests.log`. The current release build passed. The installed release app loads the live
Lidarr library.


## Add-new catalogs and detail profiles

Radarr, Sonarr, and Lidarr Add-new catalog refreshes now replace complete
collections, including successful empty responses. Removed root folders,
profiles, tags, and import lists no longer remain in the cache. Empty results
retain their TTL, so opening a screen again does not repeat a successful request.
Sonarr and Lidarr detail profile pickers use the same behavior. Sonarr's explicit
Refresh bypasses the catalog TTL.

Sonarr import-list exclusion refreshes retain temporary negative-ID rows only
while their matching create action remains queued. A server response that
acknowledges the TVDB ID removes the temporary row. Discarding the queued create
also removes it on the next refresh.

The full debug check passed in `build/audit/catalog-full-verification.log`.

## Add-series and Collections cache replacement

Sonarr's Add-series library refresh and Radarr's Collections refresh still
deleted old cache rows before inserting a replacement. A later write failure
could leave an incomplete collection. They now commit complete replacements
through SQLite, retain refresh times for empty responses, and return storage
errors without deleting the old cache. Cancellation propagates to the caller.
Each repository serializes its refresh calls. Radarr's collection movie lookup
also replaces the complete library snapshot.

Five original failures appear in
`build/audit/add-browse-refresh-reproduction.log` and
`build/audit/add-series-refresh-reproduction.log`. The focused checks pass in
`build/audit/add-browse-refresh-fixed.log`. The Android persistence regression
now covers Add-series and Collections alongside the three library repositories.

The complete debug check passed in
`build/audit/catalog-and-browse-full-verification.log` with 4,294 tests,
19 skips, and no failures or errors. All 17 Android tests passed in
`build/audit/catalog-and-browse-all-android-tests.log`. The minified build and
11 live Lidarr API tests passed in
`build/audit/catalog-and-browse-release-live-verification.log`.

## Lidarr artwork URLs

The live library displayed monograms even for artists with artwork. The artist
poster and artist detail URL builders added a slash before paths that already
start with a slash. The real server returned 404 for the doubled slash and
200 with an image body for the corrected path. Both builders now join the base
URL directly with the API path. Tests cover artist posters, banners, fanart,
and album covers with and without a server path prefix or a trailing slash.


The artwork fix passed the full debug check with 4,297 unit tests, 19 skips,
and no failures or errors. All 17 Android tests passed. Debug and minified
release builds displayed the real artist artwork. Screenshots are
`build/audit/artwork-debug-live-lidarr.png`,
`build/audit/artwork-debug-artist-detail.png`, and
`build/audit/artwork-release-live-lidarr.png`. The sanitized live HTTP comparison
is in `build/audit/lidarr-artwork-live-probe.json`.

## Detail data after a library refresh

The real Lidarr artist detail screen showed an empty album list while its
header reported 62 albums. Its album API returned all 62. A fresh artist row
from the library caused the detail ViewModel to skip albums, tracks, and
history. Sonarr used the same freshness check to skip episodes and history.
Both screens now reuse fresh parent rows while fetching their related data
on every opening. Their refresh indicator stays active through these requests.
Previously cached detail rows remain available if a network request fails.

Four failing regressions are in
`build/audit/fresh-library-detail-reproduction.log`. They cover missing detail
rows and stale related data while the parent library row remains fresh.

The corrected debug app loaded The Alchemist's albums immediately after
opening its detail page from a fresh library. It displayed album artwork and
track counts. Evidence is in `build/audit/detail-debug-live-albums.png`. The
full debug check passed with 4,301 unit tests and 19 skips. All 17 Android
tests passed in `build/audit/detail-all-android-tests.log`.

The verification script now defaults to one Gradle worker after concurrent
feature compilation and the long-running emulator exhausted the host's
available memory. `ANDROID_CHECK_WORKERS` permits an override. Restarting the
Gradle process and emulator restored enough memory for the remaining checks.
The emulator's existing app data survived the restart, and port 30071 was
forwarded again.


## Pocket ID login completion work

Pocket ID HTTP requests now run through a client with an injectable cookie store.
Cancellation aborts active network calls, and late responses cannot save a session
after cancellation. Device approval respects the advertised polling interval.
Only a long-poll timeout or HTTP 202 counts as a pending approval. Connection
failures reach the login UI. Authentication redirects are rejected, codes are
encoded as individual path segments, and a successful user response without an
access-token cookie does not count as a successful login.

Cookie restoration now adds Path=/ and Secure for prefixed HTTPS cookies. Login
waits for WebView to accept each cookie before saving the account. A challenge
session cookie alone no longer closes the web-login page as though authentication
had succeeded. Closing login cancels foreground authentication and device polling.
Other login controls remain disabled while a passkey request is active.

Fourteen new unit regressions pass, and the full debug verification passed in
`build/audit/pocketid-current-verification.log`. The final unit total is 4,315
with one skip and no failures or errors. All 18 Android tests passed in
`build/audit/pocketid-all-android-tests.log`, including actual WebView restoration
of __Host- cookies. The minified release passed in
`build/audit/pocketid-release-verification.log`.

The server is reachable from pc-appolon, rather than directly from this VM.
A remote read-only audit found all five app credentials in that machine's ignored
`/home/appolon/Projects/HomeAppolon/local.arr-test.properties`. The real Radarr
7.0.2.10888 and Sonarr 4.0.20.3014 passed 20 read-only HTTP checks with no skips.
Their libraries contain 1,015 movies and 241 series. The checks include status,
folders, library, queue, history, calendar, profiles, tags, and populated details.
These HTTP checks do not replace the app's real DTO and UI verification.

The auth host returned 404 for /.well-known/assetlinks.json. The local association
document now includes the verified certificate of the APK used in this VM, while
preserving its existing certificate entries. Deployment and secure VM-local test
connection setup are delegated to pc-appolon. Their final results are pending.


## Real server access and Pocket ID deployment

The pc-appolon agent deployed the assetlinks document and a narrow robots
exception. Public verification returned HTTP 200 with application/json, no
redirect, and the expected application ID and all three signing certificates.
Nginx syntax checks passed before reload. The agent updated SERVER.md on the NAS.

All five test credentials are now available in the VM's ignored
`local.arr-test.properties`, mode 0600. Loopback relays use private Tailscale
connectivity. Existing Lidarr entries remain intact. Radarr's six, Sonarr's six,
Prowlarr's three, and Bazarr's three live smoke tests all passed with no skips.
Logs are `build/audit/radarr-live-final.log`,
`build/audit/sonarr-live-final.log`, `build/audit/prowlarr-live-final.log`, and
`build/audit/bazarr-live-final.log`.

Pocket ID 2.19.3 accepts its HTTPS origin and has no configurable Android-origin
allowlist. The passkey button now opens the system browser with a device-approval
request. The app receives its session through the existing device exchange.
This avoids an unsupported native assertion origin and preserves server checks.
A real device-approval request rendered in the emulator. Account-owner approval
is still required to verify a complete successful login.


The emulator was provisioned through the production connection tester using a
private setup input that the instrumentation test deleted after use. The real
Radarr library opened with movie posters, and the real Sonarr library opened
with series and episode records. The Sonarr detail screen rendered episode
files and sizes. Evidence is in `build/audit/live-sonarr-detail-current.png`;
Radarr was verified on the same run.

The Pocket ID passkey button now starts the device approval request in the system
browser, which preserves Pocket ID's HTTPS WebAuthn origin. A real request
rendered with a QR code and approval code in the emulator. Account approval was
not available during this run, so the final session exchange remains the only
user-dependent check.


The final minified release after the browser approval change passed in
`build/audit/final-release-browser-flow.log` and was installed on the emulator.
The private server provisioning test passed and removed its input file.
The account owner deferred Pocket ID approval, so no successful real-user
assertion or account session is claimed.


## Final current verification

The final local run reports 4,315 unit tests, one skip, and no failures or
errors. Debug lint reports 34 warnings and five hints. The live API suites pass
with no skips: Radarr 6, Sonarr 6, Lidarr 11, Prowlarr 3, and Bazarr 3. The
private emulator provisioning test passed, and the real Radarr and Sonarr library
and detail screens rendered.

The browser approval change passed debug tests, Android packaging, lint, and the
minified release build. Pocket ID's device request rendered in the emulator.
The only deferred check is account-owner approval of that request, which the user
was unable to perform during this run.

The latest Android regression pass succeeded in
`build/audit/final-android-browser-flow-tests.log`. The optional private provisioning
check skipped because its input file had already been deleted; it passed separately
when the file was present. The current release APK was restored to the emulator.
