package dev.appolon.homelab.arr.feature.bazarr

import dev.appolon.homelab.arr.api.bazarr.BadgesApi
import dev.appolon.homelab.arr.api.bazarr.BazarrLibraryApis
import dev.appolon.homelab.arr.api.bazarr.BazarrSystemApis
import dev.appolon.homelab.arr.api.bazarr.SystemHealthApi
import dev.appolon.homelab.arr.core.AppType
import dev.appolon.homelab.arr.core.ArrError
import dev.appolon.homelab.arr.core.EntityType
import dev.appolon.homelab.arr.data.ArrServer
import dev.appolon.homelab.arr.data.EntityStore
import dev.appolon.homelab.arr.data.secrets.ArrSecrets
import dev.appolon.homelab.arr.net.ArrClientFactory
import dev.appolon.homelab.arr.net.ArrJson
import dev.appolon.homelab.arr.net.DefaultArrClientFactory
import dev.appolon.homelab.arr.net.ServerEndpoint
import dev.appolon.homelab.arr.net.TrustSpec
import dev.appolon.homelab.arr.sync.scheduler.NotificationKind
import dev.appolon.homelab.arr.sync.scheduler.PollAdapter
import dev.appolon.homelab.arr.sync.scheduler.PollEvent
import dev.appolon.homelab.arr.sync.scheduler.PollResult
import java.io.IOException
import java.time.Clock
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

/** Entity type holding this app's last poll snapshot, one "latest" row per server. */
internal val BAZARR_POLL_SNAPSHOT_TYPE = EntityType("poll.snapshot.bazarr")

/**
 * One health finding. Bazarr's health payload is undocumented in the bundled
 * spec, so issues are recognized structurally: any JSON object carrying a
 * "message" string counts, wherever it sits in the payload.
 */
internal data class BazarrPollHealthIssue(
    val key: String,
    val message: String,
    val isError: Boolean,
)

/** Wanted-subtitle badge counts; the badge payload's only stable fields. */
internal data class BazarrPollBadges(
    val episodesMissing: Int = 0,
    val moviesMissing: Int = 0,
)

/**
 * All cheap surfaces of one poll, translated off the wire DTOs. Null badges
 * mean the endpoint failed: the diff keeps the previous counts so an outage
 * cannot masquerade as subtitles found.
 */
internal data class BazarrPollSurfaces(
    val health: List<BazarrPollHealthIssue> = emptyList(),
    val badges: BazarrPollBadges? = null,
)

/** State persisted between polls; the diff is always previous vs current. */
@Serializable
internal data class BazarrPollSnapshot(
    val healthKeys: Set<String> = emptySet(),
    val missingEpisodes: Int = 0,
    val missingMovies: Int = 0,
)

/** The diff's product: scheduler-ready counts plus the snapshot to persist. */
internal data class BazarrPollDiffOutcome(val result: PollResult, val snapshot: BazarrPollSnapshot)

/**
 * Pure Bazarr poll diff. Bazarr has no download queue; its progress signal is
 * the badge counts of episodes and movies still missing subtitles. A drop in
 * that total means subtitles were found since the last poll and reports as an
 * import; an increase reports nothing because no notification channel fits
 * "more items are waiting" and the count is visible on the badges UI. First
 * poll (previous snapshot null) baselines: it persists state and reports
 * nothing, so registering a server never floods the user with notifications
 * for pre-existing health rows.
 */
internal object BazarrPollDiff {

