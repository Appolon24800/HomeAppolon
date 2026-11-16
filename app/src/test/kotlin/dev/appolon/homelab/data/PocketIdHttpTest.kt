package dev.appolon.homelab.data

import java.net.SocketException
import java.net.SocketTimeoutException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class PocketIdHttpTest {
    private val server = MockWebServer()
    private val saved = mutableListOf<List<String>>()
    private lateinit var api: PocketIdHttp

    @Before
    fun setUp() {
        server.start()
        api = PocketIdHttp(
            http = OkHttpClient(),
            userAgent = "test-agent",
            readCookies = { null },
            saveCookies = { _, cookies -> saved += cookies },
        )
    }

    @After
    fun tearDown() = server.shutdown()

    @Test
    fun `passkey start accepts host session cookie and finish stores access cookie`() = runBlocking {
        server.enqueue(
            MockResponse()
                .setHeader("Set-Cookie", "__Host-session=challenge; Path=/; Secure")
                .setBody("{\"challenge\":\"abc\",\"rpId\":\"auth.example\",\"timeout\":60000,\"userVerification\":\"preferred\"}"),
        )
        server.enqueue(
            MockResponse()
                .setHeader("Set-Cookie", "__Host-access_token=token; Path=/; Secure")
                .setBody("{\"id\":\"u1\",\"username\":\"alice\"}"),
        )

        val base = server.url("/").toString().trimEnd('/')
        val challenge = api.beginPasskeyLogin(base)
        val user = api.finishPasskeyLogin(base, challenge, "{\"id\":\"assertion\"}")

        assertEquals("__Host-session=challenge", challenge.sessionCookie)
        assertEquals("u1", user.id)
        assertEquals(listOf("__Host-access_token=token; Path=/; Secure"), saved.single())
        assertEquals("/api/webauthn/login/start", server.takeRequest().path)
        val finish = server.takeRequest()
        assertEquals("/api/webauthn/login/finish", finish.path)
        assertEquals("__Host-session=challenge", finish.getHeader("Cookie"))
    }

    @Test
    fun `one-time code reports server errors without storing a session`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(401).setBody("{\"error\":\"expired\"}"))
        val error = runCatching {
            api.signInWithOneTimeCode(server.url("/").toString(), "ABC")
        }.exceptionOrNull()
        assertTrue(error is PocketId.OneTimeCodeException)
        assertEquals("expired", error?.message)
        assertTrue(saved.isEmpty())
    }

    @Test
    fun `device login 202 remains pending and successful exchange stores session`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(202))
        server.enqueue(
            MockResponse()
                .setHeader("Set-Cookie", "__Host-access_token=token; Path=/; Secure")
                .setBody("{\"id\":\"u1\",\"username\":\"alice\"}"),
        )
        val base = server.url("/").toString()
        val session = PocketId.DeviceLoginSession(
            PocketId.DeviceLoginRequest("r1", "ABCD", "https://x/device", "https://x/device?user_code=ABCD", interval = 1),
            "__Secure-device_login_token=req",
        )
        assertEquals(null, api.awaitDeviceLogin(base, session))
        assertEquals("u1", api.awaitDeviceLogin(base, session)?.id)
    }

    @Test
    fun `successful user response without an access cookie is rejected`() = runBlocking {
        server.enqueue(MockResponse().setBody("{\"id\":\"u1\",\"username\":\"alice\"}"))
        val error = runCatching { api.signInWithOneTimeCode(server.url("/").toString(), "ABC") }.exceptionOrNull()
        assertTrue(error is PocketId.OneTimeCodeException)
        assertTrue(saved.isEmpty())
    }

    @Test
    fun `code is encoded as a single path segment beneath a server prefix`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(401))
        runCatching { api.signInWithOneTimeCode(server.url("/pocket/").toString(), "A/B?x#y") }
        assertEquals("/pocket/api/one-time-access-token/A%2FB%3Fx%23y", server.takeRequest().path)
    }

    @Test
    fun `device transport errors are reported while long-poll timeout remains pending`() = runBlocking {
        val session = deviceSession()
        for (failure in listOf(SocketException("offline"), SocketTimeoutException("pending"))) {
            val broken = PocketIdHttp(
                OkHttpClient.Builder().addInterceptor { throw failure }.build(), "test", { null },
                { _, cookies -> saved += cookies },
            )
            val result = runCatching { broken.awaitDeviceLogin(server.url("/").toString(), session) }
            if (failure is SocketTimeoutException) {
                assertTrue(result.isSuccess)
                assertEquals(null, result.getOrNull())
            } else {
                assertTrue(result.exceptionOrNull() is SocketException)
            }
        }
        assertTrue(saved.isEmpty())
    }

    @Test
    fun `cancelled exchange stops the call without saving late cookies`() = runBlocking {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
        val task = async(kotlinx.coroutines.Dispatchers.IO) {
            api.awaitDeviceLogin(server.url("/").toString(), deviceSession())
        }
        assertEquals("/api/device-login/requests/r1/exchange", server.takeRequest(5, TimeUnit.SECONDS)?.path)
        task.cancelAndJoin()
        assertTrue(saved.isEmpty())
    }

    @Test
    fun `authentication redirect does not forward cookies or adopt another endpoint`() = runBlocking {
        val redirected = MockWebServer()
        redirected.start()
        try {
            server.enqueue(MockResponse().setResponseCode(302).setHeader("Location", redirected.url("/login")))
            val result = runCatching { api.signInWithOneTimeCode(server.url("/").toString(), "ABC", "device_token=secret") }
            assertTrue(result.exceptionOrNull() is PocketId.OneTimeCodeException)
            assertEquals(0, redirected.requestCount)
            assertTrue(saved.isEmpty())
        } finally {
            redirected.shutdown()
        }
    }

    private fun deviceSession() = PocketId.DeviceLoginSession(
        PocketId.DeviceLoginRequest("r1", "ABCD", "https://x/device", "https://x/device?user_code=ABCD"),
        "device_login_token=req",
    )
}
