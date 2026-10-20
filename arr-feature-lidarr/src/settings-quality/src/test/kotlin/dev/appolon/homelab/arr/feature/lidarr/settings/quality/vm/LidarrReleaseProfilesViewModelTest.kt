package dev.appolon.homelab.arr.feature.lidarr.settings.quality.vm

import dev.appolon.homelab.arr.api.lidarr.FakeLidarrCalendar
import dev.appolon.homelab.arr.api.lidarr.model.LanguageResource
import dev.appolon.homelab.arr.api.lidarr.model.ReleaseProfileResource
import dev.appolon.homelab.arr.core.ServerId
import dev.appolon.homelab.arr.feature.lidarr.settings.quality.FakeQualityActionQueue
import dev.appolon.homelab.arr.feature.lidarr.settings.quality.FakeQualityEntityStore
import dev.appolon.homelab.arr.feature.lidarr.settings.quality.data.LidarrQualityRepository
import dev.appolon.homelab.arr.ui.lce.LceState
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
class LidarrReleaseProfilesViewModelTest {

    private lateinit var api: FakeLidarrCalendar
    private lateinit var store: FakeQualityEntityStore
    private lateinit var queue: FakeQualityActionQueue
    private var now: Long = 1_000_000L

    @Before
    fun setUp() {
        api = FakeLidarrCalendar()
        store = FakeQualityEntityStore()
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
    fun `profiles load cache-first`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        api.releaseProfiles[3] = ReleaseProfileResource(
            id = 3,
            enabled = true,
            required = listOf("flac"),
            ignored = listOf("transcode"),
        )
        val vm = LidarrReleaseProfilesViewModel(repository(testScheduler))
        vm.lce.collectWhileActive(this)
        advanceUntilIdle()

        val content = (vm.lce.value as LceState.Content<LidarrReleaseProfilesContent>).data
        assertEquals(listOf(3), content.profiles.map { it.id })
        assertFalse(vm.state.value.refreshing)
    }

    @Test
    fun `save posts term lists as clean strings and preserves tags`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        api.releaseProfiles[3] = ReleaseProfileResource(
            id = 3,
            enabled = true,
            required = listOf("flac"),
            tags = listOf(4),
        )
        val vm = LidarrReleaseProfilesViewModel(repository(testScheduler))
        vm.lce.collectWhileActive(this)
        advanceUntilIdle()

        vm.save(
            ReleaseProfileDraft(
                profileId = 3,
                enabled = false,
                mustContain = listOf("flac"),
                mustNotContain = listOf("transcode", "vinyl rip"),
                indexerId = "2",
                isExisting = true,
            ),
        )
        advanceUntilIdle()

        assertEquals(ReleaseProfilesNotice.UPDATED, vm.state.value.notice)
        assertEquals(listOf("flac"), api.releaseProfiles[3]!!.required)
        assertEquals(listOf("transcode", "vinyl rip"), api.releaseProfiles[3]!!.ignored)
        assertEquals(2, api.releaseProfiles[3]!!.indexerId)
        assertEquals(listOf(4), api.releaseProfiles[3]!!.tags)
        assertFalse(api.releaseProfiles[3]!!.enabled)
    }

    @Test
    fun `splitText trims and drops blank lines`() {
        assertEquals(
            listOf("flac", "24bit"),
            ReleaseProfileDraft.splitText("flac,\n  24bit \n,,"),
        )
    }

    @Test
    fun `a draft without terms never saves`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val vm = LidarrReleaseProfilesViewModel(repository(testScheduler))
        vm.lce.collectWhileActive(this)
        advanceUntilIdle()

        vm.save(ReleaseProfileDraft())
        advanceUntilIdle()

        assertTrue(api.releaseProfiles.isEmpty())
        assertTrue(queue.pending().isEmpty())
    }

    @Test
    fun `delete asks for confirmation then removes the row`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        api.releaseProfiles[3] = ReleaseProfileResource(id = 3, enabled = true)
        val vm = LidarrReleaseProfilesViewModel(repository(testScheduler))
        vm.lce.collectWhileActive(this)
        advanceUntilIdle()

        vm.requestDelete(3)
        assertEquals(3, vm.state.value.confirmDeleteId)
        vm.deleteConfirmed()
        advanceUntilIdle()

        assertEquals(ReleaseProfilesNotice.DELETED, vm.state.value.notice)
        assertTrue(api.releaseProfiles.isEmpty())
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class LidarrLanguagesViewModelTest {

    private lateinit var api: FakeLidarrCalendar
    private lateinit var store: FakeQualityEntityStore
    private lateinit var queue: FakeQualityActionQueue

    @Before
    fun setUp() {
        api = FakeLidarrCalendar()
        store = FakeQualityEntityStore()
        queue = FakeQualityActionQueue()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `languages load cache-first`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        api.languages[1] = LanguageResource(id = 1, name = "English", nameLower = "english")
        api.languages[0] = LanguageResource(id = 0, name = "Unknown", nameLower = "unknown")
        val repository = LidarrQualityRepository(
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
            ioContext = StandardTestDispatcher(testScheduler),
        )
        val vm = LidarrLanguagesViewModel(repository)
        vm.lce.collectWhileActive(this)
        advanceUntilIdle()

        val content = (vm.lce.value as LceState.Content<LidarrLanguagesContent>).data
        assertEquals(listOf(0, 1), content.languages.map { it.id })
        assertEquals("english", content.languages.first { it.id == 1 }.nameLower)
    }
}
