package dev.appolon.homelab.arr.feature.lidarr.settings.mm.vm

import dev.appolon.homelab.arr.api.lidarr.FakeLidarrSystem
import dev.appolon.homelab.arr.api.lidarr.model.MediaManagementConfigResource
import dev.appolon.homelab.arr.core.ServerId
import dev.appolon.homelab.arr.data.store.ActionQueueImpl
import dev.appolon.homelab.arr.data.testing.FakeEntityStore
import dev.appolon.homelab.arr.feature.lidarr.settings.mm.data.LidarrMediaManagementRepository
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LidarrMediaManagementViewModelTest {

    private val serverId = ServerId("server-1")
    private lateinit var fake: FakeLidarrSystem
    private lateinit var store: FakeEntityStore

    @Before
    fun setUp() {
        fake = FakeLidarrSystem()
        fake.mediaManagementConfig = MediaManagementConfigResource(
            id = 1,
            copyUsingHardlinks = true,
            importExtraFiles = false,
            minimumFreeSpaceWhenImporting = 128,
        )
        store = FakeEntityStore()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun TestScope.repository() = LidarrMediaManagementRepository(
        serverId = serverId,
        store = store,
        mediaManagementApi = fake,
        namingApi = fake,
        actionQueue = ActionQueueImpl(store),
        ioContext = StandardTestDispatcher(testScheduler),
    )

    private fun TestScope.viewModel() = LidarrMediaManagementViewModel(
        serverId = serverId,
        repository = repository(),
    )

    private fun <T> kotlinx.coroutines.flow.StateFlow<T>.collectWhileActive(
        scope: TestScope,
    ) {
        scope.backgroundScope.launch { collect {} }
    }

    @Test
    fun `loads the config into an editable draft`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val vm = viewModel()
        vm.lce.collectWhileActive(this)
        advanceUntilIdle()

        val content = (vm.lce.value as LceState.Content<LidarrMediaManagementContent>).data
        assertTrue(content.draft.copyUsingHardlinks)
        assertEquals("128", content.draft.minimumFreeSpaceWhenImporting)
        assertFalse(content.dirty)
    }

    @Test
    fun `edits mark the draft dirty and keep partial number input`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val vm = viewModel()
        vm.lce.collectWhileActive(this)
        advanceUntilIdle()

        vm.edit { it.copy(minimumFreeSpaceWhenImporting = "51") }
        advanceUntilIdle()

        val content = (vm.lce.value as LceState.Content<LidarrMediaManagementContent>).data
        assertTrue(content.dirty)
        assertEquals("51", content.draft.minimumFreeSpaceWhenImporting)
    }

    @Test
    fun `save PUTs the draft and clears the dirty flag`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val vm = viewModel()
        vm.lce.collectWhileActive(this)
        advanceUntilIdle()
        vm.edit { it.copy(importExtraFiles = true) }

        vm.save()
        advanceUntilIdle()

        assertEquals(MediaManagementNotice.SAVED, vm.state.value.notice)
        assertFalse(vm.state.value.dirty)
        assertTrue(fake.mediaManagementConfig.importExtraFiles)
    }

    @Test
    fun `offline save queues and reports the pending count`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val vm = viewModel()
        vm.lce.collectWhileActive(this)
        advanceUntilIdle()
        fake.error = { op -> if (op == "updateMediaManagementConfig") IOException("offline") else null }
        vm.edit { it.copy(createEmptyArtistFolders = true) }

        vm.save()
        advanceUntilIdle()

        assertEquals(MediaManagementNotice.SAVED_QUEUED, vm.state.value.notice)
        assertEquals(1, vm.state.value.pendingActions)
        // Optimistic cache: the toggle reflects the user's intent.
        assertTrue(fake.mediaManagementConfig.createEmptyArtistFolders.not())
        assertTrue(
            ActionQueueImpl(store).pending().single().path.startsWith("api/v1/config/mediamanagement"),
        )
    }

    @Test
    fun `a picked path writes into the targeted draft field`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val vm = viewModel()
        vm.lce.collectWhileActive(this)
        advanceUntilIdle()

        vm.setPickedField(LidarrMediaManagementViewModel.FIELD_RECYCLE_BIN)
        vm.applyPickedPath("/mnt/recycle")

        assertEquals("/mnt/recycle", vm.state.value.draft.recycleBin)
        assertNull(vm.state.value.pickedField)
        assertTrue(vm.state.value.dirty)
    }

    @Test
    fun `first load failure shows the error state`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        fake.error = { op -> if (op == "getMediaManagementConfig") IOException("offline") else null }
        val vm = viewModel()
        vm.lce.collectWhileActive(this)
        advanceUntilIdle()

        assertTrue(vm.lce.value is LceState.Error)
    }
}
