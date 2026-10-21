package dev.appolon.homelab.arr.api.sonarr

import dev.appolon.homelab.arr.api.sonarr.model.AutoTaggingResource
import kotlin.jvm.JvmSuppressWildcards
import kotlinx.serialization.json.JsonObject
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path

/**
 * Operations in the Sonarr AutoTagging OpenAPI tag. The spec declares no
 * response schema for the schema endpoint, so templates pass through as raw
 * JSON.
 */
@JvmSuppressWildcards
interface AutoTaggingApi {
    @POST("api/v3/autotagging")
    suspend fun create(@Body body: AutoTaggingResource): AutoTaggingResource

    @GET("api/v3/autotagging")
    suspend fun list(): List<AutoTaggingResource>

    @PUT("api/v3/autotagging/{id}")
    suspend fun update(
        @Path("id") id: String,
        @Body body: AutoTaggingResource
    ): AutoTaggingResource

    @DELETE("api/v3/autotagging/{id}")
    suspend fun delete(@Path("id") id: Int): Unit

    @GET("api/v3/autotagging/{id}")
    suspend fun get(@Path("id") id: Int): Response<AutoTaggingResource>

    @GET("api/v3/autotagging/schema")
    suspend fun schema(): JsonObject
}
