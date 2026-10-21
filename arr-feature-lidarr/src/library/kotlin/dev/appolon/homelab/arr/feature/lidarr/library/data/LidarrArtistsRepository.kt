package dev.appolon.homelab.arr.feature.lidarr.library.data

import dev.appolon.homelab.arr.api.lidarr.AlbumApi
import dev.appolon.homelab.arr.api.lidarr.AlbumStudioApi
import dev.appolon.homelab.arr.api.lidarr.ArtistApi
import dev.appolon.homelab.arr.api.lidarr.ArtistEditorApi
import dev.appolon.homelab.arr.api.lidarr.model.AlbumResource
import dev.appolon.homelab.arr.api.lidarr.model.AlbumStudioArtistResource
import dev.appolon.homelab.arr.api.lidarr.model.AlbumStudioResource
import dev.appolon.homelab.arr.api.lidarr.model.ArtistEditorResource
import dev.appolon.homelab.arr.api.lidarr.model.ArtistResource
import dev.appolon.homelab.arr.api.lidarr.model.MetadataProfileResource
import dev.appolon.homelab.arr.api.lidarr.model.MonitorTypes
import dev.appolon.homelab.arr.api.lidarr.model.MonitoringOptions
import dev.appolon.homelab.arr.api.lidarr.model.NewItemMonitorTypes
import dev.appolon.homelab.arr.api.lidarr.model.QualityProfileResource
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import retrofit2.HttpException

/** One Lidarr server command the library dispatches. */
enum class ArtistCommand(val wireName: String) {
    /** Metadata refresh, per selection or library wide. */
    REFRESH_METADATA("RefreshArtist"),

    /** Disk rescan, per selection or library wide. */
    SCAN_DISK("RescanArtist"),
}

/** Runs a named Lidarr command against the server; implemented over OkHttp POST. */
fun interface ArtistCommandRunner {
    suspend fun run(command: String, artistIds: List<Int>?): ArrResult<Unit>
}

/** How a write landed: applied on the server, or queued for replay. */
sealed interface WriteOutcome {
    data object Synced : WriteOutcome
    data class Queued(val actionId: ActionId) : WriteOutcome
}

/**
 * Artists library repository. Reads are cache-first through the [EntityStore]
 * (observe plus a fetchedAt TTL driving refresh); writes go to the server
 * first and fall back to optimistic cache application plus [ActionQueue]
 * enqueue when the network call fails with [ArrError.Offline] or
 * [ArrError.Network]. The album list rides its own entity type so the
 * release-status and missing counts survive process death. Everything runs on
 * IO; no function throws.
 */
