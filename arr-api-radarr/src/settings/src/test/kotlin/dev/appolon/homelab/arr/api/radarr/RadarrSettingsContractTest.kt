package dev.appolon.homelab.arr.api.radarr

import dev.appolon.homelab.arr.api.radarr.model.AutoTaggingResource
import dev.appolon.homelab.arr.api.radarr.model.ColonReplacementFormat
import dev.appolon.homelab.arr.api.radarr.model.HostConfigResource
import dev.appolon.homelab.arr.api.radarr.model.MediaManagementConfigResource
import dev.appolon.homelab.arr.api.radarr.model.MetadataConfigResource
import dev.appolon.homelab.arr.api.radarr.model.MetadataResource
import dev.appolon.homelab.arr.api.radarr.model.NamingConfigResource
import dev.appolon.homelab.arr.api.radarr.model.UiConfigResource
import dev.appolon.homelab.arr.net.ArrJson
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

/** Each spec operation has its own fixture and verifies the base-path prefix. */
class RadarrSettingsContractTest {
    private fun retrofit(server: MockWebServer): Retrofit = Retrofit.Builder()
        .baseUrl(server.url("/radarr/"))
        .addConverterFactory(ArrJson.asConverterFactory("application/json".toMediaType()))
        .build()

    private fun fixture(tag: String, name: String): String =
        requireNotNull(javaClass.classLoader?.getResourceAsStream(
            "fixtures/radarr/$tag/$name.json"
        )).bufferedReader().use { it.readText() }

    @Test fun autoTaggingsHappyPath() = runTest {
        val server = MockWebServer()
        try {
            server.enqueue(MockResponse().setResponseCode(200)
                .setBody(fixture("autotagging", "autoTaggings")))
            val api = RadarrSystemApis.autoTagging(retrofit(server))
            val result = api.autoTaggings()
            assertEquals(1, result.size)
            assertEquals(setOf(1), result.single().tags)
            assertEquals(1, result.single().specifications!!.size)
            val request = server.takeRequest()
            assertEquals("GET", request.method)
            assertEquals("/radarr/api/v3/autotagging", request.requestUrl!!.encodedPath)
        } finally {
            server.shutdown()
        }
    }

    @Test fun createAutoTaggingHappyPath() = runTest {
        val server = MockWebServer()
        try {
            server.enqueue(MockResponse().setResponseCode(201)
                .setBody(fixture("autotagging", "createAutoTagging")))
            val api = RadarrSystemApis.autoTagging(retrofit(server))
            val body = AutoTaggingResource(name = "Movies on SSD", tags = setOf(1))
            val result = api.createAutoTagging(body)
            assertEquals(201, result.code())
            assertEquals("Movies on SSD", result.body()!!.name)
            val request = server.takeRequest()
            assertEquals("POST", request.method)
            assertEquals("/radarr/api/v3/autotagging", request.requestUrl!!.encodedPath)
            assertEquals(body, ArrJson.decodeFromString<AutoTaggingResource>(
                request.body.readUtf8()))
        } finally {
            server.shutdown()
        }
    }

    @Test fun updateAutoTaggingHappyPath() = runTest {
        val server = MockWebServer()
        try {
            server.enqueue(MockResponse().setResponseCode(202)
                .setBody(fixture("autotagging", "updateAutoTagging")))
            val api = RadarrSystemApis.autoTagging(retrofit(server))
            val body = AutoTaggingResource(id = 1, name = "Movies on HDD", tags = setOf(1))
            val result = api.updateAutoTagging(id = "1", body = body)
            assertEquals(202, result.code())
            assertEquals("Movies on HDD", result.body()!!.name)
            val request = server.takeRequest()
            assertEquals("PUT", request.method)
            assertEquals("/radarr/api/v3/autotagging/1", request.requestUrl!!.encodedPath)
            assertEquals(body, ArrJson.decodeFromString<AutoTaggingResource>(
                request.body.readUtf8()))
        } finally {
            server.shutdown()
        }
    }

