package dev.appolon.homelab.arr.feature.lidarr.settings.quality.vm

import dev.appolon.homelab.arr.api.lidarr.FakeLidarrCalendar
import dev.appolon.homelab.arr.api.lidarr.model.CustomFormatResource
import dev.appolon.homelab.arr.api.lidarr.model.CustomFormatSpecificationSchema
import dev.appolon.homelab.arr.api.lidarr.model.Field
import dev.appolon.homelab.arr.api.lidarr.model.ProfileFormatItemResource
import dev.appolon.homelab.arr.api.lidarr.model.QualityProfileResource
import dev.appolon.homelab.arr.core.ServerId
import dev.appolon.homelab.arr.feature.lidarr.settings.quality.FakeQualityActionQueue
import dev.appolon.homelab.arr.feature.lidarr.settings.quality.FakeQualityEntityStore
import dev.appolon.homelab.arr.feature.lidarr.settings.quality.data.CustomFormatConditionKind
import dev.appolon.homelab.arr.feature.lidarr.settings.quality.data.LidarrQualityRepository
import dev.appolon.homelab.arr.ui.lce.LceState
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.json.JsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LidarrCustomFormatsViewModelTest {

    private lateinit var api: FakeLidarrCalendar
    private lateinit var store: FakeQualityEntityStore
    private lateinit var queue: FakeQualityActionQueue
    private var now: Long = 1_000_000L

    @Before
    fun setUp() {
        api = FakeLidarrCalendar()
        store = FakeQualityEntityStore()
        queue = FakeQualityActionQueue()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun repository(scheduler: TestCoroutineScheduler) = LidarrQualityRepository(
        serverId = ServerId("s1"),
        store = store,
        qualityDefinitionApi = api,
        qualityProfileApi = api,
        qualityProfileSchemaApi = api,
        metadataProfileApi = api,
        metadataProfileSchemaApi = api,
        metadataProviderConfigApi = api,
        customFormatApi = api,
        delayProfileApi = api,
        releaseProfileApi = api,
        languageApi = api,
        actionQueue = queue,
        now = { now },
        ioContext = StandardTestDispatcher(scheduler),
    )

    @Test
    fun `list loads formats cache-first`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        api.customFormats[7] = CustomFormatResource(id = 7, name = "FLAC")
        val vm = LidarrCustomFormatsViewModel(repository(testScheduler))
        vm.lce.collectWhileActive(this)
        advanceUntilIdle()

        val content = (vm.lce.value as LceState.Content<LidarrCustomFormatsContent>).data
        assertEquals(listOf("FLAC"), content.formats.map { it.name })
        assertFalse(vm.state.value.refreshing)
    }

    @Test
    fun `editor parses editable conditions and passes unknown ones through`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        api.customFormats[7] = CustomFormatResource(
            id = 7,
            name = "FLAC",
            specifications = listOf(
                CustomFormatSpecificationSchema(
                    name = "Has FLAC",
                    implementation = "ReleaseSpecification",
                    fields = listOf(Field(order = 1, name = "value", value = JsonPrimitive("flac"))),
                ),
                CustomFormatSpecificationSchema(
                    name = "Future",
                    implementation = "SomeFutureSpecification",
                ),
            ),
        )
        val vm = LidarrCustomFormatEditorViewModel(
            formatId = 7,
            repository = repository(testScheduler),
        )
        vm.lce.collectWhileActive(this)
        advanceUntilIdle()

        val draft = vm.state.value.draft
        assertEquals(1, draft.conditions.size)
        assertEquals("flac", draft.conditions.first().value)
        assertEquals(1, draft.passthroughConditions.size)
        assertTrue(vm.state.value.loaded)
    }

    @Test
    fun `score rows list every profile and save writes changed scores`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        api.customFormats[7] = CustomFormatResource(id = 7, name = "FLAC")
        api.qualityProfiles[1] = QualityProfileResource(
            id = 1,
            name = "Standard",
            formatItems = listOf(ProfileFormatItemResource(id = 1, format = 7, name = "FLAC", score = 0)),
        )
        val vm = LidarrCustomFormatEditorViewModel(
            formatId = 7,
            repository = repository(testScheduler),
        )
        vm.lce.collectWhileActive(this)
        advanceUntilIdle()

        assertEquals(1, vm.state.value.scoreRows.size)
        vm.setScore(1, 25)
        vm.edit { it.copy(name = "FLAC") }
        vm.save()
        advanceUntilIdle()

        assertTrue(vm.state.value.savedNotice)
        val savedItems = api.qualityProfiles[1]!!.formatItems.orEmpty()
        assertTrue(savedItems.any { it.format == 7 && it.score == 25 })
    }

    @Test
    fun `regex tester updates live and flags invalid patterns`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val vm = LidarrCustomFormatEditorViewModel(
            formatId = null,
            repository = repository(testScheduler),
        )
        vm.lce.collectWhileActive(this)
        advanceUntilIdle()

        vm.setRegexTester("flac", "Artist-Album-FLAC")
        advanceUntilIdle()
        assertEquals(true, vm.state.value.regexResult)
        assertFalse(vm.state.value.regexPatternInvalid)

        vm.setRegexTester("([bad", "sample")
        advanceUntilIdle()
        assertTrue(vm.state.value.regexPatternInvalid)
    }

    @Test
    fun `offline create queues and keeps the optimistic row`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val vm = LidarrCustomFormatEditorViewModel(
            formatId = null,
            repository = repository(testScheduler),
        )
        vm.lce.collectWhileActive(this)
        advanceUntilIdle()

        store.clock = { now }
        api.error = { IOException("offline") }
        vm.edit { it.copy(name = "Offline format") }
        vm.addCondition(CustomFormatConditionKind.LANGUAGE)
        vm.save()
        advanceUntilIdle()

        assertTrue(vm.state.value.savedQueued)
        assertTrue(api.customFormats.isEmpty())
        assertEquals(1, queue.pending().size)
    }
}
