package dev.appolon.homelab.arr.feature.sonarr.queue.data

import dev.appolon.homelab.arr.api.sonarr.model.EpisodeResource
import dev.appolon.homelab.arr.api.sonarr.model.SeriesResource
import dev.appolon.homelab.arr.api.sonarr.FakeSonarrQueue
import dev.appolon.homelab.arr.api.sonarr.model.QueueResource
import dev.appolon.homelab.arr.api.sonarr.model.QueueStatus
import dev.appolon.homelab.arr.core.ActionId
import dev.appolon.homelab.arr.core.AppType
import dev.appolon.homelab.arr.core.ArrError
import dev.appolon.homelab.arr.core.ArrResult
import dev.appolon.homelab.arr.core.ServerId
import dev.appolon.homelab.arr.data.QueuedAction
import dev.appolon.homelab.arr.data.store.ActionQueueImpl
import dev.appolon.homelab.arr.data.testing.FakeEntityStore
import java.io.IOException
import kotlinx.serialization.json.put
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
class SonarrQueueRepositoryTest {

    private val serverId = ServerId("server-1")
    private lateinit var fake: FakeSonarrQueue
    private lateinit var store: FakeEntityStore
    private lateinit var actionQueue: ActionQueueImpl
    private val runnerCalls = mutableListOf<List<String?>>()
    private var runnerError: (() -> Throwable?) = { null }
    private var fakeNow: () -> Long = { 1_000L }

    private fun repo(
        context: kotlin.coroutines.CoroutineContext = StandardTestDispatcher(),
    ): SonarrQueueRepository = SonarrQueueRepository(
        serverId = serverId,
        store = store,
        queueDetailsApi = fake.queueDetails,
        queueApi = fake.queue,
        queueActionApi = fake.queueAction,
        queueStatusApi = fake.queueStatus,
        commandRunner = { name, downloadId, path, episodeIds ->
            runnerError()?.let { throw it }
            runnerCalls += listOf(name, downloadId, path, episodeIds?.joinToString(","))
            ArrResult.ok(Unit)
        },
        actionQueue = actionQueue,
        now = fakeNow,
        ioContext = context,
    )

    private fun queueItem(
        id: Int,
        title: String = "Show $id",
        seriesId: Int? = id,
        status: QueueStatus = QueueStatus.DOWNLOADING,
    ) = QueueResource(
        id = id,
        seriesId = seriesId,
        episodeId = if (seriesId != null) id + 100 else null,
        seasonNumber = if (seriesId != null) 1 else null,
        series = if (seriesId != null) seriesJson(seriesId, title) else SeriesResource(),
        episode = if (seriesId != null) {
            episodeJson(id + 100, 1, id, "Episode $id")
        } else {
            EpisodeResource()
        },
        title = "Release.$id",
        status = status,
        downloadId = "dl-$id",
        outputPath = "/downloads/$id",
        size = 10.0,
        sizeleft = 5.0,
    )

    private fun seriesJson(id: Int, title: String): SeriesResource = SeriesResource(id = id, title = title)

    private fun episodeJson(id: Int, season: Int, number: Int, title: String): EpisodeResource = EpisodeResource(id = id, seasonNumber = season, episodeNumber = number)

    @Before
    fun setUp() {
        fake = FakeSonarrQueue()
        store = FakeEntityStore()
        actionQueue = ActionQueueImpl(store)
        runnerCalls.clear()
        runnerError = { null }
        fakeNow = { 1_000L }
    }

    @Test
    fun `refresh caches items and status and reconciles rows that disappeared`() = runTest {
        val repository = repo(StandardTestDispatcher(testScheduler))
        fake.queueStore[1] = queueItem(1)
        fake.queueStore[2] = queueItem(2)
        store.put(
            serverId,
            SonarrQueueRepository.QUEUE_ITEM_TYPE,
            "99",
            queueItem(99),
            QueueResource.serializer(),
        )

        val result = repository.refresh()

        assertTrue(result is ArrResult.Ok)
        val cached = repository.cachedItems()
        assertEquals(listOf(1, 2), cached.map { it.id }.sorted())
        assertTrue(store.lastFetchedAt(serverId, SonarrQueueRepository.QUEUE_ITEM_TYPE)!! > 0)
        assertEquals(2, repository.observeStatus().first()!!.totalCount)
    }

    @Test
    fun `isStale flips once the TTL elapsed`() = runTest {
        var now = 1_000L
        fakeNow = { now }
        val repository = repo(StandardTestDispatcher(testScheduler))
        fake.queueStore[1] = queueItem(1)
        repository.refresh()
        assertFalse(repository.isStale(ttlMillis = 100))

        now += 101
        assertTrue(repository.isStale(ttlMillis = 100))
    }

    @Test
    fun `remove online drops the server row and the cached row`() = runTest {
        val repository = repo(StandardTestDispatcher(testScheduler))
        fake.queueStore[1] = queueItem(1)
        repository.refresh()

        val result = repository.removeItem(1, episodeId = 101, options = QueueRemoveOptions())

        assertTrue(result is ArrResult.Ok && result.value == QueueWriteOutcome.Synced)
        assertFalse(fake.queueStore.containsKey(1))
        assertTrue(repository.cachedItems().isEmpty())
    }

    @Test
    fun `remove with blocklist leaves the replacement search to the server`() = runTest {
        val repository = repo(StandardTestDispatcher(testScheduler))
        fake.queueStore[1] = queueItem(1)
        repository.refresh()

        val result = repository.removeItem(
            1,
            episodeId = 101,
            options = QueueRemoveOptions(blocklist = true, searchReplacement = true),
        )

        assertTrue(result is ArrResult.Ok)
        // With blocklist the replacement search is the server's job: no command.
        assertTrue(runnerCalls.isEmpty())
    }

