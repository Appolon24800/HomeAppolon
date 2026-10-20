package dev.appolon.homelab.arr.api.lidarr

import dev.appolon.homelab.arr.api.lidarr.model.AlbumResource
import dev.appolon.homelab.arr.api.lidarr.model.ArtistResource
import dev.appolon.homelab.arr.api.lidarr.model.CustomFormatBulkResource
import dev.appolon.homelab.arr.api.lidarr.model.CustomFormatResource
import dev.appolon.homelab.arr.api.lidarr.model.DelayProfileResource
import dev.appolon.homelab.arr.api.lidarr.model.DownloadProtocol
import dev.appolon.homelab.arr.api.lidarr.model.LanguageResource
import dev.appolon.homelab.arr.api.lidarr.model.MetadataProviderConfigResource
import dev.appolon.homelab.arr.api.lidarr.model.QualityProfileResource
import dev.appolon.homelab.arr.api.lidarr.model.WriteAudioTagsType
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.HttpException

class FakeLidarrCalendarTest {
    @Test
    fun calendarFiltersByWindowMonitoringAndArtistInclusion() = runTest {
        val fake = FakeLidarrCalendar()
        fake.calendar[1] = AlbumResource(
            id = 1, title = "Future Days", artistId = 2, monitored = true,
            releaseDate = "2026-11-06T00:00:00Z", artist = ArtistResource(id = 2),
        )
        fake.calendar[2] = AlbumResource(
            id = 2, title = "Music for Airports", artistId = 3, monitored = false,
            releaseDate = "1978-03-01T00:00:00Z", artist = ArtistResource(id = 3),
        )
        val window = fake.getCalendar(
            start = "2026-01-01T00:00:00Z", end = "2026-12-31T00:00:00Z",
            includeArtist = true,
        )
        assertEquals(listOf(1), window.map { it.id })
        assertEquals(2, window.single().artist?.id)
        assertEquals(
            listOf(2, 1),
            fake.getCalendar(unmonitored = true).map { it.id },
        )
    }

    @Test
    fun calendarFeedServesSeededText() = runTest {
        val fake = FakeLidarrCalendar()
        fake.calendarFeed = "BEGIN:VCALENDAR END:VCALENDAR"
        assertEquals("BEGIN:VCALENDAR END:VCALENDAR", fake.getCalendarFeed().string())
    }

    @Test
    fun qualityProfileCrudAssignsIdsAndFillsFormatItems() = runTest {
        val fake = FakeLidarrCalendar()
        fake.customFormats[7] = CustomFormatResource(id = 7, name = "Lossless")
        val created = fake.createQualityProfile(QualityProfileResource(name = "Standard"))
        assertEquals(1, created.id)
        assertEquals(listOf(7), created.formatItems?.map { it.format })
        assertEquals("Standard", fake.getQualityProfileById(1).name)
        fake.updateQualityProfile("1", QualityProfileResource(name = "Renamed"))
        assertEquals("Renamed", fake.getQualityProfiles().single().name)
        fake.deleteQualityProfile(1)
        assertTrue(fake.getQualityProfiles().isEmpty())
    }

    @Test
    fun delayProfileReorderMovesToTopAndRenumbers() = runTest {
        val fake = FakeLidarrCalendar()
        fake.createDelayProfile(DelayProfileResource(preferredProtocol = DownloadProtocol.USENET))
        fake.createDelayProfile(DelayProfileResource(preferredProtocol = DownloadProtocol.TORRENT))
        fake.createDelayProfile(DelayProfileResource())
        fake.reorderDelayProfile(3, afterId = null)
        assertEquals(listOf(3, 1, 2), fake.getDelayProfiles().map { it.id })
        assertEquals(listOf(1, 2, 3), fake.getDelayProfiles().map { it.order })
        assertEquals(listOf(3 to null), fake.reorderedDelayProfiles)
    }

    @Test
    fun customFormatBulkDeleteRemovesAndRecords() = runTest {
        val fake = FakeLidarrCalendar()
        fake.customFormats[1] = CustomFormatResource(id = 1, name = "Lossless")
        fake.customFormats[2] = CustomFormatResource(id = 2, name = "24bit")
        fake.deleteCustomFormatBulk(CustomFormatBulkResource(ids = listOf(1, 2)))
        assertTrue(fake.getCustomFormats().isEmpty())
        assertEquals(listOf(listOf(1, 2)), fake.deletedCustomFormatBulk.map { it.ids })
    }

    @Test
    fun schemasAndConfigPassThroughSeededValues() = runTest {
        val fake = FakeLidarrCalendar()
        fake.customFormatSchema = JsonArray(emptyList())
        assertEquals(JsonArray(emptyList()), fake.getCustomFormatSchema())
        fake.metadataProviderConfig =
            MetadataProviderConfigResource(id = 1, writeAudioTags = WriteAudioTagsType.SYNC)
        assertEquals(WriteAudioTagsType.SYNC, fake.getMetadataProviderConfig().writeAudioTags)
        assertEquals(WriteAudioTagsType.SYNC, fake.getMetadataProviderConfigById(1).writeAudioTags)
        fake.languages[1] = LanguageResource(id = 1, name = "English", nameLower = "english")
        assertEquals("English", fake.getLanguageById(1).name)
        assertEquals(1, fake.getLanguages().size)
    }

    @Test
    fun errorInjectionRunsBeforeEveryOperation() = runTest {
        val fake = FakeLidarrCalendar(error = { op -> IllegalStateException(op) })
        try {
            fake.getQualityDefinitions()
            throw AssertionError("Expected injected error")
        } catch (error: IllegalStateException) {
            assertEquals("getQualityDefinitions", error.message)
        }
    }

    @Test
    fun unknownIdsSurfaceAsHttp404() = runTest {
        val fake = FakeLidarrCalendar()
        try {
            fake.getCalendarById(99)
            throw AssertionError("Expected HttpException")
        } catch (error: HttpException) {
            assertEquals(404, error.code())
        }
    }
}
