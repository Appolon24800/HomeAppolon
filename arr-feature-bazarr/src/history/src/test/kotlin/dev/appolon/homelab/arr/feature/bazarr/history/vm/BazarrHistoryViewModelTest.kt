package dev.appolon.homelab.arr.feature.bazarr.history.vm

import dev.appolon.homelab.arr.api.bazarr.FakeBazarrLibrary
import dev.appolon.homelab.arr.api.bazarr.FakeBazarrSystem
import dev.appolon.homelab.arr.feature.bazarr.episodes.FakeActionQueue
import dev.appolon.homelab.arr.feature.bazarr.episodes.FakeEntityStore
import dev.appolon.homelab.arr.feature.bazarr.history.historyDataPayload
import dev.appolon.homelab.arr.feature.bazarr.history.historyEventRow
import dev.appolon.homelab.arr.feature.bazarr.history.languageOptionRow
import dev.appolon.homelab.arr.feature.bazarr.history.newHistoryRepository
import dev.appolon.homelab.arr.ui.lce.LceState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
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
class BazarrHistoryViewModelTest {

    private val main = UnconfinedTestDispatcher()
    private lateinit var library: FakeBazarrLibrary
    private lateinit var system: FakeBazarrSystem
    private lateinit var vm: BazarrHistoryViewModel

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
        system.responses["get_languages"] = historyDataPayload(
            listOf(
                languageOptionRow("en", "English"),
                languageOptionRow("de", "German"),
            ),
        )
        library.responses["get_episodes_history"] = historyDataPayload(
            listOf(
                historyEventRow(action = "upgraded", timestamp = 1_700_000_002L, episodeId = 31, language = "en"),
                historyEventRow(action = "downloaded", timestamp = 1_700_000_001L, episodeId = 32, language = "de"),
            ),
        )
        library.responses["get_movies_history"] = historyDataPayload(
            listOf(
                historyEventRow(
                    action = "downloaded",
                    timestamp = 1_700_000_100L,
                    radarrId = 42,
                    language = "en",
                    title = "Arrival",
                ),
            ),
        )
    }

    private fun buildVm(): BazarrHistoryViewModel = BazarrHistoryViewModel(
        repository = newHistoryRepository(library, system, FakeEntityStore(), FakeActionQueue(), io = main),
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
    fun `initial load merges both feeds newest first with language chips`() = runVm {
        assertTrue(vm.lce.value is LceState.Content)
        val state = vm.state.value
        assertEquals(3, state.events.size)
        assertEquals(1_700_000_100_000L, state.events.first().timestampMillis)
        assertEquals(2, state.languages.size)
        // Three rows are fewer than one page, so the feed is already complete.
        assertTrue(state.endReached)
    }

    @Test
    fun `media filter narrows the feed by kind`() = runVm {
        vm.setMediaFilter(HistoryMediaFilter.MOVIES)
        assertEquals(listOf(42L), vm.state.value.presented.map { (it.radarrId ?: 0).toLong() })

        vm.setMediaFilter(HistoryMediaFilter.EPISODES)
        assertEquals(listOf(31L, 32L), vm.state.value.presented.map { (it.episodeId ?: 0).toLong() })

        vm.setMediaFilter(HistoryMediaFilter.ALL)
        assertEquals(3, vm.state.value.presented.size)
    }

    @Test
    fun `language filter narrows the feed within the media filter`() = runVm {
        vm.setMediaFilter(HistoryMediaFilter.EPISODES)

        vm.setLanguageFilter("de")

        assertEquals(listOf(32), vm.state.value.presented.map { it.episodeId })
        vm.setLanguageFilter("de")
        assertEquals(2, vm.state.value.presented.size)
    }

    @Test
    fun `load more is a no-op once the feeds are exhausted`() = runVm {
        val callsBefore = library.calls.count { it.operation == "get_episodes_history" }

        vm.loadMore()
        advanceUntilIdle()

        val callsAfter = library.calls.count { it.operation == "get_episodes_history" }
        assertEquals(callsBefore, callsAfter)
        assertTrue(vm.state.value.endReached)
    }
}
