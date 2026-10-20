package dev.appolon.homelab.arr.feature.lidarr.search.vm

import dev.appolon.homelab.arr.api.lidarr.FakeLidarrQueue
import dev.appolon.homelab.arr.api.lidarr.model.AlbumResource
import dev.appolon.homelab.arr.api.lidarr.model.ArtistResource
import dev.appolon.homelab.arr.api.lidarr.model.ManualImportResource
import dev.appolon.homelab.arr.api.lidarr.model.Quality
import dev.appolon.homelab.arr.api.lidarr.model.QualityModel
import dev.appolon.homelab.arr.api.lidarr.model.QualityProfileQualityItemResource
import dev.appolon.homelab.arr.api.lidarr.model.QualityProfileResource
import dev.appolon.homelab.arr.api.lidarr.model.TrackResource
import dev.appolon.homelab.arr.core.ServerId
import dev.appolon.homelab.arr.data.store.ActionQueueImpl
import dev.appolon.homelab.arr.feature.lidarr.search.FakeSearchEntityStore
import dev.appolon.homelab.arr.feature.lidarr.search.data.LidarrInteractiveImportRepository
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class InteractiveImportViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private val fake = FakeLidarrQueue()
    private val store = FakeSearchEntityStore()
    private val actionQueue = ActionQueueImpl(store)
    private val artist = ArtistResource(
        id = 5,
        artistName = "Target",
        path = "/media/music/Target",
        qualityProfileId = 2,
    )

    private val repository = LidarrInteractiveImportRepository(
        serverId = ServerId("server-1"),
        store = store,
        manualImportApi = fake,
        fileSystemCall = { path, _ -> listing(path) },
        artistById = { if (it == 5) artist else null },
        albumById = { id -> AlbumResource(id = id, title = "Album", artistId = 5) },
        qualityProfileById = {
            QualityProfileResource(
                id = 2,
                items = listOf(
                    QualityProfileQualityItemResource(
                        allowed = true,
                        quality = Quality(id = 7, name = "FLAC"),
                    ),
                    QualityProfileQualityItemResource(
                        allowed = true,
                        quality = Quality(id = 9, name = "MP3-320"),
                    ),
                ),
            )
        },
        actionQueue = actionQueue,
        io = Dispatchers.Unconfined,
    )

    private fun listing(path: String?): JsonObject = buildJsonObject {
        path?.let { put("parent", "/media/music") }
        put(
            "directories",
            kotlinx.serialization.json.buildJsonArray {
                add(
                    buildJsonObject {
                        put("type", "folder")
                        put("name", "Target")
                        put("path", "/media/music/Target")
                    },
                )
            },
        )
    }

    private fun candidate(
        id: Int,
        name: String,
        complete: Boolean = true,
    ) = ManualImportResource(
        id = id,
        path = "/media/music/Target/$name",
        name = name,
        size = 45_000_000L,
        artist = ArtistResource(id = 5, artistName = "Target"),
        album = AlbumResource(id = 9, title = "Album"),
        albumReleaseId = if (complete) 3 else 0,
        tracks = listOf(TrackResource(id = 100 + id, trackNumber = "$id")),
        quality = QualityModel(quality = Quality(id = 7, name = "FLAC")),
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun vm() = InteractiveImportViewModel(artistId = 5, repository = repository)

    @Test
    fun `loading the artist starts browsing its own folder`() {
        val state = vm().state.value

        assertEquals(ImportFlowLoad.Ready, state.artistLoad)
        assertEquals("/media/music/Target", state.folder)
        assertEquals(ImportFlowLoad.Ready, state.foldersLoad)
        assertEquals(2, state.qualityOptions.size)
    }

    @Test
    fun `picking a folder preselects complete matches only`() {
        fake.manualImports.putAll(
            listOf(
                candidate(1, "01.flac"),
                candidate(2, "02.flac", complete = false),
            ).associateBy { it.id },
        )
        val viewModel = vm()

        viewModel.pickFolder("/media/music/Target")

        val state = viewModel.state.value
        assertEquals(ImportFlowLoad.Ready, state.filesLoad)
        assertTrue(state.files.first { it.id == 1 }.selected)
        assertFalse(state.files.first { it.id == 2 }.selected)
        assertFalse(state.files.first { it.id == 2 }.matched)
    }

    @Test
    fun `unmatched files refuse the toggle`() {
        val unmatched = candidate(2, "02.flac", complete = false)
        fake.manualImports[unmatched.id] = unmatched
        val viewModel = vm()
        viewModel.pickFolder("/media/music/Target")

        viewModel.toggleFile(2)
        viewModel.selectAll(true)

        assertFalse(viewModel.state.value.files.single().selected)
    }

    @Test
    fun `applyDecision records the quality and reselects the file`() {
        val matched = candidate(1, "01.flac")
        fake.manualImports[matched.id] = matched
        val viewModel = vm()
        viewModel.pickFolder("/media/music/Target")
        viewModel.toggleFile(1)

        viewModel.applyDecision(1, Quality(id = 9, name = "MP3-320"))

        val file = viewModel.state.value.files.single()
        assertTrue(file.selected)
        assertEquals("MP3-320", file.qualityName)
        assertNull(viewModel.state.value.editingFileId)
    }

    @Test
    fun `approve imports the selected files`() {
        fake.manualImports.putAll(
            listOf(
                candidate(1, "01.flac"),
                candidate(2, "02.flac"),
            ).associateBy { it.id },
        )
        val viewModel = vm()
        viewModel.pickFolder("/media/music/Target")
        viewModel.toggleFile(2)

        viewModel.approve()

        assertEquals(ImportFlowMessage.Imported, viewModel.state.value.notice)
        assertEquals(1, fake.manualImportUpdates.size)
        assertEquals(1, fake.manualImportUpdates.single().id)
    }

    @Test
    fun `approve offline queues the import`() = runTest {
        val matched = candidate(1, "01.flac")
        fake.manualImports[matched.id] = matched
        val viewModel = vm()
        viewModel.pickFolder("/media/music/Target")
        fake.error = { operation ->
            if (operation == "updateManualImport") IOException("offline") else null
        }

        viewModel.approve()

        assertEquals(ImportFlowMessage.ImportQueued, viewModel.state.value.notice)
        assertEquals("api/v1/manualimport", actionQueue.pending().single().path)
    }

    @Test
    fun `folder failures surface and retry recovers`() {
        val viewModel = vm()
        fake.error = { operation ->
            if (operation == "getManualImport") IOException("offline") else null
        }
        viewModel.pickFolder("/media/music/Target")
        assertTrue(viewModel.state.value.filesLoad is ImportFlowLoad.Failed)

        fake.error = { null }
        viewModel.retryFiles()

        assertEquals(ImportFlowLoad.Ready, viewModel.state.value.filesLoad)
    }

    @Test
    fun `consumeNotice clears the one-shot outcome`() {
        val matched = candidate(1, "01.flac")
        fake.manualImports[matched.id] = matched
        val viewModel = vm()
        viewModel.pickFolder("/media/music/Target")
        viewModel.approve()

        viewModel.consumeNotice()

        assertNull(viewModel.state.value.notice)
    }
}
