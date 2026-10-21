package dev.appolon.homelab.arr.feature.sonarr.settings.mm.vm

import dev.appolon.homelab.arr.api.sonarr.FakeSonarrSystem
import dev.appolon.homelab.arr.api.sonarr.model.NamingConfigResource
import dev.appolon.homelab.arr.core.ServerId
import dev.appolon.homelab.arr.data.store.ActionQueueImpl
import dev.appolon.homelab.arr.data.testing.FakeEntityStore
import dev.appolon.homelab.arr.feature.sonarr.settings.mm.data.SonarrMediaManagementRepository
import dev.appolon.homelab.arr.feature.sonarr.settings.mm.data.SonarrNamingExamples
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
class SonarrNamingViewModelTest {

    private val serverId = ServerId("server-1")
    private lateinit var fake: FakeSonarrSystem
    private lateinit var store: FakeEntityStore

    @Before
    fun setUp() {
        fake = FakeSonarrSystem()
        fake.namingConfig = NamingConfigResource(
            id = 1,
            renameEpisodes = true,
            standardEpisodeFormat = "{Series Title} - S{season:00}E{episode:00} - {Episode Title}",
            dailyEpisodeFormat = "{Series Title} - {yyyy-MM-dd} - {Episode Title}",
            animeEpisodeFormat = "{Series Title} - {absolute:000} - {Episode Title}",
            seriesFolderFormat = "{Series Title}",
            seasonFolderFormat = "Season {season:00}",
            specialsFolderFormat = "Specials",
        )
        store = FakeEntityStore()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun TestScope.repository() = SonarrMediaManagementRepository(
        serverId = serverId,
        store = store,
        mediaManagementApi = fake.mediaManagementConfigApi,
        namingApi = fake.namingConfigApi,
        actionQueue = ActionQueueImpl(store),
        ioContext = StandardTestDispatcher(testScheduler),
    )

    private fun TestScope.viewModel() = SonarrNamingViewModel(
        serverId = serverId,
        repository = repository(),
        exampleDebounceMillis = 100,
    )

    private fun <T> kotlinx.coroutines.flow.StateFlow<T>.collectWhileActive(
        scope: TestScope,
    ) {
        // backgroundScope: runTest cancels these collectors when the test
        // body ends instead of failing on uncompleted coroutines.
        scope.backgroundScope.launch { collect {} }
    }

    @Test
    fun `loads the naming config and renders the local previews`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val vm = viewModel()
        vm.lce.collectWhileActive(this)
        advanceUntilIdle()

        val content = (vm.lce.value as LceState.Content<SonarrNamingContent>).data
        assertEquals(
            "{Series Title} - S{season:00}E{episode:00} - {Episode Title}",
            content.draft.standardEpisodeFormat,
        )
        assertEquals(
            "The Series Title's! - S01E01 - Episode Title (1)",
            content.localStandardPreview,
        )
        assertEquals(
            "The Series Title's! - 2013-10-30 - Episode Title (1)",
            content.localDailyPreview,
        )
        assertEquals(
            "The Series Title's! - 001 - Episode Title (1)",
            content.localAnimePreview,
        )
        assertEquals("Season 01", content.localSeasonFolderPreview)
        assertEquals("Specials", content.localSpecialsFolderPreview)
    }

    @Test
    fun `edits re-render the preview instantly`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val vm = viewModel()
        vm.lce.collectWhileActive(this)
        advanceUntilIdle()

        vm.edit { it.copy(standardEpisodeFormat = "{Series Title} - {Quality Title}") }
        // The lce stateIn propagates through a coroutine hop; advance the
        // scheduler before reading it.
        advanceUntilIdle()

        val content = (vm.lce.value as LceState.Content<SonarrNamingContent>).data
        assertEquals("The Series Title's! - WEBDL-1080p", content.localStandardPreview)
        assertTrue(content.dirty)
    }

    @Test
    fun `token insertion splices at the cursor and moves past it`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val vm = viewModel()

        val (appended, cursorAfterAppend) = vm.insertToken("{Series Title}", 17, "{Episode:00}")
        assertEquals("{Series Title} {Episode:00}", appended)
        assertEquals(appended.length, cursorAfterAppend)

        val (midText, midCursor) = vm.insertToken("AB", 1, "{Episode:00}")
        assertEquals("A{Episode:00}B", midText)
        assertEquals("A{Episode:00}".length, midCursor)
    }

    @Test
    fun `server examples arrive after the debounce and clear on edit`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val vm = viewModel()
        vm.lce.collectWhileActive(this)
        advanceTimeBy(500)
        advanceUntilIdle()

        // The fake answers with an empty payload: parsed but all-null.
        assertEquals(SonarrNamingExamples(), vm.state.value.serverExample)

        vm.edit { it.copy(standardEpisodeFormat = "{Series Title}") }

        assertNull(vm.state.value.serverExample)
    }

    @Test
    fun `example fetches carry the draft formats`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val vm = viewModel()
        vm.lce.collectWhileActive(this)
        advanceTimeBy(500)
        advanceUntilIdle()

        val request = fake.namingExampleRequests.single()
        assertEquals("true", request["renameEpisodes"])
        assertEquals(
            "{Series Title} - S{season:00}E{episode:00} - {Episode Title}",
            request["standardEpisodeFormat"],
        )
    }

    @Test
    fun `offline save queues the naming PUT and reports it`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val vm = viewModel()
        vm.lce.collectWhileActive(this)
        advanceUntilIdle()
        fake.error = { op -> if (op == "NamingConfig.update") IOException("offline") else null }
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
        fake.error = { op -> if (op == "NamingConfig.examples") IOException("offline") else null }
        val vm = viewModel()
        vm.lce.collectWhileActive(this)
        advanceTimeBy(500)
        advanceUntilIdle()

        val content = (vm.lce.value as LceState.Content<SonarrNamingContent>).data
        assertNull(content.serverExample)
        assertEquals(
            "The Series Title's! - S01E01 - Episode Title (1)",
            content.localStandardPreview,
        )
        assertNull(vm.state.value.writeError)
    }
}
