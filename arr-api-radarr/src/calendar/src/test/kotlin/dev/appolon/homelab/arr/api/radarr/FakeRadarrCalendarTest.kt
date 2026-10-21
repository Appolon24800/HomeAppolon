package dev.appolon.homelab.arr.api.radarr

import dev.appolon.homelab.arr.api.radarr.model.CalendarReleaseType
import dev.appolon.homelab.arr.api.radarr.model.CustomFormatBulkResource
import dev.appolon.homelab.arr.api.radarr.model.CustomFormatResource
import dev.appolon.homelab.arr.api.radarr.model.CustomFormatSpecificationSchema
import dev.appolon.homelab.arr.api.radarr.model.DelayProfileResource
import dev.appolon.homelab.arr.api.radarr.model.DownloadProtocol
import dev.appolon.homelab.arr.api.radarr.model.LanguageResource
import dev.appolon.homelab.arr.api.radarr.model.MovieResource
import dev.appolon.homelab.arr.api.radarr.model.QualityDefinitionLimitsResource
import dev.appolon.homelab.arr.api.radarr.model.QualityDefinitionResource
import dev.appolon.homelab.arr.api.radarr.model.QualityProfileResource
import dev.appolon.homelab.arr.api.radarr.model.ReleaseProfileResource
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response

class FakeRadarrCalendarTest {
    private fun movie(
        id: Int,
        title: String,
        monitored: Boolean,
        digitalRelease: String,
        tags: Set<Int> = emptySet(),
    ) = MovieResource(
        id = id,
        title = title,
        monitored = monitored,
        digitalRelease = digitalRelease,
        tags = tags,
    )

    @Test fun calendarFiltersByWindowMonitoringAndTags() = runTest {
        val fake = FakeRadarrCalendar()
        fake.movies += movie(1, "Specimen", true, "2026-11-04T00:00:00Z", tags = setOf(1))
        fake.movies += movie(2, "Archive", false, "2026-12-01T00:00:00Z")
        fake.movies += movie(3, "Future", true, "2027-01-01T00:00:00Z")

        assertEquals(listOf(1, 2, 3), fake.calendar(unmonitored = true).map { it.id })
        assertEquals(
            listOf(1, 2),
            fake.calendar(unmonitored = true, end = "2026-12-31T00:00:00Z").map { it.id },
        )
        assertEquals(listOf(1, 3), fake.calendar(unmonitored = false).map { it.id })
        assertEquals(listOf(1), fake.calendar(tags = "1").map { it.id })
        assertEquals(
            listOf(2, 3),
            fake.calendar(unmonitored = true, start = "2026-11-30").map { it.id },
        )
    }

    @Test fun calendarFeedRecordsTheRequest() = runTest {
        val fake = FakeRadarrCalendar()
        val result = fake.calendarFeed(
            pastDays = 3,
            futureDays = 14,
            tags = "2",
            unmonitored = true,
            releaseTypes = listOf(CalendarReleaseType.PHYSICAL_RELEASE),
        )
        assertTrue(result.isSuccessful)
        val recorded = fake.feedRequests.single()
        assertEquals(3, recorded.pastDays)
        assertEquals(14, recorded.futureDays)
        assertEquals("2", recorded.tags)
        assertEquals(true, recorded.unmonitored)
        assertEquals(listOf(CalendarReleaseType.PHYSICAL_RELEASE), recorded.releaseTypes)
    }

    @Test fun languageLookupFindsAndMisses() = runTest {
        val fake = FakeRadarrCalendar()
        fake.languages += LanguageResource(id = 1, name = "English", nameLower = "english")
        assertEquals("English", fake.languages().single().name)
        assertEquals(200, fake.language(1).code())
        assertEquals(404, fake.language(9).code())
    }

    @Test fun qualityDefinitionUpdateParsesStringIds() = runTest {
        val fake = FakeRadarrCalendar()
        fake.qualityDefinitions += QualityDefinitionResource(id = 2, title = "Bluray-1080p")
        val updated = fake.updateQualityDefinition("2", QualityDefinitionResource(title = "HD"))
        assertEquals(202, updated.code())
        assertEquals(2, updated.body()!!.id)
        assertEquals("HD", fake.qualityDefinitions.single().title)
        assertEquals(404, fake.updateQualityDefinition("nan", QualityDefinitionResource()).code())
    }

    @Test fun qualityDefinitionUpdateAllReplacesTheTable() = runTest {
        val fake = FakeRadarrCalendar()
        fake.qualityDefinitions += QualityDefinitionResource(id = 1)
        val replaced = fake.updateQualityDefinitions(
            listOf(QualityDefinitionResource(id = 5), QualityDefinitionResource(id = 6)),
        )
        assertEquals(listOf(5, 6), replaced.map { it.id })
        assertEquals(listOf(5, 6), fake.qualityDefinitions.map { it.id })
        fake.qualityDefinitionLimits = QualityDefinitionLimitsResource(min = 1, max = 40)
        assertEquals(
            QualityDefinitionLimitsResource(min = 1, max = 40),
            fake.qualityDefinitionLimits(),
        )
    }

