package dev.appolon.homelab.arr.feature.radarr

import dev.appolon.homelab.arr.api.radarr.CalendarApi
import dev.appolon.homelab.arr.api.radarr.DiskSpaceApi
import dev.appolon.homelab.arr.api.radarr.HealthApi
import dev.appolon.homelab.arr.api.radarr.QueueDetailsApi
import dev.appolon.homelab.arr.api.radarr.QueueStatusApi
import dev.appolon.homelab.arr.api.radarr.RadarrCalendarApis
import dev.appolon.homelab.arr.api.radarr.RadarrQueueApis
import dev.appolon.homelab.arr.api.radarr.RadarrSystemApis
import dev.appolon.homelab.arr.api.radarr.model.TrackedDownloadState
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
import java.time.Duration
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
internal val RADARR_POLL_SNAPSHOT_TYPE = EntityType("poll.snapshot.radarr")

/**
 * Normalized download lifecycle shared by the diff. Only the transitions
 * the diff keys on survive normalization.
 */
internal enum class RadarrPollDownloadState(val active: Boolean) {
    DOWNLOADING(true),
    IMPORT_BLOCKED(true),
    IMPORT_PENDING(true),
    IMPORTING(true),
    IMPORTED(false),
    FAILED(false),
    IGNORED(false),
}

/** One queue row reduced to what the diff needs. */
internal data class RadarrPollQueueItem(
    val id: Int,
    val title: String,
    val state: RadarrPollDownloadState,
    val errorMessage: String? = null,
)

/** One calendar release inside the fetched window; [at] null when undated. */
internal data class RadarrPollRelease(val id: Int, val title: String, val at: Instant?)

/** One health finding; [key] is stable across polls for the same check. */
internal data class RadarrPollHealthIssue(val key: String, val message: String)

/** One root folder disk. */
internal data class RadarrPollDisk(val path: String, val freeBytes: Long, val totalBytes: Long)

/**
 * All cheap surfaces of one poll, translated off the wire DTOs. Null means
 * the endpoint failed: the diff then carries the previous snapshot's state
 * for that signal instead of reading the failure as "all items vanished".
 */
internal data class RadarrPollSurfaces(
    val queueItems: List<RadarrPollQueueItem>? = null,
    val queueErrors: Boolean? = null,
    val releases: List<RadarrPollRelease>? = null,
    val health: List<RadarrPollHealthIssue>? = null,
    val disks: List<RadarrPollDisk>? = null,
)

/** State persisted between polls; the diff is always previous vs current. */
@Serializable
internal data class RadarrPollSnapshot(
    val queueStates: Map<Int, RadarrPollDownloadState> = emptyMap(),
    val queueErrors: Boolean = false,
    val healthKeys: Set<String> = emptySet(),
    val lowDiskPaths: Set<String> = emptySet(),
)

/** The diff's product: scheduler-ready counts plus the snapshot to persist. */
internal data class RadarrPollDiffOutcome(val result: PollResult, val snapshot: RadarrPollSnapshot)

/**
 * Pure Radarr poll diff. First poll (previous snapshot null) baselines: it
 * persists state and reports nothing, so registering a server never floods
 * the user with notifications for pre-existing queue and health rows. A
 * baseline taken while an endpoint was down starts that signal from empty,
 * so the first successful fetch may report existing rows; that one-poll
 * window beats losing the signal entirely.
 *
 * Heuristic for vanished queue rows: an item last seen IMPORT_PENDING,
 * IMPORTING, or IMPORTED that disappeared completed its import, because
 * Radarr evicts finished rows from the queue; rows last seen DOWNLOADING
 * that vanished were removed by a person or a script and report nothing.
 */
internal object RadarrPollDiff {

    val imminentWindow: Duration = Duration.ofHours(6)