    @Test fun removeAutoTaggingHappyPath() = runTest {
        val server = MockWebServer()
        try {
            server.enqueue(MockResponse().setResponseCode(200))
            val api = RadarrSystemApis.autoTagging(retrofit(server))
            val result = api.removeAutoTagging(id = 1)
            assertEquals(200, result.code())
            val request = server.takeRequest()
            assertEquals("DELETE", request.method)
            assertEquals("/radarr/api/v3/autotagging/1", request.requestUrl!!.encodedPath)
        } finally {
            server.shutdown()
        }
    }

    @Test fun autoTaggingHappyPath() = runTest {
        val server = MockWebServer()
        try {
            server.enqueue(MockResponse().setResponseCode(200)
                .setBody(fixture("autotagging", "getAutoTagging")))
            val api = RadarrSystemApis.autoTagging(retrofit(server))
            val result = api.autoTagging(id = 1)
            assertEquals(200, result.code())
            assertEquals("Movies on SSD", result.body()!!.name)
            val request = server.takeRequest()
            assertEquals("GET", request.method)
            assertEquals("/radarr/api/v3/autotagging/1", request.requestUrl!!.encodedPath)
        } finally {
            server.shutdown()
        }
    }

    @Test fun autoTaggingSchemaHappyPath() = runTest {
        val server = MockWebServer()
        try {
            server.enqueue(MockResponse().setResponseCode(200)
                .setBody(fixture("autotagging", "autoTaggingSchema")))
            val api = RadarrSystemApis.autoTagging(retrofit(server))
            val result = api.autoTaggingSchema()
            assertEquals(1, result.size)
            assertEquals("QualitySpecification", result.single().implementation)
            val request = server.takeRequest()
            assertEquals("GET", request.method)
            assertEquals("/radarr/api/v3/autotagging/schema",
                request.requestUrl!!.encodedPath)
        } finally {
            server.shutdown()
        }
    }

    @Test fun hostConfigHappyPath() = runTest {
        val server = MockWebServer()
        try {
            server.enqueue(MockResponse().setResponseCode(200)
                .setBody(fixture("hostconfig", "hostConfig")))
            val api = RadarrSystemApis.hostConfig(retrofit(server))
            val result = api.hostConfig()
            assertEquals(7878, result.port)
            assertEquals("master", result.branch)
            val request = server.takeRequest()
            assertEquals("GET", request.method)
            assertEquals("/radarr/api/v3/config/host", request.requestUrl!!.encodedPath)
        } finally {
            server.shutdown()
        }
    }

    @Test fun updateHostConfigHappyPath() = runTest {
        val server = MockWebServer()
        try {
            server.enqueue(MockResponse().setResponseCode(202)
                .setBody(fixture("hostconfig", "updateHostConfig")))
            val api = RadarrSystemApis.hostConfig(retrofit(server))
            val body = HostConfigResource(id = 1, branch = "develop", port = 7879)
            val result = api.updateHostConfig(id = "1", body = body)
            assertEquals(202, result.code())
            assertEquals("develop", result.body()!!.branch)
            val request = server.takeRequest()
            assertEquals("PUT", request.method)
            assertEquals("/radarr/api/v3/config/host/1", request.requestUrl!!.encodedPath)
            assertEquals(body, ArrJson.decodeFromString<HostConfigResource>(
                request.body.readUtf8()))
        } finally {
            server.shutdown()
        }
    }

    @Test fun hostConfigByIdHappyPath() = runTest {
        val server = MockWebServer()
        try {
            server.enqueue(MockResponse().setResponseCode(200)
                .setBody(fixture("hostconfig", "hostConfigById")))
            val api = RadarrSystemApis.hostConfig(retrofit(server))
            val result = api.hostConfigById(id = 1)
            assertEquals(200, result.code())
            assertEquals(7878, result.body()!!.port)
            val request = server.takeRequest()
            assertEquals("GET", request.method)
            assertEquals("/radarr/api/v3/config/host/1", request.requestUrl!!.encodedPath)
        } finally {
            server.shutdown()
        }
    }

