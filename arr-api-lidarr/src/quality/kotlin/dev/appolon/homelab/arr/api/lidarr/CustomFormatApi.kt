package dev.appolon.homelab.arr.api.lidarr

import dev.appolon.homelab.arr.api.lidarr.model.CustomFormatBulkResource
import dev.appolon.homelab.arr.api.lidarr.model.CustomFormatResource
import kotlin.jvm.JvmSuppressWildcards
import kotlinx.serialization.json.JsonElement
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.HTTP
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path

@JvmSuppressWildcards
interface CustomFormatApi {
    @GET("api/v1/customformat/{id}")
    suspend fun getCustomFormatById(@Path("id") id: Int): CustomFormatResource

    @PUT("api/v1/customformat/{id}")
    suspend fun updateCustomFormat(
        @Path("id") id: String,
        @Body resource: CustomFormatResource,
    ): CustomFormatResource

    @DELETE("api/v1/customformat/{id}")
    suspend fun deleteCustomFormat(@Path("id") id: Int)

    @GET("api/v1/customformat")
    suspend fun getCustomFormats(): List<CustomFormatResource>

    @POST("api/v1/customformat")
    suspend fun createCustomFormat(@Body resource: CustomFormatResource): CustomFormatResource

    @PUT("api/v1/customformat/bulk")
    suspend fun updateCustomFormatBulk(
        @Body resource: CustomFormatBulkResource,
    ): List<CustomFormatResource>

    @HTTP(method = "DELETE", path = "api/v1/customformat/bulk", hasBody = true)
    suspend fun deleteCustomFormatBulk(@Body resource: CustomFormatBulkResource)

    /**
     * The spec declares no response content. The server answers with a JSON array of
     * custom format specification schemas, so the payload passes through untyped.
     */
    @GET("api/v1/customformat/schema")
    suspend fun getCustomFormatSchema(): JsonElement
}
