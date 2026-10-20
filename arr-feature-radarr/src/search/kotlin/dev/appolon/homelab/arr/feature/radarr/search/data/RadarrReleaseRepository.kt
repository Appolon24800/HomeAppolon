package dev.appolon.homelab.arr.feature.radarr.search.data

import dev.appolon.homelab.arr.api.radarr.model.Language
import dev.appolon.homelab.arr.api.radarr.model.Quality
import dev.appolon.homelab.arr.core.ActionId
import dev.appolon.homelab.arr.core.AppType
import dev.appolon.homelab.arr.core.ArrError
import dev.appolon.homelab.arr.core.ArrResult
import dev.appolon.homelab.arr.core.EntityType
import dev.appolon.homelab.arr.core.ServerId
import dev.appolon.homelab.arr.data.ActionQueue
import dev.appolon.homelab.arr.data.EntityStore
import dev.appolon.homelab.arr.data.QueuedAction
import dev.appolon.homelab.arr.feature.radarr.library.data.WriteOutcome
import dev.appolon.homelab.arr.net.ArrJson
import dev.appolon.homelab.arr.net.arrError
import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import retrofit2.HttpException

/**
 * Interactive search repository. Reads are cache-first through the
 * [EntityStore] with a per-movie fetchedAt TTL carried inside the page
 * entity; refresh runs the release search over Retrofit and writes through.
 * Grabs and pushes go to the server first and fall back to the [ActionQueue]
 * on retryable failures, so a grab started on a flaky link replays later.
 * Nothing throws.
 */