    @Test fun mediaManagementConfigHappyPath() = runTest {
        val server = MockWebServer()
        try {
            server.enqueue(MockResponse().setResponseCode(200)
                .setBody(fixture("mediamanagementconfig", "mediaManagementConfig")))
            val api = RadarrSystemApis.mediaManagementConfig(retrofit(server))
            val result = api.mediaManagementConfig()
            assertTrue(result.copyUsingHardlinks)
            assertEquals(7, result.recycleBinCleanupDays)
            val request = server.takeRequest()
            assertEquals("GET", request.method)
            assertEquals("/radarr/api/v3/config/mediamanagement",
                request.requestUrl!!.encodedPath)
        } finally {
            server.shutdown()
        }
    }

    @Test fun updateMediaManagementConfigHappyPath() = runTest {
        val server = MockWebServer()
        try {
            server.enqueue(MockResponse().setResponseCode(202)
                .setBody(fixture("mediamanagementconfig", "updateMediaManagementConfig")))
            val api = RadarrSystemApis.mediaManagementConfig(retrofit(server))
            val body = MediaManagementConfigResource(id = 1)
            val result = api.updateMediaManagementConfig(id = "1", body = body)
            assertEquals(202, result.code())
            val request = server.takeRequest()
            assertEquals("PUT", request.method)
            assertEquals("/radarr/api/v3/config/mediamanagement/1",
                request.requestUrl!!.encodedPath)
            assertEquals(body, ArrJson.decodeFromString<MediaManagementConfigResource>(
                request.body.readUtf8()))
        } finally {
            server.shutdown()
        }
    }

    @Test fun mediaManagementConfigByIdHappyPath() = runTest {
        val server = MockWebServer()
        try {
            server.enqueue(MockResponse().setResponseCode(200)
                .setBody(fixture("mediamanagementconfig", "mediaManagementConfigById")))
            val api = RadarrSystemApis.mediaManagementConfig(retrofit(server))
            val result = api.mediaManagementConfigById(id = 1)
            assertEquals(200, result.code())
            assertEquals(1, result.body()!!.id)
            val request = server.takeRequest()
            assertEquals("GET", request.method)
            assertEquals("/radarr/api/v3/config/mediamanagement/1",
                request.requestUrl!!.encodedPath)
        } finally {
            server.shutdown()
        }
    }

    @Test fun metadataHappyPath() = runTest {
        val server = MockWebServer()
        try {
            server.enqueue(MockResponse().setResponseCode(200)
                .setBody(fixture("metadata", "metadata")))
            val api = RadarrSystemApis.metadata(retrofit(server))
            val result = api.metadata()
            assertEquals(1, result.size)
            assertEquals("Kodi", result.single().implementationName)
            assertTrue(result.single().enable)
            val request = server.takeRequest()
            assertEquals("GET", request.method)
            assertEquals("/radarr/api/v3/metadata", request.requestUrl!!.encodedPath)
        } finally {
            server.shutdown()
        }
    }

    @Test fun createMetadataHappyPath() = runTest {
        val server = MockWebServer()
        try {
            server.enqueue(MockResponse().setResponseCode(201)
                .setBody(fixture("metadata", "createMetadata")))
            val api = RadarrSystemApis.metadata(retrofit(server))
            val body = MetadataResource(name = "Kodi", enable = true)
            val result = api.createMetadata(forceSave = true, body = body)
            assertEquals(201, result.code())
            val request = server.takeRequest()
            assertEquals("POST", request.method)
            assertEquals("/radarr/api/v3/metadata", request.requestUrl!!.encodedPath)
            assertEquals("true", request.requestUrl!!.queryParameter("forceSave"))
            assertEquals(body, ArrJson.decodeFromString<MetadataResource>(
                request.body.readUtf8()))
        } finally {
            server.shutdown()
        }
    }

