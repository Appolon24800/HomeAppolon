# *arr API endpoint inventory

Generated from the OpenAPI specs in this directory. Tags group the endpoints;
each API-client task owns exactly one tag group per app.

## bazarr (56 paths, spec version 1.6.2)

### bazarr / Badges (1)

- `GET /badges` Get badges count to update the UI (get_badges)

### bazarr / Episodes (1)

- `GET /episodes` List episodes metadata for specific series or episodes (get_episodes)

### bazarr / Episodes Blacklist (3)

- `POST /episodes/blacklist` Add an episodes subtitles to blacklist (post_episodes_blacklist)
- `DELETE /episodes/blacklist` Delete an episodes subtitles from blacklist (delete_episodes_blacklist)
- `GET /episodes/blacklist` List blacklisted episodes subtitles (get_episodes_blacklist)

### bazarr / Episodes History (1)

- `GET /episodes/history` List episodes history events (get_episodes_history)

### bazarr / Episodes Subtitles (3)

- `PATCH /episodes/subtitles` Download an episode subtitles (patch_episodes_subtitles)
- `POST /episodes/subtitles` Upload an episode subtitles (post_episodes_subtitles)
- `DELETE /episodes/subtitles` Delete an episode subtitles (delete_episodes_subtitles)

### bazarr / Episodes Wanted (1)

- `GET /episodes/wanted` List episodes wanted subtitles (get_episodes_wanted)

### bazarr / Files Browser for Bazarr (1)

- `GET /files` List Bazarr file system content (get_browse_bazarr_fs)

### bazarr / Files Browser for Radarr (1)

- `GET /files/radarr` List Radarr file system content (get_browse_radarr_fs)

### bazarr / Files Browser for Sonarr (1)

- `GET /files/sonarr` List Sonarr file system content (get_browse_sonarr_fs)

### bazarr / History Statistics (1)

- `GET /history/stats` Get history statistics (get_history_stats)

### bazarr / Jellyfin (2)

- `GET /jellyfin/libraries` List available movie and series libraries from the Jellyfin server (get_jellyfin_libraries)
- `POST /jellyfin/test-connection` Test connection to a Jellyfin server with provided credentials (post_jellyfin_test_connection)

### bazarr / Movies (3)

- `PATCH /movies` Run actions on specific movies (patch_movies)
- `POST /movies` Update specific movies languages profile (post_movies)
- `GET /movies` List movies metadata for specific movies (get_movies)

### bazarr / Movies Blacklist (3)

- `POST /movies/blacklist` Add a movies subtitles to blacklist (post_movies_blacklist)
- `DELETE /movies/blacklist` Delete a movies subtitles from blacklist (delete_movies_blacklist)
- `GET /movies/blacklist` List blacklisted movies subtitles (get_movies_blacklist)

### bazarr / Movies History (1)

- `GET /movies/history` List movies history events (get_movies_history)

### bazarr / Movies Subtitles (3)

- `PATCH /movies/subtitles` Download a movie subtitles (patch_movies_subtitles)
- `POST /movies/subtitles` Upload a movie subtitles (post_movies_subtitles)
- `DELETE /movies/subtitles` Delete a movie subtitles (delete_movies_subtitles)

### bazarr / Movies Tags (1)

- `GET /movies/tags` List all distinct tags assigned to any movie (get_movies_tags)

### bazarr / Movies Wanted (1)

- `GET /movies/wanted` List movies wanted subtitles (get_movies_wanted)

### bazarr / Plex Authentication (17)

- `POST /plex/apikey` post_plex_api_key (post_plex_api_key)
- `GET /plex/autopulse/config` get_plex_autopulse_config (get_plex_autopulse_config)
- `POST /plex/encrypt-apikey` post_plex_encrypt_api_key (post_plex_encrypt_api_key)
- `GET /plex/oauth/libraries` get_plex_libraries (get_plex_libraries)
- `POST /plex/oauth/logout` post_plex_logout (post_plex_logout)
- `POST /plex/oauth/pin` post_plex_pin (post_plex_pin)
- `GET /plex/oauth/pin` get_plex_pin (get_plex_pin)
- `GET /plex/oauth/pin/{pin_id}/check` get_plex_pin_check (get_plex_pin_check)
- `GET /plex/oauth/servers` get_plex_servers (get_plex_servers)
- `GET /plex/oauth/validate` get_plex_validate (get_plex_validate)
- `POST /plex/select-server` post_plex_select_server (post_plex_select_server)
- `GET /plex/select-server` get_plex_select_server (get_plex_select_server)
- `POST /plex/test-connection` post_plex_test_connection (post_plex_test_connection)
- `GET /plex/test-connection` get_plex_test_connection (get_plex_test_connection)
- `POST /plex/webhook/create` post_plex_webhook_create (post_plex_webhook_create)
- `POST /plex/webhook/delete` post_plex_webhook_delete (post_plex_webhook_delete)
- `GET /plex/webhook/list` get_plex_webhook_list (get_plex_webhook_list)

### bazarr / Providers (2)

- `POST /providers` Reset providers status (post_providers)
- `GET /providers` Get providers status (get_providers)

### bazarr / Providers Episodes (2)

- `POST /providers/episodes` Manually download an episode subtitles (post_provider_episodes)
- `GET /providers/episodes` Search manually for an episode subtitles (get_provider_episodes)

### bazarr / Providers Movies (2)

- `POST /providers/movies` Manually download a movie subtitles (post_provider_movies)
- `GET /providers/movies` Search manually for a movie subtitles (get_provider_movies)

### bazarr / Series (3)

- `PATCH /series` Run actions on specific series (patch_series)
- `POST /series` Update specific series languages profile (post_series)
- `GET /series` List series metadata for specific series (get_series)

### bazarr / Series Tags (1)

- `GET /series/tags` List all distinct tags assigned to any series (get_series_tags)

### bazarr / Subtitle Contents (1)

- `GET /subtitles/contents` Retrieve subtitle file contents (get_subtitle_name_contents)

### bazarr / Subtitles (2)

- `PATCH /subtitles` Apply mods/tools on external subtitles (patch_subtitles)
- `GET /subtitles` Return available audio and embedded subtitles tracks with external subtitles (get_subtitles)

### bazarr / Subtitles Info (1)

- `GET /subtitles/info` Guessit over subtitles filename (get_subtitle_name_info)

### bazarr / System Announcements (2)

- `POST /system/announcements` Mark announcement as dismissed (post_system_announcements)
- `GET /system/announcements` List announcements relative to Bazarr (get_system_announcements)

### bazarr / System Backups (4)

- `PATCH /system/backups` Restore a backup file (patch_system_backups)
- `POST /system/backups` Create a new backup (post_system_backups)
- `DELETE /system/backups` Delete a backup file (delete_system_backups)
- `GET /system/backups` List backup files (get_system_backups)

### bazarr / System Health (1)

- `GET /system/health` List health issues (get_system_health)

### bazarr / System Jobs (4)

- `PATCH /system/jobs` Empty a specific jobs queue (patch_system_jobs)
- `POST /system/jobs` Force start, move to top or move to bottom of the queue a specific job (post_system_jobs)
- `DELETE /system/jobs` Delete a job from the queue (delete_system_jobs)
- `GET /system/jobs` List jobs from the queue (get_system_jobs)

### bazarr / System Languages (1)

- `GET /system/languages` List languages for history filter or for language filter menu (get_languages)

### bazarr / System Languages Profiles (1)

- `GET /system/languages/profiles` List languages profiles (get_languages_profiles)

### bazarr / System Logs (2)

- `DELETE /system/logs` Force log rotation and create a new log file (delete_system_logs)
- `GET /system/logs` List log entries (get_system_logs)

### bazarr / System Ping (1)

- `GET /system/ping` Return status and http 200 (get_system_ping)

### bazarr / System Releases (1)

