package dev.appolon.homelab.arr.api.lidarr

import dev.appolon.homelab.arr.api.lidarr.model.RootFolderResource
import kotlin.jvm.JvmSuppressWildcards
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path

/**
 * Lidarr endpoints tagged RootFolder. The update path id is a string in the
 * spec, unlike the integer ids on get and delete.
 */
@JvmSuppressWildcards
interface RootFolderApi {
    @GET("api/v1/rootfolder")
    suspend fun getRootFolders(): List<RootFolderResource>

    @GET("api/v1/rootfolder/{id}")
    suspend fun getRootFolder(@Path("id") id: Int): RootFolderResource

    @POST("api/v1/rootfolder")
    suspend fun addRootFolder(
        @Body resource: RootFolderResource,
    ): RootFolderResource

    @PUT("api/v1/rootfolder/{id}")
    suspend fun updateRootFolder(
        @Path("id") id: String,
        @Body resource: RootFolderResource,
    ): RootFolderResource

    @DELETE("api/v1/rootfolder/{id}")
    suspend fun deleteRootFolder(@Path("id") id: Int)
}
