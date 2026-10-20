package dev.appolon.homelab.arr.feature.lidarr.settings.mm.data

import dev.appolon.homelab.arr.api.lidarr.MediaManagementConfigApi
import dev.appolon.homelab.arr.api.lidarr.NamingConfigApi
import dev.appolon.homelab.arr.api.lidarr.model.MediaManagementConfigResource
import dev.appolon.homelab.arr.api.lidarr.model.NamingConfigResource
import dev.appolon.homelab.arr.core.ActionId
import dev.appolon.homelab.arr.core.AppType
import dev.appolon.homelab.arr.core.ArrError
import dev.appolon.homelab.arr.core.ArrResult
import dev.appolon.homelab.arr.core.EntityType
import dev.appolon.homelab.arr.core.ServerId
import dev.appolon.homelab.arr.data.ActionQueue
import dev.appolon.homelab.arr.data.EntityStore
import dev.appolon.homelab.arr.data.QueuedAction
import dev.appolon.homelab.arr.feature.lidarr.library.data.WriteOutcome
import dev.appolon.homelab.arr.net.ArrJson
import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import retrofit2.HttpException
import kotlin.coroutines.CoroutineContext

/**
 * Server-rendered naming samples from the naming examples endpoint. The
 * Lidarr spec declares no response schema, so the payload arrives as raw
 * JSON and the example strings are read key by key (Lidarr answers with
 * the config's own field names carrying rendered filenames); missing keys
 * stay null and the UI falls back to the local preview.
 */
data class LidarrNamingExamples(
    val standardTrackExample: String? = null,
    val multiDiscTrackExample: String? = null,
    val artistFolderExample: String? = null,
) {
    companion object {
        /** Reads the wire shape recorded from the spec's example payload. */
        fun fromJson(body: JsonObject): LidarrNamingExamples = LidarrNamingExamples(
            standardTrackExample = body.stringOrNull("standardTrackFormat"),
            multiDiscTrackExample = body.stringOrNull("multiDiscTrackFormat"),
            artistFolderExample = body.stringOrNull("artistFolderFormat"),
        )

        private fun JsonObject.stringOrNull(key: String): String? =
            (this[key] as? JsonPrimitive)?.contentOrNull
    }
}

/**
 * Cache-first repository over the two config endpoints behind the media
 * management screens: the import/file-management toggles and the track
 * naming config. Reads come from the [EntityStore] with a fetchedAt TTL;
 * writes PUT to the server first and, when the network is unreachable,
 * apply optimistically to the cache and enqueue the PUT for replay
 * through the [ActionQueue]. Nothing throws; everything runs on
 * [ioContext].
 */
