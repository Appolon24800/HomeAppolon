package dev.appolon.homelab.arr.feature.lidarr.detail.data

import dev.appolon.homelab.arr.api.lidarr.AlbumApi
import dev.appolon.homelab.arr.api.lidarr.ArtistApi
import dev.appolon.homelab.arr.api.lidarr.HistoryApi
import dev.appolon.homelab.arr.api.lidarr.ManualImportApi
import dev.appolon.homelab.arr.api.lidarr.ReleaseApi
import dev.appolon.homelab.arr.api.lidarr.RenameTrackApi
import dev.appolon.homelab.arr.api.lidarr.RetagTrackApi
import dev.appolon.homelab.arr.api.lidarr.TrackApi
import dev.appolon.homelab.arr.api.lidarr.TrackFileApi
import dev.appolon.homelab.arr.api.lidarr.model.AlbumResource
import dev.appolon.homelab.arr.api.lidarr.model.AlbumsMonitoredResource
import dev.appolon.homelab.arr.api.lidarr.model.ArtistResource
import dev.appolon.homelab.arr.api.lidarr.model.HistoryResource
import dev.appolon.homelab.arr.api.lidarr.model.ManualImportResource
import dev.appolon.homelab.arr.api.lidarr.model.ManualImportUpdateResource
import dev.appolon.homelab.arr.api.lidarr.model.MetadataProfileResource
import dev.appolon.homelab.arr.api.lidarr.model.QualityDefinitionResource
import dev.appolon.homelab.arr.api.lidarr.model.QualityProfileResource
import dev.appolon.homelab.arr.api.lidarr.model.ReleaseResource
import dev.appolon.homelab.arr.api.lidarr.model.RenameTrackResource
import dev.appolon.homelab.arr.api.lidarr.model.RetagTrackResource
import dev.appolon.homelab.arr.api.lidarr.model.TagResource
import dev.appolon.homelab.arr.api.lidarr.model.TrackFileResource
import dev.appolon.homelab.arr.api.lidarr.model.TrackResource
import dev.appolon.homelab.arr.core.ActionId
import dev.appolon.homelab.arr.core.AppType
import dev.appolon.homelab.arr.core.ArrError
import dev.appolon.homelab.arr.core.ArrResult
import dev.appolon.homelab.arr.core.EntityType
import dev.appolon.homelab.arr.core.ServerId
import dev.appolon.homelab.arr.data.ActionQueue
import dev.appolon.homelab.arr.data.EntityStore
import dev.appolon.homelab.arr.data.QueuedAction
import dev.appolon.homelab.arr.feature.lidarr.detail.LidarrCommandPoster
import dev.appolon.homelab.arr.feature.lidarr.library.data.WriteOutcome
import dev.appolon.homelab.arr.net.ArrJson
import dev.appolon.homelab.arr.net.arrError
import java.util.UUID
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.addAll
import retrofit2.HttpException

/** Command names the artist detail screen dispatches. */
enum class ArtistDetailCommand(val wireName: String) {
    REFRESH_METADATA("RefreshArtist"),
    SCAN_DISK("RescanArtist"),
    ARTIST_SEARCH("ArtistSearch"),
    ALBUM_SEARCH("AlbumSearch"),
    RENAME_FILES("RenameFiles"),
    RETAG_FILES("RetagFiles"),
}

/** Builds the JSON root for one command; selection ids live at the root. */
object LidarrCommandBodies {
    fun refreshArtist(artistId: Int): String =
        root(ArtistDetailCommand.REFRESH_METADATA.wireName) { put("artistId", artistId) }

    fun rescanArtist(artistId: Int): String =
        root(ArtistDetailCommand.SCAN_DISK.wireName) { put("artistId", artistId) }

    fun artistSearch(artistId: Int): String =
        root(ArtistDetailCommand.ARTIST_SEARCH.wireName) { put("artistId", artistId) }

    fun albumSearch(albumId: Int): String =
        root(ArtistDetailCommand.ALBUM_SEARCH.wireName) {
            putJsonArray("albumIds") { addAll(listOf(albumId)) }
        }

