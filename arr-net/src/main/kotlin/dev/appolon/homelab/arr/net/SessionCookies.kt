package dev.appolon.homelab.arr.net

import okhttp3.Cookie
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.Response

/**
 * Supplies WebView session cookies for requests to an arr server's own
 * origin.
 *
 * Reverse proxies in front of *arr (SSO gateways especially) frequently
 * intercept API calls and answer with their login page even when the request
 * carries a valid API key. The web UI itself works because the browser's
 * session cookie rides along. Native traffic therefore attaches the same
 * cookie: auto-provisioned sessions verify against the real API, and every
 * later request reaches the app instead of the login wall.
 *
 * The factory only consults the source for same-origin requests, matching
 * how credentials are scoped; third-party artwork hosts never see session
 * cookies.
 */
fun interface CookieSource {

    /** Cookies for [url], possibly empty; never null. */
    fun cookiesFor(url: String): List<Cookie>

    /** A source that always answers empty; the default when none is wired. */
    object None : CookieSource {
        override fun cookiesFor(url: String): List<Cookie> = emptyList()
    }
}

/** Attaches same-origin session cookies from a [CookieSource]. */
internal class SessionCookieInterceptor(
    private val baseUrl: String,
    private val source: CookieSource,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (!request.url.sameOrigin(baseUrl.toHttpUrlOrNull())) {
            // Foreign origin: no session cookies leave the server's domain.
            return chain.proceed(request.newBuilder().removeHeader("Cookie").build())
        }
        val cookies = runCatching { source.cookiesFor(request.url.toString()) }
            .getOrDefault(emptyList())
        if (cookies.isEmpty()) return chain.proceed(request)
        val merged = buildList {
            request.header("Cookie")?.let { add(it) }
            add(cookies.joinToString("; ") { "${it.name}=${it.value}" })
        }
        return chain.proceed(
            request.newBuilder().header("Cookie", merged.joinToString("; ")).build(),
        )
    }
}

/**
 * Header-level variant for code outside OkHttp's cookie model (the app's
 * WebView bridge supplies a raw Cookie header). Attaches [cookieHeaderProvider]
 * for same-origin requests only; returns null/blank answers leave the
 * request untouched.
 */
object SessionCookieInterceptorAccessible {
    fun forBase(
        baseUrl: String,
        cookieHeaderProvider: (String) -> String?,
    ): Interceptor = Interceptor { chain ->
        val request = chain.request()
        if (!request.url.sameOrigin(baseUrl.toHttpUrlOrNull())) {
            chain.proceed(request)
        } else {
            val header = runCatching { cookieHeaderProvider(request.url.toString()) }.getOrNull()
            if (header.isNullOrBlank()) {
                chain.proceed(request)
            } else {
                val existing = request.header("Cookie")
                val merged = listOfNotNull(existing, header).joinToString("; ")
                chain.proceed(request.newBuilder().header("Cookie", merged).build())
            }
        }
    }
}
