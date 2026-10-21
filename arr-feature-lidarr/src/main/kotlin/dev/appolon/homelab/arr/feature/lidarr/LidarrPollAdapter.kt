package dev.appolon.homelab.arr.feature.lidarr

import dev.appolon.homelab.arr.api.lidarr.CalendarApi
import dev.appolon.homelab.arr.api.lidarr.DiskSpaceApi
import dev.appolon.homelab.arr.api.lidarr.HealthApi
import dev.appolon.homelab.arr.api.lidarr.LidarrCalendarApis
import dev.appolon.homelab.arr.api.lidarr.LidarrQueueApis
import dev.appolon.homelab.arr.api.lidarr.LidarrSystemApis
import dev.appolon.homelab.arr.api.lidarr.QueueDetailsApi
import dev.appolon.homelab.arr.api.lidarr.QueueStatusApi
import dev.appolon.homelab.arr.api.lidarr.model.TrackedDownloadState
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
internal val LIDARR_POLL_SNAPSHOT_TYPE = EntityType("poll.snapshot.lidarr")

/**
 * Normalized download lifecycle shared by the diff. Lidarr splits failure
 * states between download and import; both collapse into [FAILED] because
 * the notification only needs "this item failed", not which half failed.
 */
internal enum class LidarrPollDownloadState(val active: Boolean) {
    DOWNLOADING(true),
    IMPORT_BLOCKED(true),
    IMPORT_PENDING(true),
    IMPORTING(true),
    IMPORTED(false),
    FAILED(false),
    IGNORED(false),
}

/** One queue row reduced to what the diff needs. */
internal data class LidarrPollQueueItem(
    val id: Int,
    val title: String,
    val state: LidarrPollDownloadState,
    val errorMessage: String? = null,
)

/** One calendar album inside the fetched window; [at] null when undated. */
internal data class LidarrPollRelease(val id: Int, val title: String, val at: Instant?)

/** One health finding; [key] is stable across polls for the same check. */
internal data class LidarrPollHealthIssue(val key: String, val message: String)

/** One root folder disk. */
internal data class LidarrPollDisk(val path: String, val freeBytes: Long, val totalBytes: Long)

/** All cheap surfaces of one poll, translated off the wire DTOs. */
internal data class LidarrPollSurfaces(
    val queueItems: List<LidarrPollQueueItem> = emptyList(),
    /** True when queue/status reports any tracked download in error. */
    val queueErrors: Boolean = false,
    val releases: List<LidarrPollRelease> = emptyList(),
    val health: List<LidarrPollHealthIssue> = emptyList(),
    val disks: List<LidarrPollDisk> = emptyList(),
)

/** State persisted between polls; the diff is always previous vs current. */
@Serializable
internal data class LidarrPollSnapshot(
    val queueStates: Map<Int, LidarrPollDownloadState> = emptyMap(),
    val queueErrors: Boolean = false,
    val healthKeys: Set<String> = emptySet(),
    val lowDiskPaths: Set<String> = emptySet(),
)

/** The diff's product: scheduler-ready counts plus the snapshot to persist. */
internal data class LidarrPollDiffOutcome(val result: PollResult, val snapshot: LidarrPollSnapshot)

/**
 * Pure Lidarr poll diff. First poll (previous snapshot null) baselines: it
 * persists state and reports nothing, so registering a server never floods
 * the user with notifications for pre-existing queue and health rows.
 *
 * Heuristic for vanished queue rows: an item last seen IMPORT_PENDING or
 * IMPORTING that disappeared completed its import, because Lidarr evicts
 * finished rows from the queue; rows last seen DOWNLOADING that vanished
 * were removed by a person or a script and report nothing.
 */
internal object LidarrPollDiff {

    val imminentWindow: Duration = Duration.ofHours(6)

