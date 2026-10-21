package dev.appolon.homelab.arr.api.radarr

import dev.appolon.homelab.arr.api.radarr.model.CalendarReleaseType
import dev.appolon.homelab.arr.api.radarr.model.CustomFormatBulkResource
import dev.appolon.homelab.arr.api.radarr.model.CustomFormatResource
import dev.appolon.homelab.arr.api.radarr.model.DelayProfileResource
import dev.appolon.homelab.arr.api.radarr.model.QualityDefinitionResource
import dev.appolon.homelab.arr.api.radarr.model.QualityProfileResource
import dev.appolon.homelab.arr.api.radarr.model.ReleaseProfileResource
import dev.appolon.homelab.arr.net.ArrJson
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

/**
 * Each spec operation in the calendar and quality groups has its own fixture
 * and the base-path prefix is verified on every request, so a dropped prefix
 * fails loudly instead of 404ing against the host root.
 */
class RadarrCalendarContractTest {
    private fun retrofit(server: MockWebServer): Retrofit = Retrofit.Builder()
        .baseUrl(server.url("/radarr/"))
        .addConverterFactory(ArrJson.asConverterFactory("application/json".toMediaType()))
        .build()

    private fun fixture(tag: String, name: String): String =
        requireNotNull(javaClass.classLoader?.getResourceAsStream(
            "fixtures/radarr/$tag/$name.json"
        )).bufferedReader().use { it.readText() }

    private suspend fun <T> exchange(
        response: MockResponse,
        call: suspend (Retrofit) -> T,
    ): Pair<T, RecordedRequest> {
        val server = MockWebServer()
        try {
            server.enqueue(response)
            val result = call(retrofit(server))
            return result to server.takeRequest()
        } finally {
            server.shutdown()
        }
    }

    private fun ok(tag: String, name: String): MockResponse =
        MockResponse().setResponseCode(200).setBody(fixture(tag, name))

    private fun created(tag: String, name: String): MockResponse =
        MockResponse().setResponseCode(201).setBody(fixture(tag, name))

    private fun accepted(tag: String, name: String): MockResponse =
        MockResponse().setResponseCode(202).setBody(fixture(tag, name))

    private fun assertPathAndQuery(
        request: RecordedRequest,
        method: String,
        path: String,
        vararg query: Pair<String, String>,
    ) {
        assertEquals(method, request.method)
        assertEquals(path, request.requestUrl!!.encodedPath)
        query.forEach { (name, value) ->
            assertEquals(listOf(value), request.requestUrl!!.queryParameterValues(name))
        }
    }

    @Test fun calendarHappyPath() = runTest {
        val (result, request) = exchange(ok("calendar", "calendar")) {
            RadarrCalendarApis.calendar(it).calendar(
                start = "2026-10-01T00:00:00Z",
                end = "2026-11-30T23:59:59Z",
                unmonitored = true,
                tags = "1",
            )
        }
        assertEquals(2, result.size)
        assertEquals("Specimen Movie", result[0].title)
        assertPathAndQuery(
            request, "GET", "/radarr/api/v3/calendar",
            "start" to "2026-10-01T00:00:00Z",
            "end" to "2026-11-30T23:59:59Z",
            "unmonitored" to "true",
            "tags" to "1",
        )
    }

    @Test fun calendarErrorPath() = runTest {
        try {
            exchange(MockResponse().setResponseCode(401)) {
                RadarrCalendarApis.calendar(it).calendar()
            }
            fail("Expected HttpException")
        } catch (e: HttpException) {
            assertEquals(401, e.code())
        }
    }

    @Test fun calendarFeedHappyPath() = runTest {
        val (result, request) = exchange(MockResponse().setResponseCode(204)) {
            RadarrCalendarApis.calendarFeed(it).calendarFeed(
                pastDays = 7,
                futureDays = 28,
                tags = "",
                unmonitored = false,
                releaseTypes = listOf(
                    CalendarReleaseType.CINEMA_RELEASE,
                    CalendarReleaseType.DIGITAL_RELEASE,
                ),
            )
        }
        assertTrue(result.isSuccessful)
        assertEquals(204, result.code())
        assertPathAndQuery(
            request, "GET", "/radarr/feed/v3/calendar/radarr.ics",
            "pastDays" to "7",
            "futureDays" to "28",
            "unmonitored" to "false",
        )
        assertEquals(
            listOf("cinemaRelease", "digitalRelease"),
            request.requestUrl!!.queryParameterValues("releaseTypes"),
        )
    }

    @Test fun calendarFeedErrorPath() = runTest {
        val (result, _) = exchange(MockResponse().setResponseCode(401)) {
            RadarrCalendarApis.calendarFeed(it).calendarFeed()
        }
        assertEquals(401, result.code())
    }

