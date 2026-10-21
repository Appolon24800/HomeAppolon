package dev.appolon.homelab.arr.feature.prowlarr

import dev.appolon.homelab.arr.api.prowlarr.HealthApi
import dev.appolon.homelab.arr.api.prowlarr.IndexerStatusApi
import dev.appolon.homelab.arr.api.prowlarr.ProwlarrIndexersApis
import dev.appolon.homelab.arr.api.prowlarr.ProwlarrSystemApis
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
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import okhttp3.MediaType.Companion.toMediaType
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

/** Entity type holding this app's last poll snapshot, one "latest" row per server. */
internal val PROWLARR_POLL_SNAPSHOT_TYPE = EntityType("poll.snapshot.prowlarr")

/** One health finding; [key] is stable across polls for the same check. */
internal data class ProwlarrPollHealthIssue(val key: String, val message: String)

/**
 * One indexer's availability. An indexer counts as unavailable when it has
 * failed at least once and is either in cooldown (disabledTill in the
 * future) or carries a failure with no cooldown timestamp at all; an old
 * failure whose cooldown expired counts as recovered.
 */
internal data class ProwlarrPollIndexer(
    val indexerId: Int,
    val failed: Boolean,
    val disabledTill: String?,
)

/** All cheap surfaces of one poll, translated off the wire DTOs. */
internal data class ProwlarrPollSurfaces(
    val health: List<ProwlarrPollHealthIssue> = emptyList(),
    val indexers: List<ProwlarrPollIndexer> = emptyList(),
)

/** State persisted between polls; the diff is always previous vs current. */
@Serializable
internal data class ProwlarrPollSnapshot(
    val healthKeys: Set<String> = emptySet(),
    val unavailableIndexers: Set<Int> = emptySet(),
)

/** The diff's product: scheduler-ready counts plus the snapshot to persist. */
internal data class ProwlarrPollDiffOutcome(val result: PollResult, val snapshot: ProwlarrPollSnapshot)

/**
 * Pure Prowlarr poll diff. Prowlarr has no download queue, calendar, or disk
 * endpoint in the committed API surface, so the notifiable signals are new
 * health findings and indexers slipping into failure. First poll (previous
 * snapshot null) baselines: it persists state and reports nothing, so
 * registering a server never floods the user with notifications for
 * pre-existing rows.
 */
internal object ProwlarrPollDiff {

    fun diff(
        previous: ProwlarrPollSnapshot?,
        surfaces: ProwlarrPollSurfaces,
    ): ProwlarrPollDiffOutcome {
        if (previous == null) {
            return ProwlarrPollDiffOutcome(PollResult(), snapshotOf(surfaces))
        }

        val newHealth = surfaces.health.filter { it.key !in previous.healthKeys }
        val failing = surfaces.indexers.filter { it.failed }.mapTo(mutableSetOf()) { it.indexerId }
        val newlyFailing = surfaces.indexers.filter { it.failed && it.indexerId !in previous.unavailableIndexers }

        val events = buildList {
            for (issue in newHealth) {
                add(
                    PollEvent(
                        kind = NotificationKind.HEALTH,
                        title = "Prowlarr",
                        body = issue.message,
                        dedupeKey = "health:${issue.key}",
                    ),
                )
            }
            for (indexer in newlyFailing) {
                add(
                    PollEvent(
                        kind = NotificationKind.FAIL,
                        title = "Prowlarr",
                        body = indexerBody(indexer),
                        dedupeKey = "indexer:${indexer.indexerId}",
                    ),
                )
            }
        }

        val result = PollResult(
            failures = newlyFailing.size,
            healthIssues = newHealth.size,
            events = events,
        )
        return ProwlarrPollDiffOutcome(result, snapshotOf(surfaces))
    }

    private fun snapshotOf(surfaces: ProwlarrPollSurfaces) = ProwlarrPollSnapshot(
        healthKeys = surfaces.health.mapTo(mutableSetOf()) { it.key },
        unavailableIndexers = surfaces.indexers
            .filter { it.failed }
            .mapTo(mutableSetOf()) { it.indexerId },
    )

    private fun indexerBody(indexer: ProwlarrPollIndexer): String {
        val till = indexer.disabledTill?.let { " until $it" }.orEmpty()
        return "Indexer #${indexer.indexerId} is unavailable$till."
    }
}

/**
 * Parses the wire date forms the arr stack emits. Unparseable dates return
 * null so callers decide what "unknown" means for their signal.
 */
