package dev.appolon.homelab.arr.feature.lidarr.settings.connect.vm

import dev.appolon.homelab.arr.api.lidarr.FakeLidarrDiscovery
import dev.appolon.homelab.arr.api.lidarr.FakeLidarrQueue
import dev.appolon.homelab.arr.api.lidarr.FakeLidarrSystem
import dev.appolon.homelab.arr.api.lidarr.model.DownloadClientResource
import dev.appolon.homelab.arr.api.lidarr.model.IndexerConfigResource
import dev.appolon.homelab.arr.api.lidarr.model.IndexerResource
import dev.appolon.homelab.arr.api.lidarr.model.RemotePathMappingResource
import dev.appolon.homelab.arr.core.ServerId
import dev.appolon.homelab.arr.feature.lidarr.settings.connect.FakeConnectActionQueue
import dev.appolon.homelab.arr.feature.lidarr.settings.connect.FakeConnectEntityStore
import dev.appolon.homelab.arr.feature.lidarr.settings.connect.connectRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
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
class LidarrIndexersViewModelTest {

    private lateinit var system: FakeLidarrSystem
    private lateinit var discovery: FakeLidarrDiscovery
    private lateinit var commandQueue: FakeLidarrQueue
    private lateinit var store: FakeConnectEntityStore
    private lateinit var actionQueue: FakeConnectActionQueue

    @Before
    fun setUp() {
        system = FakeLidarrSystem()
        discovery = FakeLidarrDiscovery()
        commandQueue = FakeLidarrQueue()
        store = FakeConnectEntityStore()
        actionQueue = FakeConnectActionQueue()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
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
    fun `rows load sorted by priority with options alongside`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        system.indexerConfig = IndexerConfigResource(minimumAge = 30, rssSyncInterval = 15)
        system.indexers[1] = IndexerResource(id = 1, name = "Low", priority = 50)
        system.indexers[2] = IndexerResource(id = 2, name = "High", priority = 1)
        val vm = LidarrIndexersViewModel(ServerId("s1"), repository())
        advanceUntilIdle()

        val content = vm.state.value
        assertEquals(listOf("High", "Low"), content.indexers.map { it.name })
        assertEquals(30, content.options?.minimumAge)
        Dispatchers.resetMain()
    }

    @Test
    fun `testAll reports a single verdict and never queues`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        system.indexers[1] = IndexerResource(id = 1, name = "Prowlarr")
        val vm = LidarrIndexersViewModel(ServerId("s1"), repository())
        advanceUntilIdle()

        vm.testAll()
        advanceUntilIdle()

        assertEquals(IndexersNotice.TEST_ALL_DONE, vm.state.value.notice)
        assertTrue(actionQueue.pending().isEmpty())
        Dispatchers.resetMain()
    }

    @Test
    fun `delete flows through and options save updates state`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        system.indexers[1] = IndexerResource(id = 1, name = "Prowlarr")
        val vm = LidarrIndexersViewModel(ServerId("s1"), repository())
        advanceUntilIdle()

        vm.requestDelete(system.indexers[1]!!)
        vm.deleteConfirmed()
        advanceUntilIdle()

        assertEquals(IndexersNotice.DELETED, vm.state.value.notice)
        assertTrue(system.indexers.isEmpty())

        vm.updateOptions(IndexerConfigResource(minimumAge = 90))
        advanceUntilIdle()

        assertEquals(90, system.indexerConfig.minimumAge)
        assertEquals(IndexersNotice.OPTIONS_SAVED, vm.state.value.notice)
        Dispatchers.resetMain()
    }

    @Test
    fun `indexer editor builds a draft from the picked schema`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        system.indexers[1] = IndexerResource(
            name = null,
            implementationName = "Newznab",
            implementation = "Newznab",
            presets = emptyList(),
            supportsRss = true,
            supportsSearch = true,
        )
        val vm = LidarrIndexerEditorViewModel(ServerId("s1"), null, repository())
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
        system.indexers[1] = IndexerResource(
            implementationName = "Newznab",
            implementation = "Newznab",
            presets = emptyList(),
        )
        val vm = LidarrIndexerEditorViewModel(ServerId("s1"), null, repository())
        advanceUntilIdle()
        vm.pickSchema(vm.state.value.schemas.single())
        advanceUntilIdle()

        vm.updateDraft { it.copy(priority = 40) }
        vm.save()
        advanceUntilIdle()

        assertEquals(IndexerEditorNotice.SAVED, vm.state.value.notice)
        assertEquals(40, system.indexers.values.single { it.presets == null }.priority)
        assertFalse(vm.state.value.isNew)
        Dispatchers.resetMain()
    }

    @Test
    fun `download client editor saves enable and removal toggles`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        system.downloadClients[1] = DownloadClientResource(
            implementationName = "qBittorrent",
            implementation = "QBitTorrent",
            presets = emptyList(),
        )
        val vm = LidarrDownloadClientEditorViewModel(
            ServerId("s1"),
            null,
            repository(),
        )
        advanceUntilIdle()
        vm.pickSchema(vm.state.value.schemas.single())
        advanceUntilIdle()

        vm.updateDraft { it.copy(removeFailedDownloads = false, priority = 10) }
        vm.save()
        advanceUntilIdle()

        val stored = system.downloadClients.values.single { it.presets == null }
        assertEquals(10, stored.priority)
        assertEquals(false, stored.removeFailedDownloads)
        assertEquals(DownloadClientEditorNotice.SAVED, vm.state.value.notice)
        Dispatchers.resetMain()
    }

    @Test
    fun `remote path mappings save and delete flow through`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        system.remotePathMappings[1] = RemotePathMappingResource(
            id = 1,
            host = "seedbox",
            remotePath = "/remote",
            localPath = "/local",
        )
        val vm = LidarrRemotePathMappingsViewModel(ServerId("s1"), repository())
        advanceUntilIdle()

        vm.startCreate()
        vm.updateDraft { it.copy(host = "nas", remotePath = "/r", localPath = "/l") }
        vm.save()
        advanceUntilIdle()

        assertEquals(RemotePathMappingsNotice.SAVED, vm.state.value.notice)
        assertEquals(listOf("nas", "seedbox"), vm.state.value.mappings.map { it.host })

        vm.requestDelete(system.remotePathMappings.values.first { it.host == "seedbox" })
        vm.deleteConfirmed()
        advanceUntilIdle()

        assertEquals(RemotePathMappingsNotice.DELETED, vm.state.value.notice)
        assertEquals(listOf("nas"), vm.state.value.mappings.map { it.host })
        Dispatchers.resetMain()
    }
}