    @Test fun languagesHappyPath() = runTest {
        val (result, request) = exchange(ok("language", "languages")) {
            RadarrCalendarApis.language(it).languages()
        }
        assertEquals(2, result.size)
        assertEquals("English", result[0].name)
        assertEquals("english", result[0].nameLower)
        assertPathAndQuery(request, "GET", "/radarr/api/v3/language")
    }

    @Test fun languageHappyPath() = runTest {
        val (result, request) = exchange(ok("language", "language")) {
            RadarrCalendarApis.language(it).language(1)
        }
        assertEquals(200, result.code())
        assertEquals("English", result.body()!!.name)
        assertPathAndQuery(request, "GET", "/radarr/api/v3/language/1")
    }

    @Test fun languageErrorPath() = runTest {
        val (result, _) = exchange(MockResponse().setResponseCode(404)) {
            RadarrCalendarApis.language(it).language(99)
        }
        assertEquals(404, result.code())
    }

    @Test fun qualityDefinitionsHappyPath() = runTest {
        val (result, request) = exchange(ok("qualitydefinition", "qualitydefinitions")) {
            RadarrCalendarApis.qualityDefinition(it).qualityDefinitions()
        }
        assertEquals(2, result.size)
        assertEquals(39, result[1].weight)
        assertEquals(200.0, result[1].maxSize!!, 0.0)
        assertPathAndQuery(request, "GET", "/radarr/api/v3/qualitydefinition")
    }

    @Test fun qualityDefinitionHappyPath() = runTest {
        val (result, request) = exchange(ok("qualitydefinition", "qualitydefinition")) {
            RadarrCalendarApis.qualityDefinition(it).qualityDefinition(2)
        }
        assertEquals(200, result.code())
        assertEquals("Bluray-1080p", result.body()!!.title)
        assertPathAndQuery(request, "GET", "/radarr/api/v3/qualitydefinition/2")
    }

    @Test fun updateQualityDefinitionHappyPath() = runTest {
        val body = ArrJson.decodeFromString<QualityDefinitionResource>(
            fixture("qualitydefinition", "qualitydefinition"))
        val (result, request) = exchange(accepted("qualitydefinition", "qualitydefinition")) {
            RadarrCalendarApis.qualityDefinition(it).updateQualityDefinition("2", body)
        }
        assertEquals(202, result.code())
        assertEquals(2, result.body()!!.id)
        assertEquals(
            body,
            ArrJson.decodeFromString<QualityDefinitionResource>(request.body.readUtf8()),
        )
        assertPathAndQuery(request, "PUT", "/radarr/api/v3/qualitydefinition/2")
    }

    @Test fun updateQualityDefinitionsHappyPath() = runTest {
        val body = ArrJson.decodeFromString<List<QualityDefinitionResource>>(
            fixture("qualitydefinition", "updateQualityDefinitions"))
        val (result, request) = exchange(
            ok("qualitydefinition", "updateQualityDefinitions"),
        ) {
            RadarrCalendarApis.qualityDefinition(it).updateQualityDefinitions(body)
        }
        assertEquals(2, result.size)
        assertEquals(40, result[1].weight)
        assertEquals(
            body,
            ArrJson.decodeFromString<List<QualityDefinitionResource>>(request.body.readUtf8()),
        )
        assertPathAndQuery(request, "PUT", "/radarr/api/v3/qualitydefinition/update")
    }

    @Test fun qualityDefinitionLimitsHappyPath() = runTest {
        val (result, request) = exchange(ok("qualitydefinition", "limits")) {
            RadarrCalendarApis.qualityDefinition(it).qualityDefinitionLimits()
        }
        assertEquals(0, result.min)
        assertEquals(1000, result.max)
        assertPathAndQuery(request, "GET", "/radarr/api/v3/qualitydefinition/limits")
    }

    @Test fun qualityDefinitionErrorPath() = runTest {
        val (result, _) = exchange(MockResponse().setResponseCode(404)) {
            RadarrCalendarApis.qualityDefinition(it).qualityDefinition(99)
        }
        assertEquals(404, result.code())
    }

    @Test fun qualityProfilesHappyPath() = runTest {
        val (result, request) = exchange(ok("qualityprofile", "qualityprofiles")) {
            RadarrCalendarApis.qualityProfile(it).qualityProfiles()
        }
        assertEquals(1, result.size)
        assertEquals("Any", result[0].name)
        assertEquals(1, result[0].formatItems!!.size)
        assertPathAndQuery(request, "GET", "/radarr/api/v3/qualityprofile")
    }

