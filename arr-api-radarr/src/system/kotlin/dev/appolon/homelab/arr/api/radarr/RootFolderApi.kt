package dev.appolon.homelab.arr.api.radarr

import kotlin.jvm.JvmSuppressWildcards

import dev.appolon.homelab.arr.api.radarr.model.RootFolderResource
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

/** Radarr endpoints tagged RootFolder. */
@JvmSuppressWildcards
interface RootFolderApi {
    @POST("api/v3/rootfolder")
    suspend fun createRootFolder(
        @Body body: RootFolderResource,
    ): Response<RootFolderResource>

    @GET("api/v3/rootfolder")
    suspend fun rootFolders(): List<RootFolderResource>

    @DELETE("api/v3/rootfolder/{id}")
    suspend fun removeRootFolder(
        @Path("id") id: Int,
    ): Response<Unit>

    @GET("api/v3/rootfolder/{id}")
    suspend fun rootFolder(
        @Path("id") id: Int,
    ): Response<RootFolderResource>
}
