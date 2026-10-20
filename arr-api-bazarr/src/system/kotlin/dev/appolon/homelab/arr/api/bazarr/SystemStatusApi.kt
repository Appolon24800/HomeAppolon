package dev.appolon.homelab.arr.api.bazarr

import kotlin.jvm.JvmSuppressWildcards
import kotlinx.serialization.json.JsonObject
import retrofit2.http.GET

/** Status payloads have no schema in the bundled spec, so the body passes through untouched. */
@JvmSuppressWildcards
interface SystemStatusApi {
    @GET("api/system/status")
    suspend fun getSystemStatus(): JsonObject
}
