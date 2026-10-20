package dev.appolon.homelab.arr.feature.radarr.search.vm

import dev.appolon.homelab.arr.api.radarr.FakeRadarrQueue
import dev.appolon.homelab.arr.api.radarr.model.FileSystemEntityType
import dev.appolon.homelab.arr.api.radarr.model.FileSystemModel
import dev.appolon.homelab.arr.api.radarr.model.FileSystemResult
import dev.appolon.homelab.arr.api.radarr.model.Language
import dev.appolon.homelab.arr.api.radarr.model.LanguageResource
import dev.appolon.homelab.arr.api.radarr.model.ManualImportResource
import dev.appolon.homelab.arr.api.radarr.model.MovieResource
import dev.appolon.homelab.arr.api.radarr.model.Quality
import dev.appolon.homelab.arr.api.radarr.model.QualityModel
import dev.appolon.homelab.arr.api.radarr.model.QualityProfileQualityItemResource
import dev.appolon.homelab.arr.api.radarr.model.QualityProfileResource
import dev.appolon.homelab.arr.data.testing.FakeEntityStore
import dev.appolon.homelab.arr.feature.radarr.search.data.RadarrInteractiveImportRepository
import dev.appolon.homelab.arr.feature.radarr.search.RecordingManualImport
import dev.appolon.homelab.arr.core.ServerId
import dev.appolon.homelab.arr.data.store.ActionQueueImpl
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class InteractiveImportViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private val queue = FakeRadarrQueue()
    private val manualImport = RecordingManualImport(queue)
    private val store = FakeEntityStore()
    private val actionQueue = ActionQueueImpl(store)

    private val repository = RadarrInteractiveImportRepository(
        serverId = ServerId("server-1"),
        store = store,
        manualImportApi = manualImport,
        fileSystemCall = { path, _ ->
            FileSystemResult(
                parent = path?.let { "/" },
                directories = listOf(
                    FileSystemModel(
                        type = FileSystemEntityType.FOLDER,
                        name = "movies",
                        path = (path ?: "") + "/movies",
                    ),
                ),
            )
        },
        movieById = {
            MovieResource(
                id = 5,
                title = "Target Movie",
                path = "/media/movies/target",
                qualityProfileId = 2,
            )
        },
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
                    QualityProfileQualityItemResource(
                        quality = Quality(id = 9, name = "HDTV-720p"),
                        allowed = true,
                    ),
                ),
            )
        },
        actionQueue = actionQueue,
        io = Dispatchers.Unconfined,
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        queue.importItems += ManualImportResource(
            id = 1,
            path = "/media/movies/target/movie.mkv",
            relativePath = "movie.mkv",
            size = 8_000_000_000,
            quality = QualityModel(quality = Quality(id = 7, name = "WEBDL-1080p")),
            languages = listOf(Language(id = 1, name = "English")),
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `init loads the movie its options and browses its folder`() {
        val state = InteractiveImportViewModel(5, repository).state.value

        assertEquals("Target Movie", state.movie?.title)
        assertEquals("/media/movies/target", state.folder)
        assertEquals(2, state.qualityOptions.size)
        assertEquals(listOf("English", "German"), state.languageOptions.mapNotNull { it.name })
        assertEquals(ImportFlowLoad.Ready, state.foldersLoad)
    }

    @Test
    fun `pickFolder lists the files with every file selected`() {
        val viewModel = InteractiveImportViewModel(5, repository)

        viewModel.pickFolder("/media/movies/target")

        val state = viewModel.state.value
        assertEquals(1, state.files.size)
        assertTrue(state.files.all { it.selected })
        assertEquals("movie.mkv", state.files.single().name)
        assertEquals(1, state.selectedCount)
    }

    @Test
    fun `applyDecision updates the file row and keeps it selected`() {
        val viewModel = InteractiveImportViewModel(5, repository)
        viewModel.pickFolder("/media/movies/target")

        viewModel.openEditor(1)
        viewModel.applyDecision(
            1,
            Quality(id = 9, name = "HDTV-720p"),
            Language(id = 3, name = "German"),
        )

        val file = viewModel.state.value.files.single()
        assertEquals("HDTV-720p", file.qualityName)
        assertEquals("German", file.languageName)
        assertTrue(file.selected)
        assertEquals(null, viewModel.state.value.editingFileId)
    }

    @Test
    fun `approve posts the decisions and reports the imported notice`() {
        val viewModel = InteractiveImportViewModel(5, repository)
        viewModel.pickFolder("/media/movies/target")
        viewModel.applyDecision(
            1,
            Quality(id = 9, name = "HDTV-720p"),
            Language(id = 3, name = "German"),
        )

        viewModel.approve()

        assertEquals(ImportFlowMessage.Imported, viewModel.state.value.notice)
        val posted = manualImport.posted.single().single()
        assertEquals(9, posted.quality.quality?.id)
        assertEquals(listOf(3), posted.languages?.map { it.id })
        assertEquals(5, posted.movieId)
    }

    @Test
    fun `approve offline enqueues and reports the queued notice`() =
        kotlinx.coroutines.test.runTest {
            val viewModel = InteractiveImportViewModel(5, repository)
            viewModel.pickFolder("/media/movies/target")
            queue.errorFor = { op ->
                if (op == "reprocessManualImport") IOException("offline") else null
            }

            viewModel.approve()

            assertEquals(ImportFlowMessage.ImportQueued, viewModel.state.value.notice)
            assertEquals("api/v3/manualimport", actionQueue.pending().single().path)
        }

    @Test
    fun `toggle and selectAll drive the selection count`() {
        val viewModel = InteractiveImportViewModel(5, repository)
        viewModel.pickFolder("/media/movies/target")

        viewModel.toggleFile(1)
        assertEquals(0, viewModel.state.value.selectedCount)

        viewModel.selectAll(true)
        assertEquals(1, viewModel.state.value.selectedCount)
    }
}
