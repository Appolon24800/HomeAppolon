package dev.appolon.homelab.arr.api.radarr

import dev.appolon.homelab.arr.api.radarr.model.CustomFormatBulkResource
import dev.appolon.homelab.arr.api.radarr.model.CustomFormatResource
import dev.appolon.homelab.arr.api.radarr.model.CustomFormatSpecificationSchema
import kotlinx.serialization.json.JsonObject
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.HTTP
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import kotlin.jvm.JvmSuppressWildcards

/**
 * Radarr endpoints tagged CustomFormat. The spec types the PUT path id as a
 * string and the GET and DELETE path ids as integers.
 */
@JvmSuppressWildcards
interface CustomFormatApi {
    @GET("api/v3/customformat")
    suspend fun customFormats(): List<CustomFormatResource>

    @POST("api/v3/customformat")
    suspend fun createCustomFormat(
        @Body body: CustomFormatResource,
    ): Response<CustomFormatResource>

    /** The spec documents a 404 for an unknown format id. */
    @GET("api/v3/customformat/{id}")
    suspend fun customFormat(
        @Path("id") id: Int,
    ): Response<CustomFormatResource>

    @PUT("api/v3/customformat/{id}")
    suspend fun updateCustomFormat(
        @Path("id") id: String,
        @Body body: CustomFormatResource,
    ): Response<CustomFormatResource>

    @DELETE("api/v3/customformat/{id}")
    suspend fun removeCustomFormat(
        @Path("id") id: Int,
    ): Response<Unit>

    /** The spec documents a 400 when the body is rejected. */
    @PUT("api/v3/customformat/bulk")
    suspend fun updateCustomFormats(
        @Body body: CustomFormatBulkResource,
    ): Response<List<CustomFormatResource>>

    /** The spec leaves the 200 response schema unspecified. */
    @HTTP(method = "DELETE", path = "api/v3/customformat/bulk", hasBody = true)
    suspend fun removeCustomFormats(
        @Body body: CustomFormatBulkResource,
    ): JsonObject

    @GET("api/v3/customformat/schema")
    suspend fun customFormatSchema(): List<CustomFormatSpecificationSchema>
}
