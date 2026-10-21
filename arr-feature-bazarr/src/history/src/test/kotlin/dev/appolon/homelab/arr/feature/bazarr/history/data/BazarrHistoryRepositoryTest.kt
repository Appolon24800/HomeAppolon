package dev.appolon.homelab.arr.feature.bazarr.history.data

import dev.appolon.homelab.arr.api.bazarr.FakeBazarrLibrary
import dev.appolon.homelab.arr.api.bazarr.FakeBazarrSystem
import dev.appolon.homelab.arr.core.ArrResult
import dev.appolon.homelab.arr.feature.bazarr.episodes.FakeActionQueue
import dev.appolon.homelab.arr.feature.bazarr.episodes.FakeEntityStore
import dev.appolon.homelab.arr.feature.bazarr.history.blacklistRow
import dev.appolon.homelab.arr.feature.bazarr.history.historyDataPayload
import dev.appolon.homelab.arr.feature.bazarr.history.historyEventRow
import dev.appolon.homelab.arr.feature.bazarr.history.newHistoryRepository
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BazarrHistoryRepositoryTest {

    private val main = UnconfinedTestDispatcher()
    private val episodeRows = (0 until 5).map { index ->
        historyEventRow(
            action = "upgraded",
            timestamp = 1_700_000_000L + index,
            episodeId = 30 + index,
            title = "Episode ${index + 1}",
        )
    }
    private val movieRows = listOf(
        historyEventRow(action = "downloaded", timestamp = 1_700_000_100L, radarrId = 42, title = "Arrival"),
    )
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

    /** Scripts the fake to serve one window of the episode feed per start. */
    private fun seed(pageLength: Int = 2) {
        library = FakeBazarrLibrary()
        system = FakeBazarrSystem()
        store = FakeEntityStore()
        queue = FakeActionQueue()
        library.latency = { call ->
            when (call.operation) {
                "get_episodes_history" -> {
                    val start = call.arguments["start"] as? Int ?: 0
                    library.responses["get_episodes_history"] =
                        historyDataPayload(episodeRows.drop(start).take(pageLength))
                }

                "get_movies_history" -> {
                    val start = call.arguments["start"] as? Int ?: 0
                    library.responses["get_movies_history"] =
                        historyDataPayload(movieRows.drop(start).take(pageLength))
                }
            }
        }
        library.responses["get_episodes_blacklist"] = historyDataPayload(
            listOf(
                blacklistRow(provider = "opensubtitles", subsId = "abc", episodeId = 31),
                blacklistRow(provider = "subf2m", subsId = "def", episodeId = 32, language = "de"),
            ),
        )
        library.responses["get_movies_blacklist"] = historyDataPayload(
            listOf(blacklistRow(provider = "opensubtitles", subsId = "xyz", radarrId = 42)),
        )
    }

    @Test
    fun `refresh loads the first page of both feeds newest first`() = runTest(main) {
        seed()
        val repository = newHistoryRepository(library, system, store, queue, io = main, pageLength = 2)

        assertTrue(repository.refreshHistory() is ArrResult.Ok)

        val events = repository.observeHistory().first()
        assertEquals(3, events.size)
        assertEquals(1_700_000_100_000L, events.first().timestampMillis)
        assertTrue(!repository.historyExhausted())
        val historyCalls = library.calls.filter { it.operation == "get_episodes_history" }
        assertEquals(0, historyCalls.single().arguments["start"])
        assertEquals(2, historyCalls.single().arguments["length"])
    }

    @Test
    fun `load more appends pages until both feeds are exhausted`() = runTest(main) {
        seed()
        val repository = newHistoryRepository(library, system, store, queue, io = main, pageLength = 2)

        repository.refreshHistory()
        var appended = 0
        do {
            val result = repository.loadMoreHistory()
            assertTrue(result is ArrResult.Ok)
            appended += (result as ArrResult.Ok).value
        } while (!repository.historyExhausted())

        assertEquals(3, appended)
        assertEquals(6, repository.observeHistory().first().size)
        assertTrue(repository.loadMoreHistory().let { it is ArrResult.Ok && it.value == 0 })
    }

    @Test
    fun `refresh resets paging back to the first page`() = runTest(main) {
        seed()
        val repository = newHistoryRepository(library, system, store, queue, io = main, pageLength = 2)

        repository.refreshHistory()
        repository.loadMoreHistory()
        assertTrue(repository.refreshHistory() is ArrResult.Ok)

        assertEquals(3, repository.observeHistory().first().size)
        val starts = library.calls
            .filter { it.operation == "get_episodes_history" }
            .map { it.arguments["start"] }
        assertEquals(listOf(0, 2, 0), starts)
    }

    @Test
    fun `refresh blacklist caches both feeds`() = runTest(main) {
        seed()
        val repository = newHistoryRepository(library, system, store, queue, io = main)

        assertTrue(repository.refreshBlacklist() is ArrResult.Ok)

        assertEquals(2, repository.observeEpisodeBlacklist().first().size)
        assertEquals(1, repository.observeMovieBlacklist().first().size)
        val entry = repository.observeEpisodeBlacklist().first().first { it.subsId == "abc" }
        assertEquals("opensubtitles", entry.provider)
        assertEquals("en", entry.languageCode2)
        assertEquals("Wand Name S01E01 Pilot", entry.mediaLabel)
    }

    @Test
    fun `remove deletes the row on the server and in the cache`() = runTest(main) {
        seed()
        val repository = newHistoryRepository(library, system, store, queue, io = main)
        repository.refreshBlacklist()
        val entry = repository.observeEpisodeBlacklist().first().first { it.subsId == "abc" }

        val outcome = repository.removeEpisodeBlacklistEntry(entry)

        assertTrue((outcome as ArrResult.Ok).value is BlacklistWriteOutcome.Synced)
        val call = library.calls.single { it.operation == "delete_episodes_blacklist" }
        assertEquals("opensubtitles", call.arguments["provider"])
        assertEquals("abc", call.arguments["subs_id"])
        assertEquals(1, repository.observeEpisodeBlacklist().first().size)
    }

    @Test
    fun `offline remove queues and keeps the row flagged as queued`() = runTest(main) {
        seed()
        library.failure = { call -> if (call.operation == "delete_episodes_blacklist") IOException() else null }
        val repository = newHistoryRepository(library, system, store, queue, io = main)
        repository.refreshBlacklist()
        val entry = repository.observeEpisodeBlacklist().first().first { it.subsId == "abc" }

        val outcome = repository.removeEpisodeBlacklistEntry(entry)

        assertTrue((outcome as ArrResult.Ok).value is BlacklistWriteOutcome.Queued)
        assertEquals("opensubtitles|abc", repository.queuedBlacklistKeys().first().single())
        assertTrue(repository.queuedClearAll(isMovie = false).first() == false)
    }

    @Test
    fun `offline clear queues a replayable clear all and empties the cache`() = runTest(main) {
        seed()
        library.failure = { call -> if (call.operation == "delete_movies_blacklist") IOException() else null }
        val repository = newHistoryRepository(library, system, store, queue, io = main)
        repository.refreshBlacklist()

        val outcome = repository.clearMovieBlacklist()

        assertTrue((outcome as ArrResult.Ok).value is BlacklistWriteOutcome.Queued)
        val action = queue.pending().single()
        assertEquals("DELETE", action.method)
        assertEquals("movies/blacklist?all=True", action.path)
        assertEquals(0, repository.observeMovieBlacklist().first().size)
        assertTrue(repository.queuedClearAll(isMovie = true).first())
    }
}
