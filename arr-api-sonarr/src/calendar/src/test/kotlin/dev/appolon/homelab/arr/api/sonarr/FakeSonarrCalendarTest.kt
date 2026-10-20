package dev.appolon.homelab.arr.api.sonarr

import dev.appolon.homelab.arr.api.sonarr.model.CustomFormatBulkResource
import dev.appolon.homelab.arr.api.sonarr.model.CustomFormatResource
import dev.appolon.homelab.arr.api.sonarr.model.DelayProfileResource
import dev.appolon.homelab.arr.api.sonarr.model.EpisodeResource
import dev.appolon.homelab.arr.api.sonarr.model.LanguageResource
import dev.appolon.homelab.arr.api.sonarr.model.QualityDefinitionResource
import dev.appolon.homelab.arr.api.sonarr.model.QualityProfileResource
import dev.appolon.homelab.arr.api.sonarr.model.ReleaseProfileResource
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response

class FakeSonarrCalendarTest {
    private fun episode(id: Int, airDate: String, monitored: Boolean = true) =
        EpisodeResource(
            id = id, seriesId = 10, seasonNumber = 1, episodeNumber = id,
            title = "Episode $id", airDate = airDate, monitored = monitored
        )

    @Test
    fun calendarListFiltersWindowAndMonitoring() = runTest {
        val fake = FakeSonarrCalendar()
        fake.calendarStore[1] = episode(1, "2026-01-10")
        fake.calendarStore[2] = episode(2, "2026-01-18")
        fake.calendarStore[3] = episode(3, "2026-02-01", monitored = false)

        val window = fake.calendar.list(
            start = "2026-01-01", end = "2026-01-31",
            unmonitored = true, includeSeries = null, includeEpisodeFile = null,
            includeEpisodeImages = null, tags = null
        )
        assertEquals(listOf(1, 2), window.map { it.id })

        val monitoredOnly = fake.calendar.list(
            start = "2026-01-01", end = "2026-02-28", unmonitored = false,
            includeSeries = null, includeEpisodeFile = null,
            includeEpisodeImages = null, tags = null
        )
        assertEquals(listOf(1, 2), monitoredOnly.map { it.id })
    }

    @Test
    fun calendarFeedEmitsIcsForWindow() = runTest {
        val fake = FakeSonarrCalendar()
        fake.calendarStore[1] = episode(1, "2026-01-14")
        fake.calendarStore[2] = episode(2, "2026-01-20")
        fake.calendarStore[3] = episode(3, "2026-01-01")

        val body = fake.calendarFeed.list(
            pastDays = 7, futureDays = 7, tags = null,
            unmonitored = false, premieresOnly = null, asAllDay = null
        )
        val text = body.body()!!.string()
        assertTrue(text.startsWith("BEGIN:VCALENDAR"))
        assertTrue(text.contains("UID:sonarr-1"))
        assertTrue(text.contains("UID:sonarr-2"))
        assertFalse(text.contains("UID:sonarr-3"))
    }

    @Test
    fun reorderMovesProfileAfterAnchor() = runTest {
        val fake = FakeSonarrCalendar()
        fake.delayProfileStore[1] = DelayProfileResource(id = 1, order = 1)
        fake.delayProfileStore[2] = DelayProfileResource(id = 2, order = 2)
        fake.delayProfileStore[3] = DelayProfileResource(id = 3, order = 3)

        val reordered = fake.delayProfile.reorder(id = 3, after = 1)
        assertEquals(listOf(1, 3, 2), reordered.map { it.id })
        assertEquals(listOf(1, 3, 2), fake.delayProfile.list().map { it.id })
    }

    @Test
    fun getMissingReturnsNotFoundResponse() = runTest {
        val fake = FakeSonarrCalendar()
        fake.languageStore[1] = LanguageResource(id = 1, name = "English")

        assertTrue(fake.language.get(1).isSuccessful)
        val missing = fake.language.get(9)
        assertFalse(missing.isSuccessful)
        assertEquals(404, missing.code())
    }

    @Test
    fun updateUnknownIdThrows404() = runTest {
        val fake = FakeSonarrCalendar()
        var thrown: HttpException? = null
        try {
            fake.releaseProfile.update("404", ReleaseProfileResource())
        } catch (error: HttpException) {
            thrown = error
        }
        assertEquals(404, thrown?.code())
    }

    @Test
    fun latencyRunsBeforeError() = runTest {
        val calls = mutableListOf<String>()
        val fake = FakeSonarrCalendar(
            latency = { calls += it },
            error = { if (it == "QualityProfile.list") IllegalStateException("boom") else null }
        )
        var thrown: Throwable? = null
        try {
            fake.qualityProfile.list()
        } catch (error: Throwable) {
            thrown = error
        }
        assertEquals(IllegalStateException::class.java, thrown!!.javaClass)
        assertEquals(listOf("QualityProfile.list"), calls)
    }

    @Test
    fun customFormatBulkUpdateAppliesFlag() = runTest {
        val fake = FakeSonarrCalendar()
        fake.customFormatStore[1] = CustomFormatResource(id = 1, name = "AMZN")
        fake.customFormatStore[2] = CustomFormatResource(id = 2, name = "Surround")

        val updated = fake.customFormat.updateBulk(
            CustomFormatBulkResource(
                ids = listOf(1, 2), includeCustomFormatWhenRenaming = true
            )
        )
        assertEquals(1, updated.id)
        assertTrue(fake.customFormatStore.values.all {
            it.includeCustomFormatWhenRenaming == true
        })

        fake.customFormat.deleteBulk(CustomFormatBulkResource(ids = listOf(1, 2)))
        assertTrue(fake.customFormatStore.isEmpty())
    }

    @Test
    fun createAssignsNextId() = runTest {
        val fake = FakeSonarrCalendar()
        fake.qualityDefinitionStore[1] =
            QualityDefinitionResource(id = 1, weight = 1)

        val created = fake.qualityProfile.create(
            QualityProfileResource(name = "New")
        )
        assertEquals(1, created.id)
        assertEquals("New", fake.qualityProfileStore[1]?.name)
    }

    @Test
    fun errorResponseBodyShapeMatchesRetrofit() = runTest {
        val fake = FakeSonarrCalendar()
        val response: Response<EpisodeResource> = fake.calendar.get(7)
        val body = response.errorBody()
        assertEquals("Missing episode", body?.string())
    }
}
