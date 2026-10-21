package dev.appolon.homelab.arr.feature.sonarr.settings.system.vm

import dev.appolon.homelab.arr.api.sonarr.model.TagDetailsResource
import dev.appolon.homelab.arr.api.sonarr.model.TagResource
import dev.appolon.homelab.arr.ui.lce.LceState
import java.io.IOException
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SonarrTagsViewModelTest : SonarrSettingsSystemVmTest() {

    private fun seed() {
        api.tagStore[1] = TagResource(id = 1, label = "anime")
        api.tagStore[2] = TagResource(id = 2, label = "4k")
        api.tagDetailsStore[1] = TagDetailsResource(
            id = 1,
            label = "anime",
            seriesIds = listOf(3, 7, 9),
            autoTagIds = listOf(5),
        )
        api.tagDetailsStore[2] = TagDetailsResource(id = 2, label = "4k")
    }

    @Test
    fun `loads tags with usage counts from details`() = runTest {
        seed()
        val vm = withViewModel(::SonarrTagsViewModel) { it.lce }
        val content = (vm.lce.value as LceState.Content).data

        assertEquals(listOf("4k", "anime"), content.rows.map { it.label })
        val anime = content.rows.first { it.label == "anime" }
        assertEquals(3, anime.seriesCount)
        assertEquals(1, anime.autoTagCount)
        assertTrue(anime.used)
        assertFalse(content.rows.first { it.label == "4k" }.used)
    }

    @Test
    fun `create adds the tag through the dialog flow`() = runTest {
        seed()
        val vm = withViewModel(::SonarrTagsViewModel) { it.lce }

        vm.startCreate()
        vm.editLabel("dub")
        assertTrue(vm.editorLabelValid())
        vm.editorConfirmed()
        advanceUntilIdle()

        assertEquals("dub", api.tagStore[3]!!.label)
        assertTrue(vm.state.value.notice == TagsNotice.CREATED)
    }

    @Test
    fun `duplicate labels are rejected`() = runTest {
        seed()
        val vm = withViewModel(::SonarrTagsViewModel) { it.lce }

        vm.startCreate()
        vm.editLabel("ANIME")
        assertFalse(vm.editorLabelValid())

        vm.editorCancelled()
        assertTrue(!vm.state.value.editorOpen)
    }

    @Test
    fun `rename keeps the same row`() = runTest {
        seed()
        val vm = withViewModel(::SonarrTagsViewModel) { it.lce }

        vm.startRename(TagResource(id = 2, label = "4k"))
        vm.editLabel("uhd")
        vm.editorConfirmed()
        advanceUntilIdle()

        assertEquals("uhd", api.tagStore[2]!!.label)
        assertTrue(vm.state.value.notice == TagsNotice.RENAMED)
    }

    @Test
    fun `offline create queues and renders a temp row`() = runTest {
        seed()
        api.error = { op -> if (op == "Tag.create") IOException("offline") else null }
        val vm = withViewModel(::SonarrTagsViewModel) { it.lce }

        vm.startCreate()
        vm.editLabel("offline-tag")
        vm.editorConfirmed()
        advanceUntilIdle()

        assertTrue(vm.state.value.notice == TagsNotice.CREATED_QUEUED)
        val rows = (vm.lce.value as LceState.Content).data.rows
        assertTrue(rows.any { it.label == "offline-tag" && it.tag.id < 0 })
    }

    @Test
    fun `delete online drops the row`() = runTest {
        seed()
        val vm = withViewModel(::SonarrTagsViewModel) { it.lce }

        vm.requestDelete(TagResource(id = 1, label = "anime"))
        vm.deleteConfirmed()
        advanceUntilIdle()

        assertTrue(api.tagStore[1] == null)
        assertTrue(vm.state.value.notice == TagsNotice.DELETED)
        val labels = (vm.lce.value as LceState.Content).data.rows.map { it.label }
        assertTrue("anime" !in labels)
    }

    @Test
    fun `offline delete queues and drops the row optimistically`() = runTest {
        seed()
        api.error = { op -> if (op == "Tag.delete") IOException("offline") else null }
        val vm = withViewModel(::SonarrTagsViewModel) { it.lce }

        vm.requestDelete(TagResource(id = 1, label = "anime"))
        vm.deleteConfirmed()
        advanceUntilIdle()

        assertTrue(vm.state.value.notice == TagsNotice.DELETED_QUEUED)
        assertEquals(1, vm.state.value.pendingActions)
        val labels = (vm.lce.value as LceState.Content).data.rows.map { it.label }
        assertTrue("anime" !in labels)
    }
}
