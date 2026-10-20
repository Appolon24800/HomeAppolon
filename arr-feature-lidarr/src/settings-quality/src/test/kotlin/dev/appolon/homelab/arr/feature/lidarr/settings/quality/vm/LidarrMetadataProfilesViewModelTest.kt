package dev.appolon.homelab.arr.feature.lidarr.settings.quality.vm

import dev.appolon.homelab.arr.api.lidarr.FakeLidarrCalendar
import dev.appolon.homelab.arr.api.lidarr.model.MetadataProfileResource
import dev.appolon.homelab.arr.api.lidarr.model.PrimaryAlbumType
import dev.appolon.homelab.arr.api.lidarr.model.ProfilePrimaryAlbumTypeItemResource
import dev.appolon.homelab.arr.api.lidarr.model.ProfileReleaseStatusItemResource
import dev.appolon.homelab.arr.api.lidarr.model.ProfileSecondaryAlbumTypeItemResource
import dev.appolon.homelab.arr.api.lidarr.model.ReleaseStatus
import dev.appolon.homelab.arr.api.lidarr.model.SecondaryAlbumType
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
class LidarrMetadataProfilesViewModelTest {

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

    private fun profile() = MetadataProfileResource(
        id = 1,
        name = "Standard",
        primaryAlbumTypes = listOf(
            ProfilePrimaryAlbumTypeItemResource(
                id = 1,
                albumType = PrimaryAlbumType(id = 1, name = "Album"),
                allowed = true,
            ),
            ProfilePrimaryAlbumTypeItemResource(
                id = 2,
                albumType = PrimaryAlbumType(id = 2, name = "EP"),
                allowed = false,
            ),
        ),
        secondaryAlbumTypes = listOf(
            ProfileSecondaryAlbumTypeItemResource(
                id = 3,
                albumType = SecondaryAlbumType(id = 1, name = "Studio"),
                allowed = false,
            ),
        ),
        releaseStatuses = listOf(
            ProfileReleaseStatusItemResource(
                id = 4,
                releaseStatus = ReleaseStatus(id = 1, name = "Official"),
                allowed = true,
            ),
        ),
    )

    @Test
    fun `list loads profiles cache-first`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        api.metadataProfiles[1] = profile()
        val vm = LidarrMetadataProfilesViewModel(repository(testScheduler))
        vm.lce.collectWhileActive(this)
        advanceUntilIdle()

        val content = (vm.lce.value as LceState.Content<LidarrMetadataProfilesContent>).data
        assertEquals(listOf("Standard"), content.profiles.map { it.name })
        assertFalse(vm.state.value.refreshing)
    }

    @Test
    fun `delete asks for confirmation then removes the row`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        api.metadataProfiles[1] = profile()
        val vm = LidarrMetadataProfilesViewModel(repository(testScheduler))
        vm.lce.collectWhileActive(this)
        advanceUntilIdle()

        vm.requestDelete(1)
        assertEquals(1, vm.state.value.confirmDeleteId)
        vm.deleteConfirmed()
        advanceUntilIdle()

        assertEquals(MetadataProfilesNotice.DELETED, vm.state.value.notice)
        assertTrue(api.metadataProfiles.isEmpty())
    }

    @Test
    fun `editor builds a new profile from the schema`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        api.metadataProfileSchema = profile().copy(id = 0)
        val vm = LidarrMetadataProfileEditorViewModel(
            profileId = null,
            repository = repository(testScheduler),
        )
        vm.lce.collectWhileActive(this)
        advanceUntilIdle()

        val content = (vm.lce.value as LceState.Content<LidarrMetadataProfileEditorContent>).data
        assertFalse(content.draft.isExisting)
        assertEquals(2, content.draft.primaryAlbumTypes.size)
        assertEquals(1, content.draft.secondaryAlbumTypes.size)
        assertEquals(1, content.draft.releaseStatuses.size)
    }

    @Test
    fun `toggles flip one row per list`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        api.metadataProfiles[1] = profile()
        val vm = LidarrMetadataProfileEditorViewModel(
            profileId = 1,
            repository = repository(testScheduler),
        )
        vm.lce.collectWhileActive(this)
        advanceUntilIdle()

        val draft = vm.state.value.draft
        vm.togglePrimaryType(draft.primaryAlbumTypes.first { it.albumType?.id == 2 })
        vm.toggleSecondaryType(draft.secondaryAlbumTypes.first())
        vm.toggleReleaseStatus(draft.releaseStatuses.first())
        advanceUntilIdle()

        val updated = vm.state.value.draft
        assertTrue(updated.primaryAlbumTypes.first { it.albumType?.id == 2 }.allowed)
        assertTrue(updated.secondaryAlbumTypes.first().allowed)
        assertFalse(updated.releaseStatuses.first().allowed)
        assertTrue(vm.state.value.dirty)
    }

    @Test
    fun `save posts the draft and announces the write`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        api.metadataProfileSchema = profile().copy(id = 0)
        val vm = LidarrMetadataProfileEditorViewModel(
            profileId = null,
            repository = repository(testScheduler),
        )
        vm.lce.collectWhileActive(this)
        advanceUntilIdle()

        vm.setName("Music only")
        vm.save()
        advanceUntilIdle()

        assertTrue(vm.state.value.savedNotice)
        assertEquals("Music only", api.metadataProfiles.values.first().name)
    }

    @Test
    fun `offline create queues and keeps the optimistic row`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        api.metadataProfileSchema = profile().copy(id = 0)
        val vm = LidarrMetadataProfileEditorViewModel(
            profileId = null,
            repository = repository(testScheduler),
        )
        vm.lce.collectWhileActive(this)
        advanceUntilIdle()

        store.clock = { now }
        api.error = { IOException("offline") }
        vm.setName("Offline profile")
        vm.save()
        advanceUntilIdle()

        assertTrue(vm.state.value.savedQueued)
        assertTrue(api.metadataProfiles.isEmpty())
        assertEquals(1, queue.pending().size)
        val content = (vm.lce.value as LceState.Content<LidarrMetadataProfileEditorContent>).data
        assertEquals(1, content.pendingActions)
    }
}
