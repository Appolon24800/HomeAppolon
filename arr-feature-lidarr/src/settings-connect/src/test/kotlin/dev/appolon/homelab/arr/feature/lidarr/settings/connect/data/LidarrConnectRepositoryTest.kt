package dev.appolon.homelab.arr.feature.lidarr.settings.connect.data

import dev.appolon.homelab.arr.api.lidarr.FakeLidarrDiscovery
import dev.appolon.homelab.arr.api.lidarr.FakeLidarrQueue
import dev.appolon.homelab.arr.api.lidarr.FakeLidarrSystem
import dev.appolon.homelab.arr.api.lidarr.model.DownloadClientResource
import dev.appolon.homelab.arr.api.lidarr.model.Field
import dev.appolon.homelab.arr.api.lidarr.model.ImportListExclusionResource
import dev.appolon.homelab.arr.api.lidarr.model.ImportListResource
import dev.appolon.homelab.arr.api.lidarr.model.IndexerConfigResource
import dev.appolon.homelab.arr.api.lidarr.model.IndexerResource
import dev.appolon.homelab.arr.api.lidarr.model.MetadataResource
import dev.appolon.homelab.arr.api.lidarr.model.NotificationResource
import dev.appolon.homelab.arr.api.lidarr.model.PrivacyLevel
import dev.appolon.homelab.arr.api.lidarr.model.RemotePathMappingResource
import dev.appolon.homelab.arr.api.lidarr.model.TagResource
import dev.appolon.homelab.arr.core.ArrResult
import dev.appolon.homelab.arr.core.ServerId
import dev.appolon.homelab.arr.feature.lidarr.library.data.WriteOutcome
import dev.appolon.homelab.arr.feature.lidarr.settings.connect.FakeConnectActionQueue
import dev.appolon.homelab.arr.data.testing.FakeEntityStore
import dev.appolon.homelab.arr.feature.lidarr.settings.connect.connectRepository
import java.io.IOException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class LidarrConnectRepositoryTest {

    private lateinit var system: FakeLidarrSystem
    private lateinit var discovery: FakeLidarrDiscovery
    private lateinit var commandQueue: FakeLidarrQueue
    private lateinit var store: FakeEntityStore
    private lateinit var actionQueue: FakeConnectActionQueue

    @Before
    fun setUp() {
        system = FakeLidarrSystem()
        discovery = FakeLidarrDiscovery()
        commandQueue = FakeLidarrQueue()
        store = FakeEntityStore()
        actionQueue = FakeConnectActionQueue()
    }

    private fun repository() = connectRepository(
        serverId = ServerId("s1"),
        store = store,
        system = system,
        discovery = discovery,
        queue = commandQueue,
        actionQueue = actionQueue,
        now = { 1_000L },
    )

    @Test
    fun `refresh populates every provider table`() = runTest {
        system.indexers[1] = IndexerResource(id = 1, name = "Prowlarr", priority = 5)
        system.downloadClients[2] = DownloadClientResource(id = 2, name = "qBittorrent")
        system.remotePathMappings[3] = RemotePathMappingResource(id = 3, host = "seedbox")
        discovery.importLists[4] = ImportListResource(id = 4, name = "Headphones")
        discovery.importListExclusions[5] =
            ImportListExclusionResource(id = 5, foreignId = "lidarr:1", artistName = "Excluded")
        system.notifications[6] = NotificationResource(id = 6, name = "Discord")
        system.metadatas[7] = MetadataResource(id = 7, name = "Kodi")
        system.tags[8] = TagResource(id = 8, label = "flac")
        val repository = repository()

        val result = repository.refresh()

        assertTrue(result is ArrResult.Ok)
        assertEquals(listOf("Prowlarr"), repository.cachedIndexers().map { it.name })
        assertEquals(listOf("qBittorrent"), repository.cachedDownloadClients().map { it.name })
        val mappings = store.getAll(
            ServerId("s1"),
            LidarrConnectRepository.REMOTE_PATH_MAPPING_TYPE,
            RemotePathMappingResource.serializer(),
        )
        assertEquals(listOf("seedbox"), mappings.map { it.host })
        assertEquals(listOf("Headphones"), repository.cachedImportLists().map { it.name })
        assertEquals(listOf("Excluded"), repository.cachedExclusions().map { it.artistName })
        assertEquals(listOf("Discord"), repository.cachedNotifications().map { it.name })
        assertEquals(
            listOf("Kodi"),
            store.getAll(
                ServerId("s1"),
                LidarrConnectRepository.METADATA_TYPE,
                MetadataResource.serializer(),
            ).map { it.name },
        )
        val tags = repository.tags()
        assertTrue(tags is ArrResult.Ok)
        assertEquals(listOf("flac"), (tags as ArrResult.Ok).value.map { it.label })
    }

    @Test
    fun `saveIndexer creates online and caches the server row`() = runTest {
        val repository = repository()
        val saved = repository.saveIndexer(IndexerResource(name = "IPTorrents", priority = 30))

        assertTrue((saved as ArrResult.Ok).value is WriteOutcome.Synced)
        assertEquals(1, system.indexers.values.single().id)
        assertEquals("IPTorrents", repository.cachedIndexers().single().name)
    }

    @Test
    fun `offline indexer create queues with a temporary row`() = runTest {
        system.error = { if (it == "addIndexer") IOException("offline") else null }
        val repository = repository()
        val saved = repository.saveIndexer(IndexerResource(name = "Offline indexer"))

        assertTrue((saved as ArrResult.Ok).value is WriteOutcome.Queued)
        assertEquals(1, actionQueue.pending().size)
        assertEquals("POST", actionQueue.pending().single().method)
        assertEquals("api/v1/indexer", actionQueue.pending().single().path)
        val cached = repository.cachedIndexers().single()
        assertTrue(cached.id < 0)
        assertEquals("Offline indexer", cached.name)
    }

    @Test
    fun `offline indexer update queues keeping the real id`() = runTest {
        system.indexers[1] = IndexerResource(id = 1, name = "Prowlarr")
        system.error = { if (it == "updateIndexer") IOException("offline") else null }
        val repository = repository()
        val saved = repository.saveIndexer(IndexerResource(id = 1, name = "Renamed"))

        assertTrue((saved as ArrResult.Ok).value is WriteOutcome.Queued)
        assertEquals("PUT", actionQueue.pending().single().method)
        assertEquals("api/v1/indexer/1", actionQueue.pending().single().path)
        assertEquals("Renamed", repository.cachedIndexers().single().name)
    }

    @Test
    fun `delete queues only when the network fails`() = runTest {
        system.indexers[1] = IndexerResource(id = 1, name = "Prowlarr")
        val repository = repository()

        val removed = repository.removeIndexer(1)
        assertTrue((removed as ArrResult.Ok).value is WriteOutcome.Synced)
        assertTrue(actionQueue.pending().isEmpty())

        system.indexers[1] = IndexerResource(id = 1, name = "Prowlarr")
        repository.refresh()
        system.error = { if (it == "deleteIndexer") IOException("offline") else null }
        val queuedRemoval = repository.removeIndexer(1)

        assertTrue((queuedRemoval as ArrResult.Ok).value is WriteOutcome.Queued)
        assertEquals("DELETE", actionQueue.pending().single().method)
        assertTrue(repository.cachedIndexers().isEmpty())
    }

    @Test
    fun `setDownloadClientEnabled writes the toggled row`() = runTest {
        system.downloadClients[2] = DownloadClientResource(id = 2, name = "qBittorrent")
        val repository = repository()
        repository.refresh()

        val result = repository.setDownloadClientEnabled(2, false)

        assertTrue(result is ArrResult.Ok)
        assertEquals(false, system.downloadClients[2]?.enable)
        assertEquals(false, repository.cachedDownloadClients().single().enable)
    }

    @Test
    fun `saveNotification round-trips the event matrix`() = runTest {
        val repository = repository()
        val saved = repository.saveNotification(
            NotificationResource(
                name = "Discord",
                onGrab = true,
                onReleaseImport = true,
                onArtistDelete = true,
                onImportFailure = true,
            ),
        )

        assertTrue(saved is ArrResult.Ok)
        val stored = system.notifications.values.single()
        assertTrue(stored.onGrab)
        assertTrue(stored.onReleaseImport)
        assertTrue(stored.onArtistDelete)
        assertTrue(stored.onImportFailure)
    }

    @Test
    fun `test probes never queue`() = runTest {
        system.indexers[1] = IndexerResource(id = 1, name = "Prowlarr")
        system.error = {
            if (it == "testIndexer" || it == "testAllIndexers") IOException("offline") else null
        }
        val repository = repository()

        val single = repository.testIndexer(IndexerResource(name = "draft"))
        val all = repository.testAllIndexers()

        assertTrue(single is ArrResult.Err)
        assertTrue(all is ArrResult.Err)
        assertTrue(actionQueue.pending().isEmpty())
    }

    @Test
    fun `syncImportLists posts the ImportListSync command`() = runTest {
        val repository = repository()
        val result = repository.syncImportLists()

        assertTrue(result is ArrResult.Ok)
        assertEquals("ImportListSync", commandQueue.commands.values.single().name)
    }

    @Test
    fun `import list save and test flow through`() = runTest {
        val repository = repository()
        val saved = repository.saveImportList(
            ImportListResource(name = "Headphones", rootFolderPath = "/music", enableAutomaticAdd = true),
        )
        val tested = repository.testAllImportLists()

        assertTrue(saved is ArrResult.Ok)
        assertTrue(tested is ArrResult.Ok)
        assertEquals("Headphones", repository.cachedImportLists().single().name)
        assertEquals(1, discovery.testAllCount)
    }

    @Test
    fun `offline exclusion create queues and undo cancels the queued create`() = runTest {
        discovery.error = { if (it == "addImportListExclusion") IOException("offline") else null }
        val repository = repository()
        val added = repository.addExclusion(
            ImportListExclusionResource(foreignId = "lidarr:1234", artistName = "Old Artist"),
        )

        assertTrue((added as ArrResult.Ok).value is WriteOutcome.Queued)
        assertEquals(1, actionQueue.pending().size)
        val tempRow = repository.cachedExclusions().single()
        assertTrue(tempRow.id < 0)

        val removed = repository.removeExclusion(tempRow)

        assertTrue(removed is ArrResult.Ok)
        assertTrue("queued create cancelled", actionQueue.pending().isEmpty())
        assertTrue(repository.cachedExclusions().isEmpty())
    }

    @Test
    fun `schemas cache under their own long TTL`() = runTest {
        system.indexers[1] = IndexerResource(
            name = "Newznab",
            implementation = "Newznab",
            presets = emptyList(),
            fields = listOf(
                Field(name = "baseUrl", value = JsonPrimitive("https://example")),
            ),
        )
        val repository = repository()

        val first = repository.indexerSchemas()
        val second = repository.indexerSchemas()

        assertTrue(first is ArrResult.Ok)
        assertTrue(second is ArrResult.Ok)
        assertEquals(1, (second as ArrResult.Ok).value.size)
        // The schema row is keyed by implementation, not the wire id 0.
        val cached = store.getAll(
            ServerId("s1"),
            LidarrConnectRepository.INDEXER_SCHEMA_TYPE,
            IndexerResource.serializer(),
        )
        assertEquals(listOf("Newznab"), cached.map { it.implementation })
    }

    @Test
    fun `indexer options save through the config endpoint`() = runTest {
        val repository = repository()
        val saved = repository.saveIndexerConfig(
            IndexerConfigResource(minimumAge = 45, rssSyncInterval = 20),
        )

        assertTrue(saved is ArrResult.Ok)
        assertEquals(45, system.indexerConfig.minimumAge)
        assertEquals(45, repository.cachedIndexerConfig()?.minimumAge)
    }

    @Test
    fun `queued count filters this group's paths`() = runTest {
        val repository = repository()
        actionQueue.enqueue(actionQueue.action("a1", path = "api/v1/indexer/1"))
        actionQueue.enqueue(actionQueue.action("a2", path = "api/v1/artist/editor"))

        val count = repository.queuedCount().first()

        assertEquals(1, count)
    }

    // --------------------------------------------------------------- secrets

    private fun secretIndexer(id: Int = 0, name: String = "IPTorrents") = IndexerResource(
        id = id,
        name = name,
        fields = listOf(
            Field(name = "baseUrl", label = "URL", value = JsonPrimitive("https://iptorrents.com")),
            Field(
                name = "apiKey",
                label = "Key",
                value = JsonPrimitive("real-secret"),
                privacy = PrivacyLevel.APIKEY,
            ),
        ),
    )

    @Test
    fun `offline indexer save redacts the queued body and the cache row`() = runTest {
        system.error = { if (it == "addIndexer") IOException("offline") else null }
        val repository = repository()

        val saved = repository.saveIndexer(secretIndexer())

        assertTrue((saved as ArrResult.Ok).value is WriteOutcome.Queued)
        val queuedBody = actionQueue.pending().single().bodyJson.orEmpty()
        assertFalse(queuedBody.contains("real-secret"))
        assertTrue(queuedBody.contains("__SECRET__"))
        val cached = repository.cachedIndexers().single()
        assertEquals(
            "__SECRET__",
            (cached.fields!!.single { it.name == "apiKey" }.value as JsonPrimitive).content,
        )
        assertEquals(
            "https://iptorrents.com",
            (cached.fields!!.single { it.name == "baseUrl" }.value as JsonPrimitive).content,
        )
    }

    @Test
    fun `queued indexer replay substitutes the live secret and redacts the cache row`() = runTest {
        system.error = { if (it == "addIndexer") IOException("offline") else null }
        val repository = repository()
        repository.saveIndexer(secretIndexer())

        system.error = { null }
        val replayed = repository.replayQueuedActions()

        assertEquals(1 to 0, replayed)
        assertTrue(actionQueue.pending().isEmpty())
        val sent = system.indexers.values.single().fields!!.single { it.name == "apiKey" }.value
        assertEquals("real-secret", (sent as JsonPrimitive).content)
        val cached = repository.cachedIndexers().single { it.id > 0 }
        assertEquals(
            "__SECRET__",
            (cached.fields!!.single { it.name == "apiKey" }.value as JsonPrimitive).content,
        )
    }

    @Test
    fun `queued indexer replay without the live secret drops the action without posting`() = runTest {
        system.error = { if (it == "addIndexer") IOException("offline") else null }
        repository().saveIndexer(secretIndexer())

        // A fresh repository instance models an app restart: the in-memory
        // secrets are gone but the queue survived.
        system.error = { null }
        val replayed = repository().replayQueuedActions()

        assertEquals(0 to 1, replayed)
        assertTrue(actionQueue.pending().isEmpty())
        assertTrue(system.indexers.isEmpty())
    }

    @Test
    fun `refresh caches provider rows without real secrets`() = runTest {
        system.indexers[3] = secretIndexer(id = 3, name = "Prowlarr")
        val repository = repository()

        val result = repository.refresh()

        assertTrue(result is ArrResult.Ok)
        val cached = repository.cachedIndexers().single()
        assertEquals(
            "__SECRET__",
            (cached.fields!!.single { it.name == "apiKey" }.value as JsonPrimitive).content,
        )
    }

    @Test
    fun `saving a redacted cached row restores the remembered secret`() = runTest {
        system.indexers[3] = secretIndexer(id = 3, name = "Prowlarr")
        val repository = repository()
        repository.refresh()

        val cached = repository.cachedIndexers().single()
        val saved = repository.saveIndexer(cached.copy(priority = 25))

        assertTrue((saved as ArrResult.Ok).value is WriteOutcome.Synced)
        val sent = system.indexers.values.single().fields!!.single { it.name == "apiKey" }.value
        assertEquals("real-secret", (sent as JsonPrimitive).content)
    }

    @Test
    fun `saving with a redacted secret and no remembered value fails clearly`() = runTest {
        val repository = repository()

        val saved = repository.saveIndexer(
            IndexerResource(
                id = 7,
                name = "Ghost",
                fields = listOf(
                    Field(
                        name = "apiKey",
                        value = JsonPrimitive("__SECRET__"),
                        privacy = PrivacyLevel.APIKEY,
                    ),
                ),
            ),
        )

        assertTrue(saved is ArrResult.Err)
        assertTrue(system.indexers.isEmpty())
        assertTrue(actionQueue.pending().isEmpty())
    }
}
