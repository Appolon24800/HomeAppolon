package dev.appolon.homelab.arr.api.radarr

import dev.appolon.homelab.arr.api.radarr.model.ReleaseProfileResource
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import kotlin.jvm.JvmSuppressWildcards

/**
 * Radarr endpoints tagged ReleaseProfile. The spec types the PUT path id as a
 * string and the GET and DELETE path ids as integers.
 */
@JvmSuppressWildcards
interface ReleaseProfileApi {
    @GET("api/v3/releaseprofile")
    suspend fun releaseProfiles(): List<ReleaseProfileResource>

    @POST("api/v3/releaseprofile")
    suspend fun createReleaseProfile(
        @Body body: ReleaseProfileResource,
    ): Response<ReleaseProfileResource>

    /** The spec documents a 404 for an unknown profile id. */
    @GET("api/v3/releaseprofile/{id}")
    suspend fun releaseProfile(
        @Path("id") id: Int,
    ): Response<ReleaseProfileResource>

    @PUT("api/v3/releaseprofile/{id}")
    suspend fun updateReleaseProfile(
        @Path("id") id: String,
        @Body body: ReleaseProfileResource,
    ): Response<ReleaseProfileResource>

    @DELETE("api/v3/releaseprofile/{id}")
    suspend fun removeReleaseProfile(
        @Path("id") id: Int,
    ): Response<Unit>
}
