package dev.appolon.homelab.arr.api.radarr

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
 * Live read-only smoke test against the Radarr server configured in the
 * gitignored `local.arr-test.properties`. Every test self-skips via
 * [AssumptionViolatedException] when the file or its radarr entry is absent,
 * matching the arr-core ArrTestServers rule semantics. Safety contract: GET
 * endpoints only; nothing here may search, refresh, or otherwise mutate
 * server state.
 *
 * The connection is the production stack: [DefaultArrClientFactory] over
 * [ServerEndpoint.normalizeBaseUrl], with the client re-derived at a shorter
 * timeout so an unreachable host fails fast instead of hanging the run.
 */
class RadarrLiveSmokeTest {

    private fun liveServer(): ArrTestServer = ArrTestServers.server(AppType.RADARR)
        ?: throw AssumptionViolatedException(
            "no ${AppType.RADARR.wireName} entry in ${ArrTestServers.FILE_NAME}"
        )

    private fun retrofit(server: ArrTestServer): Retrofit {
        val baseUrl = ServerEndpoint.normalizeBaseUrl(server.baseUrl)
        val client = DefaultArrClientFactory().clientFor(
            ServerEndpoint(
                serverId = ServerId("radarr-live-smoke"),
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
        val status = RadarrSystemApis.system(retrofit(liveServer())).systemStatus()
        assertEquals("Radarr", status.appName)
        assertNotNull(status.version)
        assertTrue(status.version!!.isNotBlank())
    }

    @Test fun rootFoldersParse() = runTest {
        val folders = RadarrSystemApis.rootFolder(retrofit(liveServer())).rootFolders()
        assertTrue("expected at least one root folder", folders.isNotEmpty())
        folders.forEach { assertNotNull("root folder path", it.path) }
    }

    @Test fun moviesPageParses() = runTest {
        val page = RadarrImportlistApis.missing(retrofit(liveServer()))
            .missing(page = 1, pageSize = 1)
        assertEquals(1, page.pageSize)
        val records = page.records
        assertNotNull("records", records)
        assertTrue("pageSize 1 must return at most one record", records!!.size <= 1)
        records.forEach {
            assertTrue("movie id", it.id > 0)
            assertNotNull("movie title", it.title)
        }
    }

    @Test fun queueStatusParses() = runTest {
        val status = RadarrQueueApis.queueStatus(retrofit(liveServer())).queueStatus()
        assertTrue(status.totalCount >= 0)
    }

    @Test fun calendarTomorrowParses() = runTest {
        val tomorrow = LocalDate.now(ZoneOffset.UTC).plusDays(1).toString()
        val movies = RadarrCalendarApis.calendar(retrofit(liveServer()))
            .calendar(start = tomorrow, end = tomorrow)
        movies.forEach {
            assertTrue("movie id", it.id > 0)
            assertNotNull("movie title", it.title)
        }
    }

    @Test fun qualityProfilesParse() = runTest {
        val profiles = RadarrCalendarApis.qualityProfile(retrofit(liveServer()))
            .qualityProfiles()
        assertTrue("expected at least one quality profile", profiles.isNotEmpty())
        profiles.forEach {
            assertTrue("profile id", it.id > 0)
            assertNotNull("profile name", it.name)
        }
    }
}
