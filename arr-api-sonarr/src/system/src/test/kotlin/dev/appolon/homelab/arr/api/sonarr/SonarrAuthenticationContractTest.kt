package dev.appolon.homelab.arr.api.sonarr

import dev.appolon.homelab.arr.net.ArrJson
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

class SonarrAuthenticationContractTest {
    private val server = MockWebServer()
    private lateinit var api: AuthenticationApi

    @Before
    fun setUp() {
        server.start()
        api = SonarrSystemApis.authentication(
            Retrofit.Builder().baseUrl(server.url("/sonarr/"))
                .addConverterFactory(ArrJson.asConverterFactory("application/json".toMediaType()))
                .build()
        )
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun form(): MultipartBody = MultipartBody.Builder()
        .setType(MultipartBody.FORM)
        .addFormDataPart("username", "user")
        .addFormDataPart("password", "secret")
        .build()

    @Test
    fun ssoHappyPath() = runTest {
        server.enqueue(MockResponse().setResponseCode(302))
        assertEquals(302, api.sso(returnUrl = "/sonarr/series").code())
        val request = server.takeRequest()
        assertEquals("GET", request.method)
        assertEquals("/sonarr/login/sso", request.requestUrl!!.encodedPath)
        assertEquals("/sonarr/series", request.requestUrl!!.queryParameter("returnUrl"))
    }

    @Test
    fun loginHappyPath() = runTest {
        server.enqueue(MockResponse().setResponseCode(302))
        val response: Response<Unit> = api.login(returnUrl = "/sonarr/", body = form())
        assertEquals(302, response.code())
        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/sonarr/login", request.requestUrl!!.encodedPath)
        assertEquals("multipart/form-data", request.headers["Content-Type"]!!.substringBefore(';'))
    }

    @Test
    fun loginUnauthorized() = runTest {
        server.enqueue(MockResponse().setResponseCode(401).setBody("{}"))
        val response = api.login(body = form())
        assertEquals(401, response.code())
    }

    @Test
    fun requestPasswordResetHappyPath() = runTest {
        server.enqueue(MockResponse().setResponseCode(302))
        assertEquals(302, api.requestPasswordReset().code())
        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/sonarr/reset-password/request", request.requestUrl!!.encodedPath)
    }

    @Test
    fun resetPasswordHappyPath() = runTest {
        server.enqueue(MockResponse().setResponseCode(302))
        assertEquals(302, api.resetPassword(body = form()).code())
        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/sonarr/reset-password", request.requestUrl!!.encodedPath)
    }

    @Test
    fun logoutHappyPath() = runTest {
        server.enqueue(MockResponse().setResponseCode(302))
        assertEquals(302, api.logout().code())
        val request = server.takeRequest()
        assertEquals("GET", request.method)
        assertEquals("/sonarr/logout", request.requestUrl!!.encodedPath)
    }

    @Test
    fun httpError() = runTest {
        server.enqueue(MockResponse().setResponseCode(500).setBody("{}"))
        val response = api.login(body = form())
        assertEquals(500, response.code())
    }
}
