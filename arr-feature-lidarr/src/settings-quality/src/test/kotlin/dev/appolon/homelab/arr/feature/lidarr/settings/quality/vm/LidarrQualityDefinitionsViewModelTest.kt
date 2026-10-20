package dev.appolon.homelab.arr.feature.lidarr.settings.quality.vm

import dev.appolon.homelab.arr.api.lidarr.FakeLidarrCalendar
import dev.appolon.homelab.arr.api.lidarr.model.Quality
import dev.appolon.homelab.arr.api.lidarr.model.QualityDefinitionResource
import dev.appolon.homelab.arr.core.ServerId
import dev.appolon.homelab.arr.feature.lidarr.settings.quality.FakeQualityActionQueue
import dev.appolon.homelab.arr.data.testing.FakeEntityStore
import dev.appolon.homelab.arr.feature.lidarr.settings.quality.data.LidarrQualityRepository
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
class LidarrQualityDefinitionsViewModelTest {

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

    private fun definition(id: Int, weight: Int, max: Double? = null) = QualityDefinitionResource(
        id = id,
        quality = Quality(id = id, name = "Q$id"),
        title = "Q$id",
        weight = weight,
        minSize = 0.0,
        maxSize = max,
    )

    @Test
    fun `rows load cache-first sorted by weight with a derived weight range`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        api.qualityDefinitions[2] = definition(2, 30)
        api.qualityDefinitions[1] = definition(1, 10)
        val vm = LidarrQualityDefinitionsViewModel(repository(testScheduler))
        vm.lce.collectWhileActive(this)
        advanceUntilIdle()

        val content = (vm.lce.value as LceState.Content<LidarrQualityDefinitionsContent>).data
        assertEquals(listOf(1, 2), content.drafts.map { it.id })
        assertEquals(0, content.weightRange.min)
        assertEquals(60, content.weightRange.max)
        assertFalse(vm.state.value.refreshing)
    }

    @Test
    fun `validation blocks the save and reports the failing rows`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        api.qualityDefinitions[1] = definition(1, 10, max = 100.0)
        val vm = LidarrQualityDefinitionsViewModel(repository(testScheduler))
        vm.lce.collectWhileActive(this)
        advanceUntilIdle()

        vm.editRow(1) { it.copy(minSize = "200", preferredSize = "abc") }
        vm.save()
        advanceUntilIdle()

        val errors = vm.state.value.errors[1].orEmpty()
        assertTrue(QualityDefinitionFieldError.MIN_ABOVE_MAX in errors)
        assertTrue(QualityDefinitionFieldError.PREFERRED_NOT_A_NUMBER in errors)
        assertTrue(api.updatedQualityDefinitions.isEmpty())
        assertTrue(vm.state.value.dirty)
    }

    @Test
    fun `save posts the whole table and clears the dirty flag`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        api.qualityDefinitions[1] = definition(1, 10)
        val vm = LidarrQualityDefinitionsViewModel(repository(testScheduler))
        vm.lce.collectWhileActive(this)
        advanceUntilIdle()

        vm.editRow(1) { it.copy(title = "MP3-V0", maxSize = "") }
        vm.save()
        advanceUntilIdle()

        assertEquals(QualityDefinitionsNotice.SAVED, vm.state.value.notice)
        assertFalse(vm.state.value.dirty)
        val posted = api.updatedQualityDefinitions.first().first()
        assertEquals("MP3-V0", posted.title)
        assertEquals(null, posted.maxSize)
    }

    @Test
    fun `offline save applies optimistically and queues`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        api.qualityDefinitions[1] = definition(1, 10)
        val vm = LidarrQualityDefinitionsViewModel(repository(testScheduler))
        vm.lce.collectWhileActive(this)
        advanceUntilIdle()

        store.clock = { now }
        api.error = { IOException("offline") }
        vm.editRow(1) { it.copy(weight = 20) }
        vm.save()
        advanceUntilIdle()

        assertEquals(QualityDefinitionsNotice.SAVED_QUEUED, vm.state.value.notice)
        assertEquals(1, queue.pending().size)
        val content = (vm.lce.value as LceState.Content<LidarrQualityDefinitionsContent>).data
        assertEquals(1, content.pendingActions)
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
internal fun <T> StateFlow<T>.collectWhileActive(scope: TestScope) {
    scope.backgroundScope.launch { this@collectWhileActive.collect { } }
}
