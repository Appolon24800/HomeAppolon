package dev.appolon.homelab.arr.feature.lidarr.addnew.data

import dev.appolon.homelab.arr.api.lidarr.AlbumApi
import dev.appolon.homelab.arr.api.lidarr.AlbumLookupApi
import dev.appolon.homelab.arr.api.lidarr.AlbumStudioApi
import dev.appolon.homelab.arr.api.lidarr.ArtistApi
import dev.appolon.homelab.arr.api.lidarr.ArtistLookupApi
import dev.appolon.homelab.arr.api.lidarr.ImportListApi
import dev.appolon.homelab.arr.api.lidarr.MetadataProfileApi
import dev.appolon.homelab.arr.api.lidarr.QualityProfileApi
import dev.appolon.homelab.arr.api.lidarr.RootFolderApi
import dev.appolon.homelab.arr.api.lidarr.TagApi
import dev.appolon.homelab.arr.api.lidarr.model.AlbumResource
import dev.appolon.homelab.arr.api.lidarr.model.AlbumStudioResource
import dev.appolon.homelab.arr.api.lidarr.model.ArtistResource
import dev.appolon.homelab.arr.api.lidarr.model.ImportListResource
import dev.appolon.homelab.arr.api.lidarr.model.MetadataProfileResource
import dev.appolon.homelab.arr.api.lidarr.model.QualityProfileResource
import dev.appolon.homelab.arr.api.lidarr.model.RootFolderResource
import dev.appolon.homelab.arr.api.lidarr.model.TagResource
import dev.appolon.homelab.arr.core.ActionId
import dev.appolon.homelab.arr.core.AppType
import dev.appolon.homelab.arr.core.ArrError
import dev.appolon.homelab.arr.core.ArrResult
import dev.appolon.homelab.arr.core.EntityType
import dev.appolon.homelab.arr.core.ServerId
import dev.appolon.homelab.arr.data.ActionQueue
import dev.appolon.homelab.arr.data.EntityStore
import dev.appolon.homelab.arr.data.QueuedAction
import dev.appolon.homelab.arr.net.ArrJson
import dev.appolon.homelab.arr.net.arrError
import java.util.UUID
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import retrofit2.HttpException

/**
 * Repository behind the Add new screens: artist and album lookup, the
 * add-option catalogs (root folders, quality and metadata profiles, tags),
 * the import list browse, one artist's discography, and the add and
 * album-studio writes. Reads are cache-first through the [EntityStore] with
 * a fetchedAt TTL driving refresh; writes go to the server first and fall
 * back to optimistic cache rows plus [ActionQueue] enqueue when the failure
 * is retryable (offline or network). The artist and album rows share the
 * `lidarr.artist` / `lidarr.album` entity types with the library feature so
 * both screens observe one source of truth. Everything runs on IO; nothing
 * throws.
 */
