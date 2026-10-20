package dev.appolon.homelab.arr.feature.sonarr.queue.data

import dev.appolon.homelab.arr.api.sonarr.model.EpisodeResource
import dev.appolon.homelab.arr.api.sonarr.model.SeriesResource
import dev.appolon.homelab.arr.api.sonarr.FakeSonarrQueue
import dev.appolon.homelab.arr.api.sonarr.model.ManualImportResource
import dev.appolon.homelab.arr.core.ArrResult
import dev.appolon.homelab.arr.core.ServerId
import dev.appolon.homelab.arr.data.store.ActionQueueImpl
import dev.appolon.homelab.arr.feature.sonarr.queue.FakeEntityStore
import java.io.IOException
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SonarrManualImportRepositoryTest {

    private val serverId = ServerId("server-1")
    private lateinit var fake: FakeSonarrQueue
    private lateinit var store: FakeEntityStore
    private lateinit var actionQueue: ActionQueueImpl

    /** Mirrors the shape of the committed filesystem fixture: raw JSON. */
    private var fileSystem: (String?) -> JsonObject = { path ->
        val directories = buildJsonArray {
            add(
                buildJsonObject {
                    put("type", "folder")
                    put("name", "tv")
                    put("path", (path ?: "") + "/tv")
                },
            )
            add(
                buildJsonObject {
                    put("type", "file")
                    put("name", "notes.txt")
                    put("path", (path ?: "") + "/notes.txt")
                },
            )
            add(
                buildJsonObject {
                    put("type", "drive")
                    put("name", "media")
                    put("path", "/media")
                },
            )
        }
        buildJsonObject {
            path?.let { put("parent", "/") }
            put("directories", directories)
        }
    }

    private val repository: SonarrManualImportRepository
        get() = SonarrManualImportRepository(
            serverId = serverId,
            manualImportApi = fake.manualImport,
            fileSystemCall = { path, _ -> fileSystem(path) },
            actionQueue = actionQueue,
        )

    private fun importItem(
        id: Int,
        downloadId: String? = "dl-1",
        seriesId: Int = 5,
    ) = ManualImportResource(
        id = id,
        path = "/media/tv/show.$id.mkv",
        relativePath = "show.$id.mkv",
        downloadId = downloadId,
        series = SeriesResource(id = seriesId ?: 0, title = "Matched Show"),
        seasonNumber = 2,
        episodes = listOf(
            EpisodeResource(id = id + 100, seasonNumber = 2, episodeNumber = 5),
        ),
    )

    @Before
    fun setUp() {
        fake = FakeSonarrQueue()
        store = FakeEntityStore()
        actionQueue = ActionQueueImpl(store)
    }

    @Test
    fun `listFolders keeps folders and drives, drops files, and prepends the parent hop`() = runTest {
        val result = repository.listFolders("/media")

        assertTrue(result is ArrResult.Ok)
        val folders = (result as ArrResult.Ok).value
        assertEquals("/", folders.first().path)
        assertTrue(folders.first().isParent)
        assertEquals(listOf("tv", "media"), folders.drop(1).map { it.name })
    }

    @Test
    fun `listFolders without a parent has no parent row`() = runTest {
        val result = repository.listFolders(null)

        val folders = (result as ArrResult.Ok).value
        assertTrue(folders.none { it.isParent })
        assertEquals(2, folders.size)
    }

    @Test
    fun `listFolderItems and listDownloadItems hit the manual import endpoint`() = runTest {
        fake.manualImports += importItem(1)

        val byFolder = repository.listFolderItems("/media/tv")
        val byDownload = repository.listDownloadItems("dl-1", null)
        val byOtherDownload = repository.listDownloadItems("dl-2", null)

        assertEquals(listOf(1), (byFolder as ArrResult.Ok).value.map { it.id })
        assertEquals(listOf(1), (byDownload as ArrResult.Ok).value.map { it.id })
        assertTrue((byOtherDownload as ArrResult.Ok).value.isEmpty())
    }

    @Test
    fun `importSelections posts the reprocess payload with series and episode ids`() = runTest {
        fake.manualImports += importItem(1)

        val result = repository.importSelections(fake.manualImports.toList())

        assertTrue(result is ArrResult.Ok && result.value == QueueWriteOutcome.Synced)
        val posted = fake.reprocessedImports.single()
        assertEquals(5, posted.seriesId)
        assertEquals(listOf(101), posted.episodeIds)
        assertEquals(2, posted.seasonNumber)
    }

    @Test
    fun `importSelections offline enqueues the POST`() = runTest {
        fake.manualImports += importItem(1)
        fake.error = { op -> if (op == "ManualImport.create") IOException("offline") else null }

        val result = repository.importSelections(fake.manualImports.toList())

        assertTrue(result is ArrResult.Ok && result.value is QueueWriteOutcome.Queued)
        val pending = actionQueue.pending().single()
        assertEquals("POST", pending.method)
        assertEquals("api/v3/manualimport", pending.path)
        assertTrue(pending.bodyJson!!.contains("\"seriesId\":5"))
        assertTrue(pending.bodyJson!!.contains("\"episodeIds\":[101]"))
    }

    @Test
    fun `matched helpers read the embedded series and episode objects`() {
        val matched = importItem(1)
        val unmatched = importItem(2, seriesId = 0)

        assertTrue(matched.isMatched())
        assertEquals("Matched Show", matched.seriesTitleOrNull())
        assertEquals("S02E05", matched.matchedEpisodeLabel())
        assertFalse(unmatched.isMatched())
    }
}
