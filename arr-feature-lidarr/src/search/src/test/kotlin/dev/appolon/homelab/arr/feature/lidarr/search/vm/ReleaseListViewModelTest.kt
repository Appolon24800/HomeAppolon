package dev.appolon.homelab.arr.feature.lidarr.search.vm

import dev.appolon.homelab.arr.api.lidarr.FakeLidarrQueue
import dev.appolon.homelab.arr.api.lidarr.model.CustomFormatResource
import dev.appolon.homelab.arr.api.lidarr.model.DownloadProtocol
import dev.appolon.homelab.arr.api.lidarr.model.Quality
import dev.appolon.homelab.arr.api.lidarr.model.QualityModel
import dev.appolon.homelab.arr.api.lidarr.model.ReleaseResource
import dev.appolon.homelab.arr.core.ServerId
import dev.appolon.homelab.arr.data.store.ActionQueueImpl
import dev.appolon.homelab.arr.data.testing.FakeEntityStore
import dev.appolon.homelab.arr.feature.lidarr.search.data.ReleaseGroupFilter
import dev.appolon.homelab.arr.feature.lidarr.search.data.ReleaseScope
import dev.appolon.homelab.arr.feature.lidarr.search.data.ReleaseSort
import dev.appolon.homelab.arr.feature.lidarr.search.data.LidarrReleaseRepository
import dev.appolon.homelab.arr.ui.lce.LceState
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ReleaseListViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private val fake = FakeLidarrQueue()
    private val store = FakeEntityStore()
    private val actionQueue = ActionQueueImpl(store)
    private var nowMillis = 1_000L
    private val repository = LidarrReleaseRepository(
        serverId = ServerId("server-1"),
        store = store,
        releaseApi = fake,
        releasePushApi = fake,
        actionQueue = actionQueue,
        now = { nowMillis },
        io = Dispatchers.Unconfined,
    )
    private val grabOptions = GrabOptions(
        qualities = listOf(Quality(id = 7, name = "FLAC")),
    )

    private fun release(
        guid: String,
        score: Int,
        seeders: Int?,
        discography: Boolean = false,
    ) = ReleaseResource(
        guid = guid,
        title = "Artist - Album 2019 FLAC $guid",
        artistId = 5,
        albumId = if (discography) null else 9,
        discography = discography,
        customFormatScore = score,
        customFormats = listOf(CustomFormatResource(id = 1, name = "Lossless")),
        seeders = seeders,
        leechers = seeders?.let { it / 2 },
        protocol = if (seeders == null) DownloadProtocol.USENET else DownloadProtocol.TORRENT,
        quality = QualityModel(quality = Quality(id = 7, name = "FLAC")),
        rejections = listOf("Wrong quality"),
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        store.clock = { nowMillis }
        var key = 0
        listOf(
            release("low", score = -5, seeders = 40),
            release("high", score = 20, seeders = 3),
            release("discog", score = 0, seeders = 7, discography = true),
        ).forEach { fake.releases[++key] = it }
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun vm(scope: ReleaseScope = ReleaseScope.Artist(5)) = ReleaseListViewModel(
        scope = scope,
        repository = repository,
        grabOptionsSource = { grabOptions },
    )

    @Test
    fun `initial load sorts by custom format score`() {
        val state = vm().state.value

        val content = state.releases
        assertTrue(content is LceState.Content)
        assertEquals(
            listOf("high", "discog", "low"),
            (content as LceState.Content).data.map { it.release.guid },
        )
    }

    @Test
    fun `the group filter splits discography from album packs`() {
        val viewModel = vm()

        viewModel.setGroupFilter(ReleaseGroupFilter.DISCOGRAPHY)
        assertEquals(
            listOf("discog"),
            (viewModel.state.value.releases as LceState.Content).data.map { it.release.guid },
        )

        viewModel.setGroupFilter(ReleaseGroupFilter.ALBUM_PACKS)
        assertEquals(
            listOf("high", "low"),
            (viewModel.state.value.releases as LceState.Content).data.map { it.release.guid },
        )

        viewModel.setGroupFilter(ReleaseGroupFilter.ALL)
        assertEquals(3, (viewModel.state.value.releases as LceState.Content).data.size)
    }

    @Test
    fun `switching to seeders sort reorders the same rows`() {
        val viewModel = vm()
        viewModel.setSort(ReleaseSort.SEEDERS)

        val content = viewModel.state.value.releases as LceState.Content
        assertEquals(
            listOf("low", "discog", "high"),
            content.data.map { it.release.guid },
        )
    }

    @Test
    fun `toggling rejections tracks the expanded rows`() {
        val viewModel = vm()
        viewModel.toggleRejections("high")
        viewModel.toggleRejections("discog")
        viewModel.toggleRejections("discog")

        assertEquals(setOf("high"), viewModel.state.value.expandedGuids)
    }

    @Test
    fun `openGrabSheet attaches the target with its options`() {
        val viewModel = vm()
        val row = (viewModel.state.value.releases as LceState.Content).data.first()

        viewModel.openGrabSheet(row)

        val target = viewModel.state.value.grabTarget
        assertEquals(row.release.guid, target?.row?.release?.guid)
        assertEquals(grabOptions, target?.options)
    }

    @Test
    fun `grab closes the sheet and reports the synced outcome`() {
        val viewModel = vm()
        val row = (viewModel.state.value.releases as LceState.Content).data.first()
        viewModel.openGrabSheet(row)

        viewModel.grab(Quality(id = 9, name = "MP3-320"))

        assertNull(viewModel.state.value.grabTarget)
        assertEquals(ReleaseMessage.Grabbed, viewModel.state.value.message)
        assertEquals(9, fake.grabbedReleases.single().quality?.quality?.id)
    }

    @Test
    fun `grab offline reports the queued outcome`() {
        val viewModel = vm()
        fake.error = { operation ->
            if (operation == "grabRelease") IOException("offline") else null
        }
        val row = (viewModel.state.value.releases as LceState.Content).data.first()
        viewModel.openGrabSheet(row)

        viewModel.grab(null)

        assertEquals(ReleaseMessage.GrabQueued, viewModel.state.value.message)
    }

    @Test
    fun `push external release posts the magnet as magnetUrl`() {
        val viewModel = vm()

        viewModel.pushExternalRelease(
            ExternalPushInput(
                title = "Artist - Album 2019",
                downloadUrl = "magnet:?xt=urn:btih:abc",
                indexer = "handoff",
            ),
        )

        assertEquals(ReleaseMessage.Pushed, viewModel.state.value.message)
        assertFalse(viewModel.state.value.pushDialogOpen)
        val pushed = fake.pushedReleases.single()
        assertEquals("magnet:?xt=urn:btih:abc", pushed.magnetUrl)
        assertNull(pushed.downloadUrl)
        assertEquals(5, pushed.artistId)
    }

    @Test
    fun `a failed pull-to-refresh replaces content with the error state`() {
        val viewModel = vm()
        assertTrue(viewModel.state.value.releases is LceState.Content)
        fake.error = { operation ->
            if (operation == "getReleases") IOException("offline") else null
        }

        viewModel.refresh()

        val state = viewModel.state.value
        assertTrue(state.releases is LceState.Error)
        assertFalse(state.stale)
    }

    @Test
    fun `consumeMessage clears the one-shot outcome`() {
        val viewModel = vm()
        fake.error = { operation ->
            if (operation == "grabRelease") IOException("offline") else null
        }
        val row = (viewModel.state.value.releases as LceState.Content).data.first()
        viewModel.openGrabSheet(row)
        viewModel.grab(null)
        assertEquals(ReleaseMessage.GrabQueued, viewModel.state.value.message)

        viewModel.consumeMessage()

        assertNull(viewModel.state.value.message)
    }
}
