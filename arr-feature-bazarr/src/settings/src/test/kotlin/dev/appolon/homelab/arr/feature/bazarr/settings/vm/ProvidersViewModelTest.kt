package dev.appolon.homelab.arr.feature.bazarr.settings.vm

import dev.appolon.homelab.arr.api.bazarr.FakeBazarrSubtitles
import dev.appolon.homelab.arr.api.bazarr.FakeBazarrSystem
import dev.appolon.homelab.arr.feature.bazarr.episodes.FakeActionQueue
import dev.appolon.homelab.arr.feature.bazarr.episodes.FakeEntityStore
import dev.appolon.homelab.arr.feature.bazarr.settings.newSettingsRepository
import dev.appolon.homelab.arr.feature.bazarr.settings.providerRow
import dev.appolon.homelab.arr.feature.bazarr.settings.settingsDataPayload
import dev.appolon.homelab.arr.ui.lce.LceState
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ProvidersViewModelTest {

    private val main = UnconfinedTestDispatcher()
    private lateinit var system: FakeBazarrSystem
    private lateinit var subtitles: FakeBazarrSubtitles
    private lateinit var vm: ProvidersViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(main)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun buildVm(): ProvidersViewModel = ProvidersViewModel(
        repository = newSettingsRepository(
            system = system,
            subtitles = subtitles,
            store = FakeEntityStore(),
            queue = FakeActionQueue(),
            io = main,
        ),
    )

    private fun runVm(
        block: suspend TestScope.() -> Unit,
    ) = runTest(main) {
        system = FakeBazarrSystem()
        subtitles = FakeBazarrSubtitles()
        subtitles.responses["get_providers"] = settingsDataPayload(
            listOf(providerRow(provider = "opensubtitles"), providerRow(provider = "subf2m")),
        )
        vm = buildVm()
        val job = launch { vm.lce.collect {} }
        advanceUntilIdle()
        block()
        advanceUntilIdle()
        job.cancel()
    }

    @Test
    fun `initial load lists providers`() = runVm {
        assertTrue(vm.lce.value is LceState.Content)
        assertEquals(
            listOf("opensubtitles", "subf2m"),
            vm.state.value.providers.map { it.provider },
        )
    }

    @Test
    fun `online reset reports success`() = runVm {
        vm.resetProviders()

        assertEquals(ProvidersMessage.ResetSynced, vm.state.value.message)
        assertTrue(!vm.state.value.queuedReset)
        val call = subtitles.calls.single { it.operationId == "post_providers" }
        assertEquals("reset", call.arguments["action"])
    }

    @Test
    fun `offline reset queues and keeps the queued indicator`() = runVm {
        subtitles.errorInjection = { if (it == "post_providers") IOException() else null }

        vm.resetProviders()

        assertEquals(ProvidersMessage.ResetQueued, vm.state.value.message)
        assertTrue(vm.state.value.queuedReset)
    }
}
