package dev.appolon.homelab.data

import dev.appolon.homelab.data.PocketId.DeviceLoginException
import dev.appolon.homelab.data.PocketId.DeviceLoginRequest
import dev.appolon.homelab.data.PocketId.DeviceLoginSession
import dev.appolon.homelab.data.PocketId.OneTimeCodeException
import dev.appolon.homelab.data.PocketId.PasskeyChallenge
import dev.appolon.homelab.data.PocketId.PasskeyException
import dev.appolon.homelab.data.PocketId.PocketIdUser
import java.io.IOException
import java.net.SocketTimeoutException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response

/** PocketID HTTP requests with an injectable session-cookie store. */
internal class PocketIdHttp(
    http: OkHttpClient,
    private val userAgent: String,
    private val readCookies: (String) -> String?,
    private val saveCookies: suspend (String, List<String>) -> Unit,
) {
    private val http = http.newBuilder().followRedirects(false).followSslRedirects(false).build()
    private val json = Json { ignoreUnknownKeys = true }
    private val longPollHttp = this.http.newBuilder().readTimeout(120, TimeUnit.SECONDS).build()

    suspend fun fetchUser(baseUrl: String): PocketIdUser? = withContext(Dispatchers.IO) {
        val cookie = readCookies(baseUrl)?.takeIf { it.isNotBlank() } ?: return@withContext null
        try {
            execute(request(baseUrl, "users", "me").header("Cookie", cookie).build()).use {
                if (it.code == 401 || it.code == 403) return@use null
                check(it.isSuccessful) { "PocketID session check failed. HTTP ${it.code}" }
                json.decodeFromString<PocketIdUser>(it.body.string())
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
    }

    suspend fun signInWithOneTimeCode(baseUrl: String, code: String, deviceCookie: String? = null): PocketIdUser =
        withContext(Dispatchers.IO) {
            require(code.isNotBlank()) { "Enter the code" }
            val builder = request(baseUrl, "one-time-access-token", code.trim()).post(emptyBody())
            deviceCookie?.takeIf { it.isNotBlank() }?.let { builder.header("Cookie", it) }
            execute(builder.build()).use { response ->
                authenticatedUser(baseUrl, response, ::OneTimeCodeException)
            }
        }

    suspend fun requestOneTimeCodeEmail(baseUrl: String, email: String): String? = withContext(Dispatchers.IO) {
        require(email.isNotBlank()) { "Enter your email" }
        val payload = buildJsonObject { put("email", JsonPrimitive(email.trim())) }.toString()
        val req = request(baseUrl, "one-time-access-email").post(payload.toRequestBody(JSON)).build()
        execute(req).use { response ->
            when (response.code) {
                204 -> findCookie(response, "device_token")
                400 -> throw OneTimeCodeException("That email address looks invalid")
                429 -> throw OneTimeCodeException("Too many requests. Wait a moment and try again")
                else -> throw OneTimeCodeException(errorMessage(response))
            }
        }
    }

    suspend fun beginPasskeyLogin(baseUrl: String): PasskeyChallenge = withContext(Dispatchers.IO) {
        execute(request(baseUrl, "webauthn", "login", "start").get().build()).use { response ->
            if (!response.isSuccessful) throw PasskeyException(errorMessage(response))
            val cookie = findCookie(response, "session") ?: throw PasskeyException("PocketID did not start a login session")
            PasskeyChallenge(response.body.string(), cookie)
        }
    }

    suspend fun finishPasskeyLogin(baseUrl: String, challenge: PasskeyChallenge, assertionJson: String): PocketIdUser =
        withContext(Dispatchers.IO) {
            val req = request(baseUrl, "webauthn", "login", "finish")
                .header("Cookie", challenge.sessionCookie)
                .post(assertionJson.toRequestBody(JSON)).build()
            execute(req).use { authenticatedUser(baseUrl, it, ::PasskeyException) }
        }

    suspend fun beginDeviceLogin(baseUrl: String): DeviceLoginSession = withContext(Dispatchers.IO) {
        execute(request(baseUrl, "device-login", "requests").post(emptyBody()).build()).use { response ->
            if (!response.isSuccessful) throw DeviceLoginException(errorMessage(response))
            val cookie = findCookie(response, "device_login_token")
                ?: throw DeviceLoginException("PocketID did not start a device login session")
            val request = try {
                json.decodeFromString<DeviceLoginRequest>(response.body.string())
            } catch (_: Exception) {
                throw DeviceLoginException("Unexpected PocketID response")
            }
            DeviceLoginSession(request, cookie)
        }
    }

    suspend fun awaitDeviceLogin(baseUrl: String, session: DeviceLoginSession): PocketIdUser? = withContext(Dispatchers.IO) {
        val req = request(baseUrl, "device-login", "requests", session.request.id, "exchange")
            .header("Cookie", session.loginCookie).post(emptyBody()).build()
        val response = try {
            execute(req, longPollHttp)
        } catch (_: SocketTimeoutException) {
            return@withContext null
        }
        response.use {
            if (it.code == 202) null else authenticatedUser(baseUrl, it, ::DeviceLoginException)
        }
    }

    private suspend fun authenticatedUser(
        baseUrl: String,
        response: Response,
        error: (String) -> Exception,
    ): PocketIdUser {
        if (!response.isSuccessful) throw error(errorMessage(response))
        val user = try {
            json.decodeFromString<PocketIdUser>(response.body.string())
        } catch (_: Exception) {
            throw error("Unexpected PocketID response")
        }
        if (findCookie(response, "access_token") == null) {
            throw error("PocketID did not return a session. Sign in again")
        }
        kotlin.coroutines.coroutineContext.ensureActive()
        saveCookies(baseUrl, response.headers("Set-Cookie"))
        return user
    }

    private fun request(baseUrl: String, vararg segments: String): Request.Builder {
        val url = baseUrl.trimEnd('/').toHttpUrl().newBuilder().apply {
            addPathSegment("api")
            segments.forEach(::addPathSegment)
        }.build()
        return Request.Builder().url(url).header("User-Agent", userAgent)
    }

    private fun emptyBody() = "".toRequestBody(JSON)

    private fun findCookie(response: Response, name: String): String? =
        response.headers("Set-Cookie").firstNotNullOfOrNull { header ->
            val pair = header.substringBefore(';').trim()
            val cookieName = pair.substringBefore('=')
            pair.takeIf {
                cookieName in setOf(name, "__Host-$name", "__Secure-$name") &&
                    pair.substringAfter('=', "").isNotBlank()
            }
        }

    @Serializable
    private data class ErrorResponse(val error: String? = null)

    private fun errorMessage(response: Response): String =
        runCatching { json.decodeFromString<ErrorResponse>(response.body.string()).error }
            .getOrNull()?.takeIf { it.isNotBlank() } ?: "PocketID request failed. HTTP ${response.code}"

    private suspend fun execute(request: Request, client: OkHttpClient = http): Response =
        suspendCancellableCoroutine { continuation ->
            val call = client.newCall(request)
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (!continuation.isCancelled) continuation.resumeWithException(e)
                }

                override fun onResponse(call: Call, response: Response) {
                    continuation.resume(response) { _, resource, _ -> resource.close() }
                }
            })
        }

    private companion object {
        val JSON = "application/json".toMediaType()
    }
}
