package dev.appolon.homelab.arr.api.sonarr

import dev.appolon.homelab.arr.api.sonarr.model.ReleaseProfileResource
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path

/** Operations in the Sonarr ReleaseProfile OpenAPI tag. */
@JvmSuppressWildcards
interface ReleaseProfileApi {
    @POST("api/v3/releaseprofile")
    suspend fun create(
        @Body body: ReleaseProfileResource
    ): ReleaseProfileResource

    @GET("api/v3/releaseprofile")
    suspend fun list(): List<ReleaseProfileResource>

    @DELETE("api/v3/releaseprofile/{id}")
    suspend fun delete(
        @Path("id") id: Int
    ): Unit

    @PUT("api/v3/releaseprofile/{id}")
    suspend fun update(
        @Path("id") id: String,
        @Body body: ReleaseProfileResource
    ): ReleaseProfileResource

    @GET("api/v3/releaseprofile/{id}")
    suspend fun get(
        @Path("id") id: Int
    ): Response<ReleaseProfileResource>
}
