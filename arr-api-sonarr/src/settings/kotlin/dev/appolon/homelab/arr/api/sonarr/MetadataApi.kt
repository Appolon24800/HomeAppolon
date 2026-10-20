package dev.appolon.homelab.arr.api.sonarr

import dev.appolon.homelab.arr.api.sonarr.model.MetadataResource
import kotlin.jvm.JvmSuppressWildcards
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

/** Operations in the Sonarr Metadata OpenAPI tag. */
@JvmSuppressWildcards
interface MetadataApi {
    @GET("api/v3/metadata")
    suspend fun list(): List<MetadataResource>

    @POST("api/v3/metadata")
    suspend fun create(
        @Query("forceSave") forceSave: Boolean? = null,
        @Body body: MetadataResource
    ): MetadataResource

    @PUT("api/v3/metadata/{id}")
    suspend fun update(
        @Path("id") id: Int,
        @Query("forceSave") forceSave: Boolean? = null,
        @Body body: MetadataResource
    ): MetadataResource

    @DELETE("api/v3/metadata/{id}")
    suspend fun delete(@Path("id") id: Int): Unit

    @GET("api/v3/metadata/{id}")
    suspend fun get(@Path("id") id: Int): Response<MetadataResource>

    @GET("api/v3/metadata/schema")
    suspend fun schema(): List<MetadataResource>

    @POST("api/v3/metadata/test")
    suspend fun test(
        @Query("forceTest") forceTest: Boolean? = null,
        @Body body: MetadataResource
    ): Unit

    @POST("api/v3/metadata/testall")
    suspend fun testAll(): Unit

    @POST("api/v3/metadata/action/{name}")
    suspend fun action(
        @Path("name") name: String,
        @Body body: MetadataResource
    ): Unit
}
