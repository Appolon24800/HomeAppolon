package dev.appolon.homelab.arr.feature.lidarr.search.data

import dev.appolon.homelab.arr.api.lidarr.FakeLidarrQueue
import dev.appolon.homelab.arr.api.lidarr.model.AlbumResource
import dev.appolon.homelab.arr.api.lidarr.model.ArtistResource
import dev.appolon.homelab.arr.api.lidarr.model.ManualImportResource
import dev.appolon.homelab.arr.api.lidarr.model.Quality
import dev.appolon.homelab.arr.api.lidarr.model.QualityModel
import dev.appolon.homelab.arr.api.lidarr.model.QualityProfileQualityItemResource
import dev.appolon.homelab.arr.api.lidarr.model.QualityProfileResource
import dev.appolon.homelab.arr.api.lidarr.model.Rejection
import dev.appolon.homelab.arr.api.lidarr.model.TrackResource
import dev.appolon.homelab.arr.core.ArrResult
import dev.appolon.homelab.arr.core.ServerId
import dev.appolon.homelab.arr.data.store.ActionQueueImpl
import dev.appolon.homelab.arr.feature.lidarr.library.data.WriteOutcome
import dev.appolon.homelab.arr.data.testing.FakeEntityStore
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response

class LidarrInteractiveImportRepositoryTest {

    private val serverId = ServerId("server-1")
    private lateinit var queue: FakeLidarrQueue
    private lateinit var store: FakeEntityStore
    private lateinit var actionQueue: ActionQueueImpl
    private var artistFetches = 0
    private var albumFetches = 0
    private val artistsById = mutableMapOf<Int, ArtistResource>()
    private val albumsById = mutableMapOf<Int, AlbumResource>()
    private var profile: QualityProfileResource? = null
    private val folderNames = mutableListOf<Pair<String, String>>()
    private var fileSystemError: Throwable? = null

    private val repository: LidarrInteractiveImportRepository
        get() = LidarrInteractiveImportRepository(
            serverId = serverId,
            store = store,
            manualImportApi = queue,
            fileSystemCall = { path, _ ->
                fileSystemError?.let { throw it }
                listing(path)
            },
            artistById = { id ->
                artistFetches += 1
                artistsById[id]
            },
            albumById = { id ->
                albumFetches += 1
                albumsById[id]
            },
            qualityProfileById = { profile },
            actionQueue = actionQueue,
            io = Dispatchers.Unconfined,
        )

    /** Raw filesystem listing, as the schema-less endpoint returns it. */
    private fun listing(path: String?): JsonObject = buildJsonObject {
        path?.let { put("parent", "/") }
        put(
            "directories",
            kotlinx.serialization.json.buildJsonArray {
                folderNames.forEach { (type, name) ->
                    add(
                        buildJsonObject {
                            put("type", type)
                            put("name", name)
                            put("path", (path ?: "") + "/" + name)
                        },
                    )
                }
            },
        )
    }

    private fun candidate(
        id: Int,
        path: String,
        artistId: Int = 5,
        albumId: Int = 9,
        complete: Boolean = true,
        trackCount: Int = 2,
    ) = ManualImportResource(
        id = id,
        path = path,
        name = path.substringAfterLast('/'),
        size = 45_000_000L,
        artist = ArtistResource(id = artistId, artistName = if (artistId == 5) "Target" else "Other"),
        album = AlbumResource(id = albumId, title = "Album"),
        albumReleaseId = if (complete) 3 else 0,
        tracks = (1..trackCount).map { TrackResource(id = 100 + it, trackNumber = "$it") },
        quality = QualityModel(quality = Quality(id = 7, name = "FLAC")),
        rejections = listOf(Rejection(reason = "Existing file")),
    )

    @Before
    fun setUp() {
        queue = FakeLidarrQueue()
        store = FakeEntityStore()
        actionQueue = ActionQueueImpl(store)
        artistsById[5] = ArtistResource(
            id = 5,
            artistName = "Target",
            path = "/media/music/Target",
            qualityProfileId = 2,
        )
        albumsById[9] = AlbumResource(id = 9, title = "Album", artistId = 5)
        profile = QualityProfileResource(
            id = 2,
            items = listOf(
                QualityProfileQualityItemResource(
                    allowed = true,
                    quality = Quality(id = 7, name = "FLAC"),
                ),
                QualityProfileQualityItemResource(
                    allowed = false,
                    quality = Quality(id = 6, name = "MP3-192"),
                ),
                QualityProfileQualityItemResource(
                    items = listOf(
                        QualityProfileQualityItemResource(
                            allowed = true,
                            quality = Quality(id = 9, name = "MP3-320"),
                        ),
                    ),
                ),
            ),
        )
    }

