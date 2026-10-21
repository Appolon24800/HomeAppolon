package dev.appolon.homelab.arr.feature.bazarr.series

import dev.appolon.homelab.arr.api.bazarr.FakeBazarrLibrary
import dev.appolon.homelab.arr.api.bazarr.FakeBazarrSystem
import dev.appolon.homelab.arr.core.ServerId
import dev.appolon.homelab.arr.feature.bazarr.series.data.BazarrLibraryFilters
import dev.appolon.homelab.arr.feature.bazarr.series.data.BazarrLibraryRepository
import dev.appolon.homelab.arr.feature.bazarr.series.data.BazarrMediaKind
import dev.appolon.homelab.arr.feature.bazarr.series.vm.BazarrEpisodesViewModel
import dev.appolon.homelab.arr.feature.bazarr.series.vm.BazarrLibraryUiState
import dev.appolon.homelab.arr.feature.bazarr.series.vm.BazarrLibraryViewModel
import dev.appolon.homelab.arr.feature.bazarr.series.vm.LibraryMessage
import dev.appolon.homelab.arr.ui.lce.LceState
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BazarrLibraryViewModelTest {

    private val main = StandardTestDispatcher()
    private val serverId = ServerId("s1")
    private lateinit var fake: FakeBazarrLibrary
    private lateinit var system: FakeBazarrSystem
    private lateinit var store: FakeEntityStore
    private lateinit var queue: FakeActionQueue

    @Before
    fun setUp() {
        Dispatchers.setMain(main)
        fake = FakeBazarrLibrary()
        system = FakeBazarrSystem()
        store = FakeEntityStore()
        queue = FakeActionQueue()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
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
        io = main,
    )

    private fun seedSeriesLibrary() {
        fake.responses["get_series"] = BazarrTestJson.obj(
            "data" to BazarrTestJson.array(
                BazarrTestJson.seriesItem(1, "Complete Show", fileCount = 8, missingCount = 0),
                BazarrTestJson.seriesItem(2, "Partial Show", fileCount = 8, missingCount = 3),
                BazarrTestJson.seriesItem(3, "Missing Show", fileCount = 8, missingCount = 8),
            ),
            "total" to kotlinx.serialization.json.JsonPrimitive(3),
        )
        system.responses["get_languages_profiles"] = BazarrTestJson.obj(
            "data" to BazarrTestJson.array(
                BazarrTestJson.profileItem(1, "Any", listOf("eng", "deu")),
            ),
        )
        fake.responses["get_badges"] = BazarrTestJson.obj(
            "episodes" to kotlinx.serialization.json.JsonPrimitive(11),
            "movies" to kotlinx.serialization.json.JsonPrimitive(4),
        )
    }

    private fun runSeriesVm(
        block: suspend kotlinx.coroutines.test.TestScope.(BazarrLibraryViewModel) -> Unit,
    ) = runTest {
        seedSeriesLibrary()
        val vm = BazarrLibraryViewModel(repository(), BazarrMediaKind.SERIES)
        val job = launch { vm.lce.collect {} }
        advanceUntilIdle()
        block(vm)
        advanceUntilIdle()
        job.cancel()
    }

    @Test
    fun loadsSeriesWithBadgesAndProfiles() = runSeriesVm { vm ->
        val state = vm.state.value
        assertTrue(state.loaded)
        assertEquals(3, state.series.size)
        assertEquals(11, state.badges?.episodesMissing)
        assertEquals(listOf("Any"), state.profiles.map { it.name })
        assertTrue(vm.lce.value is LceState.Content<*>)
    }

    @Test
    fun statusFilterNarrowsThePresentation() = runSeriesVm { vm ->
        vm.setFilterValue(
            BazarrLibraryFilters.STATUS,
            dev.appolon.homelab.arr.core.FilterValue.Choice(
                setOf(dev.appolon.homelab.arr.feature.bazarr.series.data.SubtitleStatus.MISSING.name),
            ),
        )
        val presented = vm.state.value.seriesPresentation.all
        assertEquals(listOf("Missing Show"), presented.map { it.title })
    }

    @Test
    fun seriesFilterSpecsOmitHearingImpaired() = runSeriesVm { vm ->
        val ids = vm.state.value.filterSpecs.map { it.id }
        assertFalse(BazarrLibraryFilters.HEARING_IMPAIRED in ids)
        assertTrue(BazarrLibraryFilters.MONOLINGUAL in ids)
        assertTrue(BazarrLibraryFilters.BLACKLIST in ids)
    }

    @Test
    fun selectionTargetsAndBulkApplyQueueWhenOffline() = runSeriesVm { vm ->
        fake.failure = { call -> if (call.operation == "post_series") IOException("offline") else null }
        vm.enterSelectMode(2)
        vm.toggleSelected(3)
        assertEquals(setOf(2, 3), vm.state.value.selection)

        vm.applyProfile(1)
        advanceUntilIdle()
        val state = vm.state.value
        assertTrue(state.message is LibraryMessage.ProfileQueued)
        assertFalse(state.selectMode)
        assertEquals(setOf(2, 3), state.queuedIds)
        assertEquals(1, queue.pending().size)
        // The optimistic cache write reflects the new profile immediately.
        assertEquals(
            listOf(1, 1),
            state.series.filter { it.seriesId in setOf(2, 3) }
                .sortedBy { it.seriesId }
                .map { it.profileId },
        )
    }

    @Test
    fun moviesViewModelExposesMovieSorts() = runTest {
        fake.responses["get_movies"] = BazarrTestJson.obj(
            "data" to BazarrTestJson.array(
                BazarrTestJson.movieItem(1, "Film", missing = listOf(BazarrTestJson.language("eng"))),
            ),
            "total" to kotlinx.serialization.json.JsonPrimitive(1),
        )
        val vm = BazarrLibraryViewModel(repository(), BazarrMediaKind.MOVIES)
        val job = launch { vm.lce.collect {} }
        advanceUntilIdle()
        val state: BazarrLibraryUiState = vm.state.value
        assertTrue(state.loaded)
        assertEquals(1, state.movies.size)
        assertTrue("subtitles" in state.sortOptions)
        job.cancel()
    }

    @Test
    fun episodesViewModelGroupsBySeasonAndLoadsHeader() = runTest {
        fake.responses["get_series"] = BazarrTestJson.obj(
            "data" to BazarrTestJson.array(
                BazarrTestJson.seriesItem(5, "Browser Show", fileCount = 9, missingCount = 4),
            ),
            "total" to kotlinx.serialization.json.JsonPrimitive(1),
        )
        fake.responses["get_episodes"] = BazarrTestJson.obj(
            "data" to BazarrTestJson.array(
                BazarrTestJson.episodeItem(5, 51, season = 2, episode = 1, title = "S2E1"),
                BazarrTestJson.episodeItem(5, 52, season = 1, episode = 1, title = "S1E1"),
                BazarrTestJson.episodeItem(5, 53, season = 1, episode = 2, title = "S1E2"),
            ),
        )
        val vm = BazarrEpisodesViewModel(repository(), seriesId = 5)
        val job = launch { vm.lce.collect {} }
        advanceUntilIdle()

        val state = vm.state.value
        assertTrue(state.loaded)
        assertEquals("Browser Show", state.series?.title)
        assertEquals(listOf(1 to 2, 2 to 1), state.seasons.map { it.first to it.second.size })
        assertEquals(listOf(52, 53), state.seasons[0].second.map { it.episodeId })
        assertTrue(vm.lce.value is LceState.Content<*>)
        job.cancel()
    }
}
