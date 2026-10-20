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
 * One error path per settings interface. Every settings client uses plain
 * suspend calls, so HTTP failures surface as HttpException with the code kept.
 */
@RunWith(Parameterized::class)
class LidarrSettingsErrorTest(
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
        val outcome = runCatching { call(settingsRetrofit(server)) }
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
                "AutoTagging" to { LidarrSystemApis.autoTagging(it).getAutoTaggings() },
                "HostConfig" to { LidarrSystemApis.hostConfig(it).getHostConfig() },
                "MediaManagementConfig" to {
                    LidarrSystemApis.mediaManagementConfig(it).getMediaManagementConfig()
                },
                "NamingConfig" to { LidarrSystemApis.namingConfig(it).getNamingConfig() },
                "UiConfig" to { LidarrSystemApis.uiConfig(it).getUiConfig() },
                "Metadata" to { LidarrSystemApis.metadata(it).getMetadatas() },
                "Notification" to { LidarrSystemApis.notification(it).getNotifications() },
                "DownloadClient" to {
                    LidarrSystemApis.downloadClient(it).getDownloadClients()
                },
                "DownloadClientConfig" to {
                    LidarrSystemApis.downloadClientConfig(it).getDownloadClientConfig()
                },
                "Indexer" to { LidarrSystemApis.indexer(it).getIndexers() },
                "IndexerConfig" to { LidarrSystemApis.indexerConfig(it).getIndexerConfig() },
                "IndexerFlag" to { LidarrSystemApis.indexerFlag(it).getIndexerFlags() },
                "RemotePathMapping" to {
                    LidarrSystemApis.remotePathMapping(it).getRemotePathMappings()
                },
                "RootFolder" to { LidarrSystemApis.rootFolder(it).getRootFolders() },
                "Tag" to { LidarrSystemApis.tag(it).getTags() },
                "TagDetails" to { LidarrSystemApis.tagDetails(it).getTagDetails() },
            )
            return calls.map { (tag, call) -> arrayOf(tag, call) }
        }
    }
}
