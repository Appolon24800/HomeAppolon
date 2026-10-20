package dev.appolon.homelab.arr.feature.sonarr.settings.quality.vm

import dev.appolon.homelab.arr.api.sonarr.FakeSonarrCalendar
import dev.appolon.homelab.arr.api.sonarr.model.Language
import dev.appolon.homelab.arr.api.sonarr.model.LanguageProfileItemResource
import dev.appolon.homelab.arr.api.sonarr.model.LanguageProfileResource
import dev.appolon.homelab.arr.api.sonarr.model.LanguageResource
import dev.appolon.homelab.arr.core.ServerId
import dev.appolon.homelab.arr.feature.sonarr.settings.quality.FakeQualityActionQueue
import dev.appolon.homelab.arr.data.testing.FakeEntityStore
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SonarrLanguageProfilesViewModelTest {

    private lateinit var api: FakeSonarrCalendar
    private lateinit var store: FakeEntityStore
    private lateinit var queue: FakeQualityActionQueue
    private var now: Long = 1_000_000L

    @Before
    fun setUp() {
        api = FakeSonarrCalendar()
        store = FakeEntityStore()
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

    private fun item(id: Int, name: String, allowed: Boolean = false) = LanguageProfileItemResource(
        id = id,
        language = Language(id = id, name = name),
        allowed = allowed,
    )

    @Test
    fun `list loads language profiles cache-first`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        api.languageProfileStore[1] = LanguageProfileResource(
            id = 1,
            name = "English only",
            cutoff = Language(id = 1, name = "English"),
            languages = listOf(item(1, "English", allowed = true)),
        )
        val vm = SonarrLanguageProfilesViewModel(repository(testScheduler))
        vm.lce.collectWhileActive(this)
        advanceUntilIdle()

        val content = (vm.lce.value as LceState.Content<SonarrLanguageProfilesContent>).data
        assertEquals(listOf("English only"), content.profiles.map { it.name })
        assertFalse(vm.state.value.refreshing)
    }

    @Test
    fun `editor builds a new profile from the language schema`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        api.languageStore[1] = LanguageResource(id = 1, name = "English")
        api.languageStore[2] = LanguageResource(id = 2, name = "Japanese")
        val vm = SonarrLanguageProfileEditorViewModel(profileId = null, repository = repository(testScheduler))
        vm.lce.collectWhileActive(this)
        advanceUntilIdle()

        val content = (vm.lce.value as LceState.Content<SonarrLanguageProfileEditorContent>).data
        assertFalse(content.draft.isExisting)
        assertEquals(listOf("English", "Japanese"), content.draft.items.map { it.language.name })
        assertTrue(content.draft.items.none { it.allowed })
    }

    @Test
    fun `toggling a language off moves the cutoff to the first allowed one`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        api.languageProfileStore[1] = LanguageProfileResource(
            id = 1,
            name = "Dual",
            upgradeAllowed = true,
            cutoff = Language(id = 2, name = "Japanese"),
            languages = listOf(
                item(1, "English", allowed = true),
                item(2, "Japanese", allowed = true),
            ),
        )
        val vm = SonarrLanguageProfileEditorViewModel(profileId = 1, repository = repository(testScheduler))
        vm.lce.collectWhileActive(this)
        advanceUntilIdle()

        vm.toggleLanguage(vm.state.value.draft.items.first { it.language.id == 2 })
        advanceUntilIdle()

        val resource = vm.state.value.draft.toResource()
        assertEquals(1, resource.cutoff.id)
        assertFalse(resource.languages!!.first { it.language.id == 2 }.allowed)
    }

    @Test
    fun `save posts the draft and announces the write`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        api.languageStore[1] = LanguageResource(id = 1, name = "English")
        val vm = SonarrLanguageProfileEditorViewModel(profileId = null, repository = repository(testScheduler))
        vm.lce.collectWhileActive(this)
        advanceUntilIdle()

        vm.setName("English only")
        vm.toggleLanguage(vm.state.value.draft.items.first())
        vm.save()
        advanceUntilIdle()

        assertTrue(vm.state.value.savedNotice)
        assertEquals("English only", api.languageProfileStore.values.first().name)
        assertEquals(1, api.languageProfileStore.values.first().cutoff.id)
    }

    @Test
    fun `offline create queues and keeps the optimistic row`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        api.languageStore[1] = LanguageResource(id = 1, name = "English")
        store.clock = { now }
        val vm = SonarrLanguageProfileEditorViewModel(profileId = null, repository = repository(testScheduler))
        vm.lce.collectWhileActive(this)
        advanceUntilIdle()

        api.error = { IOException("offline") }
        vm.setName("Queued profile")
        vm.toggleLanguage(vm.state.value.draft.items.first())
        vm.save()
        advanceUntilIdle()

        assertTrue(vm.state.value.savedQueued)
        assertTrue(api.languageProfileStore.isEmpty())
        assertEquals(1, queue.pending().size)
        assertEquals("api/v3/languageprofile", queue.pending().first().path)
    }

    @Test
    fun `delete asks for confirmation then removes the row`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        api.languageProfileStore[1] = LanguageProfileResource(id = 1, name = "English only")
        val vm = SonarrLanguageProfilesViewModel(repository(testScheduler))
        vm.lce.collectWhileActive(this)
        advanceUntilIdle()

        vm.requestDelete(1)
        vm.deleteConfirmed()
        advanceUntilIdle()

        assertEquals(LanguageProfilesNotice.DELETED, vm.state.value.notice)
        assertTrue(api.languageProfileStore.isEmpty())
    }

    @Test
    fun `editing a missing profile id surfaces a load error`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        api.languageProfileStore[1] = LanguageProfileResource(id = 1, name = "English only")
        val vm = SonarrLanguageProfileEditorViewModel(profileId = 42, repository = repository(testScheduler))
        vm.lce.collectWhileActive(this)
        advanceUntilIdle()

        assertTrue(vm.lce.value is LceState.Error)
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
private fun <T> StateFlow<T>.collectWhileActive(scope: TestScope) {
    scope.backgroundScope.launch { this@collectWhileActive.collect { } }
}
