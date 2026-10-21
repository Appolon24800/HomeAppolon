package dev.appolon.homelab.arr.feature.sonarr.settings.quality.vm

import dev.appolon.homelab.arr.api.sonarr.FakeSonarrCalendar
import dev.appolon.homelab.arr.api.sonarr.model.DelayProfileResource
import dev.appolon.homelab.arr.api.sonarr.model.DownloadProtocol
import dev.appolon.homelab.arr.core.ServerId
import dev.appolon.homelab.arr.feature.sonarr.settings.quality.FakeQualityActionQueue
import dev.appolon.homelab.arr.data.testing.FakeEntityStore
import dev.appolon.homelab.arr.feature.sonarr.settings.quality.data.SonarrQualityRepository
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
class SonarrDelayProfilesViewModelTest {

    private lateinit var api: FakeSonarrCalendar
    private lateinit var store: FakeEntityStore
    private lateinit var queue: FakeQualityActionQueue
    private var now: Long = 1_000_000L

    @Before
    fun setUp() {
        api = FakeSonarrCalendar()
        store = FakeEntityStore()
        queue = FakeQualityActionQueue()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun repository(scheduler: TestCoroutineScheduler) = SonarrQualityRepository(
        serverId = ServerId("s1"),
        store = store,
        qualityDefinitionApi = api.qualityDefinition,
        qualityProfileApi = api.qualityProfile,
        qualityProfileSchemaApi = api.qualityProfileSchema,
        languageProfileApi = api.languageProfile,
        languageProfileSchemaApi = api.languageProfileSchema,
        customFormatApi = api.customFormat,
        delayProfileApi = api.delayProfile,
        releaseProfileApi = api.releaseProfile,
        languageApi = api.language,
        actionQueue = queue,
        now = { now },
        ioContext = StandardTestDispatcher(scheduler),
    )

    @Test
    fun `profiles load in order with the global fallback flagged`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        api.delayProfileStore[1] = DelayProfileResource(id = 1, order = 1)
        api.delayProfileStore[2] = DelayProfileResource(id = 2, order = 0, tags = listOf(4))
        val vm = SonarrDelayProfilesViewModel(repository(testScheduler))
        vm.lce.collectWhileActive(this)
        advanceUntilIdle()

        val content = (vm.lce.value as LceState.Content<SonarrDelayProfilesContent>).data
        assertEquals(listOf(2, 1), content.profiles.map { it.id })
        assertTrue(vm.isDefaultProfile(content.profiles.first { it.id == 1 }))
        assertFalse(vm.isDefaultProfile(content.profiles.first { it.id == 2 }))
    }

    @Test
    fun `add then save posts a new profile with both protocols`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val vm = SonarrDelayProfilesViewModel(repository(testScheduler))
        vm.lce.collectWhileActive(this)
        advanceUntilIdle()

        vm.addProfile()
        vm.editDraft(vm.state.value.newDrafts.first()) { it.copy(usenetDelay = 120) }
        vm.save(vm.state.value.newDrafts.first())
        advanceUntilIdle()

        assertEquals(DelayProfilesNotice.CREATED, vm.state.value.notice)
        val saved = api.delayProfileStore.values.first()
        assertTrue(saved.enableUsenet)
        assertTrue(saved.enableTorrent)
        assertEquals(120, saved.usenetDelay)
        assertEquals(DownloadProtocol.USENET, saved.preferredProtocol)
    }

    @Test
    fun `editing a saved row keeps the draft local until save`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        api.delayProfileStore[1] = DelayProfileResource(id = 1, order = 0, usenetDelay = 30)
        val vm = SonarrDelayProfilesViewModel(repository(testScheduler))
        vm.lce.collectWhileActive(this)
        advanceUntilIdle()

        vm.editDraft(vm.state.value.profiles.first()) { it.copy(usenetDelay = 90) }
        advanceUntilIdle()
        val content = (vm.lce.value as LceState.Content<SonarrDelayProfilesContent>).data
        assertEquals(setOf(1), content.dirtyIds)
        assertEquals(30, api.delayProfileStore.values.first().usenetDelay)

        vm.save(vm.state.value.edits.getValue(1))
        advanceUntilIdle()

        assertEquals(DelayProfilesNotice.UPDATED, vm.state.value.notice)
        assertEquals(90, api.delayProfileStore.values.first().usenetDelay)
    }

    @Test
    fun `discard reverts a saved row's local edits`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        api.delayProfileStore[1] = DelayProfileResource(id = 1, order = 0, usenetDelay = 30)
        val vm = SonarrDelayProfilesViewModel(repository(testScheduler))
        vm.lce.collectWhileActive(this)
        advanceUntilIdle()

        vm.editDraft(vm.state.value.profiles.first()) { it.copy(usenetDelay = 90) }
        advanceUntilIdle()
        vm.discardDraft(vm.state.value.edits.getValue(1))
        advanceUntilIdle()

        val content = (vm.lce.value as LceState.Content<SonarrDelayProfilesContent>).data
        assertTrue(content.dirtyIds.isEmpty())
        assertEquals(30, content.profiles.first().usenetDelay)
    }

    @Test
    fun `move up reorders through the endpoint`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        api.delayProfileStore[1] = DelayProfileResource(id = 1, order = 0)
        api.delayProfileStore[2] = DelayProfileResource(id = 2, order = 1)
        val vm = SonarrDelayProfilesViewModel(repository(testScheduler))
        vm.lce.collectWhileActive(this)
        advanceUntilIdle()

        vm.moveUp(vm.state.value.profiles.first { it.id == 2 })
        advanceUntilIdle()

        assertEquals(DelayProfilesNotice.MOVED, vm.state.value.notice)
        assertEquals(listOf(2, 1), api.delayProfileStore.values.sortedBy { it.order }.map { it.id })
    }

    @Test
    fun `offline move queues the reorder`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        api.delayProfileStore[1] = DelayProfileResource(id = 1, order = 0)
        api.delayProfileStore[2] = DelayProfileResource(id = 2, order = 1)
        store.clock = { now }
        val vm = SonarrDelayProfilesViewModel(repository(testScheduler))
        vm.lce.collectWhileActive(this)
        advanceUntilIdle()

        api.error = { IOException("offline") }
        vm.moveUp(vm.state.value.profiles.first { it.id == 2 })
        advanceUntilIdle()

        assertEquals(DelayProfilesNotice.QUEUED, vm.state.value.notice)
        assertEquals("api/v3/delayprofile/reorder/2", queue.pending().first().path)
        val content = (vm.lce.value as LceState.Content<SonarrDelayProfilesContent>).data
        assertEquals(listOf(2, 1), content.profiles.map { it.id })
    }

    @Test
    fun `delete confirms then removes the row`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        api.delayProfileStore[2] = DelayProfileResource(id = 2, order = 0, tags = listOf(4))
        val vm = SonarrDelayProfilesViewModel(repository(testScheduler))
        vm.lce.collectWhileActive(this)
        advanceUntilIdle()

        vm.requestDelete(2)
        vm.deleteConfirmed()
        advanceUntilIdle()

        assertEquals(DelayProfilesNotice.DELETED, vm.state.value.notice)
        assertTrue(api.delayProfileStore.isEmpty())
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
private fun <T> StateFlow<T>.collectWhileActive(scope: TestScope) {
    scope.backgroundScope.launch { this@collectWhileActive.collect { } }
}