    @Test fun updateMetadataHappyPath() = runTest {
        val server = MockWebServer()
        try {
            server.enqueue(MockResponse().setResponseCode(202)
                .setBody(fixture("metadata", "updateMetadata")))
            val api = RadarrSystemApis.metadata(retrofit(server))
            val body = MetadataResource(id = 1, name = "Kodi", enable = false)
            val result = api.updateMetadata(id = 1, forceSave = false, body = body)
            assertEquals(202, result.code())
            assertFalse(result.body()!!.enable)
            val request = server.takeRequest()
            assertEquals("PUT", request.method)
            assertEquals("/radarr/api/v3/metadata/1", request.requestUrl!!.encodedPath)
            assertEquals("false", request.requestUrl!!.queryParameter("forceSave"))
            assertEquals(body, ArrJson.decodeFromString<MetadataResource>(
                request.body.readUtf8()))
        } finally {
            server.shutdown()
        }
    }

    @Test fun removeMetadataHappyPath() = runTest {
        val server = MockWebServer()
        try {
            server.enqueue(MockResponse().setResponseCode(200))
            val api = RadarrSystemApis.metadata(retrofit(server))
            val result = api.removeMetadata(id = 1)
            assertEquals(200, result.code())
            val request = server.takeRequest()
            assertEquals("DELETE", request.method)
            assertEquals("/radarr/api/v3/metadata/1", request.requestUrl!!.encodedPath)
        } finally {
            server.shutdown()
        }
    }

    @Test fun metadataByIdHappyPath() = runTest {
        val server = MockWebServer()
        try {
            server.enqueue(MockResponse().setResponseCode(200)
                .setBody(fixture("metadata", "getMetadata")))
            val api = RadarrSystemApis.metadata(retrofit(server))
            val result = api.metadataById(id = 1)
            assertEquals(200, result.code())
            assertEquals("Kodi", result.body()!!.name)
            val request = server.takeRequest()
            assertEquals("GET", request.method)
            assertEquals("/radarr/api/v3/metadata/1", request.requestUrl!!.encodedPath)
        } finally {
            server.shutdown()
        }
    }

    @Test fun metadataSchemaHappyPath() = runTest {
        val server = MockWebServer()
        try {
            server.enqueue(MockResponse().setResponseCode(200)
                .setBody(fixture("metadata", "metadataSchema")))
            val api = RadarrSystemApis.metadata(retrofit(server))
            val result = api.metadataSchema()
            assertEquals(1, result.size)
            assertEquals("KodiMetadata", result.single().implementation)
            val request = server.takeRequest()
            assertEquals("GET", request.method)
            assertEquals("/radarr/api/v3/metadata/schema", request.requestUrl!!.encodedPath)
        } finally {
            server.shutdown()
        }
    }

    @Test fun testMetadataHappyPath() = runTest {
        val server = MockWebServer()
        try {
            server.enqueue(MockResponse().setResponseCode(204))
            val api = RadarrSystemApis.metadata(retrofit(server))
            val body = MetadataResource(name = "Kodi")
            val result = api.testMetadata(forceTest = true, body = body)
            assertEquals(204, result.code())
            val request = server.takeRequest()
            assertEquals("POST", request.method)
            assertEquals("/radarr/api/v3/metadata/test", request.requestUrl!!.encodedPath)
            assertEquals("true", request.requestUrl!!.queryParameter("forceTest"))
            assertEquals(body, ArrJson.decodeFromString<MetadataResource>(
                request.body.readUtf8()))
        } finally {
            server.shutdown()
        }
    }

    @Test fun testAllMetadataHappyPath() = runTest {
        val server = MockWebServer()
        try {
            server.enqueue(MockResponse().setResponseCode(200)
                .setBody(fixture("metadata", "testAllMetadata")))
            val api = RadarrSystemApis.metadata(retrofit(server))
            val result = api.testAllMetadata()
            assertEquals(200, result.code())
            assertTrue(result.body()!!.single().isValid)
            val request = server.takeRequest()
            assertEquals("POST", request.method)
            assertEquals("/radarr/api/v3/metadata/testall", request.requestUrl!!.encodedPath)
        } finally {
            server.shutdown()
        }
    }

