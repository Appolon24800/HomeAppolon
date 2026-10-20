# Feature parity matrix

Definition of "every feature" per app, organized as deliverable screen groups.
Each feature task implements one screen group end to end: ViewModel, repository
method, Compose screens, Room cache entities, offline queue hooks where a write
exists, and unit tests. Endpoints refer to `docs/arr-api/ENDPOINTS.md` tags.

Legend: R = Radarr, S = Sonarr, L = Lidarr, P = Prowlarr, B = Bazarr.

## Shared shell (built once in :arr-ui-core and :arr-feature-servers)

- Arr home: per-server card list with health, disk space, upcoming count,
  queue count. Server switcher chip row on every arr screen.
- Server setup sheet: URL, API key (paste or scan QR of `apikey`), connection
  test, name, proxy assignment, self-signed toggle. First-run flow when a
  dashboard service is tapped and no server is registered.
- Server settings: edit, delete, reorder, proxy screens (global + per server),
  notification preferences per server and per event type.
- Offline sync status screen: pending queued actions, failures, retry controls.

## Radarr (R) screen groups

1. Movies library: poster grid and table views, filters (monitored, quality,
   genre, year, folder, tags, missing), saved custom filters, bulk selection
   edit, refresh and scan, movie editor. Tags: movies, moviefile.
2. Movie detail: overview, cast, files with quality, history, extra file
   import, rename preview, manual search and grab, interactive import, edit
   (monitoring, quality, path, tags), delete with file option.
3. Add new: search with year disambiguation, root folder, quality profile,
   monitoring strategy, minimum availability, tags, add and add+search.
   Discovery lists: popular, trending, recommendations. Tags: moviesearch,
   importlist, discover.
4. Collections: list, edit monitoring and quality, search.
5. Wanted and cutoff: missing list with search-on-add, cutoff unmet list,
   bulk search. Tags: wanted, cutoff.
6. Queue: grabbing, importing, failed items, remove with options (blocklist,
   re-search), retry import, per-item manual import. Tags: queue, command.
7. Activity history: event filters, blocklist management, failed manual
   imports. Tags: history, blocklist.
8. Calendar: month and agenda views, unmonitored toggle, per-day grouping by
   availability. Tags: calendar.
9. Settings: media management (naming tokens with live preview, root folders,
   import and file management toggles), profiles (quality definitions,
   delay profiles, release profiles), custom formats with scoring and
   specifications builder, indexers (categories, seed ratio, test),
   download clients (per client settings and test, remote path mapping),
   import lists (add, test, sync), connect notifications (event matrix,
   per-connection settings, test), metadata consumers, tags, general
   (host, security, proxy, logging, analytics, backup, updates), system
   (status, tasks, disk space, logs, updates, events feed). Tags: all
   remaining tags in the Radarr spec.

## Sonarr (S) screen groups

Identical shape to Radarr where the API matches, with series semantics:

1-8. Series library, series detail (seasons grid, episode file editor,
   per-episode history and search), add new with season picker and series
   type, wanted missing and cutoff unmet (season pass bulk), queue, history
   and blocklist, calendar with episode granularity.
9. Settings mirror of Radarr's list with Sonarr specifics: series and
   episode naming, release profiles, media management, providers.

## Lidarr (L) screen groups

1. Artists library: grid, filters, monitored state, edit, refresh, scan.
2. Artist detail: albums by release type, files, history, rename preview,
   monitored album toggles, manual search and interactive import.
3. Add new: artist search with disambiguation, album add by disambiguation
   page, root folder, quality and metadata profile, monitoring strategy,
   tags. Import lists and spotiy-style recommendations via lists endpoints.
4. Wanted: missing albums, cutoff unmet, bulk search.
5. Queue, activity history, blocklist: same contract as Radarr's.
6. Calendar: album release granularity.
7. Settings: media management with artist and album naming tokens, quality
   definitions, metadata profiles, delay profiles, release profiles,
   indexers, download clients, import lists, connect notifications,
   metadata (writing tags to files), tags, general, system.

## Prowlarr (P) screen groups

1. Indexers: card and table views, add from the prescreened definition list
   with dynamic field forms (info link, categories, seed ratio, caps),
   test, enabled toggle, priority, tags, bulk edit, categories manager,
   app sync status badges. Tags: indexer, indexerproxy, indexerflag.
2. Search: multi-indexer manual search across categories, results with
   age, size, seeders, indexer, grab to a download client or copy,
   search history with stats. Tags: search, history.
3. Stats: per indexer query counts, response time, grab ratio, user agent
   breakdown, charts by period. Tags: stats.
4. History: query log with filters and clear. Tags: history.
5. Settings: applications (sync to Radarr, Sonarr, Lidarr instances with
   sync preview), download clients, connect notifications, general
   (auth, proxy per indexer and app-level, logging), tags, UI culture.

## Bazarr (B) screen groups

1. Series and movies lists: subtitle status badges (missing, partial,
   complete, none wanted), filters, sort, blacklist status, mass editor.
2. Episodes and movie subtitle listing: per-episode subtitle entries with
   language, hearing impaired flag, score, actions (download, upload,
   delete), manual search across providers with scores, series details
   linking. Tags: episodes, movies, series, subtitles.
3. Wanted: missing subtitles with search and download, history of
   decisions. Tags: wanted, history.
4. Settings: languages, profiles (cutoff, must contain), providers with
   credentials and tags, notifications, scheduler intervals, UI, general.
   Tags: settings-related groups in the Bazarr spec.

## Cross-cutting deliverables

- Full offline: every screen group reads from Room and survives process
  death; writes queue and replay.
- SignalR: library, queue, calendar screens update live while visible.
- Notifications: grab, import, fail, health, disk per server.
- Accessibility: content descriptions on all icon buttons, minimum touch
  targets 48dp, screen reader order validated.
- Tablet and landscape layouts for library, calendar, and detail screens.
