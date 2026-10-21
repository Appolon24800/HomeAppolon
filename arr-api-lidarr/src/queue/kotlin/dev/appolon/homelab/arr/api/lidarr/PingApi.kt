package dev.appolon.homelab.arr.api.lidarr

import dev.appolon.homelab.arr.api.lidarr.model.PingResource
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.HEAD

interface PingApi {
    @GET("ping")
    suspend fun ping(): PingResource

    /** HEAD has no response body. The status alone reports server reachability. */
    @HEAD("ping")
    suspend fun headPing(): Response<Void>
}
