package dev.appolon.homelab.arr.feature.sonarr.library.data

import dev.appolon.homelab.arr.api.sonarr.SeasonPassApi
import dev.appolon.homelab.arr.api.sonarr.SeriesApi
import dev.appolon.homelab.arr.api.sonarr.SeriesEditorApi
import dev.appolon.homelab.arr.api.sonarr.model.QualityProfileResource
import dev.appolon.homelab.arr.api.sonarr.model.SeasonPassResource
import dev.appolon.homelab.arr.api.sonarr.model.SeriesEditorResource
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
import dev.appolon.homelab.arr.net.ArrJson
import dev.appolon.homelab.arr.net.arrError
import java.util.UUID
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import retrofit2.HttpException

/** One Sonarr command the series library dispatches. */
enum class SeriesCommand(val wireName: String) {
    /** Metadata refresh, per selection or library wide. */
    REFRESH_METADATA("RefreshSeries"),

    /** Disk rescan, per selection or library wide. */
    SCAN_DISK("RescanSeries"),
}

/**
 * Runs a named Sonarr command against the server. Implemented over a raw
 * POST: command payloads put selection fields such as `seriesId` at the JSON
 * root, a shape the committed `CommandResource` DTO cannot express, and
 * Sonarr rejects ids nested inside `body`.
 */
fun interface SonarrCommandRunner {
    /** [seriesId] null means a library-wide command. */
    suspend fun run(command: String, seriesId: Int?): ArrResult<Unit>
}

/** How a write landed: applied on the server, or queued for replay. */
sealed interface WriteOutcome {
    data object Synced : WriteOutcome
    data class Queued(val actionId: ActionId) : WriteOutcome
}

/**
 * Series library repository. Reads are cache-first through the [EntityStore]
 * (observe plus a fetchedAt TTL driving refresh); writes go to the server
 * first and fall back to optimistic cache application plus [ActionQueue]
 * enqueue when the network call fails with [ArrError.Offline] or
 * [ArrError.Network]. Editor and season-pass endpoints return no body, so a
 * successful write applies the same merge the server performed to keep the
 * cache truthful without a refetch. No function throws.
 */
