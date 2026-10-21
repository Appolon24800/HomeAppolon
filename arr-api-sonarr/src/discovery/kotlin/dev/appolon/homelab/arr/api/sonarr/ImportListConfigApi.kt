package dev.appolon.homelab.arr.api.sonarr

import dev.appolon.homelab.arr.api.sonarr.model.ImportListConfigResource
import kotlin.jvm.JvmSuppressWildcards
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.PUT
import retrofit2.http.Path

@JvmSuppressWildcards
interface ImportListConfigApi {
    @GET("api/v3/config/importlist")
    suspend fun list(): ImportListConfigResource

    @PUT("api/v3/config/importlist/{id}")
    suspend fun update(
        @Path("id") id: String,
        @Body body: ImportListConfigResource
    ): ImportListConfigResource

    @GET("api/v3/config/importlist/{id}")
    suspend fun get(
        @Path("id") id: Int
    ): Response<ImportListConfigResource>
}
