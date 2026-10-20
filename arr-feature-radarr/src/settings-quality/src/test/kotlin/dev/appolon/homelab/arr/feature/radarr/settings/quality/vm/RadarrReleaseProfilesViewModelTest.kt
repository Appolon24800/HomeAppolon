package dev.appolon.homelab.arr.feature.radarr.settings.quality.vm

import dev.appolon.homelab.arr.api.radarr.FakeRadarrCalendar
import dev.appolon.homelab.arr.api.radarr.model.ReleaseProfileResource
import dev.appolon.homelab.arr.core.ServerId
import dev.appolon.homelab.arr.feature.radarr.settings.quality.FakeQualityActionQueue
import dev.appolon.homelab.arr.feature.radarr.settings.quality.FakeQualityEntityStore
import dev.appolon.homelab.arr.feature.radarr.settings.quality.data.RadarrQualityRepository
import dev.appolon.homelab.arr.ui.lce.LceState
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RadarrReleaseProfilesViewModelTest {

    private lateinit var api: FakeRadarrCalendar
    private lateinit var store: FakeQualityEntityStore
    private lateinit var queue: FakeQualityActionQueue

    @Before
    fun setUp() {
        api = FakeRadarrCalendar()
        store = FakeQualityEntityStore()
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

    @Test
    fun `loads release profiles cache-first`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        api.releaseProfiles += ReleaseProfileResource(id = 1, name = "Obfuscated", enabled = true)
        val vm = RadarrReleaseProfilesViewModel(repository(testScheduler))
        vm.lce.collectWhileActive(this)
        advanceUntilIdle()

        val content = (vm.lce.value as LceState.Content<RadarrReleaseProfilesContent>).data
        assertEquals(listOf("Obfuscated"), content.profiles.map { it.name })
    }

    @Test
    fun `term lists split and join the wire's newline blob`() {
        assertEquals(listOf("a", "b", "a"), ReleaseProfileDraft.splitTerms("a\nb\r\n a \n"))
        assertEquals("a\nb", ReleaseProfileDraft.joinTerms(listOf(" a ", "b", "")))
        assertEquals(emptyList<String>(), ReleaseProfileDraft.splitTerms(null))
    }

    @Test
    fun `save updates terms and preserves server owned tags`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        api.releaseProfiles += ReleaseProfileResource(
            id = 1,
            name = "Obfuscated",
            required = "rarbg\n",
            ignored = "obfuscated\n",
            tags = setOf(3, 4),
        )
        val vm = RadarrReleaseProfilesViewModel(repository(testScheduler))
        vm.lce.collectWhileActive(this)
        advanceUntilIdle()

        val draft = ReleaseProfileDraft.from(api.releaseProfiles.first()).copy(
            mustContain = listOf("proper"),
            mustNotContain = listOf("repack", "spam"),
        )
        vm.save(draft)
        advanceUntilIdle()

        assertEquals(ReleaseProfilesNotice.UPDATED, vm.state.value.notice)
        val saved = api.releaseProfiles.first()
        assertEquals("proper", saved.required)
        assertEquals("repack\nspam", saved.ignored)
        assertEquals(setOf(3, 4), saved.tags)
    }

    @Test
    fun `create posts a new profile`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val vm = RadarrReleaseProfilesViewModel(repository(testScheduler))
        vm.lce.collectWhileActive(this)
        advanceUntilIdle()

        vm.save(ReleaseProfileDraft(name = "No Groups", mustContain = listOf("-GROUP")))
        advanceUntilIdle()

        assertEquals(ReleaseProfilesNotice.CREATED, vm.state.value.notice)
        assertEquals("-GROUP", api.releaseProfiles.single().required)
    }

    @Test
    fun `blank names never save`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val vm = RadarrReleaseProfilesViewModel(repository(testScheduler))
        vm.lce.collectWhileActive(this)
        advanceUntilIdle()

        vm.save(ReleaseProfileDraft(name = "  ", mustContain = listOf("x")))
        advanceUntilIdle()

        assertTrue(api.releaseProfiles.isEmpty())
        assertNull(vm.state.value.notice)
    }

    @Test
    fun `delete confirms then removes`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        api.releaseProfiles += ReleaseProfileResource(id = 1, name = "Obfuscated")
        val vm = RadarrReleaseProfilesViewModel(repository(testScheduler))
        vm.lce.collectWhileActive(this)
        advanceUntilIdle()

        vm.requestDelete(1)
        vm.deleteConfirmed()
        advanceUntilIdle()

        assertEquals(ReleaseProfilesNotice.DELETED, vm.state.value.notice)
        assertTrue(api.releaseProfiles.isEmpty())
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
private fun <T> StateFlow<T>.collectWhileActive(scope: TestScope) {
    scope.backgroundScope.launch { this@collectWhileActive.collect { } }
}
