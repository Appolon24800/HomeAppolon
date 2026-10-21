package dev.appolon.homelab.arr.net

import java.io.IOException
import okhttp3.Interceptor
import okhttp3.Response

/**
 * Rejects oversized responses before any converter materializes them.
 *
 * The JSON converter reads a whole body into one String; on multi-hundred-
 * megabyte library endpoints that allocation OOM-kills the process on the
 * OkHttp dispatcher thread, where no repository guard can catch it. The
 * Content-Length header is read first: a response that declares more bytes
 * than [MAX_BYTES] is closed and fails as a plain network error the
 * repositories already map to an error state.
 */
internal object OversizedResponseGuard : Interceptor {

    /** 32 MiB: the largest legitimate single response (covers big imports). */
    const val MAX_BYTES: Long = 32L * 1024 * 1024

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val response = chain.proceed(request)
        val declared = response.body?.contentLength() ?: -1L
        if (declared > MAX_BYTES) {
            val url = request.url
            response.close()
            throw IOException(
                "Response from ${url.host} declares $declared bytes at ${url.encodedPath}; " +
                    "the limit is $MAX_BYTES",
            )
        }
        return response
    }
}
