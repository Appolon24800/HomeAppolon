package dev.appolon.homelab.data

import android.webkit.CookieManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
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
 * The WebView cookie jar is global to the app, so the PocketID session
 * cookie stored there is offered automatically on every service's OIDC
 * redirect — that is the auto-login. The app is multi-account: each
 * identity's session cookies are persisted (encrypted) by [AccountStore],
 * and switching accounts swaps the cookie in and out of the jar.
 *
 *  - Passkeys (primary): native WebAuthn through Credential Manager —
 *    /api/webauthn/login/start returns the PublicKeyCredentialRequestOptions,
 *    /api/webauthn/login/finish consumes the assertion and sets the session.
 *  - One-time access codes (PocketID "alternative sign-in method"): the user
 *    generates a short code in the PocketID UI and types it here.
 *  - Web login: last-resort fallback for federated logins; the resulting
 *    cookie is adopted like any other session.
 */
object PocketId {

    /**
     * PocketID's real host on this deployment (the OIDC issuer the services
     * redirect to). The Homer config's "PocketID" entry (/auth) is stale — it
     * now 301s to OpenWebUI — so it is not trusted.
     */
    const val WELL_KNOWN_BASE_URL = "https://auth.appolon.dev"

    /**
     * Cookie names carrying a PocketID session. HTTPS deployments prefix
     * with __Host-/__Secure-; plain HTTP ones do not.
     */
    private val SESSION_COOKIE_NAMES =
        setOf("access_token", "__Host-access_token", "session", "__Host-session")

    private fun isSessionCookie(pair: String): Boolean =
        pair.substringBefore("=").trim() in SESSION_COOKIE_NAMES

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

    fun hasSession(baseUrl: String): Boolean = sessionCookies(baseUrl).isNotEmpty()

    /** PocketID profile picture for a user; the endpoint is public. */
    fun accountAvatarUrl(baseUrl: String, userId: String): String =
        "$baseUrl/api/users/$userId/profile-picture.png"

    /**
     * PocketID identifies the requesting device by User-Agent (shown on the
     * approving device and in login-audit emails), so send something human
     * instead of OkHttp's default "okhttp/x.y".
     */
    private val APP_USER_AGENT =
        "Homelab (${android.os.Build.MODEL}; Android ${android.os.Build.VERSION.RELEASE})"

    private fun Request.Builder.withAppUserAgent() = header("User-Agent", APP_USER_AGENT)

    /**
     * Extracts a "name=value" pair from a response's Set-Cookie headers where
     * the cookie name matches [name] exactly or ends with "-name" (the
     * __Host-/__Secure- prefixes https deployments add). Blank values are
     * ignored unless [allowBlank].
     */
    private fun findSetCookie(resp: okhttp3.Response, name: String, allowBlank: Boolean = false): String? =
        resp.headers("Set-Cookie").firstNotNullOfOrNull { line ->
            line.substringBefore(";").takeIf {
                val cookieName = it.substringBefore("=")
                (cookieName == name || cookieName.endsWith("-$name")) &&
                    (allowBlank || it.substringAfter("=").isNotBlank())
            }
        }

    /** PocketID's own error message when present, else [fallback]. */
    private fun serverErrorMessage(body: String, fallback: String): String =
        runCatching { json.decodeFromString<ErrorResponse>(body).error }.getOrNull() ?: fallback