- `GET /system/releases` Get Bazarr releases (get_system_releases)

### bazarr / System Searches (1)

- `GET /system/searches` List results from query (get_searches)

### bazarr / System Status (1)

- `GET /system/status` Return environment information and versions (get_system_status)

### bazarr / System Tasks (2)

- `POST /system/tasks` Run task (post_system_tasks)
- `GET /system/tasks` List tasks (get_system_tasks)

### bazarr / Webhooks Plex (1)

- `POST /webhooks/plex` Trigger subtitles search on play media event in Plex (post_web_hooks_plex)

### bazarr / Webhooks Radarr (1)

- `POST /webhooks/radarr` Search for missing subtitles based on Radarr webhooks (post_web_hooks_radarr)

### bazarr / Webhooks Sonarr (1)

- `POST /webhooks/sonarr` Search for missing subtitles based on Sonarr webhooks (post_web_hooks_sonarr)

### bazarr / systemSettings (1)

- `POST /system/webhooks/test` Test external webhook connection (post_system_webhook_test)

## lidarr (161 paths, spec version 1.0.0)

### lidarr / Album (6)

- `GET /api/v1/album`  ()
- `POST /api/v1/album`  ()
- `PUT /api/v1/album/{id}`  ()
- `DELETE /api/v1/album/{id}`  ()
- `GET /api/v1/album/{id}`  ()
- `PUT /api/v1/album/monitor`  ()

### lidarr / AlbumLookup (1)

- `GET /api/v1/album/lookup`  ()

### lidarr / AlbumStudio (1)

- `POST /api/v1/albumstudio`  ()

### lidarr / ApiInfo (1)

- `GET /api`  ()

### lidarr / Artist (5)

- `GET /api/v1/artist/{id}`  ()
- `PUT /api/v1/artist/{id}`  ()
- `DELETE /api/v1/artist/{id}`  ()
- `GET /api/v1/artist`  ()
- `POST /api/v1/artist`  ()

### lidarr / ArtistEditor (2)

- `PUT /api/v1/artist/editor`  ()
- `DELETE /api/v1/artist/editor`  ()

### lidarr / ArtistLookup (1)

- `GET /api/v1/artist/lookup`  ()

### lidarr / Authentication (2)

- `POST /login`  ()
- `GET /logout`  ()

### lidarr / AutoTagging (6)

- `GET /api/v1/autotagging/{id}`  ()
- `PUT /api/v1/autotagging/{id}`  ()
- `DELETE /api/v1/autotagging/{id}`  ()
- `POST /api/v1/autotagging`  ()
- `GET /api/v1/autotagging`  ()
- `GET /api/v1/autotagging/schema`  ()

### lidarr / Backup (4)

- `GET /api/v1/system/backup`  ()
- `DELETE /api/v1/system/backup/{id}`  ()
- `POST /api/v1/system/backup/restore/{id}`  ()
- `POST /api/v1/system/backup/restore/upload`  ()

### lidarr / Blocklist (3)

- `GET /api/v1/blocklist`  ()
- `DELETE /api/v1/blocklist/{id}`  ()
- `DELETE /api/v1/blocklist/bulk`  ()

### lidarr / Calendar (2)

- `GET /api/v1/calendar`  ()
- `GET /api/v1/calendar/{id}`  ()

### lidarr / CalendarFeed (1)

- `GET /feed/v1/calendar/lidarr.ics`  ()

### lidarr / Command (4)

- `GET /api/v1/command/{id}`  ()
- `DELETE /api/v1/command/{id}`  ()
- `POST /api/v1/command`  ()
- `GET /api/v1/command`  ()

### lidarr / CustomFilter (5)

- `GET /api/v1/customfilter/{id}`  ()
- `PUT /api/v1/customfilter/{id}`  ()
- `DELETE /api/v1/customfilter/{id}`  ()
- `GET /api/v1/customfilter`  ()
- `POST /api/v1/customfilter`  ()

### lidarr / CustomFormat (8)

- `GET /api/v1/customformat/{id}`  ()
- `PUT /api/v1/customformat/{id}`  ()
- `DELETE /api/v1/customformat/{id}`  ()
- `GET /api/v1/customformat`  ()
- `POST /api/v1/customformat`  ()
- `PUT /api/v1/customformat/bulk`  ()
- `DELETE /api/v1/customformat/bulk`  ()
- `GET /api/v1/customformat/schema`  ()

### lidarr / Cutoff (2)

- `GET /api/v1/wanted/cutoff`  ()
- `GET /api/v1/wanted/cutoff/{id}`  ()

### lidarr / DelayProfile (6)

- `POST /api/v1/delayprofile`  ()
- `GET /api/v1/delayprofile`  ()
- `DELETE /api/v1/delayprofile/{id}`  ()
- `PUT /api/v1/delayprofile/{id}`  ()
- `GET /api/v1/delayprofile/{id}`  ()
- `PUT /api/v1/delayprofile/reorder/{id}`  ()

### lidarr / DiskSpace (1)

- `GET /api/v1/diskspace`  ()

### lidarr / DownloadClient (11)

- `GET /api/v1/downloadclient/{id}`  ()
- `PUT /api/v1/downloadclient/{id}`  ()
- `DELETE /api/v1/downloadclient/{id}`  ()
- `GET /api/v1/downloadclient`  ()
- `POST /api/v1/downloadclient`  ()
- `PUT /api/v1/downloadclient/bulk`  ()
- `DELETE /api/v1/downloadclient/bulk`  ()
- `GET /api/v1/downloadclient/schema`  ()
- `POST /api/v1/downloadclient/test`  ()
- `POST /api/v1/downloadclient/testall`  ()
- `POST /api/v1/downloadclient/action/{name}`  ()

### lidarr / DownloadClientConfig (3)

- `GET /api/v1/config/downloadclient/{id}`  ()
- `PUT /api/v1/config/downloadclient/{id}`  ()
- `GET /api/v1/config/downloadclient`  ()

### lidarr / FileSystem (3)

- `GET /api/v1/filesystem`  ()
- `GET /api/v1/filesystem/type`  ()
- `GET /api/v1/filesystem/mediafiles`  ()

### lidarr / Health (1)

- `GET /api/v1/health`  ()

### lidarr / History (4)

- `GET /api/v1/history`  ()
- `GET /api/v1/history/since`  ()
- `GET /api/v1/history/artist`  ()
- `POST /api/v1/history/failed/{id}`  ()

### lidarr / HostConfig (3)

- `GET /api/v1/config/host/{id}`  ()
- `PUT /api/v1/config/host/{id}`  ()
- `GET /api/v1/config/host`  ()

### lidarr / ImportList (11)

- `GET /api/v1/importlist/{id}`  ()
- `PUT /api/v1/importlist/{id}`  ()
- `DELETE /api/v1/importlist/{id}`  ()
- `GET /api/v1/importlist`  ()
- `POST /api/v1/importlist`  ()
- `PUT /api/v1/importlist/bulk`  ()
- `DELETE /api/v1/importlist/bulk`  ()
- `GET /api/v1/importlist/schema`  ()
- `POST /api/v1/importlist/test`  ()
- `POST /api/v1/importlist/testall`  ()
- `POST /api/v1/importlist/action/{name}`  ()

### lidarr / ImportListExclusion (5)

- `GET /api/v1/importlistexclusion/{id}`  ()
- `PUT /api/v1/importlistexclusion/{id}`  ()
- `DELETE /api/v1/importlistexclusion/{id}`  ()
- `GET /api/v1/importlistexclusion`  ()
- `POST /api/v1/importlistexclusion`  ()

### lidarr / Indexer (11)

