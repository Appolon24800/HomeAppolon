package dev.appolon.homelab.arr.feature.lidarr.settings.quality.data

import dev.appolon.homelab.arr.api.lidarr.FakeLidarrCalendar
import dev.appolon.homelab.arr.api.lidarr.model.CustomFormatResource
import dev.appolon.homelab.arr.api.lidarr.model.DelayProfileResource
import dev.appolon.homelab.arr.api.lidarr.model.LanguageResource
import dev.appolon.homelab.arr.api.lidarr.model.MetadataProfileResource
import dev.appolon.homelab.arr.api.lidarr.model.MetadataProviderConfigResource
import dev.appolon.homelab.arr.api.lidarr.model.PrimaryAlbumType
import dev.appolon.homelab.arr.api.lidarr.model.ProfileFormatItemResource
import dev.appolon.homelab.arr.api.lidarr.model.ProfilePrimaryAlbumTypeItemResource
import dev.appolon.homelab.arr.api.lidarr.model.ProfileReleaseStatusItemResource
import dev.appolon.homelab.arr.api.lidarr.model.ProfileSecondaryAlbumTypeItemResource
import dev.appolon.homelab.arr.api.lidarr.model.Quality
import dev.appolon.homelab.arr.api.lidarr.model.QualityDefinitionResource
import dev.appolon.homelab.arr.api.lidarr.model.QualityProfileResource
import dev.appolon.homelab.arr.api.lidarr.model.ReleaseProfileResource
import dev.appolon.homelab.arr.api.lidarr.model.ReleaseStatus
import dev.appolon.homelab.arr.api.lidarr.model.SecondaryAlbumType
import dev.appolon.homelab.arr.api.lidarr.model.WriteAudioTagsType
import dev.appolon.homelab.arr.core.ArrResult
import dev.appolon.homelab.arr.core.ServerId
import dev.appolon.homelab.arr.feature.lidarr.library.data.WriteOutcome
import dev.appolon.homelab.arr.feature.lidarr.settings.quality.FakeQualityActionQueue
import dev.appolon.homelab.arr.feature.lidarr.settings.quality.FakeQualityEntityStore
import java.io.IOException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class LidarrQualityRepositoryTest {

    private val serverId = ServerId("s1")
    private lateinit var store: FakeQualityEntityStore
    private lateinit var queue: FakeQualityActionQueue
    private lateinit var api: FakeLidarrCalendar
    private lateinit var repository: LidarrQualityRepository
    private var now: Long = 1_000_000L

    @Before
    fun setUp() {
        store = FakeQualityEntityStore()
        queue = FakeQualityActionQueue()
        api = FakeLidarrCalendar()
        repository = repository()
    }

    private fun repository() = LidarrQualityRepository(
        serverId = serverId,
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
    )

    private fun seed() {
        api.qualityDefinitions[1] = QualityDefinitionResource(
            id = 1,
            quality = Quality(id = 1, name = "MP3-320"),
            title = "MP3-320",
            weight = 10,
            minSize = 0.0,
            maxSize = 100.0,
        )
        api.qualityProfiles[1] = QualityProfileResource(
            id = 1,
            name = "Standard",
            formatItems = listOf(ProfileFormatItemResource(id = 1, format = 7, name = "FLAC", score = 5)),
        )
        api.metadataProfiles[2] = MetadataProfileResource(
            id = 2,
            name = "Standard",
            primaryAlbumTypes = listOf(
                ProfilePrimaryAlbumTypeItemResource(
                    id = 1,
                    albumType = PrimaryAlbumType(id = 1, name = "Album"),
                    allowed = true,
                ),
            ),
            secondaryAlbumTypes = listOf(
                ProfileSecondaryAlbumTypeItemResource(
                    id = 2,
                    albumType = SecondaryAlbumType(id = 1, name = "Studio"),
                    allowed = false,
                ),
            ),
            releaseStatuses = listOf(
                ProfileReleaseStatusItemResource(
                    id = 3,
                    releaseStatus = ReleaseStatus(id = 1, name = "Official"),
                    allowed = true,
                ),
            ),
        )
        api.metadataProviderConfig = MetadataProviderConfigResource(
            id = 1,
            metadataSource = "lidarr",
            writeAudioTags = WriteAudioTagsType.NEW_FILES,
        )
        api.customFormats[7] = CustomFormatResource(id = 7, name = "FLAC")
        api.delayProfiles[1] = DelayProfileResource(id = 1, enableUsenet = true, usenetDelay = 60, order = 0)
        api.releaseProfiles[3] = ReleaseProfileResource(
            id = 3,
            enabled = true,
            required = listOf("flac"),
            tags = listOf(4),
        )
        api.languages[1] = LanguageResource(id = 1, name = "English")
    }

    @Test
    fun `refresh caches every table and sorts languages by id`() = runTest {
        seed()
        api.languages[0] = LanguageResource(id = 0, name = "Unknown")
        val result = repository.refresh()

        assertTrue(result is ArrResult.Ok)
        assertEquals(1, repository.cachedQualityDefinitions().size)
        assertEquals(1, repository.cachedQualityProfiles().size)
        assertEquals(1, repository.cachedMetadataProfiles().size)
        assertEquals(1, repository.cachedCustomFormats().size)
        assertEquals(1, repository.cachedDelayProfiles().size)
        assertEquals(1, repository.cachedReleaseProfiles().size)
        assertEquals(listOf(0, 1), repository.cachedLanguages().map { it.id })
        assertEquals(WriteAudioTagsType.NEW_FILES, repository.cachedMetadataProviderConfig()!!.writeAudioTags)
    }

    @Test
    fun `refresh survives a missing metadata provider config`() = runTest {
        seed()
        api.error = { op -> if (op == "getMetadataProviderConfig") IOException("gone") else null }
        val result = repository.refresh()

        assertTrue(result is ArrResult.Ok)
        assertEquals(1, repository.cachedQualityProfiles().size)
        assertEquals(null, repository.cachedMetadataProviderConfig())
    }

    @Test
    fun `refreshIfStale honors the TTL`() = runTest {
        seed()
        repository.refresh()
        now += LidarrQualityRepository.QUALITY_TTL_MILLIS + 1
        assertTrue(repository.isStale())
        now -= 1
        assertTrue(!repository.isStale())
    }

    @Test
    fun `saveQualityDefinitions bulk updates and rewrites the cache`() = runTest {
        seed()
        repository.refresh()
        val edited = repository.cachedQualityDefinitions()
            .first()
            .copy(title = "MP3-V0", weight = 20)

        val result = repository.saveQualityDefinitions(listOf(edited))

        assertTrue((result as ArrResult.Ok).value is WriteOutcome.Synced)
        assertEquals(1, api.updatedQualityDefinitions.size)
        assertEquals(listOf("MP3-V0"), repository.cachedQualityDefinitions().map { it.title })
    }

    @Test
    fun `offline quality definition save queues and keeps the optimistic table`() = runTest {
        seed()
        repository.refresh()
        val edited = repository.cachedQualityDefinitions().first().copy(weight = 20)
        api.error = { IOException("offline") }

        val result = repository.saveQualityDefinitions(listOf(edited))

        assertTrue((result as ArrResult.Ok).value is WriteOutcome.Queued)
        assertEquals(1, queue.pending().size)
        assertEquals(20, repository.cachedQualityDefinitions().first().weight)
    }

    @Test
    fun `create quality profile offline queues with a temporary id`() = runTest {
        seed()
        repository.refresh()
        api.error = { IOException("offline") }

        val result = repository.saveQualityProfile(QualityProfileResource(name = "Offline"))

        val outcome = (result as ArrResult.Ok).value
        assertTrue(outcome is WriteOutcome.Queued)
        val optimistic = repository.cachedQualityProfiles().first { it.name == "Offline" }
        assertTrue(optimistic.id < 0)
        assertEquals("api/v1/qualityprofile", queue.pending().first().path)
    }

    @Test
    fun `update quality profile writes through and refreshes the cached row`() = runTest {
        seed()
        repository.refresh()

        val result = repository.saveQualityProfile(
            QualityProfileResource(id = 1, name = "Renamed", upgradeAllowed = true),
        )

        assertTrue((result as ArrResult.Ok).value is WriteOutcome.Synced)
        assertEquals("Renamed", api.qualityProfiles[1]!!.name)
        assertEquals("Renamed", repository.cachedQualityProfiles().first { it.id == 1 }.name)
    }

    @Test
    fun `save metadata profile creates and caches the server row`() = runTest {
        seed()
        repository.refresh()

        val result = repository.saveMetadataProfile(
            MetadataProfileResource(name = "Albums only"),
        )

        assertTrue((result as ArrResult.Ok).value is WriteOutcome.Synced)
        assertTrue(api.metadataProfiles.values.any { it.name == "Albums only" })
        assertEquals(2, repository.cachedMetadataProfiles().size)
    }

    @Test
    fun `offline metadata profile create queues optimistically`() = runTest {
        seed()
        repository.refresh()
        api.error = { IOException("offline") }

        val result = repository.saveMetadataProfile(MetadataProfileResource(name = "Queued profile"))

        assertTrue((result as ArrResult.Ok).value is WriteOutcome.Queued)
        assertEquals("api/v1/metadataprofile", queue.pending().first().path)
        assertTrue(repository.cachedMetadataProfiles().any { it.name == "Queued profile" && it.id < 0 })
    }

    @Test
    fun `save metadata provider config PUTs with the server id`() = runTest {
        seed()
        repository.refresh()

        val result = repository.saveMetadataProviderConfig(
            MetadataProviderConfigResource(
                id = 0,
                metadataSource = "lidarr",
                writeAudioTags = WriteAudioTagsType.ALL_FILES,
                scrubAudioTags = true,
            ),
        )

        assertTrue((result as ArrResult.Ok).value is WriteOutcome.Synced)
        assertEquals(WriteAudioTagsType.ALL_FILES, api.metadataProviderConfig.writeAudioTags)
        assertEquals(1, api.metadataProviderConfig.id)
        assertEquals(
            WriteAudioTagsType.ALL_FILES,
            repository.cachedMetadataProviderConfig()!!.writeAudioTags,
        )
    }

    @Test
    fun `offline metadata provider save queues the PUT`() = runTest {
        seed()
        repository.refresh()
        api.error = { IOException("offline") }

        val result = repository.saveMetadataProviderConfig(
            MetadataProviderConfigResource(id = 1, metadataSource = "lidarr"),
        )

        assertTrue((result as ArrResult.Ok).value is WriteOutcome.Queued)
        assertEquals("api/v1/config/metadataprovider/1", queue.pending().first().path)
    }

    @Test
    fun `delete custom format removes the cached row or queues offline`() = runTest {
        seed()
        repository.refresh()

        val synced = repository.deleteCustomFormat(7)
        assertTrue((synced as ArrResult.Ok).value is WriteOutcome.Synced)
        assertTrue(repository.cachedCustomFormats().isEmpty())

        api.customFormats[7] = CustomFormatResource(id = 7, name = "FLAC")
        repository.refresh()
        api.error = { IOException("offline") }
        val queued = repository.deleteCustomFormat(7)
        assertTrue((queued as ArrResult.Ok).value is WriteOutcome.Queued)
        assertEquals("api/v1/customformat/7", queue.pending().first().path)
    }

    @Test
    fun `moveDelayProfile reorders online and rewrites orders`() = runTest {
        seed()
        api.delayProfiles[2] = DelayProfileResource(id = 2, order = 1)
        repository.refresh()

        val result = repository.moveDelayProfile(2, afterId = null)

        assertTrue((result as ArrResult.Ok).value is WriteOutcome.Synced)
        // The fake reindexes 1-based after moving profile 2 to the top.
        assertEquals(1, api.delayProfiles[2]!!.order)
        assertEquals(2, api.delayProfiles[1]!!.order)
        assertEquals(listOf(2, 1), repository.cachedDelayProfiles().sortedBy { it.order }.map { it.id })
    }

    @Test
    fun `offline delay reorder queues and reorders the cache`() = runTest {
        seed()
        api.delayProfiles[2] = DelayProfileResource(id = 2, order = 1)
        repository.refresh()
        api.error = { op -> if (op == "reorderDelayProfile") IOException("offline") else null }

        val result = repository.moveDelayProfile(2, afterId = null)

        assertTrue((result as ArrResult.Ok).value is WriteOutcome.Queued)
        assertEquals(listOf(2, 1), repository.cachedDelayProfiles().sortedBy { it.order }.map { it.id })
        assertEquals(
            "api/v1/delayprofile/reorder/2",
            queue.pending().first().path,
        )
    }

    @Test
    fun `save release profile preserves the tags the draft carries`() = runTest {
        seed()
        repository.refresh()

        val result = repository.saveReleaseProfile(
            ReleaseProfileResource(
                id = 3,
                enabled = false,
                required = listOf("flac"),
                ignored = listOf("transcode"),
                tags = listOf(4),
            ),
        )

        assertTrue((result as ArrResult.Ok).value is WriteOutcome.Synced)
        assertEquals(listOf(4), api.releaseProfiles[3]!!.tags)
        assertEquals(listOf("transcode"), api.releaseProfiles[3]!!.ignored)
    }

    @Test
    fun `assignFormatScore attaches a missing format and saves the profile`() = runTest {
        seed()
        repository.refresh()
        val profile = repository.cachedQualityProfiles().first { it.id == 1 }

        val result = repository.assignFormatScore(profile, formatId = 9, score = 15)

        assertTrue((result as ArrResult.Ok).value is WriteOutcome.Synced)
        val items = api.qualityProfiles[1]!!.formatItems.orEmpty()
        assertTrue(items.any { it.format == 9 && it.score == 15 })
    }

    @Test
    fun `queuedCount counts only this group's paths`() = runTest {
        queue.enqueue(queue.action("a", path = "api/v1/qualityprofile/1"))
        queue.enqueue(queue.action("b", path = "api/v1/customformat/2"))
        queue.enqueue(queue.action("c", path = "api/v1/artist/1"))
        queue.enqueue(queue.action("d", serverId = ServerId("other"), path = "api/v1/customformat/3"))

        assertEquals(2, repository.queuedCount().first())
    }
}
