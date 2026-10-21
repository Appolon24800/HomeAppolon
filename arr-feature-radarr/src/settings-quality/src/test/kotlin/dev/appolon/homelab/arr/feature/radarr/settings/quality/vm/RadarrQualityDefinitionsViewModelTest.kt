package dev.appolon.homelab.arr.feature.radarr.settings.quality.vm

import dev.appolon.homelab.arr.api.radarr.FakeRadarrCalendar
import dev.appolon.homelab.arr.api.radarr.model.Quality
import dev.appolon.homelab.arr.api.radarr.model.QualityDefinitionResource
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
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RadarrQualityDefinitionsViewModelTest {

    private lateinit var api: FakeRadarrCalendar
    private lateinit var store: FakeEntityStore
    private lateinit var queue: FakeQualityActionQueue
    private var now: Long = 1_000_000L

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

    private fun viewModel(scheduler: kotlinx.coroutines.test.TestCoroutineScheduler): RadarrQualityDefinitionsViewModel {
        val repository = RadarrQualityRepository(
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
            now = { now },
            ioContext = StandardTestDispatcher(scheduler),
        )
        return RadarrQualityDefinitionsViewModel(repository)
    }

    private fun definition(id: Int, weight: Int) = QualityDefinitionResource(
        id = id,
        quality = Quality(id = id, name = "Quality $id"),
        title = "Quality $id",
        weight = weight,
        minSize = 0.0,
        maxSize = 200.0,
    )

    @Test
    fun `loads rows cache-first through the LCE states`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        api.qualityDefinitions += definition(1, 10)
        api.qualityDefinitions += definition(2, 20)
        val vm = viewModel(testScheduler)
        assertEquals(LceState.Loading, vm.lce.value)

        vm.lce.collectWhileActive(this)
        advanceUntilIdle()

        val content = (vm.lce.value as LceState.Content<RadarrQualityDefinitionsContent>).data
        assertEquals(listOf(1, 2), content.drafts.map { it.id })
        assertEquals(0, content.limits!!.min)
        assertEquals(1000, content.limits.max)
        assertFalse(content.dirty)
    }

    @Test
    fun `editing a row marks the draft dirty`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        api.qualityDefinitions += definition(1, 10)
        val vm = viewModel(testScheduler)
        vm.lce.collectWhileActive(this)
        advanceUntilIdle()

        vm.editRow(1) { it.copy(title = "Renamed", weight = 40, minSize = "5", maxSize = "", preferredSize = "60") }
        val content = (vm.state.value).let { RadarrQualityDefinitionsContent(it.drafts, it.limits, it.errors, it.dirty, it.saving, it.pendingActions) }
        assertTrue(content.dirty)
        assertEquals("Renamed", vm.state.value.drafts.first().title)
    }

    @Test
    fun `validation rejects min above max and non-numeric sizes`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        api.qualityDefinitions += definition(1, 10)
        val vm = viewModel(testScheduler)
        vm.lce.collectWhileActive(this)
        advanceUntilIdle()

        vm.editRow(1) { it.copy(minSize = "150", maxSize = "100", preferredSize = "abc") }
        vm.save()
        advanceUntilIdle()

        val errors = vm.state.value.errors[1]!!
        assertTrue(QualityDefinitionFieldError.MIN_ABOVE_MAX in errors)
        assertTrue(QualityDefinitionFieldError.PREFERRED_NOT_A_NUMBER in errors)
        // Nothing was written and nothing queued.
        assertTrue(queue.pending().isEmpty())
        assertEquals("Quality 1", api.qualityDefinitions.first().title)
    }

    @Test
    fun `save writes the whole table with blank max parsed as unlimited`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        api.qualityDefinitions += definition(1, 10)
        val vm = viewModel(testScheduler)
        vm.lce.collectWhileActive(this)
        advanceUntilIdle()

        vm.editRow(1) { it.copy(title = "New Title", weight = 42, maxSize = "", preferredSize = "") }
        vm.save()
        advanceUntilIdle()

        assertEquals(QualityDefinitionsNotice.SAVED, vm.state.value.notice)
        assertFalse(vm.state.value.dirty)
        val saved = api.qualityDefinitions.first()
        assertEquals("New Title", saved.title)
        assertEquals(42, saved.weight)
        assertNull(saved.maxSize)
        assertNull(saved.preferredSize)
    }

    @Test
    fun `offline save applies optimistically and shows the queued notice`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        api.qualityDefinitions += definition(1, 10)
        val vm = viewModel(testScheduler)
        vm.lce.collectWhileActive(this)
        advanceUntilIdle()

        api.errorFor = { IOException("offline") }
        vm.editRow(1) { it.copy(weight = 99) }
        vm.save()
        advanceUntilIdle()

        assertEquals(QualityDefinitionsNotice.SAVED_QUEUED, vm.state.value.notice)
        assertEquals(1, vm.state.value.pendingActions)
        // The server copy is untouched; the cache holds the optimistic row.
        assertEquals(10, api.qualityDefinitions.first().weight)
        assertEquals(
            99,
            store.getAll(
                ServerId("s1"),
                dev.appolon.homelab.arr.core.EntityType("radarr.qualitydefinition"),
                QualityDefinitionResource.serializer(),
            ).first().weight,
        )
    }

    @Test
    fun `background refresh does not clobber a dirty draft`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        api.qualityDefinitions += definition(1, 10)
        val vm = viewModel(testScheduler)
        vm.lce.collectWhileActive(this)
        advanceUntilIdle()

        vm.editRow(1) { it.copy(title = "Local Edit") }
        now += RadarrQualityRepository.QUALITY_TTL_MILLIS + 1
        api.qualityDefinitions[0] = api.qualityDefinitions[0].copy(title = "Server Edit")
        vm.refresh()
        advanceUntilIdle()

        assertEquals("Local Edit", vm.state.value.drafts.first().title)
        assertTrue(vm.state.value.dirty)
    }

    @Test
    fun `write errors surface and clear`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        api.qualityDefinitions += definition(1, 10)
        val vm = viewModel(testScheduler)
        vm.lce.collectWhileActive(this)
        advanceUntilIdle()

        api.errorFor = { retrofit2.HttpException(retrofit2.Response.error<Any>(500, "".toResponseBody(null))) }
        vm.editRow(1) { it.copy(weight = 5) }
        vm.save()
        advanceUntilIdle()

        assertNotNull(vm.state.value.writeError)
        vm.consumeWriteError()
        assertNull(vm.state.value.writeError)
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
private fun <T> StateFlow<T>.collectWhileActive(scope: TestScope) {
    scope.backgroundScope.launch { this@collectWhileActive.collect { } }
}
