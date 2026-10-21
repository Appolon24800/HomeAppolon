package dev.appolon.homelab.arr.api.radarr

import dev.appolon.homelab.arr.api.radarr.model.QualityProfileResource
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import kotlin.jvm.JvmSuppressWildcards

/**
 * Radarr endpoints tagged QualityProfile. The spec types the PUT path id as a
 * string and the GET and DELETE path ids as integers.
 */
@JvmSuppressWildcards
interface QualityProfileApi {
    @GET("api/v3/qualityprofile")
    suspend fun qualityProfiles(): List<QualityProfileResource>

    @POST("api/v3/qualityprofile")
    suspend fun createQualityProfile(
        @Body body: QualityProfileResource,
    ): Response<QualityProfileResource>

    /** The spec documents a 404 for an unknown profile id. */
    @GET("api/v3/qualityprofile/{id}")
    suspend fun qualityProfile(
        @Path("id") id: Int,
    ): Response<QualityProfileResource>

    @PUT("api/v3/qualityprofile/{id}")
    suspend fun updateQualityProfile(
        @Path("id") id: String,
        @Body body: QualityProfileResource,
    ): Response<QualityProfileResource>

    @DELETE("api/v3/qualityprofile/{id}")
    suspend fun removeQualityProfile(
        @Path("id") id: Int,
    ): Response<Unit>
}
