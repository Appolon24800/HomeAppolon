package dev.appolon.homelab.arr.feature.radarr.settings.mm.vm

import dev.appolon.homelab.arr.api.radarr.FakeRadarrSystem
import dev.appolon.homelab.arr.api.radarr.model.DiskSpaceResource
import dev.appolon.homelab.arr.api.radarr.model.RootFolderResource
import dev.appolon.homelab.arr.api.radarr.model.UnmappedFolder
import dev.appolon.homelab.arr.core.ServerId
import dev.appolon.homelab.arr.data.store.ActionQueueImpl
import dev.appolon.homelab.arr.data.testing.FakeEntityStore
import dev.appolon.homelab.arr.feature.radarr.settings.mm.data.RadarrRootFolderRepository
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RadarrRootFoldersViewModelTest {

    private val serverId = ServerId("server-1")
    private lateinit var fake: FakeRadarrSystem
    private lateinit var store: FakeEntityStore

    @Before
    fun setUp() {
        fake = FakeRadarrSystem()
        fake.rootFolderItems += RootFolderResource(
            id = 1,
            path = "/mnt/movies",
            accessible = true,
            freeSpace = 25L,
            unmappedFolders = listOf(
                UnmappedFolder(name = "Unknown Movie"),
                UnmappedFolder(name = "Another Movie"),
            ),
        )
        fake.diskSpaceItems += DiskSpaceResource(path = "/mnt", freeSpace = 25L, totalSpace = 100L)
        store = FakeEntityStore()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun TestScope.repository() = RadarrRootFolderRepository(
        serverId = serverId,
        store = store,
        rootFolderApi = fake,
        diskSpaceLoader = fake::diskSpace,
        actionQueue = ActionQueueImpl(store),
        ioContext = StandardTestDispatcher(testScheduler),
    )

    private fun TestScope.viewModel() = RadarrRootFoldersViewModel(
        serverId = serverId,
        repository = repository(),
    )

    private fun <T> kotlinx.coroutines.flow.StateFlow<T>.collectWhileActive(
        scope: TestScope,
    ) {
        scope.backgroundScope.launch { collect {} }
    }

    @Test
    fun `loads folders joined with disks and the unmapped count`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val vm = viewModel()
        vm.lce.collectWhileActive(this)
        advanceUntilIdle()

        val content = (vm.lce.value as LceState.Content<RadarrRootFoldersContent>).data
        assertEquals(1, content.folders.size)
        val entry = content.folders.single()
        assertEquals("/mnt", entry.disk?.path)
        assertEquals(2, entry.unmappedCount)
        assertEquals(0.75f, entry.usedFraction!!, 0.0001f)
    }

    @Test
    fun `no folders renders the empty state`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        fake.rootFolderItems.clear()
        val vm = viewModel()
        vm.lce.collectWhileActive(this)
        advanceUntilIdle()

        assertEquals(LceState.Empty, vm.lce.value)
    }

    @Test
    fun `load failure with no cache renders the error state`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        fake.errorFor = { op -> if (op == "rootFolders") IOException("offline") else null }
        val vm = viewModel()
        vm.lce.collectWhileActive(this)
        advanceUntilIdle()

        assertTrue(vm.lce.value is LceState.Error)
    }

    @Test
    fun `offline removal clears the row and raises the queued badge`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val vm = viewModel()
        vm.lce.collectWhileActive(this)
        advanceUntilIdle()
        fake.errorFor = { op -> if (op == "removeRootFolder") IOException("offline") else null }

        vm.remove(1)
        advanceUntilIdle()

        // The only folder is gone, so the list renders the empty state.
        assertEquals(LceState.Empty, vm.lce.value)
        val state = vm.state.value
        assertEquals(setOf(1), state.queuedRemovedIds)
        assertEquals(1, state.pendingActions)
        assertEquals(RootFolderNotice.REMOVED_QUEUED, state.notice)
    }

    @Test
    fun `offline add stages the folder optimistically`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val vm = viewModel()
        vm.lce.collectWhileActive(this)
        advanceUntilIdle()
        fake.errorFor = { op -> if (op == "createRootFolder") IOException("offline") else null }

        vm.add("/media/movies")
        advanceUntilIdle()

        val content = (vm.lce.value as LceState.Content<RadarrRootFoldersContent>).data
        val staged = content.folders.firstOrNull { it.folder.path == "/media/movies" }
        assertTrue(staged != null)
        assertTrue(staged!!.folder.id < 0)
        assertEquals(RootFolderNotice.ADDED_QUEUED, vm.state.value.notice)
    }

    @Test
    fun `selection survives until the folder disappears`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val vm = viewModel()
        vm.lce.collectWhileActive(this)
        advanceUntilIdle()

        vm.select(1)
        assertEquals(1, vm.state.value.selectedFolderId)

        vm.remove(1)
        advanceUntilIdle()

        assertNull(vm.state.value.selectedFolderId)
    }

    @Test
    fun `online move re-points the folder path`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val vm = viewModel()
        vm.lce.collectWhileActive(this)
        advanceUntilIdle()

        vm.move(fake.rootFolderItems.single(), "/mnt/films")
        advanceUntilIdle()

        val content = (vm.lce.value as LceState.Content<RadarrRootFoldersContent>).data
        assertTrue(content.folders.any { it.folder.path == "/mnt/films" })
        assertTrue(content.folders.none { it.folder.path == "/mnt/movies" })
        assertEquals(RootFolderNotice.MOVED, vm.state.value.notice)
    }
}