class SonarrSeriesRepository(
    private val serverId: ServerId,
    private val store: EntityStore,
    private val seriesApi: SeriesApi,
    private val seriesEditorApi: SeriesEditorApi,
    private val seasonPassApi: SeasonPassApi,
    private val qualityProfileApi: suspend () -> List<QualityProfileResource>,
    private val tagApi: suspend () -> List<TagResource>,
    private val commandRunner: SonarrCommandRunner,
    private val actionQueue: ActionQueue,
    private val now: () -> Long = System::currentTimeMillis,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {

    fun observeSeries(): Flow<List<SeriesResource>> =
        store.observe(serverId, SERIES_TYPE, SeriesResource.serializer())

    suspend fun cachedSeries(): List<SeriesResource> =
        store.getAll(serverId, SERIES_TYPE, SeriesResource.serializer())

    /** True when the cache is absent or older than [ttlMillis]. */
    suspend fun isStale(ttlMillis: Long = SERIES_TTL_MILLIS): Boolean {
        val fetched = store.lastFetchedAt(serverId, SERIES_TYPE) ?: return true
        return now() - fetched > ttlMillis
    }

    /** Fetches the full library, reconciling the cache row by row. */
    suspend fun refresh(): ArrResult<Unit> = withContext(ioDispatcher) {
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
            val id = cached.id.toString()
            val replacement = incoming[cached.id]
            if (replacement != null) {
                store.put(serverId, SERIES_TYPE, id, replacement, SeriesResource.serializer())
            } else {
                store.delete(serverId, SERIES_TYPE, id)
            }
        }
        val fresh = fetched.filter { series -> existing.none { it.id == series.id } }
        store.putAll(
            serverId,
            SERIES_TYPE,
            fresh.map { it.id.toString() to it },
            SeriesResource.serializer(),
            fetchedAtMillis = now(),
        )
        ArrResult.ok(Unit)
    }

    /** Refresh only when the TTL expired; the steady-state path after the first load. */
    suspend fun refreshIfStale(ttlMillis: Long = SERIES_TTL_MILLIS): ArrResult<Unit> =
        if (isStale(ttlMillis)) refresh() else ArrResult.ok(Unit)

    /**
     * Quality profiles for filter labels and the editor dropdown, cached with
     * its own TTL because they change rarely.
     */
    suspend fun qualityProfiles(ttlMillis: Long = LOOKUP_TTL_MILLIS) =
        lookup(PROFILE_TYPE, QualityProfileResource.serializer(), { it.id.toString() }, qualityProfileApi, ttlMillis)

    /** Tags for filter labels and the editor tag picker, same caching shape as profiles. */
    suspend fun tags(ttlMillis: Long = LOOKUP_TTL_MILLIS) =
        lookup(TAG_TYPE, TagResource.serializer(), { it.id.toString() }, tagApi, ttlMillis)

    /** Cache-first lookup with a TTL; an empty fetch keeps the previous cache. */
    private suspend fun <T> lookup(
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
     * Bulk edit through the series editor endpoint. On offline or network
     * failure the edit is applied optimistically to the cache and enqueued;
     * the caller learns which via [WriteOutcome].
     */
    suspend fun editSeries(editor: SeriesEditorResource): ArrResult<WriteOutcome> =
        withContext(ioDispatcher) {
            try {
                seriesEditorApi.update(editor)
            } catch (e: HttpException) {
                return@withContext offlineOrErr(e.toArrError(), editorBody(editor, isDelete = false))
            } catch (e: Throwable) {
                return@withContext offlineOrErr(e.arrError(), editorBody(editor, isDelete = false))
            }
            applyEditLocally(editor)
            ArrResult.ok(WriteOutcome.Synced)
        }

    /** Bulk delete with the editor flags; offline deletes queue like edits. */
    suspend fun deleteSeries(
        seriesIds: List<Int>,
        deleteFiles: Boolean,
        addImportListExclusion: Boolean,
    ): ArrResult<WriteOutcome> = withContext(ioDispatcher) {
        val editor = SeriesEditorResource(
            seriesIds = seriesIds,
            deleteFiles = deleteFiles,
            addImportListExclusion = addImportListExclusion,
        )
        try {
            seriesEditorApi.delete(editor)
        } catch (e: HttpException) {
            return@withContext offlineOrErr(
                e.toArrError(),
                editorBody(editor, isDelete = true),
                optimisticDelete = seriesIds,
            )
        } catch (e: Throwable) {
            return@withContext offlineOrErr(
                e.arrError(),
                editorBody(editor, isDelete = true),
                optimisticDelete = seriesIds,
            )
        }
        seriesIds.forEach { id -> store.delete(serverId, SERIES_TYPE, id.toString()) }
        ArrResult.ok(WriteOutcome.Synced)
    }

    /**
     * Season pass bulk monitoring through the seasonpass endpoint. Applies the
     * pass optimistically on both the success path (the endpoint returns no
     * body) and the queued path.
     */
    suspend fun seasonPass(pass: SeasonPassResource): ArrResult<WriteOutcome> =
        withContext(ioDispatcher) {
            try {
                seasonPassApi.create(pass)
            } catch (e: HttpException) {
                return@withContext offlineOrErr(
                    e.toArrError(),
                    ActionBody("POST", SEASONPASS_PATH, ArrJson.encodeToString(SEASONPASS, pass)),
                )
            } catch (e: Throwable) {
                return@withContext offlineOrErr(
                    e.arrError(),
                    ActionBody("POST", SEASONPASS_PATH, ArrJson.encodeToString(SEASONPASS, pass)),
                )
            }
            applySeasonPass(cachedSeries(), pass).forEach { series ->
                store.put(serverId, SERIES_TYPE, series.id.toString(), series, SERIES_SER)
            }
            ArrResult.ok(WriteOutcome.Synced)
        }

    /**
     * Dispatches a command. A null [seriesIds] sends one library-wide command;
     * one id sends a scoped command; several ids send one command per series
     * because the Sonarr command contract is single-series scoped. Offline
     * dispatches queue and replay unchanged.
     */
    suspend fun runCommand(
        command: SeriesCommand,
        seriesIds: List<Int>?,
    ): ArrResult<WriteOutcome> = withContext(ioDispatcher) {
        val bodies = when {
            seriesIds == null -> listOf(commandBody(command, null))
            seriesIds.isEmpty() -> return@withContext ArrResult.ok(WriteOutcome.Synced)
            else -> seriesIds.map { commandBody(command, it) }
        }
        var lastError: ArrError? = null
        for (body in bodies) {
            val failure = try {
                val result = commandRunner.run(command.wireName, body.seriesId)
                if (result is ArrResult.Err) result.error else null
            } catch (e: Throwable) {
                e.arrError()
            }
            if (failure != null) {
                if (!failure.isRetryable()) {
                    return@withContext ArrResult.err(failure)
                }
                lastError = failure
            }
        }
        if (lastError != null) {
            val actionIds = bodies.map { body ->
                val action = action(
                    method = "POST",
                    path = COMMAND_PATH,
                    bodyJson = body.json,
                    description = "Command ${command.wireName}" +
                        (body.seriesId?.let { " series $it" } ?: ""),
                )
                actionQueue.enqueue(action)
                action.actionId
            }
            ArrResult.ok(WriteOutcome.Queued(actionIds.first()))
        } else {
            ArrResult.ok(WriteOutcome.Synced)
        }
    }

    /** Series ids touched by queued editor, season pass, and command actions. */
    fun queuedSeriesIds(): Flow<Set<Int>> = actionQueue.observe().map { pending ->
        pending.asSequence()
            .filter { it.serverId == serverId }
            .flatMap(::seriesIdsOf)
            .toSet()
    }

    /** Count of queued actions for this server, the library-wide queued indicator. */
    fun queuedCount(): Flow<Int> = actionQueue.observe().map { pending ->
        pending.count { it.serverId == serverId }
    }

    /** Parses the series ids a queued action touches, ignoring malformed bodies. */
    private fun seriesIdsOf(action: QueuedAction): Sequence<Int> = sequence {
        when (action.path) {
            EDITOR_PATH -> {
                val editor = runCatching {
                    ArrJson.decodeFromString(SeriesEditorResource.serializer(), action.bodyJson.orEmpty())
                }.getOrNull()
                yieldAll(editor?.seriesIds.orEmpty())
            }

            SEASONPASS_PATH -> {
                val pass = runCatching {
                    ArrJson.decodeFromString(SEASONPASS, action.bodyJson.orEmpty())
                }.getOrNull()
                yieldAll(pass?.series.orEmpty().map { it.id })
            }

            COMMAND_PATH -> {
                val root = runCatching {
                    ArrJson.parseToJsonElement(action.bodyJson.orEmpty()) as? JsonObject
                }.getOrNull()
                yieldAll(listOfNotNull(root?.get("seriesId")?.jsonPrimitive?.int))
            }
        }
    }

    private suspend fun applyEditLocally(editor: SeriesEditorResource) {
        val targets = editor.seriesIds.orEmpty().toSet()
        if (targets.isEmpty()) return
        // Only touched rows are rewritten so the fetchedAt of untouched series survives.
        applySeriesEditor(cachedSeries(), editor)
            .filter { it.id in targets }
            .forEach { series ->
                store.put(serverId, SERIES_TYPE, series.id.toString(), series, SERIES_SER)
            }
    }

    private data class ActionBody(
        val method: String,
        val path: String,
        val json: String?,
        val seriesId: Int? = null,
    )

    private fun editorBody(editor: SeriesEditorResource, isDelete: Boolean) = ActionBody(
        method = if (isDelete) "DELETE" else "PUT",
        path = EDITOR_PATH,
        json = ArrJson.encodeToString(SeriesEditorResource.serializer(), editor),
    )

    private fun commandBody(command: SeriesCommand, seriesId: Int?): ActionBody {
        val root = buildJsonObject {
            put("name", command.wireName)
            seriesId?.let { put("seriesId", it) }
        }
        return ActionBody("POST", COMMAND_PATH, root.toString(), seriesId)
    }

    private suspend fun offlineOrErr(
        error: ArrError,
        body: ActionBody,
        optimisticDelete: List<Int>? = null,
    ): ArrResult<WriteOutcome> {
        if (!error.isRetryable()) return ArrResult.err(error)
        if (optimisticDelete != null) {
            optimisticDelete.forEach { id -> store.delete(serverId, SERIES_TYPE, id.toString()) }
        } else if (body.path == EDITOR_PATH) {
            runCatching {
                ArrJson.decodeFromString(SeriesEditorResource.serializer(), body.json.orEmpty())
            }.getOrNull()?.let { editor -> applyEditLocally(editor) }
        } else if (body.path == SEASONPASS_PATH) {
            runCatching { ArrJson.decodeFromString(SEASONPASS, body.json.orEmpty()) }
                .getOrNull()
                ?.let { pass ->
                    applySeasonPass(cachedSeries(), pass).forEach { series ->
                        store.put(serverId, SERIES_TYPE, series.id.toString(), series, SERIES_SER)
                    }
                }
        }
        val action = action(
            method = body.method,
            path = body.path,
            bodyJson = body.json,
            description = when (body.path) {
                EDITOR_PATH -> if (body.method == "DELETE") "Delete series" else "Edit series"
                SEASONPASS_PATH -> "Season pass"
                else -> "Command"
            },
        )
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

    private companion object {
        internal val SERIES_TYPE = EntityType("sonarr.series")
        internal val PROFILE_TYPE = EntityType("sonarr.qualityprofile")
        internal val TAG_TYPE = EntityType("sonarr.tag")
        internal val EDITOR_PATH = "api/v3/series/editor"
        internal val SEASONPASS_PATH = "api/v3/seasonpass"
        internal val COMMAND_PATH = "api/v3/command"
        private val SERIES_SER = SeriesResource.serializer()
        private val SEASONPASS = SeasonPassResource.serializer()

        /** Ten minutes; the web library polls no faster either. */
        const val SERIES_TTL_MILLIS: Long = dev.appolon.homelab.arr.core.CacheTtl.LIBRARY

        /** One hour; profiles and tags change rarely. */
        const val LOOKUP_TTL_MILLIS: Long = dev.appolon.homelab.arr.core.CacheTtl.SETTINGS
    }
}
