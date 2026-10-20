package dev.appolon.homelab.arr.feature.radarr.addnew.data

import dev.appolon.homelab.arr.api.radarr.CollectionApi
import dev.appolon.homelab.arr.api.radarr.api.MovieApi
import dev.appolon.homelab.arr.api.radarr.model.CollectionResource
import dev.appolon.homelab.arr.api.radarr.model.MovieResource
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
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import retrofit2.HttpException
import retrofit2.Response

/**
 * Repository for the Collections browse tab. Lists are cache-first with a
 * fetchedAt TTL; monitoring and quality edits go to the server first and fall
 * back to an optimistic cache write plus [ActionQueue] enqueue when offline.
 * The per-collection search action dispatches Radarr's `MoviesSearch` command
 * with the library ids of the collection's missing movies, which is exactly
 * what the web client does.
 */
class RadarrCollectionsRepository(
    private val serverId: ServerId,
    private val store: EntityStore,
    private val collectionApi: CollectionApi,
    private val movieApi: MovieApi,
    private val commandRunner: AddNewCommandRunner,
    private val actionQueue: ActionQueue,
    private val now: () -> Long = System::currentTimeMillis,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) {

    fun observeCollections(): Flow<List<CollectionResource>> =
        store.observe(serverId, COLLECTION_TYPE, CollectionResource.serializer())

    suspend fun cachedCollections(): List<CollectionResource> =
        store.getAll(serverId, COLLECTION_TYPE, CollectionResource.serializer())

    /** Fetches every collection and reconciles the cache row by row. */
    suspend fun refresh(): ArrResult<Unit> = withContext(io) {
        val fetched = try {
            collectionApi.collections()
        } catch (e: HttpException) {
            return@withContext ArrResult.err(e.toArrError())
        } catch (e: Throwable) {
            return@withContext ArrResult.err(e.arrError())
        }
        val incomingIds = fetched.map { it.id.toString() }.toSet()
        cachedCollections().forEach { cached ->
            if (cached.id.toString() !in incomingIds) {
                store.delete(serverId, COLLECTION_TYPE, cached.id.toString())
            }
        }
        store.putAll(
            serverId,
            COLLECTION_TYPE,
            fetched.map { it.id.toString() to it },
            CollectionResource.serializer(),
            fetchedAtMillis = now(),
        )
        ArrResult.ok(Unit)
    }

    /** Refresh only when the TTL expired. */
    suspend fun refreshIfStale(ttlMillis: Long = COLLECTION_TTL_MILLIS): ArrResult<Unit> {
        val fetched = store.lastFetchedAt(serverId, COLLECTION_TYPE) ?: return refresh()
        return if (now() - fetched > ttlMillis) refresh() else ArrResult.ok(Unit)
    }

    /**
     * Library movie ids missing a file inside one TMDB collection, resolving
     * through the shared `radarr.movie` cache and refreshing it when empty.
     */
    suspend fun missingMovieIds(collectionTmdbId: Int): ArrResult<List<Int>> = withContext(io) {
        var cached = store.getAll(serverId, MOVIE_TYPE, MovieResource.serializer())
        if (cached.isEmpty()) {
            when (val result = refreshMovies()) {
                is ArrResult.Err -> return@withContext ArrResult.err(result.error)
                is ArrResult.Ok -> cached =
                    store.getAll(serverId, MOVIE_TYPE, MovieResource.serializer())
            }
        }
        ArrResult.ok(AddNewLogic.missingMovieIds(cached, collectionTmdbId))
    }

    /**
     * Edits one collection (PUT /api/v3/collection/{id}): monitoring, quality
     * profile, search-on-add. Offline edits apply optimistically and queue.
     */
    suspend fun updateCollection(updated: CollectionResource): ArrResult<AddOutcome> =
        withContext(io) {
            val response = try {
                collectionApi.updateCollection(updated.id.toString(), updated)
            } catch (e: HttpException) {
                return@withContext offlineOrErr(e.toArrError(), updated)
            } catch (e: Throwable) {
                return@withContext offlineOrErr(e.arrError(), updated)
            }
            if (response.isSuccessful) {
                store.put(
                    serverId,
                    COLLECTION_TYPE,
                    updated.id.toString(),
                    response.body() ?: updated,
                    CollectionResource.serializer(),
                )
                ArrResult.ok(AddOutcome.Synced)
            } else {
                ArrResult.err(response.toArrError())
            }
        }

    /**
     * Searches the missing movies of one collection. The command payload puts
     * `movieIds` at the JSON root, so it travels through the command runner;
     * offline dispatches queue byte-identical JSON for replay.
     */
    suspend fun searchMissing(movieIds: List<Int>): ArrResult<AddOutcome> = withContext(io) {
        if (movieIds.isEmpty()) {
            return@withContext ArrResult.ok(AddOutcome.Synced)
        }
        val error = try {
            val result = commandRunner.run(AddNewLogic.COMMAND_MOVIES_SEARCH, movieIds)
            if (result is ArrResult.Ok) {
                return@withContext ArrResult.ok(AddOutcome.Synced)
            }
            (result as ArrResult.Err).error
        } catch (e: Throwable) {
            e.arrError()
        }
        if (!error.isRetryable()) {
            return@withContext ArrResult.err(error)
        }
        val body = buildJsonObject {
            put("name", AddNewLogic.COMMAND_MOVIES_SEARCH)
            put("movieIds", JsonArray(movieIds.map { JsonPrimitive(it) }))
        }.toString()
        val action = action(
            method = "POST",
            path = COMMAND_PATH,
            bodyJson = body,
            description = "Command ${AddNewLogic.COMMAND_MOVIES_SEARCH}",
        )
        actionQueue.enqueue(action)
        ArrResult.ok(AddOutcome.Queued(action.actionId))
    }

    /** Collection ids with an edit queued, for per-row queued badges. */
    fun queuedCollectionIds(): Flow<Set<Int>> = actionQueue.observe().map { pending ->
        pending.asSequence()
            .filter {
                it.serverId == serverId && it.method == "PUT" &&
                    it.path.startsWith(COLLECTION_PREFIX)
            }
            .mapNotNull { it.path.removePrefix(COLLECTION_PREFIX).toIntOrNull() }
            .toSet()
    }

    private suspend fun offlineOrErr(
        error: ArrError,
        updated: CollectionResource,
    ): ArrResult<AddOutcome> {
        if (!error.isRetryable()) return ArrResult.err(error)
        store.put(
            serverId,
            COLLECTION_TYPE,
            updated.id.toString(),
            updated,
            CollectionResource.serializer(),
        )
        val action = action(
            method = "PUT",
            path = "$COLLECTION_PREFIX${updated.id}",
            bodyJson = ArrJson.encodeToString(CollectionResource.serializer(), updated),
            description = "Edit collection " +
                (updated.title ?: updated.id.toString()),
        )
        actionQueue.enqueue(action)
        return ArrResult.ok(AddOutcome.Queued(action.actionId))
    }

    private suspend fun refreshMovies(): ArrResult<Unit> = try {
        val fetched = movieApi.list()
        store.putAll(
            serverId,
            MOVIE_TYPE,
            fetched.map { it.id.toString() to it },
            MovieResource.serializer(),
            fetchedAtMillis = now(),
        )
        ArrResult.ok(Unit)
    } catch (e: HttpException) {
        ArrResult.err(e.toArrError())
    } catch (e: Throwable) {
        ArrResult.err(e.arrError())
    }

    private fun action(method: String, path: String, bodyJson: String, description: String) =
        QueuedAction(
            actionId = ActionId(UUID.randomUUID().toString()),
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
            val snippet = runCatching { errorBody()?.string() }
                .getOrNull()?.trim()?.take(200)?.takeIf { it.isNotEmpty() }
            ArrError.Http(code(), snippet)
        }
    }

    companion object {
        internal val COLLECTION_TYPE = EntityType("radarr.collection")
        internal val MOVIE_TYPE = EntityType("radarr.movie")

        internal const val COLLECTION_PREFIX = "api/v3/collection/"
        internal const val COMMAND_PATH = "api/v3/command"

        /** Ten minutes; collections change only when movies are added. */
        const val COLLECTION_TTL_MILLIS: Long = 10 * 60 * 1000
    }
}
