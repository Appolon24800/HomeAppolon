package dev.appolon.homelab.arr.feature.bazarr.series.data

import dev.appolon.homelab.arr.api.bazarr.BadgesApi
import dev.appolon.homelab.arr.api.bazarr.EpisodesApi
import dev.appolon.homelab.arr.api.bazarr.EpisodesBlacklistApi
import dev.appolon.homelab.arr.api.bazarr.MoviesApi
import dev.appolon.homelab.arr.api.bazarr.MoviesBlacklistApi
import dev.appolon.homelab.arr.api.bazarr.SeriesApi
import dev.appolon.homelab.arr.api.bazarr.SystemLanguagesProfilesApi
import dev.appolon.homelab.arr.core.ActionId
import dev.appolon.homelab.arr.core.AppType
import dev.appolon.homelab.arr.core.ArrError
import dev.appolon.homelab.arr.core.ArrResult
import dev.appolon.homelab.arr.core.EntityType
import dev.appolon.homelab.arr.core.ServerId
import dev.appolon.homelab.arr.data.ActionQueue
import dev.appolon.homelab.arr.data.EntityStore
import dev.appolon.homelab.arr.data.QueuedAction
import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import retrofit2.HttpException
import retrofit2.Response

/** Which Bazarr media list a screen or write targets. */
enum class BazarrMediaKind {
    SERIES,
    MOVIES,
}

/** How a write landed: applied on the server, or queued for replay. */
sealed interface WriteOutcome {
    data object Synced : WriteOutcome
    data class Queued(val actionId: ActionId) : WriteOutcome
}

/**
 * One series' cached episode list with its own fetch stamp. The entity store
 * stamps freshness per entity type, and episodes are cached per series, so
 * the row carries its own age to keep TTLs honest per series.
 */
@Serializable
internal data class CachedEpisodePage(
    val episodes: List<BazarrEpisode>,
    val fetchedAtMillis: Long,
)

/**
 * Bazarr series and movies library repository. Reads are cache-first through
 * the [EntityStore] (observe plus per-type fetchedAt TTLs driving refresh);
 * the one write this group owns, bulk language-profile assignment, goes to
 * the server first and falls back to optimistic cache application plus
 * [ActionQueue] enqueue when the network call fails with a retryable error.
 * Everything runs on IO; no function throws.
 *
 * List endpoints are fetched unpaged and reconciled row by row: the store
 * keeps exactly the rows the server reported. Tags for the tag filter derive
 * from the items themselves, so the tags endpoints stay unused here.
 */