- `GET /api/v1/indexer/{id}`  ()
- `PUT /api/v1/indexer/{id}`  ()
- `DELETE /api/v1/indexer/{id}`  ()
- `GET /api/v1/indexer`  ()
- `POST /api/v1/indexer`  ()
- `PUT /api/v1/indexer/bulk`  ()
- `DELETE /api/v1/indexer/bulk`  ()
- `GET /api/v1/indexer/schema`  ()
- `POST /api/v1/indexer/test`  ()
- `POST /api/v1/indexer/testall`  ()
- `POST /api/v1/indexer/action/{name}`  ()

### lidarr / IndexerConfig (3)

- `GET /api/v1/config/indexer/{id}`  ()
- `PUT /api/v1/config/indexer/{id}`  ()
- `GET /api/v1/config/indexer`  ()

### lidarr / IndexerFlag (1)

- `GET /api/v1/indexerflag`  ()

### lidarr / Language (2)

- `GET /api/v1/language/{id}`  ()
- `GET /api/v1/language`  ()

### lidarr / Localization (1)

- `GET /api/v1/localization`  ()

### lidarr / Log (1)

- `GET /api/v1/log`  ()

### lidarr / LogFile (2)

- `GET /api/v1/log/file`  ()
- `GET /api/v1/log/file/{filename}`  ()

### lidarr / ManualImport (2)

- `POST /api/v1/manualimport`  ()
- `GET /api/v1/manualimport`  ()

### lidarr / MediaCover (2)

- `GET /api/v1/mediacover/artist/{artistId}/{filename}`  ()
- `GET /api/v1/mediacover/album/{albumId}/{filename}`  ()

### lidarr / MediaManagementConfig (3)

- `GET /api/v1/config/mediamanagement/{id}`  ()
- `PUT /api/v1/config/mediamanagement/{id}`  ()
- `GET /api/v1/config/mediamanagement`  ()

### lidarr / Metadata (9)

- `GET /api/v1/metadata/{id}`  ()
- `PUT /api/v1/metadata/{id}`  ()
- `DELETE /api/v1/metadata/{id}`  ()
- `GET /api/v1/metadata`  ()
- `POST /api/v1/metadata`  ()
- `GET /api/v1/metadata/schema`  ()
- `POST /api/v1/metadata/test`  ()
- `POST /api/v1/metadata/testall`  ()
- `POST /api/v1/metadata/action/{name}`  ()

### lidarr / MetadataProfile (5)

- `POST /api/v1/metadataprofile`  ()
- `GET /api/v1/metadataprofile`  ()
- `DELETE /api/v1/metadataprofile/{id}`  ()
- `PUT /api/v1/metadataprofile/{id}`  ()
- `GET /api/v1/metadataprofile/{id}`  ()

### lidarr / MetadataProfileSchema (1)

- `GET /api/v1/metadataprofile/schema`  ()

### lidarr / MetadataProviderConfig (3)

- `GET /api/v1/config/metadataprovider/{id}`  ()
- `PUT /api/v1/config/metadataprovider/{id}`  ()
- `GET /api/v1/config/metadataprovider`  ()

### lidarr / Missing (2)

- `GET /api/v1/wanted/missing`  ()
- `GET /api/v1/wanted/missing/{id}`  ()

### lidarr / NamingConfig (4)

- `GET /api/v1/config/naming/{id}`  ()
- `PUT /api/v1/config/naming/{id}`  ()
- `GET /api/v1/config/naming`  ()
- `GET /api/v1/config/naming/examples`  ()

### lidarr / Notification (9)

- `GET /api/v1/notification/{id}`  ()
- `PUT /api/v1/notification/{id}`  ()
- `DELETE /api/v1/notification/{id}`  ()
- `GET /api/v1/notification`  ()
- `POST /api/v1/notification`  ()
- `GET /api/v1/notification/schema`  ()
- `POST /api/v1/notification/test`  ()
- `POST /api/v1/notification/testall`  ()
- `POST /api/v1/notification/action/{name}`  ()

### lidarr / Parse (1)

- `GET /api/v1/parse`  ()

### lidarr / Ping (1)

- `GET /ping`  ()

### lidarr / QualityDefinition (4)

- `PUT /api/v1/qualitydefinition/{id}`  ()
- `GET /api/v1/qualitydefinition/{id}`  ()
- `GET /api/v1/qualitydefinition`  ()
- `PUT /api/v1/qualitydefinition/update`  ()

### lidarr / QualityProfile (5)

- `POST /api/v1/qualityprofile`  ()
- `GET /api/v1/qualityprofile`  ()
- `DELETE /api/v1/qualityprofile/{id}`  ()
- `PUT /api/v1/qualityprofile/{id}`  ()
- `GET /api/v1/qualityprofile/{id}`  ()

### lidarr / QualityProfileSchema (1)

- `GET /api/v1/qualityprofile/schema`  ()

### lidarr / Queue (3)

- `DELETE /api/v1/queue/{id}`  ()
- `DELETE /api/v1/queue/bulk`  ()
- `GET /api/v1/queue`  ()

### lidarr / QueueAction (2)

- `POST /api/v1/queue/grab/{id}`  ()
- `POST /api/v1/queue/grab/bulk`  ()

### lidarr / QueueDetails (1)

- `GET /api/v1/queue/details`  ()

### lidarr / QueueStatus (1)

- `GET /api/v1/queue/status`  ()

### lidarr / Release (2)

- `POST /api/v1/release`  ()
- `GET /api/v1/release`  ()

### lidarr / ReleaseProfile (5)

- `GET /api/v1/releaseprofile/{id}`  ()
- `PUT /api/v1/releaseprofile/{id}`  ()
- `DELETE /api/v1/releaseprofile/{id}`  ()
- `GET /api/v1/releaseprofile`  ()
- `POST /api/v1/releaseprofile`  ()

### lidarr / ReleasePush (1)

- `POST /api/v1/release/push`  ()

### lidarr / RemotePathMapping (5)

- `GET /api/v1/remotepathmapping/{id}`  ()
- `DELETE /api/v1/remotepathmapping/{id}`  ()
- `PUT /api/v1/remotepathmapping/{id}`  ()
- `POST /api/v1/remotepathmapping`  ()
- `GET /api/v1/remotepathmapping`  ()

### lidarr / RenameTrack (1)

- `GET /api/v1/rename`  ()

### lidarr / RetagTrack (1)

- `GET /api/v1/retag`  ()

### lidarr / RootFolder (5)

- `GET /api/v1/rootfolder/{id}`  ()
- `PUT /api/v1/rootfolder/{id}`  ()
- `DELETE /api/v1/rootfolder/{id}`  ()
- `POST /api/v1/rootfolder`  ()
- `GET /api/v1/rootfolder`  ()

### lidarr / Search (1)

- `GET /api/v1/search`  ()

### lidarr / StaticResource (4)

- `GET /login`  ()
- `GET /content/{path}`  ()
- `GET /`  ()
- `GET /{path}`  ()

### lidarr / System (5)

- `GET /api/v1/system/status`  ()
- `GET /api/v1/system/routes`  ()
- `GET /api/v1/system/routes/duplicate`  ()
- `POST /api/v1/system/shutdown`  ()
- `POST /api/v1/system/restart`  ()

### lidarr / Tag (5)

- `GET /api/v1/tag/{id}`  ()
- `PUT /api/v1/tag/{id}`  ()
- `DELETE /api/v1/tag/{id}`  ()
- `GET /api/v1/tag`  ()
- `POST /api/v1/tag`  ()

### lidarr / TagDetails (2)

- `GET /api/v1/tag/detail/{id}`  ()
- `GET /api/v1/tag/detail`  ()

### lidarr / Task (2)

- `GET /api/v1/system/task`  ()
- `GET /api/v1/system/task/{id}`  ()

### lidarr / Track (2)

- `GET /api/v1/track`  ()
- `GET /api/v1/track/{id}`  ()

### lidarr / TrackFile (6)

