package dev.appolon.homelab.arr.api.prowlarr

import dev.appolon.homelab.arr.api.prowlarr.model.HostConfigResource
import kotlin.jvm.JvmSuppressWildcards
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.PUT
import retrofit2.http.Path

/**
 * HostConfig tag: the host settings singleton. GET with any id returns the
 * one row; PUT path ids are strings because the spec declares that type on
 * update routes.
 */
@JvmSuppressWildcards
interface HostConfigApi {
    @GET("api/v1/config/host")
    suspend fun getHostConfig(): HostConfigResource

    @GET("api/v1/config/host/{id}")
    suspend fun getHostConfig(@Path("id") id: Int): HostConfigResource

    @PUT("api/v1/config/host/{id}")
    suspend fun updateHostConfig(
        @Path("id") id: String,
        @Body resource: HostConfigResource,
    ): HostConfigResource
}
