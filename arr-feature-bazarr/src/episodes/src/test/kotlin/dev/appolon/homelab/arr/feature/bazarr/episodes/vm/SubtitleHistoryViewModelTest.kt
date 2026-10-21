package dev.appolon.homelab.arr.feature.bazarr.episodes.vm

import dev.appolon.homelab.arr.api.bazarr.FakeBazarrLibrary
import dev.appolon.homelab.arr.api.bazarr.FakeBazarrSubtitles
import dev.appolon.homelab.arr.api.bazarr.FakeBazarrSystem
import dev.appolon.homelab.arr.feature.bazarr.episodes.FakeActionQueue
import dev.appolon.homelab.arr.feature.bazarr.episodes.FakeEntityStore
import dev.appolon.homelab.arr.feature.bazarr.episodes.data.historyRow
import dev.appolon.homelab.arr.feature.bazarr.episodes.data.newRepository
import dev.appolon.homelab.arr.ui.lce.LceState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SubtitleHistoryViewModelTest {

    private val main = UnconfinedTestDispatcher()
    private lateinit var library: FakeBazarrLibrary
    private lateinit var subtitles: FakeBazarrSubtitles
    private lateinit var system: FakeBazarrSystem
    private lateinit var vm: SubtitleHistoryViewModel

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
        subtitles = FakeBazarrSubtitles()
        system = FakeBazarrSystem()
        library.responses["get_episodes_history"] = buildJsonObject {
            put(
                "data",
                JsonArray(
                    listOf(
                        historyRow("downloaded", 1_790_000_000, title = "Pilot", language = "en", episodeId = 31),
                        historyRow("deleted", 1_789_000_000, title = "Rooster", language = "de", episodeId = 32),
                    ),
                ),
            )
        }
        library.responses["get_movies_history"] = buildJsonObject {
            put("data", JsonArray(listOf(historyRow("uploaded", 1_790_000_100, title = "Arrival", language = "es", radarrId = 42))))
        }
        system.responses["get_languages"] = buildJsonObject {
            put(
                "data",
                JsonArray(
                    listOf(
                        buildJsonObject { put("code2", "en"); put("name", "English") },
                        buildJsonObject { put("code2", "de"); put("name", "German") },
                    ),
                ),
            )
        }
    }

    private fun buildVm(): SubtitleHistoryViewModel = SubtitleHistoryViewModel(
        repository = newRepository(library, subtitles, system, FakeEntityStore(), FakeActionQueue(), io = main),
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
    fun `merged feed renders newest first with language chips from the endpoint`() = runVm {
        assertTrue(vm.lce.value is LceState.Content)
        val state = vm.state.value
        assertEquals(3, state.events.size)
        assertEquals("uploaded", state.presented.first().action)
        assertEquals(42, state.presented.first().radarrId)
        assertEquals(listOf("en", "de"), state.languages.map { it.code2 })
        assertEquals(listOf("uploaded", "downloaded", "deleted"), state.actions)
    }

    @Test
    fun `language chip and action chip narrow the feed independently`() = runVm {
        vm.setLanguageFilter("de")
        assertEquals(listOf("deleted"), vm.state.value.presented.map { it.action })
        vm.setLanguageFilter("de")
        vm.setActionFilter("uploaded")
        assertEquals(listOf("uploaded"), vm.state.value.presented.map { it.action })
        vm.setActionFilter("uploaded")
        assertEquals(3, vm.state.value.presented.size)
    }

    @Test
    fun `refresh failure with cached rows keeps content`() = runVm {
        library.errorStatus = { call -> if (call.operation == "get_movies_history") 500 else null }

        vm.refresh()
        advanceUntilIdle()

        // Episode rows were cached before the movie feed failed; content stays.
        assertTrue(vm.lce.value is LceState.Content)
        assertTrue(vm.state.value.events.isNotEmpty())
    }
}
