package dev.appolon.homelab.arr.feature.lidarr.settings.mm.vm

import dev.appolon.homelab.arr.api.lidarr.FakeLidarrSystem
import dev.appolon.homelab.arr.api.lidarr.model.NamingConfigResource
import dev.appolon.homelab.arr.core.ServerId
import dev.appolon.homelab.arr.data.store.ActionQueueImpl
import dev.appolon.homelab.arr.feature.lidarr.settings.mm.FakeSettingsMmEntityStore
import dev.appolon.homelab.arr.feature.lidarr.settings.mm.data.LidarrMediaManagementRepository
import dev.appolon.homelab.arr.ui.lce.LceState
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LidarrNamingViewModelTest {

    private val serverId = ServerId("server-1")
    private lateinit var fake: FakeLidarrSystem
    private lateinit var store: FakeSettingsMmEntityStore

    @Before
    fun setUp() {
        fake = FakeLidarrSystem()
        fake.namingConfig = NamingConfigResource(
            id = 1,
            renameTracks = true,
            standardTrackFormat = "{Artist Name} ({Album ReleaseYear})",
            multiDiscTrackFormat = "{Artist Name} - {Album Title} - {Medium:00} - {Track:00} - {Track Title}",
            artistFolderFormat = "{Artist Name}",
        )
        store = FakeSettingsMmEntityStore()
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

    private fun TestScope.viewModel() = LidarrNamingViewModel(
        serverId = serverId,
        repository = repository(),
        exampleDebounceMillis = 100,
    )

    private fun <T> kotlinx.coroutines.flow.StateFlow<T>.collectWhileActive(
        scope: TestScope,
    ) {
        scope.backgroundScope.launch { collect {} }
    }

    @Test
    fun `loads the naming config and renders the local previews`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val vm = viewModel()
        vm.lce.collectWhileActive(this)
        advanceUntilIdle()

        val content = (vm.lce.value as LceState.Content<LidarrNamingContent>).data
        assertEquals("The Artist Name (2010)", content.localTrackPreview)
        assertEquals(
            "The Artist Name - The Album Title - 01 - 01 - Track Title (1)",
            content.localMultiDiscPreview,
        )
        assertEquals("The Artist Name", content.localArtistFolderPreview)
    }

    @Test
    fun `edits re-render the preview instantly`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val vm = viewModel()
        vm.lce.collectWhileActive(this)
        advanceUntilIdle()

        vm.edit { it.copy(standardTrackFormat = "{Artist Name} - {Quality Title}") }
        advanceUntilIdle()

        val content = (vm.lce.value as LceState.Content<LidarrNamingContent>).data
        assertEquals("The Artist Name - MP3-320", content.localTrackPreview)
        assertTrue(content.dirty)
    }

    @Test
    fun `token insertion splices at the cursor and moves past it`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val vm = viewModel()

        val (appended, cursorAfterAppend) = vm.insertToken("{Artist Name}", 16, "{Track:00}")
        assertEquals("{Artist Name} {Track:00}", appended)
        assertEquals(appended.length, cursorAfterAppend)

        val (midText, midCursor) = vm.insertToken("AB", 1, "{Track:00}")
        assertEquals("A {Track:00}B", midText)
        assertEquals("A {Track:00}".length, midCursor)
    }

    @Test
    fun `server examples arrive after the debounce and mark their source`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        fake.namingExamples = buildJsonObject {
            put("standardTrackFormat", "Server Track Example")
            put("multiDiscTrackFormat", "Server Multi-Disc Example")
            put("artistFolderFormat", "Server Artist Folder")
        }
        val vm = viewModel()
        vm.lce.collectWhileActive(this)
        advanceTimeBy(500)
        advanceUntilIdle()

        assertEquals("Server Track Example", vm.state.value.serverExample?.standardTrackExample)
        val content = (vm.lce.value as LceState.Content<LidarrNamingContent>).data
        assertEquals("Server Multi-Disc Example", content.serverExample?.multiDiscTrackExample)
    }

    @Test
    fun `an edit clears the stale server example until the next fetch`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        fake.namingExamples = buildJsonObject {
            put("standardTrackFormat", "Server Track Example")
        }
        val vm = viewModel()
        vm.lce.collectWhileActive(this)
        advanceTimeBy(500)
        advanceUntilIdle()
        assertTrue(vm.state.value.serverExample != null)

        vm.edit { it.copy(standardTrackFormat = "{Artist Name}") }

        assertNull(vm.state.value.serverExample)
    }

    @Test
    fun `offline save queues the naming PUT and reports it`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val vm = viewModel()
        vm.lce.collectWhileActive(this)
        advanceUntilIdle()
        fake.error = { op -> if (op == "updateNamingConfig") IOException("offline") else null }
        vm.edit { it.copy(replaceIllegalCharacters = true) }

        vm.save()
        advanceUntilIdle()

        val state = vm.state.value
        assertEquals(NamingNotice.SAVED_QUEUED, state.notice)
        assertEquals(1, state.pendingActions)
        // The queue path addresses the naming config endpoint.
        val pending = ActionQueueImpl(store).pending()
        assertTrue(pending.single().path.startsWith("api/v1/config/naming"))
    }

    @Test
    fun `example failures never block editing`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        fake.error = { op -> if (op == "getNamingExamples") IOException("offline") else null }
        val vm = viewModel()
        vm.lce.collectWhileActive(this)
        advanceTimeBy(500)
        advanceUntilIdle()

        val content = (vm.lce.value as LceState.Content<LidarrNamingContent>).data
        assertNull(content.serverExample)
        assertEquals("The Artist Name (2010)", content.localTrackPreview)
        assertNull(vm.state.value.writeError)
    }
}