    fun diff(
        previous: BazarrPollSnapshot?,
        surfaces: BazarrPollSurfaces,
    ): BazarrPollDiffOutcome {
        if (previous == null) {
            return BazarrPollDiffOutcome(
                PollResult(),
                snapshotOf(surfaces, surfaces.badges ?: BazarrPollBadges()),
            )
        }

        val newHealth = surfaces.health.filter { it.key !in previous.healthKeys }
        val newErrors = newHealth.filter { it.isError }

        // Unknown badges inherit the previous counts so a failed endpoint
        // reads as "no progress", never as "everything found".
        val currentBadges = surfaces.badges
            ?: BazarrPollBadges(previous.missingEpisodes, previous.missingMovies)
        val previousMissing = previous.missingEpisodes + previous.missingMovies
        val currentMissing = currentBadges.episodesMissing + currentBadges.moviesMissing
        val found = (previousMissing - currentMissing).coerceAtLeast(0)

        val events = buildList {
            for (issue in newHealth) {
                add(
                    PollEvent(
                        kind = if (issue.isError) NotificationKind.FAIL else NotificationKind.HEALTH,
                        title = "Bazarr",
                        body = issue.message,
                        dedupeKey = "health:${issue.key}",
                    ),
                )
            }
            if (found > 0) {
                add(
                    PollEvent(
                        kind = NotificationKind.IMPORT,
                        title = "Bazarr",
                        body = if (found == 1) {
                            "Subtitles were found for 1 item."
                        } else {
                            "Subtitles were found for $found items."
                        },
                        dedupeKey = "found:$previousMissing:$currentMissing",
                    ),
                )
            }
        }

        val result = PollResult(
            newImports = found,
            failures = newErrors.size,
            healthIssues = newHealth.size,
            events = events,
        )
        return BazarrPollDiffOutcome(result, snapshotOf(surfaces, currentBadges))
    }

    private fun snapshotOf(
        surfaces: BazarrPollSurfaces,
        badges: BazarrPollBadges,
    ) = BazarrPollSnapshot(
        healthKeys = surfaces.health.mapTo(mutableSetOf()) { it.key },
        missingEpisodes = badges.episodesMissing,
        missingMovies = badges.moviesMissing,
    )
}

/**
 * Reads one count out of a badges payload like
 * `{"episodes": {"missing": 2, "wanted": 1}, "movies": {...}}`. Every level
 * is optional on the wire, so anything absent reads as zero.
 */
private fun JsonObject.countOf(group: String, field: String): Int {
    val child = this[group] as? JsonObject ?: return 0
    val value = child[field] ?: return 0
    return try {
        value.jsonPrimitive.content.toIntOrNull() ?: 0
    } catch (_: Exception) {
        0
    }
}

/** Structural issue extraction from the undocumented health payload. */
private fun JsonObject.toPollIssues(): List<BazarrPollHealthIssue> {
    val found = mutableListOf<BazarrPollHealthIssue>()
    for ((member, value) in this) {
        when (value) {
            is JsonObject -> {
                val issue = value.toIssue("$member")
                if (issue != null) found += issue
            }
            is JsonArray -> {
                value.forEachIndexed { index, element ->
                    if (element is JsonObject) {
                        val issue = element.toIssue("$member:$index")
                        if (issue != null) found += issue
                    }
                }
            }
            else -> Unit
        }
    }
    return found
}

private fun JsonObject.toIssue(keyPrefix: String): BazarrPollHealthIssue? {
    val message = try {
        this["message"]?.jsonPrimitive?.content
    } catch (_: Exception) {
        null
    }
    val text = message?.takeIf { it.isNotBlank() } ?: return null
    val type = try {
        this["type"]?.jsonPrimitive?.content
    } catch (_: Exception) {
        null
    }
    return BazarrPollHealthIssue(
        key = "$keyPrefix:$text",
        message = text,
        isError = type.equals("error", ignoreCase = true),
    )
}

/** Per-server API bundle the adapter polls, mirroring the feature's Connections. */
class BazarrPollApis(
    val systemHealthApi: SystemHealthApi,
    val badgesApi: BadgesApi,
)

/**
 * [PollAdapter] for Bazarr. Polls the two cheap surfaces Bazarr offers,
 * system health and the wanted-subtitle badge counts, and diffs them against
 * the previous poll's snapshot in the entity store. Any single endpoint
 * failing degrades only its signal: the failed surface is treated as
 * unknown, its previous state carries forward, and [PollResult.error] is set.
 */
