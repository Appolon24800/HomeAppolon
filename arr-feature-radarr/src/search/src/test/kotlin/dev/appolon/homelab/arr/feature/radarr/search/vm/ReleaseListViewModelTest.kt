package dev.appolon.homelab.arr.feature.radarr.search.vm

import dev.appolon.homelab.arr.api.radarr.model.CustomFormatResource
import dev.appolon.homelab.arr.api.radarr.model.Language
import dev.appolon.homelab.arr.api.radarr.model.Quality
import dev.appolon.homelab.arr.api.radarr.model.QualityModel
import dev.appolon.homelab.arr.feature.radarr.search.FakeRadarrReleases
import dev.appolon.homelab.arr.data.testing.FakeEntityStore
import dev.appolon.homelab.arr.feature.radarr.search.data.RadarrReleaseRepository
import dev.appolon.homelab.arr.feature.radarr.search.data.RadarrReleaseResource
import dev.appolon.homelab.arr.feature.radarr.search.data.ReleaseSort
import dev.appolon.homelab.arr.core.ServerId
import dev.appolon.homelab.arr.data.store.ActionQueueImpl
import dev.appolon.homelab.arr.ui.lce.LceState
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ReleaseListViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private val fake = FakeRadarrReleases()
    private val store = FakeEntityStore()
    private val actionQueue = ActionQueueImpl(store)
    private val repository = RadarrReleaseRepository(
        serverId = ServerId("server-1"),
        store = store,
        releaseApi = fake,
        releasePushApi = fake,
        actionQueue = actionQueue,
        io = Dispatchers.Unconfined,
    )
    private val grabOptions = GrabOptions(
        qualities = listOf(Quality(id = 7, name = "WEBDL-1080p")),
        languages = listOf(Language(id = 1, name = "English")),
    )

    private fun release(guid: String, score: Int, seeders: Int?) = RadarrReleaseResource(
        guid = guid,
        title = "title-$guid",
        movieId = 5,
        customFormatScore = score,
        customFormats = listOf(CustomFormatResource(id = 1, name = "HQ")),
        seeders = seeders,
        quality = QualityModel(quality = Quality(id = 7, name = "WEBDL-1080p")),
        languages = listOf(Language(id = 1, name = "English")),
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        fake.releases += listOf(
            release("low", score = -5, seeders = 40),
            release("high", score = 20, seeders = 3),
            release("mid", score = 0, seeders = 10),
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun vm() = ReleaseListViewModel(
        movieId = 5,
        repository = repository,
        grabOptionsSource = { grabOptions },
    )

    @Test
    fun `initial load sorts by custom format score`() {
        val state = vm().state.value

        val content = state.releases
        assertTrue(content is LceState.Content)
        assertEquals(
            listOf("high", "mid", "low"),
            (content as LceState.Content).data.map { it.release.guid },
        )
    }

    @Test
    fun `switching to seeders sort reorders the same rows`() {
        val viewModel = vm()
        viewModel.setSort(ReleaseSort.SEEDERS)

        val content = viewModel.state.value.releases as LceState.Content
        assertEquals(
            listOf("low", "mid", "high"),
            content.data.map { it.release.guid },
        )
    }

    @Test
    fun `toggling rejections tracks the expanded rows`() {
        val viewModel = vm()
        viewModel.toggleRejections("high")
        viewModel.toggleRejections("mid")
        viewModel.toggleRejections("mid")

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

        assertEquals(GrabMessage.Grabbed, viewModel.state.value.message)
        assertNull(viewModel.state.value.grabTarget)
        assertEquals(1, fake.grabs.size)
    }

    @Test
    fun `grab offline reports the queued outcome`() = kotlinx.coroutines.test.runTest {
        val viewModel = vm()
        val row = (viewModel.state.value.releases as LceState.Content).data.first()
        viewModel.openGrabSheet(row)
        fake.errorFor = { op -> if (op == "grab") IOException("offline") else null }

        viewModel.grab(null, null)

        assertEquals(GrabMessage.GrabQueued, viewModel.state.value.message)
        assertTrue(actionQueue.pending().isNotEmpty())
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
