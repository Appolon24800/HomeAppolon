package dev.appolon.homelab.arr.feature.sonarr.settings.mm.vm

import dev.appolon.homelab.arr.api.sonarr.FakeSonarrSystem
import dev.appolon.homelab.arr.api.sonarr.model.DiskSpaceResource
import dev.appolon.homelab.arr.api.sonarr.model.RootFolderResource
import dev.appolon.homelab.arr.api.sonarr.model.UnmappedFolder
import dev.appolon.homelab.arr.core.ServerId
import dev.appolon.homelab.arr.data.store.ActionQueueImpl
import dev.appolon.homelab.arr.data.testing.FakeEntityStore
import dev.appolon.homelab.arr.feature.sonarr.settings.mm.data.SonarrRootFolderRepository
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
class SonarrRootFoldersViewModelTest {

    private val serverId = ServerId("server-1")
    private lateinit var fake: FakeSonarrSystem
    private lateinit var store: FakeEntityStore

    @Before
    fun setUp() {
        fake = FakeSonarrSystem()
        fake.rootFolderStore[1] = RootFolderResource(
            id = 1,
            path = "/tv/series",
            accessible = true,
            freeSpace = 25L,
            totalSpace = 100L,
            unmappedFolders = listOf(
                UnmappedFolder(name = "Unknown Series"),
                UnmappedFolder(name = "Another Series"),
            ),
        )
        fake.diskSpace += DiskSpaceResource(path = "/tv", freeSpace = 25L, totalSpace = 100L)
        store = FakeEntityStore()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun TestScope.repository() = SonarrRootFolderRepository(
        serverId = serverId,
        store = store,
        rootFolderApi = fake.rootFolderApi,
        diskSpaceLoader = fake.diskSpaceApi::list,
        actionQueue = ActionQueueImpl(store),
        ioContext = StandardTestDispatcher(testScheduler),
    )

    private fun TestScope.viewModel() = SonarrRootFoldersViewModel(
        serverId = serverId,
        repository = repository(),
    )

    private fun <T> kotlinx.coroutines.flow.StateFlow<T>.collectWhileActive(
        scope: TestScope,
    ) {
        // backgroundScope: runTest cancels these collectors when the test
        // body ends instead of failing on uncompleted coroutines.
        scope.backgroundScope.launch { collect {} }
    }

    @Test
    fun `loads folders with the unmapped count and free space`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val vm = viewModel()
        vm.lce.collectWhileActive(this)
        advanceUntilIdle()

        val content = (vm.lce.value as LceState.Content<SonarrRootFoldersContent>).data
        assertEquals(1, content.folders.size)
        val entry = content.folders.single()
        assertEquals("/tv", entry.disk?.path)
        assertEquals(2, entry.unmappedCount)
        // Folder's own totals: 25 free of 100.
        assertEquals(0.75f, entry.usedFraction!!, 0.0001f)
    }

    @Test
    fun `no folders renders the empty state`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        fake.rootFolderStore.clear()
        val vm = viewModel()
        vm.lce.collectWhileActive(this)
        advanceUntilIdle()

        assertEquals(LceState.Empty, vm.lce.value)
    }

    @Test
    fun `load failure with no cache renders the error state`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        fake.error = { op -> if (op == "RootFolder.list") IOException("offline") else null }
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
        fake.error = { op -> if (op == "RootFolder.delete") IOException("offline") else null }

        vm.remove(1)
        advanceUntilIdle()

        // The only folder is gone, so the LCE state is legitimately Empty;
        // assert through the raw state instead of the content payload.
        val state = vm.state.value
        assertTrue(state.folders.isEmpty())
        assertEquals(setOf(1), state.queuedRemovedIds)
        assertEquals(RootFolderNotice.REMOVED_QUEUED, state.notice)
    }

    @Test
    fun `offline add stages the folder optimistically`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val vm = viewModel()
        vm.lce.collectWhileActive(this)
        advanceUntilIdle()
        fake.error = { op -> if (op == "RootFolder.create") IOException("offline") else null }

        vm.add("/media/tv")
        advanceUntilIdle()

        val content = (vm.lce.value as LceState.Content<SonarrRootFoldersContent>).data
        val staged = content.folders.firstOrNull { it.folder.path == "/media/tv" }
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

        vm.move(fake.rootFolderStore.getValue(1), "/tv/shows")
        advanceUntilIdle()

        val content = (vm.lce.value as LceState.Content<SonarrRootFoldersContent>).data
        assertTrue(content.folders.any { it.folder.path == "/tv/shows" })
        assertTrue(content.folders.none { it.folder.path == "/tv/series" })
        assertEquals(RootFolderNotice.MOVED, vm.state.value.notice)
    }
}
