package dev.appolon.homelab.arr.api.sonarr

import dev.appolon.homelab.arr.api.sonarr.model.RootFolderResource
import kotlin.jvm.JvmSuppressWildcards
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

/** Operations in the Sonarr RootFolder OpenAPI tag. */
@JvmSuppressWildcards
interface RootFolderApi {
    @POST("api/v3/rootfolder")
    suspend fun create(@Body body: RootFolderResource): RootFolderResource

    @GET("api/v3/rootfolder")
    suspend fun list(): List<RootFolderResource>

    @DELETE("api/v3/rootfolder/{id}")
    suspend fun delete(@Path("id") id: Int): Unit

    @GET("api/v3/rootfolder/{id}")
    suspend fun get(@Path("id") id: Int): Response<RootFolderResource>
}
