package dev.appolon.homelab.arr.feature.sonarr.search.vm

import dev.appolon.homelab.arr.api.sonarr.model.EpisodeResource
import dev.appolon.homelab.arr.api.sonarr.FakeSonarrQueue
import dev.appolon.homelab.arr.api.sonarr.model.Language
import dev.appolon.homelab.arr.api.sonarr.model.LanguageResource
import dev.appolon.homelab.arr.api.sonarr.model.ManualImportResource
import dev.appolon.homelab.arr.api.sonarr.model.Quality
import dev.appolon.homelab.arr.api.sonarr.model.QualityModel
import dev.appolon.homelab.arr.api.sonarr.model.QualityProfileQualityItemResource
import dev.appolon.homelab.arr.api.sonarr.model.QualityProfileResource
import dev.appolon.homelab.arr.api.sonarr.model.SeriesResource
import dev.appolon.homelab.arr.core.ServerId
import dev.appolon.homelab.arr.data.store.ActionQueueImpl
import dev.appolon.homelab.arr.data.testing.FakeEntityStore
import dev.appolon.homelab.arr.feature.sonarr.search.data.SonarrInteractiveImportRepository
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.put
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class InteractiveImportViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private val queue = FakeSonarrQueue()
    private val store = FakeEntityStore()
    private val actionQueue = ActionQueueImpl(store)
    private val series = SeriesResource(
        id = 5,
        title = "Target Series",
        path = "/media/tv/Target Series",
        qualityProfileId = 2,
    )

    private val repository = SonarrInteractiveImportRepository(
        seasonNumber = 2,
        serverId = ServerId("server-1"),
        store = store,
        manualImportApi = queue.manualImport,
        fileSystemCall = { path, _ ->
            buildJsonObject {
                put("parent", "/")
                put(
                    "directories",
                    buildJsonArray {
                        add(
                            buildJsonObject {
                                put("type", "folder")
                                put("name", "season-02")
                                put("path", (path ?: "") + "/season-02")
                            },
                        )
                    },
                )
            }
        },
        seriesById = { id -> if (id == 5) series else null },
        languageTable = {
            listOf(
                LanguageResource(id = 1, name = "English"),
                LanguageResource(id = 3, name = "German"),
            )
        },
        qualityProfileById = {
            QualityProfileResource(
                id = 2,
                items = listOf(
                    QualityProfileQualityItemResource(
                        quality = Quality(id = 7, name = "WEBDL-1080p"),
                        allowed = true,
                    ),
                ),
            )
        },
        actionQueue = actionQueue,
        io = Dispatchers.Unconfined,
    )

    private fun candidate(
        id: Int,
        name: String,
        matchedSeriesId: Int = 5,
        episodeIds: List<Int> = listOf(42),
    ) = ManualImportResource(
        id = id,
        path = "/media/tv/Target Series/season-02/$name",
        relativePath = name,
        size = 1_400_000_000L,
        series = SeriesResource(
            id = matchedSeriesId,
            title = if (matchedSeriesId == series.id) series.title else "Other Series",
        ),
        seasonNumber = 2,
        episodes = episodeIds.map { episodeId ->
            EpisodeResource(id = episodeId, episodeNumber = 5)
        },
        quality = QualityModel(quality = Quality(id = 7, name = "WEBDL-1080p")),
        languages = listOf(Language(id = 1, name = "English")),
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `loading the series also browses its folder and fills the options`() {
        val vm = InteractiveImportViewModel(seriesId = 5, repository = repository)
        val state = vm.state.value

        assertEquals(ImportFlowLoad.Ready, state.seriesLoad)
        assertEquals("Target Series", state.series?.title)
        assertEquals("/media/tv/Target Series", state.folder)
        assertTrue(state.folders.isNotEmpty())
        assertEquals(listOf("WEBDL-1080p"), state.qualityOptions.map { it.name })
        assertEquals(listOf("English", "German"), state.languageOptions.map { it.name })
    }

    @Test
    fun `picking a folder lists and preselects its files`() {
        queue.manualImports += listOf(
            candidate(1, "a.mkv"),
            candidate(2, "b.mkv", matchedSeriesId = 9, episodeIds = listOf(42, 43)),
        )
        val vm = InteractiveImportViewModel(seriesId = 5, repository = repository)

        vm.pickFolder("/media/tv/Target Series/season-02")
        val state = vm.state.value

        assertEquals(ImportFlowLoad.Ready, state.filesLoad)
        assertEquals(2, state.files.size)
        assertEquals(2, state.selectedCount)
        assertEquals("S02E05", state.files[0].matchedEpisodesLabel)
        assertEquals("S02E05 (+1)", state.files[1].matchedEpisodesLabel)
        assertEquals("Other Series", state.files[1].matchedTitle)
        assertTrue(state.canApprove)
    }

    @Test
    fun `toggle and selectAll drive the selection`() {
        queue.manualImports += listOf(candidate(1, "a.mkv"), candidate(2, "b.mkv"))
        val vm = InteractiveImportViewModel(seriesId = 5, repository = repository)
        vm.pickFolder("/media/tv/Target Series/season-02")

        vm.toggleFile(1)
        assertEquals(1, vm.state.value.selectedCount)
        assertTrue(vm.state.value.canApprove)

        vm.selectAll(true)
        assertEquals(2, vm.state.value.selectedCount)

        vm.selectAll(false)
        assertEquals(0, vm.state.value.selectedCount)
    }

    @Test
    fun `applyDecision records the override and closes the editor`() {
        queue.manualImports += listOf(candidate(1, "a.mkv"))
        val vm = InteractiveImportViewModel(seriesId = 5, repository = repository)
        vm.pickFolder("/media/tv/Target Series/season-02")
        vm.openEditor(1)

        vm.applyDecision(
            1,
            Quality(id = 7, name = "WEBDL-1080p"),
            Language(id = 3, name = "German"),
        )

        val state = vm.state.value
        assertNull(state.editingFileId)
        val file = state.files.single()
        assertEquals("WEBDL-1080p", file.qualityName)
        assertEquals("German", file.languageName)
        assertTrue(file.selected)
    }

    @Test
    fun `approve imports the selection with the flow series pinned`() {
        queue.manualImports += listOf(candidate(1, "a.mkv"), candidate(2, "b.mkv"))
        val vm = InteractiveImportViewModel(seriesId = 5, repository = repository)
        vm.pickFolder("/media/tv/Target Series/season-02")
        vm.toggleFile(2)

        vm.approve()

        assertEquals(ImportFlowMessage.Imported, vm.state.value.notice)
        val body = queue.reprocessedImports.single()
        assertEquals(5, body.seriesId)
        assertEquals(2, body.seasonNumber)
        assertEquals(1, body.id)
    }

    @Test
    fun `approve offline queues the import`() = kotlinx.coroutines.test.runTest {
        queue.manualImports += listOf(candidate(1, "a.mkv"))
        val vm = InteractiveImportViewModel(seriesId = 5, repository = repository)
        vm.pickFolder("/media/tv/Target Series/season-02")
        queue.error = { operation ->
            if (operation == "ManualImport.create") IOException("offline") else null
        }

        vm.approve()

        assertEquals(ImportFlowMessage.ImportQueued, vm.state.value.notice)
        assertEquals("api/v3/manualimport", actionQueue.pending().single().path)
    }

    @Test
    fun `consumeNotice clears the one-shot outcome`() {
        queue.manualImports += listOf(candidate(1, "a.mkv"))
        val vm = InteractiveImportViewModel(seriesId = 5, repository = repository)
        vm.pickFolder("/media/tv/Target Series/season-02")

        vm.approve()
        vm.consumeNotice()

        assertNull(vm.state.value.notice)
    }
}
