# Feature parity matrix

Definition of "every feature" per app, organized as deliverable screen groups.
Each feature task implements one screen group end to end: ViewModel, repository
method, Compose screens, Room cache entities, offline queue hooks where a write
exists, and unit tests. Endpoints refer to `docs/arr-api/ENDPOINTS.md` tags.

Legend: R = Radarr, S = Sonarr, L = Lidarr, P = Prowlarr, B = Bazarr.
Status: Delivered means the group is committed on this branch with its screens,
repository, cache, and tests.

All groups below are delivered. A cleanup and optimization wave runs after
integration and before the live smoke tests, covering dead code removal, list
and image tuning, release hardening, error and offline consistency, string
dedupe, test gap fill, and the accessibility and tablet pass (briefs in
`docs/ops/wave5-cleanup-briefs.json`).

## Shared shell (built once in :arr-ui-core and :arr-feature-servers)

| Screen group | Status |
| --- | --- |
| Arr home: per-server card list with health, disk space, upcoming count, queue count. Server switcher chip row on every arr screen. | Delivered |
| Server setup sheet: URL, API key (paste or scan QR of `apikey`), connection test, name, proxy assignment, self-signed toggle. First-run flow when a dashboard service is tapped and no server is registered. | Delivered |
| Server settings: edit, delete, reorder, proxy screens (global + per server), notification preferences per server and per event type. | Delivered |
| Offline sync status screen: pending queued actions, failures, retry controls. | Delivered |

## Radarr (R) screen groups

| Screen group | Status |
| --- | --- |
| 1. Movies library: poster grid and table views, filters (monitored, quality, genre, year, folder, tags, missing), saved custom filters, bulk selection edit, refresh and scan, movie editor. Tags: movies, moviefile. | Delivered |
| 2. Movie detail: overview, cast, files with quality, history, extra file import, rename preview, manual search and grab, interactive import, edit (monitoring, quality, path, tags), delete with file option. | Delivered |
| 3. Add new: search with year disambiguation, root folder, quality profile, monitoring strategy, minimum availability, tags, add and add+search. Discovery lists: popular, trending, recommendations. Tags: moviesearch, importlist, discover. | Delivered |
| 4. Collections: list, edit monitoring and quality, search. | Delivered |
| 5. Wanted and cutoff: missing list with search-on-add, cutoff unmet list, bulk search. Tags: wanted, cutoff. | Delivered |
| 6. Queue: grabbing, importing, failed items, remove with options (blocklist, re-search), retry import, per-item manual import. Tags: queue, command. | Delivered |
| 7. Activity history: event filters, blocklist management, failed manual imports. Tags: history, blocklist. | Delivered |
| 8. Calendar: month and agenda views, unmonitored toggle, per-day grouping by availability. Tags: calendar. | Delivered |
| 9. Settings: media management (naming tokens with live preview, root folders, import and file management toggles), profiles (quality definitions, delay profiles, release profiles), custom formats with scoring and specifications builder, indexers (categories, seed ratio, test), download clients (per client settings and test, remote path mapping), import lists (add, test, sync), connect notifications (event matrix, per-connection settings, test), metadata consumers, tags, general (host, security, proxy, logging, analytics, backup, updates), system (status, tasks, disk space, logs, updates, events feed). Tags: all remaining tags in the Radarr spec. | Delivered |

## Sonarr (S) screen groups

Identical shape to Radarr where the API matches, with series semantics:

| Screen group | Status |
| --- | --- |
| 1-8. Series library, series detail (seasons grid, episode file editor, per-episode history and search), add new with season picker and series type, wanted missing and cutoff unmet (season pass bulk), queue, history and blocklist, calendar with episode granularity. | Delivered |
| 9. Settings mirror of Radarr's list with Sonarr specifics: series and episode naming, release profiles, media management, providers. | Delivered |

## Lidarr (L) screen groups

| Screen group | Status |
| --- | --- |
| 1. Artists library: grid, filters, monitored state, edit, refresh, scan. | Delivered |
| 2. Artist detail: albums by release type, files, history, rename preview, monitored album toggles, manual search and interactive import. | Delivered |
| 3. Add new: artist search with disambiguation, album add by disambiguation page, root folder, quality and metadata profile, monitoring strategy, tags. Import lists and spotiy-style recommendations via lists endpoints. | Delivered |
| 4. Wanted: missing albums, cutoff unmet, bulk search. | Delivered |
| 5. Queue, activity history, blocklist: same contract as Radarr's. | Delivered |
| 6. Calendar: album release granularity. | Delivered |
| 7. Settings: media management with artist and album naming tokens, quality definitions, metadata profiles, delay profiles, release profiles, indexers, download clients, import lists, connect notifications, metadata (writing tags to files), tags, general, system. | Delivered |

## Prowlarr (P) screen groups

| Screen group | Status |
| --- | --- |
| 1. Indexers: card and table views, add from the prescreened definition list with dynamic field forms (info link, categories, seed ratio, caps), test, enabled toggle, priority, tags, bulk edit, categories manager, app sync status badges. Tags: indexer, indexerproxy, indexerflag. | Delivered |
| 2. Search: multi-indexer manual search across categories, results with age, size, seeders, indexer, grab to a download client or copy, search history with stats. Tags: search, history. | Delivered |
| 3. Stats: per indexer query counts, response time, grab ratio, user agent breakdown, charts by period. Tags: stats. | Delivered |
| 4. History: query log with filters and clear. Tags: history. | Delivered |
| 5. Settings: applications (sync to Radarr, Sonarr, Lidarr instances with sync preview), download clients, connect notifications, general (auth, proxy per indexer and app-level, logging), tags, UI culture. | Delivered |

## Bazarr (B) screen groups

| Screen group | Status |
| --- | --- |
| 1. Series and movies lists: subtitle status badges (missing, partial, complete, none wanted), filters, sort, blacklist status, mass editor. | Delivered |
| 2. Episodes and movie subtitle listing: per-episode subtitle entries with language, hearing impaired flag, score, actions (download, upload, delete), manual search across providers with scores, series details linking. Tags: episodes, movies, series, subtitles. | Delivered |
| 3. Wanted: missing subtitles with search and download, history of decisions. Tags: wanted, history. | Delivered |
| 4. Settings: languages, profiles (cutoff, must contain), providers with credentials and tags, notifications, scheduler intervals, UI, general. Tags: settings-related groups in the Bazarr spec. | Delivered |

## Cross-cutting deliverables

| Deliverable | Status |
| --- | --- |
| Full offline: every screen group reads from Room and survives process death; writes queue and replay. | Delivered |
| SignalR: library, queue, calendar screens update live while visible. | Delivered |
| Notifications: grab, import, fail, health, disk per server. | Delivered |
| Accessibility: content descriptions on all icon buttons, minimum touch targets 48dp, screen reader order validated. | Delivered |
| Tablet and landscape layouts for library, calendar, and detail screens. | Delivered |

## Delivery notes

- Radarr collections ships as the collections tab of the add new group
  (`RadarrCollectionsRepository`, `CollectionsTabContent`).
- Bazarr wanted and history ship inside the episodes group's screens
  (`WantedSubtitlesScreen`, `SubtitleHistoryScreen`).
- The shell's server-home slot is not filled by a per-app bundle, so a
  dashboard tap lands on the app's library screen instead.
