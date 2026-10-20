package dev.appolon.homelab.arr.api.radarr

import kotlin.jvm.JvmSuppressWildcards

import dev.appolon.homelab.arr.api.radarr.model.MetadataConfigResource
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.PUT
import retrofit2.http.Path

/** Radarr endpoints tagged MetadataConfig. */
@JvmSuppressWildcards
interface MetadataConfigApi {
    @GET("api/v3/config/metadata")
    suspend fun metadataConfig(): MetadataConfigResource

    /** The spec declares this path id as a string. */
    @PUT("api/v3/config/metadata/{id}")
    suspend fun updateMetadataConfig(
        @Path("id") id: String,
        @Body body: MetadataConfigResource,
    ): Response<MetadataConfigResource>

    @GET("api/v3/config/metadata/{id}")
    suspend fun metadataConfigById(
        @Path("id") id: Int,
    ): Response<MetadataConfigResource>
}
