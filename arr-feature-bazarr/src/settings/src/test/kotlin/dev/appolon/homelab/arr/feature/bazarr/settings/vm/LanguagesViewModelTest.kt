package dev.appolon.homelab.arr.feature.bazarr.settings.vm

import dev.appolon.homelab.arr.api.bazarr.FakeBazarrSubtitles
import dev.appolon.homelab.arr.api.bazarr.FakeBazarrSystem
import dev.appolon.homelab.arr.feature.bazarr.episodes.FakeActionQueue
import dev.appolon.homelab.arr.feature.bazarr.episodes.FakeEntityStore
import dev.appolon.homelab.arr.feature.bazarr.settings.languageRow
import dev.appolon.homelab.arr.feature.bazarr.settings.newSettingsRepository
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
class LanguagesViewModelTest {

    private val main = UnconfinedTestDispatcher()
    private lateinit var system: FakeBazarrSystem
    private lateinit var vm: LanguagesViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(main)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun buildVm(): LanguagesViewModel = LanguagesViewModel(
        repository = newSettingsRepository(
            system = system,
            subtitles = FakeBazarrSubtitles(),
            store = FakeEntityStore(),
            queue = FakeActionQueue(),
            io = main,
        ),
    )

    private fun runVm(
        seed: (FakeBazarrSystem) -> Unit = {},
        block: suspend TestScope.() -> Unit,
    ) = runTest(main) {
        system = FakeBazarrSystem()
        seed(system)
        vm = buildVm()
        val job = launch { vm.lce.collect {} }
        advanceUntilIdle()
        block()
        advanceUntilIdle()
        job.cancel()
    }

    @Test
    fun `initial load lists languages enabled first`() = runVm(
        seed = {
            it.responses["get_languages"] = settingsDataPayload(
                listOf(
                    languageRow("de", "German"),
                    languageRow("en", "English", enabled = true),
                ),
            )
        },
    ) {
        assertTrue(vm.lce.value is LceState.Content)
        val state = vm.state.value
        assertEquals(listOf("en", "de"), state.languages.map { it.code2 })
        assertEquals(1, state.enabledCount)
    }

    @Test
    fun `an empty server surfaces the empty state`() = runVm {
        assertTrue(vm.lce.value is LceState.Empty)
    }

    @Test
    fun `a failed load surfaces the error state and retry recovers`() = runVm(
        seed = {
            it.failure = { call -> if (call.operation == "get_languages") IOException() else null }
        },
    ) {
        assertTrue(vm.lce.value is LceState.Error)

        system.failure = { null }
        system.responses["get_languages"] = settingsDataPayload(
            listOf(languageRow("en", "English", enabled = true)),
        )
        vm.refresh()

        assertTrue(vm.lce.value is LceState.Content)
        assertEquals(listOf("en"), vm.state.value.languages.map { it.code2 })
    }
}