- `GET /api/v1/trackfile/{id}`  ()
- `PUT /api/v1/trackfile/{id}`  ()
- `DELETE /api/v1/trackfile/{id}`  ()
- `GET /api/v1/trackfile`  ()
- `PUT /api/v1/trackfile/editor`  ()
- `DELETE /api/v1/trackfile/bulk`  ()

### lidarr / UiConfig (3)

- `PUT /api/v1/config/ui/{id}`  ()
- `GET /api/v1/config/ui/{id}`  ()
- `GET /api/v1/config/ui`  ()

### lidarr / Update (1)

- `GET /api/v1/update`  ()

### lidarr / UpdateLogFile (2)

- `GET /api/v1/log/file/update`  ()
- `GET /api/v1/log/file/update/{filename}`  ()

## prowlarr (93 paths, spec version 1.0.0)

### prowlarr / ApiInfo (1)

- `GET /api`  ()

### prowlarr / AppProfile (6)

- `POST /api/v1/appprofile`  ()
- `GET /api/v1/appprofile`  ()
- `DELETE /api/v1/appprofile/{id}`  ()
- `PUT /api/v1/appprofile/{id}`  ()
- `GET /api/v1/appprofile/{id}`  ()
- `GET /api/v1/appprofile/schema`  ()

### prowlarr / Application (11)

- `GET /api/v1/applications/{id}`  ()
- `PUT /api/v1/applications/{id}`  ()
- `DELETE /api/v1/applications/{id}`  ()
- `GET /api/v1/applications`  ()
- `POST /api/v1/applications`  ()
- `PUT /api/v1/applications/bulk`  ()
- `DELETE /api/v1/applications/bulk`  ()
- `GET /api/v1/applications/schema`  ()
- `POST /api/v1/applications/test`  ()
- `POST /api/v1/applications/testall`  ()
- `POST /api/v1/applications/action/{name}`  ()

### prowlarr / Authentication (2)

- `POST /login`  ()
- `GET /logout`  ()

### prowlarr / Backup (4)

- `GET /api/v1/system/backup`  ()
- `DELETE /api/v1/system/backup/{id}`  ()
- `POST /api/v1/system/backup/restore/{id}`  ()
- `POST /api/v1/system/backup/restore/upload`  ()

### prowlarr / Command (4)

- `GET /api/v1/command/{id}`  ()
- `DELETE /api/v1/command/{id}`  ()
- `POST /api/v1/command`  ()
- `GET /api/v1/command`  ()

### prowlarr / CustomFilter (5)

- `GET /api/v1/customfilter/{id}`  ()
- `PUT /api/v1/customfilter/{id}`  ()
- `DELETE /api/v1/customfilter/{id}`  ()
- `GET /api/v1/customfilter`  ()
- `POST /api/v1/customfilter`  ()

### prowlarr / DevelopmentConfig (3)

- `PUT /api/v1/config/development/{id}`  ()
- `GET /api/v1/config/development/{id}`  ()
- `GET /api/v1/config/development`  ()

### prowlarr / DownloadClient (11)

- `GET /api/v1/downloadclient/{id}`  ()
- `PUT /api/v1/downloadclient/{id}`  ()
- `DELETE /api/v1/downloadclient/{id}`  ()
- `GET /api/v1/downloadclient`  ()
- `POST /api/v1/downloadclient`  ()
- `PUT /api/v1/downloadclient/bulk`  ()
- `DELETE /api/v1/downloadclient/bulk`  ()
- `GET /api/v1/downloadclient/schema`  ()
- `POST /api/v1/downloadclient/test`  ()
- `POST /api/v1/downloadclient/testall`  ()
- `POST /api/v1/downloadclient/action/{name}`  ()

### prowlarr / DownloadClientConfig (3)

- `GET /api/v1/config/downloadclient/{id}`  ()
- `PUT /api/v1/config/downloadclient/{id}`  ()
- `GET /api/v1/config/downloadclient`  ()

### prowlarr / FileSystem (2)

- `GET /api/v1/filesystem`  ()
- `GET /api/v1/filesystem/type`  ()

### prowlarr / Health (1)

- `GET /api/v1/health`  ()

### prowlarr / History (3)

- `GET /api/v1/history`  ()
- `GET /api/v1/history/since`  ()
- `GET /api/v1/history/indexer`  ()

### prowlarr / HostConfig (3)

- `GET /api/v1/config/host/{id}`  ()
- `PUT /api/v1/config/host/{id}`  ()
- `GET /api/v1/config/host`  ()

### prowlarr / Indexer (11)

- `GET /api/v1/indexer/{id}`  ()
- `PUT /api/v1/indexer/{id}`  ()
- `DELETE /api/v1/indexer/{id}`  ()
- `GET /api/v1/indexer`  ()
- `POST /api/v1/indexer`  ()
- `PUT /api/v1/indexer/bulk`  ()
- `DELETE /api/v1/indexer/bulk`  ()
- `GET /api/v1/indexer/schema`  ()
- `POST /api/v1/indexer/test`  ()
- `POST /api/v1/indexer/testall`  ()
- `POST /api/v1/indexer/action/{name}`  ()

### prowlarr / IndexerDefaultCategories (1)

- `GET /api/v1/indexer/categories`  ()

### prowlarr / IndexerProxy (9)

- `GET /api/v1/indexerproxy/{id}`  ()
- `PUT /api/v1/indexerproxy/{id}`  ()
- `DELETE /api/v1/indexerproxy/{id}`  ()
- `GET /api/v1/indexerproxy`  ()
- `POST /api/v1/indexerproxy`  ()
- `GET /api/v1/indexerproxy/schema`  ()
- `POST /api/v1/indexerproxy/test`  ()
- `POST /api/v1/indexerproxy/testall`  ()
- `POST /api/v1/indexerproxy/action/{name}`  ()

### prowlarr / IndexerStats (1)

- `GET /api/v1/indexerstats`  ()

### prowlarr / IndexerStatus (1)

- `GET /api/v1/indexerstatus`  ()

### prowlarr / Localization (2)

- `GET /api/v1/localization`  ()
- `GET /api/v1/localization/options`  ()

### prowlarr / Log (1)

- `GET /api/v1/log`  ()

### prowlarr / LogFile (2)

- `GET /api/v1/log/file`  ()
- `GET /api/v1/log/file/{filename}`  ()

### prowlarr / Newznab (4)

- `GET /api/v1/indexer/{id}/newznab`  ()
- `GET /{id}/api`  ()
- `GET /api/v1/indexer/{id}/download`  ()
- `GET /{id}/download`  ()

### prowlarr / Notification (9)

- `GET /api/v1/notification/{id}`  ()
- `PUT /api/v1/notification/{id}`  ()
- `DELETE /api/v1/notification/{id}`  ()
- `GET /api/v1/notification`  ()
- `POST /api/v1/notification`  ()
- `GET /api/v1/notification/schema`  ()
- `POST /api/v1/notification/test`  ()
- `POST /api/v1/notification/testall`  ()
- `POST /api/v1/notification/action/{name}`  ()

### prowlarr / Ping (1)

- `GET /ping`  ()

### prowlarr / Search (3)

- `POST /api/v1/search`  ()
- `GET /api/v1/search`  ()
- `POST /api/v1/search/bulk`  ()

### prowlarr / StaticResource (4)

- `GET /login`  ()
- `GET /content/{path}`  ()
- `GET /`  ()
- `GET /{path}`  ()

### prowlarr / System (5)

- `GET /api/v1/system/status`  ()
- `GET /api/v1/system/routes`  ()
- `GET /api/v1/system/routes/duplicate`  ()
- `POST /api/v1/system/shutdown`  ()
- `POST /api/v1/system/restart`  ()

### prowlarr / Tag (5)

- `GET /api/v1/tag/{id}`  ()
- `PUT /api/v1/tag/{id}`  ()
- `DELETE /api/v1/tag/{id}`  ()
- `GET /api/v1/tag`  ()
- `POST /api/v1/tag`  ()

