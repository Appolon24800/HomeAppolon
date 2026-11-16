package dev.appolon.homelab.data

import android.webkit.CookieManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNames
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

/** PocketID sessions shared by native login, saved accounts, and the WebView. */
object PocketId {
    const val WELL_KNOWN_BASE_URL = "https://auth.appolon.dev"

    @Serializable
    data class PocketIdUser(
        val id: String,
        val username: String,
        val email: String? = null,
        val displayName: String? = null,
        val isAdmin: Boolean = false,
    )

    data class PasskeyChallenge(val requestJson: String, val sessionCookie: String)

    @OptIn(ExperimentalSerializationApi::class)
    @Serializable
    data class DeviceLoginRequest(
        val id: String,
        @SerialName("userCode") val userCode: String,
        val verificationUri: String,
        @SerialName("verificationURIComplete")
        @JsonNames("verificationUriComplete")
        val verificationUriComplete: String,
        val expiresAt: String? = null,
        val interval: Int = 5,
    )

    data class DeviceLoginSession(val request: DeviceLoginRequest, val loginCookie: String)

    class DeviceLoginException(message: String) : Exception(message)
    class OneTimeCodeException(message: String) : Exception(message)
    class PasskeyException(message: String) : Exception(message)

    private val api by lazy {
        PocketIdHttp(
            http = Network.client.newBuilder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                // An authentication response must come from the configured
                // endpoint. Do not forward explicit cookies through redirects.
                .followRedirects(false)
                .followSslRedirects(false)
                .build(),
            userAgent = "Homelab (${android.os.Build.MODEL}; Android ${android.os.Build.VERSION.RELEASE})",
            readCookies = { CookieManager.getInstance().getCookie(it) },
            saveCookies = ::writeCookies,
        )
    }

    fun hasSession(baseUrl: String): Boolean = sessionCookies(baseUrl).any(PocketIdCookies::isAccessCookie)

    fun accountAvatarUrl(baseUrl: String, userId: String): String =
        "${baseUrl.trimEnd('/')}/api/users/$userId/profile-picture.png"

    suspend fun fetchUser(baseUrl: String): PocketIdUser? = api.fetchUser(baseUrl)

    suspend fun signInWithOneTimeCode(baseUrl: String, code: String, deviceCookie: String? = null): PocketIdUser =
        api.signInWithOneTimeCode(baseUrl, code, deviceCookie)

    suspend fun requestOneTimeCodeEmail(baseUrl: String, email: String): String? =
        api.requestOneTimeCodeEmail(baseUrl, email)

    suspend fun beginPasskeyLogin(baseUrl: String): PasskeyChallenge = api.beginPasskeyLogin(baseUrl)

    suspend fun finishPasskeyLogin(baseUrl: String, challenge: PasskeyChallenge, assertionJson: String): PocketIdUser =
        api.finishPasskeyLogin(baseUrl, challenge, assertionJson)

    suspend fun beginDeviceLogin(baseUrl: String): DeviceLoginSession = api.beginDeviceLogin(baseUrl)

    suspend fun awaitDeviceLogin(baseUrl: String, session: DeviceLoginSession): PocketIdUser? =
        api.awaitDeviceLogin(baseUrl, session)

    /** Only nonempty authentication cookies belong to a saved account. */
    fun sessionCookies(baseUrl: String): List<String> {
        if (baseUrl.isBlank()) return emptyList()
        return PocketIdCookies.sessionPairs(CookieManager.getInstance().getCookie(baseUrl))
    }

    /** Restores the attributes required for prefixed cookies after account switching. */
    suspend fun installSessionCookies(baseUrl: String, cookies: List<String>) {
        writeCookies(baseUrl, PocketIdCookies.restoreHeaders(baseUrl, cookies))
    }

    /** Waits for WebView to accept the cookies before saving the signed-in account. */
    private suspend fun writeCookies(baseUrl: String, cookies: List<String>) = withContext(Dispatchers.Main.immediate) {
        val manager = CookieManager.getInstance()
        val url = baseUrl.trimEnd('/')
        withTimeout(5_000) {
            cookies.forEach { cookie ->
                val accepted = suspendCancellableCoroutine { continuation ->
                    manager.setCookie(url, cookie) { accepted ->
                        if (continuation.isActive) continuation.resume(accepted)
                    }
                }
                check(accepted) { "Could not save the PocketID session on this device" }
            }
        }
        manager.flush()
    }

    /** Clears service sessions before installing another account's identity. */
    suspend fun wipeCookieJar(): Boolean = withContext(Dispatchers.Main.immediate) {
        val manager = CookieManager.getInstance()
        val removed = withTimeout(5_000) {
            suspendCancellableCoroutine { continuation ->
                manager.removeAllCookies { removed ->
                    if (continuation.isActive) continuation.resume(removed)
                }
            }
        }
        manager.flush()
        removed
    }
}
