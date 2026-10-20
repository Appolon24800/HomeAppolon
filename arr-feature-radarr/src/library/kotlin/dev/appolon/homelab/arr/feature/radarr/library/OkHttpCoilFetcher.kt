package dev.appolon.homelab.arr.feature.radarr.library

import android.content.Context
import coil3.ImageLoader
import coil3.Uri
import coil3.decode.DataSource
import coil3.decode.ImageSource
import coil3.fetch.Fetcher
import coil3.fetch.SourceFetchResult
import coil3.request.Options
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okio.FileSystem

/**
 * Coil network fetcher over the same OkHttp client the API uses, so poster
 * requests carry the server's X-Api-Key header, proxy, and trust settings
 * without a second credential path. The catalog's coil-network-okhttp
 * artifact cannot be resolved in this environment (only a jvm variant exists
 * in the offline cache), and coil3 ships no built-in http fetcher, so this
 * implements the public [Fetcher] contract directly: the factory binds one
 * URL per instance, fetch hands the response body's source to Coil's decode
 * pipeline, and Coil closes the source after decoding.
 */
class OkHttpCoilFetcher private constructor(
    private val callFactory: Call.Factory,
    private val url: String,
) : Fetcher {

    override suspend fun fetch(): SourceFetchResult {
        val request = try {
            Request.Builder().url(url).build()
        } catch (t: Throwable) {
            throw IOException("Unusable image URL: $url", t)
        }
        val response = callFactory.newCall(request).await()
        val body = response.body
        if (!response.isSuccessful || body == null) {
            response.close()
            throw IOException("HTTP ${response.code} loading $url")
        }
        return SourceFetchResult(
            source = ImageSource(body.source(), FileSystem.SYSTEM),
            mimeType = body.contentType()?.toString(),
            dataSource = DataSource.NETWORK,
        )
    }

    /** Registered for [Uri] because Coil maps String models through StringMapper first. */
    class Factory(
        private val callFactory: Call.Factory,
    ) : Fetcher.Factory<Uri> {
        override fun create(data: Uri, options: Options, imageLoader: ImageLoader): Fetcher? {
            val url = data.toString()
            return if (url.startsWith("http", ignoreCase = true)) {
                OkHttpCoilFetcher(callFactory, url)
            } else {
                null
            }
        }
    }
}

/** Builds the loader for one server connection; needs the Android context on this platform. */
fun okhttpImageLoader(context: Context, client: OkHttpClient): ImageLoader =
    ImageLoader.Builder(context)
        .components { add(OkHttpCoilFetcher.Factory(client)) }
        .build()

/** Suspends until the call answers; cancelling the coroutine cancels the call. */
private suspend fun Call.await(): Response = suspendCancellableCoroutine { continuation ->
    enqueue(object : Callback {
        override fun onResponse(call: Call, response: Response) {
            continuation.resume(response)
        }

        override fun onFailure(call: Call, e: IOException) {
            continuation.resumeWithException(e)
        }
    })
    continuation.invokeOnCancellation { runCatching { cancel() } }
}
