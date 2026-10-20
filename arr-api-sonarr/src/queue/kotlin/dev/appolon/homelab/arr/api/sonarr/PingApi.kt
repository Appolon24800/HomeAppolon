package dev.appolon.homelab.arr.api.sonarr

import dev.appolon.homelab.arr.api.sonarr.model.PingResource
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.HEAD

/** Operations in the Sonarr Ping OpenAPI tag. */
@JvmSuppressWildcards
interface PingApi {
    @GET("ping")
    suspend fun list(): Response<PingResource>

    @HEAD("ping")
    suspend fun head(): Response<Unit>
}
