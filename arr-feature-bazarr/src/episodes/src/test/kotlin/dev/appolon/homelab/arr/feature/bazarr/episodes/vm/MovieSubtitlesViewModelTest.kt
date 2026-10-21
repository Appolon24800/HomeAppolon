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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MovieSubtitlesViewModelTest {

    private val main = UnconfinedTestDispatcher()
    private lateinit var library: FakeBazarrLibrary
    private lateinit var subtitles: FakeBazarrSubtitles
    private lateinit var system: FakeBazarrSystem
    private lateinit var vm: MovieSubtitlesViewModel

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
        library.responses["get_movies"] = buildJsonObject {
            put(
                "data",
                JsonArray(
                    listOf(
                        buildJsonObject {
                            put("radarrId", 42)
                            put("title", "Arrival")
                            put("year", 2016)
                            put(
                                "subtitles",
                                JsonArray(
                                    listOf(
                                        buildJsonObject {
                                            put("path", "/movies/arrival.en.srt")
                                            put("language", "en")
                                            put("score", 100.0)
                                        },
                                    ),
                                ),
                            )
                            put(
                                "missing_language",
                                JsonArray(
                                    listOf(
                                        buildJsonObject {
                                            put("code2", "es")
                                            put("name", "Spanish")
                                        },
                                    ),
                                ),
                            )
                        },
                    ),
                ),
            )
        }
        system.responses["get_languages"] = buildJsonObject {
            put("data", JsonArray(listOf(buildJsonObject { put("code2", "es"); put("name", "Spanish") })))
        }
    }

    private fun buildVm(): MovieSubtitlesViewModel = MovieSubtitlesViewModel(
        radarrId = 42,
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
    fun `initial load renders the movie breakdown`() = runVm {
        val state = vm.state.value
        assertTrue(vm.lce.value is LceState.Content)
        assertEquals("Arrival", state.title)
        assertEquals(2016, state.year)
        assertEquals(1, state.subtitles.size)
        assertEquals(1, state.missingLanguages.size)
        assertEquals(listOf("es"), state.languages.map { it.code2 })
    }

    @Test
    fun `downloading a missing language syncs`() = runVm {
        vm.downloadSubtitle(vm.state.value.missingLanguages.single())

        assertEquals(SubtitlesMessage.DownloadSynced, vm.state.value.message)
        val call = library.calls.single { it.operation == "patch_movies_subtitles" }
        assertEquals(42, call.arguments["radarrid"])
        assertEquals("es", call.arguments["language"])
    }

    @Test
    fun `offline delete removes the track optimistically and flags queued`() = runVm {
        library.failure = { call -> if (call.operation == "delete_movies_subtitles") IOException() else null }

        vm.deleteSubtitle(vm.state.value.subtitles.single())

        assertEquals(SubtitlesMessage.DeleteQueued, vm.state.value.message)
        assertTrue(vm.state.value.queued)
        assertTrue(vm.state.value.subtitles.isEmpty())
    }

    @Test
    fun `manual search and manual download target the movie`() = runVm {
        subtitles.responses["get_provider_movies"] = buildJsonObject {
            put(
                "data",
                JsonArray(
                    listOf(
                        buildJsonObject {
                            put("provider", "subtitulamos")
                            put("subtitle", "sub-1")
                            put("language", "es")
                            put("score", 80.0)
                        },
                    ),
                ),
            )
        }

        vm.openManualSearch()

        val search = vm.state.value.manualSearch
        assertEquals(1, search?.results?.size)
        assertFalse(search?.loading == true)

        vm.downloadManualResult(search!!.results.single())

        val post = subtitles.calls.single { it.operationId == "post_provider_movies" }
        assertEquals(42, post.arguments["radarrid"])
        assertEquals("sub-1", post.arguments["subtitle"])
        assertEquals(SubtitlesMessage.DownloadSynced, vm.state.value.message)
    }

    @Test
    fun `upload sheet submits for the movie`() = runVm {
        vm.openUpload()
        vm.updateUpload { it.copy(languageCode2 = "es", fileName = "arrival.es.srt") }
        vm.submitUpload(byteArrayOf(9))

        assertEquals(SubtitlesMessage.UploadSynced, vm.state.value.message)
        val call = library.calls.single { it.operation == "post_movies_subtitles" }
        assertEquals("es", call.arguments["language"])
    }
}
