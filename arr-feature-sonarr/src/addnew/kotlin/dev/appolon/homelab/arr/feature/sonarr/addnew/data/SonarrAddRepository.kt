package dev.appolon.homelab.arr.feature.sonarr.addnew.data

import dev.appolon.homelab.arr.api.sonarr.ImportListApi
import dev.appolon.homelab.arr.api.sonarr.ImportListExclusionApi
import dev.appolon.homelab.arr.api.sonarr.SeriesApi
import dev.appolon.homelab.arr.api.sonarr.model.ImportListExclusionResource
import dev.appolon.homelab.arr.api.sonarr.model.ImportListResource
import dev.appolon.homelab.arr.api.sonarr.model.LanguageProfileResource
import dev.appolon.homelab.arr.api.sonarr.model.QualityProfileResource
import dev.appolon.homelab.arr.api.sonarr.model.RootFolderResource
import dev.appolon.homelab.arr.api.sonarr.model.SeriesResource
import dev.appolon.homelab.arr.api.sonarr.model.TagResource
import dev.appolon.homelab.arr.core.ActionId
import dev.appolon.homelab.arr.core.AppType
import dev.appolon.homelab.arr.core.ArrError
import dev.appolon.homelab.arr.core.ArrResult
import dev.appolon.homelab.arr.core.EntityType
import dev.appolon.homelab.arr.core.ServerId
import dev.appolon.homelab.arr.data.ActionQueue
import dev.appolon.homelab.arr.data.EntityStore
import dev.appolon.homelab.arr.data.QueuedAction
import dev.appolon.homelab.arr.feature.sonarr.addnew.SonarrSeriesLookupRunner
import dev.appolon.homelab.arr.feature.sonarr.library.data.WriteOutcome
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
 * Add-new repository. Lookups (root folders, profiles, tags, import lists,
 * exclusions) are cache-first through the [EntityStore] with a fetchedAt TTL;
 * series search always hits the network because it proxies a metadata
 * provider. Writes go to the server first and fall back to an optimistic
 * cache row plus [ActionQueue] enqueue when the call fails with
 * [ArrError.Offline] or [ArrError.Network]. The `sonarr.series` rows share
 * the library's entity type, so an offline add shows up in the library grid
 * immediately under a synthetic negative id and the library's refresh
 * reconcile replaces it with the real row once the add replays. No function
 * throws.
 */
