package dev.appolon.homelab.arr.feature.bazarr.series

import dev.appolon.homelab.arr.api.bazarr.FakeBazarrLibrary
import dev.appolon.homelab.arr.api.bazarr.FakeBazarrSystem
import dev.appolon.homelab.arr.core.ArrResult
import dev.appolon.homelab.arr.core.ServerId
import dev.appolon.homelab.arr.feature.bazarr.series.data.BazarrBadges
import dev.appolon.homelab.arr.feature.bazarr.series.data.BazarrLanguageProfile
import dev.appolon.homelab.arr.feature.bazarr.series.data.BazarrLibraryRepository
import dev.appolon.homelab.arr.feature.bazarr.series.data.BazarrMediaKind
import dev.appolon.homelab.arr.feature.bazarr.series.data.BazarrParsers
import dev.appolon.homelab.arr.feature.bazarr.series.data.BazarrSeries
import dev.appolon.homelab.arr.feature.bazarr.series.data.SubtitleStatus
import dev.appolon.homelab.arr.feature.bazarr.series.data.WriteOutcome
import java.io.IOException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class BazarrLibraryRepositoryTest {

    private val serverId = ServerId("s1")
    private lateinit var fake: FakeBazarrLibrary
    private lateinit var system: FakeBazarrSystem
    private lateinit var store: FakeEntityStore
    private lateinit var queue: FakeActionQueue
    private var clock: Long = 1_000_000L

    @Before
    fun setUp() {
        fake = FakeBazarrLibrary()
        system = FakeBazarrSystem()
        store = FakeEntityStore()
        queue = FakeActionQueue()
    }

    private fun repository() = BazarrLibraryRepository(
        serverId = serverId,
        store = store,
        seriesApi = fake,
        moviesApi = fake,
        episodesApi = fake,
        badgesApi = fake,
        episodesBlacklistApi = fake,
        moviesBlacklistApi = fake,
        languageProfilesApi = system,
        actionQueue = queue,
        now = { clock },
    )

    private fun seriesModel(
        id: Int,
        profileId: Int? = 1,
        fileCount: Int = 10,
        missing: Int = 0,
    ): BazarrSeries = BazarrParsers.parseSeries(
        BazarrTestJson.obj(
            "data" to BazarrTestJson.array(
                BazarrTestJson.seriesItem(id, "Show $id", profileId, fileCount, missing),
            ),
        ),
    ).single()

    private fun seedSeries(vararg items: BazarrSeries) {
        fake.responses["get_series"] = BazarrTestJson.obj(
            "data" to BazarrTestJson.array(
                *items.map { series ->
                    BazarrTestJson.seriesItem(
                        id = series.seriesId,
                        title = series.title,
                        profileId = series.profileId,
                        fileCount = series.episodeFileCount,
                        missingCount = series.episodeMissingCount,
                    )
                }.toTypedArray(),
            ),
            "total" to kotlinx.serialization.json.JsonPrimitive(items.size),
        )
    }

    private fun episodeCalls(): Int = fake.calls.count { it.operation == "get_episodes" }

    @Test
    fun refreshStoresSeriesAndReconcilesRemovedRows() = runTest {
        seedSeries(seriesModel(1), seriesModel(2))
        val repository = repository()

        val first = repository.refresh(BazarrMediaKind.SERIES)
        assertTrue(first is ArrResult.Ok)
        assertEquals(2, repository.cachedSeries().size)

        seedSeries(seriesModel(2))
        val second = repository.refresh(BazarrMediaKind.SERIES)
        assertTrue(second is ArrResult.Ok)
        val cached = repository.cachedSeries()
        assertEquals(1, cached.size)
        assertEquals(2, cached.single().seriesId)
    }

    @Test
    fun refreshMapsFailuresOntoArrError() = runTest {
        fake.errorStatus = { call -> if (call.operation == "get_series") 500 else null }
        val result = repository().refresh(BazarrMediaKind.SERIES)
        assertTrue(result is ArrResult.Err)
    }

    @Test
    fun refreshIfStaleSkipsNetworkInsideTheTtl() = runTest {
        seedSeries(seriesModel(1))
        val repository = repository()
        repository.refresh(BazarrMediaKind.SERIES)
        val callsAfterFirst = fake.calls.size

        clock += 60_000
        val skipped = repository.refreshIfStale(BazarrMediaKind.SERIES)
        assertTrue(skipped is ArrResult.Ok)
        assertEquals(callsAfterFirst, fake.calls.size)

        clock += 20 * 60_000
        val refreshed = repository.refreshIfStale(BazarrMediaKind.SERIES)
        assertTrue(refreshed is ArrResult.Ok)
        assertTrue(fake.calls.size > callsAfterFirst)
    }

    @Test
    fun refreshSupportStoresProfilesBlacklistsAndBadges() = runTest {
        system.responses["get_languages_profiles"] = BazarrTestJson.obj(
            "data" to BazarrTestJson.array(
                BazarrTestJson.profileItem(1, "Any", listOf("eng", "deu")),
            ),
        )
        fake.responses["get_episodes_blacklist"] = BazarrTestJson.obj(
            "data" to BazarrTestJson.array(
                BazarrTestJson.blacklistItem("argenteam", seriesId = 4),
            ),
        )
        fake.responses["get_movies_blacklist"] = BazarrTestJson.obj(
            "data" to BazarrTestJson.array(
                BazarrTestJson.blacklistItem("opensubtitles", radarrId = 9),
            ),
        )
        fake.responses["get_badges"] = BazarrTestJson.obj(
            "episodes" to kotlinx.serialization.json.JsonPrimitive(7),
            "movies" to kotlinx.serialization.json.JsonPrimitive(2),
        )

        val repository = repository()
        val support = repository.refreshSupport()
        assertTrue(support is ArrResult.Ok)
        val profiles = store.getAll(
            serverId,
            BazarrLibraryRepository.PROFILE_TYPE,
            BazarrLanguageProfile.serializer(),
        )
        assertEquals(listOf("Any"), profiles.map { it.name })
        val badges = store.get(
            serverId,
            BazarrLibraryRepository.BADGES_TYPE,
            "badges",
            BazarrBadges.serializer(),
        )
        assertNotNull(badges)
        assertEquals(7, badges!!.episodesMissing)
    }

    @Test
    fun episodesAreCachedPerSeriesWithTheirOwnAge() = runTest {
        fake.responses["get_episodes"] = BazarrTestJson.obj(
            "data" to BazarrTestJson.array(
                BazarrTestJson.episodeItem(5, 51, season = 2, episode = 2, title = "Later"),
                BazarrTestJson.episodeItem(5, 52, season = 1, episode = 1, title = "First"),
                // Another series' episode must not leak into this page.
                BazarrTestJson.episodeItem(6, 61, season = 1, episode = 1, title = "Elsewhere"),
            ),
        )
        val repository = repository()
        val first = repository.episodes(5)
        assertTrue(first is ArrResult.Ok)
        assertEquals(listOf(52, 51), (first as ArrResult.Ok).value.map { it.episodeId })
        assertEquals(1, episodeCalls())

        // Inside the TTL the cached page answers without a network call.
        clock += 60_000
        val cached = repository.episodes(5)
        assertTrue(cached is ArrResult.Ok)
        assertEquals(1, episodeCalls())

        // After the TTL a refetch happens.
        clock += 20 * 60_000
        repository.episodes(5)
        assertEquals(2, episodeCalls())
    }

    @Test
    fun episodeFetchFailureFallsBackToTheStalePage() = runTest {
        fake.responses["get_episodes"] = BazarrTestJson.obj(
            "data" to BazarrTestJson.array(
                BazarrTestJson.episodeItem(5, 51, season = 1, episode = 1, title = "Pilot"),
            ),
        )
        val repository = repository()
        repository.episodes(5)

        clock += 20 * 60_000
        fake.failure = { call -> if (call.operation == "get_episodes") IOException("offline") else null }
        val stale = repository.episodes(5)
        assertTrue(stale is ArrResult.Ok)
        assertEquals(51, (stale as ArrResult.Ok).value.single().episodeId)
    }

    @Test
    fun applyProfileSyncsAndWritesTheCache() = runTest {
        seedSeries(seriesModel(1, profileId = 1), seriesModel(2, profileId = 1))
        val repository = repository()
        repository.refresh(BazarrMediaKind.SERIES)
        val stamp = store.lastFetchedAt(serverId, BazarrLibraryRepository.SERIES_TYPE)

        val outcome = repository.applyProfile(BazarrMediaKind.SERIES, listOf(1, 2), 3)
        assertTrue(outcome is ArrResult.Ok)
        assertEquals(WriteOutcome.Synced, (outcome as ArrResult.Ok).value)

        val cached = repository.cachedSeries().sortedBy { it.seriesId }
        assertEquals(listOf(3, 3), cached.map { it.profileId })
        // A synced write must not extend the refresh TTL.
        assertEquals(stamp, store.lastFetchedAt(serverId, BazarrLibraryRepository.SERIES_TYPE))

        val post = fake.calls.last()
        assertEquals(listOf(1, 2), post.arguments["seriesid"])
        assertEquals(listOf("3"), post.arguments["profileid"])
    }

    @Test
    fun applyProfileQueuesOptimisticallyWhenOffline() = runTest {
        seedSeries(
            seriesModel(1, profileId = 1, fileCount = 10, missing = 10),
            seriesModel(2, profileId = 1, fileCount = 10, missing = 0),
        )
        val repository = repository()
        repository.refresh(BazarrMediaKind.SERIES)
        val stamp = store.lastFetchedAt(serverId, BazarrLibraryRepository.SERIES_TYPE)
        fake.failure = { call -> if (call.operation == "post_series") IOException("offline") else null }

        val outcome = repository.applyProfile(BazarrMediaKind.SERIES, listOf(1), 4)
        assertTrue(outcome is ArrResult.Ok)
        assertTrue((outcome as ArrResult.Ok).value is WriteOutcome.Queued)

        val action = queue.pending().single()
        assertEquals("POST", action.method)
        assertEquals("series?seriesid=1&profileid=4", action.path)

        // Optimistic cache write: profile applied, statuses recomputed, stamp kept.
        val cached = repository.cachedSeries().first { it.seriesId == 1 }
        assertEquals(4, cached.profileId)
        assertEquals(SubtitleStatus.MISSING, cached.status)
        assertEquals(stamp, store.lastFetchedAt(serverId, BazarrLibraryRepository.SERIES_TYPE))

        // The queued id flow feeds the per-item queued indicator.
        val queuedIds = repository.queuedSeriesIds().first()
        assertEquals(setOf(1), queuedIds)
    }

    @Test
    fun applyProfileSurfacesAuthErrorsWithoutQueueing() = runTest {
        seedSeries(seriesModel(1))
        val repository = repository()
        repository.refresh(BazarrMediaKind.SERIES)
        fake.errorStatus = { call -> if (call.operation == "post_series") 401 else null }

        val outcome = repository.applyProfile(BazarrMediaKind.SERIES, listOf(1), 2)
        assertTrue(outcome is ArrResult.Err)
        assertTrue(queue.pending().isEmpty())
        assertEquals(1, repository.cachedSeries().single().profileId)
    }

    @Test
    fun writePathRoundTripsThroughParseQueryIds() {
        val path = BazarrLibraryRepository.writePath(BazarrMediaKind.MOVIES, listOf(3, 4, 5), 7)
        assertEquals("movies?radarrid=3&radarrid=4&radarrid=5&profileid=7", path)
        assertEquals(listOf(3, 4, 5), BazarrLibraryRepository.parseQueryIds(path, "radarrid"))
        assertTrue(BazarrLibraryRepository.parseQueryIds(path, "seriesid").isEmpty())
        assertTrue(BazarrLibraryRepository.parseQueryIds("movies", "radarrid").isEmpty())
    }
}
