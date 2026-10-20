package dev.appolon.homelab.arr.feature.lidarr.settings.quality.vm

import dev.appolon.homelab.arr.api.lidarr.FakeLidarrCalendar
import dev.appolon.homelab.arr.api.lidarr.model.DelayProfileResource
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
class LidarrDelayProfilesViewModelTest {

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
    fun `profiles display in order with the global fallback last`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        api.delayProfiles[1] = DelayProfileResource(id = 1, order = 1, tags = listOf(2))
        api.delayProfiles[2] = DelayProfileResource(id = 2, order = 0)
        val vm = LidarrDelayProfilesViewModel(repository(testScheduler))
        vm.lce.collectWhileActive(this)
        advanceUntilIdle()

        val content = (vm.lce.value as LceState.Content<LidarrDelayProfilesContent>).data
        assertEquals(listOf(2, 1), content.profiles.map { it.id })
        // The untagged row is the global fallback and cannot be deleted.
        assertTrue(vm.isDefaultProfile(content.profiles.first { it.id == 2 }))
        assertFalse(vm.isDefaultProfile(content.profiles.first { it.id == 1 }))
    }

    @Test
    fun `addProfile starts a draft with both protocols enabled`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        api.delayProfiles[1] = DelayProfileResource(id = 1, order = 0)
        val vm = LidarrDelayProfilesViewModel(repository(testScheduler))
        vm.lce.collectWhileActive(this)
        advanceUntilIdle()

        vm.addProfile()
        advanceUntilIdle()

        val draft = vm.state.value.displayed().last()
        assertTrue(draft.id <= 0)
        assertTrue(draft.enableUsenet)
        assertTrue(draft.enableTorrent)
    }

    @Test
    fun `editing a saved row keeps a local dirty draft until save`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        api.delayProfiles[1] = DelayProfileResource(id = 1, order = 0, enableUsenet = true)
        val vm = LidarrDelayProfilesViewModel(repository(testScheduler))
        vm.lce.collectWhileActive(this)
        advanceUntilIdle()

        val row = vm.state.value.displayed().first()
        vm.editDraft(row) { it.copy(usenetDelay = 120) }
        advanceUntilIdle()

        assertTrue(vm.state.value.dirtyIds().contains(1))
        assertEquals(0, api.delayProfiles[1]!!.usenetDelay)

        vm.save(vm.state.value.displayed().first())
        advanceUntilIdle()

        assertEquals(DelayProfilesNotice.UPDATED, vm.state.value.notice)
        assertEquals(120, api.delayProfiles[1]!!.usenetDelay)
    }

    @Test
    fun `moveUp reorders through the anchor row`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        api.delayProfiles[1] = DelayProfileResource(id = 1, order = 0)
        api.delayProfiles[2] = DelayProfileResource(id = 2, order = 1)
        api.delayProfiles[3] = DelayProfileResource(id = 3, order = 2)
        val vm = LidarrDelayProfilesViewModel(repository(testScheduler))
        vm.lce.collectWhileActive(this)
        advanceUntilIdle()

        vm.moveUp(api.delayProfiles[3]!!)
        advanceUntilIdle()

        assertEquals(DelayProfilesNotice.MOVED, vm.state.value.notice)
        // Profile 3 moved above profile 2, so the fake now orders 1, 3, 2.
        assertEquals(listOf(1, 3, 2), api.delayProfiles.values.sortedBy { it.order }.map { it.id })
        assertEquals(
            listOf(3 to 1),
            api.reorderedDelayProfiles.map { it.first to it.second },
        )
    }
}