    fun diff(
        previous: RadarrPollSnapshot?,
        surfaces: RadarrPollSurfaces,
        now: Instant,
    ): RadarrPollDiffOutcome {
        if (previous == null) {
            return RadarrPollDiffOutcome(PollResult(), snapshotOf(surfaces, null))
        }

        val items = surfaces.queueItems
        val newItems = items?.filter { it.id !in previous.queueStates }.orEmpty()
        val newGrabs = newItems.size

        val newImports = if (items == null) {
            0
        } else {
            val currentIds = items.mapTo(mutableSetOf()) { it.id }
            val transitioned = items.count {
                it.state == RadarrPollDownloadState.IMPORTED &&
                    previous.queueStates[it.id] != RadarrPollDownloadState.IMPORTED
            }
            val vanished = previous.queueStates.count { (id, state) ->
                id !in currentIds &&
                    (state == RadarrPollDownloadState.IMPORT_PENDING ||
                        state == RadarrPollDownloadState.IMPORTING ||
                        state == RadarrPollDownloadState.IMPORTED)
            }
            transitioned + vanished
        }

        val newFailures = items?.count {
            it.state == RadarrPollDownloadState.FAILED &&
                previous.queueStates[it.id] != RadarrPollDownloadState.FAILED
        } ?: 0
        val queueErrorFlipped = surfaces.queueErrors == true && !previous.queueErrors
        val active = items?.count { it.state.active } ?: 0

        val imminent = surfaces.releases.orEmpty().count { release ->
            val at = release.at ?: return@count false
            !at.isBefore(now) && at.isBefore(now.plus(imminentWindow))
        }

        val newHealth = surfaces.health.orEmpty().filter { it.key !in previous.healthKeys }
        val currentLow = surfaces.disks.orEmpty().filter { it.isLow() }
            .mapTo(mutableSetOf()) { it.path }
        val newLow = currentLow - previous.lowDiskPaths

        val events = buildList {
            if (newGrabs > 0) {
                add(
                    PollEvent(
                        kind = NotificationKind.GRAB,
                        title = "Radarr",
                        body = grabBody(newItems.map { it.title }),
                        dedupeKey = "grabs:" + newItems.joinToString(":") { it.id.toString() },
                    ),
                )
            }
            if (newImports > 0) {
                add(
                    PollEvent(
                        kind = NotificationKind.IMPORT,
                        title = "Radarr",
                        body = if (newImports == 1) {
                            "1 movie finished importing."
                        } else {
                            "$newImports movies finished importing."
                        },
                        dedupeKey = "imports:$newImports:${items?.size ?: 0}",
                    ),
                )
            }
            for (item in items.orEmpty()) {
                if (item.state == RadarrPollDownloadState.FAILED &&
                    previous.queueStates[item.id] != RadarrPollDownloadState.FAILED
                ) {
                    add(
                        PollEvent(
                            kind = NotificationKind.FAIL,
                            title = "Radarr",
                            body = failBody(item.title, item.errorMessage),
                            dedupeKey = "fail:${item.id}",
                        ),
                    )
                }
            }
            if (queueErrorFlipped) {
                add(
                    PollEvent(
                        kind = NotificationKind.FAIL,
                        title = "Radarr",
                        body = "A download is reporting errors.",
                        dedupeKey = "queue-errors",
                    ),
                )
            }
            for (issue in newHealth) {
                add(
                    PollEvent(
                        kind = NotificationKind.HEALTH,
                        title = "Radarr",
                        body = issue.message,
                        dedupeKey = "health:${issue.key}",
                    ),
                )
            }
            for (path in newLow) {
                val disk = surfaces.disks.orEmpty().first { it.path == path }
                add(
                    PollEvent(
                        kind = NotificationKind.DISK,
                        title = "Radarr",
                        body = diskBody(disk),
                        dedupeKey = "disk:$path",
                    ),
                )
            }
        }

        val result = PollResult(
            newGrabs = newGrabs,
            newImports = newImports,
            failures = newFailures + if (queueErrorFlipped) 1 else 0,
            healthIssues = newHealth.size,
            diskWarnings = newLow.size,
            imminentReleases = imminent,
            activeQueueItems = active,
            events = events,
        )
        return RadarrPollDiffOutcome(result, snapshotOf(surfaces, previous))
    }

