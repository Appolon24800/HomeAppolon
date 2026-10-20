package dev.appolon.homelab.arr.feature.lidarr.settings.quality.vm

import dev.appolon.homelab.arr.api.lidarr.FakeLidarrCalendar
import dev.appolon.homelab.arr.api.lidarr.model.Quality
import dev.appolon.homelab.arr.api.lidarr.model.QualityProfileQualityItemResource
import dev.appolon.homelab.arr.api.lidarr.model.QualityProfileResource
import dev.appolon.homelab.arr.core.ServerId
import dev.appolon.homelab.arr.feature.lidarr.settings.quality.FakeQualityActionQueue
import dev.appolon.homelab.arr.data.testing.FakeEntityStore
import dev.appolon.homelab.arr.feature.lidarr.settings.quality.data.LidarrQualityRepository
import dev.appolon.homelab.arr.ui.lce.LceState
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
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
class LidarrQualityProfilesViewModelTest {

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

    private fun leaf(id: Int, allowed: Boolean = false) = QualityProfileQualityItemResource(
        id = id,
        name = "Q$id",
        quality = Quality(id = id, name = "Q$id"),
        allowed = allowed,
    )

    @Test
    fun `list loads profiles cache-first`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        api.qualityProfiles[1] = QualityProfileResource(id = 1, name = "Standard")
        val vm = LidarrQualityProfilesViewModel(repository(testScheduler))
        vm.lce.collectWhileActive(this)
        advanceUntilIdle()

        val content = (vm.lce.value as LceState.Content<LidarrQualityProfilesContent>).data
        assertEquals(listOf("Standard"), content.profiles.map { it.name })
        assertFalse(vm.state.value.refreshing)
    }

    @Test
    fun `delete asks for confirmation then removes the row`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        api.qualityProfiles[1] = QualityProfileResource(id = 1, name = "Standard")
        val vm = LidarrQualityProfilesViewModel(repository(testScheduler))
        vm.lce.collectWhileActive(this)
        advanceUntilIdle()

        vm.requestDelete(1)
        assertEquals(1, vm.state.value.confirmDeleteId)
        vm.deleteConfirmed()
        advanceUntilIdle()

        assertEquals(QualityProfilesNotice.DELETED, vm.state.value.notice)
        assertTrue(api.qualityProfiles.isEmpty())
    }

    @Test
    fun `editor builds a new profile from the schema`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        api.qualityProfileSchema = QualityProfileResource(
            items = listOf(leaf(5)),
        )
        val vm = LidarrQualityProfileEditorViewModel(
            profileId = null,
            repository = repository(testScheduler),
        )
        vm.lce.collectWhileActive(this)
        advanceUntilIdle()

        val content = (vm.lce.value as LceState.Content<LidarrQualityProfileEditorContent>).data
        assertFalse(content.draft.isExisting)
        assertEquals(listOf("Q5"), content.draft.items.map { it.name })
        assertFalse(content.draft.items.first().allowed)
    }

    @Test
    fun `editing a missing profile id surfaces a load error`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        api.qualityProfiles[1] = QualityProfileResource(id = 1, name = "Standard")
        val vm = LidarrQualityProfileEditorViewModel(
            profileId = 99,
            repository = repository(testScheduler),
        )
        vm.lce.collectWhileActive(this)
        advanceUntilIdle()

        assertTrue(vm.lce.value is LceState.Error)
    }

    @Test
    fun `toggling a group toggles every child and the cutoff follows allowed leaves`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val child = leaf(2, allowed = true)
        val group = QualityProfileQualityItemResource(
            id = 1,
            name = "Lossy WEB",
            items = listOf(child, leaf(3)),
        )
        api.qualityProfiles[1] = QualityProfileResource(
            id = 1,
            name = "Standard",
            items = listOf(group),
            cutoff = 2,
        )
        val vm = LidarrQualityProfileEditorViewModel(
            profileId = 1,
            repository = repository(testScheduler),
        )
        vm.lce.collectWhileActive(this)
        advanceUntilIdle()

        vm.toggleAllowed(vm.state.value.draft.items.first())
        advanceUntilIdle()

        // The group's wire row was off, so the first toggle carries the
        // change to every child and the cutoff stays valid.
        var draft = vm.state.value.draft
        assertTrue(draft.items.first().children.all { it.allowed })
        assertEquals(2, draft.toResource().cutoff)

        // Toggling off clears every child, so the cutoff falls away too.
        vm.toggleAllowed(vm.state.value.draft.items.first())
        advanceUntilIdle()

        draft = vm.state.value.draft
        assertTrue(draft.items.first().children.none { it.allowed })
        assertEquals(0, draft.toResource().cutoff)
    }

    @Test
    fun `save posts the draft and announces the write`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        api.qualityProfileSchema = QualityProfileResource(items = listOf(leaf(1)))
        val vm = LidarrQualityProfileEditorViewModel(
            profileId = null,
            repository = repository(testScheduler),
        )
        vm.lce.collectWhileActive(this)
        advanceUntilIdle()

        vm.edit { it.copy(name = "Any", minFormatScore = "5") }
        vm.save()
        advanceUntilIdle()

        assertTrue(vm.state.value.savedNotice)
        assertEquals("Any", api.qualityProfiles.values.first().name)
        assertEquals(5, api.qualityProfiles.values.first().minFormatScore)
    }

    @Test
    fun `offline create queues and keeps the optimistic row`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        api.qualityProfileSchema = QualityProfileResource(items = listOf(leaf(1)))
        val vm = LidarrQualityProfileEditorViewModel(
            profileId = null,
            repository = repository(testScheduler),
        )
        vm.lce.collectWhileActive(this)
        advanceUntilIdle()

        store.clock = { now }
        api.error = { IOException("offline") }
        vm.edit { it.copy(name = "Offline profile") }
        vm.save()
        advanceUntilIdle()

        assertTrue(vm.state.value.savedQueued)
        assertTrue(api.qualityProfiles.isEmpty())
        assertEquals(1, queue.pending().size)
        val content = (vm.lce.value as LceState.Content<LidarrQualityProfileEditorContent>).data
        assertEquals(1, content.pendingActions)
    }
}
