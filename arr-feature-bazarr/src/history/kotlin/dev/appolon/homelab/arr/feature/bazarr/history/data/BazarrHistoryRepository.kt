package dev.appolon.homelab.arr.feature.bazarr.history.data

import dev.appolon.homelab.arr.api.bazarr.EpisodesBlacklistApi
import dev.appolon.homelab.arr.api.bazarr.EpisodesHistoryApi
import dev.appolon.homelab.arr.api.bazarr.MoviesBlacklistApi
import dev.appolon.homelab.arr.api.bazarr.MoviesHistoryApi
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
import dev.appolon.homelab.arr.feature.bazarr.episodes.data.urlEncoded
import dev.appolon.homelab.arr.feature.bazarr.episodes.model.BazarrHistoryEvent
import dev.appolon.homelab.arr.feature.bazarr.episodes.model.BazarrLanguageOption
import dev.appolon.homelab.arr.feature.bazarr.episodes.model.rowsOf
import dev.appolon.homelab.arr.feature.bazarr.episodes.model.toHistoryEvent
import dev.appolon.homelab.arr.feature.bazarr.episodes.model.toLanguageOptions
import dev.appolon.homelab.arr.feature.bazarr.history.model.BazarrBlacklistEntry
import dev.appolon.homelab.arr.feature.bazarr.history.model.toBlacklistEntry
import dev.appolon.homelab.arr.net.arrError
import java.util.UUID
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import retrofit2.Response

/** Outcome of a blacklist write: applied on the server, or queued for replay. */
sealed interface BlacklistWriteOutcome {
    data object Synced : BlacklistWriteOutcome
    data class Queued(val actionId: ActionId) : BlacklistWriteOutcome
}

/**
 * Repository for the Bazarr subtitle history and provider blacklist
 * screens. The merged decision feed pages through start/length on both
 * history endpoints, appending into the [EntityStore] cache so the feed
 * survives process death; cursors stay in memory and reset on refresh.
 * Blacklist reads cache the same way. Remove writes hit the server first
 * and, when the failure is retryable (offline or network), enqueue a
 * replayable [QueuedAction] while the row leaves the cache optimistically
 * and shows the queued indicator. No function throws.
 */
