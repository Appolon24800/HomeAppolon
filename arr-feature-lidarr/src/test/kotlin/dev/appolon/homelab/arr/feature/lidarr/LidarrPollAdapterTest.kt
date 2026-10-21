package dev.appolon.homelab.arr.feature.lidarr

import dev.appolon.homelab.arr.api.lidarr.DiskSpaceApi
import dev.appolon.homelab.arr.api.lidarr.FakeLidarrCalendar
import dev.appolon.homelab.arr.api.lidarr.FakeLidarrQueue
import dev.appolon.homelab.arr.api.lidarr.FakeLidarrSystem
import dev.appolon.homelab.arr.api.lidarr.model.AlbumResource
import dev.appolon.homelab.arr.api.lidarr.model.DiskSpaceResource
import dev.appolon.homelab.arr.api.lidarr.model.HealthCheckResult
import dev.appolon.homelab.arr.api.lidarr.model.HealthResource
import dev.appolon.homelab.arr.api.lidarr.model.QueueResource
import dev.appolon.homelab.arr.api.lidarr.model.TrackedDownloadState
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
 * Exercises [LidarrPollAdapter] end to end against the committed api-module
 * fakes: fetch, translate, diff, snapshot round-trip, and per-endpoint
 * degradation, with a fixed clock so the imminent window is deterministic.
 */
class LidarrPollAdapterTest {

    private val now: Instant = Instant.parse("2026-10-20T12:00:00Z")
    private val server = ArrServer(
        id = ServerId("lidarr-1"),
        type = AppType.LIDARR,
        name = "Lidarr",
        baseUrl = "http://lidarr.example/",
        apiKeyEncrypted = "key",
    )

    private val queue = FakeLidarrQueue()
    private val calendar = FakeLidarrCalendar()
    private val system = FakeLidarrSystem()
    private val store = MemoryEntityStore()

    private fun adapter(disks: DiskSpaceApi = system) = LidarrPollAdapter(
        secrets = PlainSecrets,
        store = store,
        clock = Clock.fixed(now, ZoneOffset.UTC),
        apis = { LidarrPollApis(queue, queue, calendar, system, disks) },
    )

    private fun item(
        id: Int,
        state: TrackedDownloadState = TrackedDownloadState.DOWNLOADING,
    ) = QueueResource(id = id, artistId = id, title = "Album $id", trackedDownloadState = state)

    @Test
    fun `first poll baselines without notifying`() = runTest {
        queue.queue[1] = item(1)
        system.health += HealthResource(id = 6, type = HealthCheckResult.ERROR, message = "old")

        val result = adapter().poll(server)

        assertEquals(PollOutcome.QUIET, result.outcome())
        assertEquals(0, result.newGrabs)
        assertEquals(0, result.healthIssues)
        assertTrue(result.events.isEmpty())
    }

    @Test
    fun `new queue item reports a grab and keeps the poll busy`() = runTest {
        adapter().poll(server)
        queue.queue[1] = item(1)

        val result = adapter().poll(server)

        assertEquals(1, result.newGrabs)
        assertEquals(1, result.activeQueueItems)
        assertEquals(PollOutcome.BUSY, result.outcome())
        assertEquals(NotificationKind.GRAB, result.events.single().kind)
    }

    @Test
    fun `download failure transition reports a failure`() = runTest {
        queue.queue[1] = item(1)
        adapter().poll(server)
        queue.queue[1] = queue.queue.getValue(1).copy(
            trackedDownloadState = TrackedDownloadState.DOWNLOAD_FAILED,
            errorMessage = "timeout",
        )

        val result = adapter().poll(server)

        assertEquals(1, result.failures)
        val fail = result.events.single()
        assertEquals(NotificationKind.FAIL, fail.kind)
        assertEquals("fail:1", fail.dedupeKey)
    }

    @Test
    fun `album releasing within six hours is imminent`() = runTest {
        adapter().poll(server)
        calendar.calendar[7] = AlbumResource(
            id = 7,
            title = "New LP",
            monitored = true,
            releaseDate = "2026-10-20T15:00:00Z",
        )

        val result = adapter().poll(server)

        assertEquals(1, result.imminentReleases)
        assertEquals(PollOutcome.BUSY, result.outcome())
    }

    @Test
    fun `new health issue and low disk notify once`() = runTest {
        adapter().poll(server)
        system.health += HealthResource(id = 1, source = "indexer", type = HealthCheckResult.WARNING, message = "Indexer disabled")
        system.diskSpace += DiskSpaceResource(path = "/music", freeSpace = 1_000_000_000L, totalSpace = 1_000_000_000_000L)

        val result = adapter().poll(server)

        assertEquals(1, result.healthIssues)
        assertEquals(1, result.diskWarnings)
        assertEquals(2, result.events.size)

        val repeat = adapter().poll(server)
        assertTrue(repeat.events.isEmpty())
    }

    @Test
    fun `disk endpoint failing degrades the poll without losing the queue`() = runTest {
        adapter().poll(server)
        queue.queue[1] = item(1)

        val result = adapter(disks = FailingDiskSpace).poll(server)

        assertEquals(1, result.newGrabs)
        assertEquals(0, result.diskWarnings)
        assertTrue(result.error is ArrError.Network)
    }
}

private object FailingDiskSpace : DiskSpaceApi {
    override suspend fun getDiskSpace(): Nothing = throw IOException("disk down")
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