    @Test fun metadataActionHappyPath() = runTest {
        val server = MockWebServer()
        try {
            server.enqueue(MockResponse().setResponseCode(200))
            val api = RadarrSystemApis.metadata(retrofit(server))
            val body = MetadataResource(name = "Kodi")
            val result = api.metadataAction(name = "someAction", body = body)
            assertEquals(200, result.code())
            val request = server.takeRequest()
            assertEquals("POST", request.method)
            assertEquals("/radarr/api/v3/metadata/action/someAction",
                request.requestUrl!!.encodedPath)
            assertEquals(body, ArrJson.decodeFromString<MetadataResource>(
                request.body.readUtf8()))
        } finally {
            server.shutdown()
        }
    }

    @Test fun metadataConfigHappyPath() = runTest {
        val server = MockWebServer()
        try {
            server.enqueue(MockResponse().setResponseCode(200)
                .setBody(fixture("metadataconfig", "metadataConfig")))
            val api = RadarrSystemApis.metadataConfig(retrofit(server))
            val result = api.metadataConfig()
            assertEquals(1, result.id)
            assertEquals("us", result.certificationCountry?.toString())
            val request = server.takeRequest()
            assertEquals("GET", request.method)
            assertEquals("/radarr/api/v3/config/metadata", request.requestUrl!!.encodedPath)
        } finally {
            server.shutdown()
        }
    }

    @Test fun updateMetadataConfigHappyPath() = runTest {
        val server = MockWebServer()
        try {
            server.enqueue(MockResponse().setResponseCode(202)
                .setBody(fixture("metadataconfig", "updateMetadataConfig")))
            val api = RadarrSystemApis.metadataConfig(retrofit(server))
            val body = MetadataConfigResource(id = 1)
            val result = api.updateMetadataConfig(id = "1", body = body)
            assertEquals(202, result.code())
            val request = server.takeRequest()
            assertEquals("PUT", request.method)
            assertEquals("/radarr/api/v3/config/metadata/1", request.requestUrl!!.encodedPath)
            assertEquals(body, ArrJson.decodeFromString<MetadataConfigResource>(
                request.body.readUtf8()))
        } finally {
            server.shutdown()
        }
    }

    @Test fun metadataConfigByIdHappyPath() = runTest {
        val server = MockWebServer()
        try {
            server.enqueue(MockResponse().setResponseCode(200)
                .setBody(fixture("metadataconfig", "metadataConfigById")))
            val api = RadarrSystemApis.metadataConfig(retrofit(server))
            val result = api.metadataConfigById(id = 1)
            assertEquals(200, result.code())
            assertEquals(1, result.body()!!.id)
            val request = server.takeRequest()
            assertEquals("GET", request.method)
            assertEquals("/radarr/api/v3/config/metadata/1", request.requestUrl!!.encodedPath)
        } finally {
            server.shutdown()
        }
    }

    @Test fun namingConfigHappyPath() = runTest {
        val server = MockWebServer()
        try {
            server.enqueue(MockResponse().setResponseCode(200)
                .setBody(fixture("namingconfig", "namingConfig")))
            val api = RadarrSystemApis.namingConfig(retrofit(server))
            val result = api.namingConfig()
            assertTrue(result.renameMovies)
            assertEquals(ColonReplacementFormat.DASH, result.colonReplacementFormat)
            val request = server.takeRequest()
            assertEquals("GET", request.method)
            assertEquals("/radarr/api/v3/config/naming", request.requestUrl!!.encodedPath)
        } finally {
            server.shutdown()
        }
    }

    @Test fun updateNamingConfigHappyPath() = runTest {
        val server = MockWebServer()
        try {
            server.enqueue(MockResponse().setResponseCode(202)
                .setBody(fixture("namingconfig", "updateNamingConfig")))
            val api = RadarrSystemApis.namingConfig(retrofit(server))
            val body = NamingConfigResource(id = 1)
            val result = api.updateNamingConfig(id = "1", body = body)
            assertEquals(202, result.code())
            val request = server.takeRequest()
            assertEquals("PUT", request.method)
            assertEquals("/radarr/api/v3/config/naming/1", request.requestUrl!!.encodedPath)
            assertEquals(body, ArrJson.decodeFromString<NamingConfigResource>(
                request.body.readUtf8()))
        } finally {
            server.shutdown()
        }
    }

