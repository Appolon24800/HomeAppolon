package dev.appolon.homelab.arr.api.sonarr

import dev.appolon.homelab.arr.api.sonarr.model.CustomFormatBulkResource
import dev.appolon.homelab.arr.api.sonarr.model.CustomFormatResource
import kotlinx.serialization.json.JsonObject
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.HTTP
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path

/**
 * Operations in the Sonarr CustomFormat OpenAPI tag. The spec declares no
 * response schema for the schema endpoint, so custom format condition
 * templates pass through as raw JSON objects.
 */
@JvmSuppressWildcards
interface CustomFormatApi {
    @GET("api/v3/customformat")
    suspend fun list(): List<CustomFormatResource>

    @POST("api/v3/customformat")
    suspend fun create(
        @Body body: CustomFormatResource
    ): CustomFormatResource

    @PUT("api/v3/customformat/bulk")
    suspend fun updateBulk(
        @Body body: CustomFormatBulkResource
    ): CustomFormatResource

    @HTTP(method = "DELETE", path = "api/v3/customformat/bulk", hasBody = true)
    suspend fun deleteBulk(
        @Body body: CustomFormatBulkResource
    ): Unit

    @GET("api/v3/customformat/schema")
    suspend fun schema(): List<JsonObject>

    @PUT("api/v3/customformat/{id}")
    suspend fun update(
        @Path("id") id: String,
        @Body body: CustomFormatResource
    ): CustomFormatResource

    @DELETE("api/v3/customformat/{id}")
    suspend fun delete(
        @Path("id") id: Int
    ): Unit

    @GET("api/v3/customformat/{id}")
    suspend fun get(
        @Path("id") id: Int
    ): Response<CustomFormatResource>
}
