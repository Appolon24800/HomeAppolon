package dev.appolon.homelab.arr.api.sonarr

import dev.appolon.homelab.arr.api.sonarr.model.RemotePathMappingResource
import kotlin.jvm.JvmSuppressWildcards
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path

@JvmSuppressWildcards
interface RemotePathMappingApi {
    @POST("api/v3/remotepathmapping")
    suspend fun create(
        @Body body: RemotePathMappingResource
    ): RemotePathMappingResource

    @GET("api/v3/remotepathmapping")
    suspend fun list(): List<RemotePathMappingResource>

    @DELETE("api/v3/remotepathmapping/{id}")
    suspend fun delete(
        @Path("id") id: Int
    ): Unit

    @PUT("api/v3/remotepathmapping/{id}")
    suspend fun update(
        @Path("id") id: String,
        @Body body: RemotePathMappingResource
    ): RemotePathMappingResource

    @GET("api/v3/remotepathmapping/{id}")
    suspend fun get(
        @Path("id") id: Int
    ): Response<RemotePathMappingResource>
}
