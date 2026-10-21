package dev.appolon.homelab.arr.feature.radarr

import dev.appolon.homelab.arr.api.radarr.FakeRadarrCalendar
import dev.appolon.homelab.arr.api.radarr.FakeRadarrQueue
import dev.appolon.homelab.arr.api.radarr.FakeRadarrSystem
import dev.appolon.homelab.arr.api.radarr.HealthApi
import dev.appolon.homelab.arr.api.radarr.QueueStatusApi
import dev.appolon.homelab.arr.api.radarr.model.DiskSpaceResource
import dev.appolon.homelab.arr.api.radarr.model.HealthCheckResult
import dev.appolon.homelab.arr.api.radarr.model.HealthResource
import dev.appolon.homelab.arr.api.radarr.model.MovieResource
import dev.appolon.homelab.arr.api.radarr.model.QueueResource
import dev.appolon.homelab.arr.api.radarr.model.TrackedDownloadState
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
 * Exercises [RadarrPollAdapter] end to end against the committed api-module
 * fakes: fetch, translate, diff, snapshot round-trip, and per-endpoint
 * degradation, with a fixed clock so the imminent window is deterministic.
 */
class RadarrPollAdapterTest {

    private val now: Instant = Instant.parse("2026-10-20T12:00:00Z")
    private val server = ArrServer(
        id = ServerId("radarr-1"),
        type = AppType.RADARR,
        name = "Radarr",
        baseUrl = "http://radarr.example/",
        apiKeyEncrypted = "key",
    )

    private val queue = FakeRadarrQueue()
    private val calendar = FakeRadarrCalendar()
    private val system = FakeRadarrSystem()
    private val store = MemoryEntityStore()

    private fun adapter(
        queueStatus: QueueStatusApi = queue,
        health: HealthApi = system,
    ) = RadarrPollAdapter(
        secrets = PlainSecrets,
        store = store,
        clock = Clock.fixed(now, ZoneOffset.UTC),
        apis = {
            RadarrPollApis(queueStatus, queue, calendar, health, system)
        },
    )

    private fun item(
        id: Int,
        state: TrackedDownloadState = TrackedDownloadState.DOWNLOADING,
    ) = QueueResource(id = id, movieId = id, title = "Movie $id", trackedDownloadState = state)

    @Test
    fun `first poll baselines without notifying`() = runTest {
        queue.queueItems += item(1)
        system.healthItems += HealthResource(id = 9, type = HealthCheckResult.ERROR, message = "old")

        val result = adapter().poll(server)

        assertEquals(PollOutcome.QUIET, result.outcome())
        assertEquals(0, result.newGrabs)
        assertEquals(0, result.healthIssues)
        assertTrue(result.events.isEmpty())
        val persisted = store.get(server.id, RADARR_POLL_SNAPSHOT_TYPE, "latest", RadarrPollSnapshot.serializer())
        assertTrue(persisted != null)
    }

    @Test
    fun `new queue item reports a grab and keeps the poll busy`() = runTest {
        adapter().poll(server)
        queue.queueItems += item(1)

        val result = adapter().poll(server)

        assertEquals(1, result.newGrabs)
        assertEquals(1, result.activeQueueItems)
        assertEquals(PollOutcome.BUSY, result.outcome())
        val grab = result.events.single()
        assertEquals(NotificationKind.GRAB, grab.kind)
        assertEquals("grabs:1", grab.dedupeKey)
    }

    @Test
    fun `item reaching imported reports an import once`() = runTest {
        queue.queueItems += item(1)
        adapter().poll(server)
        queue.queueItems[0] = queue.queueItems[0].copy(trackedDownloadState = TrackedDownloadState.IMPORTED)

        val result = adapter().poll(server)

        assertEquals(1, result.newImports)
        assertEquals(0, result.activeQueueItems)
        assertEquals(NotificationKind.IMPORT, result.events.single().kind)

        val third = adapter().poll(server)
        assertEquals(0, third.newImports)
    }

    @Test
    fun `failed transition reports a failure`() = runTest {
        queue.queueItems += item(1)
        adapter().poll(server)
        queue.queueItems[0] = queue.queueItems[0].copy(
            trackedDownloadState = TrackedDownloadState.FAILED,
            errorMessage = "404",
        )

        val result = adapter().poll(server)

        assertEquals(1, result.failures)
        val fail = result.events.single()
        assertEquals(NotificationKind.FAIL, fail.kind)
        assertEquals("fail:1", fail.dedupeKey)
    }

    @Test
    fun `vanished item last seen import pending reports an import`() = runTest {
        queue.queueItems += item(1, TrackedDownloadState.IMPORT_PENDING)
        adapter().poll(server)
        queue.queueItems.clear()

        val result = adapter().poll(server)

        assertEquals(1, result.newImports)
    }

    @Test
    fun `new health issue and low disk notify once`() = runTest {
        adapter().poll(server)
        system.healthItems += HealthResource(id = 3, source = "rootFolder", type = HealthCheckResult.ERROR, message = "Missing root folder")
        system.diskSpaceItems += DiskSpaceResource(path = "/media", freeSpace = 1_000_000_000L, totalSpace = 1_000_000_000_000L)

        val result = adapter().poll(server)

        assertEquals(1, result.healthIssues)
        assertEquals(1, result.diskWarnings)
        assertEquals(
            listOf(NotificationKind.HEALTH, NotificationKind.DISK),
            result.events.map { it.kind },
        )

        val repeat = adapter().poll(server)
        assertEquals(0, repeat.healthIssues)
        assertEquals(0, repeat.diskWarnings)
        assertTrue(repeat.events.isEmpty())
    }

    @Test
    fun `release within six hours is imminent`() = runTest {
        adapter().poll(server)
        calendar.movies += MovieResource(
            id = 10,
            title = "Soon",
            monitored = true,
            inCinemas = "2026-10-20T15:00:00Z",
        )

        val result = adapter().poll(server)

        assertEquals(1, result.imminentReleases)
        assertEquals(PollOutcome.BUSY, result.outcome())
    }

    @Test
    fun `queue status endpoint failing degrades the poll without losing details`() = runTest {
        adapter().poll(server)
        queue.queueItems += item(1)

        val result = adapter(queueStatus = FailingQueueStatus).poll(server)

        assertEquals(1, result.newGrabs)
        assertTrue(result.error is ArrError.Network)
    }

    @Test
    fun `snapshot write failure degrades but keeps counts`() = runTest {
        adapter().poll(server)
        queue.queueItems += item(1)
        store.failWrites = true

        val result = adapter().poll(server)

        assertEquals(1, result.newGrabs)
        assertTrue(result.error is ArrError.Network)
    }
}

private object FailingQueueStatus : QueueStatusApi {
    override suspend fun queueStatus(): Nothing = throw IOException("queue status down")
}

private object PlainSecrets : ArrSecrets {
    override fun encrypt(plaintext: String): String = plaintext
    override fun decrypt(envelope: String): String = envelope
}

/** Minimal in-memory EntityStore; only what the adapter touches. */
private class MemoryEntityStore : EntityStore {
    val rows = mutableMapOf<String, String>()
    var failWrites = false

    private fun key(serverId: ServerId, type: EntityType, id: String) =
        "${serverId.value}|${type.value}|$id"

    override suspend fun <T> put(
        serverId: ServerId,
        type: EntityType,
        id: String,
        value: T,
        serializer: KSerializer<T>,
    ) {
        if (failWrites) throw IOException("store down")
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