### prowlarr / TagDetails (2)

- `GET /api/v1/tag/detail/{id}`  ()
- `GET /api/v1/tag/detail`  ()

### prowlarr / Task (2)

- `GET /api/v1/system/task`  ()
- `GET /api/v1/system/task/{id}`  ()

### prowlarr / UiConfig (3)

- `PUT /api/v1/config/ui/{id}`  ()
- `GET /api/v1/config/ui/{id}`  ()
- `GET /api/v1/config/ui`  ()

### prowlarr / Update (1)

- `GET /api/v1/update`  ()

### prowlarr / UpdateLogFile (2)

- `GET /api/v1/log/file/update`  ()
- `GET /api/v1/log/file/update/{filename}`  ()

## radarr (166 paths, spec version 3.0.0)

### radarr / AlternativeTitle (2)

- `GET /api/v3/alttitle`  ()
- `GET /api/v3/alttitle/{id}`  ()

### radarr / ApiInfo (1)

- `GET /api`  ()

### radarr / Authentication (3)

- `GET /login/sso`  ()
- `POST /login`  ()
- `GET /logout`  ()

### radarr / AutoTagging (6)

- `POST /api/v3/autotagging`  ()
- `GET /api/v3/autotagging`  ()
- `PUT /api/v3/autotagging/{id}`  ()
- `DELETE /api/v3/autotagging/{id}`  ()
- `GET /api/v3/autotagging/{id}`  ()
- `GET /api/v3/autotagging/schema`  ()

### radarr / Backup (4)

- `GET /api/v3/system/backup`  ()
- `DELETE /api/v3/system/backup/{id}`  ()
- `POST /api/v3/system/backup/restore/{id}`  ()
- `POST /api/v3/system/backup/restore/upload`  ()

### radarr / Blocklist (4)

- `GET /api/v3/blocklist`  ()
- `GET /api/v3/blocklist/movie`  ()
- `DELETE /api/v3/blocklist/{id}`  ()
- `DELETE /api/v3/blocklist/bulk`  ()

### radarr / Calendar (1)

- `GET /api/v3/calendar`  ()

### radarr / CalendarFeed (1)

- `GET /feed/v3/calendar/radarr.ics`  ()

### radarr / Collection (4)

- `GET /api/v3/collection`  ()
- `PUT /api/v3/collection`  ()
- `PUT /api/v3/collection/{id}`  ()
- `GET /api/v3/collection/{id}`  ()

### radarr / Command (4)

- `POST /api/v3/command`  ()
- `GET /api/v3/command`  ()
- `DELETE /api/v3/command/{id}`  ()
- `GET /api/v3/command/{id}`  ()

### radarr / Credit (2)

- `GET /api/v3/credit`  ()
- `GET /api/v3/credit/{id}`  ()

### radarr / CustomFilter (5)

- `GET /api/v3/customfilter`  ()
- `POST /api/v3/customfilter`  ()
- `PUT /api/v3/customfilter/{id}`  ()
- `DELETE /api/v3/customfilter/{id}`  ()
- `GET /api/v3/customfilter/{id}`  ()

### radarr / CustomFormat (8)

- `GET /api/v3/customformat`  ()
- `POST /api/v3/customformat`  ()
- `PUT /api/v3/customformat/{id}`  ()
- `DELETE /api/v3/customformat/{id}`  ()
- `GET /api/v3/customformat/{id}`  ()
- `PUT /api/v3/customformat/bulk`  ()
- `DELETE /api/v3/customformat/bulk`  ()
- `GET /api/v3/customformat/schema`  ()

### radarr / Cutoff (1)

- `GET /api/v3/wanted/cutoff`  ()

### radarr / DelayProfile (6)

- `POST /api/v3/delayprofile`  ()
- `GET /api/v3/delayprofile`  ()
- `DELETE /api/v3/delayprofile/{id}`  ()
- `PUT /api/v3/delayprofile/{id}`  ()
- `GET /api/v3/delayprofile/{id}`  ()
- `PUT /api/v3/delayprofile/reorder/{id}`  ()

### radarr / DiskSpace (1)

- `GET /api/v3/diskspace`  ()

### radarr / DownloadClient (11)

- `GET /api/v3/downloadclient`  ()
- `POST /api/v3/downloadclient`  ()
- `PUT /api/v3/downloadclient/{id}`  ()
- `DELETE /api/v3/downloadclient/{id}`  ()
- `GET /api/v3/downloadclient/{id}`  ()
- `PUT /api/v3/downloadclient/bulk`  ()
- `DELETE /api/v3/downloadclient/bulk`  ()
- `GET /api/v3/downloadclient/schema`  ()
- `POST /api/v3/downloadclient/test`  ()
- `POST /api/v3/downloadclient/testall`  ()
- `POST /api/v3/downloadclient/action/{name}`  ()

### radarr / DownloadClientConfig (3)

- `GET /api/v3/config/downloadclient`  ()
- `PUT /api/v3/config/downloadclient/{id}`  ()
- `GET /api/v3/config/downloadclient/{id}`  ()

### radarr / ExtraFile (1)

- `GET /api/v3/extrafile`  ()

### radarr / FileSystem (3)

- `GET /api/v3/filesystem`  ()
- `GET /api/v3/filesystem/type`  ()
- `GET /api/v3/filesystem/mediafiles`  ()

### radarr / Health (1)

- `GET /api/v3/health`  ()

### radarr / History (4)

- `GET /api/v3/history`  ()
- `GET /api/v3/history/since`  ()
- `GET /api/v3/history/movie`  ()
- `POST /api/v3/history/failed/{id}`  ()

### radarr / HostConfig (3)

- `GET /api/v3/config/host`  ()
- `PUT /api/v3/config/host/{id}`  ()
- `GET /api/v3/config/host/{id}`  ()

### radarr / ImportList (11)

- `GET /api/v3/importlist`  ()
- `POST /api/v3/importlist`  ()
- `PUT /api/v3/importlist/{id}`  ()
- `DELETE /api/v3/importlist/{id}`  ()
- `GET /api/v3/importlist/{id}`  ()
- `PUT /api/v3/importlist/bulk`  ()
- `DELETE /api/v3/importlist/bulk`  ()
- `GET /api/v3/importlist/schema`  ()
- `POST /api/v3/importlist/test`  ()
- `POST /api/v3/importlist/testall`  ()
- `POST /api/v3/importlist/action/{name}`  ()

### radarr / ImportListConfig (3)

- `GET /api/v3/config/importlist`  ()
- `PUT /api/v3/config/importlist/{id}`  ()
- `GET /api/v3/config/importlist/{id}`  ()

### radarr / ImportListExclusion (8)

- `GET /api/v3/exclusions`  ()
- `POST /api/v3/exclusions`  ()
- `GET /api/v3/exclusions/paged`  ()
- `PUT /api/v3/exclusions/{id}`  ()
- `DELETE /api/v3/exclusions/{id}`  ()
- `GET /api/v3/exclusions/{id}`  ()
- `POST /api/v3/exclusions/bulk`  ()
- `DELETE /api/v3/exclusions/bulk`  ()

### radarr / ImportListMovies (2)

- `GET /api/v3/importlist/movie`  ()
- `POST /api/v3/importlist/movie`  ()

### radarr / Indexer (11)

- `GET /api/v3/indexer`  ()
- `POST /api/v3/indexer`  ()
- `PUT /api/v3/indexer/{id}`  ()
- `DELETE /api/v3/indexer/{id}`  ()
- `GET /api/v3/indexer/{id}`  ()
- `PUT /api/v3/indexer/bulk`  ()
- `DELETE /api/v3/indexer/bulk`  ()
- `GET /api/v3/indexer/schema`  ()
- `POST /api/v3/indexer/test`  ()
- `POST /api/v3/indexer/testall`  ()
- `POST /api/v3/indexer/action/{name}`  ()