    /** The signed-in identity, or null when the session is absent/expired. */
    suspend fun fetchUser(baseUrl: String): PocketIdUser? = withContext(Dispatchers.IO) {
        val cookie = CookieManager.getInstance().getCookie(baseUrl) ?: return@withContext null
        runCatching {
            val req = Request.Builder().url("$baseUrl/api/users/me")
                .withAppUserAgent()
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
     *
     * @param deviceCookie optional "device_token=..." from
     *   [requestOneTimeCodeEmail]; PocketID uses it to recognise the device
     *   in its login audit.
     */
    suspend fun signInWithOneTimeCode(baseUrl: String, code: String, deviceCookie: String? = null): PocketIdUser =
        withContext(Dispatchers.IO) {
            val trimmed = code.trim()
            require(trimmed.isNotEmpty()) { "Enter the code" }
            val builder = Request.Builder().url("$baseUrl/api/one-time-access-token/$trimmed")
                .withAppUserAgent()
                .post("".toRequestBody("application/json".toMediaType()))
            if (!deviceCookie.isNullOrBlank()) builder.header("Cookie", deviceCookie)
            http.newCall(builder.build()).execute().use { resp ->
                val body = resp.body.string()
                if (!resp.isSuccessful) {
                    throw OneTimeCodeException(
                        serverErrorMessage(
                            body,
                            if (resp.code == 401 || resp.code == 404) {
                                "Code invalid or expired — generate a new one"
                            } else {
                                "PocketID error (HTTP ${resp.code})"
                            }
                        )
                    )
                }
                val user = runCatching { json.decodeFromString<PocketIdUser>(body) }.getOrNull()
                    ?: throw OneTimeCodeException("Unexpected PocketID response")
                bridgeSessionCookies(baseUrl, resp)
                user
            }
        }

    /**
     * Asks PocketID to email the account a one-time access code. Always
     * succeeds with 204 (unknown emails are ignored to prevent probing).
     * Returns the "device_token=..." cookie to hand back when exchanging the
     * code, or null when the server did not set one.
     */
    suspend fun requestOneTimeCodeEmail(baseUrl: String, email: String): String? =
        withContext(Dispatchers.IO) {
            val trimmed = email.trim()
            require(trimmed.isNotEmpty()) { "Enter your email" }
            val payload = json.encodeToString(
                kotlinx.serialization.json.buildJsonObject {
                    put("email", kotlinx.serialization.json.JsonPrimitive(trimmed))
                }
            )
            val req = Request.Builder().url("$baseUrl/api/one-time-access-email")
                .withAppUserAgent()
                .post(payload.toRequestBody("application/json".toMediaType()))
                .build()
            http.newCall(req).execute().use { resp ->
                when {
                    resp.code == 204 -> findSetCookie(resp, "device_token")

                    resp.code == 429 -> throw OneTimeCodeException("Too many requests — wait a moment and try again")
                    resp.code == 400 -> throw OneTimeCodeException("That email address looks invalid")
                    else -> throw OneTimeCodeException("PocketID error (HTTP ${resp.code})")
                }
            }
        }

    /** The server's half of a passkey login: options JSON + session cookie. */
    data class PasskeyChallenge(
        /** PublicKeyCredentialRequestOptions JSON, ready for Credential Manager. */
        val requestJson: String,
        /** "__Host-session=..." that /finish requires to match /start. */
        val sessionCookie: String,
    )

    /**
     * Starts a native passkey login. The response body is already the
     * PublicKeyCredentialRequestOptions JSON Credential Manager expects.
     */
    suspend fun beginPasskeyLogin(baseUrl: String): PasskeyChallenge =
        withContext(Dispatchers.IO) {
            val req = Request.Builder().url("$baseUrl/api/webauthn/login/start").withAppUserAgent().get().build()
            http.newCall(req).execute().use { resp ->
                check(resp.isSuccessful) { "PocketID login start failed (HTTP ${resp.code})" }
                val sessionCookie = findSetCookie(resp, "session")
                    ?: error("PocketID did not start a login session")
                PasskeyChallenge(requestJson = resp.body.string(), sessionCookie = sessionCookie)
            }
        }

    /**
     * Completes a passkey login with the assertion Credential Manager produced.
     * On success the access-token cookie is bridged into the WebView jar.
     */
    suspend fun finishPasskeyLogin(
        baseUrl: String,
        challenge: PasskeyChallenge,
        assertionJson: String,
    ): PocketIdUser = withContext(Dispatchers.IO) {
        val req = Request.Builder().url("$baseUrl/api/webauthn/login/finish")
            .withAppUserAgent()
            .header("Cookie", challenge.sessionCookie)
            .post(assertionJson.toRequestBody("application/json".toMediaType()))
            .build()
        http.newCall(req).execute().use { resp ->
            val body = resp.body.string()
            if (!resp.isSuccessful) {
                throw PasskeyException(
                    serverErrorMessage(body, "Passkey sign-in failed (HTTP ${resp.code})")
                )
            }
            val user = runCatching { json.decodeFromString<PocketIdUser>(body) }.getOrNull()
                ?: throw PasskeyException("Unexpected PocketID response")
            bridgeSessionCookies(baseUrl, resp)
            user
        }
    }

    @Serializable
    private data class ErrorResponse(val error: String? = null)

    /** The PocketID session cookies currently in the WebView jar, if any. */
    fun sessionCookies(baseUrl: String): List<String> {
        if (baseUrl.isBlank()) return emptyList()
        val cookie = CookieManager.getInstance().getCookie(baseUrl) ?: return emptyList()
        return cookie.split(";").map { it.trim() }.filter(::isSessionCookie)
    }

    /** Writes stored "name=value" cookies for [baseUrl] into the WebView jar. */
    fun installSessionCookies(baseUrl: String, cookies: List<String>) {
        val cm = CookieManager.getInstance()
        val url = baseUrl.removeSuffix("/")
        cookies.forEach { runCatching { cm.setCookie(url, it) } }
        cm.flush()
    }

    /**
     * Empties the whole WebView cookie jar. Sessions for services (created by
     * their OIDC redirects) belong to whichever PocketID identity was active,
     * so switching accounts wipes them and lets each service re-authenticate.
     *
     * removeAllCookies needs a running Looper (main), but blocking main on its
     * callback deadlocks — so call it on main and *suspend* until the callback
     * resumes us; the looper stays free to deliver it.
     */
    suspend fun wipeCookieJar(): Boolean = withContext(Dispatchers.Main.immediate) {
        val cm = CookieManager.getInstance()
        val removed = kotlinx.coroutines.withTimeoutOrNull(5_000) {
            suspendCancellableCoroutine { cont ->
                cm.removeAllCookies { ok ->
                    if (cont.isActive) cont.resume(ok) { _ -> }
                }
            }
        }
        // Timeout means the WebView provider never called back; flush anyway.
        cm.flush()
        removed == true
    }

    /**
     * Device login ("sign in with other device"): PocketID v2's flow where
     * another already-authenticated device approves this one by code/QR.
     */
    @Serializable
    data class DeviceLoginRequest(
        val id: String,
        @SerialName("userCode") val userCode: String,
        val verificationUri: String,
        // Deployed instances spell this key both ways across versions.
        @SerialName("verificationURIComplete")
        @kotlinx.serialization.json.JsonNames("verificationUriComplete")
        val verificationUriComplete: String,
        val expiresAt: String? = null,
        val interval: Int = 5,
    )

    /** A created device-login request plus its exchange cookie. */
    data class DeviceLoginSession(
        val request: DeviceLoginRequest,
        /** "__Secure-device_login_token=..." that the exchange must send. */
        val loginCookie: String,
    )

    class DeviceLoginException(message: String) : Exception(message)

    /** Starts a device login; the code/QR shown to the user comes back with it. */
    suspend fun beginDeviceLogin(baseUrl: String): DeviceLoginSession =
        withContext(Dispatchers.IO) {
            val req = Request.Builder().url("$baseUrl/api/device-login/requests")
                .withAppUserAgent()
                .post("".toRequestBody("application/json".toMediaType()))
                .build()
            http.newCall(req).execute().use { resp ->
                check(resp.isSuccessful) { "PocketID device login failed (HTTP ${resp.code})" }
                val cookie = findSetCookie(resp, "device_login_token")
                    ?: error("PocketID did not start a device login session")
                val body = resp.body.string()
                val request = runCatching { json.decodeFromString<DeviceLoginRequest>(body) }
                    .onFailure {
                        android.util.Log.w(
                            "PocketId",
                            "device-login decode failed: ${it.message} body=${body.take(300)}",
                        )
                    }
                    .getOrNull()
                    ?: error("Unexpected PocketID response")
                DeviceLoginSession(request, cookie)
            }
        }

    /**
     * Long-polls the exchange endpoint. Returns the signed-in user once the
     * other device approved, null while still pending (HTTP 202), and throws
     * [DeviceLoginException] on denial/expiry. Cancellation aborts the call.
     */
    suspend fun awaitDeviceLogin(
        baseUrl: String,
        session: DeviceLoginSession,
    ): PocketIdUser? = suspendCancellableCoroutine { cont ->
        // The server blocks until a decision; allow generous time before
        // treating a silent timeout as "still pending".
        val longPollClient = http.newBuilder()
            .readTimeout(120, TimeUnit.SECONDS)
            .build()
        val req = Request.Builder()
            .url("$baseUrl/api/device-login/requests/${session.request.id}/exchange")
            .withAppUserAgent()
            .header("Cookie", session.loginCookie)
            .post("".toRequestBody("application/json".toMediaType()))
            .build()
        val call = longPollClient.newCall(req)
        cont.invokeOnCancellation { call.cancel() }
        call.enqueue(
            object : okhttp3.Callback {
                override fun onFailure(call: okhttp3.Call, e: java.io.IOException) {
                    if (cont.isCancelled) return
                    // Read timeout with no decision: report pending.
                    cont.resume(null) { _ -> }
                }

                override fun onResponse(call: okhttp3.Call, resp: okhttp3.Response) {
                    resp.use {
                        when {
                            resp.code == 202 -> cont.resume(null) { _ -> }
                            resp.isSuccessful -> {
                                val user = runCatching {
                                    json.decodeFromString<PocketIdUser>(resp.body.string())
                                }.getOrNull()
                                if (user == null) {
                                    cont.resumeWith(Result.failure(DeviceLoginException("Unexpected PocketID response")))
                                } else {
                                    bridgeSessionCookies(baseUrl, resp)
                                    cont.resume(user) { _ -> }
                                }
                            }

                            else -> {
                                val message = serverErrorMessage(
                                    resp.body.string(),
                                    "Device login failed (HTTP ${resp.code})"
                                )
                                cont.resumeWith(Result.failure(DeviceLoginException(message)))
                            }
                        }
                    }
                }
            }
        )
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

    class OneTimeCodeException(message: String) : Exception(message)
    class PasskeyException(message: String) : Exception(message)
}
