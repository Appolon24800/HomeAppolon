package dev.appolon.homelab.arr.feature.bazarr.episodes.data

import dev.appolon.homelab.arr.api.bazarr.EpisodesApi
import dev.appolon.homelab.arr.api.bazarr.EpisodesHistoryApi
import dev.appolon.homelab.arr.api.bazarr.EpisodesSubtitlesApi
import dev.appolon.homelab.arr.api.bazarr.EpisodesWantedApi
import dev.appolon.homelab.arr.api.bazarr.MoviesApi
import dev.appolon.homelab.arr.api.bazarr.MoviesHistoryApi
import dev.appolon.homelab.arr.api.bazarr.MoviesSubtitlesApi
import dev.appolon.homelab.arr.api.bazarr.MoviesWantedApi
import dev.appolon.homelab.arr.api.bazarr.ProvidersEpisodesApi
import dev.appolon.homelab.arr.api.bazarr.ProvidersMoviesApi
import dev.appolon.homelab.arr.api.bazarr.SeriesApi
import dev.appolon.homelab.arr.api.bazarr.SubtitleContentsApi
import dev.appolon.homelab.arr.api.bazarr.SystemLanguagesApi
import dev.appolon.homelab.arr.core.ActionId
import dev.appolon.homelab.arr.core.AppType
import dev.appolon.homelab.arr.core.ArrError
import dev.appolon.homelab.arr.core.ArrResult
import dev.appolon.homelab.arr.core.EntityType
import dev.appolon.homelab.arr.core.ServerId
import dev.appolon.homelab.arr.data.ActionQueue
import dev.appolon.homelab.arr.data.EntityStore
import dev.appolon.homelab.arr.data.QueuedAction
import dev.appolon.homelab.arr.feature.bazarr.episodes.model.BazarrEpisodeSubtitles
import dev.appolon.homelab.arr.feature.bazarr.episodes.model.BazarrHistoryEvent
import dev.appolon.homelab.arr.feature.bazarr.episodes.model.BazarrLanguageOption
import dev.appolon.homelab.arr.feature.bazarr.episodes.model.BazarrMovieSubtitles
import dev.appolon.homelab.arr.feature.bazarr.episodes.model.BazarrProviderResult
import dev.appolon.homelab.arr.feature.bazarr.episodes.model.BazarrWantedEpisode
import dev.appolon.homelab.arr.feature.bazarr.episodes.model.BazarrWantedMovie
import dev.appolon.homelab.arr.feature.bazarr.episodes.model.rowsOf
import dev.appolon.homelab.arr.feature.bazarr.episodes.model.toEpisodeSubtitles
import dev.appolon.homelab.arr.feature.bazarr.episodes.model.toHistoryEvent
import dev.appolon.homelab.arr.feature.bazarr.episodes.model.toLanguageOptions
import dev.appolon.homelab.arr.feature.bazarr.episodes.model.toMovieSubtitles
import dev.appolon.homelab.arr.feature.bazarr.episodes.model.toProviderResult
import dev.appolon.homelab.arr.feature.bazarr.episodes.model.toSubtitleContents
import dev.appolon.homelab.arr.feature.bazarr.episodes.model.toWantedEpisode
import dev.appolon.homelab.arr.feature.bazarr.episodes.model.toWantedMovie
import dev.appolon.homelab.arr.net.arrError
import java.util.UUID
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.HttpException
import retrofit2.Response

/** Outcome of a write: applied on the server, or queued for replay. */
sealed interface WriteOutcome {
    data object Synced : WriteOutcome
    data class Queued(val actionId: ActionId) : WriteOutcome
}

/**
 * Repository for the Bazarr episodes and movie subtitle screens. Reads are
 * cache-first through the [EntityStore] with a fetchedAt TTL; refreshes fetch
 * over Retrofit and reconcile rows the server dropped. Writes hit the server
 * first and, when the failure is retryable (offline or network), enqueue a
 * replayable [QueuedAction] through the [ActionQueue] while applying the
 * change optimistically to the cache where the local effect is knowable
 * (deletes). Subtitle downloads change server state we cannot predict, so
 * they queue without a fake row and surface the queued indicator instead.
 * No function throws.
 */
