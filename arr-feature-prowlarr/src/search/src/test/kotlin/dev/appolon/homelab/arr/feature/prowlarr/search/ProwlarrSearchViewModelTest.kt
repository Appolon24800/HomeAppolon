package dev.appolon.homelab.arr.feature.prowlarr.search

import androidx.lifecycle.ViewModelStore
import dev.appolon.homelab.arr.api.prowlarr.model.ReleaseResource
import dev.appolon.homelab.arr.core.ServerId
import dev.appolon.homelab.arr.ui.lce.LceState
import dev.appolon.homelab.arr.ui.nav.ArrRouteRegistry
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ProwlarrSearchViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val owners = ViewModelStore()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() {
        owners.clear()
        Dispatchers.resetMain()
    }

    private fun model(f: SearchFixture): ProwlarrSearchViewModel {
        f.io = dispatcher
        return ProwlarrSearchViewModel(f.repository()).also { owners.put("search", it) }
    }

    @Test
    fun `controls and search produce StateFlow content`() = runTest(dispatcher) {
        val f = SearchFixture()
        f.api.releases += ReleaseResource(title = "Movie", guid = "a", indexerId = 2)
        val vm = model(f)
        advanceUntilIdle()
        vm.setQuery("Movie")
        vm.toggleIndexer(2)
        vm.toggleCategory(2000)
        vm.toggleCategory(2000)
        vm.setSort(SearchSort.SEEDERS)
        vm.search()
        advanceUntilIdle()
        assertEquals(setOf(2), vm.state.value.request.indexerIds)
        assertTrue(vm.state.value.request.categories.isEmpty())
        assertEquals(SearchSort.SEEDERS, vm.state.value.sort)
        assertTrue(vm.state.value.results is LceState.Content)
    }

    @Test
    fun `offline search error retains cached rows and exposes error`() = runTest(dispatcher) {
        val f = SearchFixture()
        f.api.releases += ReleaseResource(guid = "a")
        f.repository().search(SearchRequest())
        val vm = model(f)
        advanceUntilIdle()
        f.api.failure = { IOException() }
        vm.search(true)
        advanceUntilIdle()
        assertTrue(vm.state.value.results is LceState.Content)
        assertNotNull(vm.state.value.resultsError)
        assertFalse((vm.state.value.results as LceState.Content).refreshing)
    }

    @Test
    fun `empty search and uncached failures map to LCE`() = runTest(dispatcher) {
        val f = SearchFixture()
        val vm = model(f)
        advanceUntilIdle()
        vm.search()
        advanceUntilIdle()
        assertEquals(LceState.Empty, vm.state.value.results)
        f.api.failure = { IOException() }
        vm.setQuery("uncached")
        vm.search()
        advanceUntilIdle()
        assertTrue(vm.state.value.results is LceState.Error)
    }

    @Test
    fun `a newer search cancels the older request`() = runTest(dispatcher) {
        val f = SearchFixture()
        val vm = model(f)
        advanceUntilIdle()
        f.api.latency = { if (it.operation == "getSearch") delay(1_000) }
        f.api.releases += ReleaseResource(title = "new", guid = "new")
        vm.setQuery("old")
        vm.search()
        runCurrent()
        vm.setQuery("new")
        vm.search()
        advanceUntilIdle()
        val rows = (vm.state.value.results as LceState.Content).data
        assertEquals("new", rows.single().guid)
    }

    @Test
    fun `grabs show optimistic sending then queued and queue removal updates state`() =
        runTest(dispatcher) {
            val f = SearchFixture()
            val vm = model(f)
            advanceUntilIdle()
            val release = ReleaseResource(guid = "a", indexerId = 2)
            f.api.latency = { if (it.operation == "createSearch") delay(1_000) }
            f.api.failure = { IOException() }
            vm.grab(release, 9)
            assertTrue(release.rowKey() in vm.state.value.grabbing)
            advanceUntilIdle()
            assertTrue(release.rowKey() in vm.state.value.queued)
            assertTrue(vm.state.value.grabbing.isEmpty())
            f.queue.remove(f.queue.pending().single().actionId)
            advanceUntilIdle()
            assertTrue(vm.state.value.queued.isEmpty())
        }

    @Test
    fun `rejected grab rolls back optimistic state`() = runTest(dispatcher) {
        val f = SearchFixture()
        val vm = model(f)
        advanceUntilIdle()
        f.api.errorStatus = { 400 }
        vm.grab(ReleaseResource(guid = "a"), 9)
        advanceUntilIdle()
        assertTrue(vm.state.value.grabbing.isEmpty())
        assertTrue(vm.state.value.grabbed.isEmpty())
        assertNotNull(vm.state.value.error)
    }

    @Test
    fun `last request restores after model recreation`() = runTest(dispatcher) {
        val f = SearchFixture()
        f.api.releases += ReleaseResource(title = "Movie", guid = "a")
        f.repository().rememberRequest(SearchRequest("Movie"))
        val vm = model(f)
        advanceUntilIdle()
        assertEquals("Movie", vm.state.value.request.query)
        assertEquals("a", (vm.state.value.results as LceState.Content).data.single().guid)
    }

    @Test
    fun `statistics period and history paging update independent sections`() = runTest(dispatcher) {
        val f = SearchFixture()
        val vm = model(f)
        advanceUntilIdle()
        vm.loadStats(30)
        vm.loadHistory(2)
        advanceUntilIdle()
        assertEquals(30, vm.state.value.days)
        assertEquals(2, vm.state.value.page)
        assertTrue(vm.state.value.stats is LceState.Content)
        assertEquals(LceState.Empty, vm.state.value.history)
    }

    @Test
    fun `feature registers all routes idempotently`() {
        val registry = ArrRouteRegistry()
        ProwlarrSearchFeature.register(registry)
        ProwlarrSearchFeature.register(registry)
        assertEquals(3, registry.nestedRoutes().size)
        SearchPage.entries.forEach { page ->
            val route = ProwlarrSearchFeature.route(ServerId("server-1"), page)!!
            assertEquals("prowlarr-search", route.owner)
            assertTrue(route.path.endsWith("server-1"))
        }
    }
}
