package dev.appolon.homelab.data

import android.webkit.CookieManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * PocketID session management for the app.
 *
 * The WebView cookie jar is global to the app, so a PocketID session stored
 * there is offered automatically on every service's OIDC redirect — that is
 * the auto-login. Sign-in/out/switch reduce to managing that cookie:
 *
 *  - One-time access codes (PocketID "alternative sign-in method"): the user
 *    generates a short code in the PocketID UI and types it here; the app
 *    exchanges it for a session and bridges the cookie into the jar.
 *  - Passkeys: the WebView has no WebAuthn support (NotSupportedError), so
 *    the ceremony runs natively through Credential Manager when available.
 */
object PocketId {

    /**
     * PocketID's real host on this deployment (the OIDC issuer the services
     * redirect to). The Homer config's "PocketID" entry (/auth) is stale — it
     * now 301s to OpenWebUI — so it is not trusted.
     */
    const val WELL_KNOWN_BASE_URL = "https://auth.appolon.dev"

    /** v2 session cookie; on https deployments the __Host- prefix is added. */
    private val SESSION_COOKIE_SUFFIXES = listOf("access_token=", "session=")

    @Serializable
    data class PocketIdUser(
        val id: String,
        val username: String,
        val email: String? = null,
        val displayName: String? = null,
        val isAdmin: Boolean = false,
    )

    private val json = Json { ignoreUnknownKeys = true }

    private val http: OkHttpClient = Network.client.newBuilder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    fun hasSession(baseUrl: String): Boolean {
        if (baseUrl.isBlank()) return false
        val cookie = CookieManager.getInstance().getCookie(baseUrl) ?: return false
        return cookie.split(";").any { part ->
            val name = part.trim()
            SESSION_COOKIE_SUFFIXES.any { suffix ->
                // __Host-access_token=... and access_token=... both qualify.
                name.endsWith(suffix) || name.startsWith(suffix)
            }
        }
    }

    /** The signed-in identity, or null when the session is absent/expired. */
    suspend fun fetchUser(baseUrl: String): PocketIdUser? = withContext(Dispatchers.IO) {
        val cookie = CookieManager.getInstance().getCookie(baseUrl) ?: return@withContext null
        runCatching {
            val req = Request.Builder().url("$baseUrl/api/users/me")
                .header("Cookie", cookie)
                .build()
            http.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@use null
                json.decodeFromString<PocketIdUser>(resp.body.string())
            }
        }.getOrNull()
    }

    /**
     * Exchanges a one-time access code for a session. On success the session
     * cookie is bridged into the app's WebView cookie jar and the user is
     * returned; invalid/expired codes throw [OneTimeCodeException].
     */
    suspend fun signInWithOneTimeCode(baseUrl: String, code: String): PocketIdUser =
        withContext(Dispatchers.IO) {
            val trimmed = code.trim()
            require(trimmed.isNotEmpty()) { "Enter the code" }
            val req = Request.Builder().url("$baseUrl/api/one-time-access-token/$trimmed")
                .post("".toRequestBody("application/json".toMediaType()))
                .build()
            http.newCall(req).execute().use { resp ->
                val body = resp.body.string()
                if (resp.code == 401 || resp.code == 404) {
                    throw OneTimeCodeException("Code invalid or expired — generate a new one")
                }
                if (!resp.isSuccessful) {
                    throw OneTimeCodeException("PocketID error (HTTP ${resp.code})")
                }
                val user = runCatching { json.decodeFromString<PocketIdUser>(body) }.getOrNull()
                    ?: throw OneTimeCodeException("Unexpected PocketID response")
                bridgeSessionCookies(baseUrl, resp)
                user
            }
        }

    /** Copies Set-Cookie headers from the response into the WebView cookie jar. */
    private fun bridgeSessionCookies(baseUrl: String, resp: okhttp3.Response) {
        val cm = CookieManager.getInstance()
        val url = baseUrl.removeSuffix("/")
        for (cookieLine in resp.headers("Set-Cookie")) {
            runCatching { cm.setCookie(url, cookieLine) }
        }
        cm.flush()
    }

    /** Expires the session cookie in the WebView jar (app-local sign-out). */
    fun clearSession(baseUrl: String, onDone: () -> Unit = {}) {
        val cm = CookieManager.getInstance()
        cm.setCookie(baseUrl, "access_token=; Path=/; Max-Age=0")
        cm.setCookie(baseUrl, "__Host-access_token=; Path=/; Max-Age=0")
        cm.setCookie(baseUrl, "session=; Path=/; Max-Age=0")
        cm.flush()
        onDone()
    }

    class OneTimeCodeException(message: String) : Exception(message)
}
