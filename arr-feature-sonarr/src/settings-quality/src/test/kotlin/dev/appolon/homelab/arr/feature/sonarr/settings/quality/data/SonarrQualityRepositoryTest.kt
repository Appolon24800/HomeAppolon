package dev.appolon.homelab.arr.feature.sonarr.settings.quality.data

import dev.appolon.homelab.arr.api.sonarr.FakeSonarrCalendar
import dev.appolon.homelab.arr.api.sonarr.model.CustomFormatResource
import dev.appolon.homelab.arr.api.sonarr.model.DelayProfileResource
import dev.appolon.homelab.arr.api.sonarr.model.Language
import dev.appolon.homelab.arr.api.sonarr.model.LanguageProfileItemResource
import dev.appolon.homelab.arr.api.sonarr.model.LanguageProfileResource
import dev.appolon.homelab.arr.api.sonarr.model.LanguageResource
import dev.appolon.homelab.arr.api.sonarr.model.ProfileFormatItemResource
import dev.appolon.homelab.arr.api.sonarr.model.Quality
import dev.appolon.homelab.arr.api.sonarr.model.QualityDefinitionLimitsResource
import dev.appolon.homelab.arr.api.sonarr.model.QualityDefinitionResource
import dev.appolon.homelab.arr.api.sonarr.model.QualityProfileResource
import dev.appolon.homelab.arr.api.sonarr.model.ReleaseProfileResource
import dev.appolon.homelab.arr.core.ArrError
import dev.appolon.homelab.arr.core.ArrResult
import dev.appolon.homelab.arr.core.ServerId
import dev.appolon.homelab.arr.feature.sonarr.library.data.WriteOutcome
import dev.appolon.homelab.arr.feature.sonarr.settings.quality.FakeQualityActionQueue
import dev.appolon.homelab.arr.data.testing.FakeEntityStore
import java.io.IOException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SonarrQualityRepositoryTest {

    private val serverId = ServerId("s1")
    private lateinit var store: FakeEntityStore
    private lateinit var queue: FakeQualityActionQueue
    private lateinit var api: FakeSonarrCalendar
    private lateinit var repository: SonarrQualityRepository
    private var now: Long = 1_000_000L

    @Before
    fun setUp() {
        store = FakeEntityStore()
        queue = FakeQualityActionQueue()
        api = FakeSonarrCalendar()
        repository = repository()
    }

    private fun repository() = SonarrQualityRepository(
        serverId = serverId,
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
    )

    private fun seed() {
        api.qualityDefinitionStore[1] = QualityDefinitionResource(
            id = 1,
            quality = Quality(id = 1, name = "WEBDL-1080p"),
            title = "WEBDL-1080p",
            weight = 10,
            minSize = 0.0,
            maxSize = 100.0,
        )
        api.qualityProfileStore[1] = QualityProfileResource(
            id = 1,
            name = "HD",
            formatItems = listOf(ProfileFormatItemResource(id = 1, format = 7, name = "Surround", score = 5)),
        )
        api.languageProfileStore[2] = LanguageProfileResource(
            id = 2,
            name = "English",
            cutoff = Language(id = 1, name = "English"),
            languages = listOf(
                LanguageProfileItemResource(id = 1, language = Language(id = 1, name = "English"), allowed = true),
            ),
        )
        api.customFormatStore[7] = CustomFormatResource(id = 7, name = "Surround")
        api.delayProfileStore[1] = DelayProfileResource(id = 1, enableUsenet = true, usenetDelay = 60, order = 0)
        api.releaseProfileStore[3] = ReleaseProfileResource(id = 3, name = "Obfuscated", enabled = true)
        api.languageStore[1] = LanguageResource(id = 1, name = "English")
    }

    @Test
    fun `refresh caches every table and sorts languages by id`() = runTest {
        seed()
        api.languageStore[0] = LanguageResource(id = 0, name = "Unknown")
        api.qualityDefinitionLimits = QualityDefinitionLimitsResource(min = 0, max = 1000)
        val result = repository.refresh()

        assertTrue(result is ArrResult.Ok)
        assertEquals(1, repository.cachedQualityDefinitions().size)
        assertEquals(1, repository.cachedQualityProfiles().size)
        assertEquals(1, repository.cachedLanguageProfiles().size)
        assertEquals(1, repository.cachedCustomFormats().size)
        assertEquals(1, repository.cachedDelayProfiles().size)
        assertEquals(1, repository.cachedReleaseProfiles().size)
        assertEquals(listOf(0, 1), repository.cachedLanguages().map { it.id })
        assertEquals(0, repository.cachedQualityDefinitionLimits()!!.min)
        assertEquals(1000, repository.cachedQualityDefinitionLimits()!!.max)
    }

    @Test
    fun `refresh reconciles rows the server no longer returns`() = runTest {
        seed()
        repository.refresh()
        api.qualityProfileStore.clear()
        repository.refresh()
        assertTrue(repository.cachedQualityProfiles().isEmpty())
    }

    @Test
    fun `isStale flips once the TTL has passed`() = runTest {
        seed()
        assertTrue(repository.isStale())
        repository.refresh()
        assertTrue(!repository.isStale())
        now += SonarrQualityRepository.QUALITY_TTL_MILLIS + 1
        assertTrue(repository.isStale())
    }

    @Test
    fun `refreshIfStale skips the network while fresh`() = runTest {
        seed()
        repository.refresh()
        api.error = { IOException("offline") }
        val result = repository.refreshIfStale()
        assertTrue(result is ArrResult.Ok)
    }

    @Test
    fun `refresh surfaces the network error when nothing is cached`() = runTest {
        api.error = { IOException("offline") }
        val result = repository.refresh()
        assertTrue((result as ArrResult.Err).error is ArrError.Network)
    }

    @Test
    fun `saveQualityDefinitions bulk updates and caches the response`() = runTest {
        seed()
        repository.refresh()
        val edited = repository.cachedQualityDefinitions().first().copy(title = "Web 1080p")
        val result = repository.saveQualityDefinitions(listOf(edited))

        assertTrue((result as ArrResult.Ok).value is WriteOutcome.Synced)
        assertEquals("Web 1080p", api.qualityDefinitionStore.values.first().title)
        assertEquals("Web 1080p", repository.cachedQualityDefinitions().first().title)
        assertTrue(queue.pending().isEmpty())
    }

    @Test
    fun `saveQualityDefinitions queues offline and applies optimistically`() = runTest {
        seed()
        repository.refresh()
        store.clock = { now } // keep single-row puts inside the TTL window
        api.error = { IOException("offline") }
        val edited = repository.cachedQualityDefinitions().first().copy(weight = 25)
        val result = repository.saveQualityDefinitions(listOf(edited))

        assertTrue((result as ArrResult.Ok).value is WriteOutcome.Queued)
        assertEquals(25, repository.cachedQualityDefinitions().first().weight)
        assertEquals("api/v3/qualitydefinition/update", queue.pending().first().path)
    }

    @Test
    fun `saveQualityProfile creates and caches the server row`() = runTest {
        seed()
        val result = repository.saveQualityProfile(QualityProfileResource(name = "Any"))

        assertTrue((result as ArrResult.Ok).value is WriteOutcome.Synced)
        assertEquals("Any", api.qualityProfileStore.values.first { it.id == 2 }.name)
        assertEquals("Any", repository.cachedQualityProfiles().first().name)
    }

    @Test
    fun `saveQualityProfile update queues offline and applies optimistically`() = runTest {
        seed()
        repository.refresh()
        store.clock = { now }
        api.error = { IOException("offline") }
        val edited = repository.cachedQualityProfiles().first().copy(name = "HD-All")
        val result = repository.saveQualityProfile(edited)

        assertTrue((result as ArrResult.Ok).value is WriteOutcome.Queued)
        assertEquals("HD-All", repository.cachedQualityProfiles().first().name)
        assertEquals("api/v3/qualityprofile/1", queue.pending().first().path)
    }

    @Test
    fun `deleteQualityProfile removes the row when online`() = runTest {
        seed()
        repository.refresh()
        val result = repository.deleteQualityProfile(1)

        assertTrue((result as ArrResult.Ok).value is WriteOutcome.Synced)
        assertTrue(repository.cachedQualityProfiles().isEmpty())
        assertTrue(queue.pending().isEmpty())
    }

    @Test
    fun `deleteQualityProfile queues offline and drops the row optimistically`() = runTest {
        seed()
        repository.refresh()
        api.error = { IOException("offline") }
        val result = repository.deleteQualityProfile(1)

        assertTrue((result as ArrResult.Ok).value is WriteOutcome.Queued)
        assertTrue(repository.cachedQualityProfiles().isEmpty())
        assertEquals("DELETE", queue.pending().first().method)
    }

    @Test
    fun `saveLanguageProfile creates and caches the server row`() = runTest {
        seed()
        val profile = LanguageProfileResource(
            name = "Deutsch",
            languages = listOf(
                LanguageProfileItemResource(
                    language = Language(id = 4, name = "German"),
                    allowed = true,
                ),
            ),
            cutoff = Language(id = 4, name = "German"),
        )
        val result = repository.saveLanguageProfile(profile)

        assertTrue((result as ArrResult.Ok).value is WriteOutcome.Synced)
        assertEquals("Deutsch", repository.cachedLanguageProfiles().first().name)
    }

    @Test
    fun `saveLanguageProfile create queues offline under a temporary id`() = runTest {
        seed()
        api.error = { IOException("offline") }
        val result = repository.saveLanguageProfile(LanguageProfileResource(name = "Deutsch"))

        assertTrue((result as ArrResult.Ok).value is WriteOutcome.Queued)
        assertEquals(1, repository.cachedLanguageProfiles().size)
        assertTrue(repository.cachedLanguageProfiles().first().id < 0)
        assertEquals("api/v3/languageprofile", queue.pending().first().path)
    }

    @Test
    fun `quality and language profile schemas are cached for offline creates`() = runTest {
        seed()
        val schema = repository.refreshQualityProfileSchema()
        val languageSchema = repository.refreshLanguageProfileSchema()

        assertTrue(schema is ArrResult.Ok)
        assertTrue(languageSchema is ArrResult.Ok)
        assertEquals("New Profile", repository.cachedQualityProfileSchema()!!.name)
        assertTrue(repository.cachedLanguageProfileSchema() != null)
    }

    @Test
    fun `moveDelayProfile reorders through the endpoint when online`() = runTest {
        seed()
        api.delayProfileStore[2] = DelayProfileResource(id = 2, order = 1)
        repository.refresh()
        val result = repository.moveDelayProfile(2, afterId = null)

        assertTrue((result as ArrResult.Ok).value is WriteOutcome.Synced)
        assertEquals(1, api.delayProfileStore.values.first { it.id == 2 }.order)
    }

    @Test
    fun `moveDelayProfile queues offline and reorders the cache`() = runTest {
        seed()
        api.delayProfileStore[2] = DelayProfileResource(id = 2, order = 1)
        repository.refresh()
        store.clock = { now }
        api.error = { IOException("offline") }
        val result = repository.moveDelayProfile(2, afterId = null)

        assertTrue((result as ArrResult.Ok).value is WriteOutcome.Queued)
        assertEquals(
            listOf(2, 1),
            repository.cachedDelayProfiles().sortedBy { it.order }.map { it.id },
        )
        assertEquals("api/v3/delayprofile/reorder/2", queue.pending().first().path)
    }

    @Test
    fun `deleteReleaseProfile queues offline`() = runTest {
        seed()
        repository.refresh()
        api.error = { IOException("offline") }
        val result = repository.deleteReleaseProfile(3)

        assertTrue((result as ArrResult.Ok).value is WriteOutcome.Queued)
        assertEquals("api/v3/releaseprofile/3", queue.pending().first().path)
    }

    @Test
    fun `assignFormatScore rewrites an attached score and attaches missing formats`() = runTest {
        seed()
        repository.refresh()
        val profile = repository.cachedQualityProfiles().first()

        val updated = repository.assignFormatScore(profile, formatId = 7, score = 9)
        assertTrue((updated as ArrResult.Ok).value is WriteOutcome.Synced)
        assertEquals(9, repository.cachedQualityProfiles().first().formatItems!!.first().score)

        val attached = repository.assignFormatScore(profile, formatId = 42, score = 3)
        assertTrue((attached as ArrResult.Ok).value is WriteOutcome.Synced)
        val items = repository.cachedQualityProfiles().first().formatItems!!
        assertEquals(3, items.first { it.format == 42 }.score)
    }

    @Test
    fun `queuedCount counts only this feature's paths`() = runTest {
        queue.enqueue(queue.action("a", path = "api/v3/qualityprofile/1"))
        queue.enqueue(queue.action("b", path = "api/v3/languageprofile/2"))
        queue.enqueue(queue.action("c", path = "api/v3/series/editor"))
        assertEquals(2, repository.queuedCount().first())
    }
}
