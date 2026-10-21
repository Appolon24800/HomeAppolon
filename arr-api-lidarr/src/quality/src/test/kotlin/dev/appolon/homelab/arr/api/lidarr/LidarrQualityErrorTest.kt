package dev.appolon.homelab.arr.api.lidarr

import dev.appolon.homelab.arr.api.lidarr.model.CustomFormatBulkResource
import dev.appolon.homelab.arr.api.lidarr.model.CustomFormatResource
import dev.appolon.homelab.arr.api.lidarr.model.DelayProfileResource
import dev.appolon.homelab.arr.api.lidarr.model.MetadataProfileResource
import dev.appolon.homelab.arr.api.lidarr.model.MetadataProviderConfigResource
import dev.appolon.homelab.arr.api.lidarr.model.QualityDefinitionResource
import dev.appolon.homelab.arr.api.lidarr.model.QualityProfileResource
import dev.appolon.homelab.arr.api.lidarr.model.ReleaseProfileResource
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
class LidarrQualityErrorTest(
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
            call(qualityRetrofit(server))
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
                "QualityDefinition" to {
                    LidarrCalendarApis.qualityDefinition(it).getQualityDefinitions()
                },
                "QualityProfile" to {
                    LidarrCalendarApis.qualityProfile(it)
                        .createQualityProfile(QualityProfileResource())
                },
                "QualityProfileSchema" to {
                    LidarrCalendarApis.qualityProfileSchema(it).getQualityProfileSchema()
                },
                "MetadataProfile" to {
                    LidarrCalendarApis.metadataProfile(it)
                        .createMetadataProfile(MetadataProfileResource())
                },
                "MetadataProfileSchema" to {
                    LidarrCalendarApis.metadataProfileSchema(it).getMetadataProfileSchema()
                },
                "MetadataProviderConfig" to {
                    LidarrCalendarApis.metadataProviderConfig(it).getMetadataProviderConfig()
                },
                "MetadataProviderConfigUpdate" to {
                    LidarrCalendarApis.metadataProviderConfig(it)
                        .updateMetadataProviderConfig("1", MetadataProviderConfigResource())
                },
                "ReleaseProfile" to {
                    LidarrCalendarApis.releaseProfile(it)
                        .createReleaseProfile(ReleaseProfileResource())
                },
                "DelayProfile" to {
                    LidarrCalendarApis.delayProfile(it).createDelayProfile(DelayProfileResource())
                },
                "DelayProfileReorder" to {
                    LidarrCalendarApis.delayProfile(it).reorderDelayProfile(1, 2)
                },
                "CustomFormat" to {
                    LidarrCalendarApis.customFormat(it).createCustomFormat(CustomFormatResource())
                },
                "CustomFormatBulkDelete" to {
                    LidarrCalendarApis.customFormat(it)
                        .deleteCustomFormatBulk(CustomFormatBulkResource())
                },
                "CustomFormatSchema" to {
                    LidarrCalendarApis.customFormat(it).getCustomFormatSchema()
                },
                "QualityDefinitionUpdate" to {
                    LidarrCalendarApis.qualityDefinition(it)
                        .updateQualityDefinition("1", QualityDefinitionResource())
                },
                "Language" to { LidarrCalendarApis.language(it).getLanguages() },
            )
            return calls.map { (tag, call) -> arrayOf(tag, call) }
        }
    }
}
