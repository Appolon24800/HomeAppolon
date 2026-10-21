package dev.appolon.homelab.arr.api.lidarr

import dev.appolon.homelab.arr.api.lidarr.model.MetadataProfileResource
import kotlin.jvm.JvmSuppressWildcards
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path

@JvmSuppressWildcards
interface MetadataProfileApi {
    @POST("api/v1/metadataprofile")
    suspend fun createMetadataProfile(
        @Body resource: MetadataProfileResource,
    ): MetadataProfileResource

    @GET("api/v1/metadataprofile")
    suspend fun getMetadataProfiles(): List<MetadataProfileResource>

    @DELETE("api/v1/metadataprofile/{id}")
    suspend fun deleteMetadataProfile(@Path("id") id: Int)

    @PUT("api/v1/metadataprofile/{id}")
    suspend fun updateMetadataProfile(
        @Path("id") id: String,
        @Body resource: MetadataProfileResource,
    ): MetadataProfileResource

    @GET("api/v1/metadataprofile/{id}")
    suspend fun getMetadataProfileById(@Path("id") id: Int): MetadataProfileResource
}