class BazarrLibraryRepository(
    private val serverId: ServerId,
    private val store: EntityStore,
    private val seriesApi: SeriesApi,
    private val moviesApi: MoviesApi,
    private val episodesApi: EpisodesApi,
    private val badgesApi: BadgesApi,
    private val episodesBlacklistApi: EpisodesBlacklistApi,
    private val moviesBlacklistApi: MoviesBlacklistApi,
    private val languageProfilesApi: SystemLanguagesProfilesApi,
    private val actionQueue: ActionQueue,
    private val now: () -> Long = System::currentTimeMillis,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) {

    fun observeSeries(): Flow<List<BazarrSeries>> =
        store.observe(serverId, SERIES_TYPE, BazarrSeries.serializer())

    fun observeMovies(): Flow<List<BazarrMovie>> =
        store.observe(serverId, MOVIE_TYPE, BazarrMovie.serializer())

    fun observeProfiles(): Flow<List<BazarrLanguageProfile>> =
        store.observe(serverId, PROFILE_TYPE, BazarrLanguageProfile.serializer())

    fun observeEpisodeBlacklist(): Flow<List<BazarrBlacklistEntry>> =
        store.observe(serverId, EPISODE_BLACKLIST_TYPE, BazarrBlacklistEntry.serializer())

    fun observeMovieBlacklist(): Flow<List<BazarrBlacklistEntry>> =
        store.observe(serverId, MOVIE_BLACKLIST_TYPE, BazarrBlacklistEntry.serializer())

    /** Badges are one row; null before the first successful fetch. */
    fun observeBadges(): Flow<BazarrBadges?> =
        store.observe(serverId, BADGES_TYPE, BazarrBadges.serializer()).map { it.firstOrNull() }

    suspend fun cachedSeries(): List<BazarrSeries> =
        store.getAll(serverId, SERIES_TYPE, BazarrSeries.serializer())

    suspend fun cachedMovies(): List<BazarrMovie> =
        store.getAll(serverId, MOVIE_TYPE, BazarrMovie.serializer())

    /** True when the kind's list cache is absent or older than [ttlMillis]. */
    suspend fun isStale(kind: BazarrMediaKind, ttlMillis: Long = LIST_TTL_MILLIS): Boolean {
        val type = when (kind) {
            BazarrMediaKind.SERIES -> SERIES_TYPE
            BazarrMediaKind.MOVIES -> MOVIE_TYPE
        }
        val fetched = store.lastFetchedAt(serverId, type) ?: return true
        return now() - fetched > ttlMillis
    }

    /** Fetches the kind's library and reconciles the cache row by row. */
    suspend fun refresh(kind: BazarrMediaKind): ArrResult<Unit> = withContext(io) {
        when (kind) {
            BazarrMediaKind.SERIES -> {
                val body = when (val fetched = call { seriesApi.getSeries() }) {
                    is ArrResult.Err -> return@withContext fetched
                    is ArrResult.Ok -> fetched.value
                }
                val series = BazarrParsers.parseSeries(body)
                reconcile(SERIES_TYPE, series.map { it.seriesId.toString() })
                store.putAll(
                    serverId, SERIES_TYPE,
                    series.map { it.seriesId.toString() to it },
                    BazarrSeries.serializer(),
                    fetchedAtMillis = now(),
                )
                ArrResult.ok(Unit)
            }

            BazarrMediaKind.MOVIES -> {
                val body = when (val fetched = call { moviesApi.getMovies() }) {
                    is ArrResult.Err -> return@withContext fetched
                    is ArrResult.Ok -> fetched.value
                }
                val movies = BazarrParsers.parseMovies(body)
                reconcile(MOVIE_TYPE, movies.map { it.radarrId.toString() })
                store.putAll(
                    serverId, MOVIE_TYPE,
                    movies.map { it.radarrId.toString() to it },
                    BazarrMovie.serializer(),
                    fetchedAtMillis = now(),
                )
                ArrResult.ok(Unit)
            }
        }
    }

    /** Refresh when the TTL expired; the steady-state path after the first load. */
    suspend fun refreshIfStale(kind: BazarrMediaKind): ArrResult<Unit> =
        if (isStale(kind)) refresh(kind) else ArrResult.ok(Unit)

    /**
     * Refreshes the shared lists the screens join against: language profiles
     * for the mass editor and monolingual filter, blacklists for the
     * blacklist filter, badges for the header summary. Each keeps its own
     * TTL; a failure of one part fails the call, the caller degrades.
     */
    suspend fun refreshSupport(): ArrResult<Unit> = withContext(io) {
        val profileStamp = store.lastFetchedAt(serverId, PROFILE_TYPE)
        if (profileStamp == null || now() - profileStamp > PROFILE_TTL_MILLIS) {
            when (val profiles = call { languageProfilesApi.getLanguagesProfiles() }) {
                is ArrResult.Ok -> {
                    val parsed = BazarrParsers.parseProfiles(profiles.value)
                    if (parsed.isNotEmpty()) {
                        store.putAll(
                            serverId, PROFILE_TYPE,
                            parsed.map { it.profileId.toString() to it },
                            BazarrLanguageProfile.serializer(),
                            fetchedAtMillis = now(),
                        )
                    }
                }
                is ArrResult.Err -> return@withContext profiles
            }
        }
        val blacklistStamp = store.lastFetchedAt(serverId, EPISODE_BLACKLIST_TYPE)
        if (blacklistStamp == null || now() - blacklistStamp > LIST_TTL_MILLIS) {
            when (val episodesList = call { episodesBlacklistApi.getEpisodesBlacklist() }) {
                is ArrResult.Ok -> replaceBlacklist(
                    EPISODE_BLACKLIST_TYPE,
                    BazarrParsers.parseEpisodeBlacklist(episodesList.value),
                )
                is ArrResult.Err -> return@withContext episodesList
            }
            when (val moviesList = call { moviesBlacklistApi.getMoviesBlacklist() }) {
                is ArrResult.Ok -> replaceBlacklist(
                    MOVIE_BLACKLIST_TYPE,
                    BazarrParsers.parseMovieBlacklist(moviesList.value),
                )
                is ArrResult.Err -> return@withContext moviesList
            }
        }
        val badgeStamp = store.lastFetchedAt(serverId, BADGES_TYPE)
        if (badgeStamp == null || now() - badgeStamp > BADGES_TTL_MILLIS) {
            when (val badges = call { badgesApi.getBadges() }) {
                is ArrResult.Ok -> store.put(
                    serverId, BADGES_TYPE, BADGES_ID,
                    BazarrParsers.parseBadges(badges.value),
                    BazarrBadges.serializer(),
                )
                is ArrResult.Err -> return@withContext badges
            }
        }
        ArrResult.ok(Unit)
    }

    /** True when the support data has been fetched at least once. */
    suspend fun supportLoaded(): Boolean =
        store.lastFetchedAt(serverId, BADGES_TYPE) != null

    /**
     * Episodes of one series, cache-first. The cached page carries its own
     * fetch stamp; a stale or missing page refetches. Items reconcile inside
     * the single row so episodes of other series keep their age.
     */
    suspend fun episodes(
        seriesId: Int,
        ttlMillis: Long = EPISODE_TTL_MILLIS,
    ): ArrResult<List<BazarrEpisode>> = withContext(io) {
        val rowId = episodeRowId(seriesId)
        val cached = store.get(serverId, EPISODE_TYPE, rowId, CachedEpisodePage.serializer())
        if (cached != null && now() - cached.fetchedAtMillis <= ttlMillis) {
            return@withContext ArrResult.ok(cached.episodes)
        }
        when (val fetched = call { episodesApi.getEpisodes(seriesIds = listOf(seriesId)) }) {
            is ArrResult.Ok -> {
                val episodes = BazarrParsers.parseEpisodes(fetched.value)
                    .filter { it.seriesId == seriesId }
                    .sortedWith(compareBy({ it.season }, { it.episode }))
                store.put(
                    serverId, EPISODE_TYPE, rowId,
                    CachedEpisodePage(episodes, now()),
                    CachedEpisodePage.serializer(),
                )
                ArrResult.ok(episodes)
            }
            is ArrResult.Err -> {
                // A stale page still beats an error for offline browsing.
                if (cached != null) ArrResult.ok(cached.episodes) else fetched
            }
        }
    }

    /**
     * Bulk language-profile assignment, the mass editor write. On success the
     * cache rows update immediately; on a retryable failure the assignment
     * applies optimistically and replays through the queue, with the item ids
     * visible through [queuedSeriesIds] and [queuedMovieIds].
     */
    suspend fun applyProfile(
        kind: BazarrMediaKind,
        ids: List<Int>,
        profileId: Int,
    ): ArrResult<WriteOutcome> = withContext(io) {
        if (ids.isEmpty()) return@withContext ArrResult.ok(WriteOutcome.Synced)
        val response = try {
            when (kind) {
                BazarrMediaKind.SERIES ->
                    seriesApi.postSeries(seriesIds = ids, profileIds = listOf(profileId.toString()))
                BazarrMediaKind.MOVIES ->
                    moviesApi.postMovies(radarrIds = ids, profileIds = listOf(profileId.toString()))
            }
        } catch (e: HttpException) {
            return@withContext offlineOrErr(e.toArrError(), kind, ids, profileId)
        } catch (e: Throwable) {
            return@withContext offlineOrErr(e.arrError(), kind, ids, profileId)
        }
        if (response.isSuccessful) {
            applyProfileToCache(kind, ids, profileId, stamp = null)
            ArrResult.ok(WriteOutcome.Synced)
        } else {
            ArrResult.err(response.toArrError())
        }
    }

    /** Series ids targeted by queued profile assignments, for queued indicators. */
    fun queuedSeriesIds(): Flow<Set<Int>> = queuedIds(SERIES_WRITE_PREFIX, "seriesid")

    /** Movie ids targeted by queued profile assignments, for queued indicators. */
    fun queuedMovieIds(): Flow<Set<Int>> = queuedIds(MOVIES_WRITE_PREFIX, "radarrid")

    /** Count of queued actions for this server, a library-wide indicator. */
    fun queuedCount(): Flow<Int> = actionQueue.observe().map { pending ->
        pending.count { it.serverId == serverId }
    }

    private fun queuedIds(pathPrefix: String, key: String): Flow<Set<Int>> =
        actionQueue.observe().map { pending ->
            pending.asSequence()
                .filter { it.serverId == serverId && it.path.startsWith(pathPrefix) }
                .flatMap { action -> parseQueryIds(action.path, key) }
                .toSet()
        }

    private suspend fun offlineOrErr(
        error: ArrError,
        kind: BazarrMediaKind,
        ids: List<Int>,
        profileId: Int,
    ): ArrResult<WriteOutcome> {
        if (!error.isRetryable()) return ArrResult.err(error)
        applyProfileToCache(kind, ids, profileId, stamp = keepStamp(kind))
        val action = QueuedAction(
            actionId = ActionId(UUID.randomUUID().toString()),
            serverId = serverId,
            appType = AppType.BAZARR,
            method = "POST",
            path = writePath(kind, ids, profileId),
            description = writeDescription(kind, ids.size),
            enqueuedAtMillis = now(),
        )
        actionQueue.enqueue(action)
        return ArrResult.ok(WriteOutcome.Queued(action.actionId))
    }

    /** Optimistic assignment: rewrites only the touched rows, preserving their fetch stamp. */
    private suspend fun applyProfileToCache(
        kind: BazarrMediaKind,
        ids: List<Int>,
        profileId: Int,
        stamp: Long?,
    ) {
        when (kind) {
            BazarrMediaKind.SERIES -> {
                val targets = ids.toSet()
                val updated = cachedSeries()
                    .filter { it.seriesId in targets }
                    .map { row ->
                        row.copy(
                            profileId = profileId,
                            status = SubtitleStatuses.ofSeries(
                                profileId, row.episodeFileCount, row.episodeMissingCount,
                            ),
                        )
                    }
                if (updated.isNotEmpty()) {
                    store.putAll(
                        serverId, SERIES_TYPE,
                        updated.map { it.seriesId.toString() to it },
                        BazarrSeries.serializer(),
                        fetchedAtMillis = stamp ?: now(),
                    )
                }
            }

            BazarrMediaKind.MOVIES -> {
                val targets = ids.toSet()
                val updated = cachedMovies()
                    .filter { it.radarrId in targets }
                    .map { row ->
                        row.copy(
                            profileId = profileId,
                            status = SubtitleStatuses.ofUnit(row.subtitles, row.missingSubtitles),
                        )
                    }
                if (updated.isNotEmpty()) {
                    store.putAll(
                        serverId, MOVIE_TYPE,
                        updated.map { it.radarrId.toString() to it },
                        BazarrMovie.serializer(),
                        fetchedAtMillis = stamp ?: now(),
                    )
                }
            }
        }
    }

    /** Carries the current stamp over so an optimistic edit does not extend the refresh TTL. */
    private suspend fun keepStamp(kind: BazarrMediaKind): Long? = when (kind) {
        BazarrMediaKind.SERIES -> store.lastFetchedAt(serverId, SERIES_TYPE)
        BazarrMediaKind.MOVIES -> store.lastFetchedAt(serverId, MOVIE_TYPE)
    }

    private suspend fun reconcile(type: EntityType, incomingIds: List<String>) {
        val existing = when (type) {
            SERIES_TYPE -> cachedSeries().map { it.seriesId.toString() }
            else -> cachedMovies().map { it.radarrId.toString() }
        }.toSet()
        existing.filter { it !in incomingIds.toSet() }.forEach { id ->
            store.delete(serverId, type, id)
        }
    }

    /** Blacklists have no row ids, so the whole list replaces the type's rows. */
    private suspend fun replaceBlacklist(
        type: EntityType,
        entries: List<BazarrBlacklistEntry>,
    ) {
        store.deleteAll(serverId, type)
        if (entries.isNotEmpty()) {
            store.putAll(
                serverId, type,
                entries.mapIndexed { index, entry -> "$index" to entry },
                BazarrBlacklistEntry.serializer(),
                fetchedAtMillis = now(),
            )
        }
    }

    /** Runs one API call, mapping every failure onto the shared error taxonomy. */
    private suspend fun <T> call(block: suspend () -> T): ArrResult<T> = try {
        ArrResult.ok(block())
    } catch (e: HttpException) {
        ArrResult.err(e.toArrError())
    } catch (e: Throwable) {
        ArrResult.err(e.arrError())
    }

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

    private fun Throwable.arrError(): ArrError = when (this) {
        is IOException -> ArrError.Network(this)
        else -> ArrError.Unknown(this)
    }

    companion object {
        internal val SERIES_TYPE = EntityType("bazarr.series")
        internal val MOVIE_TYPE = EntityType("bazarr.movie")
        internal val EPISODE_TYPE = EntityType("bazarr.episode")
        internal val PROFILE_TYPE = EntityType("bazarr.languageprofile")
        internal val EPISODE_BLACKLIST_TYPE = EntityType("bazarr.blacklist.episode")
        internal val MOVIE_BLACKLIST_TYPE = EntityType("bazarr.blacklist.movie")
        internal val BADGES_TYPE = EntityType("bazarr.badges")
        private const val BADGES_ID = "badges"
        internal const val SERIES_WRITE_PREFIX = "series?"
        internal const val MOVIES_WRITE_PREFIX = "movies?"

        /** Ten minutes; lists move as downloads and subtitle searches land. */
        const val LIST_TTL_MILLIS: Long = 10 * 60 * 1000

        /** Five minutes; badges drive the header counters users watch. */
        const val BADGES_TTL_MILLIS: Long = 5 * 60 * 1000

        /** One hour; profiles change rarely. */
        const val PROFILE_TTL_MILLIS: Long = 60 * 60 * 1000

        /** Ten minutes; an episode page refreshes with its series list. */
        const val EPISODE_TTL_MILLIS: Long = 10 * 60 * 1000

        internal fun episodeRowId(seriesId: Int): String = "series-$seriesId"

        /**
         * The replayable descriptor for a profile assignment, relative to the
         * server's API base exactly like the Retrofit declarations: repeated
         * id parameters plus one profileid.
         */
        internal fun writePath(kind: BazarrMediaKind, ids: List<Int>, profileId: Int): String {
            val builder = StringBuilder(
                if (kind == BazarrMediaKind.SERIES) SERIES_WRITE_PREFIX else MOVIES_WRITE_PREFIX,
            )
            ids.forEachIndexed { index, id ->
                if (index > 0) builder.append('&')
                builder.append(
                    if (kind == BazarrMediaKind.SERIES) "seriesid=" else "radarrid=",
                ).append(id)
            }
            builder.append("&profileid=").append(profileId)
            return builder.toString()
        }

        /** Reads one repeated integer query key back out of a queued path. */
        internal fun parseQueryIds(path: String, key: String): List<Int> {
            val query = path.substringAfter('?', "")
            if (query.isEmpty()) return emptyList()
            return query.split('&').mapNotNull { pair ->
                val parts = pair.split('=', limit = 2)
                if (parts.size == 2 && parts[0] == key) parts[1].toIntOrNull() else null
            }
        }

        private fun writeDescription(kind: BazarrMediaKind, count: Int): String =
            "Assign profile to $count " +
                if (kind == BazarrMediaKind.SERIES) "series" else "movies"
    }
}
