package dev.appolon.homelab.arr.api.lidarr

import dev.appolon.homelab.arr.api.lidarr.model.MetadataResource
import kotlin.jvm.JvmSuppressWildcards
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Lidarr endpoints tagged Metadata (the metadata consumer providers, distinct
 * from the quality group's MetadataProfile and MetadataProviderConfig). Here
 * even the update path id is an integer in the spec.
 */
@JvmSuppressWildcards
interface MetadataApi {
    @GET("api/v1/metadata")
    suspend fun getMetadatas(): List<MetadataResource>

    @GET("api/v1/metadata/{id}")
    suspend fun getMetadata(@Path("id") id: Int): MetadataResource

    @POST("api/v1/metadata")
    suspend fun addMetadata(
        @Query("forceSave") forceSave: Boolean? = null,
        @Body resource: MetadataResource,
    ): MetadataResource

    @PUT("api/v1/metadata/{id}")
    suspend fun updateMetadata(
        @Path("id") id: Int,
        @Query("forceSave") forceSave: Boolean? = null,
        @Body resource: MetadataResource,
    ): MetadataResource

    @DELETE("api/v1/metadata/{id}")
    suspend fun deleteMetadata(@Path("id") id: Int)

    @GET("api/v1/metadata/schema")
    suspend fun getMetadataSchema(): List<MetadataResource>

    @POST("api/v1/metadata/test")
    suspend fun testMetadata(
        @Query("forceTest") forceTest: Boolean? = null,
        @Body resource: MetadataResource,
    )

    @POST("api/v1/metadata/testall")
    suspend fun testAllMetadata()

    @POST("api/v1/metadata/action/{name}")
    suspend fun metadataAction(
        @Path("name") name: String,
        @Body resource: MetadataResource,
    )
}
