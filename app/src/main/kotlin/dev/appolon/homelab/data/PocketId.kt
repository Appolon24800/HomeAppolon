package dev.appolon.homelab.data

import android.webkit.CookieManager

/**
 * App-local PocketID session management. The app's WebView cookie jar is
 * global, so a PocketID session established once is offered automatically on
 * every service's OIDC redirect — that is the auto-login. Sign-in/out/switch
 * therefore reduce to managing that session cookie.
 */
object PocketId {

    /** Session cookie names PocketID has used across versions. */
    private val SESSION_COOKIES = listOf("session", "pocketid_session", "session_token")

    /**
     * PocketID's real host on this deployment (the OIDC issuer the services
     * redirect to). The Homer config's "PocketID" entry (/auth) is stale — it
     * now 301s to OpenWebUI — so it is not trusted.
     */
    const val WELL_KNOWN_BASE_URL = "https://auth.appolon.dev"

    /** True when a session cookie for PocketID exists in the app's cookie jar. */
    fun hasSession(baseUrl: String): Boolean {
        if (baseUrl.isBlank()) return false
        val cookie = CookieManager.getInstance().getCookie(baseUrl) ?: return false
        return cookie.split(";").any { part ->
            val name = part.trim().substringBefore('=')
            name in SESSION_COOKIES
        }
    }

    /**
     * Expires the session cookies (app-local sign-out; services keep their own
     * sessions, but the next OIDC login will require fresh PocketID auth).
     */
    fun clearSession(baseUrl: String, onDone: () -> Unit = {}) {
        val cm = CookieManager.getInstance()
        for (name in SESSION_COOKIES) {
            cm.setCookie(baseUrl, "$name=; Path=/; Max-Age=0")
        }
        cm.flush()
        onDone()
    }
}
