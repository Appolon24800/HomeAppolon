package dev.appolon.homelab.arr.feature.sonarr.settings.quality.vm

import dev.appolon.homelab.arr.api.sonarr.FakeSonarrCalendar
import dev.appolon.homelab.arr.api.sonarr.model.Quality
import dev.appolon.homelab.arr.api.sonarr.model.QualityDefinitionResource
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
class SonarrQualityDefinitionsViewModelTest {

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

    private fun definition(id: Int, weight: Int) = QualityDefinitionResource(
        id = id,
        quality = Quality(id = id, name = "Q$id"),
        title = "Q$id",
        weight = weight,
        minSize = 0.0,
        maxSize = 100.0,
    )

    @Test
    fun `rows load cache-first sorted by weight`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        api.qualityDefinitionStore[1] = definition(1, weight = 20)
        api.qualityDefinitionStore[2] = definition(2, weight = 10)
        val vm = SonarrQualityDefinitionsViewModel(repository(testScheduler))
        vm.lce.collectWhileActive(this)
        advanceUntilIdle()

        val content = (vm.lce.value as LceState.Content<SonarrQualityDefinitionsContent>).data
        assertEquals(listOf(2, 1), content.drafts.map { it.id })
        assertFalse(content.dirty)
    }

    @Test
    fun `validation rejects min above max and non-numeric bounds`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        api.qualityDefinitionStore[1] = definition(1, weight = 10)
        val vm = SonarrQualityDefinitionsViewModel(repository(testScheduler))
        vm.lce.collectWhileActive(this)
        advanceUntilIdle()

        vm.editRow(1) { it.copy(minSize = "abc") }
        val errors = vm.validate(vm.state.value.drafts)
        assertTrue(QualityDefinitionFieldError.MIN_NOT_A_NUMBER in errors.getValue(1))

        vm.editRow(1) { it.copy(minSize = "90", maxSize = "80") }
        val rangeErrors = vm.validate(vm.state.value.drafts)
        assertTrue(QualityDefinitionFieldError.MIN_ABOVE_MAX in rangeErrors.getValue(1))
    }

    @Test
    fun `save with errors never hits the network`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        api.qualityDefinitionStore[1] = definition(1, weight = 10)
        val vm = SonarrQualityDefinitionsViewModel(repository(testScheduler))
        vm.lce.collectWhileActive(this)
        advanceUntilIdle()

        vm.editRow(1) { it.copy(minSize = "-5") }
        vm.save()
        advanceUntilIdle()

        assertEquals(null, vm.state.value.notice)
        assertEquals(0.0, api.qualityDefinitionStore.values.first().minSize!!, 0.0)
    }

    @Test
    fun `save posts the table and clears the dirty flag`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        api.qualityDefinitionStore[1] = definition(1, weight = 10)
        val vm = SonarrQualityDefinitionsViewModel(repository(testScheduler))
        vm.lce.collectWhileActive(this)
        advanceUntilIdle()

        vm.editRow(1) { it.copy(weight = 30, title = "Web") }
        vm.save()
        advanceUntilIdle()

        assertEquals(QualityDefinitionsNotice.SAVED, vm.state.value.notice)
        assertFalse(vm.state.value.dirty)
        assertEquals(30, api.qualityDefinitionStore.values.first().weight)
        assertEquals("Web", api.qualityDefinitionStore.values.first().title)
    }

    @Test
    fun `offline save applies optimistically and queues`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        api.qualityDefinitionStore[1] = definition(1, weight = 10)
        val repository = repository(testScheduler)
        val vm = SonarrQualityDefinitionsViewModel(repository)
        vm.lce.collectWhileActive(this)
        advanceUntilIdle()

        store.clock = { now }
        api.error = { IOException("offline") }
        vm.editRow(1) { it.copy(weight = 40) }
        vm.save()
        advanceUntilIdle()

        assertEquals(QualityDefinitionsNotice.SAVED_QUEUED, vm.state.value.notice)
        assertFalse(vm.state.value.dirty)
        // The server never saw the write; the optimistic copy lives in the cache.
        assertEquals(10, api.qualityDefinitionStore.values.first().weight)
        assertEquals(1, queue.pending().size)
        val content = (vm.lce.value as LceState.Content<SonarrQualityDefinitionsContent>).data
        assertEquals(1, content.pendingActions)
    }

    @Test
    fun `background refresh never camps on unsaved edits`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        api.qualityDefinitionStore[1] = definition(1, weight = 10)
        val vm = SonarrQualityDefinitionsViewModel(repository(testScheduler))
        vm.lce.collectWhileActive(this)
        advanceUntilIdle()

        vm.editRow(1) { it.copy(title = "Local edit") }
        api.qualityDefinitionStore[1] = definition(1, weight = 10).copy(title = "Server edit")
        now += SonarrQualityRepository.QUALITY_TTL_MILLIS + 1
        vm.refresh()
        advanceUntilIdle()

        assertEquals("Local edit", vm.state.value.drafts.first().title)
        assertTrue(vm.state.value.dirty)
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
private fun <T> StateFlow<T>.collectWhileActive(scope: TestScope) {
    scope.backgroundScope.launch { this@collectWhileActive.collect { } }
}