class LidarrMediaManagementRepository(
    private val serverId: ServerId,
    private val store: EntityStore,
    private val mediaManagementApi: MediaManagementConfigApi,
    private val namingApi: NamingConfigApi,
    private val actionQueue: ActionQueue,
    private val now: () -> Long = System::currentTimeMillis,
    private val ioContext: CoroutineContext = Dispatchers.IO,
) {

    /**
     * Bumped per enqueue so successive queued writes replay in the order
     * they were made even within one clock millisecond.
     */
    private var writeSequence = 0L

    fun observeMediaManagement(): Flow<MediaManagementConfigResource?> =
        store.observe(serverId, MEDIA_MANAGEMENT_TYPE, MediaManagementConfigResource.serializer())
            .map { it.firstOrNull() }

    fun observeNaming(): Flow<NamingConfigResource?> =
        store.observe(serverId, NAMING_TYPE, NamingConfigResource.serializer())
            .map { it.firstOrNull() }

    suspend fun cachedMediaManagement(): MediaManagementConfigResource? =
        store.getAll(serverId, MEDIA_MANAGEMENT_TYPE, MediaManagementConfigResource.serializer())
            .firstOrNull()

    suspend fun cachedNaming(): NamingConfigResource? =
        store.getAll(serverId, NAMING_TYPE, NamingConfigResource.serializer()).firstOrNull()

    /** True when the media management cache is absent or older than [ttlMillis]. */
    suspend fun isStale(ttlMillis: Long = CONFIG_TTL_MILLIS): Boolean =
        isTypeStale(MEDIA_MANAGEMENT_TYPE, ttlMillis)

    /** Re-fetches both configs and replaces their single-row caches. */
    suspend fun refresh(): ArrResult<Unit> = withContext(ioContext) {
        val config = when (val r = fetch { mediaManagementApi.getMediaManagementConfig() }) {
            is ArrResult.Err -> return@withContext r
            is ArrResult.Ok -> r.value
        }
        val naming = when (val r = fetch { namingApi.getNamingConfig() }) {
            is ArrResult.Err -> return@withContext r
            is ArrResult.Ok -> r.value
        }
        putConfig(config, naming)
        ArrResult.ok(Unit)
    }

    /** Refresh only when the TTL expired. */
    suspend fun refreshIfStale(ttlMillis: Long = CONFIG_TTL_MILLIS): ArrResult<Unit> =
        if (isStale(ttlMillis)) refresh() else ArrResult.ok(Unit)

    /**
     * Saves the import/file-management config. Offline: the new value lands
     * in the cache immediately (the toggles reflect the user's intent) and
     * the PUT replays later.
     */
    suspend fun saveMediaManagement(
        config: MediaManagementConfigResource,
    ): ArrResult<WriteOutcome> = withContext(ioContext) {
        try {
            val saved = mediaManagementApi.updateMediaManagementConfig(config.id.toString(), config)
            store.put(serverId, MEDIA_MANAGEMENT_TYPE, SINGLE_ID, saved, MediaManagementConfigResource.serializer())
            ArrResult.ok(WriteOutcome.Synced)
        } catch (e: IOException) {
            enqueueWrite(
                method = PUT_METHOD,
                path = "api/v1/config/mediamanagement/${config.id}",
                body = config,
                serializer = MediaManagementConfigResource.serializer(),
                cache = { putConfig(config, null) },
                description = "Save media management settings",
                error = ArrError.Network(e),
            )
        } catch (t: Throwable) {
            ArrResult.err(t.arrErrorOrUnknown())
        }
    }

    /**
     * Saves the naming config the same way: server first, optimistic cache
     * write plus queued PUT when the connection is down.
     */
    suspend fun saveNaming(config: NamingConfigResource): ArrResult<WriteOutcome> =
        withContext(ioContext) {
            try {
                val saved = namingApi.updateNamingConfig(config.id.toString(), config)
                store.put(serverId, NAMING_TYPE, SINGLE_ID, saved, NamingConfigResource.serializer())
                ArrResult.ok(WriteOutcome.Synced)
            } catch (e: IOException) {
                enqueueWrite(
                    method = PUT_METHOD,
                    path = "api/v1/config/naming/${config.id}",
                    body = config,
                    serializer = NamingConfigResource.serializer(),
                    cache = { putConfig(null, config) },
                    description = "Save track naming settings",
                    error = ArrError.Network(e),
                )
            } catch (t: Throwable) {
                ArrResult.err(t.arrErrorOrUnknown())
            }
        }

    /**
     * Asks the server to render example names for the given draft formats.
     * The naming editor always shows the local [NamingPreviewRenderer]
     * preview; this is the authoritative sample when the server answers,
     * and a failure here never blocks editing.
     */
    suspend fun namingExamples(
        renameTracks: Boolean,
        replaceIllegalCharacters: Boolean,
        colonReplacementFormat: Int,
        standardTrackFormat: String,
        multiDiscTrackFormat: String,
        artistFolderFormat: String,
    ): ArrResult<LidarrNamingExamples> = withContext(ioContext) {
        when (
            val result = fetch {
                namingApi.getNamingExamples(
                    renameTracks = renameTracks,
                    replaceIllegalCharacters = replaceIllegalCharacters,
                    colonReplacementFormat = colonReplacementFormat,
                    standardTrackFormat = standardTrackFormat,
                    multiDiscTrackFormat = multiDiscTrackFormat,
                    artistFolderFormat = artistFolderFormat,
                )
            }
        ) {
            is ArrResult.Ok -> ArrResult.ok(LidarrNamingExamples.fromJson(result.value))
            is ArrResult.Err -> result
        }
    }

    /** Queued action count for this feature's writes, for the pending banner. */
    fun queuedCount(): Flow<Int> = actionQueue.observe().map { pending ->
        pending.count { it.serverId == serverId && FEATURE_PATHS.any { p -> it.path.startsWith(p) } }
    }

    private suspend fun putConfig(
        mediaManagement: MediaManagementConfigResource?,
        naming: NamingConfigResource?,
    ) {
        mediaManagement?.let {
            store.put(serverId, MEDIA_MANAGEMENT_TYPE, SINGLE_ID, it, MediaManagementConfigResource.serializer())
        }
        naming?.let {
            store.put(serverId, NAMING_TYPE, SINGLE_ID, it, NamingConfigResource.serializer())
        }
    }

    private suspend fun isTypeStale(type: EntityType, ttlMillis: Long): Boolean {
        val fetched = store.lastFetchedAt(serverId, type) ?: return true
        return now() - fetched > ttlMillis
    }

    private suspend fun <T> enqueueWrite(
        method: String,
        path: String,
        body: T,
        serializer: KSerializer<T>,
        cache: suspend () -> Unit,
        description: String,
        error: ArrError,
    ): ArrResult<WriteOutcome> {
        if (!error.isRetryable()) return ArrResult.err(error)
        val action = QueuedAction(
            actionId = ActionId(UUID.randomUUID().toString()),
            serverId = serverId,
            appType = AppType.LIDARR,
            method = method,
            path = path,
            bodyJson = ArrJson.encodeToString(serializer, body),
            description = description,
            // The queue orders by timestamp and breaks ties on the random
            // action id, so rapid queued writes get a strictly increasing
            // stamp to keep their replay order.
            enqueuedAtMillis = now() + writeSequence++,
        )
        cache()
        actionQueue.enqueue(action)
        return ArrResult.ok(WriteOutcome.Queued(action.actionId))
    }

    private suspend fun <T> fetch(call: suspend () -> T): ArrResult<T> = try {
        ArrResult.ok(call())
    } catch (e: IOException) {
        ArrResult.err(ArrError.Network(e))
    } catch (e: Throwable) {
        ArrResult.err(e.arrErrorOrUnknown())
    }

    private fun ArrError.isRetryable(): Boolean =
        this is ArrError.Offline || this is ArrError.Network

    private fun Throwable.arrErrorOrUnknown(): ArrError = when (this) {
        is HttpException -> when (code()) {
            401, 403 -> ArrError.Auth(code())
            else -> ArrError.Http(code(), message())
        }

        else -> ArrError.Unknown(this)
    }

    companion object {
        internal val MEDIA_MANAGEMENT_TYPE = EntityType("lidarr.mediamanagement.config")
        internal val NAMING_TYPE = EntityType("lidarr.naming.config")

        /** Five minutes; a settings screen re-reads on pull-to-refresh anyway. */
        const val CONFIG_TTL_MILLIS: Long = 5 * 60 * 1000

        internal const val SINGLE_ID = "config"
        private const val PUT_METHOD = "PUT"
        internal val FEATURE_PATHS = setOf(
            "api/v1/config/mediamanagement",
            "api/v1/config/naming",
        )
    }
}