    @Test
    fun `remove without blocklist runs the episode search itself`() = runTest {
        val repository = repo(StandardTestDispatcher(testScheduler))
        fake.queueStore[1] = queueItem(1)
        repository.refresh()

        repository.removeItem(1, episodeId = 101, options = QueueRemoveOptions(searchReplacement = true))

        assertEquals(listOf(listOf(SonarrQueueCommands.EPISODE_SEARCH, null, null, "101")), runnerCalls)
    }

    @Test
    fun `remove offline optimistically clears the row and enqueues the DELETE`() = runTest {
        val repository = repo(StandardTestDispatcher(testScheduler))
        fake.queueStore[1] = queueItem(1)
        repository.refresh()
        fake.error = { op -> if (op == "Queue.delete") IOException("offline") else null }

        val result = repository.removeItem(1, episodeId = 101, options = QueueRemoveOptions(blocklist = true))

        assertTrue(result is ArrResult.Ok && result.value is QueueWriteOutcome.Queued)
        assertTrue(repository.cachedItems().isEmpty())
        val pending = actionQueue.pending()
        assertEquals(1, pending.size)
        assertEquals("DELETE", pending.single().method)
        assertEquals(
            "api/v3/queue/1?removeFromClient=true&blocklist=true&skipRedownload=true",
            pending.single().path,
        )
    }

    @Test
    fun `remove offline with re-search enqueues a second command action`() = runTest {
        val repository = repo(StandardTestDispatcher(testScheduler))
        fake.queueStore[1] = queueItem(1)
        repository.refresh()
        fake.error = { op -> if (op == "Queue.delete") IOException("offline") else null }

        repository.removeItem(
            1,
            episodeId = 101,
            options = QueueRemoveOptions(searchReplacement = true),
        )

        val pending = actionQueue.pending()
        assertEquals(2, pending.size)
        val command = pending.first { it.method == "POST" }
        assertEquals("api/v3/command", command.path)
        assertTrue(command.bodyJson!!.contains(SonarrQueueCommands.EPISODE_SEARCH))
        assertTrue(command.bodyJson!!.contains("101"))
    }

    @Test
    fun `non-retryable remove failures surface instead of queueing`() = runTest {
        val repository = repo(StandardTestDispatcher(testScheduler))
        fake.queueStore[1] = queueItem(1)
        repository.refresh()
        fake.error = { op ->
            if (op == "Queue.delete") {
                retrofit2.HttpException(retrofit2.Response.error<Any>(401, "".toResponseBody()))
            } else {
                null
            }
        }

        val result = repository.removeItem(1, episodeId = 101, options = QueueRemoveOptions())

        assertTrue(result is ArrResult.Err && result.error is ArrError.Auth)
        assertTrue(actionQueue.pending().isEmpty())
        assertEquals(1, repository.cachedItems().size)
    }

    @Test
    fun `retry import dispatches DownloadedEpisodesScan online`() = runTest {
        val repository = repo(StandardTestDispatcher(testScheduler))

        val result = repository.retryImport("dl-7", "/downloads/7")

        assertTrue(result is ArrResult.Ok && result.value == QueueWriteOutcome.Synced)
        assertEquals(
            listOf(listOf(SonarrQueueCommands.RETRY_IMPORT, "dl-7", "/downloads/7", null)),
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
        assertEquals("api/v3/command", pending.path)
        assertTrue(pending.bodyJson!!.contains(SonarrQueueCommands.RETRY_IMPORT))
        assertTrue(pending.bodyJson!!.contains("dl-7"))
    }

    @Test
    fun `grab online hits the grab endpoint`() = runTest {
        val repository = repo(StandardTestDispatcher(testScheduler))
        fake.queueStore[1] = queueItem(1)

        val result = repository.grab(1)

        assertTrue(result is ArrResult.Ok && result.value == QueueWriteOutcome.Synced)
        assertEquals(listOf(1), fake.grabbedQueueIds)
    }

    @Test
    fun `grab offline enqueues the grab`() = runTest {
        val repository = repo(StandardTestDispatcher(testScheduler))
        fake.error = { op -> if (op == "QueueAction.create") IOException("offline") else null }

        val result = repository.grab(1)

        assertTrue(result is ArrResult.Ok && result.value is QueueWriteOutcome.Queued)
        val pending = actionQueue.pending().single()
        assertEquals("POST", pending.method)
        assertEquals("api/v3/queue/grab/1", pending.path)
    }

    @Test
    fun `queuedWrites projects removals, grabs, and retry commands`() = runTest {
        val repository = repo(StandardTestDispatcher(testScheduler))
        fake.queueStore[1] = queueItem(1)
        fake.error = { op ->
            if (op == "Queue.delete" || op == "QueueAction.create") IOException("offline") else null
        }
        repository.removeItem(2, episodeId = 101, options = QueueRemoveOptions())
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
        fake.queueStore[1] = queueItem(1)
        repository.refresh()
        runnerError = { IOException("offline") }
        repository.retryImport("dl-1", null)

        assertEquals(setOf(1), repository.queuedItemIds().first())
    }

    @Test
    fun `queuedCount only counts this server's actions`() = runTest {
        val repository = repo(StandardTestDispatcher(testScheduler))
        fake.error = { op -> if (op == "QueueAction.create") IOException("offline") else null }
        repository.grab(1)
        actionQueue.enqueue(
            QueuedAction(
                actionId = ActionId("foreign"),
                serverId = ServerId("other"),
                appType = AppType.SONARR,
                method = "POST",
                path = "api/v3/queue/grab/9",
                description = "foreign",
                enqueuedAtMillis = 0,
            ),
        )

        assertEquals(1, repository.queuedCount().first())
    }
}
