package dev.appolon.homelab.arr.feature.radarr.settings.connect.data

import dev.appolon.homelab.arr.api.radarr.FakeRadarrConnect
import dev.appolon.homelab.arr.api.radarr.model.DownloadClientResource
import dev.appolon.homelab.arr.api.radarr.model.Field
import dev.appolon.homelab.arr.api.radarr.model.ImportListExclusionResource
import dev.appolon.homelab.arr.api.radarr.model.ImportListResource
import dev.appolon.homelab.arr.api.radarr.model.IndexerConfigResource
import dev.appolon.homelab.arr.api.radarr.model.IndexerResource
import dev.appolon.homelab.arr.api.radarr.model.MetadataResource
import dev.appolon.homelab.arr.api.radarr.model.NotificationResource
import dev.appolon.homelab.arr.api.radarr.model.PrivacyLevel
import dev.appolon.homelab.arr.api.radarr.model.RemotePathMappingResource
import dev.appolon.homelab.arr.api.radarr.model.TagResource
import dev.appolon.homelab.arr.core.ArrResult
import dev.appolon.homelab.arr.core.ServerId
import dev.appolon.homelab.arr.feature.radarr.library.data.WriteOutcome
import dev.appolon.homelab.arr.feature.radarr.settings.connect.FakeConnectActionQueue
import dev.appolon.homelab.arr.feature.radarr.settings.connect.FakeConnectCommandApi
import dev.appolon.homelab.arr.data.testing.FakeEntityStore
import dev.appolon.homelab.arr.feature.radarr.settings.connect.FakeConnectMetadataApi
import dev.appolon.homelab.arr.feature.radarr.settings.connect.FakeConnectTagApi
import dev.appolon.homelab.arr.api.radarr.FakeRadarrDiscovery
import java.io.IOException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RadarrConnectRepositoryTest {

    private lateinit var connect: FakeRadarrConnect
    private lateinit var discovery: FakeRadarrDiscovery
    private lateinit var tagApi: FakeConnectTagApi
    private lateinit var metadataApi: FakeConnectMetadataApi
    private lateinit var commandApi: FakeConnectCommandApi
    private lateinit var store: FakeEntityStore
    private lateinit var queue: FakeConnectActionQueue

    @Before
    fun setUp() {
        connect = FakeRadarrConnect()
        discovery = FakeRadarrDiscovery()
        tagApi = FakeConnectTagApi()
        metadataApi = FakeConnectMetadataApi()
        commandApi = FakeConnectCommandApi()
        store = FakeEntityStore()
        queue = FakeConnectActionQueue()
    }

    private fun repository(scheduler: TestCoroutineScheduler) = RadarrConnectRepository(
        serverId = ServerId("s1"),
        store = store,
        indexerApi = connect,
        indexerConfigApi = connect,
        indexerFlagApi = connect,
        downloadClientApi = connect,
        downloadClientConfigApi = connect,
        notificationApi = connect,
        remotePathMappingApi = connect,
        importListApi = discovery,
        importListConfigApi = discovery,
        importListExclusionApi = discovery,
        importListMoviesApi = discovery,
        metadataApi = metadataApi,
        commandApi = commandApi,
        tagApi = tagApi,
        actionQueue = queue,
        now = { 1_000L },
        ioContext = StandardTestDispatcher(scheduler),
    )

    @Test
    fun `refresh populates every provider table`() = runTest {
        connect.indexers += IndexerResource(id = 1, name = "Prowlarr", priority = 5)
        connect.downloadClients += DownloadClientResource(id = 2, name = "qBittorrent")
        connect.remotePathMappings += RemotePathMappingResource(id = 3, host = "seedbox")
        discovery.importLists += ImportListResource(id = 4, name = "Trakt List")
        discovery.exclusions += ImportListExclusionResource(id = 5, movieTitle = "Excluded")
        connect.notifications += NotificationResource(id = 6, name = "Discord")
        metadataApi.rows += MetadataResource(id = 7, name = "Kodi")
        val repository = repository(testScheduler)

        val result = repository.refresh()
        advanceUntilIdle()

        assertTrue(result is ArrResult.Ok)
        assertEquals(listOf("Prowlarr"), repository.cachedIndexers().map { it.name })
        assertEquals(listOf("qBittorrent"), repository.cachedDownloadClients().map { it.name })
        val mappings = store.getAll(
            ServerId("s1"),
            RadarrConnectRepository.REMOTE_PATH_MAPPING_TYPE,
            RemotePathMappingResource.serializer(),
        )
        assertEquals(listOf("seedbox"), mappings.map { it.host })
        assertEquals(listOf("Trakt List"), repository.cachedImportLists().map { it.name })
        assertEquals(listOf("Discord"), repository.cachedNotifications().map { it.name })
    }

    @Test
    fun `saveIndexer creates online and caches the server row`() = runTest {
        val repository = repository(testScheduler)
        val saved = repository.saveIndexer(IndexerResource(name = "NZBgeek", priority = 30))
        advanceUntilIdle()

        assertTrue((saved as ArrResult.Ok).value is WriteOutcome.Synced)
        assertEquals(1, connect.indexers.single().id)
        assertEquals("NZBgeek", repository.cachedIndexers().single().name)
    }

    @Test
    fun `offline indexer create queues with a temporary row`() = runTest {
        connect.errorFor = { if (it == "createIndexer") IOException("offline") else null }
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
    fun `offline indexer delete removes the row optimistically`() = runTest {
        connect.indexers += IndexerResource(id = 1, name = "Doomed")
        val repository = repository(testScheduler)
        repository.refresh()
        advanceUntilIdle()

        connect.errorFor = { if (it == "removeIndexer") IOException("offline") else null }
        val removed = repository.removeIndexer(1)
        advanceUntilIdle()

        assertTrue((removed as ArrResult.Ok).value is WriteOutcome.Queued)
        assertTrue(repository.cachedIndexers().isEmpty())
        assertEquals("api/v3/indexer/1", queue.pending().single().path)
    }

    @Test
    fun `testAllIndexers answers per-indexer verdicts`() = runTest {
        connect.indexers += IndexerResource(id = 1, name = "A", enableRss = true)
        val repository = repository(testScheduler)

        val results = repository.testAllIndexers()
        advanceUntilIdle()

        val verdicts = (results as ArrResult.Ok).value
        assertEquals(1, verdicts.size)
        assertEquals(1, verdicts.single().id)
        assertTrue(verdicts.single().isValid)
    }

    @Test
    fun `import list preview caches what the lists discover`() = runTest {
        discovery.discoveredMovies += dev.appolon.homelab.arr.api.radarr.model.ImportListMoviesResource(
            id = 1,
            title = "Fresh Movie",
            isExisting = false,
            isExcluded = false,
        )
        val repository = repository(testScheduler)

        val preview = repository.importListPreview()
        advanceUntilIdle()

        assertEquals(1, (preview as ArrResult.Ok).value.size)
        val cached = store.getAll(
            ServerId("s1"),
            RadarrConnectRepository.IMPORT_LIST_PREVIEW_TYPE,
            dev.appolon.homelab.arr.api.radarr.model.ImportListMoviesResource.serializer(),
        )
        assertEquals("Fresh Movie", cached.single().title)
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
    fun `exclusion add posts online`() = runTest {
        val repository = repository(testScheduler)

        val result = repository.addExclusion(
            ImportListExclusionResource(tmdbId = 42, movieTitle = "Nope", movieYear = 2020),
        )
        advanceUntilIdle()

        assertTrue((result as ArrResult.Ok).value is WriteOutcome.Synced)
        assertEquals(42, discovery.exclusions.single().tmdbId)
    }

    @Test
    fun `indexer options save offline queues the config put`() = runTest {
        connect.errorFor = { if (it == "updateIndexerConfig") IOException("offline") else null }
        val repository = repository(testScheduler)

        val result = repository.saveIndexerConfig(
            IndexerConfigResource(id = 1, minimumAge = 90, rssSyncInterval = 30),
        )
        advanceUntilIdle()

        assertTrue((result as ArrResult.Ok).value is WriteOutcome.Queued)
        assertEquals("api/v3/indexer/config/1", queue.pending().single().path)
        assertEquals(90, repository.cachedIndexerConfig()?.minimumAge)
    }

    @Test
    fun `ensureTagIds creates only missing tags`() = runTest {
        tagApi.tags += TagResource(id = 1, label = "existing")
        val repository = repository(testScheduler)

        val ids = repository.ensureTagIds(listOf("existing", "fresh"))
        advanceUntilIdle()

        assertEquals(listOf(1, 2), (ids as ArrResult.Ok).value)
        assertEquals(listOf("existing", "fresh"), tagApi.tags.map { it.label })
    }

    @Test
    fun `field values round trip through the generic Field list`() {
        val fields = listOf(
            Field(order = 0, name = "baseUrl", label = "URL", value = JsonPrimitive("https://x")),
            Field(order = 1, name = "apiKey", label = "Key", value = JsonPrimitive("secret")),
        )
        val values = ConnectFieldForms.editableValues(fields)
        assertEquals("https://x", ConnectFieldForms.textOf(values["baseUrl"]))

        val applied = ConnectFieldForms.applyValues(
            fields,
            values + ("apiKey" to JsonPrimitive("rotated")),
        )
        assertEquals("rotated", ConnectFieldForms.textOf(applied[1].value))
    }

    @Test
    fun `seed criteria and categories read from the field list`() {
        val fields = listOf(
            Field(
                name = "seedCriteria.seedRatio",
                value = JsonPrimitive(2.0),
                section = "seedCriteria",
            ),
            Field(name = "categories", value = kotlinx.serialization.json.JsonArray(
                listOf(JsonPrimitive(2000), JsonPrimitive(2030)),
            )),
        )
        assertEquals(2.0, ConnectFieldForms.seedRatio(fields))
        assertEquals(listOf(2000, 2030), ConnectFieldForms.categoryIds(fields))
    }

    // --------------------------------------------------------------- secrets

    private fun secretIndexer(id: Int = 0, name: String = "NZBgeek") = IndexerResource(
        id = id,
        name = name,
        fields = listOf(
            Field(name = "baseUrl", label = "URL", value = JsonPrimitive("https://nzbgeek.info")),
            Field(
                name = "apiKey",
                label = "Key",
                value = JsonPrimitive("real-secret"),
                privacy = PrivacyLevel.API_KEY,
            ),
        ),
    )

    @Test
    fun `offline indexer save redacts the queued body and the cache row`() = runTest {
        connect.errorFor = { if (it == "createIndexer") IOException("offline") else null }
        val repository = repository(testScheduler)

        val saved = repository.saveIndexer(secretIndexer())
        advanceUntilIdle()

        assertTrue((saved as ArrResult.Ok).value is WriteOutcome.Queued)
        val queuedBody = queue.pending().single().bodyJson.orEmpty()
        assertFalse(queuedBody.contains("real-secret"))
        assertTrue(queuedBody.contains("__SECRET__"))
        val cached = repository.cachedIndexers().single()
        assertEquals(
            "__SECRET__",
            (cached.fields!!.single { it.name == "apiKey" }.value as JsonPrimitive).content,
        )
        assertEquals(
            "https://nzbgeek.info",
            (cached.fields!!.single { it.name == "baseUrl" }.value as JsonPrimitive).content,
        )
    }

    @Test
    fun `queued indexer replay substitutes the live secret and redacts the cache row`() = runTest {
        connect.errorFor = { if (it == "createIndexer") IOException("offline") else null }
        val repository = repository(testScheduler)
        repository.saveIndexer(secretIndexer())
        advanceUntilIdle()

        connect.errorFor = { null }
        val replayed = repository.replayQueuedActions()
        advanceUntilIdle()

        assertEquals(1 to 0, replayed)
        assertTrue(queue.pending().isEmpty())
        val sent = connect.indexers.single().fields!!.single { it.name == "apiKey" }.value
        assertEquals("real-secret", (sent as JsonPrimitive).content)
        val cached = repository.cachedIndexers().single { it.id > 0 }
        assertEquals(
            "__SECRET__",
            (cached.fields!!.single { it.name == "apiKey" }.value as JsonPrimitive).content,
        )
    }

    @Test
    fun `queued indexer replay without the live secret drops the action without posting`() = runTest {
        connect.errorFor = { if (it == "createIndexer") IOException("offline") else null }
        repository(testScheduler).saveIndexer(secretIndexer())
        advanceUntilIdle()

        // A fresh repository instance models an app restart: the in-memory
        // secrets are gone but the queue survived.
        connect.errorFor = { null }
        val replayed = repository(testScheduler).replayQueuedActions()
        advanceUntilIdle()

        assertEquals(0 to 1, replayed)
        assertTrue(queue.pending().isEmpty())
        assertTrue(connect.indexers.isEmpty())
    }

    @Test
    fun `refresh caches provider rows without real secrets`() = runTest {
        connect.indexers += secretIndexer(id = 3, name = "Prowlarr")
        val repository = repository(testScheduler)

        val result = repository.refresh()
        advanceUntilIdle()

        assertTrue(result is ArrResult.Ok)
        val cached = repository.cachedIndexers().single()
        assertEquals(
            "__SECRET__",
            (cached.fields!!.single { it.name == "apiKey" }.value as JsonPrimitive).content,
        )
    }

    @Test
    fun `saving a redacted cached row restores the remembered secret`() = runTest {
        connect.indexers += secretIndexer(id = 3, name = "Prowlarr")
        val repository = repository(testScheduler)
        repository.refresh()
        advanceUntilIdle()

        val cached = repository.cachedIndexers().single()
        val saved = repository.saveIndexer(cached.copy(priority = 25))
        advanceUntilIdle()

        assertTrue((saved as ArrResult.Ok).value is WriteOutcome.Synced)
        val sent = connect.indexers.single().fields!!.single { it.name == "apiKey" }.value
        assertEquals("real-secret", (sent as JsonPrimitive).content)
    }

    @Test
    fun `saving with a redacted secret and no remembered value fails clearly`() = runTest {
        val repository = repository(testScheduler)

        val saved = repository.saveIndexer(
            IndexerResource(
                id = 7,
                name = "Ghost",
                fields = listOf(
                    Field(
                        name = "apiKey",
                        value = JsonPrimitive("__SECRET__"),
                        privacy = PrivacyLevel.API_KEY,
                    ),
                ),
            ),
        )
        advanceUntilIdle()

        assertTrue(saved is ArrResult.Err)
        assertTrue(connect.indexers.isEmpty())
        assertTrue(queue.pending().isEmpty())
    }
}