    @Test fun createQualityProfileHappyPath() = runTest {
        val body = ArrJson.decodeFromString<QualityProfileResource>(
            fixture("qualityprofile", "qualityprofile"))
        val (result, request) = exchange(created("qualityprofile", "qualityprofile")) {
            RadarrCalendarApis.qualityProfile(it).createQualityProfile(body)
        }
        assertEquals(201, result.code())
        assertEquals(
            body,
            ArrJson.decodeFromString<QualityProfileResource>(request.body.readUtf8()),
        )
        assertPathAndQuery(request, "POST", "/radarr/api/v3/qualityprofile")
    }

    @Test fun qualityProfileHappyPath() = runTest {
        val (result, request) = exchange(ok("qualityprofile", "qualityprofile")) {
            RadarrCalendarApis.qualityProfile(it).qualityProfile(1)
        }
        assertEquals(200, result.code())
        assertEquals(2, result.body()!!.items!!.size)
        assertPathAndQuery(request, "GET", "/radarr/api/v3/qualityprofile/1")
    }

    @Test fun updateQualityProfileHappyPath() = runTest {
        val body = ArrJson.decodeFromString<QualityProfileResource>(
            fixture("qualityprofile", "qualityprofile"))
        val (result, request) = exchange(accepted("qualityprofile", "qualityprofile")) {
            RadarrCalendarApis.qualityProfile(it).updateQualityProfile("1", body)
        }
        assertEquals(202, result.code())
        assertEquals(
            body,
            ArrJson.decodeFromString<QualityProfileResource>(request.body.readUtf8()),
        )
        assertPathAndQuery(request, "PUT", "/radarr/api/v3/qualityprofile/1")
    }

    @Test fun removeQualityProfileHappyPath() = runTest {
        val (result, request) = exchange(MockResponse().setResponseCode(200)) {
            RadarrCalendarApis.qualityProfile(it).removeQualityProfile(1)
        }
        assertTrue(result.isSuccessful)
        assertPathAndQuery(request, "DELETE", "/radarr/api/v3/qualityprofile/1")
    }

    @Test fun qualityProfileErrorPath() = runTest {
        val (result, _) = exchange(MockResponse().setResponseCode(401)) {
            RadarrCalendarApis.qualityProfile(it).createQualityProfile(QualityProfileResource())
        }
        assertEquals(401, result.code())
    }

    @Test fun qualityProfileSchemaHappyPath() = runTest {
        val (result, request) = exchange(
            ok("qualityprofileschema", "qualityprofileschema"),
        ) {
            RadarrCalendarApis.qualityProfileSchema(it).qualityProfileSchema()
        }
        assertEquals("New Profile", result.name)
        assertEquals(2, result.items!!.size)
        assertTrue(result.items!!.none { it.allowed })
        assertPathAndQuery(request, "GET", "/radarr/api/v3/qualityprofile/schema")
    }

    @Test fun qualityProfileSchemaErrorPath() = runTest {
        try {
            exchange(MockResponse().setResponseCode(401)) {
                RadarrCalendarApis.qualityProfileSchema(it).qualityProfileSchema()
            }
            fail("Expected HttpException")
        } catch (e: HttpException) {
            assertEquals(401, e.code())
        }
    }

    @Test fun releaseProfilesHappyPath() = runTest {
        val (result, request) = exchange(ok("releaseprofile", "releaseprofiles")) {
            RadarrCalendarApis.releaseProfile(it).releaseProfiles()
        }
        assertEquals(1, result.size)
        assertEquals("x264\nh264", result[0].required)
        assertEquals(setOf(1, 2), result[0].tags)
        assertPathAndQuery(request, "GET", "/radarr/api/v3/releaseprofile")
    }

    @Test fun createReleaseProfileHappyPath() = runTest {
        val body = ArrJson.decodeFromString<ReleaseProfileResource>(
            fixture("releaseprofile", "releaseprofile"))
        val (result, request) = exchange(created("releaseprofile", "releaseprofile")) {
            RadarrCalendarApis.releaseProfile(it).createReleaseProfile(body)
        }
        assertEquals(201, result.code())
        assertEquals(
            body,
            ArrJson.decodeFromString<ReleaseProfileResource>(request.body.readUtf8()),
        )
        assertPathAndQuery(request, "POST", "/radarr/api/v3/releaseprofile")
    }

    @Test fun releaseProfileHappyPath() = runTest {
        val (result, request) = exchange(ok("releaseprofile", "releaseprofile")) {
            RadarrCalendarApis.releaseProfile(it).releaseProfile(1)
        }
        assertEquals(200, result.code())
        assertEquals("Global", result.body()!!.name)
        assertPathAndQuery(request, "GET", "/radarr/api/v3/releaseprofile/1")
    }

