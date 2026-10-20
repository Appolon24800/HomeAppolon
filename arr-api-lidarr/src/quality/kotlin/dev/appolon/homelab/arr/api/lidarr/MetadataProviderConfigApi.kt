package dev.appolon.homelab.arr.api.lidarr

import dev.appolon.homelab.arr.api.lidarr.model.MetadataProviderConfigResource
import kotlin.jvm.JvmSuppressWildcards
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.PUT
import retrofit2.http.Path

@JvmSuppressWildcards
interface MetadataProviderConfigApi {
    @GET("api/v1/config/metadataprovider/{id}")
    suspend fun getMetadataProviderConfigById(@Path("id") id: Int): MetadataProviderConfigResource

    @PUT("api/v1/config/metadataprovider/{id}")
    suspend fun updateMetadataProviderConfig(
        @Path("id") id: String,
        @Body resource: MetadataProviderConfigResource,
    ): MetadataProviderConfigResource

    @GET("api/v1/config/metadataprovider")
    suspend fun getMetadataProviderConfig(): MetadataProviderConfigResource
}
