package dev.appolon.homelab.arr.api.radarr

import kotlin.jvm.JvmSuppressWildcards

import dev.appolon.homelab.arr.api.radarr.model.RemotePathMappingResource
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path

/** Radarr endpoints tagged RemotePathMapping. */
@JvmSuppressWildcards
interface RemotePathMappingApi {
    @POST("api/v3/remotepathmapping")
    suspend fun createRemotePathMapping(
        @Body body: RemotePathMappingResource,
    ): Response<RemotePathMappingResource>

    @GET("api/v3/remotepathmapping")
    suspend fun remotePathMappings(): List<RemotePathMappingResource>

    /** The spec documents a bodyless 200, so no body is modeled. */
    @DELETE("api/v3/remotepathmapping/{id}")
    suspend fun removeRemotePathMapping(
        @Path("id") id: Int,
    )

    @PUT("api/v3/remotepathmapping/{id}")
    suspend fun updateRemotePathMapping(
        @Path("id") id: String,
        @Body body: RemotePathMappingResource,
    ): Response<RemotePathMappingResource>

    @GET("api/v3/remotepathmapping/{id}")
    suspend fun remotePathMapping(
        @Path("id") id: Int,
    ): Response<RemotePathMappingResource>

}