class SonarrAddRepository(
    private val serverId: ServerId,
    private val store: EntityStore,
    private val seriesApi: SeriesApi,
    private val lookupRunner: SonarrSeriesLookupRunner,
    private val rootFolderApi: suspend () -> List<RootFolderResource>,
    private val qualityProfileApi: suspend () -> List<QualityProfileResource>,
    private val languageProfileApi: suspend () -> List<LanguageProfileResource>,
    private val tagApi: suspend () -> List<TagResource>,
    private val importListApi: ImportListApi,
    private val importListExclusionApi: ImportListExclusionApi,
    private val actionQueue: ActionQueue,
    private val now: () -> Long = System::currentTimeMillis,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {

    fun observeSeries(): Flow<List<SeriesResource>> =
        store.observe(serverId, SERIES_TYPE, SERIES_SER)

    suspend fun cachedSeries(): List<SeriesResource> =
        store.getAll(serverId, SERIES_TYPE, SERIES_SER)

    /** True when the series cache is absent or older than [ttlMillis]. */
    suspend fun isStale(ttlMillis: Long = SERIES_TTL_MILLIS): Boolean {
        val fetched = store.lastFetchedAt(serverId, SERIES_TYPE) ?: return true
        return now() - fetched > ttlMillis
    }

    /**
     * Fetches the library and reconciles the cache the same way the library
     * repository does, so the two screens never fight over rows. Pending
     * offline adds live under negative ids and are dropped here once the real
     * row (or its absence after a replay conflict) is authoritative.
     */
    suspend fun refreshSeries(): ArrResult<Unit> = withContext(ioDispatcher) {
        val fetched = try {
            seriesApi.list()
        } catch (e: HttpException) {
            return@withContext ArrResult.err(e.toArrError())
        } catch (e: Throwable) {
            return@withContext ArrResult.err(e.arrError())
        }
        val incoming = fetched.associateBy { it.id }
        val existing = cachedSeries()
        existing.forEach { cached ->
            val key = cached.id.toString()
            val replacement = incoming[cached.id]
            if (replacement != null) {
                store.put(serverId, SERIES_TYPE, key, replacement, SERIES_SER)
            } else {
                store.delete(serverId, SERIES_TYPE, key)
            }
        }
        val fresh = fetched.filter { series -> existing.none { it.id == series.id } }
        store.putAll(
            serverId,
            SERIES_TYPE,
            fresh.map { it.id.toString() to it },
            SERIES_SER,
            fetchedAtMillis = now(),
        )
        ArrResult.ok(Unit)
    }

    /** Refresh only when the TTL expired; the steady-state path after the first load. */
    suspend fun refreshSeriesIfStale(ttlMillis: Long = SERIES_TTL_MILLIS): ArrResult<Unit> =
        if (isStale(ttlMillis)) refreshSeries() else ArrResult.ok(Unit)

    /** Series search through the metadata proxy; never served from cache. */
    suspend fun lookup(term: String): ArrResult<List<SeriesResource>> =
        withContext(ioDispatcher) { lookupRunner.lookup(term) }

    suspend fun rootFolders(ttlMillis: Long = LOOKUP_TTL_MILLIS) =
        lookupCached(
            ROOT_FOLDER_TYPE,
            RootFolderResource.serializer(),
            { it.id.toString() },
            rootFolderApi,
            ttlMillis,
        )

    suspend fun qualityProfiles(ttlMillis: Long = LOOKUP_TTL_MILLIS) =
        lookupCached(
            QUALITY_PROFILE_TYPE,
            QualityProfileResource.serializer(),
            { it.id.toString() },
            qualityProfileApi,
            ttlMillis,
        )

    suspend fun languageProfiles(ttlMillis: Long = LOOKUP_TTL_MILLIS) =
        lookupCached(
            LANGUAGE_PROFILE_TYPE,
            LanguageProfileResource.serializer(),
            { it.id.toString() },
            languageProfileApi,
            ttlMillis,
        )

    suspend fun tags(ttlMillis: Long = LOOKUP_TTL_MILLIS) =
        lookupCached(TAG_TYPE, TagResource.serializer(), { it.id.toString() }, tagApi, ttlMillis)

    /** Configured import lists for the browse tab. */
    suspend fun importLists(ttlMillis: Long = LOOKUP_TTL_MILLIS) =
        lookupCached(
            IMPORT_LIST_TYPE,
            ImportListResource.serializer(),
            { it.id.toString() },
            { importListApi.list() },
            ttlMillis,
        )

    /** Import list exclusions, cache-first like the other lookups. */
    suspend fun exclusions(ttlMillis: Long = LOOKUP_TTL_MILLIS) =
        lookupCached(
            EXCLUSION_TYPE,
            ImportListExclusionResource.serializer(),
            { it.id.toString() },
            { importListExclusionApi.list() },
            ttlMillis,
        )

    /** Cache-first lookup with a TTL; an empty fetch keeps the previous cache. */
    private suspend fun <T> lookupCached(
        type: EntityType,
        serializer: kotlinx.serialization.KSerializer<T>,
        keyOf: (T) -> String,
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
                remote.map { keyOf(it) to it },
                serializer,
                fetchedAtMillis = now(),
            )
            ArrResult.ok(remote)
        } else {
            ArrResult.ok(cached)
        }
    }

    /**
     * Adds one series. The body must come from a lookup result so tvdbId and
     * seasons are populated. On retryable failure the series lands in the
     * cache under the synthetic id `-tvdbId` and the POST is queued.
     */
    suspend fun addSeries(body: SeriesResource): ArrResult<WriteOutcome> =
        withContext(ioDispatcher) {
            val created = try {
                seriesApi.create(body)
            } catch (e: HttpException) {
                return@withContext queueSeriesOrErr(e.toArrError(), body)
            } catch (e: Throwable) {
                return@withContext queueSeriesOrErr(e.arrError(), body)
            }
            store.put(serverId, SERIES_TYPE, created.id.toString(), created, SERIES_SER)
            ArrResult.ok(WriteOutcome.Synced)
        }

    /**
     * Triggers a server-side sync of one import list, which imports series
     * per the list's own settings. There is no local state to apply, so the
     * offline path only queues the action.
     */
    suspend fun syncImportList(list: ImportListResource): ArrResult<WriteOutcome> =
        withContext(ioDispatcher) {
            val bodyJson = ArrJson.encodeToString(IMPORT_LIST_SER, list)
            try {
                importListApi.action(LIST_SYNC_ACTION, list)
            } catch (e: HttpException) {
                return@withContext queueOrErr(
                    e.toArrError(),
                    "POST",
                    "$IMPORT_LIST_ACTION_PATH/$LIST_SYNC_ACTION",
                    bodyJson,
                    "Sync import list " + (list.name ?: "#${list.id}"),
                )
            } catch (e: Throwable) {
                return@withContext queueOrErr(
                    e.arrError(),
                    "POST",
                    "$IMPORT_LIST_ACTION_PATH/$LIST_SYNC_ACTION",
                    bodyJson,
                    "Sync import list " + (list.name ?: "#${list.id}"),
                )
            }
            ArrResult.ok(WriteOutcome.Synced)
        }

    /** Creates an exclusion; offline creates land optimistically and queue. */
    suspend fun addExclusion(tvdbId: Int, title: String): ArrResult<WriteOutcome> =
        withContext(ioDispatcher) {
            val body = ImportListExclusionResource(tvdbId = tvdbId, title = title)
            val created = try {
                importListExclusionApi.create(body)
            } catch (e: HttpException) {
                return@withContext queueExclusionOrErr(e.toArrError(), body)
            } catch (e: Throwable) {
                return@withContext queueExclusionOrErr(e.arrError(), body)
            }
            store.put(serverId, EXCLUSION_TYPE, created.id.toString(), created, EXCLUSION_SER)
            ArrResult.ok(WriteOutcome.Synced)
        }

    /**
     * Deletes an exclusion. Deleting a still-pending offline create also
     * removes its matching queued action instead of enqueueing a delete the
     * replay would have to fight with.
     */
    suspend fun deleteExclusion(exclusion: ImportListExclusionResource): ArrResult<WriteOutcome> =
        withContext(ioDispatcher) {
            if (exclusion.id <= 0) {
                cancelPendingExclusion(exclusion.tvdbId)
                store.delete(serverId, EXCLUSION_TYPE, exclusion.id.toString())
                return@withContext ArrResult.ok(WriteOutcome.Synced)
            }
            try {
                importListExclusionApi.delete(exclusion.id)
            } catch (e: HttpException) {
                return@withContext deleteExclusionOrErr(e.toArrError(), exclusion)
            } catch (e: Throwable) {
                return@withContext deleteExclusionOrErr(e.arrError(), exclusion)
            }
            store.delete(serverId, EXCLUSION_TYPE, exclusion.id.toString())
            ArrResult.ok(WriteOutcome.Synced)
        }

    /** tvdb ids touched by queued add-series and exclusion actions. */
    fun queuedTvdbIds(): Flow<Set<Int>> = actionQueue.observe().map { pending ->
        pending.asSequence()
            .filter { it.serverId == serverId }
            .flatMap(::tvdbIdsOf)
            .toSet()
    }

    /** Count of queued actions for this server, the screen-wide queued indicator. */
    fun queuedCount(): Flow<Int> = actionQueue.observe().map { pending ->
        pending.count { it.serverId == serverId }
    }

    /** Parses the tvdb id a queued action touches, ignoring malformed bodies. */
    private fun tvdbIdsOf(action: QueuedAction): Sequence<Int> = sequence {
        if (action.method != "POST") return@sequence
        when (action.path) {
            SERIES_PATH -> {
                val series = runCatching {
                    ArrJson.decodeFromString(SERIES_SER, action.bodyJson.orEmpty())
                }.getOrNull()
                series?.tvdbId?.takeIf { it > 0 }?.let { yield(it) }
            }

            EXCLUSION_PATH -> {
                val exclusion = runCatching {
                    ArrJson.decodeFromString(EXCLUSION_SER, action.bodyJson.orEmpty())
                }.getOrNull()
                exclusion?.tvdbId?.takeIf { it > 0 }?.let { yield(it) }
            }
        }
    }

    private suspend fun queueSeriesOrErr(
        error: ArrError,
        body: SeriesResource,
    ): ArrResult<WriteOutcome> {
        if (!error.isRetryable()) return ArrResult.err(error)
        // Synthetic negative id keeps LazyGrid keys unique in the library while
        // the real id is unknown; the key equals the id so refresh reconcile
        // deletes it (see refreshSeries).
        if (body.tvdbId > 0) {
            store.put(
                serverId,
                SERIES_TYPE,
                (-body.tvdbId).toString(),
                body.copy(id = -body.tvdbId),
                SERIES_SER,
            )
        }
        return queueOrErr(
            error,
            "POST",
            SERIES_PATH,
            ArrJson.encodeToString(SERIES_SER, body),
            "Add series " + (body.title?.takeIf { it.isNotBlank() } ?: "#${body.tvdbId}"),
        )
    }

    private suspend fun queueExclusionOrErr(
        error: ArrError,
        body: ImportListExclusionResource,
    ): ArrResult<WriteOutcome> {
        if (!error.isRetryable()) return ArrResult.err(error)
        store.put(
            serverId,
            EXCLUSION_TYPE,
            (-body.tvdbId).toString(),
            body.copy(id = -body.tvdbId),
            EXCLUSION_SER,
        )
        return queueOrErr(
            error,
            "POST",
            EXCLUSION_PATH,
            ArrJson.encodeToString(EXCLUSION_SER, body),
            "Exclude series " + (body.title?.takeIf { it.isNotBlank() } ?: "#${body.tvdbId}"),
        )
    }

    private suspend fun deleteExclusionOrErr(
        error: ArrError,
        exclusion: ImportListExclusionResource,
    ): ArrResult<WriteOutcome> {
        if (!error.isRetryable()) return ArrResult.err(error)
        store.delete(serverId, EXCLUSION_TYPE, exclusion.id.toString())
        val action = action(
            "DELETE",
            "$EXCLUSION_PATH/${exclusion.id}",
            null,
            "Delete exclusion " + (exclusion.title ?: "#${exclusion.tvdbId}"),
        )
        actionQueue.enqueue(action)
        return ArrResult.ok(WriteOutcome.Queued(action.actionId))
    }

    /** Drops a queued exclusion create matching [tvdbId]; replay-safe cancellation. */
    private suspend fun cancelPendingExclusion(tvdbId: Int) {
        actionQueue.pending()
            .filter { it.serverId == serverId && it.method == "POST" && it.path == EXCLUSION_PATH }
            .forEach { queued ->
                val body = runCatching {
                    ArrJson.decodeFromString(EXCLUSION_SER, queued.bodyJson.orEmpty())
                }.getOrNull()
                if (body?.tvdbId == tvdbId) {
                    actionQueue.remove(queued.actionId)
                }
            }
    }

    private suspend fun queueOrErr(
        error: ArrError,
        method: String,
        path: String,
        bodyJson: String?,
        description: String,
    ): ArrResult<WriteOutcome> {
        if (!error.isRetryable()) return ArrResult.err(error)
        val action = action(method, path, bodyJson, description)
        actionQueue.enqueue(action)
        return ArrResult.ok(WriteOutcome.Queued(action.actionId))
    }

    private fun action(method: String, path: String, bodyJson: String?, description: String) =
        QueuedAction(
            actionId = ActionId(UUID.nameUUIDFromBytes(
                "$serverId:$method:$path:$bodyJson".toByteArray(Charsets.UTF_8),
            ).toString()),
            serverId = serverId,
            appType = AppType.SONARR,
            method = method,
            path = path,
            bodyJson = bodyJson,
            description = description,
            enqueuedAtMillis = now(),
        )

    private fun ArrError.isRetryable(): Boolean =
        this is ArrError.Offline || this is ArrError.Network

    private fun HttpException.toArrError(): ArrError = when (code()) {
        401, 403 -> ArrError.Auth(code())
        else -> ArrError.Http(code(), message())
    }

    companion object {
        internal val SERIES_TYPE = EntityType("sonarr.series")
        internal val ROOT_FOLDER_TYPE = EntityType("sonarr.rootfolder")
        internal val QUALITY_PROFILE_TYPE = EntityType("sonarr.qualityprofile")
        internal val LANGUAGE_PROFILE_TYPE = EntityType("sonarr.languageprofile")
        internal val TAG_TYPE = EntityType("sonarr.tag")
        internal val IMPORT_LIST_TYPE = EntityType("sonarr.importlist")
        internal val EXCLUSION_TYPE = EntityType("sonarr.importlistexclusion")

        /** Relative paths stored in queued actions, matched by the replay executor. */
        internal const val SERIES_PATH = "api/v3/series"
        internal const val EXCLUSION_PATH = "api/v3/importlistexclusion"
        internal const val IMPORT_LIST_ACTION_PATH = "api/v3/importlist/action"
        internal const val LIST_SYNC_ACTION = "sync"

        /** Ten minutes, matching the library screen's series TTL. */
        internal const val SERIES_TTL_MILLIS: Long = dev.appolon.homelab.arr.core.CacheTtl.LIBRARY

        /** One hour; folders, profiles, tags, lists, and exclusions change rarely. */
        internal const val LOOKUP_TTL_MILLIS: Long = dev.appolon.homelab.arr.core.CacheTtl.SETTINGS

        private val SERIES_SER = SeriesResource.serializer()
        private val IMPORT_LIST_SER = ImportListResource.serializer()
        private val EXCLUSION_SER = ImportListExclusionResource.serializer()
    }
}
