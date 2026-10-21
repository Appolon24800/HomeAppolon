package dev.appolon.homelab.arr.feature.radarr.settings.quality.vm

import dev.appolon.homelab.arr.api.radarr.FakeRadarrCalendar
import dev.appolon.homelab.arr.api.radarr.model.DelayProfileResource
import dev.appolon.homelab.arr.core.ServerId
import dev.appolon.homelab.arr.feature.radarr.settings.quality.FakeQualityActionQueue
import dev.appolon.homelab.arr.data.testing.FakeEntityStore
import dev.appolon.homelab.arr.feature.radarr.settings.quality.data.RadarrQualityRepository
import dev.appolon.homelab.arr.ui.lce.LceState
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.TestScope
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
class RadarrDelayProfilesViewModelTest {

    private lateinit var api: FakeRadarrCalendar
    private lateinit var store: FakeEntityStore
    private lateinit var queue: FakeQualityActionQueue

    @Before
    fun setUp() {
        api = FakeRadarrCalendar()
        store = FakeEntityStore()
        queue = FakeQualityActionQueue()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun repository(scheduler: TestCoroutineScheduler) = RadarrQualityRepository(
        serverId = ServerId("s1"),
        store = store,
        qualityDefinitionApi = api,
        qualityProfileApi = api,
        qualityProfileSchemaApi = api,
        customFormatApi = api,
        delayProfileApi = api,
        releaseProfileApi = api,
        languageApi = api,
        actionQueue = queue,
        ioContext = StandardTestDispatcher(scheduler),
    )

    private fun profile(id: Int, order: Int, tags: Set<Int>? = null) =
        DelayProfileResource(id = id, enableUsenet = true, usenetDelay = 60, order = order, tags = tags)

    @Test
    fun `loads ordered rows with the default fallback last`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        api.delayProfiles += profile(1, 0)
        api.delayProfiles += profile(2, 1, tags = setOf(5))
        val vm = RadarrDelayProfilesViewModel(repository(testScheduler))
        vm.lce.collectWhileActive(this)
        advanceUntilIdle()

        val content = (vm.lce.value as LceState.Content<RadarrDelayProfilesContent>).data
        assertEquals(listOf(1, 2), content.profiles.map { it.id })
        // The untagged row is Radarr's undeletable global fallback.
        assertTrue(vm.isDefaultProfile(content.profiles.first { it.id == 1 }))
        assertFalse(vm.isDefaultProfile(content.profiles.first { it.id == 2 }))
    }

    @Test
    fun `editing a saved row stays local until its save is tapped`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        api.delayProfiles += profile(1, 0)
        val vm = RadarrDelayProfilesViewModel(repository(testScheduler))
        vm.lce.collectWhileActive(this)
        advanceUntilIdle()

        vm.editDraft(api.delayProfiles.first()) { it.copy(usenetDelay = 240, bypassIfHighestQuality = true) }
        assertEquals(60, api.delayProfiles.first().usenetDelay)

        vm.save(vm.state.value.displayed().first())
        advanceUntilIdle()

        assertEquals(DelayProfilesNotice.UPDATED, vm.state.value.notice)
        assertEquals(240, api.delayProfiles.first().usenetDelay)
        assertTrue(api.delayProfiles.first().bypassIfHighestQuality)
    }

    @Test
    fun `add then save posts a new profile with both protocols`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        api.delayProfiles += profile(1, 0)
        val vm = RadarrDelayProfilesViewModel(repository(testScheduler))
        vm.lce.collectWhileActive(this)
        advanceUntilIdle()

        vm.addProfile()
        val draft = vm.state.value.newDrafts.single()
        vm.editDraft(draft) { it.copy(torrentDelay = 90) }
        vm.save(vm.state.value.newDrafts.single())
        advanceUntilIdle()

        assertEquals(DelayProfilesNotice.CREATED, vm.state.value.notice)
        assertEquals(90, api.delayProfiles[1].torrentDelay)
        assertTrue(api.delayProfiles[1].enableTorrent)
        assertTrue(vm.state.value.newDrafts.isEmpty())
    }

    @Test
    fun `discard drops a draft and reverts saved row edits`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        api.delayProfiles += profile(1, 0)
        val vm = RadarrDelayProfilesViewModel(repository(testScheduler))
        vm.lce.collectWhileActive(this)
        advanceUntilIdle()

        vm.editDraft(api.delayProfiles.first()) { it.copy(usenetDelay = 999) }
        vm.discardDraft(vm.state.value.displayed().first())
        assertEquals(60, vm.state.value.displayed().first().usenetDelay)

        vm.addProfile()
        vm.discardDraft(vm.state.value.newDrafts.single())
        assertTrue(vm.state.value.newDrafts.isEmpty())
    }

    @Test
    fun `move up reorders through the server`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        api.delayProfiles += profile(1, 0)
        api.delayProfiles += profile(2, 1, tags = setOf(5))
        val vm = RadarrDelayProfilesViewModel(repository(testScheduler))
        vm.lce.collectWhileActive(this)
        advanceUntilIdle()

        vm.moveUp(api.delayProfiles[1])
        advanceUntilIdle()

        assertEquals(DelayProfilesNotice.MOVED, vm.state.value.notice)
        assertEquals(listOf(2, 1), vm.state.value.profiles.sortedBy { it.order }.map { it.id })
    }

    @Test
    fun `offline move reorders locally and queues`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        api.delayProfiles += profile(1, 0)
        api.delayProfiles += profile(2, 1, tags = setOf(5))
        val vm = RadarrDelayProfilesViewModel(repository(testScheduler))
        vm.lce.collectWhileActive(this)
        advanceUntilIdle()

        api.errorFor = { IOException("offline") }
        vm.moveUp(api.delayProfiles[1])
        advanceUntilIdle()

        assertEquals(DelayProfilesNotice.QUEUED, vm.state.value.notice)
        assertEquals(listOf(2, 1), vm.state.value.displayed().map { it.id })
        assertEquals("api/v3/delayprofile/reorder/2", queue.pending().single().path)
    }

    @Test
    fun `delete confirms first`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        api.delayProfiles += profile(1, 0)
        api.delayProfiles += profile(2, 1, tags = setOf(5))
        val vm = RadarrDelayProfilesViewModel(repository(testScheduler))
        vm.lce.collectWhileActive(this)
        advanceUntilIdle()

        vm.requestDelete(2)
        vm.deleteConfirmed()
        advanceUntilIdle()

        assertEquals(listOf(1), api.delayProfiles.map { it.id })
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
private fun <T> StateFlow<T>.collectWhileActive(scope: TestScope) {
    scope.backgroundScope.launch { this@collectWhileActive.collect { } }
}
