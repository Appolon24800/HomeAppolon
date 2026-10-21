package dev.appolon.homelab.arr.feature.radarr.library.data

import dev.appolon.homelab.arr.api.radarr.api.MovieApi
import dev.appolon.homelab.arr.api.radarr.api.MovieEditorApi
import dev.appolon.homelab.arr.api.radarr.model.MovieEditorResource
import dev.appolon.homelab.arr.api.radarr.model.MovieResource
import dev.appolon.homelab.arr.api.radarr.model.QualityProfileResource
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import retrofit2.HttpException
import retrofit2.Response

/** One Radarr server command the library dispatches. */
enum class MovieCommand(val wireName: String) {
    /** Metadata refresh, per selection or library wide. */
    REFRESH_METADATA("RefreshMovie"),

    /** Disk rescan, per selection or library wide. */
    SCAN_DISK("RescanMovie"),
}

/** Runs a named Radarr command against the server; implemented over OkHttp POST. */
fun interface MovieCommandRunner {
    suspend fun run(command: String, movieIds: List<Int>?): ArrResult<Unit>
}

/** How a write landed: applied on the server, or queued for replay. */
sealed interface WriteOutcome {
    data object Synced : WriteOutcome
    data class Queued(val actionId: ActionId) : WriteOutcome
}

/**
 * Movies library repository. Reads are cache-first through the [EntityStore]
 * (observe plus a fetchedAt TTL driving refresh); writes go to the server
 * first and fall back to optimistic cache application plus [ActionQueue]
 * enqueue when the network call fails with [ArrError.Offline] or
 * [ArrError.Network]. Everything runs on IO; no function throws.
 */