    fun diff(
        previous: LidarrPollSnapshot?,
        surfaces: LidarrPollSurfaces,
        now: Instant,
    ): LidarrPollDiffOutcome {
        if (previous == null) {
            return LidarrPollDiffOutcome(PollResult(), snapshotOf(surfaces))
        }

        val currentIds = surfaces.queueItems.mapTo(mutableSetOf()) { it.id }
        val newItems = surfaces.queueItems.filter { it.id !in previous.queueStates }
        val newGrabs = newItems.size

        val transitionedImports = surfaces.queueItems.count {
            it.state == LidarrPollDownloadState.IMPORTED &&
                previous.queueStates[it.id] != LidarrPollDownloadState.IMPORTED
        }
        val vanishedImports = previous.queueStates.count { (id, state) ->
            id !in currentIds &&
                (state == LidarrPollDownloadState.IMPORT_PENDING ||
                    state == LidarrPollDownloadState.IMPORTING ||
                    state == LidarrPollDownloadState.IMPORTED)
        }
        val newImports = transitionedImports + vanishedImports

        val failures = surfaces.queueItems.count {
            it.state == LidarrPollDownloadState.FAILED &&
                previous.queueStates[it.id] != LidarrPollDownloadState.FAILED
        }
        val queueErrorFlipped = surfaces.queueErrors && !previous.queueErrors

        val imminent = surfaces.releases.count { release ->
            val at = release.at ?: return@count false
            !at.isBefore(now) && at.isBefore(now.plus(imminentWindow))
        }
        val active = surfaces.queueItems.count { it.state.active }

        val newHealth = surfaces.health.filter { it.key !in previous.healthKeys }
        val currentLow = surfaces.disks.filter { it.isLow() }.mapTo(mutableSetOf()) { it.path }
        val newLow = currentLow - previous.lowDiskPaths

        val events = buildList {
            if (newGrabs > 0) {
                add(
                    PollEvent(
                        kind = NotificationKind.GRAB,
                        title = "Lidarr",
                        body = grabBody(newItems.map { it.title }),
                        dedupeKey = "grabs:" + newItems.joinToString(":") { it.id.toString() },
                    ),
                )
            }
            if (newImports > 0) {
                add(
                    PollEvent(
                        kind = NotificationKind.IMPORT,
                        title = "Lidarr",
                        body = if (newImports == 1) {
                            "1 album finished importing."
                        } else {
                            "$newImports albums finished importing."
                        },
                        dedupeKey = "imports:$newImports:${surfaces.queueItems.size}",
                    ),
                )
            }
            for (item in surfaces.queueItems) {
                if (item.state == LidarrPollDownloadState.FAILED &&
                    previous.queueStates[item.id] != LidarrPollDownloadState.FAILED
                ) {
                    add(
                        PollEvent(
                            kind = NotificationKind.FAIL,
                            title = "Lidarr",
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
                        title = "Lidarr",
                        body = "A download is reporting errors.",
                        dedupeKey = "queue-errors",
                    ),
                )
            }
            for (issue in newHealth) {
                add(
                    PollEvent(
                        kind = NotificationKind.HEALTH,
                        title = "Lidarr",
                        body = issue.message,
                        dedupeKey = "health:${issue.key}",
                    ),
                )
            }
            for (path in newLow) {
                val disk = surfaces.disks.first { it.path == path }
                add(
                    PollEvent(
                        kind = NotificationKind.DISK,
                        title = "Lidarr",
                        body = diskBody(disk),
                        dedupeKey = "disk:$path",
                    ),
                )
            }
        }

        val result = PollResult(
            newGrabs = newGrabs,
            newImports = newImports,
            failures = failures + if (queueErrorFlipped) 1 else 0,
            healthIssues = newHealth.size,
            diskWarnings = newLow.size,
            imminentReleases = imminent,
            activeQueueItems = active,
            events = events,
        )
        return LidarrPollDiffOutcome(result, snapshotOf(surfaces))
    }

    private fun snapshotOf(surfaces: LidarrPollSurfaces) = LidarrPollSnapshot(
        queueStates = surfaces.queueItems.associate { it.id to it.state },
        queueErrors = surfaces.queueErrors,
        healthKeys = surfaces.health.mapTo(mutableSetOf()) { it.key },
        lowDiskPaths = surfaces.disks.filter { it.isLow() }.mapTo(mutableSetOf()) { it.path },
    )

    /** Warn under 5 percent free; degenerate zero-size rows never warn. */
    internal fun LidarrPollDisk.isLow(): Boolean =
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

    private fun diskBody(disk: LidarrPollDisk): String {
        val percent = disk.freeBytes * 100 / disk.totalBytes
        return "${disk.path} has $percent% free."
    }
}

/**
 * Parses the wire date forms Lidarr emits. Lidarr stamps far-future or
 * unknown release dates as 0001-01-01; those parse fine but always land
 * outside the imminent window, so they need no special case.
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
class LidarrPollApis(
    val queueStatusApi: QueueStatusApi,
    val queueDetailsApi: QueueDetailsApi,
    val calendarApi: CalendarApi,
    val healthApi: HealthApi,
    val diskSpaceApi: DiskSpaceApi,
)

private fun dev.appolon.homelab.arr.api.lidarr.model.QueueResource.toPollItem() =
    LidarrPollQueueItem(
        id = id,
        title = title?.takeIf { it.isNotBlank() }
            ?: album?.title?.takeIf { it.isNotBlank() }
            ?: artist?.artistName?.takeIf { it.isNotBlank() }
            ?: "#$id",
        state = when (trackedDownloadState) {
            TrackedDownloadState.DOWNLOADING -> LidarrPollDownloadState.DOWNLOADING
            TrackedDownloadState.IMPORT_BLOCKED -> LidarrPollDownloadState.IMPORT_BLOCKED
            TrackedDownloadState.IMPORT_PENDING -> LidarrPollDownloadState.IMPORT_PENDING
            TrackedDownloadState.IMPORTING -> LidarrPollDownloadState.IMPORTING
            TrackedDownloadState.IMPORTED -> LidarrPollDownloadState.IMPORTED
            TrackedDownloadState.DOWNLOAD_FAILED,
            TrackedDownloadState.DOWNLOAD_FAILED_PENDING,
            TrackedDownloadState.IMPORT_FAILED,
            -> LidarrPollDownloadState.FAILED
            TrackedDownloadState.IGNORED -> LidarrPollDownloadState.IGNORED
        },
        errorMessage = errorMessage,
    )

private fun dev.appolon.homelab.arr.api.lidarr.model.AlbumResource.toPollRelease() =
    LidarrPollRelease(
        id = id,
        title = title?.takeIf { it.isNotBlank() } ?: "#$id",
        at = parsePollDate(releaseDate),
    )

private fun dev.appolon.homelab.arr.api.lidarr.model.HealthResource.toPollIssue() =
    LidarrPollHealthIssue(
        key = "$id:${source.orEmpty()}:${type?.name.orEmpty()}",
        message = message?.takeIf { it.isNotBlank() }
            ?: source?.takeIf { it.isNotBlank() }
            ?: "Health check ${type?.name?.lowercase() ?: "issue"}.",
    )

private fun dev.appolon.homelab.arr.api.lidarr.model.DiskSpaceResource.toPollDisk() =
    LidarrPollDisk(path = path.orEmpty(), freeBytes = freeSpace, totalBytes = totalSpace)

/**
 * [PollAdapter] for Lidarr. Fetches the cheap status surfaces (queue status
 * and details, a 24 hour calendar window, health, disk space), diffs them
 * against the previous poll's snapshot in the entity store, and returns
 * scheduler-ready counts and events. Any single endpoint failing degrades
 * only its signal: the poll still returns every other surface's counts with
 * [PollResult.error] set.
 */
class LidarrPollAdapter(
    private val secrets: ArrSecrets,
    private val store: EntityStore,
    private val clientFactory: ArrClientFactory = DefaultArrClientFactory(),
    private val clock: Clock = Clock.systemUTC(),
    private val apis: suspend (ArrServer) -> LidarrPollApis? = { server ->
        buildApis(server, secrets, clientFactory)
    },
) : PollAdapter {

    override suspend fun poll(server: ArrServer): PollResult {
        if (server.type != AppType.LIDARR) {
            return PollResult(
                error = ArrError.Unknown(
                    IllegalStateException("LidarrPollAdapter cannot poll a ${server.type} server"),
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

        val queueStatus = signal(::degrade) { bundle.queueStatusApi.getQueueStatus() }
        val queueDetails =
            signal(::degrade) { bundle.queueDetailsApi.getQueueDetails() }
        val now = clock.instant()
        val calendar = signal(::degrade) {
            bundle.calendarApi.getCalendar(
                start = now.toString(),
                end = now.plus(CALENDAR_WINDOW).toString(),
            )
        }
        val health = signal(::degrade) { bundle.healthApi.getHealth() }
        val disks = signal(::degrade) { bundle.diskSpaceApi.getDiskSpace() }

        val surfaces = LidarrPollSurfaces(
            queueItems = queueDetails.orEmpty().map { it.toPollItem() },
            queueErrors = (queueStatus?.errors == true) || (queueStatus?.unknownErrors == true),
            releases = calendar.orEmpty().map { it.toPollRelease() },
            health = health.orEmpty().map { it.toPollIssue() },
            disks = disks.orEmpty().map { it.toPollDisk() },
        )

        val previous = try {
            store.get(server.id, LIDARR_POLL_SNAPSHOT_TYPE, SNAPSHOT_ID, LidarrPollSnapshot.serializer())
        } catch (_: Throwable) {
            // An unreadable snapshot behaves like a first poll: baseline
            // silently rather than failing a poll over local storage.
            null
        }
        val outcome = LidarrPollDiff.diff(previous, surfaces, now)
        try {
            store.put(
                server.id,
                LIDARR_POLL_SNAPSHOT_TYPE,
                SNAPSHOT_ID,
                outcome.snapshot,
                LidarrPollSnapshot.serializer(),
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
): LidarrPollApis? {
    val endpoint = endpointOf(server, secrets) ?: return null
    val retrofit = Retrofit.Builder()
        .baseUrl(ServerEndpoint.normalizeBaseUrl(server.baseUrl))
        .client(clientFactory.clientFor(endpoint))
        .addConverterFactory(ArrJson.asConverterFactory("application/json".toMediaType()))
        .build()
    return LidarrPollApis(
        queueStatusApi = LidarrQueueApis.queueStatus(retrofit),
        queueDetailsApi = LidarrQueueApis.queueDetails(retrofit),
        calendarApi = LidarrCalendarApis.calendar(retrofit),
        healthApi = LidarrSystemApis.health(retrofit),
        diskSpaceApi = LidarrSystemApis.diskSpace(retrofit),
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