class RadarrReleaseRepository(
    private val serverId: ServerId,
    private val store: EntityStore,
    private val releaseApi: RadarrReleaseApi,
    private val releasePushApi: RadarrReleasePushApi,
    private val actionQueue: ActionQueue,
    private val json: kotlinx.serialization.json.Json = ArrJson,
    private val now: () -> Long = System::currentTimeMillis,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) {

    /** Live cache of one movie's releases, empty until first stored page. */
    fun observeReleases(movieId: Int): Flow<List<RadarrReleaseResource>> =
        store.observe(serverId, RELEASE_TYPE, RadarrReleasePage.serializer())
            .map { pages -> pages.firstOrNull { it.movieId == movieId }?.releases.orEmpty() }

    suspend fun cachedReleases(movieId: Int): List<RadarrReleaseResource> =
        pageOf(movieId)?.releases.orEmpty()

    /** True when the movie has no page or its stamp is older than [ttlMillis]. */
    suspend fun isStale(movieId: Int, ttlMillis: Long = RELEASE_TTL_MILLIS): Boolean {
        val page = pageOf(movieId) ?: return true
        return now() - page.fetchedAtMillis > ttlMillis
    }

    /**
     * Cache-first load: returns the stored page while a refresh runs when the
     * TTL expired. A failed refresh keeps the cache and reports the error, so
     * the screen can show stale rows with an error banner instead of a dead
     * spinner.
     */
    suspend fun releases(movieId: Int): ArrResult<List<RadarrReleaseResource>> =
        withContext(io) {
            val cached = cachedReleases(movieId)
            if (!isStale(movieId)) return@withContext ArrResult.ok(cached)
            when (val refreshed = refresh(movieId)) {
                is ArrResult.Ok -> ArrResult.ok(refreshed.value)
                is ArrResult.Err -> refreshed
            }
        }

    /** Runs the interactive search and replaces the movie's cached page. */
    suspend fun refresh(movieId: Int): ArrResult<List<RadarrReleaseResource>> =
        withContext(io) {
            val fetched = try {
                releaseApi.list(movieId)
            } catch (e: HttpException) {
                return@withContext ArrResult.err(e.toArrError())
            } catch (e: IOException) {
                return@withContext ArrResult.err(ArrError.Network(e))
            } catch (e: Throwable) {
                return@withContext ArrResult.err(e.arrError())
            }
            store.put(
                serverId,
                RELEASE_TYPE,
                movieId.toString(),
                RadarrReleasePage(movieId = movieId, releases = fetched, fetchedAtMillis = now()),
                RadarrReleasePage.serializer(),
            )
            ArrResult.ok(fetched)
        }

    /**
     * Grabs one search result, applying the grab sheet's optional quality and
     * language overrides to the posted body. Retryable failures enqueue the
     * POST for replay; the caller learns which via [WriteOutcome].
     */
    suspend fun grabRelease(
        movieId: Int,
        release: RadarrReleaseResource,
        qualityOverride: Quality? = null,
        languageOverride: Language? = null,
    ): ArrResult<WriteOutcome> {
        val body = applyGrabOverrides(release, qualityOverride, languageOverride)
            .copy(movieId = movieId)
        return writeOrQueue(
            body = body,
            online = { releaseApi.grab(body); Unit },
            path = GRAB_PATH,
            description = "Grab release ${body.guid ?: body.title.orEmpty()}",
        )
    }

    /**
     * Pushes a release Radarr did not find itself (for example one handed
     * over by a Prowlarr search) to the download chain. Offline pushes queue
     * like grabs.
     */
    suspend fun pushRelease(release: RadarrReleaseResource): ArrResult<WriteOutcome> =
        writeOrQueue(
            body = release,
            online = { releasePushApi.push(release); Unit },
            path = PUSH_PATH,
            description = "Push release ${release.title.orEmpty()}",
        )

    /** Movie ids with a queued grab or push, for per-row queued badges. */
    fun queuedMovieIds(): Flow<Set<Int>> = actionQueue.observe().map { pending ->
        pending.asSequence()
            .filter { it.serverId == serverId && it.path in GRAB_PATHS }
            .mapNotNull { action ->
                runCatching {
                    json.decodeFromString(
                        RadarrReleaseResource.serializer(),
                        action.bodyJson.orEmpty(),
                    )
                }.getOrNull()?.movieId
            }
            .filterNotNull()
            .toSet()
    }

    private suspend fun writeOrQueue(
        body: RadarrReleaseResource,
        online: suspend () -> Unit,
        path: String,
        description: String,
    ): ArrResult<WriteOutcome> = withContext(io) {
        val failure = try {
            online()
            null
        } catch (e: HttpException) {
            e.toArrError()
        } catch (e: IOException) {
            ArrError.Network(e)
        } catch (e: Throwable) {
            e.arrError()
        }
        when {
            failure == null -> ArrResult.ok(WriteOutcome.Synced)

            failure is ArrError.Offline || failure is ArrError.Network -> {
                val actionId = ActionId(UUID.nameUUIDFromBytes(
                    "$serverId:POST:$path:$body".toByteArray(Charsets.UTF_8),
                ).toString())
                actionQueue.enqueue(
                    QueuedAction(
                        actionId = actionId,
                        serverId = serverId,
                        appType = AppType.RADARR,
                        method = "POST",
                        path = path,
                        bodyJson = json.encodeToString(RadarrReleaseResource.serializer(), body),
                        description = description,
                        enqueuedAtMillis = now(),
                    ),
                )
                ArrResult.ok(WriteOutcome.Queued(actionId))
            }

            else -> ArrResult.err(failure)
        }
    }

    private suspend fun pageOf(movieId: Int): RadarrReleasePage? =
        store.get(serverId, RELEASE_TYPE, movieId.toString(), RadarrReleasePage.serializer())

    private fun HttpException.toArrError(): ArrError = when (code()) {
        401, 403 -> ArrError.Auth(code())
        else -> ArrError.Http(code(), message())
    }

    companion object {
        /** Five minutes; indexer results go stale as grabs land. */
        const val RELEASE_TTL_MILLIS: Long = dev.appolon.homelab.arr.core.CacheTtl.LIBRARY

        internal val RELEASE_TYPE = EntityType("radarr.search.releases")
        internal const val GRAB_PATH = "api/v3/release"
        internal const val PUSH_PATH = "api/v3/release/push"
        internal val GRAB_PATHS = setOf(GRAB_PATH, PUSH_PATH)
    }
}
