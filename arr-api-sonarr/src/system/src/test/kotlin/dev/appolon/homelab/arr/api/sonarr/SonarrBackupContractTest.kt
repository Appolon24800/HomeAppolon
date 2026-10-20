package dev.appolon.homelab.arr.api.sonarr

import dev.appolon.homelab.arr.api.sonarr.model.BackupResource
import dev.appolon.homelab.arr.api.sonarr.model.BackupType
import dev.appolon.homelab.arr.net.ArrJson
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.decodeFromString
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

@Suppress("DEPRECATION")
class SonarrBackupContractTest {
    private val server = MockWebServer()
    private lateinit var api: BackupApi

    @Before
    fun setUp() {
        server.start()
        api = SonarrSystemApis.backup(
            Retrofit.Builder().baseUrl(server.url("/sonarr/"))
                .addConverterFactory(ArrJson.asConverterFactory("application/json".toMediaType()))
                .build()
        )
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun fixture(path: String): String =
        checkNotNull(javaClass.classLoader!!.getResource(path)).readText()

    @Test
    fun listHappyPath() = runTest {
        server.enqueue(
            MockResponse().setBody(fixture("fixtures/sonarr/backup/list.json"))
        )
        val expected = ArrJson.decodeFromString<List<BackupResource>>(
            fixture("fixtures/sonarr/backup/list.json")
        )
        assertEquals(BackupType.SCHEDULED, expected[0].type)
        assertEquals(expected, api.list())
        val request = server.takeRequest()
        assertEquals("GET", request.method)
        assertEquals("/sonarr/api/v3/system/backup", request.requestUrl!!.encodedPath)
    }

    @Test
    fun deleteHappyPath() = runTest {
        server.enqueue(MockResponse().setResponseCode(200))
        api.delete(2)
        val request = server.takeRequest()
        assertEquals("DELETE", request.method)
        assertEquals("/sonarr/api/v3/system/backup/2", request.requestUrl!!.encodedPath)
    }

    @Test
    fun restoreHappyPath() = runTest {
        server.enqueue(MockResponse().setResponseCode(200))
        api.restore(1)
        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/sonarr/api/v3/system/backup/restore/1", request.requestUrl!!.encodedPath)
    }

    @Test
    fun restoreUploadHappyPath() = runTest {
        server.enqueue(MockResponse().setResponseCode(200))
        api.restoreUpload()
        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/sonarr/api/v3/system/backup/restore/upload", request.requestUrl!!.encodedPath)
    }

    @Test
    fun httpError() = runTest {
        server.enqueue(MockResponse().setResponseCode(500).setBody("{}"))
        try {
            api.list()
            fail("Expected HTTP failure")
        } catch (error: HttpException) {
            assertEquals(500, error.code())
        }
    }

    @Test
    fun unknownTypeEnumCoercesToNull() = runTest {
        server.enqueue(MockResponse().setBody("""[{"id": 9, "type": "mystery"}]"""))
        val backups = api.list()
        assertEquals(9, backups[0].id)
        assertEquals(null, backups[0].type)
        assertTrue(server.takeRequest().path!!.endsWith("/api/v3/system/backup"))
    }
}
