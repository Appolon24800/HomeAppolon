package dev.appolon.homelab.arr.feature.radarr.detail

import dev.appolon.homelab.arr.api.radarr.model.CreditResource
import dev.appolon.homelab.arr.api.radarr.model.ExtraFileResource
import dev.appolon.homelab.arr.api.radarr.model.HistoryResource
import dev.appolon.homelab.arr.api.radarr.model.ManualImportResource
import dev.appolon.homelab.arr.api.radarr.model.ManualImportReprocessResource
import dev.appolon.homelab.arr.api.radarr.model.MovieFileResource
import dev.appolon.homelab.arr.api.radarr.model.MovieResource
import dev.appolon.homelab.arr.api.radarr.model.QualityProfileResource
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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import okhttp3.OkHttpClient
import retrofit2.Response
import java.util.concurrent.atomic.AtomicLong

/**
 * English fallback labels for queued actions; the sync status screen shows
 * these verbatim. Context-free by design so the repository can build them
 * without resource access.
 */
object RadarrDetailActionTexts {
    fun updateMovie(title: String) = "Update movie \"$title\""
    fun deleteMovie(title: String) = "Delete movie \"$title\""
    fun searchMovie(title: String) = "Search for movie \"$title\""
    fun renameMovie(title: String) = "Rename files for \"$title\""
    fun importFolder(folder: String) = "Import folder \"$folder\""
}

/** One write's outcome: the server's (or optimistic) value when it landed or
 * was queued, otherwise the error that blocked it. */
data class WriteOutcome<out T>(
    val value: T? = null,
    val queued: Boolean = false,
    val error: ArrError? = null,
)

/**
 * Credits carry no movie id on the wire, so each movie's credits cache as one
 * list entity keyed by the movie.
 */
@Serializable
internal data class CachedCredits(val items: List<CreditResource> = emptyList())

/**
 * Cache-first repository for one movie's detail data. Reads come from the
 * [EntityStore] and refresh over the network once per type TTL; writes go
 * through to the server and, when the network fails with [ArrError.Offline]
 * or [ArrError.Network], land in the [ActionQueue] for replay with an
 * optimistic local update applied first.
 */