### radarr / IndexerConfig (3)

- `GET /api/v3/config/indexer`  ()
- `PUT /api/v3/config/indexer/{id}`  ()
- `GET /api/v3/config/indexer/{id}`  ()

### radarr / IndexerFlag (1)

- `GET /api/v3/indexerflag`  ()

### radarr / Language (2)

- `GET /api/v3/language`  ()
- `GET /api/v3/language/{id}`  ()

### radarr / Localization (2)

- `GET /api/v3/localization`  ()
- `GET /api/v3/localization/language`  ()

### radarr / Log (1)

- `GET /api/v3/log`  ()

### radarr / LogFile (2)

- `GET /api/v3/log/file`  ()
- `GET /api/v3/log/file/{filename}`  ()

### radarr / ManualImport (2)

- `GET /api/v3/manualimport`  ()
- `POST /api/v3/manualimport`  ()

### radarr / MediaCover (1)

- `GET /api/v3/mediacover/{movieId}/{filename}`  ()

### radarr / MediaManagementConfig (3)

- `GET /api/v3/config/mediamanagement`  ()
- `PUT /api/v3/config/mediamanagement/{id}`  ()
- `GET /api/v3/config/mediamanagement/{id}`  ()

### radarr / Metadata (9)

- `GET /api/v3/metadata`  ()
- `POST /api/v3/metadata`  ()
- `PUT /api/v3/metadata/{id}`  ()
- `DELETE /api/v3/metadata/{id}`  ()
- `GET /api/v3/metadata/{id}`  ()
- `GET /api/v3/metadata/schema`  ()
- `POST /api/v3/metadata/test`  ()
- `POST /api/v3/metadata/testall`  ()
- `POST /api/v3/metadata/action/{name}`  ()

### radarr / MetadataConfig (3)

- `GET /api/v3/config/metadata`  ()
- `PUT /api/v3/config/metadata/{id}`  ()
- `GET /api/v3/config/metadata/{id}`  ()

### radarr / Missing (1)

- `GET /api/v3/wanted/missing`  ()

### radarr / Movie (5)

- `GET /api/v3/movie`  ()
- `POST /api/v3/movie`  ()
- `PUT /api/v3/movie/{id}`  ()
- `DELETE /api/v3/movie/{id}`  ()
- `GET /api/v3/movie/{id}`  ()

### radarr / MovieEditor (2)

- `PUT /api/v3/movie/editor`  ()
- `DELETE /api/v3/movie/editor`  ()

### radarr / MovieFile (7)

- `GET /api/v3/moviefile`  ()
- `PUT /api/v3/moviefile/{id}`  ()
- `DELETE /api/v3/moviefile/{id}`  ()
- `GET /api/v3/moviefile/{id}`  ()
- `PUT /api/v3/moviefile/editor`  ()
- `DELETE /api/v3/moviefile/bulk`  ()
- `PUT /api/v3/moviefile/bulk`  ()

### radarr / MovieFolder (1)

- `GET /api/v3/movie/{id}/folder`  ()

### radarr / MovieImport (1)

- `POST /api/v3/movie/import`  ()

### radarr / MovieLookup (3)

- `GET /api/v3/movie/lookup/tmdb`  ()
- `GET /api/v3/movie/lookup/imdb`  ()
- `GET /api/v3/movie/lookup`  ()

### radarr / NamingConfig (4)

- `GET /api/v3/config/naming`  ()
- `PUT /api/v3/config/naming/{id}`  ()
- `GET /api/v3/config/naming/{id}`  ()
- `GET /api/v3/config/naming/examples`  ()

### radarr / Notification (9)

- `GET /api/v3/notification`  ()
- `POST /api/v3/notification`  ()
- `PUT /api/v3/notification/{id}`  ()
- `DELETE /api/v3/notification/{id}`  ()
- `GET /api/v3/notification/{id}`  ()
- `GET /api/v3/notification/schema`  ()
- `POST /api/v3/notification/test`  ()
- `POST /api/v3/notification/testall`  ()
- `POST /api/v3/notification/action/{name}`  ()

### radarr / Parse (1)

- `GET /api/v3/parse`  ()

### radarr / Ping (1)

- `GET /ping`  ()

### radarr / QualityDefinition (5)

- `PUT /api/v3/qualitydefinition/{id}`  ()
- `GET /api/v3/qualitydefinition/{id}`  ()
- `GET /api/v3/qualitydefinition`  ()
- `PUT /api/v3/qualitydefinition/update`  ()
- `GET /api/v3/qualitydefinition/limits`  ()

### radarr / QualityProfile (5)

- `POST /api/v3/qualityprofile`  ()
- `GET /api/v3/qualityprofile`  ()
- `DELETE /api/v3/qualityprofile/{id}`  ()
- `PUT /api/v3/qualityprofile/{id}`  ()
- `GET /api/v3/qualityprofile/{id}`  ()

### radarr / QualityProfileSchema (1)

- `GET /api/v3/qualityprofile/schema`  ()

### radarr / Queue (3)

- `DELETE /api/v3/queue/{id}`  ()
- `DELETE /api/v3/queue/bulk`  ()
- `GET /api/v3/queue`  ()

### radarr / QueueAction (2)

- `POST /api/v3/queue/grab/{id}`  ()
- `POST /api/v3/queue/grab/bulk`  ()

### radarr / QueueDetails (1)

- `GET /api/v3/queue/details`  ()

### radarr / QueueStatus (1)

- `GET /api/v3/queue/status`  ()

### radarr / Release (2)

- `POST /api/v3/release`  ()
- `GET /api/v3/release`  ()

### radarr / ReleaseProfile (5)

- `POST /api/v3/releaseprofile`  ()
- `GET /api/v3/releaseprofile`  ()
- `DELETE /api/v3/releaseprofile/{id}`  ()
- `PUT /api/v3/releaseprofile/{id}`  ()
- `GET /api/v3/releaseprofile/{id}`  ()

### radarr / ReleasePush (1)

- `POST /api/v3/release/push`  ()

### radarr / RemotePathMapping (5)

- `POST /api/v3/remotepathmapping`  ()
- `GET /api/v3/remotepathmapping`  ()
- `DELETE /api/v3/remotepathmapping/{id}`  ()
- `PUT /api/v3/remotepathmapping/{id}`  ()
- `GET /api/v3/remotepathmapping/{id}`  ()

### radarr / RenameMovie (1)

- `GET /api/v3/rename`  ()

### radarr / RootFolder (4)

- `POST /api/v3/rootfolder`  ()
- `GET /api/v3/rootfolder`  ()
- `DELETE /api/v3/rootfolder/{id}`  ()
- `GET /api/v3/rootfolder/{id}`  ()

### radarr / StaticResource (5)

- `GET /login`  ()
- `GET /loggedout`  ()
- `GET /content/{path}`  ()
- `GET /`  ()
- `GET /{path}`  ()

### radarr / System (5)

- `GET /api/v3/system/status`  ()
- `GET /api/v3/system/routes`  ()
- `GET /api/v3/system/routes/duplicate`  ()
- `POST /api/v3/system/shutdown`  ()
- `POST /api/v3/system/restart`  ()

### radarr / Tag (5)

- `GET /api/v3/tag`  ()
- `POST /api/v3/tag`  ()
- `PUT /api/v3/tag/{id}`  ()
- `DELETE /api/v3/tag/{id}`  ()
- `GET /api/v3/tag/{id}`  ()

### radarr / TagDetails (2)

- `GET /api/v3/tag/detail`  ()
- `GET /api/v3/tag/detail/{id}`  ()

### radarr / Task (2)

- `GET /api/v3/system/task`  ()
- `GET /api/v3/system/task/{id}`  ()

### radarr / UiConfig (3)

- `PUT /api/v3/config/ui/{id}`  ()
- `GET /api/v3/config/ui/{id}`  ()
- `GET /api/v3/config/ui`  ()