class RadarrMoviesRepository(
    private val serverId: ServerId,
    private val store: EntityStore,
    private val movieApi: MovieApi,
    private val movieEditorApi: MovieEditorApi,
    private val qualityProfileApi: suspend () -> List<QualityProfileResource>,
    private val commandRunner: MovieCommandRunner,
    private val actionQueue: ActionQueue,
    private val now: () -> Long = System::currentTimeMillis,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) {

    fun observeMovies(): Flow<List<MovieResource>> =
        store.observe(serverId, MOVIE_TYPE, MovieResource.serializer())

    suspend fun cachedMovies(): List<MovieResource> =
        store.getAll(serverId, MOVIE_TYPE, MovieResource.serializer())

    /** True when the cache is absent or older than [ttlMillis]. */
    suspend fun isStale(ttlMillis: Long = MOVIE_TTL_MILLIS): Boolean {
        val fetched = store.lastFetchedAt(serverId, MOVIE_TYPE) ?: return true
        return now() - fetched > ttlMillis
    }

    /** Fetches the full library, reconciles the cache row by row. */
    suspend fun refresh(): ArrResult<Unit> = withContext(io) {
        val fetched = try {
            movieApi.list()
        } catch (e: HttpException) {
            return@withContext ArrResult.err(e.toArrError())
        } catch (e: Throwable) {
            return@withContext ArrResult.err(e.arrError())
        }
        val incomingIds = fetched.map { it.id }.toSet()
        val existingIds = cachedMovies().map { it.id }.toSet()
        existingIds.filter { it !in incomingIds }.forEach { id ->
            store.delete(serverId, MOVIE_TYPE, id.toString())
        }
        // One write for the whole library keeps the fetchedAt stamp uniform.
        store.putAll(
            serverId,
            MOVIE_TYPE,
            fetched.map { it.id.toString() to it },
            MovieResource.serializer(),
            fetchedAtMillis = now(),
        )
        // A successful refresh proves this server reachable, so queued
        // library writes replay right here instead of waiting for the sync
        // screen's manual retry.
        replayQueuedActions()
        ArrResult.ok(Unit)
    }

    /** Refresh only when the TTL expired; the steady-state path after the first load. */
    suspend fun refreshIfStale(ttlMillis: Long = MOVIE_TTL_MILLIS): ArrResult<Unit> =
        if (isStale(ttlMillis)) refresh() else ArrResult.ok(Unit)

    /** Quality profiles for filter labels and the editor dropdown, cached with its own TTL. */
    suspend fun qualityProfiles(
        ttlMillis: Long = PROFILE_TTL_MILLIS,
    ): ArrResult<List<QualityProfileResource>> =
        withContext(io) {
            val cached = store.getAll(serverId, PROFILE_TYPE, QualityProfileResource.serializer())
            val fetched = store.lastFetchedAt(serverId, PROFILE_TYPE)
            val fresh = fetched != null && now() - fetched <= ttlMillis
            if (fresh && cached.isNotEmpty()) {
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
                    PROFILE_TYPE,
                    profiles.map { it.id.toString() to it },
                    QualityProfileResource.serializer(),
                    fetchedAtMillis = now(),
                )
                ArrResult.ok(profiles)
            } else {
                ArrResult.ok(cached)
            }
        }

    /**
     * Bulk edit through the MovieEditor endpoint. On offline or network
     * failure the edit is applied optimistically to the cache and enqueued;
     * the caller learns which via [WriteOutcome].
     */
    suspend fun editMovies(editor: MovieEditorResource): ArrResult<WriteOutcome> =
        withContext(io) {
            val response = try {
                movieEditorApi.update(editor)
            } catch (e: HttpException) {
                return@withContext offlineOrErr(e.toArrError(), editor, isDelete = false)
            } catch (e: Throwable) {
                return@withContext offlineOrErr(e.arrError(), editor, isDelete = false)
            }
            if (response.isSuccessful) {
                response.body().orEmpty().forEach { touched ->
                    store.put(
                        serverId, MOVIE_TYPE,
                        touched.id.toString(), touched, MovieResource.serializer(),
                    )
                }
                ArrResult.ok(WriteOutcome.Synced)
            } else {
                ArrResult.err(response.toArrError())
            }
        }

    /** Bulk delete with the editor flags; offline deletes queue like edits. */
    suspend fun deleteMovies(
        movieIds: List<Int>,
        deleteFiles: Boolean,
        addImportExclusion: Boolean,
    ): ArrResult<WriteOutcome> = withContext(io) {
        val editor = MovieEditorResource(
            movieIds = movieIds,
            deleteFiles = deleteFiles,
            addImportExclusion = addImportExclusion,
        )
        try {
            // The endpoint returns an empty body; failures surface as exceptions.
            movieEditorApi.delete(editor)
            movieIds.forEach { id -> store.delete(serverId, MOVIE_TYPE, id.toString()) }
            ArrResult.ok(WriteOutcome.Synced)
        } catch (e: HttpException) {
            offlineOrErr(e.toArrError(), editor, isDelete = true)
        } catch (e: Throwable) {
            offlineOrErr(e.arrError(), editor, isDelete = true)
        }
    }

    /** Dispatches a command; offline dispatches queue and replay unchanged. */
    suspend fun runCommand(
        command: MovieCommand,
        movieIds: List<Int>?,
    ): ArrResult<WriteOutcome> = withContext(io) {
        val error = try {
            val result = commandRunner.run(command.wireName, movieIds)
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
            if (!movieIds.isNullOrEmpty()) append(",\"movieIds\":$movieIds")
            append("}")
        }
        val action = action(
            method = "POST",
            path = COMMAND_PATH,
            bodyJson = body,
            description = "Command ${command.wireName}",
        )
        actionQueue.enqueue(action)
        ArrResult.ok(WriteOutcome.Queued(action.actionId))
    }

    /** Movie ids touched by queued editor actions, for per-movie queued badges. */
    fun queuedMovieIds(): Flow<Set<Int>> = actionQueue.observe().map { pending ->
        pending.asSequence()
            .filter { it.serverId == serverId && it.path == EDITOR_PATH }
            .flatMap { action ->
                runCatching {
                    ArrJson.decodeFromString(
                        MovieEditorResource.serializer(), action.bodyJson.orEmpty(),
                    )
                }.getOrNull()?.movieIds.orEmpty()
            }
            .toSet()
    }

    /** Count of queued actions for this server, library wide indicator. */
    fun queuedCount(): Flow<Int> = actionQueue.observe().map { pending ->
        pending.count { it.serverId == serverId }
    }

    /**
     * Replays this library's queued writes through the queue's own replay,
     * so attempt counting and dead-lettering stay in one place. Runs only
     * when every pending action belongs to this library: a foreign app's
     * write must keep its attempt budget for the shared replay path instead
     * of failing here and counting toward its dead-letter limit. Returns
     * null when nothing was replayable.
     */
    suspend fun replayQueuedActions(): Pair<Int, Int>? = withContext(io) {
        val queued = actionQueue.pending()
        if (queued.isEmpty()) return@withContext null
        if (queued.any { it.serverId != serverId || it.path !in REPLAYABLE_PATHS }) {
            return@withContext null
        }
        try {
            actionQueue.replay(::executeQueued)
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            null
        }
    }

    /** Executes one of this repository's queued actions; false keeps it queued. */
    private suspend fun executeQueued(action: QueuedAction): Boolean = try {
        when {
            action.method == "POST" && action.path == COMMAND_PATH -> replayCommand(action)

            action.path == EDITOR_PATH -> replayEditor(action)

            else -> false
        }
    } catch (e: CancellationException) {
        throw e
    } catch (t: Throwable) {
        false
    }

    /** Replays a queued Radarr command through the same runner the UI uses. */
    private suspend fun replayCommand(action: QueuedAction): Boolean {
        val body = ArrJson.parseToJsonElement(action.bodyJson.orEmpty()).jsonObject
        val name = body["name"]?.jsonPrimitive?.contentOrNull ?: return false
        val movieIds = body["movieIds"]
            ?.jsonArray
            ?.mapNotNull { it.jsonPrimitive.intOrNull }
            ?.takeIf { it.isNotEmpty() }
        return commandRunner.run(name, movieIds) is ArrResult.Ok
    }

    /** Replays a queued bulk edit or delete and reconciles the cache. */
    private suspend fun replayEditor(action: QueuedAction): Boolean {
        val editor = ArrJson.decodeFromString(
            MovieEditorResource.serializer(),
            action.bodyJson.orEmpty(),
        )
        when (action.method.uppercase()) {
            "PUT" -> {
                val response = movieEditorApi.update(editor)
                if (!response.isSuccessful) return false
                response.body().orEmpty().forEach { touched ->
                    store.put(
                        serverId, MOVIE_TYPE,
                        touched.id.toString(), touched, MovieResource.serializer(),
                    )
                }
                return true
            }

            "DELETE" -> {
                movieEditorApi.delete(editor)
                editor.movieIds.orEmpty().forEach { id ->
                    store.delete(serverId, MOVIE_TYPE, id.toString())
                }
                return true
            }

            else -> return false
        }
    }

    private suspend fun offlineOrErr(
        error: ArrError,
        editor: MovieEditorResource,
        isDelete: Boolean,
    ): ArrResult<WriteOutcome> {
        if (!error.isRetryable()) return ArrResult.err(error)
        val targets = editor.movieIds.orEmpty().toSet()
        if (isDelete) {
            targets.forEach { id -> store.delete(serverId, MOVIE_TYPE, id.toString()) }
        } else {
            // Only touched rows are rewritten, and the fetch stamp is carried
            // over so an optimistic edit does not extend the refresh TTL.
            val stamp = store.lastFetchedAt(serverId, MOVIE_TYPE) ?: now()
            val merged = applyMovieEditor(cachedMovies(), editor).filter { it.id in targets }
            store.putAll(
                serverId,
                MOVIE_TYPE,
                merged.map { it.id.toString() to it },
                MovieResource.serializer(),
                fetchedAtMillis = stamp,
            )
        }
        val action = action(
            method = if (isDelete) "DELETE" else "PUT",
            path = EDITOR_PATH,
            bodyJson = ArrJson.encodeToString(MovieEditorResource.serializer(), editor),
            description = if (isDelete) "Delete movies" else "Edit movies",
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
            appType = AppType.RADARR,
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

    private fun <T> Response<T>.toArrError(): ArrError = when (code()) {
        401, 403 -> ArrError.Auth(code())
        else -> {
            val snippet = runCatching { errorBody()?.string() }.getOrNull()
                ?.trim()
                ?.take(200)
                ?.takeIf { it.isNotEmpty() }
            ArrError.Http(code(), snippet)
        }
    }

    companion object {
        internal val MOVIE_TYPE = EntityType("radarr.movie")
        internal val PROFILE_TYPE = EntityType("radarr.qualityprofile")
        internal val EDITOR_PATH = "api/v3/movie/editor"
        internal val COMMAND_PATH = "api/v3/command"
        private val REPLAYABLE_PATHS = setOf(EDITOR_PATH, COMMAND_PATH)

        /** Shared library freshness limit. */
        const val MOVIE_TTL_MILLIS: Long = dev.appolon.homelab.arr.core.CacheTtl.LIBRARY

        /** Shared settings freshness limit. */
        const val PROFILE_TTL_MILLIS: Long = dev.appolon.homelab.arr.core.CacheTtl.SETTINGS
    }
}
