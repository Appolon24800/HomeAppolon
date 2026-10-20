package dev.appolon.homelab.arr.feature.radarr.settings.mm.data

import dev.appolon.homelab.arr.api.radarr.MediaManagementConfigApi
import dev.appolon.homelab.arr.api.radarr.NamingConfigApi
import dev.appolon.homelab.arr.api.radarr.model.ColonReplacementFormat
import dev.appolon.homelab.arr.api.radarr.model.MediaManagementConfigResource
import dev.appolon.homelab.arr.api.radarr.model.NamingConfigResource
import dev.appolon.homelab.arr.api.radarr.model.NamingExampleResource
import dev.appolon.homelab.arr.core.ActionId
import dev.appolon.homelab.arr.core.AppType
import dev.appolon.homelab.arr.core.ArrError
import dev.appolon.homelab.arr.core.ArrResult
import dev.appolon.homelab.arr.core.EntityType
import dev.appolon.homelab.arr.core.ServerId
import dev.appolon.homelab.arr.data.ActionQueue
import dev.appolon.homelab.arr.data.EntityStore
import dev.appolon.homelab.arr.data.QueuedAction
import dev.appolon.homelab.arr.feature.radarr.library.data.WriteOutcome
import dev.appolon.homelab.arr.net.ArrJson
import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import retrofit2.Response
import kotlin.coroutines.CoroutineContext

/**
 * Cache-first repository over the two config endpoints behind the media
 * management screens: the import/file-management toggles and the naming
 * config. Reads come from the [EntityStore] with a fetchedAt TTL; writes
 * PUT to the server first and, when the network is unreachable, apply
 * optimistically to the cache and enqueue the PUT for replay through the
 * [ActionQueue]. Nothing throws; everything runs on [ioContext].
 */
class RadarrMediaManagementRepository(
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
        val config = when (val r = fetch { mediaManagementApi.mediaManagementConfig() }) {
            is ArrResult.Err -> return@withContext r
            is ArrResult.Ok -> r.value
        }
        val naming = when (val r = fetch { namingApi.namingConfig() }) {
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
        val response = try {
            mediaManagementApi.updateMediaManagementConfig(config.id.toString(), config)
        } catch (e: IOException) {
            return@withContext enqueueWrite(
                method = PUT_METHOD,
                path = "api/v3/config/mediamanagement/${config.id}",
                body = config,
                serializer = MediaManagementConfigResource.serializer(),
                cache = { putConfig(config, null) },
                description = "Save media management settings",
                error = ArrError.Network(e),
            )
        }
        applyWriteResponse(
            response = response,
            onSuccessCache = { store.put(serverId, MEDIA_MANAGEMENT_TYPE, SINGLE_ID, it, MediaManagementConfigResource.serializer()) },
        )
    }

    /**
     * Saves the naming config the same way: server first, optimistic cache
     * write plus queued PUT when the connection is down.
     */
    suspend fun saveNaming(config: NamingConfigResource): ArrResult<WriteOutcome> =
        withContext(ioContext) {
            val response = try {
                namingApi.updateNamingConfig(config.id.toString(), config)
            } catch (e: IOException) {
                return@withContext enqueueWrite(
                    method = PUT_METHOD,
                    path = "api/v3/config/naming/${config.id}",
                    body = config,
                    serializer = NamingConfigResource.serializer(),
                    cache = { putConfig(null, config) },
                    description = "Save movie naming settings",
                    error = ArrError.Network(e),
                )
            }
            applyWriteResponse(
                response = response,
                onSuccessCache = { store.put(serverId, NAMING_TYPE, SINGLE_ID, it, NamingConfigResource.serializer()) },
            )
        }

    /**
     * Asks the server to render example names for the given draft formats.
     * The naming editor always shows the local [NamingPreviewRenderer]
     * preview; this is the authoritative sample when the server answers,
     * and a failure here never blocks editing.
     */
    suspend fun namingExamples(
        renameMovies: Boolean,
        replaceIllegalCharacters: Boolean,
        colonReplacementFormat: ColonReplacementFormat?,
        standardMovieFormat: String,
        movieFolderFormat: String,
    ): ArrResult<NamingExampleResource> = withContext(ioContext) {
        fetch {
            namingApi.namingExamples(
                renameMovies = renameMovies,
                replaceIllegalCharacters = replaceIllegalCharacters,
                colonReplacementFormat = colonReplacementFormat,
                standardMovieFormat = standardMovieFormat,
                movieFolderFormat = movieFolderFormat,
            )
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
        serializer: kotlinx.serialization.KSerializer<T>,
        cache: suspend () -> Unit,
        description: String,
        error: ArrError,
    ): ArrResult<WriteOutcome> {
        if (!error.isRetryable()) return ArrResult.err(error)
        val action = QueuedAction(
            actionId = ActionId(UUID.nameUUIDFromBytes(
                "$serverId:$method:$path:$body".toByteArray(Charsets.UTF_8),
            ).toString()),
            serverId = serverId,
            appType = AppType.RADARR,
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

    /** Shared tail for writes whose API returns a typed [Response]. */
    private suspend fun <T> applyWriteResponse(
        response: Response<T>,
        onSuccessCache: suspend (T) -> Unit,
    ): ArrResult<WriteOutcome> = if (response.isSuccessful) {
        val body = response.body()
        if (body != null) onSuccessCache(body)
        ArrResult.ok(WriteOutcome.Synced)
    } else {
        ArrResult.err(response.toArrError())
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

    private fun <T> Response<T>.toArrError(): ArrError = when (code()) {
        401, 403 -> ArrError.Auth(code())
        else -> ArrError.Http(code(), null)
    }

    private fun Throwable.arrErrorOrUnknown(): ArrError = when (this) {
        is retrofit2.HttpException -> when (code()) {
            401, 403 -> ArrError.Auth(code())
            else -> ArrError.Http(code(), message())
        }

        else -> ArrError.Unknown(this)
    }

    companion object {
        internal val MEDIA_MANAGEMENT_TYPE = EntityType("radarr.mediamanagement.config")
        internal val NAMING_TYPE = EntityType("radarr.naming.config")

        /** Five minutes; a settings screen re-reads on pull-to-refresh anyway. */
        const val CONFIG_TTL_MILLIS: Long = dev.appolon.homelab.arr.core.CacheTtl.SETTINGS

        internal const val SINGLE_ID = "config"
        private const val PUT_METHOD = "PUT"
        internal val FEATURE_PATHS = setOf(
            "api/v3/config/mediamanagement",
            "api/v3/config/naming",
        )
    }
}
