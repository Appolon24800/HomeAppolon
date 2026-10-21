package dev.appolon.homelab.arr.api.lidarr

import dev.appolon.homelab.arr.api.lidarr.model.AutoTaggingResource
import kotlin.jvm.JvmSuppressWildcards
import kotlinx.serialization.json.JsonObject
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path

/**
 * Lidarr endpoints tagged AutoTagging. The update path id is a string in the
 * spec, unlike the integer ids on get and delete. The schema operation has no
 * documented response, so it passes through as raw JSON.
 */
@JvmSuppressWildcards
interface AutoTaggingApi {
    @GET("api/v1/autotagging")
    suspend fun getAutoTaggings(): List<AutoTaggingResource>

    @GET("api/v1/autotagging/{id}")
    suspend fun getAutoTagging(@Path("id") id: Int): AutoTaggingResource

    @POST("api/v1/autotagging")
    suspend fun addAutoTagging(
        @Body resource: AutoTaggingResource,
    ): AutoTaggingResource

    @PUT("api/v1/autotagging/{id}")
    suspend fun updateAutoTagging(
        @Path("id") id: String,
        @Body resource: AutoTaggingResource,
    ): AutoTaggingResource

    @DELETE("api/v1/autotagging/{id}")
    suspend fun deleteAutoTagging(@Path("id") id: Int)

    @GET("api/v1/autotagging/schema")
    suspend fun getAutoTaggingSchema(): JsonObject
}
