package dev.appolon.homelab.arr.feature.bazarr.wanted.data

import dev.appolon.homelab.arr.api.bazarr.BadgesApi
import dev.appolon.homelab.arr.api.bazarr.EpisodesSubtitlesApi
import dev.appolon.homelab.arr.api.bazarr.EpisodesWantedApi
import dev.appolon.homelab.arr.api.bazarr.MoviesSubtitlesApi
import dev.appolon.homelab.arr.api.bazarr.MoviesWantedApi
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
import dev.appolon.homelab.arr.feature.bazarr.episodes.data.idParamOf
import dev.appolon.homelab.arr.feature.bazarr.episodes.data.toWire
import dev.appolon.homelab.arr.feature.bazarr.episodes.model.BazarrLanguageOption
import dev.appolon.homelab.arr.feature.bazarr.episodes.model.BazarrWantedEpisode
import dev.appolon.homelab.arr.feature.bazarr.episodes.model.BazarrWantedMovie
import dev.appolon.homelab.arr.feature.bazarr.episodes.model.optIntOf
import dev.appolon.homelab.arr.feature.bazarr.episodes.model.rowsOf
import dev.appolon.homelab.arr.feature.bazarr.episodes.model.toLanguageOptions
import dev.appolon.homelab.arr.feature.bazarr.episodes.model.toWantedEpisode
import dev.appolon.homelab.arr.feature.bazarr.episodes.model.toWantedMovie
import dev.appolon.homelab.arr.feature.bazarr.wanted.model.BazarrWantedBadges
import dev.appolon.homelab.arr.net.arrError
import java.util.UUID
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.serializer
import retrofit2.HttpException
import retrofit2.Response

/** Outcome of a search-and-download write: applied, or queued for replay. */
sealed interface WantedWriteOutcome {
    data object Synced : WantedWriteOutcome
    data class Queued(val actionId: ActionId) : WantedWriteOutcome
}

/**
 * Repository for the Bazarr wanted subtitles screens. Reads are cache-first
 * through the [EntityStore] with a fetchedAt TTL; refreshes fetch a single
 * deep page because Bazarr pages its wanted lists with a length filter.
 * Search-and-download writes hit the server first and, when the failure is
 * retryable (offline or network), enqueue a replayable [QueuedAction] while
 * the row shows the queued indicator. The badges endpoint feeds the total
 * missing counts, and the wanted episode cache itself feeds the per-series
 * missing counts the series list group surfaces. No function throws.
 */
