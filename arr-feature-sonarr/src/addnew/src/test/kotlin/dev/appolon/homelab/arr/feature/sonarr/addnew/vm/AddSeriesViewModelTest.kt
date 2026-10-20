package dev.appolon.homelab.arr.feature.sonarr.addnew.vm

import dev.appolon.homelab.arr.api.sonarr.FakeSonarrCalendar
import dev.appolon.homelab.arr.api.sonarr.FakeSonarrDiscovery
import dev.appolon.homelab.arr.api.sonarr.FakeSonarrLibrary
import dev.appolon.homelab.arr.api.sonarr.FakeSonarrSystem
import dev.appolon.homelab.arr.api.sonarr.model.ImportListExclusionResource
import dev.appolon.homelab.arr.api.sonarr.model.ImportListResource
import dev.appolon.homelab.arr.api.sonarr.model.LanguageProfileResource
import dev.appolon.homelab.arr.api.sonarr.model.MonitorTypes
import dev.appolon.homelab.arr.api.sonarr.model.QualityProfileResource
import dev.appolon.homelab.arr.api.sonarr.model.RootFolderResource
import dev.appolon.homelab.arr.api.sonarr.model.SeasonResource
import dev.appolon.homelab.arr.api.sonarr.model.SeriesResource
import dev.appolon.homelab.arr.api.sonarr.model.SeriesTypes
import dev.appolon.homelab.arr.api.sonarr.model.TagResource
import dev.appolon.homelab.arr.core.ArrResult
import dev.appolon.homelab.arr.core.ServerId
import dev.appolon.homelab.arr.feature.sonarr.addnew.SonarrSeriesLookupRunner
import dev.appolon.homelab.arr.feature.sonarr.addnew.data.SonarrAddRepository
import dev.appolon.homelab.arr.feature.sonarr.library.FakeActionQueue
import dev.appolon.homelab.arr.feature.sonarr.library.FakeEntityStore
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Covers the add ViewModel's search flow with year disambiguation and
 * existing-in-library badges, the add sheet form including season monitoring,
 * both add variants including offline queueing, and exclusion management.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AddSeriesViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private val library = FakeSonarrLibrary()
    private val system = FakeSonarrSystem()
    private val calendar = FakeSonarrCalendar()
    private val discovery = FakeSonarrDiscovery()
    private val store = FakeEntityStore()
    private val queue = FakeActionQueue()
    private val serverId = ServerId("server-1")

    private var lookupCalls = 0
    private var lookupResults = listOf<SeriesResource>()

    private val lookupRunner = SonarrSeriesLookupRunner { term ->
        lookupCalls++
        ArrResult.ok(lookupResults.filter { it.title?.contains(term, ignoreCase = true) == true })
    }

    private val repository = SonarrAddRepository(
        serverId = serverId,
        store = store,
        seriesApi = library.series,
        lookupRunner = lookupRunner,
        rootFolderApi = { system.rootFolderApi.list() },
        qualityProfileApi = { calendar.qualityProfile.list() },
        languageProfileApi = { calendar.languageProfile.list() },
        tagApi = { system.tagApi.list() },
        importListApi = discovery.importList,
        importListExclusionApi = discovery.importListExclusion,
        actionQueue = queue,
        ioDispatcher = dispatcher,
    )

    private lateinit var vm: AddSeriesViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        system.rootFolderStore[1] = RootFolderResource(id = 1, path = "/tv")
        calendar.qualityProfileStore[2] = QualityProfileResource(id = 2, name = "HD")
        calendar.languageProfileStore[3] = LanguageProfileResource(id = 3, name = "English")
        system.tagStore[4] = TagResource(id = 4, label = "kids")
        // One series already in the library: badges by tvdb id.
        library.seriesStore[7] = SeriesResource(id = 7, title = "Aqua Teen", tvdbId = 100)
        lookupResults = listOf(
            lookup(100, "Aqua Teen", 2000, listOf(0, 1, 2)),
            lookup(200, "Aqua Donk", 2000, listOf(1)),
            lookup(201, "Aqua Donk", 2019, listOf(1, 2)),
        )
        vm = AddSeriesViewModel(repository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun lookup(tvdbId: Int, title: String, year: Int, seasons: List<Int>) =
        SeriesResource(
            title = title,
            tvdbId = tvdbId,
            year = year,
            seasons = seasons.map { SeasonResource(seasonNumber = it) },
        )

    @Test
    fun `initial state loads defaults and library badges`() {
        val state = vm.state.value
        assertEquals(listOf("/tv"), state.rootFolders.map { it.path })
        assertEquals(listOf("HD"), state.qualityProfiles.map { it.name })
        assertEquals(1, state.languageProfiles.size)
        assertEquals(listOf("kids"), state.tags.map { it.label })
        assertEquals(setOf(100), state.libraryTvdbIds)
    }

    @Test
    fun `search stores results and exposes distinct years`() {
        vm.onQueryChange("Aqua")
        vm.search()
        val state = vm.state.value
        assertEquals(3, state.results.size)
        assertEquals(listOf(2000, 2019), state.availableYears)
        assertTrue(state.searching.not())
        assertTrue(state.searchError == null)
    }

    @Test
    fun `year filter narrows same-titled results`() {
        vm.onQueryChange("Aqua")
        vm.search()
        vm.setYearFilter(2019)
        assertEquals(listOf("Aqua Donk"), vm.state.value.visibleResults.map { it.title })
        vm.setYearFilter(null)
        assertEquals(3, vm.state.value.visibleResults.size)
    }

    @Test
    fun `query change debounces the search`() = runTest(dispatcher.scheduler) {
        vm.onQueryChange("Aqua")
        assertEquals(0, lookupCalls)
        advanceTimeBy(500)
        assertEquals(1, lookupCalls)
    }

    @Test
    fun `clearing the query resets results`() {
        vm.onQueryChange("Aqua")
        vm.search()
        vm.onQueryChange("")
        val state = vm.state.value
        assertTrue(state.results.isEmpty())
        assertFalse(state.searched)
    }

    @Test
    fun `open add form seeds defaults and all seasons`() {
        vm.onQueryChange("Aqua")
        vm.search()
        vm.openAddForm(vm.state.value.results.first { it.tvdbId == 100 })
        val form = vm.state.value.form
        assertEquals("/tv", form?.rootFolderPath)
        assertEquals(2, form?.qualityProfileId)
        assertEquals(3, form?.languageProfileId)
        assertEquals(setOf(0, 1, 2), form?.monitoredSeasons)
        assertTrue(form?.series?.tvdbId == 100)
    }

    @Test
    fun `strategy switch recomputes the season picker`() {
        vm.onQueryChange("Aqua")
        vm.search()
        vm.openAddForm(vm.state.value.results.first { it.tvdbId == 100 })
        vm.setMonitorStrategy(MonitorTypes.FIRSTSEASON)
        assertEquals(setOf(1), vm.state.value.form?.monitoredSeasons)
        vm.toggleSeason(2)
        assertEquals(setOf(1, 2), vm.state.value.form?.monitoredSeasons)
        vm.setMonitorStrategy(MonitorTypes.NONE)
        assertEquals(emptySet<Int>(), vm.state.value.form?.monitoredSeasons)
    }

    @Test
    fun `submit add sends the form through the repository`() = runTest {
        vm.onQueryChange("Aqua")
        vm.search()
        vm.openAddForm(vm.state.value.results.first { it.tvdbId == 200 })
        vm.updateAddForm { it.copy(seriesType = SeriesTypes.ANIME, tagIds = setOf(4)) }
        vm.submitAdd(searchForMissing = true)
        val stored = library.seriesStore.values.first { it.tvdbId == 200 }
        assertEquals(200, stored.tvdbId)
        assertEquals("/tv", stored.rootFolderPath)
        assertEquals(2, stored.qualityProfileId)
        assertEquals(SeriesTypes.ANIME, stored.seriesType)
        assertEquals(setOf(4), stored.tags)
        assertEquals(MonitorTypes.ALL, stored.addOptions.monitor)
        assertTrue(stored.addOptions.searchForMissingEpisodes)
        assertEquals(listOf(1), stored.seasons?.map { it.seasonNumber })
        assertTrue(stored.seasons?.all { it.monitored } == true)
        assertNull(vm.state.value.form)
        assertTrue(vm.state.value.message is AddMessage.Added)
    }

    @Test
    fun `submit add queues offline with a pending badge`() = runTest {
        library.error = { op: String -> if (op == "Series.create") IOException("down") else null }
        vm.onQueryChange("Aqua")
        vm.search()
        vm.openAddForm(vm.state.value.results.first { it.tvdbId == 200 })
        vm.submitAdd(searchForMissing = false)
        val state = vm.state.value
        assertNull(state.form)
        assertTrue(state.message is AddMessage.AddQueued)
        assertEquals(1, state.queuedForServer)
        assertEquals(setOf(200), state.queuedTvdbIds)
        assertEquals("POST", queue.enqueued.single().method)
        assertFalse(
            library.seriesStore.values.any { it.tvdbId == 200 },
        )
    }

    @Test
    fun `submit add without a root folder is a no-op`() = runTest {
        system.rootFolderStore.clear()
        vm.onQueryChange("Aqua")
        vm.search()
        vm.openAddForm(vm.state.value.results.first { it.tvdbId == 200 })
        // Defaults loaded at construction still carry the old folder, so blank it.
        vm.updateAddForm { it.copy(rootFolderPath = "") }
        vm.submitAdd(searchForMissing = false)
        assertTrue(library.seriesStore.values.none { it.tvdbId == 200 })
        assertTrue(vm.state.value.form != null)
    }

    @Test
    fun `import lists load and sync`() = runTest {
        discovery.importListStore[3] = ImportListResource(id = 3, name = "Trakt")
        vm.refreshLists()
        assertEquals(listOf("Trakt"), vm.state.value.importLists.map { it.name })
        vm.syncImportList(vm.state.value.importLists.single())
        assertTrue(vm.state.value.message is AddMessage.ListSynced)
        discovery.error = { op: String ->
            if (op == "ImportList.action") IOException("down") else null
        }
        vm.syncImportList(vm.state.value.importLists.single())
        assertTrue(vm.state.value.message is AddMessage.ListSyncQueued)
    }

    @Test
    fun `exclusion draft validates both fields`() = runTest {
        vm.openExclusionDraft()
        vm.updateExclusionDraft { it.copy(title = "  ", tvdbText = "abc") }
        vm.submitExclusion()
        val draft = vm.state.value.exclusionDraft
        assertTrue(draft?.titleError == true)
        assertTrue(draft?.tvdbError == true)
        assertTrue(discovery.importListExclusionStore.isEmpty())

        vm.updateExclusionDraft { it.copy(title = "Aqua", tvdbText = "100") }
        vm.submitExclusion()
        assertNull(vm.state.value.exclusionDraft)
        val stored = discovery.importListExclusionStore.values.single()
        assertEquals(100, stored.tvdbId)
        assertEquals("Aqua", stored.title)
        assertTrue(vm.state.value.message is AddMessage.ExclusionSaved)
    }

    @Test
    fun `delete exclusion removes it and cancels pending creates`() = runTest {
        discovery.error = { op: String ->
            if (op == "ImportListExclusion.create") IOException("down") else null
        }
        vm.openExclusionDraft()
        vm.updateExclusionDraft { it.copy(title = "Aqua", tvdbText = "100") }
        vm.submitExclusion()
        assertEquals(1, queue.enqueued.size)

        discovery.error = { _: String -> null }
        vm.refreshLists()
        vm.deleteExclusion(vm.state.value.exclusions.single())
        assertTrue(queue.enqueued.isEmpty())
        assertTrue(vm.state.value.message is AddMessage.ExclusionDeleted)
    }

    @Test
    fun `consume message clears the snackbar`() {
        vm.onQueryChange("Aqua")
        vm.search()
        vm.openAddForm(vm.state.value.results.first { it.tvdbId == 200 })
        vm.submitAdd(searchForMissing = false)
        assertTrue(vm.state.value.message != null)
        vm.consumeMessage()
        assertNull(vm.state.value.message)
    }
}
