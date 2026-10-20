package dev.appolon.homelab.arr.feature.radarr.settings.quality.data

import dev.appolon.homelab.arr.api.radarr.FakeRadarrCalendar
import dev.appolon.homelab.arr.api.radarr.model.CustomFormatResource
import dev.appolon.homelab.arr.api.radarr.model.DelayProfileResource
import dev.appolon.homelab.arr.api.radarr.model.LanguageResource
import dev.appolon.homelab.arr.api.radarr.model.ProfileFormatItemResource
import dev.appolon.homelab.arr.api.radarr.model.Quality
import dev.appolon.homelab.arr.api.radarr.model.QualityDefinitionResource
import dev.appolon.homelab.arr.api.radarr.model.QualityProfileResource
import dev.appolon.homelab.arr.api.radarr.model.ReleaseProfileResource
import dev.appolon.homelab.arr.core.ArrError
import dev.appolon.homelab.arr.core.ArrResult
import dev.appolon.homelab.arr.core.ServerId
import dev.appolon.homelab.arr.feature.radarr.library.data.WriteOutcome
import dev.appolon.homelab.arr.feature.radarr.settings.quality.FakeQualityActionQueue
import dev.appolon.homelab.arr.feature.radarr.settings.quality.FakeQualityEntityStore
import java.io.IOException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class RadarrQualityRepositoryTest {

    private val serverId = ServerId("s1")
    private lateinit var store: FakeQualityEntityStore
    private lateinit var queue: FakeQualityActionQueue
    private lateinit var api: FakeRadarrCalendar
    private lateinit var repository: RadarrQualityRepository
    private var now: Long = 1_000_000L

    @Before
    fun setUp() {
        store = FakeQualityEntityStore()
        queue = FakeQualityActionQueue()
        api = FakeRadarrCalendar()
        repository = repository()
    }

    private fun repository() = RadarrQualityRepository(
        serverId = serverId,
        store = store,
        qualityDefinitionApi = api,
        qualityProfileApi = api,
        qualityProfileSchemaApi = api,
        customFormatApi = api,
        delayProfileApi = api,
        releaseProfileApi = api,
        languageApi = api,
        actionQueue = queue,
        now = { now },
    )

    private fun seed() {
        api.qualityDefinitions += QualityDefinitionResource(
            id = 1,
            quality = Quality(id = 1, name = "WEBDL-1080p"),
            title = "WEBDL-1080p",
            weight = 10,
            minSize = 0.0,
            maxSize = 100.0,
        )
        api.qualityProfiles += QualityProfileResource(
            id = 1,
            name = "HD",
            formatItems = listOf(ProfileFormatItemResource(id = 1, format = 7, name = "Surround", score = 5)),
        )
        api.customFormats += CustomFormatResource(id = 7, name = "Surround")
        api.delayProfiles += DelayProfileResource(id = 1, enableUsenet = true, usenetDelay = 60, order = 0)
        api.releaseProfiles += ReleaseProfileResource(id = 3, name = "Obfuscated", enabled = true)
        api.languages += LanguageResource(id = 1, name = "English")
    }

    @Test
    fun `refresh caches every table and sorts languages by id`() = runTest {
        seed()
        api.languages += LanguageResource(id = 0, name = "Unknown")
        val result = repository.refresh()

        assertTrue(result is ArrResult.Ok)
        assertEquals(1, repository.cachedQualityDefinitions().size)
        assertEquals(1, repository.cachedQualityProfiles().size)
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
        api.qualityProfiles.clear()
        repository.refresh()
        assertTrue(repository.cachedQualityProfiles().isEmpty())
    }

    @Test
    fun `isStale flips once the TTL has passed`() = runTest {
        seed()
        assertTrue(repository.isStale())
        repository.refresh()
        assertTrue(!repository.isStale())
        now += RadarrQualityRepository.QUALITY_TTL_MILLIS + 1
        assertTrue(repository.isStale())
    }

    @Test
    fun `refreshIfStale skips the network while fresh`() = runTest {
        seed()
        repository.refresh()
        api.errorFor = { IOException("offline") }
        val result = repository.refreshIfStale()
        assertTrue(result is ArrResult.Ok)
    }

    @Test
    fun `refresh surfaces the network error when nothing is cached`() = runTest {
        api.errorFor = { IOException("offline") }
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
        assertEquals("Web 1080p", api.qualityDefinitions.first().title)
        assertEquals("Web 1080p", repository.cachedQualityDefinitions().first().title)
        assertTrue(queue.pending().isEmpty())
    }

    @Test
    fun `saveQualityDefinitions queues offline and applies optimistically`() = runTest {
        seed()
        repository.refresh()
        store.clock = { now } // keep single-row puts inside the TTL window
        api.errorFor = { IOException("offline") }
        val edited = repository.cachedQualityDefinitions().first().copy(weight = 25)
        val result = repository.saveQualityDefinitions(listOf(edited))

        assertTrue((result as ArrResult.Ok).value is WriteOutcome.Queued)
        assertEquals(25, repository.cachedQualityDefinitions().first().weight)
        val action = queue.pending().single()
        assertEquals("PUT", action.method)
        assertEquals("api/v3/qualitydefinition/update", action.path)
        assertTrue(action.bodyJson!!.contains("\"weight\":25"))
    }

    @Test
    fun `quality profile update PUTs and caches the saved row`() = runTest {
        seed()
        repository.refresh()
        val edited = repository.cachedQualityProfiles().first().copy(name = "HD Pro")
        val result = repository.saveQualityProfile(edited)

        assertTrue((result as ArrResult.Ok).value is WriteOutcome.Synced)
        assertEquals("HD Pro", api.qualityProfiles.first().name)
        assertEquals("HD Pro", repository.cachedQualityProfiles().first().name)
    }

    @Test
    fun `quality profile create POSTs and offline creates use a temporary id`() = runTest {
        seed()
        repository.refresh()
        val draft = QualityProfileResource(id = 0, name = "Any")

        val synced = repository.saveQualityProfile(draft)
        assertTrue((synced as ArrResult.Ok).value is WriteOutcome.Synced)
        assertEquals("Any", api.qualityProfiles[1].name)

        api.errorFor = { IOException("offline") }
        val queued = repository.saveQualityProfile(QualityProfileResource(id = 0, name = "Offline"))
        assertTrue((queued as ArrResult.Ok).value is WriteOutcome.Queued)
        val cached = repository.cachedQualityProfiles()
        assertEquals("Offline", cached.last().name)
        assertTrue(cached.last().id < 0)
        assertEquals("POST", queue.pending().single().method)
        assertTrue(queue.pending().single().bodyJson!!.contains("\"name\":\"Offline\""))
    }

    @Test
    fun `quality profile update queues offline and keeps the fetch stamp`() = runTest {
        seed()
        repository.refresh()
        val stampBefore = store.lastFetchedAt(
            serverId,
            RadarrQualityRepository.QUALITY_PROFILE_TYPE,
        )
        api.errorFor = { IOException("offline") }
        val result = repository.saveQualityProfile(
            repository.cachedQualityProfiles().first().copy(name = "Queued Name"),
        )

        assertTrue((result as ArrResult.Ok).value is WriteOutcome.Queued)
        assertEquals("Queued Name", repository.cachedQualityProfiles().first().name)
        assertEquals(
            stampBefore,
            store.lastFetchedAt(serverId, RadarrQualityRepository.QUALITY_PROFILE_TYPE),
        )
    }

    @Test
    fun `delete quality profile removes the row online and queues offline`() = runTest {
        seed()
        repository.refresh()

        api.errorFor = { IOException("offline") }
        val queued = repository.deleteQualityProfile(1)
        assertTrue((queued as ArrResult.Ok).value is WriteOutcome.Queued)
        assertTrue(repository.cachedQualityProfiles().isEmpty())
        val action = queue.pending().single()
        assertEquals("DELETE", action.method)
        assertEquals("api/v3/qualityprofile/1", action.path)

        api.errorFor = { null }
        val synced = repository.deleteQualityProfile(1)
        assertTrue((synced as ArrResult.Ok).value is WriteOutcome.Synced)
        assertTrue(api.qualityProfiles.isEmpty())
    }

    @Test
    fun `custom format and release profile writes follow the same pattern`() = runTest {
        seed()
        repository.refresh()

        val format = repository.saveCustomFormat(
            CustomFormatResource(id = 0, name = "Dual Audio"),
        )
        assertTrue((format as ArrResult.Ok).value is WriteOutcome.Synced)
        assertEquals("Dual Audio", api.customFormats[1].name)

        api.errorFor = { IOException("offline") }
        val release = repository.saveReleaseProfile(
            ReleaseProfileResource(id = 0, name = "No RARBG", required = "rarbg\n"),
        )
        assertTrue((release as ArrResult.Ok).value is WriteOutcome.Queued)
        assertEquals("api/v3/releaseprofile", queue.pending().single().path)
        assertEquals("No RARBG", repository.cachedReleaseProfiles().last().name)
    }

    @Test
    fun `moveDelayProfile reorders through the server and updates the cache`() = runTest {
        seed()
        api.delayProfiles += DelayProfileResource(id = 2, enableTorrent = true, torrentDelay = 120, order = 1)
        repository.refresh()

        val result = repository.moveDelayProfile(2, afterId = null)
        assertTrue((result as ArrResult.Ok).value is WriteOutcome.Synced)
        assertEquals(listOf(2, 1), repository.cachedDelayProfiles().sortedBy { it.order }.map { it.id })
        assertEquals(0, repository.cachedDelayProfiles().first { it.id == 2 }.order)
    }

    @Test
    fun `moveDelayProfile offline reorders locally and queues the anchor`() = runTest {
        seed()
        api.delayProfiles += DelayProfileResource(id = 2, enableTorrent = true, order = 1)
        repository.refresh()
        api.errorFor = { IOException("offline") }

        val result = repository.moveDelayProfile(2, afterId = null)
        assertTrue((result as ArrResult.Ok).value is WriteOutcome.Queued)
        assertEquals(listOf(2, 1), repository.cachedDelayProfiles().sortedBy { it.order }.map { it.id })
        assertEquals("api/v3/delayprofile/reorder/2", queue.pending().single().path)
    }

    @Test
    fun `reorderLocally moves a row after the anchor and renumbers orders`() {
        val profiles = listOf(
            DelayProfileResource(id = 1, order = 0),
            DelayProfileResource(id = 2, order = 1),
            DelayProfileResource(id = 3, order = 2),
        )
        val moved = RadarrQualityRepository.reorderLocally(profiles, profileId = 3, afterId = 1)!!
        assertEquals(listOf(1, 3, 2), moved.map { it.id })
        assertEquals(listOf(0, 1, 2), moved.map { it.order })
        assertEquals(null, RadarrQualityRepository.reorderLocally(profiles, 99, null))
    }

    @Test
    fun `assignFormatScore updates an attached item and appends new attachments`() = runTest {
        seed()
        repository.refresh()
        val profile = repository.cachedQualityProfiles().first()

        val updated = repository.assignFormatScore(profile, formatId = 7, score = 25)
        assertTrue((updated as ArrResult.Ok).value is WriteOutcome.Synced)
        assertEquals(25, api.qualityProfiles.first().formatItems!!.first { it.format == 7 }.score)

        val appended = repository.assignFormatScore(
            repository.cachedQualityProfiles().first(),
            formatId = 99,
            score = 10,
        )
        assertTrue((appended as ArrResult.Ok).value is WriteOutcome.Synced)
        assertEquals(2, api.qualityProfiles.first().formatItems!!.size)
        assertEquals(10, api.qualityProfiles.first().formatItems!!.first { it.format == 99 }.score)
        // Existing entries keep their relative order, the new one goes last.
        assertEquals(7, api.qualityProfiles.first().formatItems!!.first().format)
    }

    @Test
    fun `queuedCount counts only this feature's paths for this server`() = runTest {
        seed()
        repository.refresh()
        api.errorFor = { IOException("offline") }
        repository.saveQualityProfile(repository.cachedQualityProfiles().first().copy(name = "x"))
        repository.deleteCustomFormat(7)
        queue.enqueue(queue.action("other", path = "api/v3/movie/editor"))

        assertEquals(2, repository.queuedCount().first())
    }

    @Test
    fun `refreshProfileSchema caches the blank profile for offline creates`() = runTest {
        seed()
        val schema = repository.refreshProfileSchema()
        assertTrue(schema is ArrResult.Ok)
        assertEquals("New Profile", (schema as ArrResult.Ok).value.name)

        api.errorFor = { IOException("offline") }
        val cached = repository.refreshProfileSchema()
        assertTrue(cached is ArrResult.Ok)
        assertEquals("New Profile", (cached as ArrResult.Ok).value.name)
    }

    @Test
    fun `auth failures never queue`() = runTest {
        seed()
        repository.refresh()
        api.errorFor = { retrofit2.HttpException(errorResponse(401)) }
        val result = repository.saveQualityProfile(
            repository.cachedQualityProfiles().first().copy(name = "Nope"),
        )
        assertTrue((result as ArrResult.Err).error is ArrError.Auth)
        assertTrue(queue.pending().isEmpty())
        assertEquals("HD", repository.cachedQualityProfiles().first().name)
    }

    private fun errorResponse(code: Int): retrofit2.Response<Any> =
        retrofit2.Response.error(code, "".toResponseBody(null))
}
