package dev.appolon.homelab.arr.api.lidarr

import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
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
 * One error path per system and connect interface. Plain suspend calls surface
 * HTTP failures as HttpException; the raw log file reads return Response, so
 * their failures keep the status code on the response.
 */
@RunWith(Parameterized::class)
class LidarrSystemErrorTest(
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
        val outcome = runCatching { call(systemRetrofit(server)) }
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
            fun part(value: String) = value.toRequestBody("text/plain".toMediaType())
            val calls: Map<String, suspend (Retrofit) -> Any?> = linkedMapOf(
                "ApiInfo" to { LidarrSystemApis.apiInfo(it).getApiInfo() },
                "Authentication" to {
                    LidarrSystemApis.authentication(it)
                        .login(part("admin"), part("secret"), null)
                },
                "Backup" to { LidarrSystemApis.backup(it).getBackups() },
                "DiskSpace" to { LidarrSystemApis.diskSpace(it).getDiskSpace() },
                "Health" to { LidarrSystemApis.health(it).getHealth() },
                "Log" to { LidarrSystemApis.log(it).getLog() },
                "LogFile" to { LidarrSystemApis.logFile(it).getLogFile("lidarr.txt") },
                "System" to { LidarrSystemApis.system(it).getStatus() },
                "Task" to { LidarrSystemApis.task(it).getTasks() },
                "Update" to { LidarrSystemApis.update(it).getUpdates() },
                "UpdateLogFile" to {
                    LidarrSystemApis.updateLogFile(it).getUpdateLogFile("lidarr.update.txt")
                },
                "Localization" to { LidarrSystemApis.localization(it).getLocalization() },
                "FileSystem" to { LidarrSystemApis.fileSystem(it).getFileSystem() },
            )
            return calls.map { (tag, call) -> arrayOf(tag, call) }
        }
    }
}
