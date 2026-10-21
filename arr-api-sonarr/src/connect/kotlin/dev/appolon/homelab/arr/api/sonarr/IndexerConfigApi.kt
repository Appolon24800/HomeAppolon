package dev.appolon.homelab.arr.api.sonarr

import dev.appolon.homelab.arr.api.sonarr.model.IndexerConfigResource
import kotlin.jvm.JvmSuppressWildcards
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.PUT
import retrofit2.http.Path

@JvmSuppressWildcards
interface IndexerConfigApi {
    @GET("api/v3/config/indexer")
    suspend fun get(): IndexerConfigResource

    @PUT("api/v3/config/indexer/{id}")
    suspend fun update(
        @Path("id") id: String,
        @Body body: IndexerConfigResource
    ): IndexerConfigResource

    @GET("api/v3/config/indexer/{id}")
    suspend fun get(
        @Path("id") id: Int
    ): Response<IndexerConfigResource>
}
