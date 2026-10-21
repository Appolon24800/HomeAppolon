package dev.appolon.homelab.arr.api.bazarr

import kotlin.jvm.JvmSuppressWildcards
import kotlinx.serialization.json.JsonObject
import retrofit2.http.GET

/** Health issues have no schema in the bundled spec, so the payload stays raw JSON. */
@JvmSuppressWildcards
interface SystemHealthApi {
    @GET("api/system/health")
    suspend fun getSystemHealth(): JsonObject
}