### radarr / Update (1)

- `GET /api/v3/update`  ()

### radarr / UpdateLogFile (2)

- `GET /api/v3/log/file/update`  ()
- `GET /api/v3/log/file/update/{filename}`  ()

## sonarr (167 paths, spec version 3.0.0)

### sonarr / ApiInfo (1)

- `GET /api`  ()

### sonarr / Authentication (5)

- `GET /login/sso`  ()
- `POST /login`  ()
- `POST /reset-password/request`  ()
- `POST /reset-password`  ()
- `GET /logout`  ()

### sonarr / AutoTagging (6)

- `POST /api/v3/autotagging`  ()
- `GET /api/v3/autotagging`  ()
- `PUT /api/v3/autotagging/{id}`  ()
- `DELETE /api/v3/autotagging/{id}`  ()
- `GET /api/v3/autotagging/{id}`  ()
- `GET /api/v3/autotagging/schema`  ()

### sonarr / Backup (4)

- `GET /api/v3/system/backup`  ()
- `DELETE /api/v3/system/backup/{id}`  ()
- `POST /api/v3/system/backup/restore/{id}`  ()
- `POST /api/v3/system/backup/restore/upload`  ()

### sonarr / Blocklist (3)

- `GET /api/v3/blocklist`  ()
- `DELETE /api/v3/blocklist/{id}`  ()
- `DELETE /api/v3/blocklist/bulk`  ()

### sonarr / Calendar (2)

- `GET /api/v3/calendar`  ()
- `GET /api/v3/calendar/{id}`  ()

### sonarr / CalendarFeed (1)

- `GET /feed/v3/calendar/sonarr.ics`  ()

### sonarr / Command (4)

- `POST /api/v3/command`  ()
- `GET /api/v3/command`  ()
- `DELETE /api/v3/command/{id}`  ()
- `GET /api/v3/command/{id}`  ()

### sonarr / CustomFilter (5)

- `GET /api/v3/customfilter`  ()
- `POST /api/v3/customfilter`  ()
- `PUT /api/v3/customfilter/{id}`  ()
- `DELETE /api/v3/customfilter/{id}`  ()
- `GET /api/v3/customfilter/{id}`  ()

### sonarr / CustomFormat (8)

- `GET /api/v3/customformat`  ()
- `POST /api/v3/customformat`  ()
- `PUT /api/v3/customformat/{id}`  ()
- `DELETE /api/v3/customformat/{id}`  ()
- `GET /api/v3/customformat/{id}`  ()
- `PUT /api/v3/customformat/bulk`  ()
- `DELETE /api/v3/customformat/bulk`  ()
- `GET /api/v3/customformat/schema`  ()

### sonarr / Cutoff (2)

- `GET /api/v3/wanted/cutoff`  ()
- `GET /api/v3/wanted/cutoff/{id}`  ()

### sonarr / DelayProfile (6)

- `POST /api/v3/delayprofile`  ()
- `GET /api/v3/delayprofile`  ()
- `DELETE /api/v3/delayprofile/{id}`  ()
- `PUT /api/v3/delayprofile/{id}`  ()
- `GET /api/v3/delayprofile/{id}`  ()
- `PUT /api/v3/delayprofile/reorder/{id}`  ()

### sonarr / DiskSpace (1)

- `GET /api/v3/diskspace`  ()

### sonarr / DownloadClient (11)

- `GET /api/v3/downloadclient`  ()
- `POST /api/v3/downloadclient`  ()
- `PUT /api/v3/downloadclient/{id}`  ()
- `DELETE /api/v3/downloadclient/{id}`  ()
- `GET /api/v3/downloadclient/{id}`  ()
- `PUT /api/v3/downloadclient/bulk`  ()
- `DELETE /api/v3/downloadclient/bulk`  ()
- `GET /api/v3/downloadclient/schema`  ()
- `POST /api/v3/downloadclient/test`  ()
- `POST /api/v3/downloadclient/testall`  ()
- `POST /api/v3/downloadclient/action/{name}`  ()

### sonarr / DownloadClientConfig (3)

- `GET /api/v3/config/downloadclient`  ()
- `PUT /api/v3/config/downloadclient/{id}`  ()
- `GET /api/v3/config/downloadclient/{id}`  ()

### sonarr / Episode (4)

- `GET /api/v3/episode`  ()
- `PUT /api/v3/episode/{id}`  ()
- `GET /api/v3/episode/{id}`  ()
- `PUT /api/v3/episode/monitor`  ()

### sonarr / EpisodeFile (7)

- `GET /api/v3/episodefile`  ()
- `PUT /api/v3/episodefile/{id}`  ()
- `DELETE /api/v3/episodefile/{id}`  ()
- `GET /api/v3/episodefile/{id}`  ()
- `PUT /api/v3/episodefile/editor`  ()
- `DELETE /api/v3/episodefile/bulk`  ()
- `PUT /api/v3/episodefile/bulk`  ()

### sonarr / FileSystem (3)

- `GET /api/v3/filesystem`  ()
- `GET /api/v3/filesystem/type`  ()
- `GET /api/v3/filesystem/mediafiles`  ()

### sonarr / Health (1)

- `GET /api/v3/health`  ()

### sonarr / History (4)

- `GET /api/v3/history`  ()
- `GET /api/v3/history/since`  ()
- `GET /api/v3/history/series`  ()
- `POST /api/v3/history/failed/{id}`  ()

### sonarr / HostConfig (3)

- `GET /api/v3/config/host`  ()
- `PUT /api/v3/config/host/{id}`  ()
- `GET /api/v3/config/host/{id}`  ()

### sonarr / ImportList (11)

- `GET /api/v3/importlist`  ()
- `POST /api/v3/importlist`  ()
- `PUT /api/v3/importlist/{id}`  ()
- `DELETE /api/v3/importlist/{id}`  ()
- `GET /api/v3/importlist/{id}`  ()
- `PUT /api/v3/importlist/bulk`  ()
- `DELETE /api/v3/importlist/bulk`  ()
- `GET /api/v3/importlist/schema`  ()
- `POST /api/v3/importlist/test`  ()
- `POST /api/v3/importlist/testall`  ()
- `POST /api/v3/importlist/action/{name}`  ()

### sonarr / ImportListConfig (3)

- `GET /api/v3/config/importlist`  ()
- `PUT /api/v3/config/importlist/{id}`  ()
- `GET /api/v3/config/importlist/{id}`  ()

### sonarr / ImportListExclusion (7)

- `GET /api/v3/importlistexclusion`  ()
- `POST /api/v3/importlistexclusion`  ()
- `GET /api/v3/importlistexclusion/paged`  ()
- `PUT /api/v3/importlistexclusion/{id}`  ()
- `DELETE /api/v3/importlistexclusion/{id}`  ()
- `GET /api/v3/importlistexclusion/{id}`  ()
- `DELETE /api/v3/importlistexclusion/bulk`  ()

### sonarr / Indexer (11)

- `GET /api/v3/indexer`  ()
- `POST /api/v3/indexer`  ()
- `PUT /api/v3/indexer/{id}`  ()
- `DELETE /api/v3/indexer/{id}`  ()
- `GET /api/v3/indexer/{id}`  ()
- `PUT /api/v3/indexer/bulk`  ()
- `DELETE /api/v3/indexer/bulk`  ()
- `GET /api/v3/indexer/schema`  ()
- `POST /api/v3/indexer/test`  ()
- `POST /api/v3/indexer/testall`  ()
- `POST /api/v3/indexer/action/{name}`  ()

### sonarr / IndexerConfig (3)

- `GET /api/v3/config/indexer`  ()
- `PUT /api/v3/config/indexer/{id}`  ()
- `GET /api/v3/config/indexer/{id}`  ()

### sonarr / IndexerFlag (1)

