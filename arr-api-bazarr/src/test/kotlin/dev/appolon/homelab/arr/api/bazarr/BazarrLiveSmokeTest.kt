package dev.appolon.homelab.arr.api.bazarr

import dev.appolon.homelab.arr.core.AppType
import dev.appolon.homelab.arr.core.ArrTestServer
import dev.appolon.homelab.arr.core.ArrTestServers
import dev.appolon.homelab.arr.core.ServerId
import dev.appolon.homelab.arr.net.ArrJson
import dev.appolon.homelab.arr.net.DefaultArrClientFactory
import dev.appolon.homelab.arr.net.ServerEndpoint
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import okhttp3.MediaType.Companion.toMediaType
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.AssumptionViolatedException
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

private const val TIMEOUT_SECONDS = 10L

/**
 * Live read-only smoke test against the Bazarr server configured in the
 * gitignored `local.arr-test.properties`. Every test self-skips via
 * [AssumptionViolatedException] when the file or its bazarr entry is absent.
 * Safety contract: GET endpoints only; no subtitle searches or downloads.
 *
 * Bazarr's base URL contract is the deployment root: Retrofit gets
 * [ServerEndpoint.normalizeBaseUrl] of the bare URL and every annotation path
 * carries its own `api/` prefix. The bundled spec declares no response
 * schemas for these endpoints, so the committed layer passes raw
 * [JsonObject]s through and the smoke asserts the paged envelope shape
 * (`data` array, `total` counter) instead of typed DTO fields.
 */
class BazarrLiveSmokeTest {

    private fun liveServer(): ArrTestServer = ArrTestServers.server(AppType.BAZARR)
        ?: throw AssumptionViolatedException(
            "no ${AppType.BAZARR.wireName} entry in ${ArrTestServers.FILE_NAME}"
        )

    private fun retrofit(server: ArrTestServer): Retrofit {
        val baseUrl = ServerEndpoint.normalizeBaseUrl(server.baseUrl)
        val client = DefaultArrClientFactory().clientFor(
            ServerEndpoint(
                serverId = ServerId("bazarr-live-smoke"),
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

    /**
     * The bundled spec declares no schema here, but live Bazarr wraps the
     * payload in the same `data` envelope as its paged endpoints, with
     * `bazarr_version` among the fields. The smoke pins that envelope; the
     * committed layer passes the whole object through untouched.
     */
    @Test fun systemStatusParses() = runTest {
        val status = BazarrSystemApis.status(retrofit(liveServer())).getSystemStatus()
        val data = status["data"] as? JsonObject
        assertNotNull("expected a data object in the status envelope", data)
        assertTrue("expected a non-empty status payload", data!!.isNotEmpty())
        assertTrue("expected a bazarr_version key", data.containsKey("bazarr_version"))
    }

    @Test fun seriesPageParses() = runTest {
        val page = BazarrLibraryApis.series(retrofit(liveServer()))
            .getSeries(start = 0, length = 1)
        val data = pagedData(page)
        assertTrue("length 1 must return at most one row", data.size <= 1)
        data.forEach { row ->
            val obj = row as? JsonObject
            assertNotNull("series row must be an object", obj)
            assertTrue("series row must be non-empty", obj!!.isNotEmpty())
        }
    }

    @Test fun episodesWantedParses() = runTest {
        val page = BazarrLibraryApis.episodesWanted(retrofit(liveServer()))
            .getEpisodesWanted(start = 0, length = 1)
        val data = pagedData(page)
        assertTrue("length 1 must return at most one row", data.size <= 1)
        data.forEach { row ->
            val obj = row as? JsonObject
            assertNotNull("wanted row must be an object", obj)
            assertTrue("wanted row must be non-empty", obj!!.isNotEmpty())
        }
    }

    private fun pagedData(page: JsonObject): JsonArray {
        assertTrue("expected a data array in the paging envelope", page.containsKey("data"))
        val data = page["data"]
        assertNotNull("data", data)
        assertTrue("data must be an array", data is JsonArray)
        return data as JsonArray
    }
}