    fun renameFiles(trackFileIds: List<Int>): String =
        root(ArtistDetailCommand.RENAME_FILES.wireName) {
            putJsonArray("files") { addAll(trackFileIds) }
        }

    fun retagFiles(trackFileIds: List<Int>): String =
        root(ArtistDetailCommand.RETAG_FILES.wireName) {
            putJsonArray("files") { addAll(trackFileIds) }
        }

    private inline fun root(
        name: String,
        build: kotlinx.serialization.json.JsonObjectBuilder.() -> Unit,
    ): String = buildJsonObject {
        put("name", name)
        build()
    }.toString()
}

/**
 * Artist detail repository. Reads are cache-first through the [EntityStore]
 * with a fetchedAt TTL; writes hit the server first and, when the network
 * fails, apply optimistically to the cache and enqueue for replay through
 * the [ActionQueue]. The artist and album entity type values match the
 * library's so both screens share one cache. No function throws.
 */
class LidarrArtistDetailRepository(
    private val serverId: ServerId,
    private val store: EntityStore,
    private val artistApi: ArtistApi,
    private val albumApi: AlbumApi,
    private val trackApi: TrackApi,
    private val trackFileApi: TrackFileApi,
    private val renameTrackApi: RenameTrackApi,
    private val retagTrackApi: RetagTrackApi,
    private val historyApi: HistoryApi,
    private val releaseApi: ReleaseApi,
    private val manualImportApi: ManualImportApi,
    private val qualityProfileApi: suspend () -> List<QualityProfileResource>,
    private val qualityDefinitionApi: suspend () -> List<QualityDefinitionResource>,
    private val metadataProfileApi: suspend () -> List<MetadataProfileResource>,
    private val tagApi: suspend () -> List<TagResource>,
    private val commandPoster: LidarrCommandPoster,
    private val actionQueue: ActionQueue,
    private val now: () -> Long = System::currentTimeMillis,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {

    /** Live artist row; emits again after every write-through. */
    fun observeArtist(artistId: Int): Flow<ArtistResource?> =
        store.observe(serverId, ARTIST_TYPE, ArtistResource.serializer())
            .map { rows -> rows.firstOrNull { it.id == artistId } }

    suspend fun cachedArtist(artistId: Int): ArtistResource? =
        store.get(serverId, ARTIST_TYPE, artistId.toString(), ArtistResource.serializer())

    /** True when the shared artist cache is absent or older than [ttlMillis]. */
    suspend fun artistStale(ttlMillis: Long = ARTIST_TTL_MILLIS): Boolean {
        val fetched = store.lastFetchedAt(serverId, ARTIST_TYPE) ?: return true
        return now() - fetched > ttlMillis
    }

    /** Fetches one artist and writes it through. */
    suspend fun refreshArtist(artistId: Int): ArrResult<ArtistResource> =
        withContext(ioDispatcher) {
            val fetched = try {
                artistApi.getArtist(artistId)
            } catch (e: HttpException) {
                return@withContext ArrResult.err(e.toArrError())
            } catch (e: Throwable) {
                return@withContext ArrResult.err(e.arrError())
            }
            store.put(serverId, ARTIST_TYPE, artistId.toString(), fetched, ARTIST_SER)
            ArrResult.ok(fetched)
        }

    /** All cached albums of one artist. */
    fun observeAlbums(artistId: Int): Flow<List<AlbumResource>> =
        store.observe(serverId, ALBUM_TYPE, AlbumResource.serializer())
            .map { rows -> rows.filter { it.artistId == artistId } }

    /**
     * Fetches the artist's albums, then reconciles this artist's cache rows
     * (removes rows the server dropped).
     */
    suspend fun refreshAlbums(artistId: Int): ArrResult<Unit> = withContext(ioDispatcher) {
        val fetched = try {
            albumApi.getAlbums(
                artistId = artistId,
                albumIds = null,
                foreignAlbumId = null,
                includeAllArtistAlbums = null,
            )
        } catch (e: HttpException) {
            return@withContext ArrResult.err(e.toArrError())
        } catch (e: Throwable) {
            return@withContext ArrResult.err(e.arrError())
        }
        reconcile(ALBUM_TYPE, fetched, cachedAlbumsOf(artistId)) { it.id.toString() }
        store.putAll(
            serverId,
            ALBUM_TYPE,
            fetched.map { it.id.toString() to it },
            ALBUM_SER,
            fetchedAtMillis = now(),
        )
        ArrResult.ok(Unit)
    }

    /** All cached tracks of one artist, files embedded. */
    fun observeTracks(artistId: Int): Flow<List<TrackResource>> =
        store.observe(serverId, TRACK_TYPE, TrackResource.serializer())
            .map { rows -> rows.filter { it.artistId == artistId } }

    /** Fetches every track of the artist with its file embedded, then reconciles. */
    suspend fun refreshTracks(artistId: Int): ArrResult<Unit> = withContext(ioDispatcher) {
        val fetched = try {
            trackApi.getTracks(
                artistId = artistId,
                albumId = null,
                albumReleaseId = null,
                trackIds = null,
            )
        } catch (e: HttpException) {
            return@withContext ArrResult.err(e.toArrError())
        } catch (e: Throwable) {
            return@withContext ArrResult.err(e.arrError())
        }
        reconcile(TRACK_TYPE, fetched, cachedTracksOf(artistId)) { it.id.toString() }
        store.putAll(
            serverId,
            TRACK_TYPE,
            fetched.map { it.id.toString() to it },
            TRACK_SER,
            fetchedAtMillis = now(),
        )
        ArrResult.ok(Unit)
    }

    /** History events of one artist, newest first. */
    fun observeHistory(artistId: Int): Flow<List<HistoryResource>> =
        store.observe(serverId, HISTORY_TYPE, HistoryResource.serializer())
            .map { rows ->
                rows.filter { it.artistId == artistId }
                    .sortedByDescending { it.date }
            }

    /** Fetches the artist history feed and reconciles its cached rows. */
    suspend fun refreshHistory(artistId: Int): ArrResult<Unit> = withContext(ioDispatcher) {
        val fetched = try {
            historyApi.getArtistHistory(
                artistId = artistId,
                albumId = null,
                eventType = null,
                includeArtist = false,
                includeAlbum = true,
                includeTrack = true,
            )
        } catch (e: HttpException) {
            return@withContext ArrResult.err(e.toArrError())
        } catch (e: Throwable) {
            return@withContext ArrResult.err(e.arrError())
        }
        reconcile(HISTORY_TYPE, fetched, cachedHistoryOf(artistId)) { it.id.toString() }
        store.putAll(
            serverId,
            HISTORY_TYPE,
            fetched.map { it.id.toString() to it },
            HISTORY_SER,
            fetchedAtMillis = now(),
        )
        ArrResult.ok(Unit)
    }

    /** Cache-first lookup family, each with its own TTL. */
    suspend fun qualityProfiles(ttlMillis: Long = LOOKUP_TTL_MILLIS) = lookup(
        QUALITY_PROFILE_TYPE,
        QualityProfileResource.serializer(),
        { it.id.toString() },
        qualityProfileApi,
        ttlMillis,
    )

    suspend fun qualityDefinitions(ttlMillis: Long = LOOKUP_TTL_MILLIS) = lookup(
        QUALITY_TYPE,
        QualityDefinitionResource.serializer(),
        { it.id.toString() },
        qualityDefinitionApi,
        ttlMillis,
    )

    suspend fun metadataProfiles(ttlMillis: Long = LOOKUP_TTL_MILLIS) = lookup(
        METADATA_PROFILE_TYPE,
        MetadataProfileResource.serializer(),
        { it.id.toString() },
        metadataProfileApi,
        ttlMillis,
    )

    suspend fun tags(ttlMillis: Long = LOOKUP_TTL_MILLIS) = lookup(
        TAG_TYPE,
        TagResource.serializer(),
        { it.id.toString() },
        tagApi,
        ttlMillis,
    )

    private suspend fun <T> lookup(
        type: EntityType,
        serializer: kotlinx.serialization.KSerializer<T>,
        idOf: (T) -> String,
        fetch: suspend () -> List<T>,
        ttlMillis: Long,
    ): ArrResult<List<T>> = withContext(ioDispatcher) {
        val cached = store.getAll(serverId, type, serializer)
        val fetchedAt = store.lastFetchedAt(serverId, type)
        if (fetchedAt != null && now() - fetchedAt <= ttlMillis && cached.isNotEmpty()) {
            return@withContext ArrResult.ok(cached)
        }
        val remote = try {
            fetch()
        } catch (e: HttpException) {
            return@withContext ArrResult.err(e.toArrError())
        } catch (e: Throwable) {
            return@withContext ArrResult.err(e.arrError())
        }
        if (remote.isNotEmpty()) {
            store.putAll(
                serverId,
                type,
                remote.map { idOf(it) to it },
                serializer,
                fetchedAtMillis = now(),
            )
            ArrResult.ok(remote)
        } else {
            ArrResult.ok(cached)
        }
    }

    /**
     * Artist-level monitored toggle through the artist update endpoint. The
     * response is authoritative and written through; offline, the merged
     * resource lands in the cache and the PUT is queued.
     */
    suspend fun setArtistMonitored(
        artist: ArtistResource,
        monitored: Boolean,
    ): ArrResult<WriteOutcome> = withContext(ioDispatcher) {
        updateArtistResource(artist.copy(monitored = monitored), moveFiles = false)
    }

    /**
     * Full artist edit from the edit sheet. `moveFiles` asks the server to
     * relocate files after a path change.
     */
    suspend fun updateArtist(
        artist: ArtistResource,
        moveFiles: Boolean,
    ): ArrResult<WriteOutcome> = withContext(ioDispatcher) {
        updateArtistResource(artist, moveFiles)
    }

    private suspend fun updateArtistResource(
        updated: ArtistResource,
        moveFiles: Boolean,
    ): ArrResult<WriteOutcome> = try {
        val saved = artistApi.updateArtist(updated.id.toString(), moveFiles, updated)
        store.put(serverId, ARTIST_TYPE, saved.id.toString(), saved, ARTIST_SER)
        ArrResult.ok(WriteOutcome.Synced)
    } catch (e: HttpException) {
        enqueueOrErr(
            e.toArrError(),
            "PUT",
            "api/v1/artist/${updated.id}",
            ArrJson.encodeToString(ARTIST_SER, updated),
        ) {
            store.put(serverId, ARTIST_TYPE, updated.id.toString(), updated, ARTIST_SER)
        }
    } catch (e: Throwable) {
        enqueueOrErr(
            e.arrError(),
            "PUT",
            "api/v1/artist/${updated.id}",
            ArrJson.encodeToString(ARTIST_SER, updated),
        ) {
            store.put(serverId, ARTIST_TYPE, updated.id.toString(), updated, ARTIST_SER)
        }
    }

    /**
     * Toggles monitoring for a set of albums. The endpoint returns no body,
     * so the cache rows flip optimistically on both the synced and queued
     * paths.
     */
    suspend fun setAlbumsMonitored(
        albumIds: List<Int>,
        monitored: Boolean,
    ): ArrResult<WriteOutcome> = withContext(ioDispatcher) {
        if (albumIds.isEmpty()) return@withContext ArrResult.ok(WriteOutcome.Synced)
        val body = AlbumsMonitoredResource(albumIds = albumIds, monitored = monitored)
        val path = "api/v1/album/monitor"
        try {
            albumApi.monitorAlbums(body)
            applyAlbumMonitored(albumIds, monitored)
            ArrResult.ok(WriteOutcome.Synced)
        } catch (e: HttpException) {
            enqueueOrErr(e.toArrError(), "PUT", path, ArrJson.encodeToString(MONITOR_SER, body)) {
                applyAlbumMonitored(albumIds, monitored)
            }
        } catch (e: Throwable) {
            enqueueOrErr(e.arrError(), "PUT", path, ArrJson.encodeToString(MONITOR_SER, body)) {
                applyAlbumMonitored(albumIds, monitored)
            }
        }
    }

    /**
     * Saves the track file editor's quality and release group choices. Both
     * paths update the embedded file on cached tracks.
     */
    suspend fun updateTrackFile(file: TrackFileResource): ArrResult<WriteOutcome> =
        withContext(ioDispatcher) {
            val path = "api/v1/trackfile/${file.id}"
            try {
                val saved = trackFileApi.updateTrackFile(file.id.toString(), file)
                applyTrackFile(saved)
                ArrResult.ok(WriteOutcome.Synced)
            } catch (e: HttpException) {
                enqueueOrErr(e.toArrError(), "PUT", path, ArrJson.encodeToString(FILE_SER, file)) {
                    applyTrackFile(file)
                }
            } catch (e: Throwable) {
                enqueueOrErr(e.arrError(), "PUT", path, ArrJson.encodeToString(FILE_SER, file)) {
                    applyTrackFile(file)
                }
            }
        }

    /**
     * Deletes one track file. Both paths detach the file from cached tracks
     * so the row shows missing immediately.
     */
    suspend fun deleteTrackFile(fileId: Int): ArrResult<WriteOutcome> = withContext(ioDispatcher) {
        val path = "api/v1/trackfile/$fileId"
        try {
            trackFileApi.deleteTrackFile(fileId)
            applyTrackFileRemoved(fileId)
            ArrResult.ok(WriteOutcome.Synced)
        } catch (e: HttpException) {
            enqueueOrErr(e.toArrError(), "DELETE", path, null) { applyTrackFileRemoved(fileId) }
        } catch (e: Throwable) {
            enqueueOrErr(e.arrError(), "DELETE", path, null) { applyTrackFileRemoved(fileId) }
        }
    }

    /** Delete with the two web UI flags; both paths clear this artist's cache rows. */
    suspend fun deleteArtist(
        artistId: Int,
        deleteFiles: Boolean,
        addImportListExclusion: Boolean,
    ): ArrResult<WriteOutcome> = withContext(ioDispatcher) {
        val path = "api/v1/artist/$artistId?deleteFiles=$deleteFiles" +
            "&addImportListExclusion=$addImportListExclusion"
        try {
            artistApi.deleteArtist(artistId, deleteFiles, addImportListExclusion)
            purgeArtistCache(artistId)
            ArrResult.ok(WriteOutcome.Synced)
        } catch (e: HttpException) {
            enqueueOrErr(e.toArrError(), "DELETE", path, null) { purgeArtistCache(artistId) }
        } catch (e: Throwable) {
            enqueueOrErr(e.arrError(), "DELETE", path, null) { purgeArtistCache(artistId) }
        }
    }

    /** Rename preview rows; a transient server computation, never cached. */
    suspend fun renamePreview(
        artistId: Int,
        albumId: Int?,
    ): ArrResult<List<RenameTrackResource>> = withContext(ioDispatcher) {
        try {
            ArrResult.ok(renameTrackApi.getTrackRenames(artistId = artistId, albumId = albumId))
        } catch (e: HttpException) {
            ArrResult.err(e.toArrError())
        } catch (e: Throwable) {
            ArrResult.err(e.arrError())
        }
    }

    /** Retag preview rows with MusicBrainz tag differences; transient, never cached. */
    suspend fun retagPreview(
        artistId: Int,
        albumId: Int?,
    ): ArrResult<List<RetagTrackResource>> = withContext(ioDispatcher) {
        try {
            ArrResult.ok(retagTrackApi.getTrackRetags(artistId = artistId, albumId = albumId))
        } catch (e: HttpException) {
            ArrResult.err(e.toArrError())
        } catch (e: Throwable) {
            ArrResult.err(e.arrError())
        }
    }

    /** Sends a command; offline commands queue and replay unchanged. */
    suspend fun runCommandBody(bodyJson: String, description: String): ArrResult<WriteOutcome> =
        withContext(ioDispatcher) {
            val failure = try {
                val result = commandPoster.post(bodyJson)
                if (result is ArrResult.Err) result.error else null
            } catch (e: Throwable) {
                e.arrError()
            }
            if (failure == null) {
                ArrResult.ok(WriteOutcome.Synced)
            } else if (failure.isRetryable()) {
                enqueueAction("POST", COMMAND_PATH, bodyJson, description)?.let {
                    ArrResult.ok(WriteOutcome.Queued(it))
                } ?: ArrResult.err(failure)
            } else {
                ArrResult.err(failure)
            }
        }

    /** Manual search releases for one album or the whole artist. */
    suspend fun releases(
        artistId: Int,
        albumId: Int?,
    ): ArrResult<List<ReleaseResource>> = withContext(ioDispatcher) {
        try {
            ArrResult.ok(releaseApi.getReleases(albumId = albumId, artistId = artistId))
        } catch (e: HttpException) {
            ArrResult.err(e.toArrError())
        } catch (e: Throwable) {
            ArrResult.err(e.arrError())
        }
    }

    /** Grabs one release; offline grabs queue. */
    suspend fun grabRelease(release: ReleaseResource): ArrResult<WriteOutcome> =
        withContext(ioDispatcher) {
            val path = "api/v1/release"
            try {
                releaseApi.grabRelease(release)
                ArrResult.ok(WriteOutcome.Synced)
            } catch (e: HttpException) {
                enqueueOrErr(
                    e.toArrError(),
                    "POST",
                    path,
                    ArrJson.encodeToString(RELEASE_SER, release),
                )
            } catch (e: Throwable) {
                enqueueOrErr(
                    e.arrError(),
                    "POST",
                    path,
                    ArrJson.encodeToString(RELEASE_SER, release),
                )
            }
        }

    /** Interactive import candidates under the artist folder. */
    suspend fun importCandidates(
        folder: String?,
        artistId: Int,
    ): ArrResult<List<ManualImportResource>> = withContext(ioDispatcher) {
        try {
            ArrResult.ok(
                manualImportApi.getManualImport(
                    folder = folder,
                    downloadId = null,
                    artistId = artistId,
                    filterExistingFiles = null,
                    replaceExistingFiles = null,
                ),
            )
        } catch (e: HttpException) {
            ArrResult.err(e.toArrError())
        } catch (e: Throwable) {
            ArrResult.err(e.arrError())
        }
    }

    /** Imports the chosen candidates; offline imports queue. */
    suspend fun importItems(items: List<ManualImportUpdateResource>): ArrResult<WriteOutcome> =
        withContext(ioDispatcher) {
            if (items.isEmpty()) return@withContext ArrResult.ok(WriteOutcome.Synced)
            val path = "api/v1/manualimport"
            val body = ArrJson.encodeToString(ListSerializer(IMPORT_SER), items)
            try {
                manualImportApi.updateManualImport(items)
                ArrResult.ok(WriteOutcome.Synced)
            } catch (e: HttpException) {
                enqueueOrErr(e.toArrError(), "POST", path, body)
            } catch (e: Throwable) {
                enqueueOrErr(e.arrError(), "POST", path, body)
            }
        }

    /** Artist ids touched by this screen's queued actions, for the pending badge. */
    fun queuedArtistIds(): Flow<Set<Int>> = actionQueue.observe().map { pending ->
        val ids = mutableSetOf<Int>()
        pending.filter { it.serverId == serverId }.forEach { action ->
            ids.addAll(artistIdsOf(action))
        }
        ids
    }

    /** Count of every queued action for this server. */
    fun queuedCount(): Flow<Int> = actionQueue.observe().map { pending ->
        pending.count { it.serverId == serverId }
    }

    /**
     * Maps one queued action onto the artist ids it touches. Direct artist
     * paths carry the id; album, track file, release, and command bodies
     * resolve through the cache so the badge survives mixed payloads.
     */
    private suspend fun artistIdsOf(action: QueuedAction): List<Int> {
        val ids = mutableSetOf<Int>()
        when {
            action.path.startsWith("api/v1/artist/") &&
                !action.path.startsWith("api/v1/artist/editor") ->
                action.path.substringAfter("api/v1/artist/").substringBefore('?')
                    .toIntOrNull()?.let(ids::add)

            action.path == "api/v1/album/monitor" ->
                runCatching {
                    ArrJson.decodeFromString(MONITOR_SER, action.bodyJson.orEmpty())
                }.getOrNull()
                    ?.let { body -> ids.addAll(artistIdsOfAlbums(body.albumIds.orEmpty())) }

            action.path.startsWith("api/v1/trackfile/") -> {
                val fileId = action.path.substringAfter("api/v1/trackfile/")
                    .substringBefore('?').toIntOrNull()
                if (fileId != null) {
                    ids.addAll(
                        cachedTracksAll().filter { it.trackFileId == fileId }.map { it.artistId },
                    )
                }
            }

            action.path == "api/v1/command" -> {
                val root = runCatching {
                    ArrJson.parseToJsonElement(action.bodyJson.orEmpty()) as? JsonObject
                }.getOrNull()
                root?.get("artistId")?.jsonPrimitive?.intOrNull?.let(ids::add)
                (root?.get("artistIds") as? JsonArray)?.forEach { element ->
                    element.jsonPrimitive.intOrNull?.let(ids::add)
                }
                val albumIds = (root?.get("albumIds") as? JsonArray)
                    ?.mapNotNull { it.jsonPrimitive.intOrNull }
                    .orEmpty()
                ids.addAll(artistIdsOfAlbums(albumIds))
            }

            action.path == "api/v1/release" ->
                runCatching { ArrJson.decodeFromString(RELEASE_SER, action.bodyJson.orEmpty()) }
                    .getOrNull()
                    ?.let { release ->
                        release.artistId?.let(ids::add)
                        ids.addAll(artistIdsOfAlbums(listOfNotNull(release.albumId)))
                    }

            action.path == "api/v1/manualimport" ->
                runCatching {
                    ArrJson.decodeFromString(ListSerializer(IMPORT_SER), action.bodyJson.orEmpty())
                }.getOrNull()
                    ?.let { rows -> ids.addAll(rows.mapNotNull { it.artistId }) }
        }
        return ids.toList()
    }

    private suspend fun artistIdsOfAlbums(albumIds: List<Int>): List<Int> {
        if (albumIds.isEmpty()) return emptyList()
        val targets = albumIds.toSet()
        return cachedAlbumsAll().filter { it.id in targets }.map { it.artistId }.distinct()
    }

    private suspend fun cachedAlbumsOf(artistId: Int): List<AlbumResource> =
        cachedAlbumsAll().filter { it.artistId == artistId }

    private suspend fun cachedAlbumsAll(): List<AlbumResource> =
        store.getAll(serverId, ALBUM_TYPE, ALBUM_SER)

    private suspend fun cachedTracksOf(artistId: Int): List<TrackResource> =
        cachedTracksAll().filter { it.artistId == artistId }

    private suspend fun cachedTracksAll(): List<TrackResource> =
        store.getAll(serverId, TRACK_TYPE, TRACK_SER)

    private suspend fun cachedHistoryOf(artistId: Int): List<HistoryResource> =
        store.getAll(serverId, HISTORY_TYPE, HISTORY_SER).filter { it.artistId == artistId }

    /** Removes rows of this collection that disappeared server-side. */
    private suspend fun <T> reconcile(
        type: EntityType,
        incoming: List<T>,
        existing: List<T>,
        idOf: (T) -> String,
    ) {
        val incomingIds = incoming.map(idOf).toSet()
        existing.forEach { row ->
            val id = idOf(row)
            if (id !in incomingIds) store.delete(serverId, type, id)
        }
    }

    private suspend fun applyAlbumMonitored(albumIds: List<Int>, monitored: Boolean) {
        val targets = albumIds.toSet()
        cachedAlbumsAll().filter { it.id in targets }.forEach { album ->
            store.put(
                serverId,
                ALBUM_TYPE,
                album.id.toString(),
                album.copy(monitored = monitored),
                ALBUM_SER,
            )
        }
    }

    private suspend fun applyTrackFile(file: TrackFileResource) {
        cachedTracksAll().filter { it.trackFileId == file.id }.forEach { track ->
            store.put(
                serverId,
                TRACK_TYPE,
                track.id.toString(),
                track.copy(
                    hasFile = true,
                    trackFileId = file.id,
                    trackFile = file,
                ),
                TRACK_SER,
            )
        }
    }

    private suspend fun applyTrackFileRemoved(fileId: Int) {
        cachedTracksAll().filter { it.trackFileId == fileId }.forEach { track ->
            store.put(
                serverId,
                TRACK_TYPE,
                track.id.toString(),
                track.copy(
                    hasFile = false,
                    trackFileId = 0,
                    trackFile = null,
                ),
                TRACK_SER,
            )
        }
    }

    private suspend fun purgeArtistCache(artistId: Int) {
        store.delete(serverId, ARTIST_TYPE, artistId.toString())
        cachedAlbumsOf(artistId).forEach { store.delete(serverId, ALBUM_TYPE, it.id.toString()) }
        cachedTracksOf(artistId).forEach { store.delete(serverId, TRACK_TYPE, it.id.toString()) }
        cachedHistoryOf(artistId).forEach { store.delete(serverId, HISTORY_TYPE, it.id.toString()) }
    }

    private suspend fun enqueueOrErr(
        error: ArrError,
        method: String,
        path: String,
        bodyJson: String?,
        optimistic: (suspend () -> Unit)? = null,
    ): ArrResult<WriteOutcome> {
        if (!error.isRetryable()) return ArrResult.err(error)
        optimistic?.invoke()
        val actionId = enqueueAction(method, path, bodyJson, describe(method, path))
            ?: return ArrResult.err(error)
        return ArrResult.ok(WriteOutcome.Queued(actionId))
    }

    private suspend fun enqueueAction(
        method: String,
        path: String,
        bodyJson: String?,
        description: String,
    ): ActionId? = try {
        val action = QueuedAction(
            actionId = ActionId(UUID.randomUUID().toString()),
            serverId = serverId,
            appType = AppType.LIDARR,
            method = method,
            path = path,
            bodyJson = bodyJson,
            description = description,
            enqueuedAtMillis = now(),
        )
        actionQueue.enqueue(action)
        action.actionId
    } catch (e: Throwable) {
        null
    }

    private fun describe(method: String, path: String): String = when {
        path.startsWith("api/v1/artist/") && method == "DELETE" -> "Delete artist"
        path.startsWith("api/v1/artist/") -> "Edit artist"
        path == "api/v1/album/monitor" -> "Album monitoring"
        path.startsWith("api/v1/trackfile/") && method == "DELETE" -> "Delete track file"
        path.startsWith("api/v1/trackfile/") -> "Edit track file"
        path == "api/v1/release" -> "Grab release"
        path == "api/v1/manualimport" -> "Interactive import"
        else -> "Lidarr write"
    }

    private fun ArrError.isRetryable(): Boolean =
        this is ArrError.Offline || this is ArrError.Network

    private fun HttpException.toArrError(): ArrError = when (code()) {
        401, 403 -> ArrError.Auth(code())
        else -> ArrError.Http(code(), message())
    }

    private companion object {
        /** Same values as the library's types, so both screens share the cache. */
        internal val ARTIST_TYPE = EntityType("lidarr.artist")
        internal val ALBUM_TYPE = EntityType("lidarr.album")
        internal val TRACK_TYPE = EntityType("lidarr.track")
        internal val HISTORY_TYPE = EntityType("lidarr.history")
        internal val QUALITY_PROFILE_TYPE = EntityType("lidarr.qualityprofile")
        internal val METADATA_PROFILE_TYPE = EntityType("lidarr.metadataprofile")
        internal val QUALITY_TYPE = EntityType("lidarr.qualitydefinition")
        internal val TAG_TYPE = EntityType("lidarr.tag")
        internal const val COMMAND_PATH = "api/v1/command"

        private val ARTIST_SER = ArtistResource.serializer()
        private val ALBUM_SER = AlbumResource.serializer()
        private val TRACK_SER = TrackResource.serializer()
        private val HISTORY_SER = HistoryResource.serializer()
        private val MONITOR_SER = AlbumsMonitoredResource.serializer()
        private val FILE_SER = TrackFileResource.serializer()
        private val RELEASE_SER = ReleaseResource.serializer()
        private val IMPORT_SER = ManualImportUpdateResource.serializer()

        /** Ten minutes, matching the library's artist TTL. */
        const val ARTIST_TTL_MILLIS: Long = 10 * 60 * 1000

        /** One hour; profiles, qualities, and tags change rarely. */
        const val LOOKUP_TTL_MILLIS: Long = 60 * 60 * 1000
    }
}
