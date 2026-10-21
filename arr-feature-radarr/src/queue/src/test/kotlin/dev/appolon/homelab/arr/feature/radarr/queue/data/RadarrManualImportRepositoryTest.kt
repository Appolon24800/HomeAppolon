package dev.appolon.homelab.arr.feature.radarr.queue.data

import dev.appolon.homelab.arr.api.radarr.FakeRadarrQueue
import dev.appolon.homelab.arr.api.radarr.model.FileSystemEntityType
import dev.appolon.homelab.arr.api.radarr.model.FileSystemModel
import dev.appolon.homelab.arr.api.radarr.model.FileSystemResult
import dev.appolon.homelab.arr.api.radarr.model.ManualImportResource
import dev.appolon.homelab.arr.api.radarr.model.MovieResource
import dev.appolon.homelab.arr.core.ArrResult
import dev.appolon.homelab.arr.core.ServerId
import dev.appolon.homelab.arr.data.store.ActionQueueImpl
import dev.appolon.homelab.arr.data.testing.FakeEntityStore
import java.io.IOException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class RadarrManualImportRepositoryTest {

    private val serverId = ServerId("server-1")
    private lateinit var fake: FakeRadarrQueue
    private lateinit var store: FakeEntityStore
    private lateinit var actionQueue: ActionQueueImpl
    private var fileSystem: (String?) -> FileSystemResult = { path ->
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

    private val repository: RadarrManualImportRepository
        get() = RadarrManualImportRepository(
            serverId = serverId,
            manualImportApi = fake,
            fileSystemCall = { path, _ -> fileSystem(path) },
            actionQueue = actionQueue,
        )

    @Before
    fun setUp() {
        fake = FakeRadarrQueue()
        store = FakeEntityStore()
        actionQueue = ActionQueueImpl(store)
    }

    @Test
    fun `listFolders keeps folders, drops files, and prepends the parent hop`() = runTest {
        val result = repository.listFolders("/media")

        assertTrue(result is ArrResult.Ok)
        val folders = (result as ArrResult.Ok).value
        assertEquals("/", folders.first().path)
        assertTrue(folders.first().isParent)
        assertEquals(listOf("movies"), folders.drop(1).map { it.name })
    }

    @Test
    fun `listFolders without a parent has no parent row`() = runTest {
        val result = repository.listFolders(null)

        val folders = (result as ArrResult.Ok).value
        assertTrue(folders.none { it.isParent })
    }

    @Test
    fun `listFolderItems and listDownloadItems hit the manual import endpoint`() = runTest {
        fake.importItems += ManualImportResource(
            id = 1,
            path = "/media/movies/a.mkv",
            relativePath = "a.mkv",
            folderName = "/media/movies",
            downloadId = "dl-1",
            movie = MovieResource(id = 5, title = "Matched Movie"),
        )

        val byFolder = repository.listFolderItems("/media/movies")
        val byDownload = repository.listDownloadItems("dl-1", null)

        assertEquals(listOf(1), (byFolder as ArrResult.Ok).value.map { it.id })
        assertEquals(listOf(1), (byDownload as ArrResult.Ok).value.map { it.id })
    }

    @Test
    fun `importSelections posts the reprocess payload`() = runTest {
        fake.importItems += ManualImportResource(
            id = 1,
            path = "/media/movies/a.mkv",
            downloadId = "dl-1",
            movie = MovieResource(id = 5, title = "Matched Movie"),
        )

        val result = repository.importSelections(fake.importItems.toList())

        assertTrue(result is ArrResult.Ok && result.value == QueueWriteOutcome.Synced)
        assertTrue(fake.importItems.isNotEmpty())
    }

    @Test
    fun `importSelections offline enqueues the POST`() = runTest {
        fake.importItems += ManualImportResource(
            id = 1,
            path = "/media/movies/a.mkv",
            movie = MovieResource(id = 5, title = "Matched Movie"),
        )
        fake.errorFor = { op -> if (op == "reprocessManualImport") IOException("offline") else null }

        val result = repository.importSelections(fake.importItems.toList())

        assertTrue(result is ArrResult.Ok && result.value is QueueWriteOutcome.Queued)
        val pending = actionQueue.pending().single()
        assertEquals("POST", pending.method)
        assertEquals("api/v3/manualimport", pending.path)
        assertTrue(pending.bodyJson!!.contains("\"movieId\":5"))
    }
}
