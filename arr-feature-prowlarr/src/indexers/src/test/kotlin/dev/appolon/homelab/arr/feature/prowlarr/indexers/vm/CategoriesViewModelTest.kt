package dev.appolon.homelab.arr.feature.prowlarr.indexers.vm

import androidx.lifecycle.viewModelScope
import dev.appolon.homelab.arr.api.prowlarr.FakeProwlarrIndexers
import dev.appolon.homelab.arr.api.prowlarr.model.IndexerCategory
import dev.appolon.homelab.arr.api.prowlarr.model.IndexerResource
import dev.appolon.homelab.arr.core.ServerId
import dev.appolon.homelab.arr.feature.prowlarr.indexers.FakeActionQueue
import dev.appolon.homelab.arr.feature.prowlarr.indexers.FakeEntityStore
import dev.appolon.homelab.arr.feature.prowlarr.indexers.data.ProwlarrIndexersRepository
import dev.appolon.homelab.arr.feature.prowlarr.indexers.fakeIndexersApis
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CategoriesViewModelTest {

    private val serverId = ServerId("s1")
    private val dispatcher = UnconfinedTestDispatcher()
    private lateinit var fake: FakeProwlarrIndexers
    private lateinit var repository: ProwlarrIndexersRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        fake = FakeProwlarrIndexers()
        repository = ProwlarrIndexersRepository(
            serverId = serverId,
            store = FakeEntityStore(),
            apis = fakeIndexersApis(fake),
            actionQueue = FakeActionQueue(),
            now = { 1_000L },
            io = dispatcher,
        )
        fake.defaultCategories += listOf(
            IndexerCategory(
                id = 2000,
                name = "Movies",
                subCategories = listOf(IndexerCategory(id = 2010, name = "Foreign")),
            ),
            IndexerCategory(id = 5000, name = "TV"),
        )
        listOf(
            IndexerResource(
                id = 1,
                name = "Alpha",
                capabilities = dev.appolon.homelab.arr.api.prowlarr.model.IndexerCapabilityResource(
                    categories = listOf(IndexerCategory(id = 2010)),
                ),
            ),
            IndexerResource(
                id = 2,
                name = "Beta",
                capabilities = dev.appolon.homelab.arr.api.prowlarr.model.IndexerCapabilityResource(
                    categories = listOf(IndexerCategory(id = 2010, name = "Foreign")),
                ),
            ),
        ).forEach { fake.indexers[it.id] = it }
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `rows show top level categories with supporting indexer counts`() = runTest(dispatcher) {
        val vm = CategoriesViewModel(repository)
        vm.viewModelScope.coroutineContext.cancelChildren()
        val state = vm.state.value
        assertTrue(state.loaded)
        assertEquals(2, state.rows.size)
        val movies = state.rows.first { it.category.id == 2000 }
        assertEquals(0, movies.supportingIndexers)
        assertEquals(2, state.totalIndexers)
    }

    @Test
    fun `expanding a parent reveals subcategories with their counts`() = runTest(dispatcher) {
        val vm = CategoriesViewModel(repository)
        vm.viewModelScope.coroutineContext.cancelChildren()
        vm.toggleExpanded(2000)
        val state = vm.state.value
        assertEquals(3, state.rows.size)
        val foreign = state.rows.first { it.category.id == 2010 }
        assertEquals(1, foreign.depth)
        assertEquals(2, foreign.supportingIndexers)
        vm.toggleExpanded(2000)
        assertEquals(2, vm.state.value.rows.size)
    }
}
