package dev.appolon.homelab.arr.feature.radarr.settings.mm.vm

import dev.appolon.homelab.arr.api.radarr.FakeRadarrSystem
import dev.appolon.homelab.arr.api.radarr.model.MediaManagementConfigResource
import dev.appolon.homelab.arr.core.ServerId
import dev.appolon.homelab.arr.data.store.ActionQueueImpl
import dev.appolon.homelab.arr.data.testing.FakeEntityStore
import dev.appolon.homelab.arr.feature.radarr.settings.mm.data.RadarrMediaManagementRepository
import dev.appolon.homelab.arr.ui.lce.LceState
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.StateFlow
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
class RadarrMediaManagementViewModelTest {

    private val serverId = ServerId("server-1")
    private lateinit var fake: FakeRadarrSystem
    private lateinit var store: FakeEntityStore

    @Before
    fun setUp() {
        fake = FakeRadarrSystem()
        fake.mediaManagementConfigState = MediaManagementConfigResource(
            id = 1,
            copyUsingHardlinks = true,
            minimumFreeSpaceWhenImporting = 256,
            setPermissionsLinux = true,
            chmodFolder = "775",
        )
        store = FakeEntityStore()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun TestScope.repository() = RadarrMediaManagementRepository(
        serverId = serverId,
        store = store,
        mediaManagementApi = fake,
        namingApi = fake,
        actionQueue = ActionQueueImpl(store),
        ioContext = StandardTestDispatcher(testScheduler),
    )

    private fun TestScope.viewModel() = RadarrMediaManagementViewModel(
        serverId = serverId,
        repository = repository(),
    )

    private fun <T> StateFlow<T>.collectWhileActive(
        scope: kotlinx.coroutines.test.TestScope,
    ) {
        scope.backgroundScope.launch { collect {} }
    }

    @Test
    fun `loads the config into a content draft`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val vm = viewModel()
        vm.lce.collectWhileActive(this)
        advanceUntilIdle()

        val content = (vm.lce.value as LceState.Content<RadarrMediaManagementContent>).data
        assertEquals("775", content.draft.chmodFolder)
        assertEquals(true, content.draft.copyUsingHardlinks)
        assertEquals("256", content.draft.minimumFreeSpaceWhenImporting)
        assertFalse(content.dirty)
    }

    @Test
    fun `load failure with no cache renders the error state`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        fake.errorFor = { op -> if (op == "mediaManagementConfig") IOException("offline") else null }
        val vm = viewModel()
        vm.lce.collectWhileActive(this)
        advanceUntilIdle()

        assertTrue(vm.lce.value is LceState.Error)
    }

    @Test
    fun `edits mark the draft dirty without touching the network`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val vm = viewModel()
        vm.lce.collectWhileActive(this)
        advanceUntilIdle()

        vm.edit { it.copy(chmodFolder = "770") }

        val state = vm.state.value
        assertTrue(state.dirty)
        assertEquals("770", state.draft.chmodFolder)
    }

    @Test
    fun `save syncs online and clears the dirty flag`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val vm = viewModel()
        vm.lce.collectWhileActive(this)
        advanceUntilIdle()
        vm.edit { it.copy(chmodFolder = "700") }

        vm.save()
        advanceUntilIdle()

        val state = vm.state.value
        assertFalse(state.dirty)
        assertEquals(MediaManagementNotice.SAVED, state.notice)
        assertEquals("700", fake.mediaManagementConfigState.chmodFolder)
    }

    @Test
    fun `offline save queues and reports the queued notice`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val vm = viewModel()
        vm.lce.collectWhileActive(this)
        advanceUntilIdle()
        fake.errorFor = { op -> if (op == "updateMediaManagementConfig") IOException("offline") else null }
        vm.edit { it.copy(importExtraFiles = true) }

        vm.save()
        advanceUntilIdle()

        val state = vm.state.value
        assertEquals(MediaManagementNotice.SAVED_QUEUED, state.notice)
        assertEquals(1, state.pendingActions)
        // Optimistic: the toggle reads as on before the replay.
        assertTrue(state.draft.importExtraFiles)
    }

    @Test
    fun `picked paths land in the right draft field`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val vm = viewModel()
        vm.lce.collectWhileActive(this)
        advanceUntilIdle()

        vm.setPickedField(RadarrMediaManagementViewModel.FIELD_RECYCLE_BIN)
        vm.applyPickedPath("/var/tmp/radarr")

        assertEquals("/var/tmp/radarr", vm.state.value.draft.recycleBin)
        assertNull(vm.state.value.pickedField)
    }

    @Test
    fun `numeric drafts fall back to the loaded value at save time`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val vm = viewModel()
        vm.lce.collectWhileActive(this)
        advanceUntilIdle()

        vm.edit { it.copy(minimumFreeSpaceWhenImporting = "not-a-number") }
        vm.save()
        advanceUntilIdle()

        // The malformed text never reaches the wire; the loaded 256 stands.
        assertEquals(256, fake.mediaManagementConfigState.minimumFreeSpaceWhenImporting)
    }
}
