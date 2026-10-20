package dev.appolon.homelab.arr.feature.radarr.search.data

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
import dev.appolon.homelab.arr.core.ArrResult
import dev.appolon.homelab.arr.core.ServerId
import dev.appolon.homelab.arr.data.store.ActionQueueImpl
import dev.appolon.homelab.arr.feature.radarr.library.data.WriteOutcome
import dev.appolon.homelab.arr.feature.radarr.search.FakeSearchEntityStore
import dev.appolon.homelab.arr.feature.radarr.search.RecordingManualImport
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class RadarrInteractiveImportRepositoryTest {

    private val serverId = ServerId("server-1")
    private lateinit var queue: FakeRadarrQueue
    private lateinit var manualImport: RecordingManualImport
    private lateinit var store: FakeSearchEntityStore
    private lateinit var actionQueue: ActionQueueImpl
    private var movieFetches = 0
    private val movies = mutableMapOf<Int, MovieResource>()
    private var profile: QualityProfileResource? = null
    private val languages = mutableListOf<LanguageResource>()
    private val fileSystem: (String?) -> FileSystemResult = { path ->
        FileSystemResult(
            parent = path?.let { "/" },
            directories = listOf(
                FileSystemModel(
                    type = FileSystemEntityType.FOLDER,
                    name = "movies",
                    path = (path ?: "") + "/movies",
                ),
                FileSystemModel(
                    type = FileSystemEntityType.FILE,
                    name = "notes.txt",
                    path = (path ?: "") + "/notes.txt",
                ),
            ),
        )
    }

    private val repository: RadarrInteractiveImportRepository
        get() = RadarrInteractiveImportRepository(
            serverId = serverId,
            store = store,
            manualImportApi = manualImport,
            fileSystemCall = { path, _ -> fileSystem(path) },
            movieById = { id ->
                movieFetches += 1
                movies[id]
            },
            languageTable = { languages.toList() },
            qualityProfileById = { profile },
            actionQueue = actionQueue,
            io = Dispatchers.Unconfined,
        )

    @Before
    fun setUp() {
        queue = FakeRadarrQueue()
        manualImport = RecordingManualImport(queue)
        store = FakeSearchEntityStore()
        actionQueue = ActionQueueImpl(store)
        movies[5] = MovieResource(
            id = 5,
            title = "Target Movie",
            path = "/media/movies/Target Movie",
            qualityProfileId = 2,
        )
        profile = QualityProfileResource(
            id = 2,
            items = listOf(
                QualityProfileQualityItemResource(
                    quality = Quality(id = 7, name = "WEBDL-1080p"),
                    allowed = true,
                ),
                QualityProfileQualityItemResource(
                    quality = Quality(id = 8, name = "Bluray-1080p"),
                    allowed = false,
                ),
                QualityProfileQualityItemResource(
                    name = "Group 720p",
                    items = listOf(
                        QualityProfileQualityItemResource(
                            quality = Quality(id = 9, name = "HDTV-720p"),
                            allowed = true,
                        ),
                    ),
                ),
            ),
        )
        languages += listOf(
            LanguageResource(id = 0, name = "unknown"),
            LanguageResource(id = 1, name = "English"),
            LanguageResource(id = 3, name = "German"),
        )
    }

    @Test
    fun `listFolders keeps folders drops files and prepends the parent hop`() = runTest {
        val result = repository.listFolders("/media")

        assertTrue(result is ArrResult.Ok)
        val folders = (result as ArrResult.Ok).value
        assertEquals("/", folders.first().path)
        assertTrue(folders.first().isParent)
        assertEquals(listOf("movies"), folders.drop(1).map { it.name })
    }

    @Test
    fun `listFiles returns the folder candidates`() = runTest {
        queue.importItems += file(id = 1, path = "/media/movies/a.mkv", qualityId = 7)

        val result = repository.listFiles("/media/movies")

        assertEquals(listOf(1), (result as ArrResult.Ok).value.map { it.id })
    }

    @Test
    fun `movie is cache-first across calls`() = runTest {
        val first = repository.movie(5)
        repository.movie(5)

        assertTrue(first is ArrResult.Ok)
        assertEquals(1, movieFetches)
    }

    @Test
    fun `qualityOptions flattens groups and keeps only allowed leaves`() = runTest {
        val options = repository.qualityOptions(movies.getValue(5))

        assertEquals(listOf(7, 9), options.map { it.id })
    }

    @Test
    fun `languageOptions drops the unknown row and sorts by name`() = runTest {
        val options = repository.languageOptions()

        assertEquals(listOf("English", "German"), options.mapNotNull { it.name })
    }

    @Test
    fun `importSelections posts per-file quality and language and forces the movie`() = runTest {
        val resource = file(id = 1, path = "/media/movies/a.mkv", qualityId = 7)

        val outcome = repository.importSelections(
            movie = movies.getValue(5),
            decisions = listOf(
                ImportDecision(
                    resource = resource,
                    quality = Quality(id = 9, name = "HDTV-720p"),
                    language = Language(id = 3, name = "German"),
                    selected = true,
                ),
            ),
        )

        val ok = outcome as ArrResult.Ok
        assertEquals(WriteOutcome.Synced, ok.value)
        val posted = manualImport.posted.single().single()
        assertEquals(9, posted.quality.quality?.id)
        assertEquals(1, posted.quality.revision?.version)
        assertEquals(listOf(3), posted.languages?.map { it.id })
        assertEquals(5, posted.movieId)
        assertEquals(5, posted.movie.id)
    }

    @Test
    fun `importSelections keeps parsed values when no decision was made`() = runTest {
        val resource = file(id = 2, path = "/media/movies/b.mkv", qualityId = 7)

        repository.importSelections(
            movie = movies.getValue(5),
            decisions = listOf(
                ImportDecision(
                    resource = resource,
                    quality = null,
                    language = null,
                    selected = true,
                ),
            ),
        )

        val posted = manualImport.posted.single().single()
        assertEquals(7, posted.quality.quality?.id)
        assertEquals(listOf(1), posted.languages?.map { it.id })
    }

    @Test
    fun `importSelections offline enqueues the POST`() = runTest {
        queue.errorFor = { op ->
            if (op == "reprocessManualImport") IOException("offline") else null
        }
        val resource = file(id = 1, path = "/media/movies/a.mkv", qualityId = 7)

        val outcome = repository.importSelections(
            movie = movies.getValue(5),
            decisions = listOf(
                ImportDecision(
                    resource = resource,
                    quality = null,
                    language = null,
                    selected = true,
                ),
            ),
        )

        val ok = outcome as ArrResult.Ok
        assertTrue(ok.value is WriteOutcome.Queued)
        val pending = actionQueue.pending().single()
        assertEquals("POST", pending.method)
        assertEquals("api/v3/manualimport", pending.path)
        assertTrue(pending.bodyJson!!.contains("\"movieId\":5"))
        assertTrue(pending.bodyJson!!.contains("\"quality\":{\"quality\":{\"id\":7"))
    }

    private fun file(id: Int, path: String, qualityId: Int) = ManualImportResource(
        id = id,
        path = path,
        relativePath = path.substringAfterLast('/'),
        size = 4_000_000_000,
        quality = QualityModel(
            quality = Quality(id = qualityId, name = "WEBDL-1080p"),
            revision = dev.appolon.homelab.arr.api.radarr.model.Revision(version = 1),
        ),
        languages = listOf(Language(id = 1, name = "English")),
        downloadId = null,
    )
}
