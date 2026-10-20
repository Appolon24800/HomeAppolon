package dev.appolon.homelab.arr.api.bazarr

import kotlin.jvm.JvmSuppressWildcards
import kotlinx.serialization.json.JsonObject
import retrofit2.http.GET

/**
 * The bundled spec documents the HTTP 200 itself as the payload ("Return status and http 200")
 * with no body schema, so the JSON body, when present, passes through untouched.
 */
@JvmSuppressWildcards
interface SystemPingApi {
    @GET("system/ping")
    suspend fun getSystemPing(): JsonObject
}
