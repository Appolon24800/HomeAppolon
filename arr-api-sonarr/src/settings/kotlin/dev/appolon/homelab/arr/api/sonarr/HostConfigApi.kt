package dev.appolon.homelab.arr.api.sonarr

import dev.appolon.homelab.arr.api.sonarr.model.HostConfigResource
import kotlin.jvm.JvmSuppressWildcards
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.PUT
import retrofit2.http.Path

/** Operations in the Sonarr HostConfig OpenAPI tag. */
@JvmSuppressWildcards
interface HostConfigApi {
    @GET("api/v3/config/host")
    suspend fun get(): HostConfigResource

    @PUT("api/v3/config/host/{id}")
    suspend fun update(
        @Path("id") id: String,
        @Body body: HostConfigResource
    ): HostConfigResource

    @GET("api/v3/config/host/{id}")
    suspend fun get(@Path("id") id: Int): Response<HostConfigResource>
}
