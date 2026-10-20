package dev.appolon.homelab.arr.feature.lidarr.queue.data

import dev.appolon.homelab.arr.api.lidarr.FakeLidarrQueue
import dev.appolon.homelab.arr.api.lidarr.model.AlbumResource
import dev.appolon.homelab.arr.api.lidarr.model.ArtistResource
import dev.appolon.homelab.arr.api.lidarr.model.ManualImportResource
import dev.appolon.homelab.arr.api.lidarr.model.TrackResource
import dev.appolon.homelab.arr.core.ArrResult
import dev.appolon.homelab.arr.core.ServerId
import dev.appolon.homelab.arr.data.store.ActionQueueImpl
import dev.appolon.homelab.arr.feature.lidarr.queue.FakeEntityStore
import java.io.IOException
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class LidarrManualImportRepositoryTest {

    private val serverId = ServerId("server-1")
    private lateinit var fake: FakeLidarrQueue
    private lateinit var store: FakeEntityStore
    private lateinit var actionQueue: ActionQueueImpl

    private fun fileSystem(path: String?): JsonObject {
        val directories = buildJsonArray {
            add(
                buildJsonObject {
                    put("type", "folder")
                    put("name", "music")
                    put("path", (path ?: "") + "/music")
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
        return buildJsonObject {
            path?.let { put("parent", "/") }
            put("directories", directories)
        }
    }

    private val repository: LidarrManualImportRepository
        get() = LidarrManualImportRepository(
            serverId = serverId,
            manualImportApi = fake,
            fileSystemCall = { path, _ -> fileSystem(path) },
            actionQueue = actionQueue,
        )

    private fun importItem(
        id: Int,
        downloadId: String? = "dl-1",
        artistId: Int = 5,
        albumId: Int = 7,
    ) = ManualImportResource(
        id = id,
        path = "/media/music/artist/track-$id.flac",
        name = "track-$id.flac",
        size = 1_000L,
        downloadId = downloadId,
        artist = ArtistResource(id = artistId, artistName = "Matched Artist"),
        album = AlbumResource(id = albumId, title = "Matched Album"),
        albumReleaseId = 9,
        tracks = listOf(TrackResource(id = id + 100)),
    )

    @Before
    fun setUp() {
        fake = FakeLidarrQueue()
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
        assertEquals(listOf("music", "media"), folders.drop(1).map { it.name })
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
        fake.manualImports[1] = importItem(1)

        val byFolder = repository.listFolderItems("/media/music/artist")
        val byDownload = repository.listDownloadItems("dl-1", null)
        val byOtherDownload = repository.listDownloadItems("dl-2", null)

        assertEquals(listOf(1), (byFolder as ArrResult.Ok).value.map { it.id })
        assertEquals(listOf(1), (byDownload as ArrResult.Ok).value.map { it.id })
        assertTrue((byOtherDownload as ArrResult.Ok).value.isEmpty())
    }

    @Test
    fun `importSelections posts the update payload with artist, album, and track ids`() = runTest {
        fake.manualImports[1] = importItem(1)

        val result = repository.importSelections(fake.manualImports.values.toList())

        assertTrue(result is ArrResult.Ok && result.value == QueueWriteOutcome.Synced)
        val posted = fake.manualImportUpdates.single()
        assertEquals(5, posted.artistId)
        assertEquals(7, posted.albumId)
        assertEquals(9, posted.albumReleaseId)
        assertEquals(listOf(101), posted.trackIds)
    }

    @Test
    fun `importSelections offline enqueues the POST`() = runTest {
        fake.manualImports[1] = importItem(1)
        fake.error = { op -> if (op == "updateManualImport") IOException("offline") else null }

        val result = repository.importSelections(fake.manualImports.values.toList())

        assertTrue(result is ArrResult.Ok && result.value is QueueWriteOutcome.Queued)
        val pending = actionQueue.pending().single()
        assertEquals("POST", pending.method)
        assertEquals("api/v1/manualimport", pending.path)
        assertTrue(pending.bodyJson!!.contains("\"artistId\":5"))
        assertTrue(pending.bodyJson!!.contains("\"albumReleaseId\":9"))
        assertTrue(pending.bodyJson!!.contains("\"trackIds\":[101]"))
    }

    @Test
    fun `matched helpers read the embedded artist, album, and release`() {
        assertTrue(importItem(1).isMatched())
        assertFalse(importItem(1, artistId = 0).isMatched())
        assertFalse(importItem(1, albumId = 0).isMatched())
        assertFalse(importItem(1).copy(albumReleaseId = 0).isMatched())
        assertEquals("Matched Artist", importItem(1).artistTitleOrNull())
        assertEquals("Matched Album", importItem(1).albumTitleOrNull())
        assertEquals(null, importItem(1).copy(artist = null).artistTitleOrNull())
    }

    @Test
    fun `folder listing failures map to network errors`() = runTest {
        val failing = LidarrManualImportRepository(
            serverId = serverId,
            manualImportApi = fake,
            fileSystemCall = { _, _ -> throw IOException("offline") },
            actionQueue = actionQueue,
        )

        val result = failing.listFolders("/media")

        assertTrue(result is ArrResult.Err)
    }
}
