package dev.appolon.homelab.arr.api.sonarr

import dev.appolon.homelab.arr.core.AppType
import dev.appolon.homelab.arr.core.ArrTestServer
import dev.appolon.homelab.arr.core.ArrTestServers
import dev.appolon.homelab.arr.core.ServerId
import dev.appolon.homelab.arr.net.ArrJson
import dev.appolon.homelab.arr.net.DefaultArrClientFactory
import dev.appolon.homelab.arr.net.ServerEndpoint
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.AssumptionViolatedException
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

private const val TIMEOUT_SECONDS = 10L

/**
 * Live read-only smoke test against the Sonarr server configured in the
 * gitignored `local.arr-test.properties`. Every test self-skips via
 * [AssumptionViolatedException] when the file or its sonarr entry is absent.
 * Safety contract: GET endpoints only; nothing here may search, refresh, or
 * otherwise mutate server state.
 *
 * The series surface mirrors RadarrLiveSmokeTest. Sonarr's series library
 * endpoint is unpaged (the spec layer has no SeriesResourcePagingResource),
 * so the smoke reads the whole list and asserts the parsed records.
 */
class SonarrLiveSmokeTest {

    private fun liveServer(): ArrTestServer = ArrTestServers.server(AppType.SONARR)
        ?: throw AssumptionViolatedException(
            "no ${AppType.SONARR.wireName} entry in ${ArrTestServers.FILE_NAME}"
        )

    private fun retrofit(server: ArrTestServer): Retrofit {
        val baseUrl = ServerEndpoint.normalizeBaseUrl(server.baseUrl)
        val client = DefaultArrClientFactory().clientFor(
            ServerEndpoint(
                serverId = ServerId("sonarr-live-smoke"),
                baseUrl = baseUrl,
                apiKey = server.apiKey,
            ),
        ).newBuilder()
            .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .writeTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .build()
        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(ArrJson.asConverterFactory("application/json".toMediaType()))
            .build()
    }

    @Test fun systemStatusParses() = runTest {
        val status = SonarrSystemApis.system(retrofit(liveServer())).status()
        assertEquals("Sonarr", status.appName)
        assertNotNull(status.version)
        assertTrue(status.version!!.isNotBlank())
    }

    @Test fun rootFoldersParse() = runTest {
        val folders = SonarrSystemApis.rootFolder(retrofit(liveServer())).list()
        assertTrue("expected at least one root folder", folders.isNotEmpty())
        folders.forEach { assertNotNull("root folder path", it.path) }
    }

    @Test fun seriesParse() = runTest {
        val series = SonarrLibraryApis.series(retrofit(liveServer())).list()
        assertTrue("expected at least one series", series.isNotEmpty())
        series.forEach {
            assertTrue("series id", it.id > 0)
            assertNotNull("series title", it.title)
        }
    }

    @Test fun queueStatusParses() = runTest {
        val status = SonarrQueueApis.queueStatus(retrofit(liveServer())).list()
        assertTrue(status.totalCount >= 0)
    }

    @Test fun calendarTomorrowParses() = runTest {
        val tomorrow = LocalDate.now(ZoneOffset.UTC).plusDays(1).toString()
        val episodes = SonarrCalendarApis.calendar(retrofit(liveServer()))
            .list(start = tomorrow, end = tomorrow)
        episodes.forEach {
            assertTrue("episode id", it.id > 0)
            assertTrue("episode seriesId", it.seriesId > 0)
        }
    }

    @Test fun qualityProfilesParse() = runTest {
        val profiles = SonarrCalendarApis.qualityProfile(retrofit(liveServer())).list()
        assertTrue("expected at least one quality profile", profiles.isNotEmpty())
        profiles.forEach {
            assertTrue("profile id", it.id > 0)
            assertNotNull("profile name", it.name)
        }
    }
}