class LidarrArtistsRepository(
    private val serverId: ServerId,
    private val store: EntityStore,
    private val artistApi: ArtistApi,
    private val artistEditorApi: ArtistEditorApi,
    private val albumApi: AlbumApi,
    private val albumStudioApi: AlbumStudioApi,
    private val qualityProfileApi: suspend () -> List<QualityProfileResource>,
    private val metadataProfileApi: suspend () -> List<MetadataProfileResource>,
    private val commandRunner: ArtistCommandRunner,
    private val actionQueue: ActionQueue,
    private val now: () -> Long = System::currentTimeMillis,
    private val ioContext: kotlin.coroutines.CoroutineContext = Dispatchers.IO,
) {

    fun observeArtists(): Flow<List<ArtistResource>> =
        store.observe(serverId, ARTIST_TYPE, ArtistResource.serializer())

    suspend fun cachedArtists(): List<ArtistResource> =
        store.getAll(serverId, ARTIST_TYPE, ArtistResource.serializer())

    fun observeAlbums(): Flow<List<AlbumResource>> =
        store.observe(serverId, ALBUM_TYPE, AlbumResource.serializer())

    /** True when the artist cache is absent or older than [ttlMillis]. */
    suspend fun isStale(ttlMillis: Long = ARTIST_TTL_MILLIS): Boolean {
        val fetched = store.lastFetchedAt(serverId, ARTIST_TYPE) ?: return true
        return now() - fetched > ttlMillis
    }

    /** Fetches the artist library; album caches fill from scoped fetches only. */
    suspend fun refresh(): ArrResult<Unit> = withContext(ioContext) {
        // Never fetch /api/v1/album unfiltered: a large library answers with
        // tens of megabytes, and the JSON converter materializes the whole
        // body as one string, which OOM-kills the process. Album data arrives
        // through scoped fetches (wanted, calendar, detail) instead, and the
        // artist statistics carry the counts the list shows.
        refreshArtists()
    }

    /** Refresh only when the TTL expired; the steady-state path after the first load. */
    suspend fun refreshIfStale(ttlMillis: Long = ARTIST_TTL_MILLIS): ArrResult<Unit> =
        if (isStale(ttlMillis)) refresh() else ArrResult.ok(Unit)

    private suspend fun refreshArtists(): ArrResult<Unit> {
        val fetched = try {
            artistApi.getArtists(mbId = null)
        } catch (e: HttpException) {
            return ArrResult.err(e.toArrError())
        } catch (e: Throwable) {
            return ArrResult.err(e.arrError())
        }
        val incoming = fetched.associateBy { it.id }
        val existing = cachedArtists()
        existing.forEach { cached ->
            val id = cached.id.toString()
            val replacement = incoming[cached.id]
            if (replacement != null) {
                store.put(serverId, ARTIST_TYPE, id, replacement, ArtistResource.serializer())
            } else {
                store.delete(serverId, ARTIST_TYPE, id)
            }
        }
        val fresh = fetched.filter { artist -> existing.none { it.id == artist.id } }
        store.putAll(
            serverId,
            ARTIST_TYPE,
            fresh.map { it.id.toString() to it },
            ArtistResource.serializer(),
            fetchedAtMillis = now(),
        )
        return ArrResult.ok(Unit)
    }

    suspend fun cachedAlbums(): List<AlbumResource> =
        store.getAll(serverId, ALBUM_TYPE, AlbumResource.serializer())

    /** Quality profiles for filter labels and the editor dropdown, cached with its own TTL. */
    suspend fun qualityProfiles(ttlMillis: Long = PROFILE_TTL_MILLIS): ArrResult<List<QualityProfileResource>> =
        withContext(ioContext) {
            val cached = store.getAll(serverId, QUALITY_PROFILE_TYPE, QualityProfileResource.serializer())
            val fetched = store.lastFetchedAt(serverId, QUALITY_PROFILE_TYPE)
            if (fetched != null && now() - fetched <= ttlMillis && cached.isNotEmpty()) {
                return@withContext ArrResult.ok(cached)
            }
            val profiles = try {
                qualityProfileApi()
            } catch (e: HttpException) {
                return@withContext ArrResult.err(e.toArrError())
            } catch (e: Throwable) {
                return@withContext ArrResult.err(e.arrError())
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

    /** Metadata profiles for filter labels and the editor dropdown. */
    suspend fun metadataProfiles(ttlMillis: Long = PROFILE_TTL_MILLIS): ArrResult<List<MetadataProfileResource>> =
        withContext(ioContext) {
            val cached = store.getAll(serverId, METADATA_PROFILE_TYPE, MetadataProfileResource.serializer())
            val fetched = store.lastFetchedAt(serverId, METADATA_PROFILE_TYPE)
            if (fetched != null && now() - fetched <= ttlMillis && cached.isNotEmpty()) {
                return@withContext ArrResult.ok(cached)
            }
            val profiles = try {
                metadataProfileApi()
            } catch (e: HttpException) {
                return@withContext ArrResult.err(e.toArrError())
            } catch (e: Throwable) {
                return@withContext ArrResult.err(e.arrError())
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

    /**
     * Bulk edit through the ArtistEditor endpoint. When [missingStrategy] is
     * set, a second AlbumStudio call applies the "missing albums" monitoring
     * strategy, the one shape the editor endpoint cannot express. On offline
     * or network failure both parts apply optimistically to the cache and
     * enqueue; the caller learns which via [WriteOutcome].
     */
    suspend fun editArtists(
        editor: ArtistEditorResource,
        missingStrategy: Boolean = false,
    ): ArrResult<WriteOutcome> = withContext(ioContext) {
        var queuedActionId: ActionId? = null
        try {
            artistEditorApi.updateArtists(editor)
            // The endpoint returns an empty body, so the cache takes the merged
            // projection of the same payload the server just accepted.
            applyEditorToCache(editor)
        } catch (e: Throwable) {
            val error = errorOf(e)
            if (!error.isRetryable()) return@withContext ArrResult.err(error)
            applyEditorToCache(editor)
            queuedActionId = enqueue(
                method = "PUT",
                path = EDITOR_PATH,
                bodyJson = ArrJson.encodeToString(ArtistEditorResource.serializer(), editor),
                description = "Edit artists",
            )
        }
        if (missingStrategy) {
            val outcome = applyMissingStrategy(editor.artistIds.orEmpty())
            if (outcome is ArrResult.Err && !outcome.error.isRetryable()) {
                return@withContext ArrResult.err(outcome.error)
            }
            val queuedStudio = (outcome as? ArrResult.Ok)?.value as? WriteOutcome.Queued
            if (queuedStudio != null && queuedActionId == null) {
                queuedActionId = queuedStudio.actionId
            }
        }
        queuedActionId?.let { ArrResult.ok(WriteOutcome.Queued(it)) } ?: ArrResult.ok(WriteOutcome.Synced)
    }

    /**
     * Applies the missing-albums monitoring strategy through AlbumStudio:
     * monitored becomes true exactly for albums without files.
     */
    private suspend fun applyMissingStrategy(artistIds: List<Int>): ArrResult<WriteOutcome> {
        val body = AlbumStudioResource(
            artist = artistIds.map { AlbumStudioArtistResource(id = it) },
            monitoringOptions = MonitoringOptions(monitor = MonitorTypes.MISSING, monitored = true),
            monitorNewItems = NewItemMonitorTypes.NONE,
        )
        return try {
            albumStudioApi.updateAlbumStudio(body)
            applyMissingToCache(artistIds)
            ArrResult.ok(WriteOutcome.Synced)
        } catch (e: Throwable) {
            val error = errorOf(e)
            if (!error.isRetryable()) return ArrResult.err(error)
            applyMissingToCache(artistIds)
            val action = enqueue(
                method = "POST",
                path = STUDIO_PATH,
                bodyJson = ArrJson.encodeToString(AlbumStudioResource.serializer(), body),
                description = "Monitor missing albums",
            )
            ArrResult.ok(WriteOutcome.Queued(action))
        }
    }

    /** Bulk delete with the editor flags; offline deletes queue like edits. */
    suspend fun deleteArtists(
        artistIds: List<Int>,
        deleteFiles: Boolean,
        addImportListExclusion: Boolean,
    ): ArrResult<WriteOutcome> = withContext(ioContext) {
        val editor = ArtistEditorResource(
            artistIds = artistIds,
            deleteFiles = deleteFiles,
            addImportListExclusion = addImportListExclusion,
        )
        try {
            artistEditorApi.deleteArtists(editor)
            artistIds.forEach { id -> store.delete(serverId, ARTIST_TYPE, id.toString()) }
            cachedAlbums().filter { it.artistId in artistIds.toSet() }
                .forEach { album -> store.delete(serverId, ALBUM_TYPE, album.id.toString()) }
            ArrResult.ok(WriteOutcome.Synced)
        } catch (e: Throwable) {
            val error = errorOf(e)
            if (!error.isRetryable()) return@withContext ArrResult.err(error)
            val targets = artistIds.toSet()
            artistIds.forEach { id -> store.delete(serverId, ARTIST_TYPE, id.toString()) }
            // The replayed delete drops these albums server-side too.
            cachedAlbums().filter { it.artistId in targets }
                .forEach { album -> store.delete(serverId, ALBUM_TYPE, album.id.toString()) }
            val action = enqueue(
                method = "DELETE",
                path = EDITOR_PATH,
                bodyJson = ArrJson.encodeToString(ArtistEditorResource.serializer(), editor),
                description = "Delete artists",
            )
            ArrResult.ok(WriteOutcome.Queued(action))
        }
    }

    /** Dispatches a command; offline dispatches queue and replay unchanged. */
    suspend fun runCommand(
        command: ArtistCommand,
        artistIds: List<Int>?,
    ): ArrResult<WriteOutcome> = withContext(ioContext) {
        val error = try {
            val result = commandRunner.run(command.wireName, artistIds)
            if (result is ArrResult.Ok) {
                return@withContext ArrResult.ok(WriteOutcome.Synced)
            }
            (result as ArrResult.Err).error
        } catch (e: Throwable) {
            e.arrError()
        }
        if (!error.isRetryable()) {
            return@withContext ArrResult.err(error)
        }
        val body = buildString {
            append("{\"name\":\"")
            append(command.wireName)
            append("\"")
            if (!artistIds.isNullOrEmpty()) append(",\"artistIds\":$artistIds")
            append("}")
        }
        val action = enqueue(
            method = "POST",
            path = COMMAND_PATH,
            bodyJson = body,
            description = "Command ${command.wireName}",
        )
        ArrResult.ok(WriteOutcome.Queued(action))
    }

    /** Artist ids touched by queued editor and album studio actions, for per-artist queued badges. */
    fun queuedArtistIds(): Flow<Set<Int>> = actionQueue.observe().map { pending ->
        pending.asSequence()
            .filter { it.serverId == serverId && it.path in QUEUED_WRITE_PATHS }
            .flatMap { action ->
                runCatching {
                    when (action.path) {
                        EDITOR_PATH -> ArrJson.decodeFromString(
                            ArtistEditorResource.serializer(),
                            action.bodyJson.orEmpty(),
                        ).artistIds.orEmpty()

                        else -> ArrJson.decodeFromString(
                            AlbumStudioResource.serializer(),
                            action.bodyJson.orEmpty(),
                        ).artist.orEmpty().map { it.id }
                    }
                }.getOrNull().orEmpty()
            }
            .toSet()
    }

    /** Count of queued actions for this server, library wide indicator. */
    fun queuedCount(): Flow<Int> = actionQueue.observe().map { pending ->
        pending.count { it.serverId == serverId }
    }

    private suspend fun applyEditorToCache(editor: ArtistEditorResource) {
        val targets = editor.artistIds.orEmpty().toSet()
        if (targets.isEmpty()) return
        // Only touched rows are rewritten so the fetchedAt of untouched artists survives.
        val merged = applyArtistEditor(cachedArtists(), editor).filter { it.id in targets }
        store.putAll(
            serverId,
            ARTIST_TYPE,
            merged.map { it.id.toString() to it },
            ArtistResource.serializer(),
        )
    }

    private suspend fun applyMissingToCache(artistIds: List<Int>) {
        val targets = artistIds.toSet()
        if (targets.isEmpty()) return
        val changed = cachedAlbums()
            .filter { it.artistId in targets && isMissingAlbum(it) && !it.monitored }
            .map { it.copy(monitored = true) }
        if (changed.isEmpty()) return
        store.putAll(
            serverId,
            ALBUM_TYPE,
            changed.map { it.id.toString() to it },
            AlbumResource.serializer(),
        )
    }

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

    private fun errorOf(t: Throwable): ArrError = when (t) {
        is HttpException -> t.toArrError()
        else -> t.arrError()
    }

    private fun ArrError.isRetryable(): Boolean =
        this is ArrError.Offline || this is ArrError.Network

    private fun HttpException.toArrError(): ArrError = when (code()) {
        401, 403 -> ArrError.Auth(code())
        else -> ArrError.Http(code(), message())
    }

    companion object {
        internal val ARTIST_TYPE = EntityType("lidarr.artist")
        internal val ALBUM_TYPE = EntityType("lidarr.album")
        internal val QUALITY_PROFILE_TYPE = EntityType("lidarr.qualityprofile")
        internal val METADATA_PROFILE_TYPE = EntityType("lidarr.metadataprofile")
        internal val EDITOR_PATH = "api/v1/artist/editor"
        internal val STUDIO_PATH = "api/v1/albumstudio"
        internal val COMMAND_PATH = "api/v1/command"
        private val QUEUED_WRITE_PATHS = setOf(EDITOR_PATH, STUDIO_PATH)

        /** Ten minutes; the web library polls no faster either. */
        const val ARTIST_TTL_MILLIS: Long = 10 * 60 * 1000

        /** One hour; profiles change rarely. */
        const val PROFILE_TTL_MILLIS: Long = 60 * 60 * 1000
    }
}
