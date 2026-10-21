package dev.appolon.homelab.arr.api.prowlarr

import dev.appolon.homelab.arr.core.AppType
import dev.appolon.homelab.arr.core.ArrTestServer
import dev.appolon.homelab.arr.core.ArrTestServers
import dev.appolon.homelab.arr.core.ServerId
import dev.appolon.homelab.arr.net.ArrJson
import dev.appolon.homelab.arr.net.DefaultArrClientFactory
import dev.appolon.homelab.arr.net.ServerEndpoint
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
 * Live read-only smoke test against the Prowlarr server configured in the
 * gitignored `local.arr-test.properties`. Every test self-skips via
 * [AssumptionViolatedException] when the file or its prowlarr entry is absent.
 * Safety contract: GET endpoints only; no search queries, no indexer tests,
 * nothing that mutates server state or reaches out to indexers.
 */
class ProwlarrLiveSmokeTest {

    private fun liveServer(): ArrTestServer = ArrTestServers.server(AppType.PROWLARR)
        ?: throw AssumptionViolatedException(
            "no ${AppType.PROWLARR.wireName} entry in ${ArrTestServers.FILE_NAME}"
        )

    private fun retrofit(server: ArrTestServer): Retrofit {
        val baseUrl = ServerEndpoint.normalizeBaseUrl(server.baseUrl)
        val client = DefaultArrClientFactory().clientFor(
            ServerEndpoint(
                serverId = ServerId("prowlarr-live-smoke"),
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
        val status = ProwlarrSystemApis.system(retrofit(liveServer())).getSystemStatus()
        assertEquals("Prowlarr", status.appName)
        assertNotNull(status.version)
        assertTrue(status.version!!.isNotBlank())
    }

    @Test fun indexersParse() = runTest {
        val indexers = ProwlarrIndexersApis.indexer(retrofit(liveServer())).getIndexers()
        assertTrue("expected at least one indexer", indexers.isNotEmpty())
        indexers.take(1).forEach {
            assertTrue("indexer id", it.id > 0)
            assertNotNull("indexer name", it.name)
        }
    }

    @Test fun indexerStatsParse() = runTest {
        val stats = ProwlarrSearchApis.indexerStats(retrofit(liveServer()))
            .getIndexerStats(
                startDate = null,
                endDate = null,
                indexers = null,
                protocols = null,
                tags = null,
            )
        assertNotNull("indexers rollup", stats.indexers)
        assertNotNull("userAgents rollup", stats.userAgents)
        assertNotNull("hosts rollup", stats.hosts)
    }
}