    /**
     * Failed signals inherit the previous snapshot's view so a dead endpoint
     * reads as "no news", never as "everything disappeared".
     */
    private fun snapshotOf(surfaces: RadarrPollSurfaces, previous: RadarrPollSnapshot?) =
        RadarrPollSnapshot(
            queueStates = surfaces.queueItems?.associate { it.id to it.state }
                ?: previous?.queueStates.orEmpty(),
            queueErrors = surfaces.queueErrors ?: previous?.queueErrors ?: false,
            healthKeys = surfaces.health?.mapTo(mutableSetOf()) { it.key }
                ?: previous?.healthKeys.orEmpty(),
            lowDiskPaths = surfaces.disks?.filter { it.isLow() }?.mapTo(mutableSetOf()) { it.path }
                ?: previous?.lowDiskPaths.orEmpty(),
        )

    /** Warn under 5 percent free; degenerate zero-size rows never warn. */
    internal fun RadarrPollDisk.isLow(): Boolean =
        totalBytes > 0 && freeBytes * 100 < totalBytes * 5

    private fun grabBody(titles: List<String>): String {
        val shown = titles.take(3).joinToString(", ")
        return when {
            titles.size > 3 -> "$shown and ${titles.size - 3} more."
            else -> "$shown sent to the download client."
        }
    }

    private fun failBody(title: String, errorMessage: String?): String {
        val reason = errorMessage?.take(120)?.let { ": $it" }.orEmpty()
        return "Download failed for $title$reason"
    }

    private fun diskBody(disk: RadarrPollDisk): String {
        val percent = disk.freeBytes * 100 / disk.totalBytes
        return "${disk.path} has $percent% free."
    }
}

/**
 * Parses the wire date forms Radarr emits: full ISO instants and bare local
 * dates. Unparseable dates stay null so they simply never count as imminent.
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
class RadarrPollApis(
    val queueStatusApi: QueueStatusApi,
    val queueDetailsApi: QueueDetailsApi,
    val calendarApi: CalendarApi,
    val healthApi: HealthApi,
    val diskSpaceApi: DiskSpaceApi,
)

private fun dev.appolon.homelab.arr.api.radarr.model.QueueResource.toPollItem() =
    RadarrPollQueueItem(
        id = id,
        title = title?.takeIf { it.isNotBlank() }
            ?: movie.title?.takeIf { it.isNotBlank() }
            ?: "#$id",
        state = when (trackedDownloadState) {
            TrackedDownloadState.DOWNLOADING -> RadarrPollDownloadState.DOWNLOADING
            TrackedDownloadState.IMPORT_BLOCKED -> RadarrPollDownloadState.IMPORT_BLOCKED
            TrackedDownloadState.IMPORT_PENDING -> RadarrPollDownloadState.IMPORT_PENDING
            TrackedDownloadState.IMPORTING -> RadarrPollDownloadState.IMPORTING
            TrackedDownloadState.IMPORTED -> RadarrPollDownloadState.IMPORTED
            TrackedDownloadState.FAILED, TrackedDownloadState.FAILED_PENDING ->
                RadarrPollDownloadState.FAILED
            TrackedDownloadState.IGNORED -> RadarrPollDownloadState.IGNORED
        },
        errorMessage = errorMessage,
    )

private fun dev.appolon.homelab.arr.api.radarr.model.MovieResource.toPollRelease() =
    RadarrPollRelease(
        id = id,
        title = title?.takeIf { it.isNotBlank() } ?: "#$id",
        // The earliest concrete date drives imminence, matching the calendar
        // fake's window filter over the same fields.
        at = listOf(inCinemas, digitalRelease, physicalRelease)
            .mapNotNull { parsePollDate(it) }
            .minOrNull(),
    )

private fun dev.appolon.homelab.arr.api.radarr.model.HealthResource.toPollIssue() =
    RadarrPollHealthIssue(
        key = "$id:${source.orEmpty()}:${type?.name.orEmpty()}",
        message = message?.takeIf { it.isNotBlank() }
            ?: source?.takeIf { it.isNotBlank() }
            ?: "Health check ${type?.name?.lowercase() ?: "issue"}.",
    )

private fun dev.appolon.homelab.arr.api.radarr.model.DiskSpaceResource.toPollDisk() =
    RadarrPollDisk(path = path.orEmpty(), freeBytes = freeSpace, totalBytes = totalSpace)

/**
 * [PollAdapter] for Radarr. Fetches the cheap status surfaces (queue status
 * and details, a 24 hour calendar window, health, disk space), diffs them
 * against the previous poll's snapshot in the entity store, and returns
 * scheduler-ready counts and events. Any single endpoint failing degrades
 * only its signal: the failed surface is treated as unknown, its previous
 * state carries forward, and [PollResult.error] is set.
 */
