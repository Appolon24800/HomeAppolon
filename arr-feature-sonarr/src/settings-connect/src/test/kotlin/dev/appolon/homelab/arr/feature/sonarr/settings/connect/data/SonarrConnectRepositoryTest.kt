package dev.appolon.homelab.arr.feature.sonarr.settings.connect.data

import dev.appolon.homelab.arr.api.sonarr.FakeSonarrConnect
import dev.appolon.homelab.arr.api.sonarr.FakeSonarrDiscovery
import dev.appolon.homelab.arr.api.sonarr.model.DownloadClientResource
import dev.appolon.homelab.arr.api.sonarr.model.Field
import dev.appolon.homelab.arr.api.sonarr.model.ImportListExclusionResource
import dev.appolon.homelab.arr.api.sonarr.model.ImportListResource
import dev.appolon.homelab.arr.api.sonarr.model.IndexerConfigResource
import dev.appolon.homelab.arr.api.sonarr.model.IndexerResource
import dev.appolon.homelab.arr.api.sonarr.model.MetadataResource
import dev.appolon.homelab.arr.api.sonarr.model.NotificationResource
import dev.appolon.homelab.arr.api.sonarr.model.RemotePathMappingResource
import dev.appolon.homelab.arr.api.sonarr.model.TagResource
import dev.appolon.homelab.arr.core.ArrResult
import dev.appolon.homelab.arr.core.ServerId
import dev.appolon.homelab.arr.feature.sonarr.library.data.WriteOutcome
import dev.appolon.homelab.arr.feature.sonarr.settings.connect.FakeConnectActionQueue
import dev.appolon.homelab.arr.feature.sonarr.settings.connect.FakeConnectCommandApi
import dev.appolon.homelab.arr.feature.sonarr.settings.connect.FakeConnectEntityStore
import dev.appolon.homelab.arr.feature.sonarr.settings.connect.FakeConnectMetadataApi
import dev.appolon.homelab.arr.feature.sonarr.settings.connect.FakeConnectTagApi
import java.io.IOException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SonarrConnectRepositoryTest {

    private lateinit var connect: FakeSonarrConnect
    private lateinit var discovery: FakeSonarrDiscovery
    private lateinit var tagApi: FakeConnectTagApi
    private lateinit var metadataApi: FakeConnectMetadataApi
    private lateinit var commandApi: FakeConnectCommandApi
    private lateinit var store: FakeConnectEntityStore
    private lateinit var queue: FakeConnectActionQueue

    @Before
    fun setUp() {
        connect = FakeSonarrConnect()
        discovery = FakeSonarrDiscovery()
        tagApi = FakeConnectTagApi()
        metadataApi = FakeConnectMetadataApi()
        commandApi = FakeConnectCommandApi()
        store = FakeConnectEntityStore()
        queue = FakeConnectActionQueue()
    }

    private fun repository(scheduler: TestCoroutineScheduler) = SonarrConnectRepository(
        serverId = ServerId("s1"),
        store = store,
        indexerApi = connect.indexerApi,
        indexerConfigApi = connect.indexerConfigApi,
        indexerFlagApi = connect.indexerFlagApi,
        downloadClientApi = connect.downloadClientApi,
        downloadClientConfigApi = connect.downloadClientConfigApi,
        notificationApi = connect.notificationApi,
        remotePathMappingApi = connect.remotePathMappingApi,
        importListApi = discovery.importList,
        importListConfigApi = discovery.importListConfigApi,
        importListExclusionApi = discovery.importListExclusion,
        metadataApi = metadataApi,
        commandApi = commandApi,
        tagApi = tagApi,
        actionQueue = queue,
        now = { 1_000L },
        ioContext = StandardTestDispatcher(scheduler),
    )

    @Test
    fun `refresh populates every provider table`() = runTest {
        connect.indexerStore[1] = IndexerResource(id = 1, name = "Prowlarr", priority = 5)
        connect.downloadClientStore[2] = DownloadClientResource(id = 2, name = "qBittorrent")
        connect.remotePathMappingStore[3] = RemotePathMappingResource(id = 3, host = "seedbox")
        discovery.importListStore[4] = ImportListResource(id = 4, name = "Trakt List")
        discovery.importListExclusionStore[5] = ImportListExclusionResource(id = 5, title = "Excluded")
        connect.notificationStore[6] = NotificationResource(id = 6, name = "Discord")
        metadataApi.rows += MetadataResource(id = 7, name = "Kodi")
        tagApi.tags += TagResource(id = 8, label = "anime")
        val repository = repository(testScheduler)

        val result = repository.refresh()
        advanceUntilIdle()

        assertTrue(result is ArrResult.Ok)
        assertEquals(listOf("Prowlarr"), repository.cachedIndexers().map { it.name })
        assertEquals(listOf("qBittorrent"), repository.cachedDownloadClients().map { it.name })
        val mappings = store.getAll(
            ServerId("s1"),
            SonarrConnectRepository.REMOTE_PATH_MAPPING_TYPE,
            RemotePathMappingResource.serializer(),
        )
        assertEquals(listOf("seedbox"), mappings.map { it.host })
        assertEquals(listOf("Trakt List"), repository.cachedImportLists().map { it.name })
        assertEquals(listOf("Excluded"), repository.cachedExclusions().map { it.title })
        assertEquals(listOf("Discord"), repository.cachedNotifications().map { it.name })
        assertEquals(
            listOf("Kodi"),
            store.getAll(
                ServerId("s1"),
                SonarrConnectRepository.METADATA_TYPE,
                MetadataResource.serializer(),
            ).map { it.name },
        )
        val tags = repository.tags()
        assertTrue(tags is ArrResult.Ok)
        assertEquals(listOf("anime"), (tags as ArrResult.Ok).value.map { it.label })
    }

    @Test
    fun `saveIndexer creates online and caches the server row`() = runTest {
        val repository = repository(testScheduler)
        val saved = repository.saveIndexer(IndexerResource(name = "NZBgeek", priority = 30))
        advanceUntilIdle()

        assertTrue((saved as ArrResult.Ok).value is WriteOutcome.Synced)
        assertEquals(1, connect.indexerStore.values.single().id)
        assertEquals("NZBgeek", repository.cachedIndexers().single().name)
    }

    @Test
    fun `offline indexer create queues with a temporary row`() = runTest {
        connect.error = { if (it == "Indexer.create") IOException("offline") else null }
        val repository = repository(testScheduler)
        val saved = repository.saveIndexer(IndexerResource(name = "Offline indexer"))
        advanceUntilIdle()

        assertTrue((saved as ArrResult.Ok).value is WriteOutcome.Queued)
        assertEquals(1, queue.pending().size)
        assertEquals("POST", queue.pending().single().method)
        assertEquals("api/v3/indexer", queue.pending().single().path)
        val cached = repository.cachedIndexers().single()
        assertTrue(cached.id < 0)
        assertEquals("Offline indexer", cached.name)
    }

    @Test
    fun `offline indexer update queues keeping the real id`() = runTest {
        connect.indexerStore[1] = IndexerResource(id = 1, name = "Prowlarr")
        connect.error = { if (it == "Indexer.update") IOException("offline") else null }
        val repository = repository(testScheduler)
        val saved = repository.saveIndexer(IndexerResource(id = 1, name = "Renamed"))
        advanceUntilIdle()

        assertTrue((saved as ArrResult.Ok).value is WriteOutcome.Queued)
        assertEquals("PUT", queue.pending().single().method)
        assertEquals("api/v3/indexer/1", queue.pending().single().path)
        assertEquals("Renamed", repository.cachedIndexers().single().name)
    }

    @Test
    fun `delete queues only when the network fails`() = runTest {
        connect.indexerStore[1] = IndexerResource(id = 1, name = "Prowlarr")
        val repository = repository(testScheduler)

        val removed = repository.removeIndexer(1)
        advanceUntilIdle()
        assertTrue((removed as ArrResult.Ok).value is WriteOutcome.Synced)
        assertTrue(queue.pending().isEmpty())

        connect.indexerStore[1] = IndexerResource(id = 1, name = "Prowlarr")
        repository.refresh()
        advanceUntilIdle()
        connect.error = { if (it == "Indexer.delete") IOException("offline") else null }
        val queuedRemoval = repository.removeIndexer(1)
        advanceUntilIdle()

        assertTrue((queuedRemoval as ArrResult.Ok).value is WriteOutcome.Queued)
        assertEquals("DELETE", queue.pending().single().method)
        assertTrue(repository.cachedIndexers().isEmpty())
    }

    @Test
    fun `setDownloadClientEnabled writes the toggled row`() = runTest {
        connect.downloadClientStore[2] = DownloadClientResource(id = 2, name = "qBittorrent")
        val repository = repository(testScheduler)
        repository.refresh()
        advanceUntilIdle()

        val result = repository.setDownloadClientEnabled(2, false)
        advanceUntilIdle()

        assertTrue(result is ArrResult.Ok)
        assertEquals(false, connect.downloadClientStore[2]?.enable)
        assertEquals(false, repository.cachedDownloadClients().single().enable)
    }

    @Test
    fun `saveNotification round-trips the event matrix`() = runTest {
        val repository = repository(testScheduler)
        val saved = repository.saveNotification(
            NotificationResource(
                name = "Discord",
                onGrab = true,
                onDownload = true,
                onSeriesDelete = true,
                onManualInteractionRequired = true,
            ),
        )
        advanceUntilIdle()

        assertTrue(saved is ArrResult.Ok)
        val stored = connect.notificationStore.values.single()
        assertTrue(stored.onGrab)
        assertTrue(stored.onDownload)
        assertTrue(stored.onSeriesDelete)
        assertTrue(stored.onManualInteractionRequired)
    }

    @Test
    fun `test probes never queue`() = runTest {
        connect.indexerStore[1] = IndexerResource(id = 1, name = "Prowlarr")
        connect.error = { if (it.startsWith("Indexer.test")) IOException("offline") else null }
        val repository = repository(testScheduler)

        val single = repository.testIndexer(IndexerResource(name = "draft"))
        val all = repository.testAllIndexers()
        advanceUntilIdle()

        assertTrue(single is ArrResult.Err)
        assertTrue(all is ArrResult.Err)
        assertTrue(queue.pending().isEmpty())
    }

    @Test
    fun `syncImportLists posts the ImportListSync command`() = runTest {
        val repository = repository(testScheduler)
        val result = repository.syncImportLists()
        advanceUntilIdle()

        assertTrue(result is ArrResult.Ok)
        assertEquals("ImportListSync", commandApi.started.single().name)
    }

    @Test
    fun `import list save and test flow through`() = runTest {
        val repository = repository(testScheduler)
        val saved = repository.saveImportList(
            ImportListResource(name = "Trakt", rootFolderPath = "/tv", enableAutomaticAdd = true),
        )
        val tested = repository.testAllImportLists()
        advanceUntilIdle()

        assertTrue(saved is ArrResult.Ok)
        assertTrue(tested is ArrResult.Ok)
        assertEquals("Trakt", repository.cachedImportLists().single().name)
        assertEquals(1, discovery.testedAllCount.single())
    }

    @Test
    fun `offline exclusion create queues and undo cancels the queued create`() = runTest {
        discovery.error = { if (it == "ImportListExclusion.create") IOException("offline") else null }
        val repository = repository(testScheduler)
        val added = repository.addExclusion(
            ImportListExclusionResource(tvdbId = 1234, title = "Old Show"),
        )
        advanceUntilIdle()

        assertTrue((added as ArrResult.Ok).value is WriteOutcome.Queued)
        assertEquals(1, queue.pending().size)
        val tempRow = repository.cachedExclusions().single()
        assertTrue(tempRow.id < 0)

        val removed = repository.removeExclusion(tempRow)
        advanceUntilIdle()

        assertTrue(removed is ArrResult.Ok)
        assertTrue("queued create cancelled", queue.pending().isEmpty())
        assertTrue(repository.cachedExclusions().isEmpty())
    }

    @Test
    fun `schemas cache under their own long TTL`() = runTest {
        connect.indexerSchemas += IndexerResource(
            name = "Newznab",
            implementation = "Newznab",
            fields = listOf(
                Field(name = "baseUrl", value = JsonPrimitive("https://example")),
            ),
        )
        val repository = repository(testScheduler)

        val first = repository.indexerSchemas()
        advanceUntilIdle()
        val second = repository.indexerSchemas()
        advanceUntilIdle()

        assertTrue(first is ArrResult.Ok)
        assertTrue(second is ArrResult.Ok)
        assertEquals(1, (second as ArrResult.Ok).value.size)
        // The schema row is keyed by implementation, not the wire id 0.
        val cached = store.getAll(
            ServerId("s1"),
            SonarrConnectRepository.INDEXER_SCHEMA_TYPE,
            IndexerResource.serializer(),
        )
        assertEquals(listOf("Newznab"), cached.map { it.implementation })
    }

    @Test
    fun `indexer options save through the config endpoint`() = runTest {
        val repository = repository(testScheduler)
        val saved = repository.saveIndexerConfig(
            IndexerConfigResource(minimumAge = 45, rssSyncInterval = 20),
        )
        advanceUntilIdle()

        assertTrue(saved is ArrResult.Ok)
        assertEquals(45, connect.indexerConfig.minimumAge)
        assertEquals(45, repository.cachedIndexerConfig()?.minimumAge)
    }

    @Test
    fun `queued count filters this group's paths`() = runTest {
        val repository = repository(testScheduler)
        queue.enqueue(queue.action("a1", path = "api/v3/indexer/1"))
        queue.enqueue(queue.action("a2", path = "api/v3/series/editor"))
        advanceUntilIdle()

        val count = repository.queuedCount().first()

        assertEquals(1, count)
    }
}
