package dev.appolon.homelab.arr.feature.lidarr.settings.system.vm

import dev.appolon.homelab.arr.api.lidarr.model.AutoTaggingResource
import dev.appolon.homelab.arr.api.lidarr.model.AutoTaggingSpecificationSchema
import dev.appolon.homelab.arr.api.lidarr.model.TagResource
import dev.appolon.homelab.arr.feature.lidarr.settings.system.data.LidarrAutoTaggingConditions
import dev.appolon.homelab.arr.ui.lce.LceState
import java.io.IOException
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LidarrAutoTaggingViewModelTest : LidarrSettingsSystemVmTest() {

    private fun seed() {
        api.tags[1] = TagResource(id = 1, label = "vinyl")
        api.autoTaggings[2] = AutoTaggingResource(
            id = 2,
            name = "Vinyl folder",
            tags = setOf(1),
            specifications = listOf(
                AutoTaggingSpecificationSchema(
                    implementation = "StatusSpecification",
                    implementationName = "Status",
                ),
            ),
        )
    }

    @Test
    fun `loads rules with tag labels and condition counts`() = runTest {
        seed()
        val vm = withViewModel(::LidarrAutoTaggingViewModel) { it.lce }
        val content = (vm.lce.value as LceState.Content).data

        val row = content.rows.single()
        assertEquals("Vinyl folder", row.name)
        assertEquals(listOf("vinyl"), row.tagLabels)
        assertEquals(1, row.conditionCount)
    }

    @Test
    fun `delete online removes the rule`() = runTest {
        seed()
        val vm = withViewModel(::LidarrAutoTaggingViewModel) { it.lce }

        vm.requestDelete(vm.state.value.rules.single())
        vm.deleteConfirmed()
        advanceUntilIdle()

        assertTrue(api.autoTaggings.isEmpty())
        assertTrue(vm.state.value.notice == AutoTaggingNotice.DELETED)
    }

    @Test
    fun `delete offline queues and drops the row optimistically`() = runTest {
        seed()
        api.error = { op -> if (op == "deleteAutoTagging") IOException("offline") else null }
        val vm = withViewModel(::LidarrAutoTaggingViewModel) { it.lce }

        vm.requestDelete(vm.state.value.rules.single())
        vm.deleteConfirmed()
        advanceUntilIdle()

        assertTrue(vm.state.value.notice == AutoTaggingNotice.DELETED_QUEUED)
        assertEquals(1, vm.state.value.pendingActions)
        assertTrue(vm.lce.value is LceState.Empty)
    }

    @Test
    fun `editor loads an existing rule into the draft`() = runTest {
        seed()
        val vm = withViewModel({ LidarrAutoTaggingEditorViewModel(2, it) }) { it.state }
        advanceUntilIdle()

        assertTrue(vm.state.value.loaded)
        assertEquals("Vinyl folder", vm.state.value.draft.name)
        assertEquals(setOf(1), vm.state.value.draft.tagIds)
        assertEquals(1, vm.state.value.draft.conditions.size)
        assertTrue(!vm.state.value.isNew)
    }

    @Test
    fun `editor for a new rule starts blank and loaded`() = runTest {
        seed()
        val vm = withViewModel({ LidarrAutoTaggingEditorViewModel(null, it) }) { it.state }
        advanceUntilIdle()

        assertTrue(vm.state.value.isNew)
        assertTrue(vm.state.value.loaded)
        assertEquals("", vm.state.value.draft.name)
    }

    @Test
    fun `editor seeds the picker from the static catalog until the schema loads`() = runTest {
        seed()
        api.error = { op -> if (op == "getAutoTaggingSchema") IOException("offline") else null }
        val vm = withViewModel({ LidarrAutoTaggingEditorViewModel(null, it) }) { it.state }

        assertEquals(LidarrAutoTaggingConditions.entries, vm.state.value.conditions)
        assertTrue(vm.state.value.conditions.isNotEmpty())
    }

    @Test
    fun `a live schema refines the picker over the static catalog`() = runTest {
        seed()
        val vm = withViewModel({ LidarrAutoTaggingEditorViewModel(null, it) }) { it.state }

        val parsed = vm.state.value.conditions
        assertEquals(1, parsed.size)
        assertEquals("Root Folder", parsed.single().implementationName)
    }

    @Test
    fun `addCondition seeds a catalog condition with its default fields`() = runTest {
        seed()
        val vm = withViewModel({ LidarrAutoTaggingEditorViewModel(null, it) }) { it.state }

        vm.addCondition(LidarrAutoTaggingConditions.byImplementation("StatusSpecification")!!)
        vm.toggleTag(1)

        val draft = vm.state.value.draft
        assertEquals(1, draft.conditions.size)
        assertEquals("StatusSpecification", draft.conditions.single().specification.implementation)
        assertTrue(draft.conditions.single().fieldValues.containsKey("status"))
        assertEquals(setOf(1), draft.tagIds)
        assertTrue(vm.state.value.dirty)
    }

    @Test
    fun `editCondition writes field edits that round-trip onto the wire rule`() = runTest {
        seed()
        val vm = withViewModel({ LidarrAutoTaggingEditorViewModel(2, it) }) { it.state }

        vm.addCondition(LidarrAutoTaggingConditions.byImplementation("QualityProfileSpecification")!!)
        vm.editCondition(vm.state.value.draft.conditions.lastIndex) { condition ->
            condition.copy(
                fieldValues = condition.fieldValues + ("value" to kotlinx.serialization.json.JsonPrimitive(3)),
            )
        }
        vm.edit { it.copy(name = "Renamed") }
        vm.save()
        advanceUntilIdle()

        val saved = api.autoTaggings.values.first { it.id == 2 }
        assertEquals("Renamed", saved.name)
        val quality = saved.specifications!!.first { it.implementation == "QualityProfileSpecification" }
        assertEquals(3, (quality.fields!!.single { it.name == "value" }.value as kotlinx.serialization.json.JsonPrimitive).content.toInt())
    }

    @Test
    fun `save blocks on a blank name`() = runTest {
        seed()
        val vm = withViewModel({ LidarrAutoTaggingEditorViewModel(null, it) }) { it.state }

        vm.save()
        advanceUntilIdle()

        assertTrue(vm.state.value.nameError)
        assertTrue(api.autoTaggings.values.none { it.id < 0 || it.id > 2 })
    }

    @Test
    fun `offline create queues with a temp id`() = runTest {
        seed()
        api.error = { op -> if (op == "addAutoTagging") IOException("offline") else null }
        val vm = withViewModel({ LidarrAutoTaggingEditorViewModel(null, it) }) { it.state }

        vm.edit { it.copy(name = "Offline rule") }
        vm.save()
        advanceUntilIdle()

        assertTrue(vm.state.value.notice == AutoTaggingNotice.SAVED_QUEUED)
        val cached = store.getAll(
            dev.appolon.homelab.arr.core.ServerId("s1"),
            dev.appolon.homelab.arr.feature.lidarr.settings.system.data.LidarrSettingsSystemRepository.AUTO_TAGGING_TYPE,
            AutoTaggingResource.serializer(),
        )
        assertTrue(cached.any { it.name == "Offline rule" && it.id < 0 })
    }
}
