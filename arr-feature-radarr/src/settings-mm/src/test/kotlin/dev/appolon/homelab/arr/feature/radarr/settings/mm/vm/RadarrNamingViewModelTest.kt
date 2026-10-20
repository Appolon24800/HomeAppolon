package dev.appolon.homelab.arr.feature.radarr.settings.mm.vm

import dev.appolon.homelab.arr.api.radarr.FakeRadarrSystem
import dev.appolon.homelab.arr.api.radarr.model.NamingConfigResource
import dev.appolon.homelab.arr.api.radarr.model.NamingExampleResource
import dev.appolon.homelab.arr.core.ServerId
import dev.appolon.homelab.arr.data.store.ActionQueueImpl
import dev.appolon.homelab.arr.data.testing.FakeEntityStore
import dev.appolon.homelab.arr.feature.radarr.settings.mm.data.RadarrMediaManagementRepository
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
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RadarrNamingViewModelTest {

    private val serverId = ServerId("server-1")
    private lateinit var fake: FakeRadarrSystem
    private lateinit var store: FakeEntityStore

    @Before
    fun setUp() {
        fake = FakeRadarrSystem()
        fake.namingConfigState = NamingConfigResource(
            id = 1,
            renameMovies = true,
            standardMovieFormat = "{Movie Title} ({Year})",
            movieFolderFormat = "{Movie Title}",
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

    private fun TestScope.viewModel() = RadarrNamingViewModel(
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
    fun `loads the naming config and renders the local preview`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val vm = viewModel()
        vm.lce.collectWhileActive(this)
        advanceUntilIdle()

        val content = (vm.lce.value as LceState.Content<RadarrNamingContent>).data
        assertEquals("{Movie Title} ({Year})", content.draft.standardMovieFormat)
        assertEquals("Movie Title (2010)", content.localMoviePreview)
        assertEquals("Movie Title", content.localFolderPreview)
    }

    @Test
    fun `edits re-render the preview instantly`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val vm = viewModel()
        vm.lce.collectWhileActive(this)
        advanceUntilIdle()

        vm.edit { it.copy(standardMovieFormat = "{Movie Title} - {Quality Title}") }
        advanceUntilIdle()

        val content = (vm.lce.value as LceState.Content<RadarrNamingContent>).data
        assertEquals("Movie Title - Bluray-1080p", content.localMoviePreview)
        assertTrue(content.dirty)
    }

    @Test
    fun `token insertion splices at the cursor and moves past it`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val vm = viewModel()

        val (appended, cursorAfterAppend) = vm.insertToken("{Movie Title}", 13, "{Year}")
        assertEquals("{Movie Title} {Year}", appended)
        assertEquals(appended.length, cursorAfterAppend)

        val (midText, midCursor) = vm.insertToken("AB", 1, "{Year}")
        assertEquals("A {Year}B", midText)
        assertEquals("A {Year}".length, midCursor)
    }

    @Test
    fun `server examples arrive after the debounce and mark their source`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        fake.namingExamplesResult = NamingExampleResource(
            movieExample = "Server Movie (2010)",
            movieFolderExample = "Server Folder",
        )
        val vm = viewModel()
        vm.lce.collectWhileActive(this)
        advanceTimeBy(500)
        advanceUntilIdle()

        assertEquals(fake.namingExamplesResult, vm.state.value.serverExample)
        val content = (vm.lce.value as LceState.Content<RadarrNamingContent>).data
        assertEquals("Server Movie (2010)", content.serverExample?.movieExample)
    }

    @Test
    fun `an edit clears the stale server example until the next fetch`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        fake.namingExamplesResult = NamingExampleResource(movieExample = "Server Movie")
        val vm = viewModel()
        vm.lce.collectWhileActive(this)
        advanceTimeBy(500)
        advanceUntilIdle()
        assertTrue(vm.state.value.serverExample != null)

        vm.edit { it.copy(standardMovieFormat = "{Movie Title}") }

        assertNull(vm.state.value.serverExample)
    }

    @Test
    fun `offline save queues the naming PUT and reports it`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val vm = viewModel()
        vm.lce.collectWhileActive(this)
        advanceUntilIdle()
        fake.errorFor = { op -> if (op == "updateNamingConfig") IOException("offline") else null }
        vm.edit { it.copy(replaceIllegalCharacters = true) }

        vm.save()
        advanceUntilIdle()

        val state = vm.state.value
        assertEquals(NamingNotice.SAVED_QUEUED, state.notice)
        assertEquals(1, state.pendingActions)
        // The queue path addresses the naming config endpoint.
        val pending = ActionQueueImpl(store).pending()
        assertTrue(pending.single().path.startsWith("api/v3/config/naming"))
    }

    @Test
    fun `example failures never block editing`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        fake.errorFor = { op -> if (op == "namingExamples") IOException("offline") else null }
        val vm = viewModel()
        vm.lce.collectWhileActive(this)
        advanceTimeBy(500)
        advanceUntilIdle()

        val content = (vm.lce.value as LceState.Content<RadarrNamingContent>).data
        assertNull(content.serverExample)
        assertEquals("Movie Title (2010)", content.localMoviePreview)
        assertNull(vm.state.value.writeError)
    }
}
