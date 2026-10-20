package dev.appolon.homelab.arr.feature.radarr.settings.quality.vm

import dev.appolon.homelab.arr.api.radarr.FakeRadarrCalendar
import dev.appolon.homelab.arr.api.radarr.model.CustomFormatResource
import dev.appolon.homelab.arr.api.radarr.model.CustomFormatSpecificationSchema
import dev.appolon.homelab.arr.api.radarr.model.ProfileFormatItemResource
import dev.appolon.homelab.arr.api.radarr.model.QualityProfileResource
import dev.appolon.homelab.arr.core.ServerId
import dev.appolon.homelab.arr.feature.radarr.settings.quality.FakeQualityActionQueue
import dev.appolon.homelab.arr.data.testing.FakeEntityStore
import dev.appolon.homelab.arr.feature.radarr.settings.quality.data.CustomFormatConditionKind
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
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RadarrCustomFormatsViewModelTest {

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

    @Test
    fun `list loads formats cache-first and deletes after confirmation`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        api.customFormats += CustomFormatResource(id = 7, name = "Surround")
        val vm = RadarrCustomFormatsViewModel(repository(testScheduler))
        vm.lce.collectWhileActive(this)
        advanceUntilIdle()

        val content = (vm.lce.value as LceState.Content<RadarrCustomFormatsContent>).data
        assertEquals(listOf("Surround"), content.formats.map { it.name })

        vm.requestDelete(7)
        vm.deleteConfirmed()
        advanceUntilIdle()
        assertTrue(api.customFormats.isEmpty())
    }

    @Test
    fun `editor parses editable conditions and carries unknown ones through`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        api.customFormats += CustomFormatResource(
            id = 7,
            name = "Surround",
            specifications = listOf(
                CustomFormatSpecificationSchema(implementation = "ReleaseTitleSpecification"),
                CustomFormatSpecificationSchema(implementation = "YearSpecification"),
            ),
        )
        val vm = RadarrCustomFormatEditorViewModel(formatId = 7, repository = repository(testScheduler))
        vm.lce.collectWhileActive(this)
        advanceUntilIdle()

        val draft = vm.state.value.draft
        assertEquals(1, draft.conditions.size)
        assertEquals(CustomFormatConditionKind.RELEASE_NAME, draft.conditions.first().kind)
        assertEquals(1, draft.passthroughConditions.size)

        vm.edit { it.copy(name = "Surround 2") }
        vm.save()
        advanceUntilIdle()

        // The unknown condition survives the round trip untouched.
        val saved = api.customFormats.single()
        assertEquals("Surround 2", saved.name)
        assertEquals(2, saved.specifications!!.size)
        assertTrue(saved.specifications!!.any { it.implementation == "YearSpecification" })
    }

    @Test
    fun `regex tester tracks validity and match live`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val vm = RadarrCustomFormatEditorViewModel(formatId = null, repository = repository(testScheduler))
        vm.lce.collectWhileActive(this)
        advanceUntilIdle()

        vm.setRegexTester("remux", "Movie.2024.REMUX.1080p")
        assertEquals(java.lang.Boolean.TRUE, vm.state.value.regexResult)
        assertFalse(vm.state.value.regexPatternInvalid)

        vm.setRegexTester("([broken", "sample")
        assertNull(vm.state.value.regexResult)
        assertTrue(vm.state.value.regexPatternInvalid)
    }

    @Test
    fun `condition add and remove edit the draft`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val vm = RadarrCustomFormatEditorViewModel(formatId = null, repository = repository(testScheduler))
        vm.lce.collectWhileActive(this)
        advanceUntilIdle()

        vm.addCondition(CustomFormatConditionKind.SIZE_RANGE)
        val condition = vm.state.value.draft.conditions.single()
        vm.editCondition(condition) { it.copy(minSize = "1", maxSize = "20") }
        vm.addCondition(CustomFormatConditionKind.EDITION)
        assertEquals(2, vm.state.value.draft.conditions.size)

        val edition = vm.state.value.draft.conditions.first { it.kind == CustomFormatConditionKind.EDITION }
        vm.removeCondition(edition)
        assertEquals(1, vm.state.value.draft.conditions.size)
        assertEquals("1", vm.state.value.draft.conditions.single().minSize)
        assertTrue(vm.state.value.dirty)
    }

    @Test
    fun `score rows resolve per profile and changed scores save into profiles`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        api.customFormats += CustomFormatResource(id = 7, name = "Surround")
        api.qualityProfiles += QualityProfileResource(
            id = 1,
            name = "HD",
            formatItems = listOf(ProfileFormatItemResource(format = 7, name = "Surround", score = 5)),
        )
        val vm = RadarrCustomFormatEditorViewModel(formatId = 7, repository = repository(testScheduler))
        vm.lce.collectWhileActive(this)
        advanceUntilIdle()

        val rows = vm.state.value.scoreRows
        assertEquals(1, rows.size)
        assertEquals(5, rows.first().score)
        assertTrue(rows.first().attached)

        vm.edit { it.copy(name = "Surround") }
        vm.setScore(1, 42)
        vm.save()
        advanceUntilIdle()

        val savedFormat = api.customFormats.single()
        assertEquals("Surround", savedFormat.name)
        assertEquals(42, api.qualityProfiles.first().formatItems!!.first { it.format == 7 }.score)
    }

    @Test
    fun `unchanged scores skip the profile save`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        api.customFormats += CustomFormatResource(id = 7, name = "Surround")
        api.qualityProfiles += QualityProfileResource(
            id = 1,
            name = "HD",
            formatItems = listOf(ProfileFormatItemResource(format = 7, score = 5)),
        )
        val vm = RadarrCustomFormatEditorViewModel(formatId = 7, repository = repository(testScheduler))
        vm.lce.collectWhileActive(this)
        advanceUntilIdle()

        vm.edit { it.copy(name = "Surround+") }
        vm.save()
        advanceUntilIdle()

        // The format saved; the profile was never PUT because no score moved.
        assertEquals("Surround+", api.customFormats.single().name)
        assertEquals(1, api.qualityProfiles.size)
        assertTrue(queue.pending().isEmpty())
    }

    @Test
    fun `offline create queues and keeps the draft`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val vm = RadarrCustomFormatEditorViewModel(formatId = null, repository = repository(testScheduler))
        vm.lce.collectWhileActive(this)
        advanceUntilIdle()

        api.errorFor = { IOException("offline") }
        vm.edit { it.copy(name = "Offline Format") }
        vm.save()
        advanceUntilIdle()

        assertTrue(vm.state.value.savedQueued)
        assertEquals("POST", queue.pending().single().method)
        assertEquals("Offline Format", queue.pending().single().bodyJson!!.substringAfter("\"name\":\"").substringBefore('"'))
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
private fun <T> StateFlow<T>.collectWhileActive(scope: TestScope) {
    scope.backgroundScope.launch { this@collectWhileActive.collect { } }
}
