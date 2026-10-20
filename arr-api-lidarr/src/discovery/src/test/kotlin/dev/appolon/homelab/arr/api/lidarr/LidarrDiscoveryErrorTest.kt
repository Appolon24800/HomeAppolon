package dev.appolon.homelab.arr.api.lidarr

import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import retrofit2.HttpException
import retrofit2.Response
import retrofit2.Retrofit

/**
 * One error path per discovery interface. Plain suspend calls surface HTTP
 * failures as HttpException; the StaticResource interface returns Response, so
 * its failure keeps the status code on the response.
 */
@RunWith(Parameterized::class)
class LidarrDiscoveryErrorTest(
    private val tag: String,
    private val call: suspend (Retrofit) -> Any?,
) {
    private val server = MockWebServer()

    @Before
    fun setUp() = server.start()

    @After
    fun tearDown() = server.shutdown()

    @Test
    fun httpErrorPreservesStatus() = runTest {
        server.enqueue(MockResponse().setResponseCode(503).setBody("{}"))
        val outcome = runCatching { call(discoveryRetrofit(server)) }
        val failure = outcome.exceptionOrNull()
        if (failure is HttpException) {
            assertEquals(503, failure.code())
            return@runTest
        }
        when (val result = outcome.getOrNull()) {
            is Response<*> -> assertEquals(503, result.code())
            else -> fail("Expected HttpException or error Response for $tag")
        }
    }

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun tags(): List<Array<Any>> {
            val calls: Map<String, suspend (Retrofit) -> Any?> = linkedMapOf(
                "ImportList" to { LidarrDiscoveryApis.importList(it).getImportLists() },
                "ImportListExclusion" to {
                    LidarrDiscoveryApis.importListExclusion(it).getImportListExclusions()
                },
                "CustomFilter" to { LidarrDiscoveryApis.customFilter(it).getCustomFilters() },
                "Missing" to { LidarrDiscoveryApis.missing(it).getMissing() },
                "Cutoff" to { LidarrDiscoveryApis.cutoff(it).getCutoff() },
                "Search" to { LidarrDiscoveryApis.search(it).search("term") },
                "StaticResource" to {
                    LidarrDiscoveryApis.staticResource(it).getStaticResource("favicon.ico")
                },
            )
            return calls.map { (tag, call) -> arrayOf(tag, call) }
        }
    }
}