- `GET /api/v3/indexerflag`  ()

### sonarr / Language (2)

- `GET /api/v3/language`  ()
- `GET /api/v3/language/{id}`  ()

### sonarr / LanguageProfile (5)

- `POST /api/v3/languageprofile`  ()
- `GET /api/v3/languageprofile`  ()
- `DELETE /api/v3/languageprofile/{id}`  ()
- `PUT /api/v3/languageprofile/{id}`  ()
- `GET /api/v3/languageprofile/{id}`  ()

### sonarr / LanguageProfileSchema (1)

- `GET /api/v3/languageprofile/schema`  ()

### sonarr / Localization (3)

- `GET /api/v3/localization`  ()
- `GET /api/v3/localization/language`  ()
- `GET /api/v3/localization/{id}`  ()

### sonarr / Log (1)

- `GET /api/v3/log`  ()

### sonarr / LogFile (2)

- `GET /api/v3/log/file`  ()
- `GET /api/v3/log/file/{filename}`  ()

### sonarr / ManualImport (2)

- `GET /api/v3/manualimport`  ()
- `POST /api/v3/manualimport`  ()

### sonarr / MediaCover (1)

- `GET /api/v3/mediacover/{seriesId}/{filename}`  ()

### sonarr / MediaManagementConfig (3)

- `GET /api/v3/config/mediamanagement`  ()
- `PUT /api/v3/config/mediamanagement/{id}`  ()
- `GET /api/v3/config/mediamanagement/{id}`  ()

### sonarr / Metadata (9)

- `GET /api/v3/metadata`  ()
- `POST /api/v3/metadata`  ()
- `PUT /api/v3/metadata/{id}`  ()
- `DELETE /api/v3/metadata/{id}`  ()
- `GET /api/v3/metadata/{id}`  ()
- `GET /api/v3/metadata/schema`  ()
- `POST /api/v3/metadata/test`  ()
- `POST /api/v3/metadata/testall`  ()
- `POST /api/v3/metadata/action/{name}`  ()

### sonarr / Missing (2)

- `GET /api/v3/wanted/missing`  ()
- `GET /api/v3/wanted/missing/{id}`  ()

### sonarr / NamingConfig (4)

- `GET /api/v3/config/naming`  ()
- `PUT /api/v3/config/naming/{id}`  ()
- `GET /api/v3/config/naming/{id}`  ()
- `GET /api/v3/config/naming/examples`  ()

### sonarr / Notification (9)

- `GET /api/v3/notification`  ()
- `POST /api/v3/notification`  ()
- `PUT /api/v3/notification/{id}`  ()
- `DELETE /api/v3/notification/{id}`  ()
- `GET /api/v3/notification/{id}`  ()
- `GET /api/v3/notification/schema`  ()
- `POST /api/v3/notification/test`  ()
- `POST /api/v3/notification/testall`  ()
- `POST /api/v3/notification/action/{name}`  ()

### sonarr / Parse (1)

- `GET /api/v3/parse`  ()

### sonarr / Ping (1)

- `GET /ping`  ()

### sonarr / QualityDefinition (5)

- `PUT /api/v3/qualitydefinition/{id}`  ()
- `GET /api/v3/qualitydefinition/{id}`  ()
- `GET /api/v3/qualitydefinition`  ()
- `PUT /api/v3/qualitydefinition/update`  ()
- `GET /api/v3/qualitydefinition/limits`  ()

### sonarr / QualityProfile (5)

- `POST /api/v3/qualityprofile`  ()
- `GET /api/v3/qualityprofile`  ()
- `DELETE /api/v3/qualityprofile/{id}`  ()
- `PUT /api/v3/qualityprofile/{id}`  ()
- `GET /api/v3/qualityprofile/{id}`  ()

### sonarr / QualityProfileSchema (1)

- `GET /api/v3/qualityprofile/schema`  ()

### sonarr / Queue (3)

- `DELETE /api/v3/queue/{id}`  ()
- `DELETE /api/v3/queue/bulk`  ()
- `GET /api/v3/queue`  ()

### sonarr / QueueAction (2)

- `POST /api/v3/queue/grab/{id}`  ()
- `POST /api/v3/queue/grab/bulk`  ()

### sonarr / QueueDetails (1)

- `GET /api/v3/queue/details`  ()

### sonarr / QueueStatus (1)

- `GET /api/v3/queue/status`  ()

### sonarr / Release (2)

- `POST /api/v3/release`  ()
- `GET /api/v3/release`  ()

### sonarr / ReleaseProfile (5)

- `POST /api/v3/releaseprofile`  ()
- `GET /api/v3/releaseprofile`  ()
- `DELETE /api/v3/releaseprofile/{id}`  ()
- `PUT /api/v3/releaseprofile/{id}`  ()
- `GET /api/v3/releaseprofile/{id}`  ()

### sonarr / ReleasePush (1)

- `POST /api/v3/release/push`  ()

### sonarr / RemotePathMapping (5)

- `POST /api/v3/remotepathmapping`  ()
- `GET /api/v3/remotepathmapping`  ()
- `DELETE /api/v3/remotepathmapping/{id}`  ()
- `PUT /api/v3/remotepathmapping/{id}`  ()
- `GET /api/v3/remotepathmapping/{id}`  ()

### sonarr / RenameEpisode (2)

- `GET /api/v3/rename`  ()
- `GET /api/v3/rename/bulk`  ()

### sonarr / RootFolder (4)

- `POST /api/v3/rootfolder`  ()
- `GET /api/v3/rootfolder`  ()
- `DELETE /api/v3/rootfolder/{id}`  ()
- `GET /api/v3/rootfolder/{id}`  ()

### sonarr / SeasonPass (1)

- `POST /api/v3/seasonpass`  ()

### sonarr / Series (5)

- `GET /api/v3/series`  ()
- `POST /api/v3/series`  ()
- `GET /api/v3/series/{id}`  ()
- `PUT /api/v3/series/{id}`  ()
- `DELETE /api/v3/series/{id}`  ()

### sonarr / SeriesEditor (2)

- `PUT /api/v3/series/editor`  ()
- `DELETE /api/v3/series/editor`  ()

### sonarr / SeriesFolder (1)

- `GET /api/v3/series/{id}/folder`  ()

### sonarr / SeriesImport (1)

- `POST /api/v3/series/import`  ()

### sonarr / SeriesLookup (1)

- `GET /api/v3/series/lookup`  ()

### sonarr / StaticResource (5)

- `GET /login`  ()
- `GET /loggedout`  ()
- `GET /content/{path}`  ()
- `GET /`  ()
- `GET /{path}`  ()

### sonarr / System (5)

- `GET /api/v3/system/status`  ()
- `GET /api/v3/system/routes`  ()
- `GET /api/v3/system/routes/duplicate`  ()
- `POST /api/v3/system/shutdown`  ()
- `POST /api/v3/system/restart`  ()

### sonarr / Tag (5)

- `GET /api/v3/tag`  ()
- `POST /api/v3/tag`  ()
- `PUT /api/v3/tag/{id}`  ()
- `DELETE /api/v3/tag/{id}`  ()
- `GET /api/v3/tag/{id}`  ()

### sonarr / TagDetails (2)

- `GET /api/v3/tag/detail`  ()
- `GET /api/v3/tag/detail/{id}`  ()

### sonarr / Task (2)

- `GET /api/v3/system/task`  ()
- `GET /api/v3/system/task/{id}`  ()

### sonarr / UiConfig (3)

- `PUT /api/v3/config/ui/{id}`  ()
- `GET /api/v3/config/ui/{id}`  ()
- `GET /api/v3/config/ui`  ()

### sonarr / Update (1)

- `GET /api/v3/update`  ()

### sonarr / UpdateLogFile (2)

- `GET /api/v3/log/file/update`  ()
- `GET /api/v3/log/file/update/{filename}`  ()