internal fun parsePollDate(raw: String?): Instant? {
    if (raw.isNullOrBlank()) return null
    return try {
        Instant.parse(raw)
    } catch (_: Exception) {
        try {
            OffsetDateTime.parse(raw).toInstant()
        } catch (_: Exception) {
            try {
                LocalDateTime.parse(raw).toInstant(ZoneOffset.UTC)
            } catch (_: Exception) {
                try {
                    LocalDate.parse(raw).atStartOfDay(ZoneOffset.UTC).toInstant()
                } catch (_: Exception) {
                    null
                }
            }
        }
    }
}

/** Per-server API bundle the adapter polls, mirroring the feature's Connections. */
class ProwlarrPollApis(
    val healthApi: HealthApi,
    val indexerStatusApi: IndexerStatusApi,
)

private fun dev.appolon.homelab.arr.api.prowlarr.model.HealthResource.toPollIssue() =
    ProwlarrPollHealthIssue(
        key = "$id:${source.orEmpty()}:${type?.name.orEmpty()}",
        message = message?.takeIf { it.isNotBlank() }
            ?: source?.takeIf { it.isNotBlank() }
            ?: "Health check ${type?.name?.lowercase() ?: "issue"}.",
    )

private fun dev.appolon.homelab.arr.api.prowlarr.model.IndexerStatusResource.toPollIndexer(now: Instant) =
    ProwlarrPollIndexer(
        indexerId = indexerId,
        // A cooldown in the future means currently throttled; a null
        // cooldown with a recorded failure is treated as still failing
        // because the server gave us nothing better to go on.
        failed = mostRecentFailure != null && when (val till = parsePollDate(disabledTill)) {
            null -> true
            else -> till.isAfter(now)
        },
        disabledTill = disabledTill,
    )

/**
 * [PollAdapter] for Prowlarr. Prowlarr publishes no queue, calendar, or disk
 * endpoints in its committed API surface, so the poll watches the two cheap
 * signals that exist: system health and per-indexer failure state. Any
 * single endpoint failing degrades only its signal: the poll still returns
 * the other's counts with [PollResult.error] set.
 */
class ProwlarrPollAdapter(
    private val secrets: ArrSecrets,
    private val store: EntityStore,
    private val clientFactory: ArrClientFactory = DefaultArrClientFactory(),
    private val clock: Clock = Clock.systemUTC(),
    private val apis: suspend (ArrServer) -> ProwlarrPollApis? = { server ->
        buildApis(server, secrets, clientFactory)
    },
) : PollAdapter {

    override suspend fun poll(server: ArrServer): PollResult {
        if (server.type != AppType.PROWLARR) {
            return PollResult(
                error = ArrError.Unknown(
                    IllegalStateException("ProwlarrPollAdapter cannot poll a ${server.type} server"),
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

        val now = clock.instant()
        val health = signal(::degrade) { bundle.healthApi.getHealth() }
        val indexerStatuses = signal(::degrade) { bundle.indexerStatusApi.getIndexerStatuses() }

        val surfaces = ProwlarrPollSurfaces(
            health = health.orEmpty().map { it.toPollIssue() },
            indexers = indexerStatuses.orEmpty().map { it.toPollIndexer(now) },
        )

        val previous = try {
            store.get(server.id, PROWLARR_POLL_SNAPSHOT_TYPE, SNAPSHOT_ID, ProwlarrPollSnapshot.serializer())
        } catch (_: Throwable) {
            // An unreadable snapshot behaves like a first poll: baseline
            // silently rather than failing a poll over local storage.
            null
        }
        val outcome = ProwlarrPollDiff.diff(previous, surfaces)
        try {
            store.put(
                server.id,
                PROWLARR_POLL_SNAPSHOT_TYPE,
                SNAPSHOT_ID,
                outcome.snapshot,
                ProwlarrPollSnapshot.serializer(),
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
): ProwlarrPollApis? {
    val endpoint = endpointOf(server, secrets) ?: return null
    val retrofit = Retrofit.Builder()
        .baseUrl(ServerEndpoint.normalizeBaseUrl(server.baseUrl))
        .client(clientFactory.clientFor(endpoint))
        .addConverterFactory(ArrJson.asConverterFactory("application/json".toMediaType()))
        .build()
    return ProwlarrPollApis(
        healthApi = ProwlarrSystemApis.health(retrofit),
        indexerStatusApi = ProwlarrIndexersApis.indexerStatus(retrofit),
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
