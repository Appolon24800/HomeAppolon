package dev.appolon.homelab.arr.feature.sonarr.detail.vm

import dev.appolon.homelab.arr.api.sonarr.FakeSonarrLibrary
import dev.appolon.homelab.arr.api.sonarr.FakeSonarrQueue
import dev.appolon.homelab.arr.api.sonarr.model.EpisodeFileResource
import dev.appolon.homelab.arr.api.sonarr.model.EpisodeHistoryEventType
import dev.appolon.homelab.arr.api.sonarr.model.EpisodeResource
import dev.appolon.homelab.arr.api.sonarr.model.HistoryResource
import dev.appolon.homelab.arr.api.sonarr.model.LanguageResource
import dev.appolon.homelab.arr.api.sonarr.model.ManualImportResource
import dev.appolon.homelab.arr.api.sonarr.model.Quality
import dev.appolon.homelab.arr.api.sonarr.model.QualityDefinitionResource
import dev.appolon.homelab.arr.api.sonarr.model.QualityModel
import dev.appolon.homelab.arr.api.sonarr.model.QualityProfileResource
import dev.appolon.homelab.arr.api.sonarr.model.ReleaseResource
import dev.appolon.homelab.arr.api.sonarr.model.SeasonResource
import dev.appolon.homelab.arr.api.sonarr.model.SeasonStatisticsResource
import dev.appolon.homelab.arr.api.sonarr.model.SeriesResource
import dev.appolon.homelab.arr.api.sonarr.model.SeriesTypes
import dev.appolon.homelab.arr.api.sonarr.model.TagResource
import dev.appolon.homelab.arr.core.ArrError
import dev.appolon.homelab.arr.core.ArrResult
import dev.appolon.homelab.arr.core.ServerId
import dev.appolon.homelab.arr.feature.sonarr.detail.data.SonarrCommandBodies
import dev.appolon.homelab.arr.feature.sonarr.detail.data.SonarrSeriesDetailRepository
import dev.appolon.homelab.arr.feature.sonarr.library.FakeActionQueue
import dev.appolon.homelab.arr.feature.sonarr.library.FakeEntityStore
import dev.appolon.homelab.arr.ui.lce.LceState
import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Covers the detail ViewModel's LCE mapping, season selection, episode
 * loading, every sheet flow (edit, file editor, season pass, rename, manual
 * search, interactive import), offline queue indicators, and delete.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SeriesDetailViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private val library = FakeSonarrLibrary()
    private val queue = FakeSonarrQueue()
    private val store = FakeEntityStore()
    private val actionQueue = FakeActionQueue()
    private val serverId = ServerId("server-1")
    private val postedCommands = mutableListOf<String>()
    private var posterResult: ArrResult<Unit> = ArrResult.ok(Unit)

    private val repository = SonarrSeriesDetailRepository(
        serverId = serverId,
        store = store,
        seriesApi = library.series,
        episodeApi = library.episode,
        episodeFileApi = library.episodeFile,
        renameEpisodeApi = library.renameEpisode,
        seasonPassApi = library.seasonPass,
        historyApi = queue.history,
        releaseApi = queue.release,
        manualImportApi = queue.manualImport,
        commandPoster = { body ->
            postedCommands.add(body)
            posterResult
        },
        qualityProfileApi = { listOf(QualityProfileResource(id = 1, name = "Any HD")) },
        qualityDefinitionApi = {
            listOf(
                QualityDefinitionResource(id = 1, quality = Quality(id = 1, name = "HDTV-720p")),
                QualityDefinitionResource(id = 2, quality = Quality(id = 2, name = "WEBDL-1080p")),
            )
        },
        languageApi = { listOf(LanguageResource(id = 1, name = "English")) },
        tagApi = { listOf(TagResource(id = 4, label = "anime")) },
        actionQueue = actionQueue,
        ioDispatcher = dispatcher,
    )

    private lateinit var vm: SeriesDetailViewModel
    private var latestLce: LceState<SeriesDetailUiState> = LceState.Loading
    private val collectors = CoroutineScope(dispatcher + SupervisorJob())

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        library.seriesStore[5] = series()
        library.episodeStore[11] = episode(11, 1)
        library.episodeStore[12] = episode(12, 2, monitored = false)
        library.episodeFileStore[30] = file()
        library.renames.add(
            dev.appolon.homelab.arr.api.sonarr.model.RenameEpisodeResource(
                id = 1,
                seriesId = 5,
                seasonNumber = 1,
                episodeFileId = 30,
                existingPath = "/tv/old.mkv",
                newPath = "/tv/new.mkv",
            ),
        )
        queue.historyStore[1] = history(1, episodeId = 11)
        queue.releases.add(
            ReleaseResource(
                guid = "g1",
                seriesId = 5,
                episodeId = 11,
                title = "Show.S01E01.720p-GROUP",
                indexer = "indexer1",
            ),
        )
        queue.manualImports.add(manualImportCandidate())
        vm = SeriesDetailViewModel(5, repository)
        // stateIn only computes while subscribed; keep one eager collector.
        collectors.launch { vm.lce.collect { latestLce = it } }
    }

    @After
    fun tearDown() {
        collectors.cancel()
        Dispatchers.resetMain()
    }

    @Test
    fun `initial load renders series seasons and episodes`() = runTest {
        val state = vm.state.value
        assertEquals("Show", state.series?.title)
        // Seasons sort with specials last.
        assertEquals(listOf(1, 0), state.seasons.map { it.seasonNumber })
        // The first non-special season is selected by default.
        assertEquals(1, state.selectedSeason)
        assertEquals(listOf(1, 2), state.seasonEpisodes.map { it.episodeNumber })
        assertEquals(listOf("Any HD"), state.qualityProfiles.map { it.name })
        assertEquals(2, state.qualities.size)
        assertTrue(latestLce is LceState.Content)
    }

    @Test
    fun `network failure with empty cache surfaces error`() = runTest {
        library.error = { IOException("down") }
        val failingVm = SeriesDetailViewModel(
            5,
            repository.copyFor(store = FakeEntityStore()),
        )
        collectors.launch { failingVm.lce.collect {} }
        assertTrue(failingVm.lce.value is LceState.Error)
    }

    @Test
    fun `selecting specials switches episodes`() = runTest {
        vm.selectSeason(0)
        assertEquals(0, vm.state.value.selectedSeason)
        assertTrue(vm.state.value.seasonEpisodes.isEmpty())
    }

    @Test
    fun `season toggle updates series through the cache`() = runTest {
        vm.toggleSeasonMonitored(1)
        assertTrue(library.seriesStore[5]?.seasons?.firstOrNull { it.seasonNumber == 1 }?.monitored == true)
        assertTrue(vm.state.value.seasons.first { it.seasonNumber == 1 }.monitored)
    }

    @Test
    fun `episode search offline queues and flags the series`() = runTest {
        posterResult = ArrResult.err(ArrError.Network(null))

        vm.searchSeason(1)

        assertEquals(DetailMessage.SearchQueued, vm.state.value.message)
        assertTrue(vm.state.value.queuedForSeries)
        assertTrue(postedCommands.single().contains("SeasonSearch"))
        val action = actionQueue.enqueued.single()
        assertEquals("api/v3/command", action.path)
    }

    @Test
    fun `manual search loads releases and grab sends`() = runTest {
        vm.openManualSearchEpisode(vm.state.value.seasonEpisodes.first())

        val target = vm.state.value.searchTarget
        assertTrue(target is SearchTarget.Episode)
        assertEquals(1, vm.state.value.releases.size)
        assertFalse(vm.state.value.releasesLoading)

        vm.grabRelease(vm.state.value.releases.first())

        assertEquals(listOf("g1"), queue.grabbedReleases.map { it.guid })
        assertEquals(DetailMessage.GrabSent, vm.state.value.message)
        vm.consumeMessage()
        assertNull(vm.state.value.message)
    }

    @Test
    fun `rename preview loads rows and rename posts command`() = runTest {
        vm.openRenamePreview()

        val preview = vm.state.value.renamePreview
        assertEquals(listOf("/tv/new.mkv"), preview?.rows?.map { it.newPath })
        assertFalse(preview?.loading == true)

        vm.runRename()

        assertNull(vm.state.value.renamePreview)
        assertTrue(postedCommands.single().contains("RenameFiles"))
        assertEquals(DetailMessage.RenameStarted, vm.state.value.message)
    }

    @Test
    fun `interactive import selects and imports`() = runTest {
        vm.openImport(seasonNumber = 1)

        val importState = vm.state.value.importState
        assertEquals(1, importState?.candidates?.size)
        assertFalse(importState?.loading == true)

        vm.toggleImportSelected(importState!!.candidates.first().id)
        vm.importSelected()

        assertEquals(1, queue.reprocessedImports.size)
        assertEquals(DetailMessage.ImportSynced(1), vm.state.value.message)
        assertNull(vm.state.value.importState)
    }

    @Test
    fun `file editor prefills from episode and saves quality`() = runTest {
        vm.openFileEditor(vm.state.value.seasonEpisodes.first { it.episodeNumber == 1 })

        val form = vm.state.value.fileForm
        assertEquals(30, form?.fileId)
        assertEquals(1, form?.qualityId)
        assertEquals(setOf(1), form?.languageIds)

        vm.updateFileEditor { it.copy(qualityId = 2, releaseGroup = "GRP") }
        vm.submitFileEditor()

        assertEquals("WEBDL-1080p", library.episodeFileStore[30]?.quality?.quality?.name)
        assertEquals(DetailMessage.WriteSynced, vm.state.value.message)
        assertNull(vm.state.value.fileForm)
    }

    @Test
    fun `file delete from editor detaches the file`() = runTest {
        vm.openFileEditor(vm.state.value.seasonEpisodes.first { it.episodeNumber == 1 })
        vm.deleteFileFromEditor()

        val episode = vm.state.value.episodes.first { it.id == 11 }
        assertFalse(episode.hasFile)
        assertNull(vm.state.value.fileForm)
    }

    @Test
    fun `edit sheet prefills from series and submits changes`() = runTest {
        vm.openEdit()

        val form = vm.state.value.editForm
        assertTrue(form?.monitored == true)
        assertEquals(SeriesTypes.STANDARD, form?.seriesType)
        assertEquals("4", form?.tagsText)

        vm.updateEdit { it.copy(seriesType = SeriesTypes.ANIME, tagsText = "4, 7") }
        vm.submitEdit()

        val saved = library.seriesStore[5]
        assertEquals(SeriesTypes.ANIME, saved?.seriesType)
        assertEquals(setOf(4, 7), saved?.tags)
        assertNull(vm.state.value.editForm)
    }

    @Test
    fun `edit sheet rejects malformed tag list`() = runTest {
        vm.openEdit()
        vm.updateEdit { it.copy(tagsText = "four") }
        vm.submitEdit()

        assertTrue(vm.state.value.editForm?.tagsError == true)
        assertEquals(SeriesTypes.STANDARD, library.seriesStore[5]?.seriesType)
    }

    @Test
    fun `season pass sheet applies strategy to this series`() = runTest {
        vm.openSeasonPass()
        vm.updateSeasonPass { it.copy(monitored = true, monitor = dev.appolon.homelab.arr.api.sonarr.model.MonitorTypes.FUTURE) }
        vm.submitSeasonPass()

        val saved = library.seriesStore[5]
        assertTrue(saved?.monitored == true)
        assertTrue(saved?.seasons.orEmpty().all { it.monitored })
        assertNull(vm.state.value.seasonPassForm)
    }

    @Test
    fun `history tab lists events for the series`() = runTest {
        vm.setTab(DetailTab.HISTORY)

        assertEquals(1, vm.state.value.history.size)
        assertEquals(EpisodeHistoryEventType.GRABBED, vm.state.value.history.first().eventType)
        assertEquals(1, vm.state.value.historyFor(11).size)
    }

    @Test
    fun `delete confirms and marks deleted`() = runTest {
        vm.confirmDelete(deleteFiles = true, addImportListExclusion = false)

        assertTrue(vm.state.value.deleted)
        assertEquals(DetailMessage.DeleteSynced, vm.state.value.message)
        assertNull(repository.cachedSeries(5))
        assertNull(library.seriesStore[5])
        assertTrue(latestLce is LceState.Empty)
    }

    @Test
    fun `delete offline still leaves the screen`() = runTest {
        library.error = { op -> if (op == "Series.delete") IOException("down") else null }

        vm.confirmDelete(deleteFiles = false, addImportListExclusion = false)

        assertTrue(vm.state.value.deleted)
        assertEquals(DetailMessage.DeleteQueued, vm.state.value.message)
        assertEquals(1, actionQueue.enqueued.size)
    }

    private fun SonarrSeriesDetailRepository.copyFor(
        store: FakeEntityStore,
    ): SonarrSeriesDetailRepository = SonarrSeriesDetailRepository(
        serverId = serverId,
        store = store,
        seriesApi = library.series,
        episodeApi = library.episode,
        episodeFileApi = library.episodeFile,
        renameEpisodeApi = library.renameEpisode,
        seasonPassApi = library.seasonPass,
        historyApi = queue.history,
        releaseApi = queue.release,
        manualImportApi = queue.manualImport,
        commandPoster = { body ->
            postedCommands.add(body)
            posterResult
        },
        qualityProfileApi = { emptyList() },
        qualityDefinitionApi = { emptyList() },
        languageApi = { emptyList() },
        tagApi = { emptyList() },
        actionQueue = FakeActionQueue(),
        ioDispatcher = dispatcher,
    )

    private fun series() = SeriesResource(
        id = 5,
        title = "Show",
        monitored = true,
        qualityProfileId = 1,
        path = "/tv/Show",
        year = 2020,
        seasons = listOf(
            SeasonResource(
                seasonNumber = 1,
                monitored = false,
                statistics = SeasonStatisticsResource(
                    episodeCount = 2,
                    episodeFileCount = 1,
                    sizeOnDisk = 2048,
                    percentOfEpisodes = 50.0,
                ),
            ),
            SeasonResource(
                seasonNumber = 0,
                monitored = false,
                statistics = SeasonStatisticsResource(),
            ),
        ),
        tags = setOf(4),
    )

    private fun episode(id: Int, number: Int, monitored: Boolean = true) = EpisodeResource(
        id = id,
        seriesId = 5,
        seasonNumber = 1,
        episodeNumber = number,
        title = "Episode $number",
        monitored = monitored,
        hasFile = id == 11,
        episodeFileId = if (id == 11) 30 else 0,
        episodeFile = if (id == 11) file() else EpisodeFileResource(),
    )

    private fun file() = EpisodeFileResource(
        id = 30,
        seriesId = 5,
        seasonNumber = 1,
        size = 4096,
        releaseGroup = "OLD",
        languages = listOf(dev.appolon.homelab.arr.api.sonarr.model.Language(id = 1, name = "English")),
        quality = QualityModel(quality = Quality(id = 1, name = "HDTV-720p")),
    )

    private fun history(id: Int, episodeId: Int) = HistoryResource(
        id = id,
        episodeId = episodeId,
        seriesId = 5,
        sourceTitle = "Show.S01E01.720p",
        eventType = EpisodeHistoryEventType.GRABBED,
        date = "2026-10-01T00:00:00Z",
    )

    private fun manualImportCandidate() = ManualImportResource(
        id = 1,
        path = "/tv/Show/file.mkv",
        name = "file.mkv",
        size = 4096,
        seasonNumber = 1,
        series = SeriesResource(id = 5),
        quality = QualityModel(quality = Quality(id = 1, name = "HDTV-720p")),
    )
}
