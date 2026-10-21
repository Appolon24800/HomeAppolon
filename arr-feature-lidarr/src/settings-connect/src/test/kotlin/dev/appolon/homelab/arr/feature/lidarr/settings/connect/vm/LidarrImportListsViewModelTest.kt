package dev.appolon.homelab.arr.feature.lidarr.settings.connect.vm

import dev.appolon.homelab.arr.api.lidarr.FakeLidarrDiscovery
import dev.appolon.homelab.arr.api.lidarr.FakeLidarrQueue
import dev.appolon.homelab.arr.api.lidarr.FakeLidarrSystem
import dev.appolon.homelab.arr.api.lidarr.model.ImportListExclusionResource
import dev.appolon.homelab.arr.api.lidarr.model.ImportListMonitorType
import dev.appolon.homelab.arr.api.lidarr.model.ImportListResource
import dev.appolon.homelab.arr.core.ActionId
import dev.appolon.homelab.arr.core.ServerId
import dev.appolon.homelab.arr.data.ActionQueue
import dev.appolon.homelab.arr.data.QueuedAction
import dev.appolon.homelab.arr.feature.lidarr.settings.connect.data.LidarrConnectRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import dev.appolon.homelab.arr.data.testing.FakeEntityStore

@OptIn(ExperimentalCoroutinesApi::class)
class LidarrImportListsViewModelTest {

    private lateinit var system: FakeLidarrSystem
    private lateinit var discovery: FakeLidarrDiscovery
    private lateinit var queue: FakeLidarrQueue
    private lateinit var store: FakeEntityStore
    private lateinit var actionQueue: LocalActionQueue

    @Before
    fun setUp() {
        system = FakeLidarrSystem()
        discovery = FakeLidarrDiscovery()
        queue = FakeLidarrQueue()
        store = FakeEntityStore()
        actionQueue = LocalActionQueue()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun repository(scheduler: TestCoroutineScheduler) = LidarrConnectRepository(
        serverId = ServerId("s1"),
        store = store,
        indexerApi = system,
        indexerConfigApi = system,
        indexerFlagApi = system,
        downloadClientApi = system,
        downloadClientConfigApi = system,
        notificationApi = system,
        remotePathMappingApi = system,
        importListApi = discovery,
        importListExclusionApi = discovery,
        metadataApi = system,
        commandApi = queue,
        tagApi = system,
        actionQueue = actionQueue,
        now = { 1_000L },
        ioContext = StandardTestDispatcher(scheduler),
    )

    @Test
    fun `sync preview summarizes lists and exclusions`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        discovery.importLists[1] = ImportListResource(
            id = 1,
            name = "Last.fm Top",
            enableAutomaticAdd = true,
            shouldMonitor = ImportListMonitorType.SPECIFIC_ALBUM,
            qualityProfileId = 3,
            metadataProfileId = 4,
            rootFolderPath = "/music",
        )
        discovery.importListExclusions[2] = ImportListExclusionResource(
            id = 2,
            foreignId = "mb-99",
            artistName = "Old Artist",
        )
        val vm = LidarrImportListsViewModel(ServerId("s1"), repository(testScheduler))
        advanceUntilIdle()

        val preview = vm.state.value.preview
        assertEquals(1, preview.rows.size)
        val row = preview.rows.single()
        assertEquals("Last.fm Top", row.name)
        assertTrue(row.automaticAdd)
        assertEquals(ImportListMonitorType.SPECIFIC_ALBUM, row.monitor)
        assertEquals(3, row.qualityProfileId)
        assertEquals(4, row.metadataProfileId)
        assertEquals("/music", row.rootFolderPath)
        assertEquals(1, preview.exclusionCount)
        Dispatchers.resetMain()
    }

    @Test
    fun `sync posts the server command and testall probes`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        discovery.importLists[1] = ImportListResource(id = 1, name = "Last.fm")
        val vm = LidarrImportListsViewModel(ServerId("s1"), repository(testScheduler))
        advanceUntilIdle()

        vm.sync()
        advanceUntilIdle()
        assertEquals(ImportListsNotice.SYNC_STARTED, vm.state.value.notice)
        assertEquals("ImportListSync", queue.commands.values.single().name)

        vm.testAll()
        advanceUntilIdle()

        assertEquals(ImportListsNotice.TEST_ALL_DONE, vm.state.value.notice)
        assertEquals(1, discovery.testAllCount)
        assertTrue(actionQueue.pending().isEmpty())
        Dispatchers.resetMain()
    }

    @Test
    fun `list editor round-trips the add options`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        discovery.importLists[1] = ImportListResource(
            implementationName = "Last.fm List",
            implementation = "LastFmImport",
            presets = emptyList(),
        )
        val vm = LidarrImportListEditorViewModel(ServerId("s1"), null, repository(testScheduler))
        advanceUntilIdle()
        vm.pickSchema(vm.state.value.schemas.single())
        advanceUntilIdle()

        vm.updateDraft {
            it.copy(
                shouldMonitor = ImportListMonitorType.ENTIRE_ARTIST,
                shouldMonitorExisting = true,
                qualityProfileId = 7,
                metadataProfileId = 5,
                shouldSearch = true,
            )
        }
        vm.save()
        advanceUntilIdle()

        val stored = discovery.importLists.values.single { it.name == "Last.fm List" }
        assertEquals(ImportListMonitorType.ENTIRE_ARTIST, stored.shouldMonitor)
        assertTrue(stored.shouldMonitorExisting)
        assertEquals(7, stored.qualityProfileId)
        assertEquals(5, stored.metadataProfileId)
        assertTrue(stored.shouldSearch)
        assertEquals(ImportListEditorNotice.SAVED, vm.state.value.notice)
        Dispatchers.resetMain()
    }

    @Test
    fun `exclusions add and remove round-trip`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val vm = LidarrImportListExclusionsViewModel(ServerId("s1"), repository(testScheduler))
        advanceUntilIdle()

        vm.startAdd()
        vm.updateDraft { it.copy(foreignId = "mb-72129", artistName = "Old Artist") }
        vm.add()
        advanceUntilIdle()

        assertEquals(ExclusionsNotice.ADDED, vm.state.value.notice)
        assertEquals("Old Artist", discovery.importListExclusions.values.single().artistName)

        val row = vm.state.value.exclusions.single()
        vm.requestDelete(row)
        vm.deleteConfirmed()
        advanceUntilIdle()

        assertEquals(ExclusionsNotice.DELETED, vm.state.value.notice)
        assertTrue(discovery.importListExclusions.isEmpty())
        Dispatchers.resetMain()
    }
}

/** In-memory [ActionQueue] with the same oldest-first semantics as the real one. */
private class LocalActionQueue : ActionQueue {

    private val pendingFlow = MutableStateFlow<List<QueuedAction>>(emptyList())

    override suspend fun enqueue(action: QueuedAction) {
        pendingFlow.value = (pendingFlow.value + action).sortedBy { it.enqueuedAtMillis }
    }

    override suspend fun pending(): List<QueuedAction> = pendingFlow.value

    override fun observe(): Flow<List<QueuedAction>> = pendingFlow

    override suspend fun replay(executor: suspend (QueuedAction) -> Boolean): Pair<Int, Int> {
        var succeeded = 0
        var failed = 0
        pendingFlow.value.toList().forEach { action ->
            if (executor(action)) {
                pendingFlow.value = pendingFlow.value - action
                succeeded++
            } else {
                failed++
            }
        }
        return succeeded to failed
    }

    override suspend fun remove(actionId: ActionId) {
        pendingFlow.value = pendingFlow.value.filterNot { it.actionId == actionId }
    }
}
