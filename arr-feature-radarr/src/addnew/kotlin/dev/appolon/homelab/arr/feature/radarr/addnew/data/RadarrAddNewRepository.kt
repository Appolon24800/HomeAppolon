package dev.appolon.homelab.arr.feature.radarr.addnew.data

import dev.appolon.homelab.arr.api.radarr.ImportListMoviesApi
import dev.appolon.homelab.arr.api.radarr.QualityProfileApi
import dev.appolon.homelab.arr.api.radarr.RootFolderApi
import dev.appolon.homelab.arr.api.radarr.TagApi
import dev.appolon.homelab.arr.api.radarr.api.MovieApi
import dev.appolon.homelab.arr.api.radarr.api.MovieImportApi
import dev.appolon.homelab.arr.api.radarr.api.MovieLookupApi
import dev.appolon.homelab.arr.api.radarr.model.ImportListMoviesResource
import dev.appolon.homelab.arr.api.radarr.model.MovieResource
import dev.appolon.homelab.arr.api.radarr.model.QualityProfileResource
import dev.appolon.homelab.arr.api.radarr.model.RootFolderResource
import dev.appolon.homelab.arr.api.radarr.model.TagResource
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
import kotlinx.serialization.builtins.ListSerializer
import retrofit2.HttpException
import retrofit2.Response

/**
 * Repository behind the Add new screens: movie lookup with year
 * disambiguation, the add-option catalogs (root folders, quality profiles,
 * tags), the discovery feed, and movie adds. Reads are cache-first through
 * the [EntityStore] with a fetchedAt TTL driving refresh; adds go to the
 * server first and fall back to optimistic cache rows plus [ActionQueue]
 * enqueue when the failure is retryable (offline or network). The movie rows
 * share the `radarr.movie` entity type with the library feature so both
 * screens observe one source of truth. Everything runs on IO; nothing throws.
 */
