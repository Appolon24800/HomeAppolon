package dev.appolon.homelab.arr.api.lidarr

import dev.appolon.homelab.arr.api.lidarr.model.ReleaseProfileResource
import kotlin.jvm.JvmSuppressWildcards
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path

@JvmSuppressWildcards
interface ReleaseProfileApi {
    @GET("api/v1/releaseprofile/{id}")
    suspend fun getReleaseProfileById(@Path("id") id: Int): ReleaseProfileResource

    @PUT("api/v1/releaseprofile/{id}")
    suspend fun updateReleaseProfile(
        @Path("id") id: String,
        @Body resource: ReleaseProfileResource,
    ): ReleaseProfileResource

    @DELETE("api/v1/releaseprofile/{id}")
    suspend fun deleteReleaseProfile(@Path("id") id: Int)

    @GET("api/v1/releaseprofile")
    suspend fun getReleaseProfiles(): List<ReleaseProfileResource>

    @POST("api/v1/releaseprofile")
    suspend fun createReleaseProfile(@Body resource: ReleaseProfileResource): ReleaseProfileResource
}