class RadarrPollAdapter(
    private val secrets: ArrSecrets,
    private val store: EntityStore,
    private val clientFactory: ArrClientFactory = DefaultArrClientFactory(),
    private val clock: Clock = Clock.systemUTC(),
    private val apis: suspend (ArrServer) -> RadarrPollApis? = { server ->
        buildApis(server, secrets, clientFactory)
    },
) : PollAdapter {

    override suspend fun poll(server: ArrServer): PollResult {
        if (server.type != AppType.RADARR) {
            return PollResult(
                error = ArrError.Unknown(
                    IllegalStateException("RadarrPollAdapter cannot poll a ${server.type} server"),
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

        val queueStatus = signal(::degrade) { bundle.queueStatusApi.queueStatus() }
        val queueDetails = signal(::degrade) { bundle.queueDetailsApi.queueDetails() }
        val now = clock.instant()
        val calendar = signal(::degrade) {
            bundle.calendarApi.calendar(
                start = now.toString(),
                end = now.plus(CALENDAR_WINDOW).toString(),
            )
        }
        val health = signal(::degrade) { bundle.healthApi.health() }
        val disks = signal(::degrade) { bundle.diskSpaceApi.diskSpace() }

        val surfaces = RadarrPollSurfaces(
            queueItems = queueDetails?.map { it.toPollItem() },
            queueErrors = queueStatus?.let { it.errors || it.unknownErrors },
            releases = calendar?.map { it.toPollRelease() },
            health = health?.map { it.toPollIssue() },
            disks = disks?.map { it.toPollDisk() },
        )

        val previous = try {
            store.get(server.id, RADARR_POLL_SNAPSHOT_TYPE, SNAPSHOT_ID, RadarrPollSnapshot.serializer())
        } catch (_: Throwable) {
            // An unreadable snapshot behaves like a first poll: baseline
            // silently rather than failing a poll over local storage.
            null
        }
        val outcome = RadarrPollDiff.diff(previous, surfaces, now)
        try {
            store.put(
                server.id,
                RADARR_POLL_SNAPSHOT_TYPE,
                SNAPSHOT_ID,
                outcome.snapshot,
                RadarrPollSnapshot.serializer(),
            )
        } catch (t: Throwable) {
            // Counts stay valid; the poll is degraded because the next one
            // will re-baseline and could re-notify.
            degrade(t)
        }
        return outcome.result.copy(error = firstError ?: outcome.result.error)
    }

    private companion object {
        val CALENDAR_WINDOW: Duration = Duration.ofHours(24)
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
): RadarrPollApis? {
    val endpoint = endpointOf(server, secrets) ?: return null
    val retrofit = Retrofit.Builder()
        .baseUrl(ServerEndpoint.normalizeBaseUrl(server.baseUrl))
        .client(clientFactory.clientFor(endpoint))
        .addConverterFactory(ArrJson.asConverterFactory("application/json".toMediaType()))
        .build()
    return RadarrPollApis(
        queueStatusApi = RadarrQueueApis.queueStatus(retrofit),
        queueDetailsApi = RadarrQueueApis.queueDetails(retrofit),
        calendarApi = RadarrCalendarApis.calendar(retrofit),
        healthApi = RadarrSystemApis.health(retrofit),
        diskSpaceApi = RadarrSystemApis.diskSpace(retrofit),
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
