package dev.appolon.homelab.arr.api.lidarr

import dev.appolon.homelab.arr.api.lidarr.model.ReleaseResource
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
import retrofit2.Retrofit

@RunWith(Parameterized::class)
class LidarrQueueErrorTest(
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
        try {
            call(queueRetrofit(server))
            fail("Expected HttpException for $tag")
        } catch (error: HttpException) {
            assertEquals(503, error.code())
        }
    }

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun tags(): List<Array<Any>> {
            val calls: Map<String, suspend (Retrofit) -> Any?> = linkedMapOf(
                "Queue" to { LidarrQueueApis.queue(it).getQueue() },
                "QueueAction" to { LidarrQueueApis.queueAction(it).grabQueue(1) },
                "QueueDetails" to { LidarrQueueApis.queueDetails(it).getQueueDetails() },
                "QueueStatus" to { LidarrQueueApis.queueStatus(it).getQueueStatus() },
                "Blocklist" to { LidarrQueueApis.blocklist(it).getBlocklist() },
                "History" to { LidarrQueueApis.history(it).getHistory() },
                "Command" to { LidarrQueueApis.command(it).getCommands() },
                "ManualImport" to { LidarrQueueApis.manualImport(it).getManualImport() },
                "Release" to { LidarrQueueApis.release(it).getReleases() },
                "ReleasePush" to { LidarrQueueApis.releasePush(it).pushRelease(ReleaseResource()) },
                "Parse" to { LidarrQueueApis.parse(it).parse() },
                "Ping" to { LidarrQueueApis.ping(it).ping() },
            )
            return calls.map { (tag, call) -> arrayOf(tag, call) }
        }
    }
}