class BazarrPollAdapter(
    private val secrets: ArrSecrets,
    private val store: EntityStore,
    private val clientFactory: ArrClientFactory = DefaultArrClientFactory(),
    private val clock: Clock = Clock.systemUTC(),
    private val apis: suspend (ArrServer) -> BazarrPollApis? = { server ->
        buildApis(server, secrets, clientFactory)
    },
) : PollAdapter {

    override suspend fun poll(server: ArrServer): PollResult {
        if (server.type != AppType.BAZARR) {
            return PollResult(
                error = ArrError.Unknown(
                    IllegalStateException("BazarrPollAdapter cannot poll a ${server.type} server"),
                ),
            )
        }
        val bundle = try {
            apis(server)
        } catch (t: Throwable) {
            return PollResult(error = t.toArrError())
        } ?: return PollResult(
            error = ArrError.Unknown(
                IllegalStateException("no clients for server ${server.id.value}"),
            ),
        )

        var firstError: ArrError? = null
        fun degrade(t: Throwable) {
            if (firstError == null) firstError = t.toArrError()
        }

        val health = signal(::degrade) { bundle.systemHealthApi.getSystemHealth() }
        val badges = signal(::degrade) { bundle.badgesApi.getBadges() }

        val surfaces = BazarrPollSurfaces(
            health = health?.toPollIssues().orEmpty(),
            badges = badges?.let { json ->
                BazarrPollBadges(
                    episodesMissing = json.countOf("episodes", "missing"),
                    moviesMissing = json.countOf("movies", "missing"),
                )
            },
        )

        val previous = try {
            store.get(server.id, BAZARR_POLL_SNAPSHOT_TYPE, SNAPSHOT_ID, BazarrPollSnapshot.serializer())
        } catch (_: Throwable) {
            // An unreadable snapshot behaves like a first poll: baseline
            // silently rather than failing a poll over local storage.
            null
        }
        val outcome = BazarrPollDiff.diff(previous, surfaces)
        try {
            store.put(
                server.id,
                BAZARR_POLL_SNAPSHOT_TYPE,
                SNAPSHOT_ID,
                outcome.snapshot,
                BazarrPollSnapshot.serializer(),
            )
        } catch (t: Throwable) {
            // Counts stay valid; the poll is degraded because the next one
            // will re-baseline and could re-notify.
            degrade(t)
        }
        return outcome.result.copy(error = firstError ?: outcome.result.error)
    }

    private companion object {
        const val SNAPSHOT_ID = "latest"
    }
}

/**
 * Builds the Retrofit stack exactly like the feature's Connection
 * classes: per-server OkHttp from [DefaultArrClientFactory], shared
 * [ArrJson] converter, base URL normalized with its path prefix. Top-level
 * so the constructor default for the api provider can call it before the
 * adapter instance exists.
 */
private suspend fun buildApis(
    server: ArrServer,
    secrets: ArrSecrets,
    clientFactory: ArrClientFactory,
): BazarrPollApis? {
    val endpoint = endpointOf(server, secrets) ?: return null
    val retrofit = Retrofit.Builder()
        .baseUrl(ServerEndpoint.normalizeBaseUrl(server.baseUrl))
        .client(clientFactory.clientFor(endpoint))
        .addConverterFactory(ArrJson.asConverterFactory("application/json".toMediaType()))
        .build()
    return BazarrPollApis(
        systemHealthApi = BazarrSystemApis.health(retrofit),
        badgesApi = BazarrLibraryApis.badges(retrofit),
    )
}

private fun endpointOf(server: ArrServer, secrets: ArrSecrets): ServerEndpoint? = try {
    ServerEndpoint(
        serverId = server.id,
        baseUrl = server.baseUrl,
        apiKey = secrets.decrypt(server.apiKeyEncrypted),
        basicAuthUser = server.basicAuthUser,
        basicAuthPassword = server.basicAuthPasswordEncrypted?.let { secrets.decrypt(it) },
        proxy = server.proxy,
        trust = server.trust ?: TrustSpec.System,
    )
} catch (_: Throwable) {
    null
}

/** Runs one endpoint call, mapping any failure to [ArrError] for the caller. */
internal suspend fun <T> signal(degrade: (Throwable) -> Unit, block: suspend () -> T): T? = try {
    block()
} catch (t: Throwable) {
    if (t is kotlinx.coroutines.CancellationException) throw t
    degrade(t)
    null
}

internal fun Throwable.toArrError(): ArrError = when (this) {
    is HttpException -> when (code()) {
        in 401..403 -> ArrError.Auth(code())
        else -> ArrError.Http(code(), message()?.take(120))
    }
    is IOException -> ArrError.Network(this)
    is SerializationException -> ArrError.Parse(this)
    else -> ArrError.Unknown(this)
}
