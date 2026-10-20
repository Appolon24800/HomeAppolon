package dev.appolon.homelab.arr.feature.sonarr.settings.connect.vm

import dev.appolon.homelab.arr.api.sonarr.FakeSonarrConnect
import dev.appolon.homelab.arr.api.sonarr.FakeSonarrDiscovery
import dev.appolon.homelab.arr.api.sonarr.model.DownloadClientResource
import dev.appolon.homelab.arr.api.sonarr.model.IndexerConfigResource
import dev.appolon.homelab.arr.api.sonarr.model.IndexerResource
import dev.appolon.homelab.arr.api.sonarr.model.NotificationResource
import dev.appolon.homelab.arr.core.ServerId
import dev.appolon.homelab.arr.feature.sonarr.settings.connect.FakeConnectActionQueue
import dev.appolon.homelab.arr.feature.sonarr.settings.connect.FakeConnectCommandApi
import dev.appolon.homelab.arr.feature.sonarr.settings.connect.FakeConnectEntityStore
import dev.appolon.homelab.arr.feature.sonarr.settings.connect.FakeConnectMetadataApi
import dev.appolon.homelab.arr.feature.sonarr.settings.connect.FakeConnectTagApi
import dev.appolon.homelab.arr.feature.sonarr.settings.connect.data.SonarrConnectRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SonarrIndexersViewModelTest {

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

    @After
    fun tearDown() {
        Dispatchers.resetMain()
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
    fun `rows load sorted by priority with options alongside`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        connect.indexerConfig = IndexerConfigResource(minimumAge = 30, rssSyncInterval = 15)
        connect.indexerStore[1] = IndexerResource(id = 1, name = "Low", priority = 50)
        connect.indexerStore[2] = IndexerResource(id = 2, name = "High", priority = 1)
        val vm = SonarrIndexersViewModel(ServerId("s1"), repository(testScheduler))
        advanceUntilIdle()

        val content = vm.state.value
        assertEquals(listOf("High", "Low"), content.indexers.map { it.name })
        assertEquals(30, content.options?.minimumAge)
        Dispatchers.resetMain()
    }

    @Test
    fun `testAll reports a single verdict and never queues`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        connect.indexerStore[1] = IndexerResource(id = 1, name = "Prowlarr")
        val vm = SonarrIndexersViewModel(ServerId("s1"), repository(testScheduler))
        advanceUntilIdle()

        vm.testAll()
        advanceUntilIdle()

        assertEquals(IndexersNotice.TEST_ALL_DONE, vm.state.value.notice)
        assertTrue(queue.pending().isEmpty())
        Dispatchers.resetMain()
    }

    @Test
    fun `delete flows through and options save updates state`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        connect.indexerStore[1] = IndexerResource(id = 1, name = "Prowlarr")
        val vm = SonarrIndexersViewModel(ServerId("s1"), repository(testScheduler))
        advanceUntilIdle()

        vm.requestDelete(connect.indexerStore[1]!!)
        vm.deleteConfirmed()
        advanceUntilIdle()

        assertEquals(IndexersNotice.DELETED, vm.state.value.notice)
        assertTrue(connect.indexerStore.isEmpty())

        vm.updateOptions(IndexerConfigResource(minimumAge = 90))
        advanceUntilIdle()

        assertEquals(90, connect.indexerConfig.minimumAge)
        assertEquals(IndexersNotice.OPTIONS_SAVED, vm.state.value.notice)
        Dispatchers.resetMain()
    }

    @Test
    fun `indexer editor builds a draft from the picked schema`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        connect.indexerSchemas += IndexerResource(
            name = null,
            implementationName = "Newznab",
            implementation = "Newznab",
            supportsRss = true,
            supportsSearch = true,
        )
        val vm = SonarrIndexerEditorViewModel(ServerId("s1"), null, repository(testScheduler))
        advanceUntilIdle()

        assertTrue(vm.state.value.picker)
        assertEquals(listOf("Newznab"), vm.state.value.schemas.map { it.implementation })

        vm.pickSchema(vm.state.value.schemas.single())
        advanceUntilIdle()

        val draft = vm.state.value.draft
        assertEquals("Newznab", draft?.name)
        assertTrue(draft?.enableRss == true)
        assertTrue(draft?.enableAutomaticSearch == true)
        assertFalse(vm.state.value.picker)
        Dispatchers.resetMain()
    }

    @Test
    fun `indexer editor save writes through`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        connect.indexerSchemas += IndexerResource(
            implementationName = "Newznab",
            implementation = "Newznab",
        )
        val vm = SonarrIndexerEditorViewModel(ServerId("s1"), null, repository(testScheduler))
        advanceUntilIdle()
        vm.pickSchema(vm.state.value.schemas.single())
        advanceUntilIdle()

        vm.updateDraft { it.copy(priority = 40) }
        vm.save()
        advanceUntilIdle()

        assertEquals(IndexerEditorNotice.SAVED, vm.state.value.notice)
        assertEquals(40, connect.indexerStore.values.single().priority)
        assertFalse(vm.state.value.isNew)
        Dispatchers.resetMain()
    }

    @Test
    fun `notification editor event matrix honors supports flags`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        connect.notificationSchemas += NotificationResource(
            implementationName = "Discord",
            implementation = "Discord",
            supportsOnGrab = true,
            supportsOnDownload = true,
            supportsOnManualInteractionRequired = true,
        )
        val vm = SonarrNotificationEditorViewModel(
            ServerId("s1"),
            null,
            repository(testScheduler),
        )
        advanceUntilIdle()
        vm.pickSchema(vm.state.value.schemas.single())
        advanceUntilIdle()

        val keys = vm.state.value.draft?.events?.map { it.key }
        assertEquals(listOf("onGrab", "onDownload", "onManualInteractionRequired"), keys)

        vm.toggleEvent("onGrab")
        advanceUntilIdle()
        assertTrue(vm.state.value.draft?.onGrab == true)

        vm.save()
        advanceUntilIdle()

        assertEquals(NotificationEditorNotice.SAVED, vm.state.value.notice)
        assertTrue(connect.notificationStore.values.single().onGrab)
        Dispatchers.resetMain()
    }

    @Test
    fun `download client editor saves enable and removal toggles`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        connect.downloadClientSchemas += DownloadClientResource(
            implementationName = "qBittorrent",
            implementation = "QBitTorrent",
        )
        val vm = SonarrDownloadClientEditorViewModel(
            ServerId("s1"),
            null,
            repository(testScheduler),
        )
        advanceUntilIdle()
        vm.pickSchema(vm.state.value.schemas.single())
        advanceUntilIdle()

        vm.updateDraft { it.copy(removeFailedDownloads = false, priority = 10) }
        vm.save()
        advanceUntilIdle()

        val stored = connect.downloadClientStore.values.single()
        assertEquals(10, stored.priority)
        assertEquals(false, stored.removeFailedDownloads)
        assertEquals(DownloadClientEditorNotice.SAVED, vm.state.value.notice)
        Dispatchers.resetMain()
    }
}
