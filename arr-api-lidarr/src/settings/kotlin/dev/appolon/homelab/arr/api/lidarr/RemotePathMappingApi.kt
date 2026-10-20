package dev.appolon.homelab.arr.api.lidarr

import dev.appolon.homelab.arr.api.lidarr.model.RemotePathMappingResource
import kotlin.jvm.JvmSuppressWildcards
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path

/**
 * Lidarr endpoints tagged RemotePathMapping. The update path id is a string in
 * the spec, unlike the integer ids on get and delete.
 */
@JvmSuppressWildcards
interface RemotePathMappingApi {
    @GET("api/v1/remotepathmapping")
    suspend fun getRemotePathMappings(): List<RemotePathMappingResource>

    @GET("api/v1/remotepathmapping/{id}")
    suspend fun getRemotePathMapping(@Path("id") id: Int): RemotePathMappingResource

    @POST("api/v1/remotepathmapping")
    suspend fun addRemotePathMapping(
        @Body resource: RemotePathMappingResource,
    ): RemotePathMappingResource

    @PUT("api/v1/remotepathmapping/{id}")
    suspend fun updateRemotePathMapping(
        @Path("id") id: String,
        @Body resource: RemotePathMappingResource,
    ): RemotePathMappingResource

    @DELETE("api/v1/remotepathmapping/{id}")
    suspend fun deleteRemotePathMapping(@Path("id") id: Int)
}
