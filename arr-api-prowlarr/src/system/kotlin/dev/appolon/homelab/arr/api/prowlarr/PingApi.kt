package dev.appolon.homelab.arr.api.prowlarr

import dev.appolon.homelab.arr.api.prowlarr.model.PingResource
import kotlin.jvm.JvmSuppressWildcards
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.HEAD

/**
 * Ping tag: liveness probe used by the connection manager. The HEAD variant
 * skips the body for cheap connectivity checks and returns [Response]
 * because the status code alone is the answer.
 */
@JvmSuppressWildcards
interface PingApi {
    @GET("ping")
    suspend fun ping(): PingResource

    @HEAD("ping")
    suspend fun pingHead(): Response<Unit>
}