    @Test fun qualityProfileCrudAndSchema() = runTest {
        val fake = FakeRadarrCalendar()
        fake.qualityDefinitions += QualityDefinitionResource(id = 3, title = "Webdl-1080p")
        fake.customFormats += CustomFormatResource(id = 7, name = "Surround")

        val created = fake.createQualityProfile(QualityProfileResource(name = "Any"))
        assertEquals(1, created.body()!!.id)
        assertEquals("Any", fake.qualityProfile(1).body()!!.name)

        val updated = fake.updateQualityProfile("1", QualityProfileResource(name = "Better"))
        assertEquals(202, updated.code())
        assertEquals("Better", fake.qualityProfiles().single().name)

        assertTrue(fake.removeQualityProfile(1).isSuccessful)
        assertEquals(0, fake.qualityProfiles().size)

        val schema = fake.qualityProfileSchema()
        assertEquals(3, schema.items!!.single().id)
        assertEquals(7, schema.formatItems!!.single().format)
    }

    @Test fun releaseProfileCrud() = runTest {
        val fake = FakeRadarrCalendar()
        val created = fake.createReleaseProfile(
            ReleaseProfileResource(name = "Global", required = "x264"),
        )
        assertEquals(1, created.body()!!.id)
        assertEquals("x264", fake.releaseProfile(1).body()!!.required)
        assertEquals(404, fake.releaseProfile(9).code())
        assertEquals(
            202,
            fake.updateReleaseProfile("1", ReleaseProfileResource(name = "Local")).code(),
        )
        assertEquals("Local", fake.releaseProfiles().single().name)
        assertTrue(fake.removeReleaseProfile(1).isSuccessful)
        assertEquals(0, fake.releaseProfiles().size)
    }

    @Test fun delayProfileReorderRenumbersFromZero() = runTest {
        val fake = FakeRadarrCalendar()
        fake.delayProfiles += DelayProfileResource(id = 1, order = 0)
        fake.delayProfiles += DelayProfileResource(id = 2, order = 1)
        fake.delayProfiles += DelayProfileResource(id = 3, order = 2)

        val reordered = fake.reorderDelayProfile(3, after = 1)
        assertEquals(listOf(1, 3, 2), reordered.map { it.id })
        assertEquals(listOf(0, 1, 2), reordered.map { it.order })

        val topped = fake.reorderDelayProfile(2, after = null)
        assertEquals(listOf(2, 1, 3), topped.map { it.id })
    }

    @Test fun delayProfileCreateKeepsServerOrder() = runTest {
        val fake = FakeRadarrCalendar()
        fake.delayProfiles += DelayProfileResource(id = 4, order = 3)
        val created = fake.createDelayProfile(
            DelayProfileResource(
                enableTorrent = true,
                preferredProtocol = DownloadProtocol.TORRENT,
            ),
        )
        assertEquals(5, created.body()!!.id)
        assertEquals(3, created.body()!!.order)
        assertEquals(
            202,
            fake.updateDelayProfile("5", DelayProfileResource(usenetDelay = 9)).code(),
        )
        val updated = fake.delayProfiles.single { it.id == 5 }
        assertEquals(9, updated.usenetDelay)
        assertEquals(3, updated.order)
        assertTrue(fake.removeDelayProfile(5).isSuccessful)
        assertEquals(listOf(4), fake.delayProfiles.map { it.id })
    }

    @Test fun customFormatBulkEditAndDelete() = runTest {
        val fake = FakeRadarrCalendar()
        fake.customFormats += CustomFormatResource(id = 1, name = "Surround")
        fake.customFormats += CustomFormatResource(id = 2, name = "3D")

        val updated = fake.updateCustomFormats(
            CustomFormatBulkResource(ids = setOf(1, 2), includeCustomFormatWhenRenaming = true),
        )
        assertEquals(setOf(1, 2), updated.body()!!.map { it.id }.toSet())
        assertTrue(fake.customFormats.all { it.includeCustomFormatWhenRenaming == true })

        fake.customFormatPresets = listOf(
            CustomFormatSpecificationSchema(name = "TrueHD Atmos"),
        )
        assertEquals("TrueHD Atmos", fake.customFormatSchema().single().name)

        assertEquals(
            0,
            fake.removeCustomFormats(CustomFormatBulkResource(ids = setOf(1))).keys.size,
        )
        assertEquals(listOf(2), fake.customFormats.map { it.id })
        assertTrue(fake.removeCustomFormat(2).isSuccessful)
        assertEquals(0, fake.customFormats.size)
    }

    @Test fun errorInjectionRunsBeforeMutation() = runTest {
        var failNextCreate = true
        val fake = FakeRadarrCalendar(
            errorFor = { operation ->
                if (operation == "createQualityProfile" && failNextCreate) {
                    failNextCreate = false
                    HttpException(Response.error<Any>(500, "{}".toResponseBody()))
                } else {
                    null
                }
            },
        )
        runCatching { fake.createQualityProfile(QualityProfileResource(name = "Boom")) }
        assertEquals(0, fake.qualityProfiles.size)
        assertEquals(1, fake.createQualityProfile(QualityProfileResource(name = "Ok")).body()!!.id)
    }

    @Test fun injectedLatencyUsesTheInjectedSleep() = runTest {
        val fake = FakeRadarrCalendar(latencyMs = 250, sleep = { delay(it) })
        fake.languages()
        assertEquals(250, testScheduler.currentTime)
    }

    @Test fun negativeLatencyIsRejected() = runTest {
        val fake = FakeRadarrCalendar(latencyMs = -1)
        val outcome = runCatching { fake.languages() }
        assertTrue(outcome.isFailure)
    }
}
