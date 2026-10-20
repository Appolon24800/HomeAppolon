package dev.appolon.homelab.arr.feature.lidarr.settings.quality.vm

import dev.appolon.homelab.arr.api.lidarr.FakeLidarrCalendar
import dev.appolon.homelab.arr.api.lidarr.model.MetadataProviderConfigResource
import dev.appolon.homelab.arr.api.lidarr.model.WriteAudioTagsType
import dev.appolon.homelab.arr.core.ServerId
import dev.appolon.homelab.arr.feature.lidarr.settings.quality.FakeQualityActionQueue
import dev.appolon.homelab.arr.data.testing.FakeEntityStore
import dev.appolon.homelab.arr.feature.lidarr.settings.quality.data.LidarrQualityRepository
import dev.appolon.homelab.arr.ui.lce.LceState
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LidarrMetadataProviderViewModelTest {

    private lateinit var api: FakeLidarrCalendar
    private lateinit var store: FakeEntityStore
    private lateinit var queue: FakeQualityActionQueue
    private var now: Long = 1_000_000L

    @Before
    fun setUp() {
        api = FakeLidarrCalendar()
        store = FakeEntityStore()
        queue = FakeQualityActionQueue()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun repository(scheduler: TestCoroutineScheduler) = LidarrQualityRepository(
        serverId = ServerId("s1"),
        store = store,
        qualityDefinitionApi = api,
        qualityProfileApi = api,
        qualityProfileSchemaApi = api,
        metadataProfileApi = api,
        metadataProfileSchemaApi = api,
        metadataProviderConfigApi = api,
        customFormatApi = api,
        delayProfileApi = api,
        releaseProfileApi = api,
        languageApi = api,
        actionQueue = queue,
        now = { now },
        ioContext = StandardTestDispatcher(scheduler),
    )

    @Test
    fun `loads the cached config row`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        api.metadataProviderConfig = MetadataProviderConfigResource(
            id = 1,
            metadataSource = "lidarr",
            writeAudioTags = WriteAudioTagsType.SYNC,
            scrubAudioTags = true,
            embedCoverArt = true,
        )
        val vm = LidarrMetadataProviderViewModel(repository(testScheduler))
        vm.lce.collectWhileActive(this)
        advanceUntilIdle()

        val content = (vm.lce.value as LceState.Content<LidarrMetadataProviderContent>).data
        assertTrue(content.draft.isExisting)
        assertEquals("lidarr", content.draft.metadataSource)
        assertEquals(WriteAudioTagsType.SYNC, content.draft.writeAudioTags)
        assertTrue(content.draft.scrubAudioTags)
        assertTrue(content.draft.embedCoverArt)
    }

    @Test
    fun `switching tag writing off clears scrub and cover art`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        api.metadataProviderConfig = MetadataProviderConfigResource(
            id = 1,
            metadataSource = "lidarr",
            writeAudioTags = WriteAudioTagsType.ALL_FILES,
            scrubAudioTags = true,
            embedCoverArt = true,
        )
        val vm = LidarrMetadataProviderViewModel(repository(testScheduler))
        vm.lce.collectWhileActive(this)
        advanceUntilIdle()

        vm.setWriteAudioTags(WriteAudioTagsType.NO)
        advanceUntilIdle()

        val draft = vm.state.value.draft
        assertEquals(WriteAudioTagsType.NO, draft.writeAudioTags)
        assertFalse(draft.scrubAudioTags)
        assertFalse(draft.embedCoverArt)
        assertTrue(vm.state.value.dirty)
    }

    @Test
    fun `save PUTs the draft`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        api.metadataProviderConfig = MetadataProviderConfigResource(id = 1, metadataSource = "lidarr")
        val vm = LidarrMetadataProviderViewModel(repository(testScheduler))
        vm.lce.collectWhileActive(this)
        advanceUntilIdle()

        vm.setWriteAudioTags(WriteAudioTagsType.NEW_FILES)
        vm.setEmbedCoverArt(true)
        vm.save()
        advanceUntilIdle()

        assertTrue(vm.state.value.savedNotice)
        assertEquals(WriteAudioTagsType.NEW_FILES, api.metadataProviderConfig.writeAudioTags)
        assertTrue(api.metadataProviderConfig.embedCoverArt)
    }

    @Test
    fun `offline save queues the PUT`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        api.metadataProviderConfig = MetadataProviderConfigResource(id = 1, metadataSource = "lidarr")
        val vm = LidarrMetadataProviderViewModel(repository(testScheduler))
        vm.lce.collectWhileActive(this)
        advanceUntilIdle()

        store.clock = { now }
        api.error = { IOException("offline") }
        vm.setScrubAudioTags(true)
        vm.save()
        advanceUntilIdle()

        assertTrue(vm.state.value.savedQueued)
        assertEquals(1, queue.pending().size)
        val content = (vm.lce.value as LceState.Content<LidarrMetadataProviderContent>).data
        assertEquals(1, content.pendingActions)
    }
}
