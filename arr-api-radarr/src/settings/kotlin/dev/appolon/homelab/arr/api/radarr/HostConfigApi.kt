package dev.appolon.homelab.arr.api.radarr

import kotlin.jvm.JvmSuppressWildcards

import dev.appolon.homelab.arr.api.radarr.model.HostConfigResource
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.PUT
import retrofit2.http.Path

/** Radarr endpoints tagged HostConfig. */
@JvmSuppressWildcards
interface HostConfigApi {
    @GET("api/v3/config/host")
    suspend fun hostConfig(): HostConfigResource

    /** The spec declares this path id as a string. */
    @PUT("api/v3/config/host/{id}")
    suspend fun updateHostConfig(
        @Path("id") id: String,
        @Body body: HostConfigResource,
    ): Response<HostConfigResource>

    @GET("api/v3/config/host/{id}")
    suspend fun hostConfigById(
        @Path("id") id: Int,
    ): Response<HostConfigResource>
}