    @Test
    fun `listFolders returns the parent hop plus folders only`() = runTest {
        folderNames += listOf("folder" to "Disc 1", "file" to "cover.jpg")

        val result = repository.listFolders("/media/music/Target")

        val ok = result as ArrResult.Ok
        assertEquals(2, ok.value.size)
        assertTrue(ok.value.first().isParent)
        assertEquals("/media/music/Target/Disc 1", ok.value[1].path)
        assertEquals("Disc 1", ok.value[1].name)
    }

    @Test
    fun `listFolders reports filesystem failures`() = runTest {
        fileSystemError = IOException("offline")

        val result = repository.listFolders(null)

        assertTrue(result is ArrResult.Err)
    }

    @Test
    fun `listFiles lists the folder's candidates online`() = runTest {
        queue.manualImports.putAll(
            listOf(
                candidate(1, "/media/music/Target/01.flac"),
                candidate(2, "/media/music/Target/02.flac"),
            ).associateBy { it.id },
        )

        val result = repository.listFiles("/media/music/Target")

        assertTrue(result is ArrResult.Ok && (result as ArrResult.Ok).value.size == 2)
    }

    @Test
    fun `artist is cache-first and writes the fetch through`() = runTest {
        val first = repository.artist(5)
        val second = repository.artist(5)

        assertTrue(first is ArrResult.Ok && (first as ArrResult.Ok).value.id == 5)
        assertTrue(second is ArrResult.Ok)
        assertEquals(1, artistFetches)
    }

    @Test
    fun `artist reports a missing id as an error`() = runTest {
        val result = repository.artist(99)

        assertTrue(result is ArrResult.Err)
    }

    @Test
    fun `album is cache-first for the album scope`() = runTest {
        val first = repository.album(9)
        val second = repository.album(9)

        assertTrue(first is ArrResult.Ok && (first as ArrResult.Ok).value.artistId == 5)
        assertEquals(1, albumFetches)
        assertTrue(second is ArrResult.Ok)
    }

    @Test
    fun `qualityOptions flattens groups and drops disallowed qualities`() = runTest {
        val options = repository.qualityOptions(artistsById.getValue(5))

        assertEquals(listOf(7, 9), options.map { it.id })
    }

    @Test
    fun `importSelections posts each decision with its quality override`() = runTest {
        val resource = candidate(1, "/media/music/Target/01.flac")
        queue.manualImports[resource.id] = resource

        val outcome = repository.importSelections(
            listOf(ImportDecision(resource = resource, quality = Quality(id = 9, name = "MP3-320"), selected = true)),
        )

        assertEquals(WriteOutcome.Synced, (outcome as ArrResult.Ok).value)
        val update = queue.manualImportUpdates.single()
        assertEquals(9, update.quality?.quality?.id)
        assertEquals(5, update.artistId)
        assertEquals(9, update.albumId)
        assertEquals(3, update.albumReleaseId)
        assertEquals(listOf(101, 102), update.trackIds)
    }

    @Test
    fun `importSelections keeps the parsed quality without an override`() = runTest {
        val resource = candidate(1, "/media/music/Target/01.flac")
        queue.manualImports[resource.id] = resource

        repository.importSelections(
            listOf(ImportDecision(resource = resource, quality = null, selected = true)),
        )

        assertEquals(7, queue.manualImportUpdates.single().quality?.quality?.id)
    }

    @Test
    fun `importSelections offline enqueues the POST for replay`() = runTest {
        val resource = candidate(1, "/media/music/Target/01.flac")
        queue.manualImports[resource.id] = resource
        queue.error = { operation ->
            if (operation == "updateManualImport") IOException("offline") else null
        }

        val outcome = repository.importSelections(
            listOf(ImportDecision(resource = resource, quality = null, selected = true)),
        )

        assertTrue(
            (outcome as ArrResult.Ok).value is WriteOutcome.Queued,
        )
        val pending = actionQueue.pending().single()
        assertEquals("POST", pending.method)
        assertEquals("api/v1/manualimport", pending.path)
    }

    @Test
    fun `importSelections does not queue terminal failures`() = runTest {
        val resource = candidate(1, "/media/music/Target/01.flac")
        queue.manualImports[resource.id] = resource
        queue.error = { operation ->
            if (operation == "updateManualImport") {
                HttpException(Response.error<Unit>(401, "{}".toResponseBody()))
            } else {
                null
            }
        }

        val outcome = repository.importSelections(
            listOf(ImportDecision(resource = resource, quality = null, selected = true)),
        )

        assertTrue(outcome is ArrResult.Err)
        assertTrue(actionQueue.pending().isEmpty())
    }

    @Test
    fun `an empty selection is a no-op sync`() = runTest {
        val outcome = repository.importSelections(emptyList())

        assertEquals(WriteOutcome.Synced, (outcome as ArrResult.Ok).value)
        assertTrue(queue.manualImportUpdates.isEmpty())
    }
}
