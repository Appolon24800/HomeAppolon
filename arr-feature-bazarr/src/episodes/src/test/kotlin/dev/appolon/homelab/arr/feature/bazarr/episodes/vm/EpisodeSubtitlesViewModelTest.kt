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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class EpisodeSubtitlesViewModelTest {

    private val main = UnconfinedTestDispatcher()
    private lateinit var library: FakeBazarrLibrary
    private lateinit var subtitles: FakeBazarrSubtitles
    private lateinit var system: FakeBazarrSystem
    private lateinit var vm: EpisodeSubtitlesViewModel

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
        library.responses["get_series"] = buildJsonObject {
            put("data", JsonArray(listOf(buildJsonObject { put("title", "Wand Name") })))
        }
        library.responses["get_episodes"] = buildJsonObject {
            put(
                "data",
                JsonArray(
                    listOf(
                        buildJsonObject {
                            put("episodeId", 31)
                            put("seriesId", 12)
                            put("seasonNumber", 1)
                            put("episodeNumber", 1)
                            put("title", "Pilot")
                            put(
                                "subtitles",
                                JsonArray(
                                    listOf(
                                        buildJsonObject {
                                            put("path", "/tv/1x1.en.srt")
                                            put("language", "en")
                                            put("score", 90.0)
                                            put("hearing_impaired", true)
                                        },
                                    ),
                                ),
                            )
                            put(
                                "missing_language",
                                JsonArray(
                                    listOf(
                                        buildJsonObject {
                                            put("code2", "de")
                                            put("name", "German")
                                            put("hi", true)
                                        },
                                    ),
                                ),
                            )
                        },
                        buildJsonObject {
                            put("episodeId", 32)
                            put("seriesId", 12)
                            put("seasonNumber", 1)
                            put("episodeNumber", 2)
                            put("title", "Rooster")
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
                        buildJsonObject { put("code2", "en"); put("name", "English") },
                        buildJsonObject { put("code2", "de"); put("name", "German") },
                    ),
                ),
            )
        }
    }

    private fun buildVm(): EpisodeSubtitlesViewModel {
        val repository = newRepository(
            library, subtitles, system,
            store = FakeEntityStore(), queue = FakeActionQueue(),
            io = main,
        )
        return EpisodeSubtitlesViewModel(seriesId = 12, repository = repository)
    }

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
    fun `initial load renders content with series title and language chips`() = runVm {
        val lce = vm.lce.value
        assertTrue(lce is LceState.Content)
        val state = vm.state.value
        assertEquals("Wand Name", state.seriesTitle)
        assertEquals(2, state.episodes.size)
        assertEquals(listOf("en", "de"), state.languages.map { it.code2 })
        assertFalse((lce as LceState.Content).refreshing)
    }

    @Test
    fun `language chip filters episodes to that language and toggles off`() = runVm {
        vm.setLanguageFilter("de")
        assertEquals(listOf(31), vm.state.value.presented.map { it.episodeId })
        vm.setLanguageFilter("de")
        assertEquals(2, vm.state.value.presented.size)
    }

    @Test
    fun `missing only keeps episodes with missing languages`() = runVm {
        vm.toggleMissingOnly()
        assertEquals(listOf(31), vm.state.value.presented.map { it.episodeId })
    }

    @Test
    fun `downloading a missing language syncs and refreshes the row`() = runVm {
        val episode = vm.state.value.episodes.single { it.episodeId == 31 }
        val german = episode.missingLanguages.single()

        vm.downloadSubtitle(episode, german)

        assertEquals(SubtitlesMessage.DownloadSynced, vm.state.value.message)
        // The refresh after the synced download counts one extra episodes fetch.
        assertTrue(
            library.calls.count { it.operation == "get_episodes" } >= 2,
        )
    }

    @Test
    fun `offline download queues and flags the episode row`() = runVm {
        library.failure = { call -> if (call.operation == "patch_episodes_subtitles") IOException() else null }
        val episode = vm.state.value.episodes.single { it.episodeId == 31 }

        vm.downloadSubtitle(episode, episode.missingLanguages.single())

        assertEquals(SubtitlesMessage.DownloadQueued, vm.state.value.message)
        assertEquals(setOf(31), vm.state.value.queuedEpisodeIds)
    }

    @Test
    fun `offline delete removes the track optimistically and queues`() = runVm {
        library.failure = { call -> if (call.operation == "delete_episodes_subtitles") IOException() else null }
        val episode = vm.state.value.episodes.single { it.episodeId == 31 }
        val track = episode.subtitles.single()

        vm.deleteSubtitle(episode, track)

        assertEquals(SubtitlesMessage.DeleteQueued, vm.state.value.message)
        val updated = vm.state.value.episodes.single { it.episodeId == 31 }
        assertTrue(updated.subtitles.isEmpty())
    }

    @Test
    fun `manual search loads provider hits and download posts the chosen hit`() = runVm {
        subtitles.responses["get_provider_episodes"] = buildJsonObject {
            put(
                "data",
                JsonArray(
                    listOf(
                        buildJsonObject {
                            put("provider", "opensubtitles")
                            put("subtitle", "os-9")
                            put("language", "en")
                            put("score", 95.0)
                        },
                    ),
                ),
            )
        }
        val episode = vm.state.value.episodes.single { it.episodeId == 32 }

        vm.openManualSearch(episode)

        val search = vm.state.value.manualSearch
        assertNotNull(search)
        assertFalse(search!!.loading)
        assertEquals(1, search.results.size)

        vm.downloadManualResult(search.results.single())

        val post = subtitles.calls.single { it.operationId == "post_provider_episodes" }
        assertEquals(32, post.arguments["episodeid"])
        assertEquals("os-9", post.arguments["subtitle"])
        assertEquals(SubtitlesMessage.DownloadSynced, vm.state.value.message)
        assertNull(vm.state.value.manualSearch)
    }

    @Test
    fun `viewer loads subtitle contents and closes`() = runVm {
        subtitles.responses["get_subtitle_name_contents"] = buildJsonObject {
            put("data", "1\n00:00:01,000 --> 00:00:02,000\nHello")
        }
        val episode = vm.state.value.episodes.single { it.episodeId == 31 }

        vm.openViewer(episode.subtitles.single(), episode.code)

        assertEquals("1\n00:00:01,000 --> 00:00:02,000\nHello", vm.state.value.viewer?.content)
        vm.closeViewer()
        assertNull(vm.state.value.viewer)
    }

    @Test
    fun `upload sheet submits the picked file`() = runVm {
        val episode = vm.state.value.episodes.single { it.episodeId == 31 }

        vm.openUpload(episode)
        vm.updateUpload { it.copy(languageCode2 = "de", fileName = "pilot.de.srt") }
        vm.submitUpload(byteArrayOf(1, 2, 3))

        assertEquals(SubtitlesMessage.UploadSynced, vm.state.value.message)
        assertNull(vm.state.value.upload)
        val call = library.calls.single { it.operation == "post_episodes_subtitles" }
        assertEquals("de", call.arguments["language"])
    }
}
