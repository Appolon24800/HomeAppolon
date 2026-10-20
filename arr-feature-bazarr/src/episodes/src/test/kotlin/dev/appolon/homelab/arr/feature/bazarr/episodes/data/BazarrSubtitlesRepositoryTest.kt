package dev.appolon.homelab.arr.feature.bazarr.episodes.data

import dev.appolon.homelab.arr.api.bazarr.FakeBazarrLibrary
import dev.appolon.homelab.arr.api.bazarr.FakeBazarrSubtitles
import dev.appolon.homelab.arr.api.bazarr.FakeBazarrSystem
import dev.appolon.homelab.arr.core.ArrError
import dev.appolon.homelab.arr.core.ArrResult
import dev.appolon.homelab.arr.feature.bazarr.episodes.FakeActionQueue
import dev.appolon.homelab.arr.feature.bazarr.episodes.FakeEntityStore
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
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
class BazarrSubtitlesRepositoryTest {

    private val main = UnconfinedTestDispatcher()
    private lateinit var library: FakeBazarrLibrary
    private lateinit var subtitles: FakeBazarrSubtitles
    private lateinit var system: FakeBazarrSystem
    private lateinit var store: FakeEntityStore
    private lateinit var queue: FakeActionQueue
    private var clock: Long = 1_000_000L