    @Test fun namingConfigByIdHappyPath() = runTest {
        val server = MockWebServer()
        try {
            server.enqueue(MockResponse().setResponseCode(200)
                .setBody(fixture("namingconfig", "namingConfigById")))
            val api = RadarrSystemApis.namingConfig(retrofit(server))
            val result = api.namingConfigById(id = 1)
            assertEquals(200, result.code())
            assertEquals(1, result.body()!!.id)
            val request = server.takeRequest()
            assertEquals("GET", request.method)
            assertEquals("/radarr/api/v3/config/naming/1", request.requestUrl!!.encodedPath)
        } finally {
            server.shutdown()
        }
    }

    @Test fun namingExamplesHappyPath() = runTest {
        val server = MockWebServer()
        try {
            server.enqueue(MockResponse().setResponseCode(200)
                .setBody(fixture("namingconfig", "namingExamples")))
            val api = RadarrSystemApis.namingConfig(retrofit(server))
            val result = api.namingExamples(
                renameMovies = true,
                replaceIllegalCharacters = false,
                colonReplacementFormat = ColonReplacementFormat.SMART,
                standardMovieFormat = "{Movie Title} ({Release Year})",
                movieFolderFormat = "{Movie Title} ({Release Year})",
                id = 1,
                resourceName = "radarr",
            )
            assertEquals("Movie Title (2026) BluRay-1080p", result.movieExample)
            assertEquals("Movie Title (2026)", result.movieFolderExample)
            val request = server.takeRequest()
            assertEquals("GET", request.method)
            assertEquals("/radarr/api/v3/config/naming/examples",
                request.requestUrl!!.encodedPath)
            assertEquals("true", request.requestUrl!!.queryParameter("renameMovies"))
            assertEquals("false",
                request.requestUrl!!.queryParameter("replaceIllegalCharacters"))
            assertEquals("smart",
                request.requestUrl!!.queryParameter("colonReplacementFormat"))
            assertEquals("{Movie Title} ({Release Year})",
                request.requestUrl!!.queryParameter("standardMovieFormat"))
            assertEquals("{Movie Title} ({Release Year})",
                request.requestUrl!!.queryParameter("movieFolderFormat"))
            assertEquals("1", request.requestUrl!!.queryParameter("id"))
            assertEquals("radarr", request.requestUrl!!.queryParameter("resourceName"))
        } finally {
            server.shutdown()
        }
    }

    @Test fun uiConfigHappyPath() = runTest {
        val server = MockWebServer()
        try {
            server.enqueue(MockResponse().setResponseCode(200)
                .setBody(fixture("uiconfig", "uiConfig")))
            val api = RadarrSystemApis.uiConfig(retrofit(server))
            val result = api.uiConfig()
            assertEquals(0, result.firstDayOfWeek)
            assertEquals("auto", result.theme)
            val request = server.takeRequest()
            assertEquals("GET", request.method)
            assertEquals("/radarr/api/v3/config/ui", request.requestUrl!!.encodedPath)
        } finally {
            server.shutdown()
        }
    }

    @Test fun updateUiConfigHappyPath() = runTest {
        val server = MockWebServer()
        try {
            server.enqueue(MockResponse().setResponseCode(202)
                .setBody(fixture("uiconfig", "updateUiConfig")))
            val api = RadarrSystemApis.uiConfig(retrofit(server))
            val body = UiConfigResource(id = 1, firstDayOfWeek = 1)
            val result = api.updateUiConfig(id = "1", body = body)
            assertEquals(202, result.code())
            assertEquals(1, result.body()!!.firstDayOfWeek)
            val request = server.takeRequest()
            assertEquals("PUT", request.method)
            assertEquals("/radarr/api/v3/config/ui/1", request.requestUrl!!.encodedPath)
            assertEquals(body, ArrJson.decodeFromString<UiConfigResource>(
                request.body.readUtf8()))
        } finally {
            server.shutdown()
        }
    }

