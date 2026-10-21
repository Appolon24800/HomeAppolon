package dev.appolon.homelab.arr.net

import dev.appolon.homelab.arr.core.ArrResult
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * Posts one raw JSON command body to an *arr app's command endpoint over the
 * shared authenticated client. Command payloads put selection fields such as
 * `movieIds` or `seriesId` at the JSON root, a shape the Retrofit
 * `CommandResource` DTO cannot express, so each feature builds its own body
 * and delegates the identical wire path here: URL join, media type, and the
 * error mapping used by every other call.
 *
 * [post] blocks on the calling thread (OkHttp `execute`); callers run it on
 * Dispatchers.IO. This module's main source set has no coroutines dependency,
 * so dispatching stays with the callers.
 */
class ArrCommandRunner(
    private val client: OkHttpClient,
    private val baseUrl: String,
) {

    /** Posts [jsonBody] to [commandPath]; a non-2xx reply maps to [ArrResult.err]. */
    fun post(commandPath: String, jsonBody: String): ArrResult<Unit> {
        val request = try {
            Request.Builder()
                .url(baseUrl.trimEnd('/') + "/" + commandPath.trimStart('/'))
                .post(jsonBody.toRequestBody(JSON.toMediaType()))
                .build()
        } catch (t: Throwable) {
            return ArrResult.err(t.arrError())
        }
        return try {
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    ArrResult.ok(Unit)
                } else {
                    ArrResult.err(response.arrError())
                }
            }
        } catch (t: Throwable) {
            ArrResult.err(t.arrError())
        }
    }

    private companion object {
        const val JSON = "application/json"
    }
}
