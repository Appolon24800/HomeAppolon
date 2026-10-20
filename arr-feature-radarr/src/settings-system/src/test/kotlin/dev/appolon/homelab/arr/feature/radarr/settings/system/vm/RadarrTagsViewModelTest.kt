package dev.appolon.homelab.arr.feature.radarr.settings.system.vm

import dev.appolon.homelab.arr.api.radarr.FakeRadarrQueue
import dev.appolon.homelab.arr.api.radarr.FakeRadarrSystem
import dev.appolon.homelab.arr.api.radarr.model.TagDetailsResource
import dev.appolon.homelab.arr.api.radarr.model.TagResource
import dev.appolon.homelab.arr.core.ServerId
import dev.appolon.homelab.arr.feature.radarr.settings.system.FakeSystemActionQueue
import dev.appolon.homelab.arr.data.testing.FakeEntityStore
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
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RadarrTagsViewModelTest {

    private lateinit var api: FakeRadarrSystem
    private lateinit var store: FakeEntityStore
    private lateinit var queue: FakeSystemActionQueue
    private var now: Long = 1_000_000L

    @Before
    fun setUp() {
        api = FakeRadarrSystem()
        store = FakeEntityStore()
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

    private fun TestScope.withViewModel(): RadarrTagsViewModel {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val vm = RadarrTagsViewModel(repository())
        backgroundScope.launch { vm.lce.collect { } }
        advanceUntilIdle()
        return vm
    }

    private fun seed() {
        api.tagItems += TagResource(id = 1, label = "hd")
        api.tagItems += TagResource(id = 2, label = "4k")
        api.tagUsage = mapOf(
            1 to TagDetailsResource(id = 1, label = "hd", movieIds = listOf(3, 7)),
        )
    }

    @Test
    fun `rows carry usage counts and the unused label`() = runTest {
        seed()
        val vm = withViewModel()
        val content = (vm.lce.value as LceState.Content).data

        val hd = content.rows.first { it.label == "hd" }
        assertEquals(2, hd.movieCount)
        assertTrue(hd.used)
        val fourK = content.rows.first { it.label == "4k" }
        assertEquals(0, fourK.movieCount)
        assertFalse(fourK.used)
    }

    @Test
    fun `create dialog validates empty and duplicate labels`() = runTest {
        seed()
        val vm = withViewModel()

        vm.startCreate()
        assertTrue(vm.state.value.editorOpen)
        assertFalse(vm.editorLabelValid())

        vm.editLabel("hd")
        assertFalse(vm.editorLabelValid())

        vm.editLabel("anime")
        assertTrue(vm.editorLabelValid())
    }

    @Test
    fun `confirming create saves and closes the dialog`() = runTest {
        seed()
        val vm = withViewModel()

        vm.startCreate()
        vm.editLabel("anime")
        vm.editorConfirmed()
        advanceUntilIdle()

        assertTrue(vm.state.value.notice == TagsNotice.CREATED)
        assertFalse(vm.state.value.editorOpen)
        assertEquals(listOf("hd", "4k", "anime"), api.tagItems.map { it.label })
    }

    @Test
    fun `rename keeps the id`() = runTest {
        seed()
        val vm = withViewModel()

        vm.startRename(TagResource(id = 1, label = "hd"))
        vm.editLabel("HD-All")
        vm.editorConfirmed()
        advanceUntilIdle()

        assertEquals("HD-All", api.tagItems.first { it.id == 1 }.label)
        assertTrue(vm.state.value.notice == TagsNotice.RENAMED)
    }

    @Test
    fun `delete confirms first and clears the row`() = runTest {
        seed()
        val vm = withViewModel()

        vm.requestDelete(TagResource(id = 2, label = "4k"))
        assertNotNull(vm.state.value.confirmDeleteFor)
        vm.deleteConfirmed()
        advanceUntilIdle()

        assertTrue(vm.state.value.notice == TagsNotice.DELETED)
        assertEquals(listOf("hd"), api.tagItems.map { it.label })
    }

    @Test
    fun `offline create queues and renders a temp row`() = runTest {
        seed()
        val vm = withViewModel()

        api.errorFor = { IOException("offline") }
        vm.startCreate()
        vm.editLabel("offline-tag")
        vm.editorConfirmed()
        advanceUntilIdle()

        assertTrue(vm.state.value.notice == TagsNotice.CREATED_QUEUED)
        val content = (vm.lce.value as LceState.Content).data
        val temp = content.rows.first { it.label == "offline-tag" }
        assertTrue(temp.tag.id < 0)
        assertNull(vm.state.value.writeError)
    }
}