class BazarrWantedRepository(
    private val serverId: ServerId,
    private val store: EntityStore,
    private val episodesWantedApi: EpisodesWantedApi,
    private val moviesWantedApi: MoviesWantedApi,
    private val episodesSubtitlesApi: EpisodesSubtitlesApi,
    private val moviesSubtitlesApi: MoviesSubtitlesApi,
    private val badgesApi: BadgesApi,
    private val languagesApi: SystemLanguagesApi,
    private val actionQueue: ActionQueue,
    private val now: () -> Long = System::currentTimeMillis,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {

    // ---------------------------------------------------------------- reads

    /** Live wanted episode rows, ordered by season then episode number. */
    fun observeWantedEpisodes(): Flow<List<BazarrWantedEpisode>> =
        store.observe(serverId, WANTED_EPISODE_TYPE, BazarrWantedEpisode.serializer())
            .map { rows -> rows.sortedWith(compareBy({ it.seasonNumber }, { it.episodeNumber })) }

    /** Replaces the wanted episode cache with one deep page from the server. */
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

    /** Replaces the wanted movie cache with one deep page from the server. */
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

    /**
     * Missing-subtitle episode count per series id, derived from the wanted
     * cache. The series list group reads this flow to badge each series row.
     */
    fun observeSeriesMissingCounts(): Flow<Map<Int, Int>> =
        observeWantedEpisodes().map { rows ->
            rows.groupingBy { it.seriesId }.eachCount()
        }

    /** Live total missing counts from the badges endpoint; null until fetched. */
    fun observeBadges(): Flow<BazarrWantedBadges?> =
        store.observe(serverId, BADGE_TYPE, BazarrWantedBadges.serializer())
            .map { rows -> rows.firstOrNull() }

    /** Fetches the badges totals under a short TTL; keeps the cache on failure. */
    suspend fun refreshBadges(): ArrResult<Unit> = withContext(ioDispatcher) {
        val fetched = try {
            badgesApi.getBadges()
        } catch (e: HttpException) {
            return@withContext ArrResult.err(e.toArrError())
        } catch (e: Throwable) {
            return@withContext ArrResult.err(e.arrError())
        }
        val badges = BazarrWantedBadges(
            episodes = fetched.optIntOf("episodes") ?: 0,
            movies = fetched.optIntOf("movies") ?: 0,
        )
        store.deleteAll(serverId, BADGE_TYPE)
        store.putAll(
            serverId,
            BADGE_TYPE,
            listOf(BADGE_ROW_ID to badges),
            BazarrWantedBadges.serializer(),
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

    // --------------------------------------------------------------- writes

    /**
     * Asks Bazarr to download the best matching subtitle for one language
     * (PATCH episodes subtitles). The server picks the file, so the queued
     * path only surfaces the queued indicator; a synced download leaves the
     * row in place until the next refresh moves it out of the wanted list.
     */
    suspend fun downloadEpisodeSubtitle(
        seriesId: Int,
        episodeId: Int,
        languageCode2: String,
        forced: Boolean,
        hearingImpaired: Boolean,
    ): ArrResult<WantedWriteOutcome> = withContext(ioDispatcher) {
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
    ): ArrResult<WantedWriteOutcome> = withContext(ioDispatcher) {
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

    // ------------------------------------------------------------ internals

    private suspend fun Response<Unit>.toOutcome(): ArrResult<WantedWriteOutcome> =
        if (isSuccessful) {
            ArrResult.ok(WantedWriteOutcome.Synced)
        } else {
            ArrResult.err(HttpException(this).toArrError())
        }

    private suspend fun enqueueOrErr(
        error: ArrError,
        method: String,
        path: String,
    ): ArrResult<WantedWriteOutcome> {
        if (!error.isRetryable()) return ArrResult.err(error)
        val action = QueuedAction(
            actionId = ActionId(UUID.randomUUID().toString()),
            serverId = serverId,
            appType = AppType.BAZARR,
            method = method,
            path = path,
            description = "Search and download missing subtitle",
            enqueuedAtMillis = now(),
        )
        return try {
            actionQueue.enqueue(action)
            ArrResult.ok(WantedWriteOutcome.Queued(action.actionId))
        } catch (e: Throwable) {
            ArrResult.err(error)
        }
    }

    private fun ArrError.isRetryable(): Boolean =
        this is ArrError.Offline || this is ArrError.Network

    private fun HttpException.toArrError(): ArrError = when (code()) {
        401, 403 -> ArrError.Auth(code())
        else -> ArrError.Http(code(), message())
    }

    public companion object {
        /** Stable entity types; the series list group reads the episode one. */
        public val WANTED_EPISODE_TYPE: EntityType = EntityType("bazarr.wanted.episode")
        public val WANTED_MOVIE_TYPE: EntityType = EntityType("bazarr.wanted.movie")

        internal val BADGE_TYPE = EntityType("bazarr.wanted.badge")
        internal val LANGUAGE_TYPE = EntityType("bazarr.wanted.language")
        internal const val BADGE_ROW_ID = "badges"

        /** Five minutes: the wanted list changes with every download. */
        public const val LIST_TTL_MILLIS: Long = 5 * 60 * 1000

        /** One day: the languages list changes only with settings edits. */
        public const val LANGUAGE_TTL_MILLIS: Long = 24 * 60 * 60 * 1000

        /** Bazarr pages its list endpoints; one deep page covers real libraries. */
        public const val PAGE_LENGTH: Int = 10_000
    }
}