class RadarrMovieDetailRepository(
    private val connection: RadarrDetailConnection,
    private val store: EntityStore,
    private val queue: ActionQueue,
    private val nowMillis: () -> Long = System::currentTimeMillis,
    private val ttlMillis: Long = DEFAULT_TTL_MILLIS,
) {

    companion object {
        val MOVIE = EntityType("radarr.movie")
        val MOVIE_FILE = EntityType("radarr.moviefile")
        val CREDIT = EntityType("radarr.credit")
        val EXTRA_FILE = EntityType("radarr.extrafile")
        val HISTORY = EntityType("radarr.history")
        val TAG = EntityType("radarr.tag")
        val QUALITY_PROFILE = EntityType("radarr.qualityprofile")

        const val DEFAULT_TTL_MILLIS = 10L * 60L * 1000L
        private const val MOVIE_PATH_PREFIX = "api/v3/movie/"
        private val actionSequence = AtomicLong(0)
    }

    /** Live cached movie; emits on every store write, null until cached. */
    fun observeMovie(serverId: ServerId, movieId: Int): Flow<MovieResource?> =
        store.observe(serverId, MOVIE, MovieResource.serializer())
            .map { movies -> movies.firstOrNull { it.id == movieId } }

    /** The server's base URL for resolving image paths, or null when unknown. */
    suspend fun baseUrl(serverId: ServerId): String? = connection.baseUrlOf(serverId)

    /** Auth-scoped OkHttp client for the Coil image loader, or null offline. */
    suspend fun imageLoaderClient(serverId: ServerId): OkHttpClient? =
        connection.imageLoaderClient(serverId)

    suspend fun movie(
        serverId: ServerId,
        movieId: Int,
        force: Boolean = false,
    ): ArrResult<MovieResource> {
        val cached = store.get(serverId, MOVIE, movieId.toString(), MovieResource.serializer())
        if (cached != null && !force && fresh(serverId, MOVIE)) return ArrResult.ok(cached)
        val apis = connection.apis(serverId)
            ?: return cached?.let { ArrResult.ok(it) } ?: ArrResult.err(ArrError.Offline())
        val response = runCatching { apis.movie.get(movieId) }
            .getOrElse { t ->
                return cached?.let { ArrResult.ok(it) } ?: ArrResult.err(t.toArrError())
            }
        return when {
            response.isSuccessful -> {
                val value = response.body() ?: cached ?: MovieResource(id = movieId)
                store.put(serverId, MOVIE, value.id.toString(), value, MovieResource.serializer())
                ArrResult.ok(value)
            }
            // 404 means the movie is gone: drop the stale cache instead of
            // showing a ghost page until the TTL lapses.
            response.code() == 404 -> {
                evictMovie(serverId, movieId)
                ArrResult.err(response.toArrError())
            }
            cached != null -> ArrResult.ok(cached)
            else -> ArrResult.err(response.toArrError())
        }
    }

    suspend fun files(
        serverId: ServerId,
        movieId: Int,
        force: Boolean = false,
    ): ArrResult<List<MovieFileResource>> = cachedList(
        serverId = serverId,
        type = MOVIE_FILE,
        serializer = MovieFileResource.serializer(),
        filter = { it.movieId == movieId },
        idOf = { it.id.toString() },
        force = force,
        fetch = { it.movieFile.list(movieId = listOf(movieId)) },
    )

    /** Credits cache as one list entity per movie because they carry no movie id. */
    suspend fun credits(
        serverId: ServerId,
        movieId: Int,
        force: Boolean = false,
    ): ArrResult<List<CreditResource>> {
        val key = movieId.toString()
        val cached = store.get(serverId, CREDIT, key, CachedCredits.serializer())?.items
        if (!cached.isNullOrEmpty() && !force && fresh(serverId, CREDIT)) {
            return ArrResult.ok(cached)
        }
        val apis = connection.apis(serverId)
            ?: return cached?.let { ArrResult.ok(it) } ?: ArrResult.err(ArrError.Offline())
        val fetched = runCatching { apis.credit.list(movieId = movieId) }
            .getOrElse { t ->
                return cached?.let { ArrResult.ok(it) } ?: ArrResult.err(t.toArrError())
            }
        store.put(serverId, CREDIT, key, CachedCredits(fetched), CachedCredits.serializer())
        return ArrResult.ok(fetched)
    }

    suspend fun extraFiles(
        serverId: ServerId,
        movieId: Int,
        force: Boolean = false,
    ): ArrResult<List<ExtraFileResource>> = cachedList(
        serverId = serverId,
        type = EXTRA_FILE,
        serializer = ExtraFileResource.serializer(),
        filter = { it.movieId == movieId },
        idOf = { it.id.toString() },
        force = force,
        fetch = { it.extraFile.list(movieId = movieId) },
    )

    suspend fun history(
        serverId: ServerId,
        movieId: Int,
        force: Boolean = false,
    ): ArrResult<List<HistoryResource>> = cachedList(
        serverId = serverId,
        type = HISTORY,
        serializer = HistoryResource.serializer(),
        filter = { it.movieId == movieId },
        idOf = { it.id.toString() },
        force = force,
        fetch = { it.history.movieHistory(movieId = movieId, includeMovie = false) },
    )

    /** Quality profiles and tags backing the edit sheet pickers. */
    suspend fun editOptions(serverId: ServerId, force: Boolean = false): EditOptionsUi {
        val profiles = cachedList(
            serverId = serverId,
            type = QUALITY_PROFILE,
            serializer = QualityProfileResource.serializer(),
            filter = { true },
            idOf = { it.id.toString() },
            force = force,
            fetch = { it.qualityProfile.qualityProfiles() },
        )
        val tags = cachedList(
            serverId = serverId,
            type = TAG,
            serializer = TagResource.serializer(),
            filter = { true },
            idOf = { it.id.toString() },
            force = force,
            fetch = { it.tag.tags() },
        )
        return EditOptionsUi(
            qualityProfiles = profiles.valueOrDefault(emptyList())
                .map { NamedIdUi(it.id, it.name.orEmpty().ifBlank { "#${it.id}" }) }
                .sortedBy { it.name.lowercase() },
            tags = tags.valueOrDefault(emptyList())
                .map { NamedIdUi(it.id, it.label.orEmpty().ifBlank { "#${it.id}" }) }
                .sortedBy { it.name.lowercase() },
        )
    }

    /**
     * Pushes edit-sheet values. Online: one PUT plus a cache write. Offline
     * ([ArrError.Offline] or [ArrError.Network]): the patch is applied to the
     * cache optimistically and the PUT descriptor is queued for replay.
     */
    suspend fun updateMovie(
        serverId: ServerId,
        current: MovieResource,
        edits: EditableMovieUi,
        moveFiles: Boolean,
    ): WriteOutcome<MovieResource> {
        val patched = RadarrDetailLogic.applyEdits(current, edits)
        val apis = connection.apis(serverId)
        if (apis == null) {
            return queueOfflineMovie(serverId, patched)
        }
        val outcome = runCatching { apis.movie.update(patched.id.toString(), moveFiles, patched) }
        val errorOrResponse = outcome.fold(
            onSuccess = { response ->
                if (response.isSuccessful) {
                    val value = response.body() ?: patched
                    store.put(serverId, MOVIE, value.id.toString(), value, MovieResource.serializer())
                    null
                } else {
                    response.toArrError()
                }
            },
            onFailure = { it.toArrError() },
        )
        if (errorOrResponse == null) {
            val stored = store.get(serverId, MOVIE, patched.id.toString(), MovieResource.serializer())
            return WriteOutcome(value = stored ?: patched)
        }
        return if (errorOrResponse.isConnectionLoss()) {
            queueOfflineMovie(serverId, patched)
        } else {
            WriteOutcome(error = errorOrResponse)
        }
    }

    /**
     * Deletes the movie. Online: the cache rows for the movie and its files
     * drop immediately. Offline: the delete is queued; the movie stays
     * visible with its queued badge until the queue replays.
     */
    suspend fun deleteMovie(
        serverId: ServerId,
        movie: MovieResource,
        deleteFiles: Boolean,
        addImportExclusion: Boolean,
    ): WriteOutcome<Unit> {
        val title = movie.title.orEmpty().ifBlank { "#${movie.id}" }
        val path = MOVIE_PATH_PREFIX + movie.id +
            "?deleteFiles=$deleteFiles&addImportExclusion=$addImportExclusion"
        val apis = connection.apis(serverId) ?: return enqueueAction(
            serverId, "DELETE", path, null, RadarrDetailActionTexts.deleteMovie(title),
        )
        val error = runCatching { apis.movie.delete(movie.id, deleteFiles, addImportExclusion) }
            .fold(onSuccess = { null }, onFailure = { it.toArrError() })
        if (error == null) {
            evictMovie(serverId, movie.id)
            return WriteOutcome(value = Unit)
        }
        return if (error.isConnectionLoss()) {
            enqueueAction(serverId, "DELETE", path, null, RadarrDetailActionTexts.deleteMovie(title))
        } else {
            WriteOutcome(error = error)
        }
    }

    /** Starts `MoviesSearch`: the server searches indexers and grabs the best release. */
    suspend fun searchMovie(serverId: ServerId, movie: MovieResource): WriteOutcome<CommandReceipt> =
        postOrQueue(
            serverId,
            RadarrDetailLogic.searchCommandBody(movie.id),
            RadarrDetailActionTexts.searchMovie(displayTitle(movie)),
        )

    /** Starts `RenameFiles`: renames the movie's files to the current naming spec. */
    suspend fun renameFiles(serverId: ServerId, movie: MovieResource): WriteOutcome<CommandReceipt> =
        postOrQueue(
            serverId,
            RadarrDetailLogic.renameCommandBody(movie.id),
            RadarrDetailActionTexts.renameMovie(displayTitle(movie)),
        )

    /** Starts `DownloadedMoviesScan` over [folder] for interactive import. */
    suspend fun importFolder(serverId: ServerId, folder: String): WriteOutcome<CommandReceipt> =
        postOrQueue(
            serverId,
            RadarrDetailLogic.importScanCommandBody(folder),
            RadarrDetailActionTexts.importFolder(folder),
        )

    /** Interactive import candidates inside the movie's folder. Never cached. */
    suspend fun manualImportItems(
        serverId: ServerId,
        folder: String,
        movieId: Int,
    ): ArrResult<List<ManualImportResource>> {
        val apis = connection.apis(serverId) ?: return ArrResult.err(ArrError.Offline())
        return runCatching {
            apis.manualImport.manualImport(
                folder = folder,
                movieId = movieId,
                filterExistingFiles = true,
            )
        }.fold(onSuccess = { ArrResult.ok(it) }, onFailure = { ArrResult.err(it.toArrError()) })
    }

    /** Validates (re-parses) interactive import selections; surfaces rejections. */
    suspend fun reprocess(
        serverId: ServerId,
        items: List<ManualImportReprocessResource>,
    ): ArrResult<List<ManualImportReprocessResource>> {
        val apis = connection.apis(serverId) ?: return ArrResult.err(ArrError.Offline())
        return runCatching { apis.manualImport.reprocessManualImport(items) }.fold(
            onSuccess = { response ->
                if (response.isSuccessful) {
                    ArrResult.ok(response.body().orEmpty())
                } else {
                    ArrResult.err(response.toArrError())
                }
            },
            onFailure = { ArrResult.err(it.toArrError()) },
        )
    }

    private suspend fun postOrQueue(
        serverId: ServerId,
        body: JsonObject,
        description: String,
    ): WriteOutcome<CommandReceipt> = when (val result = connection.postCommand(serverId, body)) {
        is ArrResult.Ok -> WriteOutcome(value = result.value)
        is ArrResult.Err -> if (result.error.isConnectionLoss()) {
            enqueueAction(serverId, "POST", "api/v3/command", body.toString(), description)
        } else {
            WriteOutcome(error = result.error)
        }
    }

    private suspend fun queueOfflineMovie(
        serverId: ServerId,
        optimistic: MovieResource,
    ): WriteOutcome<MovieResource> {
        store.put(serverId, MOVIE, optimistic.id.toString(), optimistic, MovieResource.serializer())
        val path = MOVIE_PATH_PREFIX + optimistic.id
        enqueueAction(
            serverId,
            "PUT",
            path,
            ArrJson.encodeToString(MovieResource.serializer(), optimistic),
            RadarrDetailActionTexts.updateMovie(displayTitle(optimistic)),
        )
        return WriteOutcome(value = optimistic, queued = true)
    }

    private suspend fun enqueueAction(
        serverId: ServerId,
        method: String,
        path: String,
        bodyJson: String?,
        description: String,
    ): WriteOutcome<Nothing> {
        queue.enqueue(
            QueuedAction(
                actionId = ActionId("$method:$path:${nowMillis()}:${actionSequence.incrementAndGet()}"),
                serverId = serverId,
                appType = AppType.RADARR,
                method = method,
                path = path,
                bodyJson = bodyJson,
                description = description,
                enqueuedAtMillis = nowMillis(),
            ),
        )
        return WriteOutcome(queued = true)
    }

    private fun displayTitle(movie: MovieResource): String =
        movie.title.orEmpty().ifBlank { "#${movie.id}" }

    private suspend fun evictMovie(serverId: ServerId, movieId: Int) {
        store.delete(serverId, MOVIE, movieId.toString())
        store.getAll(serverId, MOVIE_FILE, MovieFileResource.serializer())
            .filter { it.movieId == movieId }
            .forEach { store.delete(serverId, MOVIE_FILE, it.id.toString()) }
        store.getAll(serverId, EXTRA_FILE, ExtraFileResource.serializer())
            .filter { it.movieId == movieId }
            .forEach { store.delete(serverId, EXTRA_FILE, it.id.toString()) }
        store.delete(serverId, CREDIT, movieId.toString())
    }

    private suspend fun fresh(serverId: ServerId, type: EntityType): Boolean =
        RadarrDetailLogic.isFresh(
            fetchedAtMillis = store.lastFetchedAt(serverId, type),
            nowMillis = nowMillis(),
            ttlMillis = ttlMillis,
        )

    /**
     * Shared cache-first read: cached rows inside the TTL short-circuit the
     * network; a fetch stores this movie's fresh rows (pruning cached ids
     * that disappeared server-side) and falls back to stale cache on
     * connection loss so the screen keeps rendering.
     */
    private suspend fun <T : Any> cachedList(
        serverId: ServerId,
        type: EntityType,
        serializer: KSerializer<T>,
        filter: (T) -> Boolean,
        idOf: (T) -> String,
        force: Boolean,
        fetch: suspend (RadarrDetailApis) -> List<T>,
    ): ArrResult<List<T>> {
        val cached = store.getAll(serverId, type, serializer).filter(filter)
        if (cached.isNotEmpty() && !force && fresh(serverId, type)) {
            return ArrResult.ok(cached)
        }
        val apis = connection.apis(serverId)
        if (apis == null) {
            return if (cached.isNotEmpty()) {
                ArrResult.ok(cached)
            } else {
                ArrResult.err(ArrError.Offline())
            }
        }
        val fetched = runCatching { fetch(apis) }.getOrElse { t ->
            return if (cached.isNotEmpty()) {
                ArrResult.ok(cached)
            } else {
                ArrResult.err(t.toArrError())
            }
        }
        store.putAll(
            serverId,
            type,
            fetched.map { idOf(it) to it },
            serializer,
            fetchedAtMillis = nowMillis(),
        )
        val freshIds = fetched.map(idOf).toSet()
        cached.map(idOf).filterNot { it in freshIds }.forEach { staleId ->
            store.delete(serverId, type, staleId)
        }
        return ArrResult.ok(fetched.filter(filter))
    }
}

/** True for the two error classes the brief routes into the offline queue. */
private fun ArrError.isConnectionLoss(): Boolean =
    this is ArrError.Offline || this is ArrError.Network

private fun <T> Response<T>.toArrError(): ArrError {
    val code = code()
    return if (code == 401 || code == 403) ArrError.Auth(code) else ArrError.Http(code, null)
}
