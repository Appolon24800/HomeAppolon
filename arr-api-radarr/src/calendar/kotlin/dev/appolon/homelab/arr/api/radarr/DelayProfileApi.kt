package dev.appolon.homelab.arr.api.radarr

import dev.appolon.homelab.arr.api.radarr.model.DelayProfileResource
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query
import kotlin.jvm.JvmSuppressWildcards

/**
 * Radarr endpoints tagged DelayProfile. The spec types the PUT path id as a
 * string and the GET and DELETE path ids as integers.
 */
@JvmSuppressWildcards
interface DelayProfileApi {
    @GET("api/v3/delayprofile")
    suspend fun delayProfiles(): List<DelayProfileResource>

    @POST("api/v3/delayprofile")
    suspend fun createDelayProfile(
        @Body body: DelayProfileResource,
    ): Response<DelayProfileResource>

    /** The spec documents a 404 for an unknown profile id. */
    @GET("api/v3/delayprofile/{id}")
    suspend fun delayProfile(
        @Path("id") id: Int,
    ): Response<DelayProfileResource>

    @PUT("api/v3/delayprofile/{id}")
    suspend fun updateDelayProfile(
        @Path("id") id: String,
        @Body body: DelayProfileResource,
    ): Response<DelayProfileResource>

    @DELETE("api/v3/delayprofile/{id}")
    suspend fun removeDelayProfile(
        @Path("id") id: Int,
    ): Response<Unit>

    /** A null `after` moves the profile to the top of the list. */
    @PUT("api/v3/delayprofile/reorder/{id}")
    suspend fun reorderDelayProfile(
        @Path("id") id: Int,
        @Query("after") after: Int? = null,
    ): List<DelayProfileResource>
}
