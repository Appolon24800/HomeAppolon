package dev.appolon.homelab.arr.feature.radarr.settings.system.vm

import dev.appolon.homelab.arr.api.radarr.FakeRadarrQueue
import dev.appolon.homelab.arr.api.radarr.FakeRadarrSystem
import dev.appolon.homelab.arr.api.radarr.model.AutoTaggingResource
import dev.appolon.homelab.arr.api.radarr.model.AutoTaggingSpecificationSchema
import dev.appolon.homelab.arr.api.radarr.model.Field
import dev.appolon.homelab.arr.api.radarr.model.TagResource
import dev.appolon.homelab.arr.core.ServerId
import dev.appolon.homelab.arr.feature.radarr.settings.system.FakeSystemActionQueue
import dev.appolon.homelab.arr.feature.radarr.settings.system.FakeSystemEntityStore
import dev.appolon.homelab.arr.feature.radarr.settings.system.data.RadarrSettingsSystemRepository
import dev.appolon.homelab.arr.ui.lce.LceState
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.json.JsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RadarrAutoTaggingViewModelTest {

    private lateinit var api: FakeRadarrSystem
    private lateinit var store: FakeSystemEntityStore
    private lateinit var queue: FakeSystemActionQueue
    private var now: Long = 1_000_000L

    @Before
    fun setUp() {
        api = FakeRadarrSystem()
        store = FakeSystemEntityStore()
        queue = FakeSystemActionQueue()
        store.clock = { now }
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun TestScope.repository() = RadarrSettingsSystemRepository(
        serverId = ServerId("s1"),
        store = store,
        hostConfigApi = api,
        backupApi = api,
        updateApi = api,
        updateLogFileApi = api,
        systemApi = api,
        taskApi = api,
        diskSpaceApi = api,
        healthApi = api,
        tagApi = api,
        tagDetailsApi = api,
        autoTaggingApi = api,
        commandApi = FakeRadarrQueue(),
        actionQueue = queue,
        now = { now },
        ioContext = StandardTestDispatcher(testScheduler),
    )

    private fun TestScope.withListViewModel(): RadarrAutoTaggingViewModel {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val vm = RadarrAutoTaggingViewModel(repository())
        backgroundScope.launch { vm.lce.collect { } }
        advanceUntilIdle()
        return vm
    }

    private fun TestScope.withEditorViewModel(
        ruleId: Int?,
    ): RadarrAutoTaggingEditorViewModel {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val vm = RadarrAutoTaggingEditorViewModel(ruleId, repository())
        backgroundScope.launch { vm.state.collect { } }
        advanceUntilIdle()
        return vm
    }

    private fun seed() {
        api.tagItems += TagResource(id = 1, label = "ssd")
        api.autoTaggingItems += AutoTaggingResource(
            id = 2,
            name = "SSD movies",
            removeTagsAutomatically = true,
            tags = setOf(1),
            specifications = listOf(
                AutoTaggingSpecificationSchema(
                    id = 3,
                    name = "Root Folder",
                    implementation = "RootFolderSpecification",
                    implementationName = "Root Folder",
                    required = true,
                    fields = listOf(
                        Field(order = 1, name = "value", label = "Root Folder", value = JsonPrimitive("/movies")),
                    ),
                ),
            ),
        )
    }

    @Test
    fun `list rows show tag labels and condition counts`() = runTest {
        seed()
        val vm = withListViewModel()
        val content = (vm.lce.value as LceState.Content).data

        val row = content.rows.single()
        assertEquals("SSD movies", row.name)
        assertEquals(listOf("ssd"), row.tagLabels)
        assertEquals(1, row.conditionCount)
    }

    @Test
    fun `delete confirms first and removes the rule`() = runTest {
        seed()
        val vm = withListViewModel()

        vm.requestDelete(vm.state.value.rules.single())
        assertNotNull(vm.state.value.confirmDeleteFor)
        vm.deleteConfirmed()
        advanceUntilIdle()

        assertTrue(api.autoTaggingItems.isEmpty())
        assertTrue(vm.state.value.notice == AutoTaggingNotice.DELETED)
    }

    @Test
    fun `editor loads the rule draft from the cache`() = runTest {
        seed()
        val vm = withEditorViewModel(ruleId = 2)
        advanceUntilIdle()

        assertTrue(vm.state.value.loaded)
        assertEquals("SSD movies", vm.state.value.draft.name)
        assertEquals(setOf(1), vm.state.value.draft.tagIds)
        assertEquals(1, vm.state.value.draft.conditions.size)
        assertEquals("/movies", vm.state.value.draft.conditions.single().fieldValues["value"]?.let {
            (it as JsonPrimitive).content
        })
    }

    @Test
    fun `blank names are rejected before saving`() = runTest {
        seed()
        val vm = withEditorViewModel(ruleId = 2)

        vm.edit { it.copy(name = "  ") }
        vm.save()
        advanceUntilIdle()

        assertTrue(vm.state.value.nameError)
        assertEquals("SSD movies", api.autoTaggingItems.single().name)
    }

    @Test
    fun `edits write through to the wire fields on save`() = runTest {
        seed()
        val vm = withEditorViewModel(ruleId = 2)

        vm.edit { it.copy(name = "Renamed") }
        vm.toggleTag(1)
        vm.editCondition(0) {
            it.copy(
                specification = it.specification.copy(negate = true),
                fieldValues = it.fieldValues + ("value" to JsonPrimitive("/ssd")),
            )
        }
        vm.save()
        advanceUntilIdle()

        val saved = api.autoTaggingItems.single()
        assertEquals("Renamed", saved.name)
        assertTrue(saved.specifications!!.single().negate)
        assertEquals(
            "/ssd",
            (saved.specifications!!.single().fields!!.single().value as JsonPrimitive).content,
        )
        assertTrue(vm.state.value.notice == AutoTaggingNotice.SAVED)
    }

    @Test
    fun `addCondition seeds values from the schema entry`() = runTest {
        seed()
        api.autoTaggingSchemaResult = listOf(
            AutoTaggingSpecificationSchema(
                implementation = "QualitySpecification",
                implementationName = "Quality",
                fields = listOf(
                    Field(order = 1, name = "value", label = "Quality", value = JsonPrimitive("1080")),
                ),
            ),
        )
        val vm = withEditorViewModel(ruleId = 2)
        advanceUntilIdle()

        vm.addCondition(api.autoTaggingSchemaResult.single())
        assertEquals(2, vm.state.value.draft.conditions.size)
        val added = vm.state.value.draft.conditions.last()
        assertEquals("QualitySpecification", added.specification.implementation)
        assertEquals(
            "1080",
            (added.fieldValues["value"] as JsonPrimitive).content,
        )
    }

    @Test
    fun `removeCondition drops the condition by index`() = runTest {
        seed()
        val vm = withEditorViewModel(ruleId = 2)

        vm.removeCondition(0)
        assertTrue(vm.state.value.draft.conditions.isEmpty())
    }

    @Test
    fun `new rule editor saves as create and queues offline`() = runTest {
        seed()
        val vm = withEditorViewModel(ruleId = null)

        vm.edit { it.copy(name = "Brand new") }
        api.errorFor = { IOException("offline") }
        vm.save()
        advanceUntilIdle()

        assertTrue(vm.state.value.notice == AutoTaggingNotice.SAVED_QUEUED)
        assertEquals("POST", queue.pending().single().method)
        assertEquals("api/v3/autotagging", queue.pending().single().path)
        val cached = store.getAll(
            ServerId("s1"),
            RadarrSettingsSystemRepository.AUTO_TAGGING_TYPE,
            AutoTaggingResource.serializer(),
        ).first { it.name == "Brand new" }
        assertTrue(cached.id < 0)
        assertFalse(vm.state.value.dirty)
    }
}