class BazarrHistoryRepository(
    private val serverId: ServerId,
    private val store: EntityStore,
    private val episodesHistoryApi: EpisodesHistoryApi,
    private val moviesHistoryApi: MoviesHistoryApi,
    private val episodesBlacklistApi: EpisodesBlacklistApi,
    private val moviesBlacklistApi: MoviesBlacklistApi,
    private val languagesApi: SystemLanguagesApi,
    private val actionQueue: ActionQueue,
    private val now: () -> Long = System::currentTimeMillis,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val pageLength: Int = DEFAULT_PAGE_LENGTH,
) {

    private val pagingMutex = Mutex()

    private var episodeLoaded = 0
    private var movieLoaded = 0
    private var episodesExhausted = false
    private var moviesExhausted = false

    // ---------------------------------------------------------------- reads

    /** Live merged decision feed, newest first. */
    fun observeHistory(): Flow<List<BazarrHistoryEvent>> =
        store.observe(serverId, HISTORY_TYPE, BazarrHistoryEvent.serializer())
            .map { rows -> rows.sortedByDescending { it.timestampMillis } }

    /** True when both history feeds are known to have no further pages. */
    fun historyExhausted(): Boolean = episodesExhausted && moviesExhausted

    /**
     * Resets paging and replaces the cache with the first page of both
     * feeds. Pull-to-refresh and error retry land here.
     */
    suspend fun refreshHistory(): ArrResult<Unit> = pagingMutex.withLock {
        withContext(ioDispatcher) {
            val episodeRows = try {
                episodesHistoryApi.getEpisodesHistory(start = 0, length = pageLength)
                    .rowsOf().mapNotNull { it.toHistoryEvent(isMovie = false) }
            } catch (e: HttpException) {
                return@withContext ArrResult.err(e.toArrError())
            } catch (e: Throwable) {
                return@withContext ArrResult.err(e.arrError())
            }
            val movieRows = try {
                moviesHistoryApi.getMoviesHistory(start = 0, length = pageLength)
                    .rowsOf().mapNotNull { it.toHistoryEvent(isMovie = true) }
            } catch (e: HttpException) {
                return@withContext ArrResult.err(e.toArrError())
            } catch (e: Throwable) {
                return@withContext ArrResult.err(e.arrError())
            }
            episodesExhausted = episodeRows.size < pageLength
            moviesExhausted = movieRows.size < pageLength
            episodeLoaded = episodeRows.size
            movieLoaded = movieRows.size
            // History rows carry no server id, so a refresh replaces the whole
            // feed atomically; page offsets keep row ids stable between loads.
            store.deleteAll(serverId, HISTORY_TYPE)
            store.putAll(
                serverId,
                HISTORY_TYPE,
                historyIds(episodeRows, movieRows),
                BazarrHistoryEvent.serializer(),
                fetchedAtMillis = now(),
            )
            ArrResult.ok(Unit)
        }
    }

    /**
     * Fetches the next page of whichever feed is not exhausted and appends
     * it to the cache. Returns the number of rows appended so the caller can
     * stop paging on a stuck server.
     */
    suspend fun loadMoreHistory(): ArrResult<Int> = pagingMutex.withLock {
        withContext(ioDispatcher) {
            if (episodesExhausted && moviesExhausted) return@withContext ArrResult.ok(0)
            var appended = 0
            if (!episodesExhausted) {
                when (val page = fetchEpisodePage(episodeLoaded)) {
                    is ArrResult.Err -> return@withContext page
                    is ArrResult.Ok -> {
                        val base = episodeLoaded
                        episodesExhausted = page.value.size < pageLength
                        episodeLoaded += page.value.size
                        appended += page.value.size
                        store.putAll(
                            serverId,
                            HISTORY_TYPE,
                            page.value.mapIndexed { index, event ->
                                "${event.timestampMillis}_${event.episodeId ?: 0}_e${base + index}" to event
                            },
                            BazarrHistoryEvent.serializer(),
                        )
                    }
                }
            }
            if (!moviesExhausted) {
                when (val page = fetchMoviePage(movieLoaded)) {
                    is ArrResult.Err -> return@withContext page
                    is ArrResult.Ok -> {
                        val base = movieLoaded
                        moviesExhausted = page.value.size < pageLength
                        movieLoaded += page.value.size
                        appended += page.value.size
                        store.putAll(
                            serverId,
                            HISTORY_TYPE,
                            page.value.mapIndexed { index, event ->
                                "${event.timestampMillis}_${event.radarrId ?: 0}_m${base + index}" to event
                            },
                            BazarrHistoryEvent.serializer(),
                        )
                    }
                }
            }
            ArrResult.ok(appended)
        }
    }

    /** Live episode blacklist rows, newest first. */
    fun observeEpisodeBlacklist(): Flow<List<BazarrBlacklistEntry>> =
        store.observe(serverId, BLACKLIST_EPISODE_TYPE, BazarrBlacklistEntry.serializer())
            .map { rows -> rows.sortedByDescending { it.timestampMillis } }

    /** Live movie blacklist rows, newest first. */
    fun observeMovieBlacklist(): Flow<List<BazarrBlacklistEntry>> =
        store.observe(serverId, BLACKLIST_MOVIE_TYPE, BazarrBlacklistEntry.serializer())
            .map { rows -> rows.sortedByDescending { it.timestampMillis } }

    /** Fetches both blacklist feeds as one deep page and reconciles them. */
    suspend fun refreshBlacklist(): ArrResult<Unit> = withContext(ioDispatcher) {
        val episodeRows = try {
            episodesBlacklistApi.getEpisodesBlacklist(start = 0, length = PAGE_LENGTH)
                .rowsOf().mapNotNull { it.toBlacklistEntry(isMovie = false) }
        } catch (e: HttpException) {
            return@withContext ArrResult.err(e.toArrError())
        } catch (e: Throwable) {
            return@withContext ArrResult.err(e.arrError())
        }
        val movieRows = try {
            moviesBlacklistApi.getMoviesBlacklist(start = 0, length = PAGE_LENGTH)
                .rowsOf().mapNotNull { it.toBlacklistEntry(isMovie = true) }
        } catch (e: HttpException) {
            return@withContext ArrResult.err(e.toArrError())
        } catch (e: Throwable) {
            return@withContext ArrResult.err(e.arrError())
        }
        replaceBlacklist(BLACKLIST_EPISODE_TYPE, episodeRows)
        replaceBlacklist(BLACKLIST_MOVIE_TYPE, movieRows)
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
     * Removes one blacklisted episode subtitle. Both the synced and queued
     * paths drop the row from the cache so the UI updates immediately.
     */
    suspend fun removeEpisodeBlacklistEntry(entry: BazarrBlacklistEntry): ArrResult<BlacklistWriteOutcome> =
        withContext(ioDispatcher) {
            val path = "episodes/blacklist?provider=${entry.provider.urlEncoded()}" +
                "&subs_id=${entry.subsId.urlEncoded()}"
            try {
                episodesBlacklistApi.deleteEpisodesBlacklist(
                    all = null,
                    provider = entry.provider,
                    subsId = entry.subsId,
                ).toOutcome { store.delete(serverId, BLACKLIST_EPISODE_TYPE, entry.key) }
            } catch (e: HttpException) {
                enqueueOrErr(e.toArrError(), path) {
                    store.delete(serverId, BLACKLIST_EPISODE_TYPE, entry.key)
                }
            } catch (e: Throwable) {
                enqueueOrErr(e.arrError(), path) {
                    store.delete(serverId, BLACKLIST_EPISODE_TYPE, entry.key)
                }
            }
        }

    /** Movie twin of [removeEpisodeBlacklistEntry]. */
    suspend fun removeMovieBlacklistEntry(entry: BazarrBlacklistEntry): ArrResult<BlacklistWriteOutcome> =
        withContext(ioDispatcher) {
            val path = "movies/blacklist?provider=${entry.provider.urlEncoded()}" +
                "&subs_id=${entry.subsId.urlEncoded()}"
            try {
                moviesBlacklistApi.deleteMoviesBlacklist(
                    all = null,
                    provider = entry.provider,
                    subsId = entry.subsId,
                ).toOutcome { store.delete(serverId, BLACKLIST_MOVIE_TYPE, entry.key) }
            } catch (e: HttpException) {
                enqueueOrErr(e.toArrError(), path) {
                    store.delete(serverId, BLACKLIST_MOVIE_TYPE, entry.key)
                }
            } catch (e: Throwable) {
                enqueueOrErr(e.arrError(), path) {
                    store.delete(serverId, BLACKLIST_MOVIE_TYPE, entry.key)
                }
            }
        }

    /**
     * Removes every entry of the episode blacklist. The optimistic clear
     * wipes the cached rows on both the synced and queued paths.
     */
    suspend fun clearEpisodeBlacklist(): ArrResult<BlacklistWriteOutcome> = withContext(ioDispatcher) {
        val path = "episodes/blacklist?all=True"
        try {
            episodesBlacklistApi.deleteEpisodesBlacklist(all = "True", provider = null, subsId = null)
                .toOutcome { store.deleteAll(serverId, BLACKLIST_EPISODE_TYPE) }
        } catch (e: HttpException) {
            enqueueOrErr(e.toArrError(), path) {
                store.deleteAll(serverId, BLACKLIST_EPISODE_TYPE)
            }
        } catch (e: Throwable) {
            enqueueOrErr(e.arrError(), path) {
                store.deleteAll(serverId, BLACKLIST_EPISODE_TYPE)
            }
        }
    }

    /** Movie twin of [clearEpisodeBlacklist]. */
    suspend fun clearMovieBlacklist(): ArrResult<BlacklistWriteOutcome> = withContext(ioDispatcher) {
        val path = "movies/blacklist?all=True"
        try {
            moviesBlacklistApi.deleteMoviesBlacklist(all = "True", provider = null, subsId = null)
                .toOutcome { store.deleteAll(serverId, BLACKLIST_MOVIE_TYPE) }
        } catch (e: HttpException) {
            enqueueOrErr(e.toArrError(), path) {
                store.deleteAll(serverId, BLACKLIST_MOVIE_TYPE)
            }
        } catch (e: Throwable) {
            enqueueOrErr(e.arrError(), path) {
                store.deleteAll(serverId, BLACKLIST_MOVIE_TYPE)
            }
        }
    }

    // ------------------------------------------------------- queued signals

    /** Blacklist entry keys this server has queued remove actions for. */
    fun queuedBlacklistKeys(): Flow<Set<String>> = actionQueue.observe().map { pending ->
        pending.asSequence()
            .filter { action ->
                action.serverId == serverId &&
                    (action.path.startsWith("episodes/blacklist") || action.path.startsWith("movies/blacklist"))
            }
            .flatMap { action ->
                val provider = paramValueOf(action.path, "provider")
                val subsId = paramValueOf(action.path, "subs_id")
                if (provider != null && subsId != null) {
                    sequenceOf("$provider|$subsId")
                } else {
                    emptySequence()
                }
            }
            .toSet()
    }

    /** True when a queued clear-all exists for the given media kind. */
    fun queuedClearAll(isMovie: Boolean): Flow<Boolean> = actionQueue.observe().map { pending ->
        pending.any { action ->
            action.serverId == serverId &&
                (if (isMovie) action.path.startsWith("movies/blacklist") else action.path.startsWith("episodes/blacklist")) &&
                paramValueOf(action.path, "all") == "True"
        }
    }

    // ------------------------------------------------------------ internals

    private suspend fun fetchEpisodePage(start: Int): ArrResult<List<BazarrHistoryEvent>> = try {
        ArrResult.ok(
            episodesHistoryApi.getEpisodesHistory(start = start, length = pageLength)
                .rowsOf().mapNotNull { it.toHistoryEvent(isMovie = false) },
        )
    } catch (e: HttpException) {
        ArrResult.err(e.toArrError())
    } catch (e: Throwable) {
        ArrResult.err(e.arrError())
    }

    private suspend fun fetchMoviePage(start: Int): ArrResult<List<BazarrHistoryEvent>> = try {
        ArrResult.ok(
            moviesHistoryApi.getMoviesHistory(start = start, length = pageLength)
                .rowsOf().mapNotNull { it.toHistoryEvent(isMovie = true) },
        )
    } catch (e: HttpException) {
        ArrResult.err(e.toArrError())
    } catch (e: Throwable) {
        ArrResult.err(e.arrError())
    }

    private fun historyIds(
        episodeRows: List<BazarrHistoryEvent>,
        movieRows: List<BazarrHistoryEvent>,
    ): List<Pair<String, BazarrHistoryEvent>> =
        episodeRows.mapIndexed { index, event ->
            "${event.timestampMillis}_${event.episodeId ?: 0}_e$index" to event
        } + movieRows.mapIndexed { index, event ->
            "${event.timestampMillis}_${event.radarrId ?: 0}_m$index" to event
        }

    private suspend fun replaceBlacklist(type: EntityType, rows: List<BazarrBlacklistEntry>) {
        val incomingKeys = rows.map { it.key }.toSet()
        store.getAll(serverId, type, BazarrBlacklistEntry.serializer()).forEach { row ->
            if (row.key !in incomingKeys) store.delete(serverId, type, row.key)
        }
        store.putAll(
            serverId,
            type,
            rows.map { it.key to it },
            BazarrBlacklistEntry.serializer(),
            fetchedAtMillis = now(),
        )
    }

    private suspend fun Response<Unit>.toOutcome(
        optimistic: suspend () -> Unit,
    ): ArrResult<BlacklistWriteOutcome> = if (isSuccessful) {
        optimistic()
        ArrResult.ok(BlacklistWriteOutcome.Synced)
    } else {
        ArrResult.err(HttpException(this).toArrError())
    }

    private suspend fun enqueueOrErr(
        error: ArrError,
        path: String,
        optimistic: suspend () -> Unit,
    ): ArrResult<BlacklistWriteOutcome> {
        if (!error.isRetryable()) return ArrResult.err(error)
        optimistic()
        val action = QueuedAction(
            actionId = ActionId(UUID.randomUUID().toString()),
            serverId = serverId,
            appType = AppType.BAZARR,
            method = "DELETE",
            path = path,
            description = "Remove blacklisted subtitle",
            enqueuedAtMillis = now(),
        )
        return try {
            actionQueue.enqueue(action)
            ArrResult.ok(BlacklistWriteOutcome.Queued(action.actionId))
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
        internal val HISTORY_TYPE = EntityType("bazarr.history.event")
        internal val BLACKLIST_EPISODE_TYPE = EntityType("bazarr.history.blacklist.episode")
        internal val BLACKLIST_MOVIE_TYPE = EntityType("bazarr.history.blacklist.movie")
        internal val LANGUAGE_TYPE = EntityType("bazarr.history.language")

        /** History pages are short by design; the screen grows the feed on scroll. */
        public const val DEFAULT_PAGE_LENGTH: Int = 50

        /** Blacklist feeds are small; one deep page covers real servers. */
        public const val PAGE_LENGTH: Int = 10_000

        /** One day: the languages list changes only with settings edits. */
        public const val LANGUAGE_TTL_MILLIS: Long = 24 * 60 * 60 * 1000
    }
}

/**
 * Extracts one string query parameter from a queued path, decoding the
 * percent escapes [urlEncoded] added so the value replays identically.
 */
internal fun paramValueOf(path: String, name: String): String? {
    val index = path.indexOf("$name=")
    if (index < 0) return null
    val start = index + name.length + 1
    var end = start
    while (end < path.length && path[end] != '&') end++
    if (end == start) return null
    return percentDecoded(path.substring(start, end))
}

private fun percentDecoded(value: String): String {
    if (!value.contains('%')) return value
    val bytes = ArrayList<Byte>(value.length)
    var index = 0
    while (index < value.length) {
        val c = value[index]
        if (c == '%' && index + 2 < value.length) {
            val high = value[index + 1].digitToIntOrNull(16) ?: return value
            val low = value[index + 2].digitToIntOrNull(16) ?: return value
            bytes.add(((high shl 4) or low).toByte())
            index += 3
        } else {
            bytes.add(c.code.toByte())
            index++
        }
    }
    return String(bytes.toByteArray(), Charsets.UTF_8)
}
