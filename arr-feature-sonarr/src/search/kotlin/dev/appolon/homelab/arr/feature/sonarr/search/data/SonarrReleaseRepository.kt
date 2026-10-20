package dev.appolon.homelab.arr.feature.sonarr.search.data

import dev.appolon.homelab.arr.api.sonarr.ReleaseApi
import dev.appolon.homelab.arr.api.sonarr.ReleasePushApi
import dev.appolon.homelab.arr.api.sonarr.model.Language
import dev.appolon.homelab.arr.api.sonarr.model.Quality
import dev.appolon.homelab.arr.api.sonarr.model.ReleaseResource
import dev.appolon.homelab.arr.core.ActionId
import dev.appolon.homelab.arr.core.AppType
import dev.appolon.homelab.arr.core.ArrError
import dev.appolon.homelab.arr.core.ArrResult
import dev.appolon.homelab.arr.core.EntityType
import dev.appolon.homelab.arr.core.ServerId
import dev.appolon.homelab.arr.data.ActionQueue
import dev.appolon.homelab.arr.data.EntityStore
import dev.appolon.homelab.arr.data.QueuedAction
import dev.appolon.homelab.arr.feature.sonarr.library.data.WriteOutcome
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
 * [EntityStore] with a per-scope fetchedAt TTL carried inside the page
 * entity; refresh runs the release search over Retrofit and writes through.
 * Grabs and pushes go to the server first and fall back to the [ActionQueue]
 * on retryable failures, so a grab started on a flaky link replays later.
 * Nothing throws.
 */
class SonarrReleaseRepository(
    private val serverId: ServerId,
    private val store: EntityStore,
    private val releaseApi: ReleaseApi,
    private val releasePushApi: ReleasePushApi,
    private val actionQueue: ActionQueue,
    private val json: kotlinx.serialization.json.Json = ArrJson,
    private val now: () -> Long = System::currentTimeMillis,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) {

    /** Live cache of one scope's releases, empty until the first stored page. */
    fun observeReleases(scope: ReleaseScope): Flow<List<ReleaseResource>> =
        store.observe(serverId, RELEASE_TYPE, SonarrReleasePage.serializer())
            .map { pages ->
                pages.firstOrNull { it.scopeKey == scope.cacheKey() }?.releases.orEmpty()
            }

    suspend fun cachedReleases(scope: ReleaseScope): List<ReleaseResource> =
        pageOf(scope)?.releases.orEmpty()

    /** True when the scope has no page or its stamp is older than [ttlMillis]. */
    suspend fun isStale(scope: ReleaseScope, ttlMillis: Long = RELEASE_TTL_MILLIS): Boolean {
        val page = pageOf(scope) ?: return true
        return now() - page.fetchedAtMillis > ttlMillis
    }

    /**
     * Cache-first load: returns the stored page while a refresh runs when the
     * TTL expired. A failed refresh keeps the cache and reports the error, so
     * the screen can show stale rows with an error banner instead of a dead
     * spinner.
     */
    suspend fun releases(scope: ReleaseScope): ArrResult<List<ReleaseResource>> =
        withContext(io) {
            val cached = cachedReleases(scope)
            if (!isStale(scope)) return@withContext ArrResult.ok(cached)
            when (val refreshed = refresh(scope)) {
                is ArrResult.Ok -> ArrResult.ok(refreshed.value)
                is ArrResult.Err -> {
                    if (cached.isNotEmpty()) ArrResult.ok(cached) else refreshed
                }
            }
        }

    /** Runs the interactive search for the scope and replaces its cached page. */
    suspend fun refresh(scope: ReleaseScope): ArrResult<List<ReleaseResource>> =
        withContext(io) {
            val fetched = try {
                releaseApi.list(
                    seriesId = scope.seriesId,
                    seasonNumber = (scope as? ReleaseScope.Season)?.seasonNumber,
                    episodeId = (scope as? ReleaseScope.Episode)?.episodeId,
                )
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
                scope.cacheKey(),
                SonarrReleasePage(
                    scopeKey = scope.cacheKey(),
                    releases = fetched,
                    fetchedAtMillis = now(),
                ),
                SonarrReleasePage.serializer(),
            )
            ArrResult.ok(fetched)
        }

    /**
     * Grabs one search result, applying the grab sheet's optional quality and
     * language overrides to the posted body and pinning the scope's series
     * (and episode, for episode scope) so the grab lands where the user
     * searched. Retryable failures enqueue the POST for replay.
     */
    suspend fun grabRelease(
        scope: ReleaseScope,
        release: ReleaseResource,
        qualityOverride: Quality? = null,
        languageOverride: Language? = null,
    ): ArrResult<WriteOutcome> {
        val body = applyGrabOverrides(release, qualityOverride, languageOverride)
            .copy(seriesId = scope.seriesId)
        val episodeScope = scope as? ReleaseScope.Episode
        val withEpisode = if (episodeScope == null) {
            body
        } else {
            body.copy(episodeId = episodeScope.episodeId)
        }
        return writeOrQueue(
            body = withEpisode,
            online = { releaseApi.create(withEpisode); Unit },
            path = GRAB_PATH,
            description = "Grab release ${withEpisode.guid ?: withEpisode.title.orEmpty()}",
        )
    }

    /**
     * Pushes a release Sonarr did not find itself (for example one handed
     * over by a Prowlarr search) to the download chain. Offline pushes queue
     * like grabs.
     */
    suspend fun pushRelease(release: ReleaseResource): ArrResult<WriteOutcome> =
        writeOrQueue(
            body = release,
            online = { releasePushApi.create(release); Unit },
            path = PUSH_PATH,
            description = "Push release ${release.title.orEmpty()}",
        )

    /** Series ids with a queued grab or push, for per-screen queued badges. */
    fun queuedSeriesIds(): Flow<Set<Int>> = actionQueue.observe().map { pending ->
        pending.asSequence()
            .filter { it.serverId == serverId && it.path in GRAB_PATHS }
            .mapNotNull { action ->
                runCatching {
                    json.decodeFromString(ReleaseResource.serializer(), action.bodyJson.orEmpty())
                }.getOrNull()?.seriesId
            }
            .filterNotNull()
            .toSet()
    }

    private suspend fun writeOrQueue(
        body: ReleaseResource,
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
                val actionId = ActionId(UUID.randomUUID().toString())
                actionQueue.enqueue(
                    QueuedAction(
                        actionId = actionId,
                        serverId = serverId,
                        appType = AppType.SONARR,
                        method = "POST",
                        path = path,
                        bodyJson = json.encodeToString(ReleaseResource.serializer(), body),
                        description = description,
                        enqueuedAtMillis = now(),
                    ),
                )
                ArrResult.ok(WriteOutcome.Queued(actionId))
            }

            else -> ArrResult.err(failure)
        }
    }

    private suspend fun pageOf(scope: ReleaseScope): SonarrReleasePage? =
        store.get(serverId, RELEASE_TYPE, scope.cacheKey(), SonarrReleasePage.serializer())

    private fun HttpException.toArrError(): ArrError = when (code()) {
        401, 403 -> ArrError.Auth(code())
        else -> ArrError.Http(code(), message())
    }

    companion object {
        /** Five minutes; indexer results go stale as grabs land. */
        const val RELEASE_TTL_MILLIS: Long = 5 * 60 * 1000

        internal val RELEASE_TYPE = EntityType("sonarr.search.releases")
        internal const val GRAB_PATH = "api/v3/release"
        internal const val PUSH_PATH = "api/v3/release/push"
        internal val GRAB_PATHS = setOf(GRAB_PATH, PUSH_PATH)
    }
}
