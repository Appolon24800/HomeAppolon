package dev.appolon.homelab.arr.feature.bazarr.wanted.data

import dev.appolon.homelab.arr.api.bazarr.FakeBazarrLibrary
import dev.appolon.homelab.arr.api.bazarr.FakeBazarrSystem
import dev.appolon.homelab.arr.core.ArrResult
import dev.appolon.homelab.arr.feature.bazarr.episodes.FakeActionQueue
import dev.appolon.homelab.arr.feature.bazarr.episodes.FakeEntityStore
import dev.appolon.homelab.arr.feature.bazarr.wanted.dataPayload
import dev.appolon.homelab.arr.feature.bazarr.wanted.missingLanguageRow
import dev.appolon.homelab.arr.feature.bazarr.wanted.newWantedRepository
import dev.appolon.homelab.arr.feature.bazarr.wanted.wantedEpisodeRow
import dev.appolon.homelab.arr.feature.bazarr.wanted.wantedMovieRow
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BazarrWantedRepositoryTest {

    private val main = UnconfinedTestDispatcher()
    private lateinit var library: FakeBazarrLibrary
    private lateinit var system: FakeBazarrSystem
    private lateinit var store: FakeEntityStore
    private lateinit var queue: FakeActionQueue

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
        store = FakeEntityStore()
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
                wantedEpisodeRow(
                    episodeId = 33,
                    seriesId = 13,
                    season = 2,
                    episode = 1,
                    missing = listOf(missingLanguageRow("de", "German")),
                ),
            ),
        )
        library.responses["get_movies_wanted"] = dataPayload(
            listOf(wantedMovieRow(radarrId = 42, missing = listOf(missingLanguageRow("es", "Spanish")))),
        )
        library.responses["get_badges"] = buildJsonObject {
            put("episodes", 7)
            put("movies", 3)
        }
        system.responses["get_languages"] = dataPayload(
            listOf(
                buildJsonObject { put("code2", "de"); put("name", "German") },
                buildJsonObject { put("code2", "fr"); put("name", "French") },
            ),
        )
    }

    @Test
    fun `refresh caches wanted episodes sorted and replaces stale rows`() = runTest(main) {
        seed()
        val repository = newWantedRepository(library, system, store, queue, io = main)
        store.put(
            dev.appolon.homelab.arr.core.ServerId("s1"),
            BazarrWantedRepository.WANTED_EPISODE_TYPE,
            "999",
            dev.appolon.homelab.arr.feature.bazarr.episodes.model.BazarrWantedEpisode(episodeId = 999),
            dev.appolon.homelab.arr.feature.bazarr.episodes.model.BazarrWantedEpisode.serializer(),
        )

        assertTrue(repository.refreshWantedEpisodes() is ArrResult.Ok)

        val rows = repository.observeWantedEpisodes().first()
        assertEquals(listOf(32, 31, 33), rows.map { it.episodeId })
        assertEquals(3, rows.size)
    }

    @Test
    fun `series missing counts derive from the wanted cache`() = runTest(main) {
        seed()
        val repository = newWantedRepository(library, system, store, queue, io = main)

        assertTrue(repository.refreshWantedEpisodes() is ArrResult.Ok)

        assertEquals(mapOf(12 to 2, 13 to 1), repository.observeSeriesMissingCounts().first())
    }

    @Test
    fun `badges totals parse from the badges endpoint`() = runTest(main) {
        seed()
        val repository = newWantedRepository(library, system, store, queue, io = main)

        assertTrue(repository.refreshBadges() is ArrResult.Ok)

        val badges = repository.observeBadges().first()
        assertEquals(7, badges?.episodes)
        assertEquals(3, badges?.movies)
    }

    @Test
    fun `languages are fetched once inside the ttl`() = runTest(main) {
        seed()
        val repository = newWantedRepository(library, system, store, queue, now = { 1_000L }, io = main)

        repository.languages()
        repository.languages()

        assertEquals(1, system.calls.count { it.operation == "get_languages" })
    }

    @Test
    fun `episode search patches one call per missing language`() = runTest(main) {
        seed()
        val repository = newWantedRepository(library, system, store, queue, io = main)
        assertTrue(repository.refreshWantedEpisodes() is ArrResult.Ok)
        val row = repository.observeWantedEpisodes().first().first { it.episodeId == 31 }

        val outcome = repository.downloadEpisodeSubtitle(
            seriesId = row.seriesId,
            episodeId = row.episodeId,
            languageCode2 = "de",
            forced = false,
            hearingImpaired = true,
        )

        assertTrue((outcome as ArrResult.Ok).value is WantedWriteOutcome.Synced)
        val call = library.calls.single { it.operation == "patch_episodes_subtitles" }
        assertEquals(31, call.arguments["episodeid"])
        assertEquals(12, call.arguments["seriesid"])
        assertEquals("de", call.arguments["language"])
        assertEquals("True", call.arguments["hi"])
    }

    @Test
    fun `offline episode search queues a replayable action and flags the row`() = runTest(main) {
        seed()
        library.failure = { call -> if (call.operation == "patch_episodes_subtitles") IOException() else null }
        val repository = newWantedRepository(library, system, store, queue, io = main)

        val outcome = repository.downloadEpisodeSubtitle(12, 31, "de", forced = false, hearingImpaired = false)

        assertTrue((outcome as ArrResult.Ok).value is WantedWriteOutcome.Queued)
        val action = queue.pending().single()
        assertEquals("PATCH", action.method)
        assertTrue(action.path.contains("episodeid=31"))
        assertTrue(action.path.contains("language=de"))
        assertEquals(setOf(31), repository.queuedEpisodeIds().first())
    }

    @Test
    fun `offline movie search queues with the radarr id in the path`() = runTest(main) {
        seed()
        library.failure = { call -> if (call.operation == "patch_movies_subtitles") IOException() else null }
        val repository = newWantedRepository(library, system, store, queue, io = main)

        val outcome = repository.downloadMovieSubtitle(42, "es", forced = true, hearingImpaired = false)

        assertTrue((outcome as ArrResult.Ok).value is WantedWriteOutcome.Queued)
        assertTrue(queue.pending().single().path.contains("radarrid=42"))
        assertTrue(queue.pending().single().path.contains("forced=True"))
        assertEquals(setOf(42), repository.queuedMovieIds().first())
    }
}
