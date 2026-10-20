package dev.appolon.homelab.arr.api.lidarr

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
 * Live read-only smoke test against the Lidarr server configured in the
 * gitignored `local.arr-test.properties`. Every test self-skips via
 * [AssumptionViolatedException] when the file or its lidarr entry is absent.
 * Safety contract: GET endpoints only; nothing here may search, refresh, or
 * otherwise mutate server state.
 *
 * The artist surface mirrors RadarrLiveSmokeTest. Lidarr's artist library
 * endpoint is unpaged, so the smoke reads the list and asserts the parsed
 * records; the calendar endpoint models entries as albums.
 */
class LidarrLiveSmokeTest {

    private fun liveServer(): ArrTestServer = ArrTestServers.server(AppType.LIDARR)
        ?: throw AssumptionViolatedException(
            "no ${AppType.LIDARR.wireName} entry in ${ArrTestServers.FILE_NAME}"
        )

    private fun retrofit(server: ArrTestServer): Retrofit {
        val baseUrl = ServerEndpoint.normalizeBaseUrl(server.baseUrl)
        val client = DefaultArrClientFactory().clientFor(
            ServerEndpoint(
                serverId = ServerId("lidarr-live-smoke"),
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
        val status = LidarrSystemApis.system(retrofit(liveServer())).getStatus()
        assertEquals("Lidarr", status.appName)
        assertNotNull(status.version)
        assertTrue(status.version!!.isNotBlank())
    }

    @Test fun rootFoldersParse() = runTest {
        val folders = LidarrSystemApis.rootFolder(retrofit(liveServer())).getRootFolders()
        assertTrue("expected at least one root folder", folders.isNotEmpty())
        folders.forEach { assertNotNull("root folder path", it.path) }
    }

    @Test fun artistsParse() = runTest {
        val artists = LidarrLibraryApis.artist(retrofit(liveServer())).getArtists()
        assertTrue("expected at least one artist", artists.isNotEmpty())
        artists.forEach {
            assertTrue("artist id", it.id > 0)
            assertNotNull("artist name", it.artistName)
        }
    }

    @Test fun queueStatusParses() = runTest {
        val status = LidarrQueueApis.queueStatus(retrofit(liveServer())).getQueueStatus()
        assertTrue(status.totalCount >= 0)
    }

    @Test fun calendarTomorrowParses() = runTest {
        val tomorrow = LocalDate.now(ZoneOffset.UTC).plusDays(1).toString()
        val albums = LidarrCalendarApis.calendar(retrofit(liveServer()))
            .getCalendar(start = tomorrow, end = tomorrow)
        albums.forEach {
            assertTrue("album id", it.id > 0)
            assertNotNull("album title", it.title)
        }
    }

    @Test fun qualityProfilesParse() = runTest {
        val profiles = LidarrCalendarApis.qualityProfile(retrofit(liveServer()))
            .getQualityProfiles()
        assertTrue("expected at least one quality profile", profiles.isNotEmpty())
        profiles.forEach {
            assertTrue("profile id", it.id > 0)
            assertNotNull("profile name", it.name)
        }
    }
}
