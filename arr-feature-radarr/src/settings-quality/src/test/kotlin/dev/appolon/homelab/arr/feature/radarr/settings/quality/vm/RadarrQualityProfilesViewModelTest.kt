package dev.appolon.homelab.arr.feature.radarr.settings.quality.vm

import dev.appolon.homelab.arr.api.radarr.FakeRadarrCalendar
import dev.appolon.homelab.arr.api.radarr.model.Language
import dev.appolon.homelab.arr.api.radarr.model.LanguageResource
import dev.appolon.homelab.arr.api.radarr.model.ProfileFormatItemResource
import dev.appolon.homelab.arr.api.radarr.model.Quality
import dev.appolon.homelab.arr.api.radarr.model.QualityProfileQualityItemResource
import dev.appolon.homelab.arr.api.radarr.model.QualityProfileResource
import dev.appolon.homelab.arr.core.ServerId
import dev.appolon.homelab.arr.feature.radarr.settings.quality.FakeQualityActionQueue
import dev.appolon.homelab.arr.data.testing.FakeEntityStore
import dev.appolon.homelab.arr.feature.radarr.settings.quality.data.RadarrQualityRepository
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
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RadarrQualityProfilesViewModelTest {

    private lateinit var api: FakeRadarrCalendar
    private lateinit var store: FakeEntityStore
    private lateinit var queue: FakeQualityActionQueue

    @Before
    fun setUp() {
        api = FakeRadarrCalendar()
        store = FakeEntityStore()
        queue = FakeQualityActionQueue()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun repository(scheduler: TestCoroutineScheduler) = RadarrQualityRepository(
        serverId = ServerId("s1"),
        store = store,
        qualityDefinitionApi = api,
        qualityProfileApi = api,
        qualityProfileSchemaApi = api,
        customFormatApi = api,
        delayProfileApi = api,
        releaseProfileApi = api,
        languageApi = api,
        actionQueue = queue,
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
        api.qualityProfiles += QualityProfileResource(id = 1, name = "HD")
        val vm = RadarrQualityProfilesViewModel(repository(testScheduler))
        vm.lce.collectWhileActive(this)
        advanceUntilIdle()

        val content = (vm.lce.value as LceState.Content<RadarrQualityProfilesContent>).data
        assertEquals(listOf("HD"), content.profiles.map { it.name })
        assertFalse(vm.state.value.refreshing)
    }

    @Test
    fun `delete asks for confirmation then removes the row`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        api.qualityProfiles += QualityProfileResource(id = 1, name = "HD")
        val vm = RadarrQualityProfilesViewModel(repository(testScheduler))
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
        api.qualityDefinitions += dev.appolon.homelab.arr.api.radarr.model.QualityDefinitionResource(
            id = 5,
            quality = Quality(id = 5, name = "Bluray-1080p"),
            title = "Bluray-1080p",
        )
        val vm = RadarrQualityProfileEditorViewModel(profileId = null, repository = repository(testScheduler))
        vm.lce.collectWhileActive(this)
        advanceUntilIdle()

        val content = (vm.lce.value as LceState.Content<RadarrQualityProfileEditorContent>).data
        assertFalse(content.draft.isExisting)
        assertEquals(listOf("Bluray-1080p"), content.draft.items.map { it.name })
        assertFalse(content.draft.items.first().allowed)
    }

    @Test
    fun `editing a missing profile id surfaces a load error`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        api.qualityProfiles += QualityProfileResource(id = 1, name = "HD")
        val vm = RadarrQualityProfileEditorViewModel(profileId = 99, repository = repository(testScheduler))
        vm.lce.collectWhileActive(this)
        advanceUntilIdle()

        assertTrue(vm.lce.value is LceState.Error)
    }

    @Test
    fun `toggle allowed cascades through group children`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val group = QualityProfileQualityItemResource(
            id = 10,
            name = "WEB 480p",
            items = listOf(leaf(1), leaf(2)),
        )
        api.qualityProfiles += QualityProfileResource(
            id = 1,
            name = "HD",
            items = listOf(group, leaf(3)),
        )
        val vm = RadarrQualityProfileEditorViewModel(profileId = 1, repository = repository(testScheduler))
        vm.lce.collectWhileActive(this)
        advanceUntilIdle()

        val root = vm.state.value.draft.items.first()
        vm.toggleAllowed(root)
        val toggled = vm.state.value.draft.items.first()
        assertTrue(toggled.allowed)
        assertTrue(toggled.children.all { it.allowed })
        assertTrue(vm.state.value.dirty)
    }

    @Test
    fun `save maps the draft onto the wire with cutoff inside the allowed set`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        api.qualityProfiles += QualityProfileResource(
            id = 1,
            name = "HD",
            items = listOf(leaf(1), leaf(2), leaf(3)),
            formatItems = listOf(ProfileFormatItemResource(format = 7, name = "Surround", score = 5)),
            language = Language(id = 1, name = "English"),
        )
        val vm = RadarrQualityProfileEditorViewModel(profileId = 1, repository = repository(testScheduler))
        vm.lce.collectWhileActive(this)
        advanceUntilIdle()

        vm.edit { it.copy(name = "HD Plus", minFormatScore = "35") }
        vm.toggleAllowed(vm.state.value.draft.items[1])
        vm.setCutoff(2)
        vm.setLanguage(Language(id = 3, name = "German"))
        vm.setFormatScore(7, "100")
        vm.save()
        advanceUntilIdle()

        assertTrue(vm.state.value.savedNotice)
        val saved = api.qualityProfiles.first()
        assertEquals("HD Plus", saved.name)
        assertEquals(35, saved.minFormatScore)
        assertEquals(2, saved.cutoff)
        assertEquals(3, saved.language!!.id)
        assertEquals(100, saved.formatItems!!.first().score)
        assertFalse(vm.state.value.dirty)
    }

    @Test
    fun `cutoff falls back to the first allowed leaf when disallowed`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        api.qualityProfiles += QualityProfileResource(
            id = 1,
            name = "HD",
            cutoff = 3,
            items = listOf(leaf(1), leaf(3)),
        )
        val vm = RadarrQualityProfileEditorViewModel(profileId = 1, repository = repository(testScheduler))
        vm.lce.collectWhileActive(this)
        advanceUntilIdle()

        vm.toggleAllowed(vm.state.value.draft.items[0])
        vm.save()
        advanceUntilIdle()

        // Only leaf 1 is allowed, so the wire cutoff cannot stay on 3.
        assertEquals(1, api.qualityProfiles.first().cutoff)
    }

    @Test
    fun `create posts the schema draft and queues offline`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        api.qualityDefinitions += dev.appolon.homelab.arr.api.radarr.model.QualityDefinitionResource(
            id = 5,
            quality = Quality(id = 5, name = "Bluray-1080p"),
            title = "Bluray-1080p",
        )
        api.languages += LanguageResource(id = 1, name = "English")
        val vm = RadarrQualityProfileEditorViewModel(profileId = null, repository = repository(testScheduler))
        vm.lce.collectWhileActive(this)
        advanceUntilIdle()

        vm.edit { it.copy(name = "Fresh") }
        vm.toggleAllowed(vm.state.value.draft.items.first())
        vm.save()
        advanceUntilIdle()

        assertEquals("Fresh", api.qualityProfiles.single().name)

        api.errorFor = { IOException("offline") }
        vm.edit { it.copy(name = "Second") }
        vm.save()
        advanceUntilIdle()

        assertTrue(vm.state.value.savedQueued)
        assertEquals("POST", queue.pending().single().method)
    }

    @Test
    fun `list write errors surface without crashing`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        api.qualityProfiles += QualityProfileResource(id = 1, name = "HD")
        val vm = RadarrQualityProfilesViewModel(repository(testScheduler))
        vm.lce.collectWhileActive(this)
        advanceUntilIdle()

        api.errorFor = { retrofit2.HttpException(retrofit2.Response.error<Any>(500, "".toResponseBody(null))) }
        vm.requestDelete(1)
        vm.deleteConfirmed()
        advanceUntilIdle()

        assertNotNull(vm.state.value.writeError)
        assertFalse(api.qualityProfiles.isEmpty())
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
private fun <T> StateFlow<T>.collectWhileActive(scope: TestScope) {
    scope.backgroundScope.launch { this@collectWhileActive.collect { } }
}
