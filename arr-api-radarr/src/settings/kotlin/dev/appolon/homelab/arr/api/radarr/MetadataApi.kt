package dev.appolon.homelab.arr.api.radarr

import kotlin.jvm.JvmSuppressWildcards

import dev.appolon.homelab.arr.api.radarr.model.MetadataResource
import dev.appolon.homelab.arr.api.radarr.model.ProviderTestAllResult
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

/** Radarr endpoints tagged Metadata. */
@JvmSuppressWildcards
interface MetadataApi {
    @GET("api/v3/metadata")
    suspend fun metadata(): List<MetadataResource>

    @POST("api/v3/metadata")
    suspend fun createMetadata(
        @Query("forceSave") forceSave: Boolean? = null,
        @Body body: MetadataResource,
    ): Response<MetadataResource>

    @PUT("api/v3/metadata/{id}")
    suspend fun updateMetadata(
        @Path("id") id: Int,
        @Query("forceSave") forceSave: Boolean? = null,
        @Body body: MetadataResource,
    ): Response<MetadataResource>

    @DELETE("api/v3/metadata/{id}")
    suspend fun removeMetadata(
        @Path("id") id: Int,
    ): Response<Unit>

    @GET("api/v3/metadata/{id}")
    suspend fun metadataById(
        @Path("id") id: Int,
    ): Response<MetadataResource>

    @GET("api/v3/metadata/schema")
    suspend fun metadataSchema(): List<MetadataResource>

    /** The spec documents only a 204 response, so success carries no body. */
    @POST("api/v3/metadata/test")
    suspend fun testMetadata(
        @Query("forceTest") forceTest: Boolean? = null,
        @Body body: MetadataResource,
    ): Response<Unit>

    /** 200 lists per-provider results, 400 the same shape with failures. */
    @POST("api/v3/metadata/testall")
    suspend fun testAllMetadata(): Response<List<ProviderTestAllResult>>

    /** The spec documents only a 400 response, so success carries no body. */
    @POST("api/v3/metadata/action/{name}")
    suspend fun metadataAction(
        @Path("name") name: String,
        @Body body: MetadataResource,
    ): Response<Unit>
}
