package dev.appolon.homelab.arr.feature.lidarr.settings.system.vm

import dev.appolon.homelab.arr.api.lidarr.model.TagDetailsResource
import dev.appolon.homelab.arr.api.lidarr.model.TagResource
import dev.appolon.homelab.arr.ui.lce.LceState
import java.io.IOException
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LidarrTagsViewModelTest : LidarrSettingsSystemVmTest() {

    private fun seed() {
        api.tags[1] = TagResource(id = 1, label = "vinyl")
        api.tags[2] = TagResource(id = 2, label = "flac")
        api.tagDetails[1] = TagDetailsResource(
            id = 1,
            label = "vinyl",
            artistIds = listOf(3, 7, 9),
            autoTagIds = listOf(5),
        )
        api.tagDetails[2] = TagDetailsResource(id = 2, label = "flac")
    }

    @Test
    fun `loads tags with usage counts from details`() = runTest {
        seed()
        val vm = withViewModel(::LidarrTagsViewModel) { it.lce }
        val content = (vm.lce.value as LceState.Content).data

        assertEquals(listOf("flac", "vinyl"), content.rows.map { it.label })
        val vinyl = content.rows.first { it.label == "vinyl" }
        assertEquals(3, vinyl.artistCount)
        assertEquals(1, vinyl.autoTagCount)
        assertTrue(vinyl.used)
        assertFalse(content.rows.first { it.label == "flac" }.used)
    }

    @Test
    fun `create adds the tag through the dialog flow`() = runTest {
        seed()
        val vm = withViewModel(::LidarrTagsViewModel) { it.lce }

        vm.startCreate()
        vm.editLabel("dub")
        assertTrue(vm.editorLabelValid())
        vm.editorConfirmed()
        advanceUntilIdle()

        assertEquals("dub", api.tags[3]!!.label)
        assertTrue(vm.state.value.notice == TagsNotice.CREATED)
    }

    @Test
    fun `duplicate labels are rejected`() = runTest {
        seed()
        val vm = withViewModel(::LidarrTagsViewModel) { it.lce }

        vm.startCreate()
        vm.editLabel("VINYL")
        assertFalse(vm.editorLabelValid())

        vm.editorCancelled()
        assertTrue(!vm.state.value.editorOpen)
    }

    @Test
    fun `rename keeps the same row`() = runTest {
        seed()
        val vm = withViewModel(::LidarrTagsViewModel) { it.lce }

        vm.startRename(TagResource(id = 2, label = "flac"))
        vm.editLabel("lossless")
        vm.editorConfirmed()
        advanceUntilIdle()

        assertEquals("lossless", api.tags[2]!!.label)
        assertTrue(vm.state.value.notice == TagsNotice.RENAMED)
    }

    @Test
    fun `offline create queues and renders a temp row`() = runTest {
        seed()
        api.error = { op -> if (op == "addTag") IOException("offline") else null }
        val vm = withViewModel(::LidarrTagsViewModel) { it.lce }

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
        val vm = withViewModel(::LidarrTagsViewModel) { it.lce }

        vm.requestDelete(TagResource(id = 1, label = "vinyl"))
        vm.deleteConfirmed()
        advanceUntilIdle()

        assertTrue(api.tags[1] == null)
        assertTrue(vm.state.value.notice == TagsNotice.DELETED)
        val labels = (vm.lce.value as LceState.Content).data.rows.map { it.label }
        assertTrue("vinyl" !in labels)
    }

    @Test
    fun `offline delete queues and drops the row optimistically`() = runTest {
        seed()
        api.error = { op -> if (op == "deleteTag") IOException("offline") else null }
        val vm = withViewModel(::LidarrTagsViewModel) { it.lce }

        vm.requestDelete(TagResource(id = 1, label = "vinyl"))
        vm.deleteConfirmed()
        advanceUntilIdle()

        assertTrue(vm.state.value.notice == TagsNotice.DELETED_QUEUED)
        assertEquals(1, vm.state.value.pendingActions)
        val labels = (vm.lce.value as LceState.Content).data.rows.map { it.label }
        assertTrue("vinyl" !in labels)
    }
}