    @Test fun uiConfigByIdHappyPath() = runTest {
        val server = MockWebServer()
        try {
            server.enqueue(MockResponse().setResponseCode(200)
                .setBody(fixture("uiconfig", "uiConfigById")))
            val api = RadarrSystemApis.uiConfig(retrofit(server))
            val result = api.uiConfigById(id = 1)
            assertEquals(200, result.code())
            assertEquals(1, result.body()!!.id)
            val request = server.takeRequest()
            assertEquals("GET", request.method)
            assertEquals("/radarr/api/v3/config/ui/1", request.requestUrl!!.encodedPath)
        } finally {
            server.shutdown()
        }
    }

    @Test fun autoTaggingErrorPath() = runTest {
        val server = MockWebServer()
        try {
            server.enqueue(MockResponse().setResponseCode(503).setBody("{}"))
            val api = RadarrSystemApis.autoTagging(retrofit(server))
            assertEquals(503, api.autoTagging(id = 1).code())
        } finally {
            server.shutdown()
        }
    }

    @Test fun hostConfigErrorPath() = runTest {
        val server = MockWebServer()
        try {
            server.enqueue(MockResponse().setResponseCode(503).setBody("{}"))
            val api = RadarrSystemApis.hostConfig(retrofit(server))
            try {
                api.hostConfig()
                fail("Expected HTTP failure")
            } catch (error: HttpException) {
                assertEquals(503, error.code())
            }
        } finally {
            server.shutdown()
        }
    }

    @Test fun mediaManagementConfigErrorPath() = runTest {
        val server = MockWebServer()
        try {
            server.enqueue(MockResponse().setResponseCode(503).setBody("{}"))
            val api = RadarrSystemApis.mediaManagementConfig(retrofit(server))
            try {
                api.mediaManagementConfig()
                fail("Expected HTTP failure")
            } catch (error: HttpException) {
                assertEquals(503, error.code())
            }
        } finally {
            server.shutdown()
        }
    }

    @Test fun metadataErrorPath() = runTest {
        val server = MockWebServer()
        try {
            server.enqueue(MockResponse().setResponseCode(503).setBody("{}"))
            val api = RadarrSystemApis.metadata(retrofit(server))
            assertEquals(503, api.metadataById(id = 1).code())
        } finally {
            server.shutdown()
        }
    }

    @Test fun metadataConfigErrorPath() = runTest {
        val server = MockWebServer()
        try {
            server.enqueue(MockResponse().setResponseCode(503).setBody("{}"))
            val api = RadarrSystemApis.metadataConfig(retrofit(server))
            try {
                api.metadataConfig()
                fail("Expected HTTP failure")
            } catch (error: HttpException) {
                assertEquals(503, error.code())
            }
        } finally {
            server.shutdown()
        }
    }

    @Test fun namingConfigErrorPath() = runTest {
        val server = MockWebServer()
        try {
            server.enqueue(MockResponse().setResponseCode(503).setBody("{}"))
            val api = RadarrSystemApis.namingConfig(retrofit(server))
            try {
                api.namingConfig()
                fail("Expected HTTP failure")
            } catch (error: HttpException) {
                assertEquals(503, error.code())
            }
        } finally {
            server.shutdown()
        }
    }

    @Test fun uiConfigErrorPath() = runTest {
        val server = MockWebServer()
        try {
            server.enqueue(MockResponse().setResponseCode(503).setBody("{}"))
            val api = RadarrSystemApis.uiConfig(retrofit(server))
            try {
                api.uiConfig()
                fail("Expected HTTP failure")
            } catch (error: HttpException) {
                assertEquals(503, error.code())
            }
        } finally {
            server.shutdown()
        }
    }
}
