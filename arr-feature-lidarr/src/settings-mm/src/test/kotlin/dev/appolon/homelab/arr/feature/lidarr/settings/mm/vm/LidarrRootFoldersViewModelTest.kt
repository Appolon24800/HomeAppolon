package dev.appolon.homelab.arr.feature.lidarr.settings.mm.vm

import dev.appolon.homelab.arr.api.lidarr.FakeLidarrSystem
import dev.appolon.homelab.arr.api.lidarr.model.RootFolderResource
import dev.appolon.homelab.arr.core.ServerId
import dev.appolon.homelab.arr.data.store.ActionQueueImpl
import dev.appolon.homelab.arr.data.testing.FakeEntityStore
import dev.appolon.homelab.arr.feature.lidarr.settings.mm.data.LidarrRootFolderRepository
import dev.appolon.homelab.arr.ui.lce.LceState
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LidarrRootFoldersViewModelTest {

    private val serverId = ServerId("server-1")
    private lateinit var fake: FakeLidarrSystem
    private lateinit var store: FakeEntityStore

    @Before
    fun setUp() {
        fake = FakeLidarrSystem()
        fake.rootFolders[1] = RootFolderResource(
            id = 1,
            path = "/music",
            accessible = true,
            freeSpace = 250L,
            totalSpace = 1_000L,
        )
        fake.rootFolders[2] = RootFolderResource(
            id = 2,
            path = "/jazz",
            accessible = false,
            freeSpace = null,
            totalSpace = null,
        )
        store = FakeEntityStore()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun TestScope.repository() = LidarrRootFolderRepository(
        serverId = serverId,
        store = store,
        rootFolderApi = fake,
        diskSpaceLoader = fake::getDiskSpace,
        actionQueue = ActionQueueImpl(store),
        ioContext = StandardTestDispatcher(testScheduler),
    )

    private fun TestScope.viewModel() = LidarrRootFoldersViewModel(
        serverId = serverId,
        repository = repository(),
    )

    private fun <T> kotlinx.coroutines.flow.StateFlow<T>.collectWhileActive(
        scope: TestScope,
    ) {
        scope.backgroundScope.launch { collect {} }
    }

    @Test
    fun `loads the folder list with space fractions`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val vm = viewModel()
        vm.lce.collectWhileActive(this)
        advanceUntilIdle()

        val content = (vm.lce.value as LceState.Content<LidarrRootFoldersContent>).data
        assertEquals(2, content.folders.size)
        val music = content.folders.first { it.folder.path == "/music" }
        assertEquals(0.75f, music.usedFraction!!, 0.0001f)
    }

    @Test
    fun `selection survives refresh and clears when the folder disappears`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val vm = viewModel()
        vm.lce.collectWhileActive(this)
        advanceUntilIdle()

        vm.select(1)
        assertEquals(1, vm.state.value.selectedFolderId)

        vm.remove(1)
        advanceUntilIdle()

        assertEquals(null, vm.state.value.selectedFolderId)
        assertEquals(RootFolderNotice.REMOVED, vm.state.value.notice)
    }

    @Test
    fun `offline removal queues and badges the row`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val vm = viewModel()
        vm.lce.collectWhileActive(this)
        advanceUntilIdle()
        fake.error = { op -> if (op == "deleteRootFolder") IOException("offline") else null }

        vm.remove(1)
        advanceUntilIdle()

        assertEquals(RootFolderNotice.REMOVED_QUEUED, vm.state.value.notice)
        assertEquals(setOf(1), vm.state.value.queuedRemovedIds)
        assertEquals(1, vm.state.value.pendingActions)
    }

    @Test
    fun `offline path edit queues a single update`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val vm = viewModel()
        vm.lce.collectWhileActive(this)
        advanceUntilIdle()
        fake.error = { op -> if (op == "updateRootFolder") IOException("offline") else null }
        val folder = fake.rootFolders.getValue(1)

        vm.move(folder, "/music-new")
        advanceUntilIdle()

        assertEquals(RootFolderNotice.EDITED_QUEUED, vm.state.value.notice)
        val pending = ActionQueueImpl(store).pending()
        assertEquals(1, pending.size)
        assertEquals("PUT", pending.single().method)
        // The cached list already reflects the new path.
        val content = (vm.lce.value as LceState.Content<LidarrRootFoldersContent>).data
        assertTrue(content.folders.any { it.folder.path == "/music-new" })
    }

    @Test
    fun `offline add reports the queued outcome with a placeholder row`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val vm = viewModel()
        vm.lce.collectWhileActive(this)
        advanceUntilIdle()
        fake.error = { op -> if (op == "addRootFolder") IOException("offline") else null }

        vm.add("/new-music")
        advanceUntilIdle()

        assertEquals(RootFolderNotice.ADDED_QUEUED, vm.state.value.notice)
        val content = (vm.lce.value as LceState.Content<LidarrRootFoldersContent>).data
        assertTrue(content.folders.any { it.folder.path == "/new-music" && it.folder.id < 0 })
    }

    @Test
    fun `empty first load with failure shows the error state`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        fake.error = { op -> if (op == "getRootFolders") IOException("offline") else null }
        val vm = viewModel()
        vm.lce.collectWhileActive(this)
        advanceUntilIdle()

        assertTrue(vm.lce.value is LceState.Error)
    }
}
