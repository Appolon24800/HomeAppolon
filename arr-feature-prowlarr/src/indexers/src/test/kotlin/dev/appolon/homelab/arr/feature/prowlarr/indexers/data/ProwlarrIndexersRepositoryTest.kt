package dev.appolon.homelab.arr.feature.prowlarr.indexers.data

import dev.appolon.homelab.arr.api.prowlarr.FakeProwlarrIndexers
import dev.appolon.homelab.arr.api.prowlarr.model.AppProfileResource
import dev.appolon.homelab.arr.api.prowlarr.model.ApplicationResource
import dev.appolon.homelab.arr.api.prowlarr.model.ApplyTags
import dev.appolon.homelab.arr.api.prowlarr.model.IndexerBulkResource
import dev.appolon.homelab.arr.api.prowlarr.model.IndexerCategory
import dev.appolon.homelab.arr.api.prowlarr.model.IndexerProxyResource
import dev.appolon.homelab.arr.api.prowlarr.model.IndexerResource
import dev.appolon.homelab.arr.api.prowlarr.model.IndexerStatusResource
import dev.appolon.homelab.arr.api.prowlarr.model.TagResource
import dev.appolon.homelab.arr.core.ArrError
import dev.appolon.homelab.arr.core.ArrResult
import dev.appolon.homelab.arr.core.ServerId
import dev.appolon.homelab.arr.feature.prowlarr.indexers.FakeActionQueue
import dev.appolon.homelab.arr.feature.prowlarr.indexers.FakeEntityStore
import dev.appolon.homelab.arr.feature.prowlarr.indexers.fakeIndexersApis
import java.io.IOException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ProwlarrIndexersRepositoryTest {

    private val serverId = ServerId("s1")
    private val io = UnconfinedTestDispatcher()
    private lateinit var fake: FakeProwlarrIndexers
    private lateinit var store: FakeEntityStore
    private lateinit var queue: FakeActionQueue
    private lateinit var repository: ProwlarrIndexersRepository
    private var nowMs = 1_000L
    private var syncCommands = mutableListOf<String>()
    private var syncResult: ArrResult<Unit> = ArrResult.ok(Unit)

    private fun newRepository(): ProwlarrIndexersRepository =
        ProwlarrIndexersRepository(
            serverId = serverId,
            store = store,
            apis = fakeIndexersApis(
                fake,
                commandRunner = { name ->
                    syncCommands += name
                    syncResult
                },
            ),
            actionQueue = queue,
            now = { nowMs },
            io = io,
        )

    @Before
    fun setUp() {
        fake = FakeProwlarrIndexers()
        store = FakeEntityStore()
        queue = FakeActionQueue()
        syncCommands = mutableListOf()
        repository = newRepository()
        listOf(
            IndexerResource(
                id = 1,
                name = "Alpha",
                enable = true,
                priority = 25,
                tags = listOf(10),
            ),
            IndexerResource(id = 2, name = "Beta", enable = false, priority = 50),
        ).forEach { fake.indexers[it.id] = it }
        fake.indexerStatuses += listOf(
            IndexerStatusResource(id = 1, indexerId = 1, disabledTill = "2030-01-01T00:00:00Z"),
        )
        fake.tags[10] = TagResource(id = 10, label = "hd")
        fake.defaultCategories += listOf(
            IndexerCategory(
                id = 2000,
                name = "Movies",
                subCategories = listOf(IndexerCategory(id = 2010, name = "Foreign")),
            ),
        )
    }

    @Test
    fun `refresh fills the cache stamps it and reconciles removals`() = runTest {
        val result = repository.refresh()
        assertTrue(result is ArrResult.Ok)
        assertEquals(listOf("Alpha", "Beta"), repository.cachedIndexers().map { it.name })
        assertEquals(1_000L, store.lastFetchedAt(serverId, ProwlarrIndexersRepository.INDEXER_TYPE))
        assertTrue(!repository.isStale())

        fake.indexers.remove(1)
        nowMs += ProwlarrIndexersRepository.INDEXER_TTL_MILLIS + 1
        repository.refresh()
        assertEquals(listOf("Beta"), repository.cachedIndexers().map { it.name })
        nowMs += ProwlarrIndexersRepository.INDEXER_TTL_MILLIS + 1
        assertTrue(repository.isStale())
    }

    @Test
    fun `refresh also stores statuses and tags`() = runTest {
        repository.refresh()
        assertEquals(1, repository.observeStatuses().first().size)
        assertEquals(listOf("hd"), repository.observeTags().first().map { it.label })
    }

    @Test
    fun `schemas and categories cache under their own entity types`() = runTest {
        fake.indexerSchemas += IndexerResource(
            definitionName = "iptorrents",
            name = " IPTorrents",
            fields = emptyList(),
        )
        val schemas = repository.schemas()
        assertTrue((schemas as ArrResult.Ok).value.size == 1)
        assertEquals(1_000L, store.lastFetchedAt(serverId, ProwlarrIndexersRepository.SCHEMA_TYPE))

        val categories = repository.defaultCategories()
        assertTrue((categories as ArrResult.Ok).value.first().id == 2000)
    }

    @Test
    fun `setEnabled syncs online and writes the returned row`() = runTest {
        repository.refresh()
        val outcome = repository.setEnabled(2, true)
        assertTrue((outcome as ArrResult.Ok).value is WriteOutcome.Synced)
        assertEquals(true, repository.cachedIndexers().first { it.id == 2 }.enable)
        assertTrue(queue.pending().isEmpty())
    }

    @Test
    fun `setEnabled offline applies optimistically and enqueues the update`() = runTest {
        repository.refresh()
        fake.failure = { call -> if (call.operation == "updateIndexer") IOException("offline") else null }
        val outcome = repository.setEnabled(2, true)
        val queued = (outcome as ArrResult.Ok).value as WriteOutcome.Queued
        assertNotNull(queued.actionId)

        assertEquals(true, repository.cachedIndexers().first { it.id == 2 }.enable)

        val action = queue.pending().single()
        assertEquals("PUT", action.method)
        assertEquals("api/v1/indexer/2", action.path)
        assertEquals(serverId, action.serverId)
        assertTrue(action.bodyJson!!.contains("\"enable\":true"))
        assertEquals(setOf(2), repository.queuedIndexerIds().first())
    }

    @Test
    fun `offline creates queue without a guessed id row`() = runTest {
        repository.refresh()
        fake.failure = { call -> if (call.operation == "createIndexer") IOException("offline") else null }
        val resource = IndexerResource(name = "New", enable = true)
        val outcome = repository.saveIndexer(resource)
        assertTrue((outcome as ArrResult.Ok).value is WriteOutcome.Queued)
        assertTrue(repository.cachedIndexers().none { it.name == "New" })

        val action = queue.pending().single()
        assertEquals("POST", action.method)
        assertEquals("api/v1/indexer", action.path)
        assertTrue(action.bodyJson!!.contains("\"name\":\"New\""))
    }

    @Test
    fun `bulk edit online merges with ApplyTags semantics`() = runTest {
        repository.refresh()
        val bulk = IndexerBulkResource(
            ids = listOf(1, 2),
            enable = true,
            priority = 10,
            tags = listOf(20),
            applyTags = ApplyTags.ADD,
        )
        val outcome = repository.bulkEdit(bulk)
        assertTrue((outcome as ArrResult.Ok).value is WriteOutcome.Synced)
        val alpha = repository.cachedIndexers().first { it.id == 1 }
        val beta = repository.cachedIndexers().first { it.id == 2 }
        assertEquals(listOf(10, 20), alpha.tags)
        assertEquals(listOf(20), beta.tags)
        assertEquals(10, beta.priority)
    }

    @Test
    fun `bulk edit offline enqueues and merges optimistically`() = runTest {
        repository.refresh()
        fake.failure = { call -> if (call.operation == "updateIndexers") IOException("offline") else null }
        val bulk = IndexerBulkResource(
            ids = listOf(1),
            tags = listOf(10),
            applyTags = ApplyTags.REMOVE,
        )
        val outcome = repository.bulkEdit(bulk)
        assertTrue((outcome as ArrResult.Ok).value is WriteOutcome.Queued)
        assertEquals(emptyList<Int>(), repository.cachedIndexers().first { it.id == 1 }.tags)

        val action = queue.pending().single()
        assertEquals("PUT", action.method)
        assertEquals("api/v1/indexer/bulk", action.path)
        assertEquals(setOf(1), repository.queuedIndexerIds().first())
    }

    @Test
    fun `single and bulk deletes queue with the right shapes`() = runTest {
        repository.refresh()
        fake.indexers[3] = IndexerResource(id = 3, name = "Gamma")
        repository.refresh()
        fake.failure = { call ->
            if (call.operation == "deleteIndexer" || call.operation == "deleteIndexers") {
                IOException("offline")
            } else {
                null
            }
        }
        val single = repository.deleteIndexers(listOf(1))
        assertTrue((single as ArrResult.Ok).value is WriteOutcome.Queued)
        assertTrue(repository.cachedIndexers().none { it.id == 1 })
        assertEquals("api/v1/indexer/1", queue.pending()[0].path)
        assertEquals(null, queue.pending()[0].bodyJson)

        val bulk = repository.deleteIndexers(listOf(2, 3))
        assertTrue((bulk as ArrResult.Ok).value is WriteOutcome.Queued)
        assertTrue(repository.cachedIndexers().isEmpty())
        assertEquals("api/v1/indexer/bulk", queue.pending()[1].path)
        assertTrue(queue.pending()[1].bodyJson!!.contains("[2,3]"))
        assertEquals(setOf(1, 2, 3), repository.queuedIndexerIds().first())
    }

    @Test
    fun `probes never queue`() = runTest {
        repository.refresh()
        fake.failure = { IOException("offline") }
        assertTrue(repository.testIndexer(IndexerResource(name = "x")) is ArrResult.Err)
        assertTrue(repository.testAllIndexers() is ArrResult.Err)
        assertTrue(queue.pending().isEmpty())
    }

    @Test
    fun `ensureTagIds reuses existing labels and creates missing ones`() = runTest {
        repository.refresh()
        val ids = repository.ensureTagIds(listOf("hd", "new-tag"))
        assertEquals(listOf(10, 101), (ids as ArrResult.Ok).value)
        assertEquals("new-tag", fake.tags[101]?.label)
    }

    @Test
    fun `proxies CRUD offline queue under the proxy paths`() = runTest {
        repository.refreshProxies()
        fake.indexerProxies[5] = IndexerProxyResource(id = 5, name = "Socks")
        repository.refreshProxies()

        fake.failure = { call ->
            if (call.operation.startsWith("updateIndexerProxy") ||
                call.operation.startsWith("createIndexerProxy")
            ) {
                IOException("offline")
            } else {
                null
            }
        }
        val saved = repository.saveProxy(IndexerProxyResource(id = 5, name = "Socks2"))
        assertTrue((saved as ArrResult.Ok).value is WriteOutcome.Queued)
        assertEquals("Socks2", repository.observeProxies().first().single().name)
        assertEquals("api/v1/indexerproxy/5", queue.pending().single().path)

        val added = repository.saveProxy(IndexerProxyResource(name = "Http"))
        assertTrue((added as ArrResult.Ok).value is WriteOutcome.Queued)
        assertEquals("api/v1/indexerproxy", queue.pending()[1].path)
        assertTrue(queue.pending()[1].bodyJson!!.contains("\"name\":\"Http\""))
    }

    @Test
    fun `app profiles save and delete through the queue when offline`() = runTest {
        repository.appProfiles()
        fake.failure = { call ->
            if (call.operation == "createAppProfile" || call.operation == "deleteAppProfile") {
                IOException("offline")
            } else {
                null
            }
        }
        val saved = repository.saveAppProfile(AppProfileResource(name = "Standard", minimumSeeders = 5))
        assertTrue((saved as ArrResult.Ok).value is WriteOutcome.Queued)
        assertEquals("api/v1/appprofile", queue.pending()[0].path)

        val deleted = repository.deleteAppProfile(9)
        assertTrue((deleted as ArrResult.Ok).value is WriteOutcome.Queued)
        assertEquals("api/v1/appprofile/9", queue.pending()[1].path)
    }

    @Test
    fun `applications sync dispatches the command and queues when offline`() = runTest {
        val online = repository.syncApplications()
        assertTrue((online as ArrResult.Ok).value is WriteOutcome.Synced)
        assertEquals(listOf("ApplicationSync"), syncCommands)

        syncResult = ArrResult.err(ArrError.Network(IOException("down")))
        val offline = repository.syncApplications()
        assertTrue((offline as ArrResult.Ok).value is WriteOutcome.Queued)
        val action = queue.pending().single()
        assertEquals("POST", action.method)
        assertEquals("api/v1/command", action.path)
        assertTrue(action.bodyJson!!.contains("ApplicationSync"))
    }

    @Test
    fun `applications save offline queues under the applications path`() = runTest {
        repository.refreshApplications()
        fake.failure = { call -> if (call.operation == "updateApplication") IOException("off") else null }
        val saved = repository.saveApplication(ApplicationResource(id = 3, name = "Radarr"))
        assertTrue((saved as ArrResult.Ok).value is WriteOutcome.Queued)
        assertEquals("api/v1/applications/3", queue.pending().single().path)
    }

    @Test
    fun `queuedCount counts this server only`() = runTest {
        queue.enqueue(queue.action("a"))
        queue.enqueue(queue.action("b", serverId = ServerId("other")))
        assertEquals(1, repository.queuedCount().first())
    }

    @Test
    fun `retryable classification keeps non network failures terminal`() = runTest {
        repository.refresh()
        fake.errorStatus = { call -> if (call.operation == "updateIndexer") 400 else null }
        val outcome = repository.setEnabled(2, true)
        assertTrue(outcome is ArrResult.Err)
        assertTrue((outcome as ArrResult.Err).error is ArrError.Http)
        assertTrue(queue.pending().isEmpty())
    }
}
