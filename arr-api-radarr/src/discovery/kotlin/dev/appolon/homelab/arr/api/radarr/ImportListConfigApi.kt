package dev.appolon.homelab.arr.api.radarr

import dev.appolon.homelab.arr.api.radarr.model.ImportListConfigResource
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.PUT
import retrofit2.http.Path

/** Radarr endpoints tagged ImportListConfig. */
interface ImportListConfigApi {
    @GET("api/v3/config/importlist")
    suspend fun importListConfig(): ImportListConfigResource

    @PUT("api/v3/config/importlist/{id}")
    suspend fun updateImportListConfig(
        @Path("id") id: String,
        @Body body: ImportListConfigResource,
    ): Response<ImportListConfigResource>

    @GET("api/v3/config/importlist/{id}")
    suspend fun importListConfigById(
        @Path("id") id: Int,
    ): Response<ImportListConfigResource>

}
