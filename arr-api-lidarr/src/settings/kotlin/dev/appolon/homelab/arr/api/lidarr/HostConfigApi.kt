package dev.appolon.homelab.arr.api.lidarr

import dev.appolon.homelab.arr.api.lidarr.model.HostConfigResource
import kotlin.jvm.JvmSuppressWildcards
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.PUT
import retrofit2.http.Path

/**
 * Lidarr endpoints tagged HostConfig. The update path id is a string in the
 * spec, unlike the integer id on the single get.
 */
@JvmSuppressWildcards
interface HostConfigApi {
    @GET("api/v1/config/host")
    suspend fun getHostConfig(): HostConfigResource

    @GET("api/v1/config/host/{id}")
    suspend fun getHostConfigById(@Path("id") id: Int): HostConfigResource

    @PUT("api/v1/config/host/{id}")
    suspend fun updateHostConfig(
        @Path("id") id: String,
        @Body resource: HostConfigResource,
    ): HostConfigResource
}