class RadarrAddNewRepository(
    private val serverId: ServerId,
    private val store: EntityStore,
    private val movieLookupApi: MovieLookupApi,
    private val movieApi: MovieApi,
    private val movieImportApi: MovieImportApi,
    private val importListMoviesApi: ImportListMoviesApi,
    private val rootFolderApi: RootFolderApi,
    private val tagApi: TagApi,
    private val qualityProfileApi: QualityProfileApi,
    private val actionQueue: ActionQueue,
    private val now: () -> Long = System::currentTimeMillis,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) {

    /** Lookup runs server-side every time: results change with TMDB, so no cache. */
    suspend fun search(rawQuery: String): ArrResult<List<MovieResource>> =
        withContext(io) {
            val parsed = AddNewLogic.parseSearchQuery(rawQuery)
            if (parsed.term.isEmpty()) {
                return@withContext ArrResult.ok(emptyList())
            }
            val results = try {
                movieLookupApi.lookup(parsed.term)
            } catch (e: HttpException) {
                return@withContext ArrResult.err(e.toArrError())
            } catch (e: Throwable) {
                return@withContext ArrResult.err(e.arrError())
            }
            ArrResult.ok(AddNewLogic.rankByYear(results, parsed.year) { it.year })
        }

    /** Library movies as observed by every Radarr screen, for existing flags. */
    fun observeLibraryMovies(): Flow<List<MovieResource>> =
        store.observe(serverId, MOVIE_TYPE, MovieResource.serializer())

    /** Cache-first library sync feeding the in-library badges on results. */
    suspend fun syncLibraryIfStale(ttlMillis: Long = MOVIE_TTL_MILLIS): ArrResult<Unit> =
        withContext(io) {
            val fetched = store.lastFetchedAt(serverId, MOVIE_TYPE)
            if (isFresh(ttlMillis, fetched)) {
                return@withContext ArrResult.ok(Unit)
            }
            val fetchedMovies = try {
                movieApi.list()
            } catch (e: HttpException) {
                return@withContext ArrResult.err(e.toArrError())
            } catch (e: Throwable) {
                return@withContext ArrResult.err(e.arrError())
            }
            reconcileMovies(fetchedMovies)
            ArrResult.ok(Unit)
        }

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
                rootFolderApi.rootFolders()
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

    /** Quality profiles for the add sheet; rows shared with the library. */
    suspend fun qualityProfiles(
        ttlMillis: Long = CATALOG_TTL_MILLIS,
    ): ArrResult<List<QualityProfileResource>> = withContext(io) {
        val cached = store.getAll(serverId, PROFILE_TYPE, QualityProfileResource.serializer())
        val fetchedAt = store.lastFetchedAt(serverId, PROFILE_TYPE)
        if (isFresh(ttlMillis, fetchedAt) && cached.isNotEmpty()) {
            return@withContext ArrResult.ok(cached)
        }
            val profiles = try {
                qualityProfileApi.qualityProfiles()
            } catch (e: HttpException) {
                return@withContext serveOrErr(cached, e.toArrError())
            } catch (e: Throwable) {
                return@withContext serveOrErr(cached, e.arrError())
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

    /** Tags for the add sheet chips. */
    suspend fun tags(ttlMillis: Long = CATALOG_TTL_MILLIS): ArrResult<List<TagResource>> =
        withContext(io) {
            val cached = store.getAll(serverId, TAG_TYPE, TagResource.serializer())
            val fetchedAt = store.lastFetchedAt(serverId, TAG_TYPE)
            if (isFresh(ttlMillis, fetchedAt) && cached.isNotEmpty()) {
                return@withContext ArrResult.ok(cached)
            }
            val fetched = try {
                tagApi.tags()
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

    /**
     * The discovery feed: recommendations, trending, and popular in one call.
     * Cached under its own short TTL so the Discover tab opens instantly and
     * still refreshes when stale.
     */
    suspend fun discover(
        ttlMillis: Long = DISCOVERY_TTL_MILLIS,
    ): ArrResult<List<ImportListMoviesResource>> = withContext(io) {
        val cached = store.getAll(serverId, DISCOVERY_TYPE, ImportListMoviesResource.serializer())
        val fetchedAt = store.lastFetchedAt(serverId, DISCOVERY_TYPE)
        if (isFresh(ttlMillis, fetchedAt) && cached.isNotEmpty()) {
            return@withContext ArrResult.ok(cached)
        }
            val feed = try {
                importListMoviesApi.importListMovies(
                    includeRecommendations = true,
                    includeTrending = true,
                    includePopular = true,
                )
            } catch (e: HttpException) {
                return@withContext serveOrErr(cached, e.toArrError())
            } catch (e: Throwable) {
                return@withContext serveOrErr(cached, e.arrError())
            }
            if (feed.isNotEmpty()) {
                reconcileDiscovery(feed)
                ArrResult.ok(feed)
            } else {
                ArrResult.ok(cached)
            }
        }

    /**
     * Adds one movie (POST /api/v3/movie). On success the created movie is
     * written through to the cache. On a retryable failure the payload is
     * applied optimistically under a `add-tmdb-` key and the POST is queued.
     */
    suspend fun addMovie(payload: MovieResource): ArrResult<AddOutcome> = withContext(io) {
        val response = try {
            movieApi.create(payload)
        } catch (e: HttpException) {
            return@withContext offlineAddOrErr(e.toArrError(), listOf(payload), single = true)
        } catch (e: Throwable) {
            return@withContext offlineAddOrErr(e.arrError(), listOf(payload), single = true)
        }
        if (response.isSuccessful) {
            val created = response.body()
            if (created != null && created.id != 0) {
                store.put(
                    serverId,
                    MOVIE_TYPE,
                    created.id.toString(),
                    created,
                    MovieResource.serializer(),
                )
            }
            ArrResult.ok(AddOutcome.Synced)
        } else {
            ArrResult.err(response.toArrError())
        }
    }

    /**
     * Bulk add for discovery selections (POST /api/v3/movie/import). The
     * offline path queues one replayable action for the whole batch.
     */
    suspend fun importMovies(payloads: List<MovieResource>): ArrResult<AddOutcome> =
        withContext(io) {
            if (payloads.isEmpty()) {
                return@withContext ArrResult.ok(AddOutcome.Synced)
            }
            val imported = try {
                movieImportApi.importMovies(payloads)
            } catch (e: HttpException) {
                return@withContext offlineAddOrErr(e.toArrError(), payloads, single = false)
            } catch (e: Throwable) {
                return@withContext offlineAddOrErr(e.arrError(), payloads, single = false)
            }
            imported.forEach { movie ->
                if (movie.id != 0) {
                    store.put(
                        serverId,
                        MOVIE_TYPE,
                        movie.id.toString(),
                        movie,
                        MovieResource.serializer(),
                    )
                }
            }
            ArrResult.ok(AddOutcome.Synced)
        }

    /** TMDB ids with an add queued, for per-result "queued" badges. */
    fun queuedTmdbIds(): Flow<Set<Int>> = actionQueue.observe().map { pending ->
        pending.asSequence()
            .filter { it.serverId == serverId && (it.path == ADD_PATH || it.path == IMPORT_PATH) }
            .flatMap { action ->
                val movies = when (action.path) {
                    ADD_PATH -> runCatching {
                        listOf(
                            ArrJson.decodeFromString(
                                MovieResource.serializer(),
                                action.bodyJson.orEmpty(),
                            ),
                        )
                    }.getOrNull()

                    IMPORT_PATH -> runCatching {
                        ArrJson.decodeFromString(
                            ListSerializer(MovieResource.serializer()),
                            action.bodyJson.orEmpty(),
                        )
                    }.getOrNull()

                    else -> emptyList<MovieResource>()
                }
                movies.orEmpty().asSequence().map { it.tmdbId }
            }
            .toSet()
    }

    /** Count of queued actions for this server, the add screen's sync banner. */
    fun queuedCount(): Flow<Int> = actionQueue.observe().map { pending ->
        pending.count { it.serverId == serverId }
    }

    private suspend fun offlineAddOrErr(
        error: ArrError,
        payloads: List<MovieResource>,
        single: Boolean,
    ): ArrResult<AddOutcome> {
        if (!error.isRetryable()) return ArrResult.err(error)
        payloads.forEach { payload ->
            // Optimistic row keyed by TMDB id so multiple offline adds never
            // collide on the id-less payload and the library observes them.
            store.put(
                serverId,
                MOVIE_TYPE,
                optimisticKey(payload.tmdbId),
                payload,
                MovieResource.serializer(),
            )
        }
        val action = action(
            method = "POST",
            path = if (single) ADD_PATH else IMPORT_PATH,
            bodyJson = if (single) {
                ArrJson.encodeToString(MovieResource.serializer(), payloads.first())
            } else {
                ArrJson.encodeToString(ListSerializer(MovieResource.serializer()), payloads)
            },
            description = if (single) {
                val first = payloads.firstOrNull()
                "Add movie " + (first?.title ?: first?.tmdbId.toString())
            } else {
                "Import ${payloads.size} movies"
            },
        )
        actionQueue.enqueue(action)
        return ArrResult.ok(AddOutcome.Queued(action.actionId))
    }

    /** Server rows replace by id; optimistic offline-add rows are preserved. */
    private suspend fun reconcileMovies(incoming: List<MovieResource>) {
        val existing = store.getAll(serverId, MOVIE_TYPE, MovieResource.serializer())
        val incomingIds = incoming.map { it.id.toString() }.toSet()
        existing.forEach { cached ->
            val key = cached.id.toString()
            if (cached.id != 0 && key !in incomingIds) {
                store.delete(serverId, MOVIE_TYPE, key)
            }
        }
        store.putAll(
            serverId,
            MOVIE_TYPE,
            incoming.map { it.id.toString() to it },
            MovieResource.serializer(),
            fetchedAtMillis = now(),
        )
    }

    private suspend fun reconcileDiscovery(feed: List<ImportListMoviesResource>) {
        val existing = store.getAll(serverId, DISCOVERY_TYPE, ImportListMoviesResource.serializer())
        val incomingIds = feed.map { it.tmdbId.toString() }.toSet()
        existing.forEach { cached ->
            if (cached.tmdbId.toString() !in incomingIds) {
                store.delete(serverId, DISCOVERY_TYPE, cached.tmdbId.toString())
            }
        }
        store.putAll(
            serverId,
            DISCOVERY_TYPE,
            feed.map { it.tmdbId.toString() to it },
            ImportListMoviesResource.serializer(),
            fetchedAtMillis = now(),
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
        /** Same rows and TTL window the movies library feature uses. */
        internal val MOVIE_TYPE = EntityType("radarr.movie")
        internal val PROFILE_TYPE = EntityType("radarr.qualityprofile")
        internal val ROOT_FOLDER_TYPE = EntityType("radarr.rootfolder")
        internal val TAG_TYPE = EntityType("radarr.tag")
        internal val DISCOVERY_TYPE = EntityType("radarr.discovery")

        internal const val ADD_PATH = "api/v3/movie"
        internal const val IMPORT_PATH = "api/v3/movie/import"

        internal fun optimisticKey(tmdbId: Int): String = "add-tmdb-$tmdbId"

        /** Ten minutes, matching the library feature's movie TTL. */
        const val MOVIE_TTL_MILLIS: Long = 10 * 60 * 1000

        /** One hour; add-option catalogs change rarely. */
        const val CATALOG_TTL_MILLIS: Long = 60 * 60 * 1000

        /** Five minutes; discovery feeds move faster than catalogs. */
        const val DISCOVERY_TTL_MILLIS: Long = 5 * 60 * 1000
    }
}
