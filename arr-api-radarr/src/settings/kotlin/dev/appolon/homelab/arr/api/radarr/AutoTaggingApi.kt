package dev.appolon.homelab.arr.api.radarr

import kotlin.jvm.JvmSuppressWildcards

import dev.appolon.homelab.arr.api.radarr.model.AutoTaggingResource
import dev.appolon.homelab.arr.api.radarr.model.AutoTaggingSpecificationSchema
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path

/** Radarr endpoints tagged AutoTagging. */
@JvmSuppressWildcards
interface AutoTaggingApi {
    @POST("api/v3/autotagging")
    suspend fun createAutoTagging(
        @Body body: AutoTaggingResource,
    ): Response<AutoTaggingResource>

    @GET("api/v3/autotagging")
    suspend fun autoTaggings(): List<AutoTaggingResource>

    /** The spec declares this path id as a string. */
    @PUT("api/v3/autotagging/{id}")
    suspend fun updateAutoTagging(
        @Path("id") id: String,
        @Body body: AutoTaggingResource,
    ): Response<AutoTaggingResource>

    @DELETE("api/v3/autotagging/{id}")
    suspend fun removeAutoTagging(
        @Path("id") id: Int,
    ): Response<Unit>

    @GET("api/v3/autotagging/{id}")
    suspend fun autoTagging(
        @Path("id") id: Int,
    ): Response<AutoTaggingResource>

    @GET("api/v3/autotagging/schema")
    suspend fun autoTaggingSchema(): List<AutoTaggingSpecificationSchema>
}
