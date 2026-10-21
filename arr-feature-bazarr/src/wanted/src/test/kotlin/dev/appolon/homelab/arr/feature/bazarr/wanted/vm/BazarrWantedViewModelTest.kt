package dev.appolon.homelab.arr.feature.bazarr.wanted.vm

import dev.appolon.homelab.arr.api.bazarr.FakeBazarrLibrary
import dev.appolon.homelab.arr.api.bazarr.FakeBazarrSystem
import dev.appolon.homelab.arr.feature.bazarr.episodes.FakeActionQueue
import dev.appolon.homelab.arr.feature.bazarr.episodes.FakeEntityStore
import dev.appolon.homelab.arr.feature.bazarr.wanted.dataPayload
import dev.appolon.homelab.arr.feature.bazarr.wanted.missingLanguageRow
import dev.appolon.homelab.arr.feature.bazarr.wanted.newWantedRepository
import dev.appolon.homelab.arr.feature.bazarr.wanted.wantedEpisodeRow
import dev.appolon.homelab.arr.feature.bazarr.wanted.wantedMovieRow
import dev.appolon.homelab.arr.ui.lce.LceState
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
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
class BazarrWantedViewModelTest {

    private val main = UnconfinedTestDispatcher()
    private lateinit var library: FakeBazarrLibrary
    private lateinit var system: FakeBazarrSystem
    private lateinit var queue: FakeActionQueue
    private lateinit var vm: BazarrWantedViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(main)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun seed() {
        library = FakeBazarrLibrary()
        system = FakeBazarrSystem()
        queue = FakeActionQueue()
        library.responses["get_episodes_wanted"] = dataPayload(
            listOf(
                wantedEpisodeRow(
                    episodeId = 31,
                    seriesId = 12,
                    season = 1,
                    episode = 2,
                    missing = listOf(missingLanguageRow("de", "German", hi = true), missingLanguageRow("fr", "French")),
                ),
                wantedEpisodeRow(
                    episodeId = 32,
                    seriesId = 12,
                    season = 1,
                    episode = 1,
                    missing = listOf(missingLanguageRow("en", "English")),
                ),
            ),
        )
        library.responses["get_movies_wanted"] = dataPayload(
            listOf(wantedMovieRow(radarrId = 42, missing = listOf(missingLanguageRow("es", "Spanish")))),
        )
        library.responses["get_badges"] = buildJsonObject {
            put("episodes", 2)
            put("movies", 1)
        }
        system.responses["get_languages"] = dataPayload(
            listOf(
                buildJsonObject { put("code2", "de"); put("name", "German") },
                buildJsonObject { put("code2", "fr"); put("name", "French") },
            ),
        )
    }

    private fun buildVm(): BazarrWantedViewModel = BazarrWantedViewModel(
        repository = newWantedRepository(library, system, FakeEntityStore(), queue, io = main),
    )

    private fun runVm(block: suspend kotlinx.coroutines.test.TestScope.() -> Unit) = runTest(main) {
        seed()
        vm = buildVm()
        val job = launch { vm.lce.collect {} }
        advanceUntilIdle()
        block()
        advanceUntilIdle()
        job.cancel()
    }

    @Test
    fun `initial load lists wanted rows with badges and language chips`() = runVm {
        assertTrue(vm.lce.value is LceState.Content)
        val state = vm.state.value
        assertEquals(2, state.episodes.size)
        assertEquals(1, state.movies.size)
        assertEquals(2, state.languages.size)
        assertEquals(2, state.badges?.episodes)
        assertEquals(1, state.badges?.movies)
    }

    @Test
    fun `language chip narrows the visible tab`() = runVm {
        vm.setLanguageFilter("fr")
        assertEquals(listOf(31), vm.state.value.presentedEpisodes.map { it.episodeId })
        vm.setTab(WantedListTab.MOVIES)
        assertTrue(vm.state.value.presentedMovies.isEmpty())
    }

    @Test
    fun `per item search requests every missing language`() = runVm {
        val row = vm.state.value.episodes.first { it.episodeId == 31 }

        vm.searchEpisode(row)

        val downloads = library.calls.filter { it.operation == "patch_episodes_subtitles" }
        assertEquals(listOf("de", "fr"), downloads.map { it.arguments["language"] })
        assertEquals(listOf("True", "False"), downloads.map { it.arguments["hi"] })
        assertEquals(WantedMessage.SearchSynced(1), vm.state.value.message)
    }

    @Test
    fun `bulk search of the selection requests every row then clears it`() = runVm {
        vm.toggleEpisodeSelected(31)
        vm.toggleEpisodeSelected(32)

        vm.searchSelected()

        val downloads = library.calls.filter { it.operation == "patch_episodes_subtitles" }
        assertEquals(listOf("en", "de", "fr"), downloads.map { it.arguments["language"] })
        assertEquals(WantedMessage.SearchSynced(2), vm.state.value.message)
        assertTrue(vm.state.value.selectedEpisodeIds.isEmpty())
    }

    @Test
    fun `offline bulk search queues every row and flags them`() = runVm {
        library.failure = { call -> if (call.operation == "patch_episodes_subtitles") IOException() else null }
        vm.selectAllPresented()

        vm.searchAllPresented()

        assertEquals(WantedMessage.SearchQueued(2), vm.state.value.message)
        assertEquals(setOf(31, 32), vm.state.value.queuedEpisodeIds)
    }

    @Test
    fun `select all on the movies tab targets every presented movie`() = runVm {
        vm.setTab(WantedListTab.MOVIES)

        vm.selectAllPresented()
        vm.searchAllPresented()

        val downloads = library.calls.filter { it.operation == "patch_movies_subtitles" }
        assertEquals(1, downloads.size)
        assertEquals(42, downloads.single().arguments["radarrid"])
        assertEquals("es", downloads.single().arguments["language"])
    }

    @Test
    fun `switching tabs drops the selection`() = runVm {
        vm.toggleEpisodeSelected(31)
        assertEquals(1, vm.state.value.selectionCount)

        vm.setTab(WantedListTab.MOVIES)

        assertEquals(0, vm.state.value.selectionCount)
        assertTrue(vm.state.value.selectedEpisodeIds.isEmpty())
    }

    @Test
    fun `consume message clears the snackbar`() = runVm {
        val row = vm.state.value.episodes.first { it.episodeId == 31 }
        vm.searchEpisode(row)
        assertTrue(vm.state.value.message != null)

        vm.consumeMessage()

        assertNull(vm.state.value.message)
    }
}
