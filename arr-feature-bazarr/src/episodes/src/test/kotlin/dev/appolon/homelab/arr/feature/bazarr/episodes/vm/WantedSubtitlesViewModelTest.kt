package dev.appolon.homelab.arr.feature.bazarr.episodes.vm

import dev.appolon.homelab.arr.api.bazarr.FakeBazarrLibrary
import dev.appolon.homelab.arr.api.bazarr.FakeBazarrSubtitles
import dev.appolon.homelab.arr.api.bazarr.FakeBazarrSystem
import dev.appolon.homelab.arr.feature.bazarr.episodes.FakeActionQueue
import dev.appolon.homelab.arr.feature.bazarr.episodes.FakeEntityStore
import dev.appolon.homelab.arr.feature.bazarr.episodes.data.newRepository
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
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WantedSubtitlesViewModelTest {

    private val main = UnconfinedTestDispatcher()
    private lateinit var library: FakeBazarrLibrary
    private lateinit var subtitles: FakeBazarrSubtitles
    private lateinit var system: FakeBazarrSystem
    private lateinit var vm: WantedSubtitlesViewModel

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
        library.responses["get_episodes_wanted"] = buildJsonObject {
            put(
                "data",
                JsonArray(
                    listOf(
                        buildJsonObject {
                            put("episodeId", 31)
                            put("seriesId", 12)
                            put("seriesTitle", "Wand Name")
                            put("seasonNumber", 1)
                            put("episodeNumber", 1)
                            put("title", "Pilot")
                            put(
                                "missing_language",
                                JsonArray(
                                    listOf(
                                        buildJsonObject {
                                            put("code2", "de")
                                            put("name", "German")
                                            put("hi", true)
                                        },
                                        buildJsonObject { put("code2", "fr"); put("name", "French") },
                                    ),
                                ),
                            )
                        },
                    ),
                ),
            )
        }
        library.responses["get_movies_wanted"] = buildJsonObject {
            put(
                "data",
                JsonArray(
                    listOf(
                        buildJsonObject {
                            put("radarrId", 42)
                            put("title", "Arrival")
                            put(
                                "missing_language",
                                JsonArray(listOf(buildJsonObject { put("code2", "es"); put("name", "Spanish") })),
                            )
                        },
                    ),
                ),
            )
        }
        system.responses["get_languages"] = buildJsonObject {
            put(
                "data",
                JsonArray(
                    listOf(
                        buildJsonObject { put("code2", "de"); put("name", "German") },
                        buildJsonObject { put("code2", "fr"); put("name", "French") },
                    ),
                ),
            )
        }
    }

    private fun buildVm(): WantedSubtitlesViewModel = WantedSubtitlesViewModel(
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
    fun `initial load lists wanted episodes and movies with language chips`() = runVm {
        assertTrue(vm.lce.value is LceState.Content)
        val state = vm.state.value
        assertEquals(1, state.episodes.size)
        assertEquals(1, state.movies.size)
        assertEquals(2, state.missingLanguageCount(state.episodes.single()))
        assertEquals(2, state.languages.size)
    }

    private fun WantedSubtitlesUiState.missingLanguageCount(row: dev.appolon.homelab.arr.feature.bazarr.episodes.model.BazarrWantedEpisode): Int =
        row.missingLanguages.size

    @Test
    fun `language chip narrows both tabs`() = runVm {
        vm.setLanguageFilter("fr")
        assertEquals(listOf(31), vm.state.value.presentedEpisodes.map { it.episodeId })
        vm.setTab(WantedTab.MOVIES)
        assertTrue(vm.state.value.presentedMovies.isEmpty())
        vm.setLanguageFilter("fr")
        vm.setLanguageFilter("es")
        assertEquals(listOf(42), vm.state.value.presentedMovies.map { it.radarrId })
    }

    @Test
    fun `searching a wanted episode requests every missing language`() = runVm {
        val row = vm.state.value.episodes.single()

        vm.searchEpisode(row)

        val downloads = library.calls.filter { it.operation == "patch_episodes_subtitles" }
        assertEquals(2, downloads.size)
        assertEquals(
            listOf("True", "False"),
            downloads.map { it.arguments["hi"] },
        )
        assertEquals(listOf("de", "fr"), downloads.map { it.arguments["language"] })
        assertEquals(SubtitlesMessage.DownloadSynced, vm.state.value.message)
    }

    @Test
    fun `offline search queues per language and flags the row`() = runVm {
        library.failure = { call -> if (call.operation == "patch_episodes_subtitles") IOException() else null }
        val row = vm.state.value.episodes.single()

        vm.searchEpisode(row)

        assertEquals(SubtitlesMessage.DownloadQueued, vm.state.value.message)
        assertEquals(setOf(31), vm.state.value.queuedEpisodeIds)
    }

    @Test
    fun `searching a wanted movie targets its radarr id`() = runVm {
        val row = vm.state.value.movies.single()

        vm.searchMovie(row)

        val downloads = library.calls.filter { it.operation == "patch_movies_subtitles" }
        assertEquals(1, downloads.size)
        assertEquals(42, downloads.single().arguments["radarrid"])
        assertEquals("es", downloads.single().arguments["language"])
    }
}
