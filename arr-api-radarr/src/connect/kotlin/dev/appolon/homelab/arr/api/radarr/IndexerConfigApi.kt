package dev.appolon.homelab.arr.api.radarr

import kotlin.jvm.JvmSuppressWildcards

import dev.appolon.homelab.arr.api.radarr.model.IndexerConfigResource
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.PUT
import retrofit2.http.Path

/** Radarr endpoints tagged IndexerConfig. */
@JvmSuppressWildcards
interface IndexerConfigApi {
    @GET("api/v3/config/indexer")
    suspend fun indexerConfig(): IndexerConfigResource

    @PUT("api/v3/config/indexer/{id}")
    suspend fun updateIndexerConfig(
        @Path("id") id: String,
        @Body body: IndexerConfigResource,
    ): Response<IndexerConfigResource>

    @GET("api/v3/config/indexer/{id}")
    suspend fun indexerConfigById(
        @Path("id") id: Int,
    ): Response<IndexerConfigResource>

}
