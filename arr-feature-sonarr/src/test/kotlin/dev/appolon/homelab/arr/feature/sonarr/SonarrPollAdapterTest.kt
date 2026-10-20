package dev.appolon.homelab.arr.feature.sonarr

import dev.appolon.homelab.arr.api.sonarr.FakeSonarrCalendar
import dev.appolon.homelab.arr.api.sonarr.FakeSonarrQueue
import dev.appolon.homelab.arr.api.sonarr.FakeSonarrSystem
import dev.appolon.homelab.arr.api.sonarr.HealthApi
import dev.appolon.homelab.arr.api.sonarr.model.DiskSpaceResource
import dev.appolon.homelab.arr.api.sonarr.model.EpisodeResource
import dev.appolon.homelab.arr.api.sonarr.model.HealthCheckResult
import dev.appolon.homelab.arr.api.sonarr.model.HealthResource
import dev.appolon.homelab.arr.api.sonarr.model.QueueResource
import dev.appolon.homelab.arr.api.sonarr.model.TrackedDownloadState
import dev.appolon.homelab.arr.core.AppType
import dev.appolon.homelab.arr.core.ArrError
import dev.appolon.homelab.arr.core.EntityType
import dev.appolon.homelab.arr.core.ServerId
import dev.appolon.homelab.arr.data.ArrServer
import dev.appolon.homelab.arr.data.EntityStore
import dev.appolon.homelab.arr.data.secrets.ArrSecrets
import dev.appolon.homelab.arr.net.ArrJson
import dev.appolon.homelab.arr.sync.scheduler.NotificationKind
import dev.appolon.homelab.arr.sync.scheduler.PollOutcome
import java.io.IOException
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.KSerializer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Exercises [SonarrPollAdapter] end to end against the committed api-module
 * fakes: fetch, translate, diff, snapshot round-trip, and per-endpoint
 * degradation, with a fixed clock so the imminent window is deterministic.
 */
class SonarrPollAdapterTest {

    private val now: Instant = Instant.parse("2026-10-20T19:00:00Z")
    private val server = ArrServer(
        id = ServerId("sonarr-1"),
        type = AppType.SONARR,
        name = "Sonarr",
        baseUrl = "http://sonarr.example/",
        apiKeyEncrypted = "key",
    )

    private val queue = FakeSonarrQueue()
    private val calendar = FakeSonarrCalendar()
    private val system = FakeSonarrSystem()
    private val store = MemoryEntityStore()

    private fun adapter(health: HealthApi = system.healthApi) = SonarrPollAdapter(
        secrets = PlainSecrets,
        store = store,
        clock = Clock.fixed(now, ZoneOffset.UTC),
        apis = {
            SonarrPollApis(
                queue.queueStatus,
                queue.queueDetails,
                calendar.calendar,
                health,
                system.diskSpaceApi,
            )
        },
    )

    private fun item(
        id: Int,
        state: TrackedDownloadState = TrackedDownloadState.DOWNLOADING,
    ) = QueueResource(id = id, seriesId = id, title = "Episode $id", trackedDownloadState = state)

    @Test
    fun `first poll baselines without notifying`() = runTest {
        queue.queueStore[1] = item(1)
        system.health += HealthResource(id = 4, type = HealthCheckResult.ERROR, message = "old")

        val result = adapter().poll(server)

        assertEquals(PollOutcome.QUIET, result.outcome())
        assertEquals(0, result.newGrabs)
        assertEquals(0, result.healthIssues)
        assertTrue(result.events.isEmpty())
    }

    @Test
    fun `new queue item reports a grab and keeps the poll busy`() = runTest {
        adapter().poll(server)
        queue.queueStore[1] = item(1)

        val result = adapter().poll(server)

        assertEquals(1, result.newGrabs)
        assertEquals(1, result.activeQueueItems)
        assertEquals(PollOutcome.BUSY, result.outcome())
        assertEquals(NotificationKind.GRAB, result.events.single().kind)
    }

