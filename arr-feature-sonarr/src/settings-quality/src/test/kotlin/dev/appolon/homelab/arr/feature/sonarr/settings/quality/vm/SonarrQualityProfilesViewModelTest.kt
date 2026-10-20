package dev.appolon.homelab.arr.feature.sonarr.settings.quality.vm

import dev.appolon.homelab.arr.api.sonarr.FakeSonarrCalendar
import dev.appolon.homelab.arr.api.sonarr.model.Quality
import dev.appolon.homelab.arr.api.sonarr.model.QualityDefinitionResource
import dev.appolon.homelab.arr.api.sonarr.model.QualityProfileQualityItemResource
import dev.appolon.homelab.arr.api.sonarr.model.QualityProfileResource
import dev.appolon.homelab.arr.core.ServerId
import dev.appolon.homelab.arr.feature.sonarr.settings.quality.FakeQualityActionQueue
import dev.appolon.homelab.arr.feature.sonarr.settings.quality.FakeQualityEntityStore
import dev.appolon.homelab.arr.feature.sonarr.settings.quality.data.SonarrQualityRepository
import dev.appolon.homelab.arr.ui.lce.LceState
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
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
class SonarrQualityProfilesViewModelTest {

    private lateinit var api: FakeSonarrCalendar
    private lateinit var store: FakeQualityEntityStore
    private lateinit var queue: FakeQualityActionQueue
    private var now: Long = 1_000_000L

    @Before
    fun setUp() {
        api = FakeSonarrCalendar()
        store = FakeQualityEntityStore()
        queue = FakeQualityActionQueue()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun repository(scheduler: TestCoroutineScheduler) = SonarrQualityRepository(
        serverId = ServerId("s1"),
        store = store,
        qualityDefinitionApi = api.qualityDefinition,
        qualityProfileApi = api.qualityProfile,
        qualityProfileSchemaApi = api.qualityProfileSchema,
        languageProfileApi = api.languageProfile,
        languageProfileSchemaApi = api.languageProfileSchema,
        customFormatApi = api.customFormat,
        delayProfileApi = api.delayProfile,
        releaseProfileApi = api.releaseProfile,
        languageApi = api.language,
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
        api.qualityProfileStore[1] = QualityProfileResource(id = 1, name = "HD")
        val vm = SonarrQualityProfilesViewModel(repository(testScheduler))
        vm.lce.collectWhileActive(this)
        advanceUntilIdle()

        val content = (vm.lce.value as LceState.Content<SonarrQualityProfilesContent>).data
        assertEquals(listOf("HD"), content.profiles.map { it.name })
        assertFalse(vm.state.value.refreshing)
    }

    @Test
    fun `delete asks for confirmation then removes the row`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        api.qualityProfileStore[1] = QualityProfileResource(id = 1, name = "HD")
        val vm = SonarrQualityProfilesViewModel(repository(testScheduler))
        vm.lce.collectWhileActive(this)
        advanceUntilIdle()

        vm.requestDelete(1)
        assertEquals(1, vm.state.value.confirmDeleteId)
        vm.deleteConfirmed()
        advanceUntilIdle()

        assertEquals(QualityProfilesNotice.DELETED, vm.state.value.notice)
        assertTrue(api.qualityProfileStore.isEmpty())
    }

    @Test
    fun `editor builds a new profile from the schema`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        api.qualityDefinitionStore[5] = QualityDefinitionResource(
            id = 5,
            quality = Quality(id = 5, name = "Bluray-1080p"),
            title = "Bluray-1080p",
        )
        val vm = SonarrQualityProfileEditorViewModel(profileId = null, repository = repository(testScheduler))
        vm.lce.collectWhileActive(this)
        advanceUntilIdle()

        val content = (vm.lce.value as LceState.Content<SonarrQualityProfileEditorContent>).data
        assertFalse(content.draft.isExisting)
        assertEquals(listOf("Bluray-1080p"), content.draft.items.map { it.name })
        assertFalse(content.draft.items.first().allowed)
    }

    @Test
    fun `editing a missing profile id surfaces a load error`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        api.qualityProfileStore[1] = QualityProfileResource(id = 1, name = "HD")
        val vm = SonarrQualityProfileEditorViewModel(profileId = 99, repository = repository(testScheduler))
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
            name = "WEB 720-1080p",
            items = listOf(child, leaf(3)),
        )
        api.qualityProfileStore[1] = QualityProfileResource(
            id = 1,
            name = "HD",
            items = listOf(group),
            cutoff = 2,
        )
        val vm = SonarrQualityProfileEditorViewModel(profileId = 1, repository = repository(testScheduler))
        vm.lce.collectWhileActive(this)
        advanceUntilIdle()

        val groupItem = vm.state.value.draft.items.first()
        vm.toggleAllowed(groupItem)
        advanceUntilIdle()

        // Toggling the group on carries the change to every child.
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
        api.qualityDefinitionStore[1] = QualityDefinitionResource(
            id = 1,
            quality = Quality(id = 1, name = "HDTV"),
            title = "HDTV",
        )
        val vm = SonarrQualityProfileEditorViewModel(profileId = null, repository = repository(testScheduler))
        vm.lce.collectWhileActive(this)
        advanceUntilIdle()

        vm.edit { it.copy(name = "Any", minFormatScore = "5") }
        vm.save()
        advanceUntilIdle()

        assertTrue(vm.state.value.savedNotice)
        assertEquals("Any", api.qualityProfileStore.values.first().name)
        assertEquals(5, api.qualityProfileStore.values.first().minFormatScore)
    }

    @Test
    fun `offline create queues and keeps the optimistic row`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        api.qualityDefinitionStore[1] = QualityDefinitionResource(
            id = 1,
            quality = Quality(id = 1, name = "HDTV"),
            title = "HDTV",
        )
        store.clock = { now }
        val vm = SonarrQualityProfileEditorViewModel(profileId = null, repository = repository(testScheduler))
        vm.lce.collectWhileActive(this)
        advanceUntilIdle()

        api.error = { IOException("offline") }
        vm.edit { it.copy(name = "Offline profile") }
        vm.save()
        advanceUntilIdle()

        assertTrue(vm.state.value.savedQueued)
        assertTrue(api.qualityProfileStore.isEmpty())
        assertEquals(1, queue.pending().size)
        val content = (vm.lce.value as LceState.Content<SonarrQualityProfileEditorContent>).data
        assertEquals(1, content.pendingActions)
    }

    @Test
    fun `a blank name never saves`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        api.qualityDefinitionStore[1] = QualityDefinitionResource(
            id = 1,
            quality = Quality(id = 1, name = "HDTV"),
            title = "HDTV",
        )
        val vm = SonarrQualityProfileEditorViewModel(profileId = null, repository = repository(testScheduler))
        vm.lce.collectWhileActive(this)
        advanceUntilIdle()

        vm.save()
        advanceUntilIdle()

        assertFalse(vm.state.value.saving)
        assertTrue(api.qualityProfileStore.isEmpty())
        assertNotNull(vm.state.value)
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
private fun <T> StateFlow<T>.collectWhileActive(scope: TestScope) {
    scope.backgroundScope.launch { this@collectWhileActive.collect { } }
}