    @Test fun updateReleaseProfileHappyPath() = runTest {
        val body = ArrJson.decodeFromString<ReleaseProfileResource>(
            fixture("releaseprofile", "releaseprofile"))
        val (result, request) = exchange(accepted("releaseprofile", "releaseprofile")) {
            RadarrCalendarApis.releaseProfile(it).updateReleaseProfile("1", body)
        }
        assertEquals(202, result.code())
        assertEquals(
            body,
            ArrJson.decodeFromString<ReleaseProfileResource>(request.body.readUtf8()),
        )
        assertPathAndQuery(request, "PUT", "/radarr/api/v3/releaseprofile/1")
    }

    @Test fun removeReleaseProfileHappyPath() = runTest {
        val (result, request) = exchange(MockResponse().setResponseCode(200)) {
            RadarrCalendarApis.releaseProfile(it).removeReleaseProfile(1)
        }
        assertTrue(result.isSuccessful)
        assertPathAndQuery(request, "DELETE", "/radarr/api/v3/releaseprofile/1")
    }

    @Test fun releaseProfileErrorPath() = runTest {
        val (result, _) = exchange(MockResponse().setResponseCode(404)) {
            RadarrCalendarApis.releaseProfile(it).releaseProfile(99)
        }
        assertEquals(404, result.code())
    }

    @Test fun delayProfilesHappyPath() = runTest {
        val (result, request) = exchange(ok("delayprofile", "delayprofiles")) {
            RadarrCalendarApis.delayProfile(it).delayProfiles()
        }
        assertEquals(2, result.size)
        assertEquals(120, result[0].usenetDelay)
        assertPathAndQuery(request, "GET", "/radarr/api/v3/delayprofile")
    }

    @Test fun createDelayProfileHappyPath() = runTest {
        val body = ArrJson.decodeFromString<DelayProfileResource>(
            fixture("delayprofile", "delayprofile"))
        val (result, request) = exchange(created("delayprofile", "delayprofile")) {
            RadarrCalendarApis.delayProfile(it).createDelayProfile(body)
        }
        assertEquals(201, result.code())
        assertEquals(
            body,
            ArrJson.decodeFromString<DelayProfileResource>(request.body.readUtf8()),
        )
        assertPathAndQuery(request, "POST", "/radarr/api/v3/delayprofile")
    }

    @Test fun delayProfileHappyPath() = runTest {
        val (result, request) = exchange(ok("delayprofile", "delayprofile")) {
            RadarrCalendarApis.delayProfile(it).delayProfile(1)
        }
        assertEquals(200, result.code())
        assertEquals(120, result.body()!!.torrentDelay)
        assertPathAndQuery(request, "GET", "/radarr/api/v3/delayprofile/1")
    }

    @Test fun updateDelayProfileHappyPath() = runTest {
        val body = ArrJson.decodeFromString<DelayProfileResource>(
            fixture("delayprofile", "delayprofile"))
        val (result, request) = exchange(accepted("delayprofile", "delayprofile")) {
            RadarrCalendarApis.delayProfile(it).updateDelayProfile("1", body)
        }
        assertEquals(202, result.code())
        assertEquals(
            body,
            ArrJson.decodeFromString<DelayProfileResource>(request.body.readUtf8()),
        )
        assertPathAndQuery(request, "PUT", "/radarr/api/v3/delayprofile/1")
    }

    @Test fun removeDelayProfileHappyPath() = runTest {
        val (result, request) = exchange(MockResponse().setResponseCode(200)) {
            RadarrCalendarApis.delayProfile(it).removeDelayProfile(1)
        }
        assertTrue(result.isSuccessful)
        assertPathAndQuery(request, "DELETE", "/radarr/api/v3/delayprofile/1")
    }

    @Test fun reorderDelayProfileHappyPath() = runTest {
        val (result, request) = exchange(
            ok("delayprofile", "reorderDelayProfiles"),
        ) {
            RadarrCalendarApis.delayProfile(it).reorderDelayProfile(1, after = 2)
        }
        assertEquals(2, result.size)
        assertEquals(2, result[0].id)
        assertEquals(0, result[0].order)
        assertEquals(1, result[1].id)
        assertEquals(1, result[1].order)
        assertPathAndQuery(
            request, "PUT", "/radarr/api/v3/delayprofile/reorder/1",
            "after" to "2",
        )
    }