class BazarrSubtitlesRepository(
    private val serverId: ServerId,
    private val store: EntityStore,
    private val seriesApi: SeriesApi,
    private val episodesApi: EpisodesApi,
    private val episodesSubtitlesApi: EpisodesSubtitlesApi,
    private val episodesWantedApi: EpisodesWantedApi,
    private val episodesHistoryApi: EpisodesHistoryApi,
    private val moviesApi: MoviesApi,
    private val moviesSubtitlesApi: MoviesSubtitlesApi,
    private val moviesWantedApi: MoviesWantedApi,
    private val moviesHistoryApi: MoviesHistoryApi,
    private val providersEpisodesApi: ProvidersEpisodesApi,
    private val providersMoviesApi: ProvidersMoviesApi,
    private val subtitleContentsApi: SubtitleContentsApi,
    private val languagesApi: SystemLanguagesApi,
    private val actionQueue: ActionQueue,
    private val now: () -> Long = System::currentTimeMillis,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {

    // ---------------------------------------------------------------- reads

    /** Live episode rows of one series; emits again after every write-through. */
    fun observeEpisodes(seriesId: Int): Flow<List<BazarrEpisodeSubtitles>> =
        store.observe(serverId, EPISODE_TYPE, BazarrEpisodeSubtitles.serializer())
            .map { rows -> rows.filter { it.seriesId == seriesId } }

    /** Fetches every episode of the series and reconciles dropped rows. */
    suspend fun refreshEpisodes(seriesId: Int): ArrResult<Unit> = withContext(ioDispatcher) {
        val fetched = try {
            episodesApi.getEpisodes(seriesIds = listOf(seriesId))
        } catch (e: HttpException) {
            return@withContext ArrResult.err(e.toArrError())
        } catch (e: Throwable) {
            return@withContext ArrResult.err(e.arrError())
        }
        val rows = fetched.rowsOf().mapNotNull { it.toEpisodeSubtitles() }
        reconcile(EPISODE_TYPE, rows, cachedEpisodesOf(seriesId)) { it.episodeId.toString() }
        store.putAll(
            serverId,
            EPISODE_TYPE,
            rows.map { it.episodeId.toString() to it },
            BazarrEpisodeSubtitles.serializer(),
            fetchedAtMillis = now(),
        )
        ArrResult.ok(Unit)
    }

    /** True when the episode cache is absent or older than [ttlMillis]. */
    suspend fun episodesStale(seriesId: Int, ttlMillis: Long = LIST_TTL_MILLIS): Boolean {
        val fetched = store.lastFetchedAt(serverId, EPISODE_TYPE) ?: return true
        return now() - fetched > ttlMillis
    }

    /** Live row of one movie; emits again after every write-through. */
    fun observeMovie(radarrId: Int): Flow<BazarrMovieSubtitles?> =
        store.observe(serverId, MOVIE_TYPE, BazarrMovieSubtitles.serializer())
            .map { rows -> rows.firstOrNull { it.radarrId == radarrId } }

    /** Fetches one movie row and reconciles it in the movie cache. */
    suspend fun refreshMovie(radarrId: Int): ArrResult<Unit> = withContext(ioDispatcher) {
        val fetched = try {
            moviesApi.getMovies(radarrIds = listOf(radarrId))
        } catch (e: HttpException) {
            return@withContext ArrResult.err(e.toArrError())
        } catch (e: Throwable) {
            return@withContext ArrResult.err(e.arrError())
        }
        val rows = fetched.rowsOf().mapNotNull { it.toMovieSubtitles() }
        reconcile(MOVIE_TYPE, rows, cachedMoviesOf(radarrId)) { it.radarrId.toString() }
        store.putAll(
            serverId,
            MOVIE_TYPE,
            rows.map { it.radarrId.toString() to it },
            BazarrMovieSubtitles.serializer(),
            fetchedAtMillis = now(),
        )
        ArrResult.ok(Unit)
    }

    suspend fun movieStale(radarrId: Int, ttlMillis: Long = LIST_TTL_MILLIS): Boolean {
        val fetched = store.lastFetchedAt(serverId, MOVIE_TYPE) ?: return true
        return now() - fetched > ttlMillis
    }

    /** Series title for the screen header; cached under this group's own type. */
    suspend fun seriesTitle(seriesId: Int, ttlMillis: Long = LIST_TTL_MILLIS): String =
        withContext(ioDispatcher) {
            val cached = store.get(serverId, SERIES_TYPE, seriesId.toString(), String.serializer())
            val fresh = store.lastFetchedAt(serverId, SERIES_TYPE)
                ?.let { now() - it <= ttlMillis } == true
            if (cached != null && fresh) return@withContext cached
            val fetched = try {
                seriesApi.getSeries(seriesIds = listOf(seriesId))
            } catch (e: Throwable) {
                return@withContext cached.orEmpty()
            }
            val title = fetched.rowsOf().firstOrNull()
                ?.let { row ->
                    val raw = row["title"]
                    (raw as? kotlinx.serialization.json.JsonPrimitive)?.content
                }.orEmpty()
            if (title.isNotEmpty()) {
                store.putAll(
                    serverId,
                    SERIES_TYPE,
                    listOf(seriesId.toString() to title),
                    String.serializer(),
                    fetchedAtMillis = now(),
                )
                title
            } else {
                cached.orEmpty()
            }
        }

    /** Live wanted episode rows, newest episodes first by season and number. */
    fun observeWantedEpisodes(): Flow<List<BazarrWantedEpisode>> =
        store.observe(serverId, WANTED_EPISODE_TYPE, BazarrWantedEpisode.serializer())
            .map { rows -> rows.sortedWith(compareBy({ it.seasonNumber }, { it.episodeNumber })) }

    suspend fun refreshWantedEpisodes(): ArrResult<Unit> = withContext(ioDispatcher) {
        val fetched = try {
            episodesWantedApi.getEpisodesWanted(start = 0, length = PAGE_LENGTH)
        } catch (e: HttpException) {
            return@withContext ArrResult.err(e.toArrError())
        } catch (e: Throwable) {
            return@withContext ArrResult.err(e.arrError())
        }
        val rows = fetched.rowsOf().mapNotNull { it.toWantedEpisode() }
        store.deleteAll(serverId, WANTED_EPISODE_TYPE)
        store.putAll(
            serverId,
            WANTED_EPISODE_TYPE,
            rows.map { it.episodeId.toString() to it },
            BazarrWantedEpisode.serializer(),
            fetchedAtMillis = now(),
        )
        ArrResult.ok(Unit)
    }

    /** Live wanted movie rows, alphabetical. */
    fun observeWantedMovies(): Flow<List<BazarrWantedMovie>> =
        store.observe(serverId, WANTED_MOVIE_TYPE, BazarrWantedMovie.serializer())
            .map { rows -> rows.sortedBy { it.title.lowercase() } }

    suspend fun refreshWantedMovies(): ArrResult<Unit> = withContext(ioDispatcher) {
        val fetched = try {
            moviesWantedApi.getMoviesWanted(start = 0, length = PAGE_LENGTH)
        } catch (e: HttpException) {
            return@withContext ArrResult.err(e.toArrError())
        } catch (e: Throwable) {
            return@withContext ArrResult.err(e.arrError())
        }
        val rows = fetched.rowsOf().mapNotNull { it.toWantedMovie() }
        store.deleteAll(serverId, WANTED_MOVIE_TYPE)
        store.putAll(
            serverId,
            WANTED_MOVIE_TYPE,
            rows.map { it.radarrId.toString() to it },
            BazarrWantedMovie.serializer(),
            fetchedAtMillis = now(),
        )
        ArrResult.ok(Unit)
    }

    /** Live merged history feed, newest first. */
    fun observeHistory(): Flow<List<BazarrHistoryEvent>> =
        store.observe(serverId, HISTORY_TYPE, BazarrHistoryEvent.serializer())
            .map { rows -> rows.sortedByDescending { it.timestampMillis } }

    /** Fetches both history feeds and reconciles the merged cache. */
    suspend fun refreshHistory(): ArrResult<Unit> = withContext(ioDispatcher) {
        val episodeRows = try {
            episodesHistoryApi.getEpisodesHistory(start = 0, length = PAGE_LENGTH)
                .rowsOf().mapNotNull { it.toHistoryEvent(isMovie = false) }
        } catch (e: HttpException) {
            return@withContext ArrResult.err(e.toArrError())
        } catch (e: Throwable) {
            return@withContext ArrResult.err(e.arrError())
        }
        val movieRows = try {
            moviesHistoryApi.getMoviesHistory(start = 0, length = PAGE_LENGTH)
                .rowsOf().mapNotNull { it.toHistoryEvent(isMovie = true) }
        } catch (e: HttpException) {
            return@withContext ArrResult.err(e.toArrError())
        } catch (e: Throwable) {
            return@withContext ArrResult.err(e.arrError())
        }
        val rows = episodeRows + movieRows
        val ids = rows.mapIndexed { index, event -> historyId(event, index) to event }
        // History rows carry no server id, so the refresh replaces the whole
        // feed atomically instead of reconciling key by key.
        store.deleteAll(serverId, HISTORY_TYPE)
        store.putAll(
            serverId,
            HISTORY_TYPE,
            ids,
            BazarrHistoryEvent.serializer(),
            fetchedAtMillis = now(),
        )
        ArrResult.ok(Unit)
    }

    /** Language filter chips from the Bazarr languages endpoint, cache-first. */
    suspend fun languages(ttlMillis: Long = LANGUAGE_TTL_MILLIS): ArrResult<List<BazarrLanguageOption>> =
        withContext(ioDispatcher) {
            val cached = store.getAll(serverId, LANGUAGE_TYPE, BazarrLanguageOption.serializer())
            val fresh = store.lastFetchedAt(serverId, LANGUAGE_TYPE)
                ?.let { now() - it <= ttlMillis } == true
            if (cached.isNotEmpty() && fresh) return@withContext ArrResult.ok(cached)
            val fetched = try {
                languagesApi.getLanguages(history = null)
            } catch (e: HttpException) {
                return@withContext ArrResult.err(e.toArrError())
            } catch (e: Throwable) {
                return@withContext ArrResult.err(e.arrError())
            }
            val options = fetched.toLanguageOptions()
            if (options.isNotEmpty()) {
                store.deleteAll(serverId, LANGUAGE_TYPE)
                store.putAll(
                    serverId,
                    LANGUAGE_TYPE,
                    options.map { it.code2 to it },
                    BazarrLanguageOption.serializer(),
                    fetchedAtMillis = now(),
                )
                ArrResult.ok(options)
            } else {
                ArrResult.ok(cached)
            }
        }

    /** Manual search hits across providers for one episode; transient, never cached. */
    suspend fun manualSearchEpisode(episodeId: Int): ArrResult<List<BazarrProviderResult>> =
        withContext(ioDispatcher) {
            try {
                ArrResult.ok(
                    providersEpisodesApi.getProviderEpisodes(episodeid = episodeId)
                        .rowsOf().map { it.toProviderResult() },
                )
            } catch (e: HttpException) {
                ArrResult.err(e.toArrError())
            } catch (e: Throwable) {
                ArrResult.err(e.arrError())
            }
        }

    /** Manual search hits across providers for one movie; transient, never cached. */
    suspend fun manualSearchMovie(radarrId: Int): ArrResult<List<BazarrProviderResult>> =
        withContext(ioDispatcher) {
            try {
                ArrResult.ok(
                    providersMoviesApi.getProviderMovies(radarrid = radarrId)
                        .rowsOf().map { it.toProviderResult() },
                )
            } catch (e: HttpException) {
                ArrResult.err(e.toArrError())
            } catch (e: Throwable) {
                ArrResult.err(e.arrError())
            }
        }

    /** Text of one subtitle file for the viewer; transient, never cached. */
    suspend fun subtitleContents(path: String): ArrResult<String> = withContext(ioDispatcher) {
        try {
            ArrResult.ok(subtitleContentsApi.getSubtitleNameContents(subtitlePath = path).toSubtitleContents())
        } catch (e: HttpException) {
            ArrResult.err(e.toArrError())
        } catch (e: Throwable) {
            ArrResult.err(e.arrError())
        }
    }

    // --------------------------------------------------------------- writes

    /**
     * Asks Bazarr to download the best matching subtitle (PATCH episodes or
     * movies subtitles). The server picks the file, so the only local effect
     * on the queued path is the queued indicator; a synced download refreshes
     * the row so the new track appears.
     */
    suspend fun downloadEpisodeSubtitle(
        seriesId: Int,
        episodeId: Int,
        languageCode2: String,
        forced: Boolean,
        hearingImpaired: Boolean,
    ): ArrResult<WriteOutcome> = withContext(ioDispatcher) {
        val path = "episodes/subtitles?seriesid=$seriesId&episodeid=$episodeId" +
            "&language=$languageCode2&forced=${forced.toWire()}&hi=${hearingImpaired.toWire()}"
        try {
            episodesSubtitlesApi.patchEpisodesSubtitles(
                seriesId = seriesId,
                episodeId = episodeId,
                language = languageCode2,
                forced = forced.toWire(),
                hi = hearingImpaired.toWire(),
            ).toOutcome()
        } catch (e: HttpException) {
            enqueueOrErr(e.toArrError(), "PATCH", path)
        } catch (e: Throwable) {
            enqueueOrErr(e.arrError(), "PATCH", path)
        }
    }

    /** Movie twin of [downloadEpisodeSubtitle]. */
    suspend fun downloadMovieSubtitle(
        radarrId: Int,
        languageCode2: String,
        forced: Boolean,
        hearingImpaired: Boolean,
    ): ArrResult<WriteOutcome> = withContext(ioDispatcher) {
        val path = "movies/subtitles?radarrid=$radarrId&language=$languageCode2" +
            "&forced=${forced.toWire()}&hi=${hearingImpaired.toWire()}"
        try {
            moviesSubtitlesApi.patchMoviesSubtitles(
                radarrId = radarrId,
                language = languageCode2,
                forced = forced.toWire(),
                hi = hearingImpaired.toWire(),
            ).toOutcome()
        } catch (e: HttpException) {
            enqueueOrErr(e.toArrError(), "PATCH", path)
        } catch (e: Throwable) {
            enqueueOrErr(e.arrError(), "PATCH", path)
        }
    }

    /**
     * Downloads one manual-search hit. All parameters ride the query string,
     * so an offline attempt queues a fully replayable action.
     */
    suspend fun downloadManualEpisode(
        seriesId: Int,
        episodeId: Int,
        result: BazarrProviderResult,
    ): ArrResult<WriteOutcome> = withContext(ioDispatcher) {
        val path = "api/providers/episodes?seriesid=$seriesId&episodeid=$episodeId" +
            "&hi=${result.hearingImpaired.toWire()}&forced=${result.forced.toWire()}" +
            "&original_format=${result.originalFormat.toWire()}" +
            "&provider=${result.provider.urlEncoded()}&subtitle=${result.subtitleId.urlEncoded()}"
        try {
            providersEpisodesApi.postProviderEpisodes(
                seriesid = seriesId,
                episodeid = episodeId,
                hi = result.hearingImpaired.toWire(),
                forced = result.forced.toWire(),
                originalFormat = result.originalFormat.toWire(),
                provider = result.provider,
                subtitle = result.subtitleId,
            ).toOutcome()
        } catch (e: HttpException) {
            enqueueOrErr(e.toArrError(), "POST", path)
        } catch (e: Throwable) {
            enqueueOrErr(e.arrError(), "POST", path)
        }
    }

    /** Movie twin of [downloadManualEpisode]. */
    suspend fun downloadManualMovie(
        radarrId: Int,
        result: BazarrProviderResult,
    ): ArrResult<WriteOutcome> = withContext(ioDispatcher) {
        val path = "api/providers/movies?radarrid=$radarrId" +
            "&hi=${result.hearingImpaired.toWire()}&forced=${result.forced.toWire()}" +
            "&original_format=${result.originalFormat.toWire()}" +
            "&provider=${result.provider.urlEncoded()}&subtitle=${result.subtitleId.urlEncoded()}"
        try {
            providersMoviesApi.postProviderMovies(
                radarrid = radarrId,
                hi = result.hearingImpaired.toWire(),
                forced = result.forced.toWire(),
                originalFormat = result.originalFormat.toWire(),
                provider = result.provider,
                subtitle = result.subtitleId,
            ).toOutcome()
        } catch (e: HttpException) {
            enqueueOrErr(e.toArrError(), "POST", path)
        } catch (e: Throwable) {
            enqueueOrErr(e.arrError(), "POST", path)
        }
    }

    /**
     * Deletes one subtitle file. Both the synced and queued paths remove the
     * track from the cached row so the UI updates immediately.
     */
    suspend fun deleteEpisodeSubtitle(
        seriesId: Int,
        episodeId: Int,
        languageCode2: String,
        forced: Boolean,
        hearingImpaired: Boolean,
        path: String,
    ): ArrResult<WriteOutcome> = withContext(ioDispatcher) {
        val actionPath = "episodes/subtitles?seriesid=$seriesId&episodeid=$episodeId" +
            "&language=$languageCode2&forced=${forced.toWire()}&hi=${hearingImpaired.toWire()}" +
            "&path=${path.urlEncoded()}"
        try {
            episodesSubtitlesApi.deleteEpisodesSubtitles(
                seriesId = seriesId,
                episodeId = episodeId,
                language = languageCode2,
                forced = forced.toWire(),
                hi = hearingImpaired.toWire(),
                path = path,
            ).toOutcome { removeEpisodeTrack(episodeId, languageCode2, forced, hearingImpaired, path) }
        } catch (e: HttpException) {
            enqueueOrErr(e.toArrError(), "DELETE", actionPath) {
                removeEpisodeTrack(episodeId, languageCode2, forced, hearingImpaired, path)
            }
        } catch (e: Throwable) {
            enqueueOrErr(e.arrError(), "DELETE", actionPath) {
                removeEpisodeTrack(episodeId, languageCode2, forced, hearingImpaired, path)
            }
        }
    }

    /** Movie twin of [deleteEpisodeSubtitle]. */
    suspend fun deleteMovieSubtitle(
        radarrId: Int,
        languageCode2: String,
        forced: Boolean,
        hearingImpaired: Boolean,
        path: String,
    ): ArrResult<WriteOutcome> = withContext(ioDispatcher) {
        val actionPath = "movies/subtitles?radarrid=$radarrId&language=$languageCode2" +
            "&forced=${forced.toWire()}&hi=${hearingImpaired.toWire()}&path=${path.urlEncoded()}"
        try {
            moviesSubtitlesApi.deleteMoviesSubtitles(
                radarrId = radarrId,
                language = languageCode2,
                forced = forced.toWire(),
                hi = hearingImpaired.toWire(),
                path = path,
            ).toOutcome { removeMovieTrack(radarrId, languageCode2, forced, hearingImpaired, path) }
        } catch (e: HttpException) {
            enqueueOrErr(e.toArrError(), "DELETE", actionPath) {
                removeMovieTrack(radarrId, languageCode2, forced, hearingImpaired, path)
            }
        } catch (e: Throwable) {
            enqueueOrErr(e.arrError(), "DELETE", actionPath) {
                removeMovieTrack(radarrId, languageCode2, forced, hearingImpaired, path)
            }
        }
    }

    /**
     * Uploads a subtitle file. Deliberately online-only: the payload is a
     * multipart binary the string-bodied [QueuedAction] cannot represent, so
     * a failed upload surfaces its error instead of queueing a request that
     * could never replay correctly.
     */
    suspend fun uploadEpisodeSubtitle(
        seriesId: Int,
        episodeId: Int,
        languageCode2: String,
        forced: Boolean,
        hearingImpaired: Boolean,
        fileName: String,
        bytes: ByteArray,
    ): ArrResult<WriteOutcome> = withContext(ioDispatcher) {
        try {
            episodesSubtitlesApi.postEpisodesSubtitles(
                seriesId = seriesId,
                episodeId = episodeId,
                language = languageCode2,
                forced = forced.toWire(),
                hi = hearingImpaired.toWire(),
                file = bytes.asSubtitlePart(fileName),
            ).toOutcome()
        } catch (e: HttpException) {
            ArrResult.err(e.toArrError())
        } catch (e: Throwable) {
            ArrResult.err(e.arrError())
        }
    }

    /** Movie twin of [uploadEpisodeSubtitle], online-only for the same reason. */
    suspend fun uploadMovieSubtitle(
        radarrId: Int,
        languageCode2: String,
        forced: Boolean,
        hearingImpaired: Boolean,
        fileName: String,
        bytes: ByteArray,
    ): ArrResult<WriteOutcome> = withContext(ioDispatcher) {
        try {
            moviesSubtitlesApi.postMoviesSubtitles(
                radarrId = radarrId,
                language = languageCode2,
                forced = forced.toWire(),
                hi = hearingImpaired.toWire(),
                file = bytes.asSubtitlePart(fileName),
            ).toOutcome()
        } catch (e: HttpException) {
            ArrResult.err(e.toArrError())
        } catch (e: Throwable) {
            ArrResult.err(e.arrError())
        }
    }

    // ------------------------------------------------------- queued signals

    /** Episode ids this server has queued subtitle actions for. */
    fun queuedEpisodeIds(): Flow<Set<Int>> = actionQueue.observe().map { pending ->
        pending.asSequence()
            .filter { it.serverId == serverId }
            .flatMap { idParamOf(it.path, "episodeid").asSequence() }
            .toSet()
    }

    /** Movie ids this server has queued subtitle actions for. */
    fun queuedMovieIds(): Flow<Set<Int>> = actionQueue.observe().map { pending ->
        pending.asSequence()
            .filter { it.serverId == serverId }
            .flatMap { idParamOf(it.path, "radarrid").asSequence() }
            .toSet()
    }

    /** Count of every queued action for this server. */
    fun queuedCount(): Flow<Int> = actionQueue.observe().map { pending ->
        pending.count { it.serverId == serverId }
    }

    // ------------------------------------------------------------ internals

    private suspend fun cachedEpisodesOf(seriesId: Int): List<BazarrEpisodeSubtitles> =
        store.getAll(serverId, EPISODE_TYPE, BazarrEpisodeSubtitles.serializer())
            .filter { it.seriesId == seriesId }

    private suspend fun cachedMoviesOf(radarrId: Int): List<BazarrMovieSubtitles> =
        store.getAll(serverId, MOVIE_TYPE, BazarrMovieSubtitles.serializer())
            .filter { it.radarrId == radarrId }

    private suspend fun removeEpisodeTrack(
        episodeId: Int,
        languageCode2: String,
        forced: Boolean,
        hearingImpaired: Boolean,
        path: String,
    ) {
        val row = store.get(serverId, EPISODE_TYPE, episodeId.toString(), BazarrEpisodeSubtitles.serializer())
            ?: return
        val remaining = row.subtitles.filterNot {
            it.languageCode2 == languageCode2 && it.forced == forced &&
                it.hearingImpaired == hearingImpaired && (path.isBlank() || it.path == path)
        }
        store.put(
            serverId,
            EPISODE_TYPE,
            episodeId.toString(),
            row.copy(subtitles = remaining),
            BazarrEpisodeSubtitles.serializer(),
        )
    }

    private suspend fun removeMovieTrack(
        radarrId: Int,
        languageCode2: String,
        forced: Boolean,
        hearingImpaired: Boolean,
        path: String,
    ) {
        val row = store.get(serverId, MOVIE_TYPE, radarrId.toString(), BazarrMovieSubtitles.serializer())
            ?: return
        val remaining = row.subtitles.filterNot {
            it.languageCode2 == languageCode2 && it.forced == forced &&
                it.hearingImpaired == hearingImpaired && (path.isBlank() || it.path == path)
        }
        store.put(
            serverId,
            MOVIE_TYPE,
            radarrId.toString(),
            row.copy(subtitles = remaining),
            BazarrMovieSubtitles.serializer(),
        )
    }

    /** Removes rows of one collection that disappeared server-side. */
    private suspend fun <T> reconcile(
        type: EntityType,
        incoming: List<T>,
        existing: List<T>,
        idOf: (T) -> String,
    ) {
        val incomingIds = incoming.map(idOf).toSet()
        existing.forEach { row ->
            val id = idOf(row)
            if (id !in incomingIds) store.delete(serverId, type, id)
        }
    }

    private fun historyId(event: BazarrHistoryEvent, index: Int): String =
        "${event.timestampMillis}_${event.episodeId ?: event.radarrId ?: 0}_$index"

    private suspend fun Response<Unit>.toOutcome(
        optimistic: (suspend () -> Unit)? = null,
    ): ArrResult<WriteOutcome> = if (isSuccessful) {
        optimistic?.invoke()
        ArrResult.ok(WriteOutcome.Synced)
    } else {
        ArrResult.err(HttpException(this).toArrError())
    }

    private suspend fun enqueueOrErr(
        error: ArrError,
        method: String,
        path: String,
        optimistic: (suspend () -> Unit)? = null,
    ): ArrResult<WriteOutcome> {
        if (!error.isRetryable()) return ArrResult.err(error)
        optimistic?.invoke()
        val action = QueuedAction(
            actionId = ActionId(UUID.randomUUID().toString()),
            serverId = serverId,
            appType = AppType.BAZARR,
            method = method,
            path = path,
            description = describe(method, path),
            enqueuedAtMillis = now(),
        )
        return try {
            actionQueue.enqueue(action)
            ArrResult.ok(WriteOutcome.Queued(action.actionId))
        } catch (e: Throwable) {
            ArrResult.err(error)
        }
    }

    private fun describe(method: String, path: String): String = when {
        path.startsWith("episodes/subtitles") && method == "DELETE" -> "Delete episode subtitle"
        path.startsWith("episodes/subtitles") -> "Download episode subtitle"
        path.startsWith("movies/subtitles") && method == "DELETE" -> "Delete movie subtitle"
        path.startsWith("movies/subtitles") -> "Download movie subtitle"
        path.startsWith("api/providers/episodes") -> "Manual episode subtitle download"
        path.startsWith("api/providers/movies") -> "Manual movie subtitle download"
        else -> "Bazarr subtitle action"
    }

    private fun ArrError.isRetryable(): Boolean =
        this is ArrError.Offline || this is ArrError.Network

    private fun HttpException.toArrError(): ArrError = when (code()) {
        401, 403 -> ArrError.Auth(code())
        else -> ArrError.Http(code(), message())
    }

    internal companion object {
        internal val EPISODE_TYPE = EntityType("bazarr.episodes.episode")
        internal val MOVIE_TYPE = EntityType("bazarr.episodes.movie")
        internal val SERIES_TYPE = EntityType("bazarr.episodes.series")
        internal val WANTED_EPISODE_TYPE = EntityType("bazarr.episodes.wanted.episode")
        internal val WANTED_MOVIE_TYPE = EntityType("bazarr.episodes.wanted.movie")
        internal val HISTORY_TYPE = EntityType("bazarr.episodes.history")
        internal val LANGUAGE_TYPE = EntityType("bazarr.episodes.language")

        /** Five minutes: subtitle state changes with every download. */
        const val LIST_TTL_MILLIS: Long = 5 * 60 * 1000

        /** One day: the languages list changes only with settings edits. */
        const val LANGUAGE_TTL_MILLIS: Long = 24 * 60 * 60 * 1000

        /** Bazarr pages its list endpoints; one deep page covers real libraries. */
        const val PAGE_LENGTH = 10_000
    }
}

/** "True"/"False" wire spelling the Bazarr query parameters require. */
internal fun Boolean.toWire(): String = if (this) "True" else "False"

/** Percent-encodes a query value so queued action paths replay identically. */
internal fun String.urlEncoded(): String = StringBuilder(length).apply {
    for (byte in this@urlEncoded.encodeToByteArray()) {
        val c = byte.toInt() and 0xFF
        val unreserved = c in '0'.code..'9'.code || c in 'A'.code..'Z'.code ||
            c in 'a'.code..'z'.code || c == '-'.code || c == '.'.code ||
            c == '_'.code || c == '~'.code
        if (unreserved) append(c.toChar()) else {
            append('%')
            append("0123456789ABCDEF"[c ushr 4])
            append("0123456789ABCDEF"[c and 0xF])
        }
    }
}.toString()

private fun ByteArray.asSubtitlePart(fileName: String): MultipartBody.Part =
    MultipartBody.Part.createFormData(
        "file",
        fileName,
        toRequestBody("application/octet-stream".toMediaTypeOrNull()),
    )

/** Extracts every integer value of one query parameter from a queued path. */
internal fun idParamOf(path: String, name: String): List<Int> {
    val ids = mutableListOf<Int>()
    var index = path.indexOf("$name=")
    while (index >= 0) {
        val start = index + name.length + 1
        var end = start
        while (end < path.length && path[end].isDigit()) end++
        if (end > start) path.substring(start, end).toIntOrNull()?.let { ids += it }
        index = path.indexOf("$name=", end)
    }
    return ids
}
