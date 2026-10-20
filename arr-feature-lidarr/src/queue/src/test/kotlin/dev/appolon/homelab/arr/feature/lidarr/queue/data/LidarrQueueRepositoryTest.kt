package dev.appolon.homelab.arr.feature.lidarr.queue.data

import dev.appolon.homelab.arr.api.lidarr.FakeLidarrQueue
import dev.appolon.homelab.arr.api.lidarr.model.AlbumResource
import dev.appolon.homelab.arr.api.lidarr.model.ArtistResource
import dev.appolon.homelab.arr.api.lidarr.model.QueueResource
import dev.appolon.homelab.arr.core.ActionId
import dev.appolon.homelab.arr.core.AppType
import dev.appolon.homelab.arr.core.ArrError
import dev.appolon.homelab.arr.core.ArrResult
import dev.appolon.homelab.arr.core.ServerId
import dev.appolon.homelab.arr.data.QueuedAction
import dev.appolon.homelab.arr.data.store.ActionQueueImpl
import dev.appolon.homelab.arr.feature.lidarr.queue.FakeEntityStore
import java.io.IOException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LidarrQueueRepositoryTest {

    private val serverId = ServerId("server-1")
    private lateinit var fake: FakeLidarrQueue
    private lateinit var store: FakeEntityStore
    private lateinit var actionQueue: ActionQueueImpl
    private val runnerCalls = mutableListOf<List<String?>>()
    private var runnerError: (() -> Throwable?) = { null }
    private var fakeNow: () -> Long = { 1_000L }

    private fun repo(
        context: kotlin.coroutines.CoroutineContext = StandardTestDispatcher(),
    ): LidarrQueueRepository = LidarrQueueRepository(
        serverId = serverId,
        store = store,
        queueDetailsApi = fake,
        queueApi = fake,
        queueActionApi = fake,
        queueStatusApi = fake,
        commandRunner = { name, downloadId, path, albumIds ->
            runnerError()?.let { throw it }
            runnerCalls += listOf(name, downloadId, path, albumIds?.joinToString(","))
            ArrResult.ok(Unit)
        },
        actionQueue = actionQueue,
        now = fakeNow,
        ioContext = context,
    )

    private fun queueItem(
        id: Int,
        title: String = "Artist $id",
        artistId: Int? = id,
        status: String? = "downloading",
    ) = QueueResource(
        id = id,
        artistId = artistId,
        albumId = if (artistId != null) id + 50 else null,
        artist = if (artistId != null) ArtistResource(id = artistId, artistName = title) else null,
        album = if (artistId != null) AlbumResource(id = id + 50, title = "Album $id") else null,
        title = "Release.$id",
        status = status,
        downloadId = "dl-$id",
        outputPath = "/downloads/$id",
        size = 10.0,
        sizeleft = 5.0,
    )

    @Before
    fun setUp() {
        fake = FakeLidarrQueue()
        store = FakeEntityStore()
        actionQueue = ActionQueueImpl(store)
        runnerCalls.clear()
        runnerError = { null }
        fakeNow = { 1_000L }
    }

    @Test
    fun `refresh caches items and status and reconciles rows that disappeared`() = runTest {
        val repository = repo(StandardTestDispatcher(testScheduler))
        fake.queue[1] = queueItem(1)
        fake.queue[2] = queueItem(2)
        store.put(
            serverId,
            LidarrQueueRepository.QUEUE_ITEM_TYPE,
            "99",
            queueItem(99),
            QueueResource.serializer(),
        )

        val result = repository.refresh()

        assertTrue(result is ArrResult.Ok)
        val cached = repository.cachedItems()
        assertEquals(listOf(1, 2), cached.map { it.id }.sorted())
        assertTrue(store.lastFetchedAt(serverId, LidarrQueueRepository.QUEUE_ITEM_TYPE)!! > 0)
        assertEquals(2, repository.observeStatus().first()!!.totalCount)
    }

    @Test
    fun `refresh keeps the embedded artist and album for row projection`() = runTest {
        val repository = repo(StandardTestDispatcher(testScheduler))
        fake.queue[1] = queueItem(1, title = "Pink Floyd")

        repository.refresh()

        val cached = repository.cachedItems().single()
        assertEquals("Pink Floyd", cached.artist?.artistName)
        assertEquals("Album 1", cached.album?.title)
    }

    @Test
    fun `isStale flips once the TTL elapsed`() = runTest {
        var now = 1_000L
        fakeNow = { now }
        val repository = repo(StandardTestDispatcher(testScheduler))
        fake.queue[1] = queueItem(1)
        repository.refresh()
        assertFalse(repository.isStale(ttlMillis = 100))

        now += 101
        assertTrue(repository.isStale(ttlMillis = 100))
    }

    @Test
    fun `remove online drops the server row and the cached row`() = runTest {
        val repository = repo(StandardTestDispatcher(testScheduler))
        fake.queue[1] = queueItem(1)
        repository.refresh()

        val result = repository.removeItem(1, albumId = 51, options = QueueRemoveOptions())

        assertTrue(result is ArrResult.Ok && result.value == QueueWriteOutcome.Synced)
        assertFalse(fake.queue.containsKey(1))
        assertTrue(repository.cachedItems().isEmpty())
    }

    @Test
    fun `remove with blocklist leaves the replacement search to the server`() = runTest {
        val repository = repo(StandardTestDispatcher(testScheduler))
        fake.queue[1] = queueItem(1)
        repository.refresh()

        val result = repository.removeItem(
            1,
            albumId = 51,
            options = QueueRemoveOptions(blocklist = true, searchReplacement = true),
        )

        assertTrue(result is ArrResult.Ok)
        // With blocklist the replacement search is the server's job: no command.
        assertTrue(runnerCalls.isEmpty())
        assertEquals(1, fake.blocklist.size)
    }

    @Test
    fun `remove without blocklist runs the album search itself`() = runTest {
        val repository = repo(StandardTestDispatcher(testScheduler))
        fake.queue[1] = queueItem(1)
        repository.refresh()

        repository.removeItem(1, albumId = 51, options = QueueRemoveOptions(searchReplacement = true))

        assertEquals(listOf(listOf(LidarrQueueCommands.ALBUM_SEARCH, null, null, "51")), runnerCalls)
    }

    @Test
    fun `remove offline optimistically clears the row and enqueues the DELETE`() = runTest {
        val repository = repo(StandardTestDispatcher(testScheduler))
        fake.queue[1] = queueItem(1)
        repository.refresh()
        fake.error = { op -> if (op == "deleteQueue") IOException("offline") else null }

        val result = repository.removeItem(1, albumId = 51, options = QueueRemoveOptions(blocklist = true))

        assertTrue(result is ArrResult.Ok && result.value is QueueWriteOutcome.Queued)
        assertTrue(repository.cachedItems().isEmpty())
        val pending = actionQueue.pending()
        assertEquals(1, pending.size)
        assertEquals("DELETE", pending.single().method)
        assertEquals(
            "api/v1/queue/1?removeFromClient=true&blocklist=true&skipRedownload=true",
            pending.single().path,
        )
    }

    @Test
    fun `remove offline with re-search enqueues a second command action`() = runTest {
        val repository = repo(StandardTestDispatcher(testScheduler))
        fake.queue[1] = queueItem(1)
        repository.refresh()
        fake.error = { op -> if (op == "deleteQueue") IOException("offline") else null }

        repository.removeItem(
            1,
            albumId = 51,
            options = QueueRemoveOptions(searchReplacement = true),
        )

        val pending = actionQueue.pending()
        assertEquals(2, pending.size)
        val command = pending.first { it.method == "POST" }
        assertEquals("api/v1/command", command.path)
        assertTrue(command.bodyJson!!.contains(LidarrQueueCommands.ALBUM_SEARCH))
        assertTrue(command.bodyJson!!.contains("51"))
    }

    @Test
    fun `non-retryable remove failures surface instead of queueing`() = runTest {
        val repository = repo(StandardTestDispatcher(testScheduler))
        fake.queue[1] = queueItem(1)
        repository.refresh()
        fake.error = { op ->
            if (op == "deleteQueue") {
                retrofit2.HttpException(retrofit2.Response.error<Any>(401, "".toResponseBody()))
            } else {
                null
            }
        }

        val result = repository.removeItem(1, albumId = 51, options = QueueRemoveOptions())

        assertTrue(result is ArrResult.Err && result.error is ArrError.Auth)
        assertTrue(actionQueue.pending().isEmpty())
        assertEquals(1, repository.cachedItems().size)
    }

    @Test
    fun `retry import dispatches DownloadedAlbumsScan online`() = runTest {
        val repository = repo(StandardTestDispatcher(testScheduler))

        val result = repository.retryImport("dl-7", "/downloads/7")

        assertTrue(result is ArrResult.Ok && result.value == QueueWriteOutcome.Synced)
        assertEquals(
            listOf(listOf(LidarrQueueCommands.RETRY_IMPORT, "dl-7", "/downloads/7", null)),
            runnerCalls,
        )
    }

    @Test
    fun `retry import offline enqueues the command verbatim`() = runTest {
        val repository = repo(StandardTestDispatcher(testScheduler))
        runnerError = { IOException("offline") }

        val result = repository.retryImport("dl-7", "/downloads/7")

        assertTrue(result is ArrResult.Ok && result.value is QueueWriteOutcome.Queued)
        val pending = actionQueue.pending().single()
        assertEquals("POST", pending.method)
        assertEquals("api/v1/command", pending.path)
        assertTrue(pending.bodyJson!!.contains(LidarrQueueCommands.RETRY_IMPORT))
        assertTrue(pending.bodyJson!!.contains("dl-7"))
    }

    @Test
    fun `grab online hits the grab endpoint`() = runTest {
        val repository = repo(StandardTestDispatcher(testScheduler))
        fake.queue[1] = queueItem(1)

        val result = repository.grab(1)

        assertTrue(result is ArrResult.Ok && result.value == QueueWriteOutcome.Synced)
        assertEquals(listOf(1), fake.grabbedQueueIds)
    }

    @Test
    fun `grab offline enqueues the grab`() = runTest {
        val repository = repo(StandardTestDispatcher(testScheduler))
        fake.error = { op -> if (op == "grabQueue") IOException("offline") else null }

        val result = repository.grab(1)

        assertTrue(result is ArrResult.Ok && result.value is QueueWriteOutcome.Queued)
        val pending = actionQueue.pending().single()
        assertEquals("POST", pending.method)
        assertEquals("api/v1/queue/grab/1", pending.path)
    }

    @Test
    fun `queuedWrites projects removals, grabs, and retry commands`() = runTest {
        val repository = repo(StandardTestDispatcher(testScheduler))
        fake.queue[1] = queueItem(1)
        fake.error = { op ->
            if (op == "deleteQueue" || op == "grabQueue") IOException("offline") else null
        }
        repository.removeItem(2, albumId = 52, options = QueueRemoveOptions())
        repository.grab(3)
        runnerError = { IOException("offline") }
        repository.retryImport("dl-1", null)

        val writes = repository.queuedWrites().first()

        assertEquals(setOf(2), writes.removedItemIds)
        assertEquals(setOf(3), writes.grabbedItemIds)
        assertEquals(setOf("dl-1"), writes.retriedDownloadIds)
    }

    @Test
    fun `queuedItemIds matches retried downloads back to live queue rows`() = runTest {
        val repository = repo(StandardTestDispatcher(testScheduler))
        fake.queue[1] = queueItem(1)
        repository.refresh()
        runnerError = { IOException("offline") }
        repository.retryImport("dl-1", null)

        assertEquals(setOf(1), repository.queuedItemIds().first())
    }

    @Test
    fun `queuedCount only counts this server's actions`() = runTest {
        val repository = repo(StandardTestDispatcher(testScheduler))
        fake.error = { op -> if (op == "grabQueue") IOException("offline") else null }
        repository.grab(1)
        actionQueue.enqueue(
            QueuedAction(
                actionId = ActionId("foreign"),
                serverId = ServerId("other"),
                appType = AppType.LIDARR,
                method = "POST",
                path = "api/v1/queue/grab/9",
                description = "foreign",
                enqueuedAtMillis = 0,
            ),
        )

        assertEquals(1, repository.queuedCount().first())
    }
}
