package dev.appolon.homelab.arr.feature.prowlarr.indexers.vm

import androidx.lifecycle.viewModelScope
import dev.appolon.homelab.arr.api.prowlarr.FakeProwlarrIndexers
import dev.appolon.homelab.arr.api.prowlarr.model.AppProfileResource
import dev.appolon.homelab.arr.api.prowlarr.model.Field
import dev.appolon.homelab.arr.api.prowlarr.model.IndexerCategory
import dev.appolon.homelab.arr.api.prowlarr.model.IndexerResource
import dev.appolon.homelab.arr.core.ServerId
import dev.appolon.homelab.arr.feature.prowlarr.indexers.FakeActionQueue
import dev.appolon.homelab.arr.feature.prowlarr.indexers.FakeEntityStore
import dev.appolon.homelab.arr.feature.prowlarr.indexers.data.FieldForms
import dev.appolon.homelab.arr.feature.prowlarr.indexers.data.ProwlarrIndexersRepository
import dev.appolon.homelab.arr.feature.prowlarr.indexers.fakeIndexersApis
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.json.JsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class IndexerEditorViewModelTest {

    private val serverId = ServerId("s1")
    private val dispatcher = UnconfinedTestDispatcher()
    private lateinit var fake: FakeProwlarrIndexers
    private lateinit var store: FakeEntityStore
    private lateinit var queue: FakeActionQueue
    private lateinit var repository: ProwlarrIndexersRepository

    private val schemaFields = listOf(
        Field(name = "baseUrl", label = "Base URL", value = JsonPrimitive("https://ipt.example")),
        Field(name = "apiKey", label = "API Key", type = "password"),
        Field(name = "seedCriteria.seedRatio", label = "Seed Ratio", type = "number"),
        Field(name = "internal", hidden = "hidden", value = JsonPrimitive("x")),
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        fake = FakeProwlarrIndexers()
        store = FakeEntityStore()
        queue = FakeActionQueue()
        repository = ProwlarrIndexersRepository(
            serverId = serverId,
            store = store,
            apis = fakeIndexersApis(fake),
            actionQueue = queue,
            now = { 1_000L },
            io = dispatcher,
        )
        fake.indexerSchemas += IndexerResource(
            definitionName = "iptorrents",
            name = "IPTorrents",
            fields = schemaFields,
            presets = listOf(
                IndexerResource(
                    name = "Ratio 1.5",
                    fields = listOf(
                        Field(name = "seedCriteria.seedRatio", value = JsonPrimitive(1.5)),
                    ),
                ),
            ),
            capabilities = dev.appolon.homelab.arr.api.prowlarr.model.IndexerCapabilityResource(
                categories = listOf(IndexerCategory(id = 2000, name = "Movies")),
            ),
        )
        fake.defaultCategories += listOf(
            IndexerCategory(
                id = 2000,
                name = "Movies",
                subCategories = listOf(
                    IndexerCategory(id = 2010, name = "Foreign"),
                    IndexerCategory(id = 2020, name = "Other"),
                ),
            ),
            IndexerCategory(id = 5000, name = "TV"),
        )
        fake.appProfiles[1] = AppProfileResource(id = 1, name = "Standard")
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `add flow starts at the picker and filters schemas`() = runTest(dispatcher) {
        val vm = IndexerEditorViewModel(repository)
        vm.viewModelScope.coroutineContext.cancelChildren()
        assertTrue(vm.state.value.pickingSchema)
        assertTrue(vm.state.value.filteredSchemas.any { it.name == "IPTorrents" })
        vm.setSchemaSearch("ipt")
        assertEquals(1, vm.state.value.filteredSchemas.size)
    }

    @Test
    fun `picking a schema seeds values presets and categories`() = runTest(dispatcher) {
        val vm = IndexerEditorViewModel(repository)
        vm.viewModelScope.coroutineContext.cancelChildren()
        vm.pickSchema(vm.state.value.filteredSchemas.first())
        val state = vm.state.value
        assertFalse(state.pickingSchema)
        assertEquals("https://ipt.example", FieldForms.textOf(state.values["baseUrl"]))
        assertEquals(1, state.presets.size)
        assertEquals(setOf(2000), state.selectedCategories)
        assertEquals(3, state.generalFields.size + state.seedCriteriaFields.size)
        assertEquals(1, state.seedCriteriaFields.size)
    }

    @Test
    fun `field value changes and presets overlay`() = runTest(dispatcher) {
        val vm = IndexerEditorViewModel(repository)
        vm.viewModelScope.coroutineContext.cancelChildren()
        vm.pickSchema(vm.state.value.filteredSchemas.first())
        vm.setFieldValue("apiKey", JsonPrimitive("secret"))
        assertEquals("secret", FieldForms.textOf(vm.state.value.values["apiKey"]))
        vm.applyPreset(0)
        assertEquals("1.5", FieldForms.textOf(vm.state.value.values["seedCriteria.seedRatio"]))
        assertEquals("secret", FieldForms.textOf(vm.state.value.values["apiKey"]))
    }

    @Test
    fun `toggling a parent category selects its subtree`() = runTest(dispatcher) {
        val vm = IndexerEditorViewModel(repository)
        vm.viewModelScope.coroutineContext.cancelChildren()
        vm.pickSchema(vm.state.value.filteredSchemas.first())
        // The schema starts with Movies (2000) selected.
        assertEquals(setOf(2000), vm.state.value.selectedCategories)
        vm.toggleCategory(2010)
        assertEquals(setOf(2000, 2010), vm.state.value.selectedCategories)
        vm.toggleCategory(2000)
        assertEquals(emptySet<Int>(), vm.state.value.selectedCategories)
        vm.toggleCategory(2000)
        assertEquals(setOf(2000, 2010, 2020), vm.state.value.selectedCategories)
    }

    @Test
    fun `save with a blank name reports NameMissing`() = runTest(dispatcher) {
        val vm = IndexerEditorViewModel(repository)
        vm.viewModelScope.coroutineContext.cancelChildren()
        vm.pickSchema(vm.state.value.filteredSchemas.first())
        vm.save()
        assertEquals(IndexerEditorMessage.NameMissing, vm.state.value.message)
        assertTrue(fake.calls.none { it.operation == "createIndexer" })
    }

    @Test
    fun `save online posts the form values and category selection`() = runTest(dispatcher) {
        val vm = IndexerEditorViewModel(repository)
        vm.viewModelScope.coroutineContext.cancelChildren()
        vm.pickSchema(vm.state.value.filteredSchemas.first())
        vm.setName("My IPT")
        vm.setPriority(60)
        vm.setAppProfileId(1)
        vm.setFieldValue("apiKey", JsonPrimitive("secret"))
        vm.toggleCategory(2010)
        vm.save()
        assertEquals(IndexerEditorMessage.Saved, vm.state.value.message)

        val created = fake.indexers.values.single { it.name == "My IPT" }
        assertEquals("secret", FieldForms.textOf(created.fields?.first { it.name == "apiKey" }?.value))
        assertEquals(60, created.priority)
        assertEquals(1, created.appProfileId)
        assertEquals(listOf(2000, 2010), created.capabilities.categories?.map { it.id })
    }

    @Test
    fun `save offline queues the create`() = runTest(dispatcher) {
        val vm = IndexerEditorViewModel(repository)
        vm.viewModelScope.coroutineContext.cancelChildren()
        vm.pickSchema(vm.state.value.filteredSchemas.first())
        vm.setName("My IPT")
        fake.failure = { call -> if (call.operation == "createIndexer") IOException("off") else null }
        vm.save()
        assertEquals(IndexerEditorMessage.SavedQueued, vm.state.value.message)
        assertEquals("api/v1/indexer", queue.pending().single().path)
    }

    @Test
    fun `edit mode loads the existing resource into the form`() = runTest(dispatcher) {
        fake.indexers[7] = IndexerResource(
            id = 7,
            name = "Existing",
            enable = false,
            priority = 12,
            fields = schemaFields,
            tags = listOf(3),
        )
        fake.tags[3] = dev.appolon.homelab.arr.api.prowlarr.model.TagResource(id = 3, label = "usenet")
        val vm = IndexerEditorViewModel(repository, editIndexerId = 7)
        vm.viewModelScope.coroutineContext.cancelChildren()
        val state = vm.state.value
        assertFalse(state.pickingSchema)
        assertEquals("Existing", state.name)
        assertEquals(false, state.enable)
        assertEquals(12, state.priority)
        assertEquals("https://ipt.example", FieldForms.textOf(state.values["baseUrl"]))
        assertEquals(listOf("usenet"), state.tagLabels)
    }

    @Test
    fun `test probes the built resource`() = runTest(dispatcher) {
        val vm = IndexerEditorViewModel(repository)
        vm.viewModelScope.coroutineContext.cancelChildren()
        vm.pickSchema(vm.state.value.filteredSchemas.first())
        vm.setName("My IPT")
        vm.test()
        assertEquals(IndexerEditorMessage.TestPassed, vm.state.value.message)
        assertTrue(fake.calls.any { it.operation == "testIndexer" })
    }
}