    @Before
    fun setUp() {
        Dispatchers.setMain(main)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun repo() = newRepository(library, subtitles, system, store, queue, now = { clock }, io = main)

    private fun seedEpisodes() {
        library.responses["get_series"] = buildJsonObject {
            put("data", JsonArray(listOf(buildJsonObject { put("title", "Wand Name") })))
        }
        library.responses["get_episodes"] = buildJsonObject {
            put(
                "data",
                JsonArray(
                    listOf(
                        episodeRow(
                            episodeId = 31,
                            seriesId = 12,
                            season = 1,
                            episode = 1,
                            title = "Pilot",
                            subtitles = listOf(trackRow("en", hi = true, score = 90.0)),
                            missing = listOf(languageRow("de", "German", hi = true)),
                        ),
                        episodeRow(episodeId = 32, seriesId = 12, season = 1, episode = 2, title = "Rooster"),
                    ),
                ),
            )
        }
    }

    @Test
    fun `refreshEpisodes stores rows and observe filters by series`() = runTest(main) {
        library = FakeBazarrLibrary(); subtitles = FakeBazarrSubtitles(); system = FakeBazarrSystem()
        store = FakeEntityStore(); queue = FakeActionQueue()
        seedEpisodes()
        val repository = repo()

        val outcome = repository.refreshEpisodes(12)

        assertTrue(outcome is ArrResult.Ok)
        val rows = repository.observeEpisodes(12).first()
        assertEquals(listOf(31, 32), rows.map { it.episodeId })
        assertEquals("S01E01", rows.first().code)
        assertFalse(repository.episodesStale(12))
    }

    @Test
    fun `episodesStale flips once the ttl passes`() = runTest(main) {
        library = FakeBazarrLibrary(); subtitles = FakeBazarrSubtitles(); system = FakeBazarrSystem()
        store = FakeEntityStore(); queue = FakeActionQueue()
        seedEpisodes()
        val repository = repo()
        repository.refreshEpisodes(12)

        clock += BazarrSubtitlesRepository.LIST_TTL_MILLIS + 1

        assertTrue(repository.episodesStale(12))
    }

    @Test
    fun `seriesTitle caches within the ttl and refetches after it`() = runTest(main) {
        library = FakeBazarrLibrary(); subtitles = FakeBazarrSubtitles(); system = FakeBazarrSystem()
        store = FakeEntityStore(); queue = FakeActionQueue()
        seedEpisodes()
        val repository = repo()

        assertEquals("Wand Name", repository.seriesTitle(12))
        clock += 1
        assertEquals("Wand Name", repository.seriesTitle(12))
        val callsAfterCache = library.calls.count { it.operation == "get_series" }
        assertEquals(1, callsAfterCache)

        clock += BazarrSubtitlesRepository.LIST_TTL_MILLIS + 1
        repository.seriesTitle(12)
        assertEquals(2, library.calls.count { it.operation == "get_series" })
    }

    @Test
    fun `languages are cache-first within their ttl`() = runTest(main) {
        library = FakeBazarrLibrary(); subtitles = FakeBazarrSubtitles(); system = FakeBazarrSystem()
        store = FakeEntityStore(); queue = FakeActionQueue()
        system.responses["get_languages"] = buildJsonObject {
            put("data", JsonArray(listOf(languageRow("en", "English"), languageRow("de", "German"))))
        }
        val repository = repo()

        val first = repository.languages()
        assertTrue(first is ArrResult.Ok)
        assertEquals(listOf("en", "de"), (first as ArrResult.Ok).value.map { it.code2 })

        repository.languages()
        assertEquals(1, system.calls.count { it.operation == "get_languages" })

        clock += BazarrSubtitlesRepository.LANGUAGE_TTL_MILLIS + 1
        repository.languages()
        assertEquals(2, system.calls.count { it.operation == "get_languages" })
    }

    @Test
    fun `download subtitle queues when the network fails and flags the episode`() = runTest(main) {
        library = FakeBazarrLibrary(); subtitles = FakeBazarrSubtitles(); system = FakeBazarrSystem()
        store = FakeEntityStore(); queue = FakeActionQueue()
        seedEpisodes()
        library.failure = { call -> if (call.operation == "patch_episodes_subtitles") IOException() else null }
        val repository = repo()
        repository.refreshEpisodes(12)

        val outcome = repository.downloadEpisodeSubtitle(12, 31, "de", forced = false, hearingImpaired = true)

        assertTrue((outcome as ArrResult.Ok).value is WriteOutcome.Queued)
        assertEquals(1, queue.pending().size)
        val action = queue.pending().single()
        assertEquals("PATCH", action.method)
        assertTrue(action.path.contains("episodeid=31"))
        assertTrue(action.path.contains("language=de"))
        assertTrue(action.path.contains("hi=True"))
        assertEquals(setOf(31), repository.queuedEpisodeIds().first())
    }

    @Test
    fun `download subtitle surfaces http failures without queueing`() = runTest(main) {
        library = FakeBazarrLibrary(); subtitles = FakeBazarrSubtitles(); system = FakeBazarrSystem()
        store = FakeEntityStore(); queue = FakeActionQueue()
        library.errorStatus = { call -> if (call.operation == "patch_episodes_subtitles") 500 else null }
        val repository = repo()

        val outcome = repository.downloadEpisodeSubtitle(12, 31, "de", forced = false, hearingImpaired = false)

        assertTrue(outcome is ArrResult.Err)
        assertTrue((outcome as ArrResult.Err).error is ArrError.Http)
        assertTrue(queue.pending().isEmpty())
    }

    @Test
    fun `delete subtitle applies optimistically on the queued path`() = runTest(main) {
        library = FakeBazarrLibrary(); subtitles = FakeBazarrSubtitles(); system = FakeBazarrSystem()
        store = FakeEntityStore(); queue = FakeActionQueue()
        seedEpisodes()
        library.failure = { call -> if (call.operation == "delete_episodes_subtitles") IOException() else null }
        val repository = repo()
        repository.refreshEpisodes(12)
        val before = repository.observeEpisodes(12).first().single { it.episodeId == 31 }
        assertEquals(1, before.subtitles.size)

        val outcome = repository.deleteEpisodeSubtitle(
            seriesId = 12,
            episodeId = 31,
            languageCode2 = "en",
            forced = false,
            hearingImpaired = true,
            path = "/tv/show.en.srt",
        )

        assertTrue((outcome as ArrResult.Ok).value is WriteOutcome.Queued)
        val after = repository.observeEpisodes(12).first().single { it.episodeId == 31 }
        assertTrue(after.subtitles.isEmpty())
        assertTrue(queue.pending().single().path.contains("path="))
    }

    @Test
    fun `delete subtitle online removes the track and stays synced`() = runTest(main) {
        library = FakeBazarrLibrary(); subtitles = FakeBazarrSubtitles(); system = FakeBazarrSystem()
        store = FakeEntityStore(); queue = FakeActionQueue()
        seedEpisodes()
        val repository = repo()
        repository.refreshEpisodes(12)

        val outcome = repository.deleteEpisodeSubtitle(
            seriesId = 12,
            episodeId = 31,
            languageCode2 = "en",
            forced = false,
            hearingImpaired = true,
            path = "/tv/show.en.srt",
        )

        assertTrue((outcome as ArrResult.Ok).value is WriteOutcome.Synced)
        assertTrue(queue.pending().isEmpty())
        val after = repository.observeEpisodes(12).first().single { it.episodeId == 31 }
        assertTrue(after.subtitles.isEmpty())
    }

    @Test
    fun `manual search parses provider hits and manual download posts all arguments`() = runTest(main) {
        library = FakeBazarrLibrary(); subtitles = FakeBazarrSubtitles(); system = FakeBazarrSystem()
        store = FakeEntityStore(); queue = FakeActionQueue()
        subtitles.responses["get_provider_episodes"] = buildJsonObject {
            put("data", JsonArray(listOf(providerRow("opensubtitles", "os-9", score = 95.0))))
        }
        val repository = repo()

        val results = repository.manualSearchEpisode(31)

        assertTrue(results is ArrResult.Ok)
        assertEquals("os-9", (results as ArrResult.Ok).value.single().subtitleId)

        val outcome = repository.downloadManualEpisode(
            seriesId = 12,
            episodeId = 31,
            result = (results as ArrResult.Ok).value.single(),
        )

        assertTrue((outcome as ArrResult.Ok).value is WriteOutcome.Synced)
        val call = subtitles.calls.single { it.operationId == "post_provider_episodes" }
        assertEquals(31, call.arguments["episodeid"])
        assertEquals("opensubtitles", call.arguments["provider"])
        assertEquals("os-9", call.arguments["subtitle"])
    }

    @Test
    fun `manual download queues on network failure`() = runTest(main) {
        library = FakeBazarrLibrary(); subtitles = FakeBazarrSubtitles(); system = FakeBazarrSystem()
        store = FakeEntityStore(); queue = FakeActionQueue()
        subtitles.errorInjection = { op -> if (op == "post_provider_episodes") IOException() else null }
        val repository = repo()

        val outcome = repository.downloadManualEpisode(
            seriesId = 12,
            episodeId = 31,
            result = dev.appolon.homelab.arr.feature.bazarr.episodes.model.BazarrProviderResult(
                provider = "opensubtitles",
                subtitleId = "os-9",
            ),
        )

        assertTrue((outcome as ArrResult.Ok).value is WriteOutcome.Queued)
        assertTrue(queue.pending().single().path.contains("provider=opensubtitles"))
        assertTrue(queue.pending().single().path.contains("subtitle=os-9"))
    }

    @Test
    fun `upload is online only and never queues`() = runTest(main) {
        library = FakeBazarrLibrary(); subtitles = FakeBazarrSubtitles(); system = FakeBazarrSystem()
        store = FakeEntityStore(); queue = FakeActionQueue()
        library.failure = { call -> if (call.operation == "post_episodes_subtitles") IOException() else null }
        val repository = repo()

        val failed = repository.uploadEpisodeSubtitle(
            seriesId = 12, episodeId = 31, languageCode2 = "en",
            forced = false, hearingImpaired = false, fileName = "sub.srt", bytes = byteArrayOf(1),
        )

        assertTrue(failed is ArrResult.Err)
        assertTrue(queue.pending().isEmpty())

        library.failure = { null }
        val ok = repository.uploadEpisodeSubtitle(
            seriesId = 12, episodeId = 31, languageCode2 = "en",
            forced = false, hearingImpaired = false, fileName = "sub.srt", bytes = byteArrayOf(1),
        )

        assertTrue((ok as ArrResult.Ok).value is WriteOutcome.Synced)
        val uploadCalls = library.calls.filter { it.operation == "post_episodes_subtitles" }
        assertEquals(2, uploadCalls.size)
        assertTrue(uploadCalls.last().arguments["file"] is okhttp3.MultipartBody.Part)
    }

    @Test
    fun `movie delete queues optimistically and flags the movie id`() = runTest(main) {
        library = FakeBazarrLibrary(); subtitles = FakeBazarrSubtitles(); system = FakeBazarrSystem()
        store = FakeEntityStore(); queue = FakeActionQueue()
        library.responses["get_movies"] = buildJsonObject {
            put(
                "data",
                JsonArray(
                    listOf(movieRow(42, "Arrival", 2016, subtitles = listOf(trackRow("en", path = "/movies/arrival.en.srt")))),
                ),
            )
        }
        library.failure = { call -> if (call.operation == "delete_movies_subtitles") IOException() else null }
        val repository = repo()
        repository.refreshMovie(42)

        val outcome = repository.deleteMovieSubtitle(
            radarrId = 42, languageCode2 = "en", forced = false,
            hearingImpaired = false, path = "/movies/arrival.en.srt",
        )

        assertTrue((outcome as ArrResult.Ok).value is WriteOutcome.Queued)
        val movie = repository.observeMovie(42).first()
        assertTrue(movie!!.subtitles.isEmpty())
        assertEquals(setOf(42), repository.queuedMovieIds().first())
    }

    @Test
    fun `wanted and history refreshes store merged feeds`() = runTest(main) {
        library = FakeBazarrLibrary(); subtitles = FakeBazarrSubtitles(); system = FakeBazarrSystem()
        store = FakeEntityStore(); queue = FakeActionQueue()
        library.responses["get_episodes_wanted"] = buildJsonObject {
            put("data", JsonArray(listOf(buildJsonObject {
                put("episodeId", 31)
                put("seriesId", 12)
                put("seriesTitle", "Wand Name")
                put("seasonNumber", 1)
                put("episodeNumber", 1)
                put("title", "Pilot")
                put("missing_language", JsonArray(listOf(languageRow("de", "German"))))
            })))
        }
        library.responses["get_movies_wanted"] = buildJsonObject {
            put("data", JsonArray(listOf(buildJsonObject {
                put("radarrId", 42)
                put("title", "Arrival")
                put("missing_language", JsonArray(listOf(languageRow("es", "Spanish"))))
            })))
        }
        library.responses["get_episodes_history"] = buildJsonObject {
            put("data", JsonArray(listOf(historyRow("downloaded", 1_790_000_000, episodeId = 31))))
        }
        library.responses["get_movies_history"] = buildJsonObject {
            put("data", JsonArray(listOf(historyRow("uploaded", 1_790_000_100, radarrId = 42))))
        }
        val repository = repo()

        assertTrue(repository.refreshWantedEpisodes() is ArrResult.Ok)
        assertTrue(repository.refreshWantedMovies() is ArrResult.Ok)
        assertTrue(repository.refreshHistory() is ArrResult.Ok)

        val wantedEpisodes = repository.observeWantedEpisodes().first()
        val wantedMovies = repository.observeWantedMovies().first()
        val history = repository.observeHistory().first()

        assertEquals(1, wantedEpisodes.size)
        assertEquals("de", wantedEpisodes.single().missingLanguages.single().code2)
        assertEquals(1, wantedMovies.size)
        assertEquals(2, history.size)
        // Newest first: the movie event (100 seconds later) leads.
        assertEquals(42, history.first().radarrId)
    }

    @Test
    fun `subtitle contents ride the passthrough payload`() = runTest(main) {
        library = FakeBazarrLibrary(); subtitles = FakeBazarrSubtitles(); system = FakeBazarrSystem()
        store = FakeEntityStore(); queue = FakeActionQueue()
        subtitles.responses["get_subtitle_name_contents"] = buildJsonObject {
            put("data", "1\n00:00:01,000 --> 00:00:02,000\nHello")
        }
        val repository = repo()

        val contents = repository.subtitleContents("/tv/2x3.en.srt")

        assertTrue((contents as ArrResult.Ok).value.startsWith("1\n"))
    }
}
