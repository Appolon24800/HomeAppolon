package dev.appolon.homelab.arr.feature.sonarr.settings.quality.vm

import dev.appolon.homelab.arr.api.sonarr.FakeSonarrCalendar
import dev.appolon.homelab.arr.api.sonarr.model.ReleaseProfileResource
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
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SonarrReleaseProfilesViewModelTest {

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

    @Test
    fun `list loads release profiles cache-first`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        api.releaseProfileStore[3] = ReleaseProfileResource(id = 3, name = "Obfuscated", enabled = true)
        val vm = SonarrReleaseProfilesViewModel(repository(testScheduler))
        vm.lce.collectWhileActive(this)
        advanceUntilIdle()

        val content = (vm.lce.value as LceState.Content<SonarrReleaseProfilesContent>).data
        assertEquals(listOf("Obfuscated"), content.profiles.map { it.name })
        assertFalse(vm.state.value.refreshing)
    }

    @Test
    fun `splitTerms reads comma-joined strings and arrays`() {
        assertEquals(
            listOf("remux", "proper"),
            ReleaseProfileDraft.splitTerms(JsonPrimitive("remux,proper")),
        )
        assertEquals(
            listOf("x264", "h265"),
            ReleaseProfileDraft.splitTerms(buildJsonArray { add(JsonPrimitive("x264")); add(JsonPrimitive("h265")) }),
        )
        assertEquals(emptyList<String>(), ReleaseProfileDraft.splitTerms(null))
        assertEquals(
            listOf("a", "b"),
            ReleaseProfileDraft.splitText("a\nb\n"),
        )
    }

    @Test
    fun `joinTerms writes an unambiguous array`() {
        val joined = ReleaseProfileDraft.joinTerms(listOf("remux", " ", "proper"))
        assertTrue(joined is JsonArray)
        assertEquals(2, (joined as JsonArray).size)
        assertEquals("remux", joined.first().let { (it as JsonPrimitive).content })
    }

    @Test
    fun `draft round trip preserves Sonarr-specific fields`() {
        val resource = ReleaseProfileResource(
            id = 3,
            name = "Obfuscated",
            enabled = true,
            required = JsonPrimitive("remux,proper"),
            ignored = buildJsonArray { add(JsonPrimitive("obfuscated")) },
            airDateRestriction = true,
            airDateGracePeriod = 12,
            allowSeasonPackWithoutAllEpisodesAired = true,
            tags = listOf(4),
            excludedTags = listOf(5),
        )
        val draft = ReleaseProfileDraft.from(resource)
        val rebuilt = draft.toResource(resource)

        assertEquals(listOf("remux", "proper"), draft.mustContain)
        assertEquals(listOf("obfuscated"), draft.mustNotContain)
        assertTrue(rebuilt.airDateRestriction)
        assertEquals(12, rebuilt.airDateGracePeriod)
        assertTrue(rebuilt.allowSeasonPackWithoutAllEpisodesAired)
        assertEquals(listOf(4), rebuilt.tags)
        assertEquals(listOf(5), rebuilt.excludedTags)
    }

    @Test
    fun `save posts a new draft with the edited terms`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val vm = SonarrReleaseProfilesViewModel(repository(testScheduler))
        vm.lce.collectWhileActive(this)
        advanceUntilIdle()

        vm.save(
            ReleaseProfileDraft(
                name = "No obfuscated",
                mustContain = listOf("x264"),
                mustNotContain = listOf("obfuscated"),
                indexerId = "2",
            ),
        )
        advanceUntilIdle()

        assertEquals(ReleaseProfilesNotice.CREATED, vm.state.value.notice)
        val saved = api.releaseProfileStore.values.first()
        assertEquals("No obfuscated", saved.name)
        assertEquals(2, saved.indexerId)
        assertEquals(
            listOf("x264"),
            ReleaseProfileDraft.splitTerms(saved.required),
        )
        assertEquals(
            listOf("obfuscated"),
            ReleaseProfileDraft.splitTerms(saved.ignored),
        )
    }

    @Test
    fun `save editing an existing row keeps its original indexer when blank`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        api.releaseProfileStore[3] = ReleaseProfileResource(id = 3, name = "Old", indexerId = 5)
        val vm = SonarrReleaseProfilesViewModel(repository(testScheduler))
        vm.lce.collectWhileActive(this)
        advanceUntilIdle()

        vm.save(ReleaseProfileDraft.from(api.releaseProfileStore.getValue(3)).copy(name = "New"))
        advanceUntilIdle()

        assertEquals(ReleaseProfilesNotice.UPDATED, vm.state.value.notice)
        assertEquals(5, api.releaseProfileStore.getValue(3).indexerId)
    }

    @Test
    fun `a blank name never saves`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val vm = SonarrReleaseProfilesViewModel(repository(testScheduler))
        vm.lce.collectWhileActive(this)
        advanceUntilIdle()

        vm.save(ReleaseProfileDraft(name = " "))
        advanceUntilIdle()

        assertTrue(api.releaseProfileStore.isEmpty())
    }

    @Test
    fun `delete confirms then removes the row`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        api.releaseProfileStore[3] = ReleaseProfileResource(id = 3, name = "Obfuscated")
        val vm = SonarrReleaseProfilesViewModel(repository(testScheduler))
        vm.lce.collectWhileActive(this)
        advanceUntilIdle()

        vm.requestDelete(3)
        vm.deleteConfirmed()
        advanceUntilIdle()

        assertEquals(ReleaseProfilesNotice.DELETED, vm.state.value.notice)
        assertTrue(api.releaseProfileStore.isEmpty())
    }

    @Test
    fun `offline save queues and keeps the optimistic row`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        store.clock = { now }
        val vm = SonarrReleaseProfilesViewModel(repository(testScheduler))
        vm.lce.collectWhileActive(this)
        advanceUntilIdle()

        api.error = { IOException("offline") }
        vm.save(ReleaseProfileDraft(name = "Queued", mustContain = listOf("x264")))
        advanceUntilIdle()

        assertEquals(ReleaseProfilesNotice.QUEUED, vm.state.value.notice)
        assertTrue(api.releaseProfileStore.isEmpty())
        assertEquals("api/v3/releaseprofile", queue.pending().first().path)
        val content = (vm.lce.value as LceState.Content<SonarrReleaseProfilesContent>).data
        assertEquals(listOf("Queued"), content.profiles.map { it.name })
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
private fun <T> StateFlow<T>.collectWhileActive(scope: TestScope) {
    scope.backgroundScope.launch { this@collectWhileActive.collect { } }
}
