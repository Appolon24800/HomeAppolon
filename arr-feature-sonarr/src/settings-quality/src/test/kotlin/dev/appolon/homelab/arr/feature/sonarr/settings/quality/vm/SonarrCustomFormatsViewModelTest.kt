package dev.appolon.homelab.arr.feature.sonarr.settings.quality.vm

import dev.appolon.homelab.arr.api.sonarr.FakeSonarrCalendar
import dev.appolon.homelab.arr.api.sonarr.model.CustomFormatResource
import dev.appolon.homelab.arr.api.sonarr.model.CustomFormatSpecificationSchema
import dev.appolon.homelab.arr.api.sonarr.model.Field
import dev.appolon.homelab.arr.api.sonarr.model.ProfileFormatItemResource
import dev.appolon.homelab.arr.api.sonarr.model.QualityProfileResource
import dev.appolon.homelab.arr.core.ArrResult
import dev.appolon.homelab.arr.core.ServerId
import dev.appolon.homelab.arr.feature.sonarr.library.data.WriteOutcome
import dev.appolon.homelab.arr.feature.sonarr.settings.quality.FakeQualityActionQueue
import dev.appolon.homelab.arr.data.testing.FakeEntityStore
import dev.appolon.homelab.arr.feature.sonarr.settings.quality.data.CustomFormatCondition
import dev.appolon.homelab.arr.feature.sonarr.settings.quality.data.CustomFormatConditionKind
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
import kotlinx.serialization.json.JsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SonarrCustomFormatsViewModelTest {

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

    @Test
    fun `list loads formats cache-first`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        api.customFormatStore[7] = CustomFormatResource(id = 7, name = "Surround")
        val vm = SonarrCustomFormatsViewModel(repository(testScheduler))
        vm.lce.collectWhileActive(this)
        advanceUntilIdle()

        val content = (vm.lce.value as LceState.Content<SonarrCustomFormatsContent>).data
        assertEquals(listOf("Surround"), content.formats.map { it.name })
        assertFalse(vm.state.value.refreshing)
    }

    @Test
    fun `delete asks for confirmation then removes the row`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        api.customFormatStore[7] = CustomFormatResource(id = 7, name = "Surround")
        val vm = SonarrCustomFormatsViewModel(repository(testScheduler))
        vm.lce.collectWhileActive(this)
        advanceUntilIdle()

        vm.requestDelete(7)
        vm.deleteConfirmed()
        advanceUntilIdle()

        assertEquals(CustomFormatsNotice.DELETED, vm.state.value.notice)
        assertTrue(api.customFormatStore.isEmpty())
    }

    @Test
    fun `editor parses editable conditions and carries unknown ones through`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        api.customFormatStore[7] = CustomFormatResource(
            id = 7,
            name = "Surround",
            specifications = listOf(
                CustomFormatSpecificationSchema(
                    name = "Has surround",
                    implementation = "ReleaseSpecification",
                    fields = listOf(Field(name = "value", value = JsonPrimitive("surround"))),
                ),
                CustomFormatSpecificationSchema(implementation = "FutureSpecification"),
            ),
        )
        val vm = SonarrCustomFormatEditorViewModel(formatId = 7, repository = repository(testScheduler))
        vm.lce.collectWhileActive(this)
        advanceUntilIdle()

        val draft = vm.state.value.draft
        assertEquals(1, draft.conditions.size)
        assertEquals(CustomFormatConditionKind.RELEASE_NAME, draft.conditions.first().kind)
        assertEquals(1, draft.passthroughConditions.size)
        assertTrue(vm.state.value.loaded)
    }

    @Test
    fun `condition edits mark the draft dirty and save writes the schema`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        api.customFormatStore[7] = CustomFormatResource(id = 7, name = "Surround")
        val vm = SonarrCustomFormatEditorViewModel(formatId = 7, repository = repository(testScheduler))
        vm.lce.collectWhileActive(this)
        advanceUntilIdle()

        vm.addCondition(CustomFormatConditionKind.RELEASE_NAME)
        vm.editCondition(vm.state.value.draft.conditions.first()) { it.copy(value = "remux") }
        vm.save()
        advanceUntilIdle()

        assertTrue(vm.state.value.savedNotice)
        val saved = api.customFormatStore.values.first()
        assertEquals("ReleaseSpecification", saved.specifications!!.first().implementation)
        assertEquals(
            "remux",
            (saved.specifications!!.first().fields!!.first().value as JsonPrimitive).content,
        )
    }

    @Test
    fun `regex tester updates live and flags invalid patterns`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val vm = SonarrCustomFormatEditorViewModel(formatId = null, repository = repository(testScheduler))
        vm.lce.collectWhileActive(this)
        advanceUntilIdle()

        vm.setRegexTester("remux", "Show.S01.REMUX.1080p")
        assertEquals(true, vm.state.value.regexResult)
        assertFalse(vm.state.value.regexPatternInvalid)

        vm.setRegexTester("(unclosed", "Show")
        assertNull(vm.state.value.regexResult)
        assertTrue(vm.state.value.regexPatternInvalid)
    }

    @Test
    fun `score rows list every profile and save only changed scores`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        api.customFormatStore[7] = CustomFormatResource(id = 7, name = "Surround")
        api.qualityProfileStore[1] = QualityProfileResource(
            id = 1,
            name = "HD",
            formatItems = listOf(ProfileFormatItemResource(id = 1, format = 7, name = "Surround", score = 5)),
        )
        val vm = SonarrCustomFormatEditorViewModel(formatId = 7, repository = repository(testScheduler))
        vm.lce.collectWhileActive(this)
        advanceUntilIdle()

        val rows = vm.state.value.scoreRows
        assertEquals(listOf("HD"), rows.map { it.profile.name })
        assertEquals(5, rows.first().score)

        vm.setScore(1, 10)
        vm.save()
        advanceUntilIdle()

        assertTrue(vm.state.value.savedNotice)
        assertEquals(10, api.qualityProfileStore.values.first().formatItems!!.first().score)
    }

    @Test
    fun `offline save queues and reports the queued notice`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        api.customFormatStore[7] = CustomFormatResource(id = 7, name = "Surround")
        store.clock = { now }
        val vm = SonarrCustomFormatEditorViewModel(formatId = 7, repository = repository(testScheduler))
        vm.lce.collectWhileActive(this)
        advanceUntilIdle()

        api.error = { IOException("offline") }
        vm.edit { it.copy(name = "Renamed") }
        vm.save()
        advanceUntilIdle()

        assertTrue(vm.state.value.savedQueued)
        assertEquals(1, queue.pending().size)
        assertEquals("api/v3/customformat/7", queue.pending().first().path)
    }

    @Test
    fun `editor drops unknown conditions into passthrough on save`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        api.customFormatStore[7] = CustomFormatResource(
            id = 7,
            name = "Surround",
            specifications = listOf(CustomFormatSpecificationSchema(implementation = "FutureSpecification")),
        )
        val vm = SonarrCustomFormatEditorViewModel(formatId = 7, repository = repository(testScheduler))
        vm.lce.collectWhileActive(this)
        advanceUntilIdle()

        vm.addCondition(CustomFormatConditionKind.SIZE_RANGE)
        vm.editCondition(vm.state.value.draft.conditions.first()) { it.copy(minSize = "1") }
        vm.save()
        advanceUntilIdle()

        val implementations = api.customFormatStore.values.first().specifications!!.map { it.implementation }
        assertTrue("FutureSpecification" in implementations)
        assertTrue("SizeSpecification" in implementations)
    }

    @Test
    fun `incomplete size conditions still save their partial bounds`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val condition = CustomFormatCondition(kind = CustomFormatConditionKind.SIZE_RANGE, maxSize = "12")
        val schema = dev.appolon.homelab.arr.feature.sonarr.settings.quality.data.CustomFormatConditions
            .toSchema(condition)

        assertEquals("SizeSpecification", schema.implementation)
        assertEquals(2, schema.fields!!.size)
    }

    @Test
    fun `writeOutcome surfaced for a format delete queued offline`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        api.customFormatStore[7] = CustomFormatResource(id = 7, name = "Surround")
        val repository = repository(testScheduler)
        repository.refreshIfStale()
        advanceUntilIdle()
        api.error = { IOException("offline") }
        val result = repository.deleteCustomFormat(7)
        advanceUntilIdle()

        assertTrue((result as ArrResult.Ok).value is WriteOutcome.Queued)
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
private fun <T> StateFlow<T>.collectWhileActive(scope: TestScope) {
    scope.backgroundScope.launch { this@collectWhileActive.collect { } }
}