    @Test
    fun `import and failure transitions both notify`() = runTest {
        queue.queueStore[1] = item(1)
        queue.queueStore[2] = item(2)
        adapter().poll(server)
        queue.queueStore[1] = queue.queueStore.getValue(1).copy(trackedDownloadState = TrackedDownloadState.IMPORTED)
        queue.queueStore[2] = queue.queueStore.getValue(2).copy(trackedDownloadState = TrackedDownloadState.FAILED)

        val result = adapter().poll(server)

        assertEquals(1, result.newImports)
        assertEquals(1, result.failures)
        assertEquals(
            setOf(NotificationKind.IMPORT, NotificationKind.FAIL),
            result.events.map { it.kind }.toSet(),
        )
    }

    @Test
    fun `episode airing within six hours is imminent`() = runTest {
        adapter().poll(server)
        calendar.calendarStore[10] = EpisodeResource(
            id = 10,
            title = "New episode",
            airDate = "2026-10-21",
            airDateUtc = "2026-10-21T00:00:00Z",
            monitored = true,
        )

        val result = adapter().poll(server)

        assertEquals(1, result.imminentReleases)
        assertEquals(PollOutcome.BUSY, result.outcome())
    }

    @Test
    fun `new health issue and low disk notify once`() = runTest {
        adapter().poll(server)
        system.health += HealthResource(id = 2, source = "recycleBin", type = HealthCheckResult.WARNING, message = "Bin missing")
        system.diskSpace += DiskSpaceResource(path = "/tv", freeSpace = 1_000_000_000L, totalSpace = 1_000_000_000_000L)

        val result = adapter().poll(server)

        assertEquals(1, result.healthIssues)
        assertEquals(1, result.diskWarnings)
        assertEquals(2, result.events.size)

        val repeat = adapter().poll(server)
        assertTrue(repeat.events.isEmpty())
    }

    @Test
    fun `health endpoint failing degrades the poll without losing the queue`() = runTest {
        adapter().poll(server)
        queue.queueStore[1] = item(1)

        val result = adapter(health = FailingHealth).poll(server)

        assertEquals(1, result.newGrabs)
        assertEquals(0, result.healthIssues)
        assertTrue(result.error is ArrError.Network)
    }
}

private object FailingHealth : HealthApi {
    override suspend fun list(): Nothing = throw IOException("health down")
}

private object PlainSecrets : ArrSecrets {
    override fun encrypt(plaintext: String): String = plaintext
    override fun decrypt(envelope: String): String = envelope
}

/** Minimal in-memory EntityStore; only what the adapter touches. */
private class MemoryEntityStore : EntityStore {
    val rows = mutableMapOf<String, String>()

    private fun key(serverId: ServerId, type: EntityType, id: String) =
        "${serverId.value}|${type.value}|$id"

    override suspend fun <T> put(
        serverId: ServerId,
        type: EntityType,
        id: String,
        value: T,
        serializer: KSerializer<T>,
    ) {
        rows[key(serverId, type, id)] = ArrJson.encodeToString(serializer, value)
    }

    override suspend fun <T> putAll(
        serverId: ServerId,
        type: EntityType,
        values: List<Pair<String, T>>,
        serializer: KSerializer<T>,
        fetchedAtMillis: Long,
    ) {
        values.forEach { (id, value) -> put(serverId, type, id, value, serializer) }
    }

    override suspend fun <T> getAll(
        serverId: ServerId,
        type: EntityType,
        serializer: KSerializer<T>,
    ): List<T> = rows.entries
        .filter { it.key.startsWith("${serverId.value}|${type.value}|") }
        .map { ArrJson.decodeFromString(serializer, it.value) }

    override suspend fun <T> get(
        serverId: ServerId,
        type: EntityType,
        id: String,
        serializer: KSerializer<T>,
    ): T? = rows[key(serverId, type, id)]?.let { ArrJson.decodeFromString(serializer, it) }

    override suspend fun delete(serverId: ServerId, type: EntityType, id: String) {
        rows.remove(key(serverId, type, id))
    }

    override suspend fun deleteAll(serverId: ServerId, type: EntityType?) {
        val prefix = type?.let { "${serverId.value}|${it.value}|" } ?: "${serverId.value}|"
        rows.keys.filter { it.startsWith(prefix) }.forEach(rows::remove)
    }

    override suspend fun lastFetchedAt(serverId: ServerId, type: EntityType): Long? = null

    override fun <T> observe(
        serverId: ServerId,
        type: EntityType,
        serializer: KSerializer<T>,
    ): Flow<List<T>> = flow { emit(getAll(serverId, type, serializer)) }
}