class LidarrAddNewRepository(
    private val serverId: ServerId,
    private val store: EntityStore,
    private val artistLookupApi: ArtistLookupApi,
    private val artistApi: ArtistApi,
    private val albumLookupApi: AlbumLookupApi,
    private val albumApi: AlbumApi,
    private val albumStudioApi: AlbumStudioApi,
    private val importListApi: ImportListApi,
    private val rootFolderApi: RootFolderApi,
    private val tagApi: TagApi,
    private val qualityProfileApi: QualityProfileApi,
    private val metadataProfileApi: MetadataProfileApi,
    private val actionQueue: ActionQueue,
    private val now: () -> Long = System::currentTimeMillis,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) {

    /** Artist lookup runs server-side every time: MusicBrainz data changes. */
    suspend fun searchArtists(term: String): ArrResult<List<ArtistResource>> =
        withContext(io) {
            if (term.isBlank()) {
                return@withContext ArrResult.ok(emptyList())
            }
            val results = try {
                artistLookupApi.lookupArtists(term)
            } catch (e: HttpException) {
                return@withContext ArrResult.err(e.toArrError())
            } catch (e: Throwable) {
                return@withContext ArrResult.err(e.arrError())
            }
            ArrResult.ok(results)
        }

    /** Album lookup for standalone adds; uncached like artist lookup. */
    suspend fun searchAlbums(term: String): ArrResult<List<AlbumResource>> =
        withContext(io) {
            if (term.isBlank()) {
                return@withContext ArrResult.ok(emptyList())
            }
            val results = try {
                albumLookupApi.lookupAlbums(term)
            } catch (e: HttpException) {
                return@withContext ArrResult.err(e.toArrError())
            } catch (e: Throwable) {
                return@withContext ArrResult.err(e.arrError())
            }
            ArrResult.ok(results)
        }

    /** Library artists as observed by every Lidarr screen, for existing flags. */
    fun observeLibraryArtists(): Flow<List<ArtistResource>> =
        store.observe(serverId, ARTIST_TYPE, ArtistResource.serializer())

    /** Library albums as observed by every Lidarr screen. */
    fun observeLibraryAlbums(): Flow<List<AlbumResource>> =
        store.observe(serverId, ALBUM_TYPE, AlbumResource.serializer())

    /** Cache-first library sync feeding the in-library badges and the studio picker. */
    suspend fun syncLibraryIfStale(ttlMillis: Long = LIBRARY_TTL_MILLIS): ArrResult<Unit> =
        withContext(io) {
            val fetched = store.lastFetchedAt(serverId, ARTIST_TYPE)
            if (isFresh(ttlMillis, fetched)) {
                return@withContext ArrResult.ok(Unit)
            }
            val fetchedArtists = try {
                artistApi.getArtists(mbId = null)
            } catch (e: HttpException) {
                return@withContext ArrResult.err(e.toArrError())
            } catch (e: Throwable) {
                return@withContext ArrResult.err(e.arrError())
            }
            reconcileArtists(fetchedArtists)
            // Album badges degrade to whatever scoped fetches cached; the
            // unfiltered album list is tens of megabytes on large libraries
            // and one string materialization OOM-kills the process.
            ArrResult.ok(Unit)
        }

    /**
     * One artist's discography for the studio picker. Always refreshed on
     * open because the picker is entered deliberately; on failure the cached
     * rows serve the sheet. The write-through keeps other artists' rows and
     * optimistic offline adds untouched.
     */
    suspend fun discography(artistId: Int): ArrResult<List<AlbumResource>> = withContext(io) {
        val cached = cachedAlbumsOf(artistId)
        val fetched = try {
            albumApi.getAlbums(
                artistId = artistId,
                albumIds = null,
                foreignAlbumId = null,
                includeAllArtistAlbums = null,
            )
        } catch (e: HttpException) {
            return@withContext serveOrErr(cached, e.toArrError())
        } catch (e: Throwable) {
            return@withContext serveOrErr(cached, e.arrError())
        }
        if (fetched.isNotEmpty()) {
            store.putAll(
                serverId,
                ALBUM_TYPE,
                fetched.map { it.id.toString() to it },
                AlbumResource.serializer(),
            )
            ArrResult.ok(fetched)
        } else {
            ArrResult.ok(cached)
        }
    }

    private suspend fun cachedAlbumsOf(artistId: Int): List<AlbumResource> =
        store.getAll(serverId, ALBUM_TYPE, AlbumResource.serializer())
            .filter { it.artistId == artistId }

    /** Root folders for the picker, cached with their own TTL. */
    suspend fun rootFolders(
        ttlMillis: Long = CATALOG_TTL_MILLIS,
    ): ArrResult<List<RootFolderResource>> = withContext(io) {
        val cached = store.getAll(serverId, ROOT_FOLDER_TYPE, RootFolderResource.serializer())
        val fetchedAt = store.lastFetchedAt(serverId, ROOT_FOLDER_TYPE)
        if (isFresh(ttlMillis, fetchedAt) && cached.isNotEmpty()) {
            return@withContext ArrResult.ok(cached)
        }
        val folders = try {
            rootFolderApi.getRootFolders()
        } catch (e: HttpException) {
            return@withContext serveOrErr(cached, e.toArrError())
        } catch (e: Throwable) {
            return@withContext serveOrErr(cached, e.arrError())
        }
        store.putAll(
            serverId,
            ROOT_FOLDER_TYPE,
            folders.map { it.id.toString() to it },
            RootFolderResource.serializer(),
            fetchedAtMillis = now(),
        )
        ArrResult.ok(folders)
    }

    /** Quality profiles for the add sheet; rows shared with the library editor. */
    suspend fun qualityProfiles(
        ttlMillis: Long = CATALOG_TTL_MILLIS,
    ): ArrResult<List<QualityProfileResource>> = withContext(io) {
        val cached = store.getAll(serverId, QUALITY_PROFILE_TYPE, QualityProfileResource.serializer())
        val fetchedAt = store.lastFetchedAt(serverId, QUALITY_PROFILE_TYPE)
        if (isFresh(ttlMillis, fetchedAt) && cached.isNotEmpty()) {
            return@withContext ArrResult.ok(cached)
        }
        val profiles = try {
            qualityProfileApi.getQualityProfiles()
        } catch (e: HttpException) {
            return@withContext serveOrErr(cached, e.toArrError())
        } catch (e: Throwable) {
            return@withContext serveOrErr(cached, e.arrError())
        }
        if (profiles.isNotEmpty()) {
            store.putAll(
                serverId,
                QUALITY_PROFILE_TYPE,
                profiles.map { it.id.toString() to it },
                QualityProfileResource.serializer(),
                fetchedAtMillis = now(),
            )
            ArrResult.ok(profiles)
        } else {
            ArrResult.ok(cached)
        }
    }

    /** Metadata profiles for the add sheet; Lidarr-specific catalog. */
    suspend fun metadataProfiles(
        ttlMillis: Long = CATALOG_TTL_MILLIS,
    ): ArrResult<List<MetadataProfileResource>> = withContext(io) {
        val cached = store.getAll(
            serverId,
            METADATA_PROFILE_TYPE,
            MetadataProfileResource.serializer(),
        )
        val fetchedAt = store.lastFetchedAt(serverId, METADATA_PROFILE_TYPE)
        if (isFresh(ttlMillis, fetchedAt) && cached.isNotEmpty()) {
            return@withContext ArrResult.ok(cached)
        }
        val profiles = try {
            metadataProfileApi.getMetadataProfiles()
        } catch (e: HttpException) {
            return@withContext serveOrErr(cached, e.toArrError())
        } catch (e: Throwable) {
            return@withContext serveOrErr(cached, e.arrError())
        }
        if (profiles.isNotEmpty()) {
            store.putAll(
                serverId,
                METADATA_PROFILE_TYPE,
                profiles.map { it.id.toString() to it },
                MetadataProfileResource.serializer(),
                fetchedAtMillis = now(),
            )
            ArrResult.ok(profiles)
        } else {
            ArrResult.ok(cached)
        }
    }

    /** Tags for the add sheet chips. */
    suspend fun tags(ttlMillis: Long = CATALOG_TTL_MILLIS): ArrResult<List<TagResource>> =
        withContext(io) {
            val cached = store.getAll(serverId, TAG_TYPE, TagResource.serializer())
            val fetchedAt = store.lastFetchedAt(serverId, TAG_TYPE)
            if (isFresh(ttlMillis, fetchedAt) && cached.isNotEmpty()) {
                return@withContext ArrResult.ok(cached)
            }
            val fetched = try {
                tagApi.getTags()
            } catch (e: HttpException) {
                return@withContext serveOrErr(cached, e.toArrError())
            } catch (e: Throwable) {
                return@withContext serveOrErr(cached, e.arrError())
            }
            if (fetched.isNotEmpty()) {
                store.putAll(
                    serverId,
                    TAG_TYPE,
                    fetched.map { it.id.toString() to it },
                    TagResource.serializer(),
                    fetchedAtMillis = now(),
                )
                ArrResult.ok(fetched)
            } else {
                ArrResult.ok(cached)
            }
        }

    /** Import lists for the browse tab, cached under a short TTL. */
    suspend fun importLists(
        ttlMillis: Long = LIST_TTL_MILLIS,
    ): ArrResult<List<ImportListResource>> = withContext(io) {
        val cached = store.getAll(serverId, IMPORT_LIST_TYPE, ImportListResource.serializer())
        val fetchedAt = store.lastFetchedAt(serverId, IMPORT_LIST_TYPE)
        if (isFresh(ttlMillis, fetchedAt) && cached.isNotEmpty()) {
            return@withContext ArrResult.ok(cached)
        }
        val fetched = try {
            importListApi.getImportLists()
        } catch (e: HttpException) {
            return@withContext serveOrErr(cached, e.toArrError())
        } catch (e: Throwable) {
            return@withContext serveOrErr(cached, e.arrError())
        }
        if (fetched.isNotEmpty()) {
            store.putAll(
                serverId,
                IMPORT_LIST_TYPE,
                fetched.map { it.id.toString() to it },
                ImportListResource.serializer(),
                fetchedAtMillis = now(),
            )
            ArrResult.ok(fetched)
        } else {
            ArrResult.ok(cached)
        }
    }

    /**
     * Adds one artist (POST /api/v1/artist). On success the created artist
     * is written through to the cache. On a retryable failure the payload is
     * applied optimistically under a `add-mb-` key and the POST is queued.
     */
    suspend fun addArtist(payload: ArtistResource): ArrResult<AddOutcome> = withContext(io) {
        val created = try {
            artistApi.addArtist(payload)
        } catch (e: HttpException) {
            return@withContext offlineAddOrErr(e.toArrError(), payload)
        } catch (e: Throwable) {
            return@withContext offlineAddOrErr(e.arrError(), payload)
        }
        if (created.id != 0) {
            store.put(
                serverId,
                ARTIST_TYPE,
                created.id.toString(),
                created,
                ArtistResource.serializer(),
            )
        }
        ArrResult.ok(AddOutcome.Synced)
    }

    /**
     * Adds one standalone album (POST /api/v1/album). Same write-through and
     * offline-queue contract as [addArtist], keyed by MusicBrainz album id.
     */
    suspend fun addAlbum(payload: AlbumResource): ArrResult<AddOutcome> = withContext(io) {
        val created = try {
            albumApi.addAlbum(payload)
        } catch (e: HttpException) {
            return@withContext offlineAddOrErr(e.toArrError(), payload)
        } catch (e: Throwable) {
            return@withContext offlineAddOrErr(e.arrError(), payload)
        }
        if (created.id != 0) {
            store.put(
                serverId,
                ALBUM_TYPE,
                created.id.toString(),
                created,
                AlbumResource.serializer(),
            )
        }
        ArrResult.ok(AddOutcome.Synced)
    }

    /**
     * Applies an album-studio payload (POST /api/v1/albumstudio): the exact
     * per-album monitored state for one artist's discography. On success the
     * cache rows are updated to the same state; on a retryable failure the
     * state applies optimistically and the POST queues for replay.
     */
    suspend fun applyAlbumStudio(payload: AlbumStudioResource): ArrResult<AddOutcome> =
        withContext(io) {
            val artistId = payload.artist.orEmpty().firstOrNull()?.id ?: run {
                return@withContext ArrResult.ok(AddOutcome.Synced)
            }
            try {
                albumStudioApi.updateAlbumStudio(payload)
            } catch (e: HttpException) {
                return@withContext offlineStudioOrErr(e.toArrError(), payload, artistId)
            } catch (e: Throwable) {
                return@withContext offlineStudioOrErr(e.arrError(), payload, artistId)
            }
            applyStudioToCache(artistId, payload)
            ArrResult.ok(AddOutcome.Synced)
        }

    private suspend fun offlineStudioOrErr(
        error: ArrError,
        payload: AlbumStudioResource,
        artistId: Int,
    ): ArrResult<AddOutcome> {
        if (!error.isRetryable()) return ArrResult.err(error)
        applyStudioToCache(artistId, payload)
        val action = enqueue(
            method = "POST",
            path = STUDIO_PATH,
            bodyJson = ArrJson.encodeToString(AlbumStudioResource.serializer(), payload),
            description = "Update album studio",
        )
        return ArrResult.ok(AddOutcome.Queued(action))
    }

    private suspend fun applyStudioToCache(artistId: Int, payload: AlbumStudioResource) {
        val cached = cachedAlbumsOf(artistId)
        if (cached.isEmpty()) return
        val updated = AddNewLogic.applyStudioToAlbums(cached, payload)
        store.putAll(
            serverId,
            ALBUM_TYPE,
            updated.map { it.id.toString() to it },
            AlbumResource.serializer(),
        )
    }

    private suspend fun offlineAddOrErr(
        error: ArrError,
        payload: ArtistResource,
    ): ArrResult<AddOutcome> {
        if (!error.isRetryable()) return ArrResult.err(error)
        // Optimistic row keyed by MusicBrainz id so repeated offline adds of
        // the same artist collapse onto one row and the library observes it.
        store.put(
            serverId,
            ARTIST_TYPE,
            optimisticKey(payload.foreignArtistId ?: payload.mbId),
            payload,
            ArtistResource.serializer(),
        )
        val action = enqueue(
            method = "POST",
            path = ADD_ARTIST_PATH,
            bodyJson = ArrJson.encodeToString(ArtistResource.serializer(), payload),
            description = "Add artist " + (payload.artistName ?: payload.foreignArtistId),
        )
        return ArrResult.ok(AddOutcome.Queued(action))
    }

    private suspend fun offlineAddOrErr(
        error: ArrError,
        payload: AlbumResource,
    ): ArrResult<AddOutcome> {
        if (!error.isRetryable()) return ArrResult.err(error)
        store.put(
            serverId,
            ALBUM_TYPE,
            optimisticKey(payload.foreignAlbumId),
            payload,
            AlbumResource.serializer(),
        )
        val action = enqueue(
            method = "POST",
            path = ADD_ALBUM_PATH,
            bodyJson = ArrJson.encodeToString(AlbumResource.serializer(), payload),
            description = "Add album " + (payload.title ?: payload.foreignAlbumId),
        )
        return ArrResult.ok(AddOutcome.Queued(action))
    }

    /** MusicBrainz ids with an artist add queued, for per-result queued badges. */
    fun queuedForeignArtistIds(): Flow<Set<String>> = actionQueue.observe().map { pending ->
        pending.asSequence()
            .filter { it.serverId == serverId && it.path == ADD_ARTIST_PATH }
            .mapNotNull { action ->
                runCatching {
                    ArrJson.decodeFromString(
                        ArtistResource.serializer(),
                        action.bodyJson.orEmpty(),
                    )
                }.getOrNull()?.let { artist -> artist.foreignArtistId ?: artist.mbId }
            }
            .toSet()
    }

    /** MusicBrainz ids with an album add queued, for per-result queued badges. */
    fun queuedForeignAlbumIds(): Flow<Set<String>> = actionQueue.observe().map { pending ->
        pending.asSequence()
            .filter { it.serverId == serverId && it.path == ADD_ALBUM_PATH }
            .flatMap { action ->
                runCatching {
                    ArrJson.decodeFromString(
                        AlbumResource.serializer(),
                        action.bodyJson.orEmpty(),
                    )
                }.getOrNull()?.foreignAlbumId?.let { listOf(it) }.orEmpty()
            }
            .toSet()
    }

    /** Count of queued actions for this server, the add screen's sync banner. */
    fun queuedCount(): Flow<Int> = actionQueue.observe().map { pending ->
        pending.count { it.serverId == serverId }
    }

    /** Server rows replace by id; optimistic offline-add rows are preserved. */
    private suspend fun reconcileArtists(incoming: List<ArtistResource>) {
        val existing = store.getAll(serverId, ARTIST_TYPE, ArtistResource.serializer())
        val incomingIds = incoming.map { it.id.toString() }.toSet()
        existing.forEach { cached ->
            val key = cached.id.toString()
            if (cached.id != 0 && key !in incomingIds) {
                store.delete(serverId, ARTIST_TYPE, key)
            }
        }
        store.putAll(
            serverId,
            ARTIST_TYPE,
            incoming.map { it.id.toString() to it },
            ArtistResource.serializer(),
            fetchedAtMillis = now(),
        )
    }

    private suspend fun reconcileAlbums(incoming: List<AlbumResource>) {
        val existing = store.getAll(serverId, ALBUM_TYPE, AlbumResource.serializer())
        val incomingIds = incoming.map { it.id.toString() }.toSet()
        existing.forEach { cached ->
            val key = cached.id.toString()
            if (cached.id != 0 && key !in incomingIds) {
                store.delete(serverId, ALBUM_TYPE, key)
            }
        }
        store.putAll(
            serverId,
            ALBUM_TYPE,
            incoming.map { it.id.toString() to it },
            AlbumResource.serializer(),
        )
    }

    private fun <T> serveOrErr(cached: List<T>, error: ArrError): ArrResult<List<T>> =
        if (cached.isNotEmpty()) ArrResult.ok(cached) else ArrResult.err(error)

    /**
     * Freshness gate shared by every cache-first read: a positive TTL honors
     * the fetchedAt stamp, ttl 0 forces a network refresh (pull-to-refresh).
     */
    private fun isFresh(ttlMillis: Long, fetchedAt: Long?): Boolean =
        ttlMillis > 0 && fetchedAt != null && now() - fetchedAt <= ttlMillis

    private suspend fun enqueue(method: String, path: String, bodyJson: String, description: String): ActionId {
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
        return action.actionId
    }

    private fun ArrError.isRetryable(): Boolean =
        this is ArrError.Offline || this is ArrError.Network

    private fun HttpException.toArrError(): ArrError = when (code()) {
        401, 403 -> ArrError.Auth(code())
        else -> ArrError.Http(code(), message())
    }

    companion object {
        /** Same rows and TTL windows the library feature uses. */
        internal val ARTIST_TYPE = EntityType("lidarr.artist")
        internal val ALBUM_TYPE = EntityType("lidarr.album")
        internal val QUALITY_PROFILE_TYPE = EntityType("lidarr.qualityprofile")
        internal val METADATA_PROFILE_TYPE = EntityType("lidarr.metadataprofile")
        internal val ROOT_FOLDER_TYPE = EntityType("lidarr.rootfolder")
        internal val TAG_TYPE = EntityType("lidarr.tag")
        internal val IMPORT_LIST_TYPE = EntityType("lidarr.importlist")

        internal const val ADD_ARTIST_PATH = "api/v1/artist"
        internal const val ADD_ALBUM_PATH = "api/v1/album"
        internal const val STUDIO_PATH = "api/v1/albumstudio"

        internal fun optimisticKey(foreignId: String?): String = "add-mb-$foreignId"

        /** Ten minutes, matching the library feature's artist TTL. */
        const val LIBRARY_TTL_MILLIS: Long = 10 * 60 * 1000

        /** One hour; add-option catalogs change rarely. */
        const val CATALOG_TTL_MILLIS: Long = 60 * 60 * 1000

        /** Five minutes; import list configs change rarely but the browse is casual. */
        const val LIST_TTL_MILLIS: Long = 5 * 60 * 1000
    }
}
