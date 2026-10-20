package dev.appolon.homelab.arr.feature.sonarr.search.vm

import dev.appolon.homelab.arr.api.sonarr.FakeSonarrQueue
import dev.appolon.homelab.arr.api.sonarr.model.CustomFormatResource
import dev.appolon.homelab.arr.api.sonarr.model.DownloadProtocol
import dev.appolon.homelab.arr.api.sonarr.model.Language
import dev.appolon.homelab.arr.api.sonarr.model.Quality
import dev.appolon.homelab.arr.api.sonarr.model.QualityModel
import dev.appolon.homelab.arr.api.sonarr.model.ReleaseResource
import dev.appolon.homelab.arr.core.ServerId
import dev.appolon.homelab.arr.data.store.ActionQueueImpl
import dev.appolon.homelab.arr.data.testing.FakeEntityStore
import dev.appolon.homelab.arr.feature.sonarr.search.data.ReleaseGroupFilter
import dev.appolon.homelab.arr.feature.sonarr.search.data.ReleaseScope
import dev.appolon.homelab.arr.feature.sonarr.search.data.ReleaseSort
import dev.appolon.homelab.arr.feature.sonarr.search.data.SonarrReleaseRepository
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
    private val fake = FakeSonarrQueue()
    private val store = FakeEntityStore()
    private val actionQueue = ActionQueueImpl(store)
    private val repository = SonarrReleaseRepository(
        serverId = ServerId("server-1"),
        store = store,
        releaseApi = fake.release,
        releasePushApi = fake.releasePush,
        actionQueue = actionQueue,
        io = Dispatchers.Unconfined,
    )
    private val grabOptions = GrabOptions(
        qualities = listOf(Quality(id = 7, name = "WEBDL-1080p")),
        languages = listOf(Language(id = 1, name = "English")),
    )

    private fun release(
        guid: String,
        score: Int,
        seeders: Int?,
        fullSeason: Boolean = false,
    ) = ReleaseResource(
        guid = guid,
        title = "Some.Series.S02E05.$guid",
        seriesId = 5,
        episodeId = if (fullSeason) null else 9,
        seasonNumber = 2,
        fullSeason = fullSeason,
        customFormatScore = score,
        customFormats = listOf(CustomFormatResource(id = 1, name = "HQ")),
        seeders = seeders,
        leechers = seeders?.let { it / 2 },
        protocol = if (seeders == null) DownloadProtocol.USENET else DownloadProtocol.TORRENT,
        quality = QualityModel(quality = Quality(id = 7, name = "WEBDL-1080p")),
        languages = listOf(Language(id = 1, name = "English")),
        rejections = listOf("Wrong quality"),
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        fake.releases += listOf(
            release("low", score = -5, seeders = 40),
            release("high", score = 20, seeders = 3),
            release("pack", score = 0, seeders = 7, fullSeason = true),
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun vm(scope: ReleaseScope = ReleaseScope.Season(5, 2)) = ReleaseListViewModel(
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
            listOf("high", "pack", "low"),
            (content as LceState.Content).data.map { it.release.guid },
        )
    }

    @Test
    fun `the group filter splits season packs from episodes`() {
        val viewModel = vm()

        viewModel.setGroupFilter(ReleaseGroupFilter.SEASON_PACKS)
        assertEquals(
            listOf("pack"),
            (viewModel.state.value.releases as LceState.Content).data.map { it.release.guid },
        )

        viewModel.setGroupFilter(ReleaseGroupFilter.EPISODES)
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
            listOf("low", "pack", "high"),
            content.data.map { it.release.guid },
        )
    }

    @Test
    fun `toggling rejections tracks the expanded rows`() {
        val viewModel = vm()
        viewModel.toggleRejections("high")
        viewModel.toggleRejections("pack")
        viewModel.toggleRejections("pack")

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
    fun `grab posts the overrides and reports the synced outcome`() {
        val viewModel = vm()
        val row = (viewModel.state.value.releases as LceState.Content).data.first()
        viewModel.openGrabSheet(row)

        viewModel.grab(Quality(id = 7, name = "WEBDL-1080p"), Language(id = 1, name = "English"))

        assertEquals(ReleaseMessage.Grabbed, viewModel.state.value.message)
        assertNull(viewModel.state.value.grabTarget)
        assertEquals(1, fake.grabbedReleases.size)
    }

    @Test
    fun `grab offline reports the queued outcome`() = kotlinx.coroutines.test.runTest {
        val viewModel = vm()
        val row = (viewModel.state.value.releases as LceState.Content).data.first()
        viewModel.openGrabSheet(row)
        fake.error = { operation ->
            if (operation == "Release.create") IOException("offline") else null
        }

        viewModel.grab(null, null)

        assertEquals(ReleaseMessage.GrabQueued, viewModel.state.value.message)
        assertTrue(actionQueue.pending().isNotEmpty())
    }

    @Test
    fun `pushExternalRelease posts a magnet as magnetUrl and reports the outcome`() {
        val viewModel = vm()

        viewModel.pushExternalRelease(
            ExternalPushInput(
                title = "Some.Series.S02E05.1080p.GROUP",
                downloadUrl = "magnet:?xt=urn:btih:abc",
                indexer = "prowlarr",
                protocol = DownloadProtocol.TORRENT,
            ),
        )

        assertEquals(ReleaseMessage.Pushed, viewModel.state.value.message)
        assertFalse(viewModel.state.value.pushDialogOpen)
        val pushed = fake.pushedReleases.single()
        assertEquals("magnet:?xt=urn:btih:abc", pushed.magnetUrl)
        assertNull(pushed.downloadUrl)
        assertEquals(5, pushed.seriesId)
        assertEquals("prowlarr", pushed.indexer)
    }

    @Test
    fun `pushExternalRelease offline reports the queued outcome`() {
        val viewModel = vm()
        fake.error = { operation ->
            if (operation == "ReleasePush.create") IOException("offline") else null
        }

        viewModel.pushExternalRelease(
            ExternalPushInput(
                title = "Some.Series.S02E05.1080p.GROUP",
                downloadUrl = "https://example.org/torrent.torrent",
            ),
        )

        assertEquals(ReleaseMessage.PushQueued, viewModel.state.value.message)
    }

    @Test
    fun `consumeMessage clears the one-shot notice`() {
        val viewModel = vm()
        val row = (viewModel.state.value.releases as LceState.Content).data.first()
        viewModel.openGrabSheet(row)
        viewModel.grab(null, null)
        viewModel.consumeMessage()

        assertNull(viewModel.state.value.message)
    }
}