    @Test fun delayProfileErrorPath() = runTest {
        try {
            exchange(MockResponse().setResponseCode(401)) {
                RadarrCalendarApis.delayProfile(it).delayProfiles()
            }
            fail("Expected HttpException")
        } catch (e: HttpException) {
            assertEquals(401, e.code())
        }
    }

    @Test fun customFormatsHappyPath() = runTest {
        val (result, request) = exchange(ok("customformat", "customformats")) {
            RadarrCalendarApis.customFormat(it).customFormats()
        }
        assertEquals(1, result.size)
        assertEquals("Surround", result[0].name)
        assertEquals(1, result[0].specifications!!.size)
        assertPathAndQuery(request, "GET", "/radarr/api/v3/customformat")
    }

    @Test fun createCustomFormatHappyPath() = runTest {
        val body = ArrJson.decodeFromString<CustomFormatResource>(
            fixture("customformat", "customformat"))
        val (result, request) = exchange(created("customformat", "customformat")) {
            RadarrCalendarApis.customFormat(it).createCustomFormat(body)
        }
        assertEquals(201, result.code())
        assertEquals(
            body,
            ArrJson.decodeFromString<CustomFormatResource>(request.body.readUtf8()),
        )
        assertPathAndQuery(request, "POST", "/radarr/api/v3/customformat")
    }

    @Test fun customFormatHappyPath() = runTest {
        val (result, request) = exchange(ok("customformat", "customformat")) {
            RadarrCalendarApis.customFormat(it).customFormat(1)
        }
        assertEquals(200, result.code())
        assertEquals("Surround", result.body()!!.name)
        assertPathAndQuery(request, "GET", "/radarr/api/v3/customformat/1")
    }

    @Test fun updateCustomFormatHappyPath() = runTest {
        val body = ArrJson.decodeFromString<CustomFormatResource>(
            fixture("customformat", "customformat"))
        val (result, request) = exchange(accepted("customformat", "customformat")) {
            RadarrCalendarApis.customFormat(it).updateCustomFormat("1", body)
        }
        assertEquals(202, result.code())
        assertEquals(
            body,
            ArrJson.decodeFromString<CustomFormatResource>(request.body.readUtf8()),
        )
        assertPathAndQuery(request, "PUT", "/radarr/api/v3/customformat/1")
    }

    @Test fun removeCustomFormatHappyPath() = runTest {
        val (result, request) = exchange(MockResponse().setResponseCode(200)) {
            RadarrCalendarApis.customFormat(it).removeCustomFormat(1)
        }
        assertTrue(result.isSuccessful)
        assertPathAndQuery(request, "DELETE", "/radarr/api/v3/customformat/1")
    }

    @Test fun updateCustomFormatsHappyPath() = runTest {
        val (result, request) = exchange(ok("customformat", "updateCustomFormats")) {
            RadarrCalendarApis.customFormat(it).updateCustomFormats(
                CustomFormatBulkResource(ids = setOf(1), includeCustomFormatWhenRenaming = true),
            )
        }
        assertEquals(200, result.code())
        assertEquals(1, result.body()!!.single().id)
        assertEquals(
            CustomFormatBulkResource(ids = setOf(1), includeCustomFormatWhenRenaming = true),
            ArrJson.decodeFromString<CustomFormatBulkResource>(request.body.readUtf8()),
        )
        assertPathAndQuery(request, "PUT", "/radarr/api/v3/customformat/bulk")
    }

    @Test fun removeCustomFormatsHappyPath() = runTest {
        val (result, request) = exchange(ok("customformat", "removeCustomFormats")) {
            RadarrCalendarApis.customFormat(it).removeCustomFormats(
                CustomFormatBulkResource(ids = setOf(1)),
            )
        }
        assertEquals(0, result.keys.size)
        assertEquals(
            CustomFormatBulkResource(ids = setOf(1)),
            ArrJson.decodeFromString<CustomFormatBulkResource>(request.body.readUtf8()),
        )
        assertPathAndQuery(request, "DELETE", "/radarr/api/v3/customformat/bulk")
    }

    @Test fun customFormatSchemaHappyPath() = runTest {
        val (result, request) = exchange(ok("customformat", "customformatSchema")) {
            RadarrCalendarApis.customFormat(it).customFormatSchema()
        }
        assertEquals(1, result.size)
        assertEquals("TrueHD Atmos", result[0].name)
        assertPathAndQuery(request, "GET", "/radarr/api/v3/customformat/schema")
    }

    @Test fun customFormatErrorPath() = runTest {
        val (result, _) = exchange(MockResponse().setResponseCode(400)) {
            RadarrCalendarApis.customFormat(it).updateCustomFormats(CustomFormatBulkResource())
        }
        assertEquals(400, result.code())
    }
}
