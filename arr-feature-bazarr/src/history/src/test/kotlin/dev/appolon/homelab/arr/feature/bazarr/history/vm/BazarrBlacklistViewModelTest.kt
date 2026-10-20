package dev.appolon.homelab.arr.feature.bazarr.history.vm

import dev.appolon.homelab.arr.api.bazarr.FakeBazarrLibrary
import dev.appolon.homelab.arr.api.bazarr.FakeBazarrSystem
import dev.appolon.homelab.arr.feature.bazarr.episodes.FakeActionQueue
import dev.appolon.homelab.arr.feature.bazarr.episodes.FakeEntityStore
import dev.appolon.homelab.arr.feature.bazarr.history.blacklistRow
import dev.appolon.homelab.arr.feature.bazarr.history.historyDataPayload
import dev.appolon.homelab.arr.feature.bazarr.history.newHistoryRepository
import dev.appolon.homelab.arr.ui.lce.LceState
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
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
class BazarrBlacklistViewModelTest {

    private val main = UnconfinedTestDispatcher()
    private lateinit var library: FakeBazarrLibrary
    private lateinit var system: FakeBazarrSystem
    private lateinit var queue: FakeActionQueue
    private lateinit var vm: BazarrBlacklistViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(main)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun seed() {
        library = FakeBazarrLibrary()
        system = FakeBazarrSystem()
        queue = FakeActionQueue()
        library.responses["get_episodes_blacklist"] = historyDataPayload(
            listOf(
                blacklistRow(provider = "opensubtitles", subsId = "abc", episodeId = 31),
                blacklistRow(provider = "subf2m", subsId = "def", episodeId = 32, language = "de"),
            ),
        )
        library.responses["get_movies_blacklist"] = historyDataPayload(
            listOf(blacklistRow(provider = "opensubtitles", subsId = "xyz", radarrId = 42)),
        )
    }

    private fun buildVm(): BazarrBlacklistViewModel = BazarrBlacklistViewModel(
        repository = newHistoryRepository(library, system, FakeEntityStore(), queue, io = main),
    )

    private fun runVm(block: suspend kotlinx.coroutines.test.TestScope.() -> Unit) = runTest(main) {
        seed()
        vm = buildVm()
        val job = launch { vm.lce.collect {} }
        advanceUntilIdle()
        block()
        advanceUntilIdle()
        job.cancel()
    }

    @Test
    fun `initial load lists the episode blacklist first`() = runVm {
        assertTrue(vm.lce.value is LceState.Content)
        val state = vm.state.value
        assertEquals(BlacklistTab.EPISODES, state.tab)
        assertEquals(2, state.episodeEntries.size)
        assertEquals(1, state.movieEntries.size)
        assertEquals(2, state.presented.size)
    }

    @Test
    fun `remove deletes the entry and reports success`() = runVm {
        val entry = vm.state.value.presented.first { it.subsId == "abc" }

        vm.remove(entry)

        assertEquals(BlacklistMessage.RemovedSynced, vm.state.value.message)
        assertEquals(1, vm.state.value.presented.size)
        val call = library.calls.single { it.operation == "delete_episodes_blacklist" }
        assertEquals("opensubtitles", call.arguments["provider"])
        assertEquals("abc", call.arguments["subs_id"])
    }

    @Test
    fun `offline remove queues and keeps the queued indicator`() = runVm {
        library.failure = { call -> if (call.operation == "delete_episodes_blacklist") IOException() else null }
        val entry = vm.state.value.presented.first { it.subsId == "abc" }

        vm.remove(entry)

        assertEquals(BlacklistMessage.RemovedQueued, vm.state.value.message)
        assertEquals(setOf("opensubtitles|abc"), vm.state.value.queuedKeys)
        // The optimistic removal already dropped the row from the visible list.
        assertEquals(1, vm.state.value.presented.size)
    }

    @Test
    fun `clear visible on the movies tab removes every entry`() = runVm {
        vm.setTab(BlacklistTab.MOVIES)

        vm.clearVisible()

        assertEquals(BlacklistMessage.ClearedSynced, vm.state.value.message)
        assertEquals(0, vm.state.value.movieEntries.size)
        assertEquals(2, vm.state.value.episodeEntries.size)
        val call = library.calls.single { it.operation == "delete_movies_blacklist" }
        assertEquals("True", call.arguments["all"])
    }

    @Test
    fun `offline clear queues the clear all for replay`() = runVm {
        library.failure = { call -> if (call.operation == "delete_episodes_blacklist") IOException() else null }

        vm.clearVisible()

        assertEquals(BlacklistMessage.ClearedQueued, vm.state.value.message)
        assertTrue(vm.state.value.queuedClearEpisodes)
        assertEquals(0, vm.state.value.episodeEntries.size)
        assertEquals("episodes/blacklist?all=True", queue.pending().single().path)
    }
}
