package dev.appolon.homelab.arr.api.sonarr

import dev.appolon.homelab.arr.api.sonarr.model.QualityProfileResource
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path

/** Operations in the Sonarr QualityProfile OpenAPI tag. */
@JvmSuppressWildcards
interface QualityProfileApi {
    @POST("api/v3/qualityprofile")
    suspend fun create(
        @Body body: QualityProfileResource
    ): QualityProfileResource

    @GET("api/v3/qualityprofile")
    suspend fun list(): List<QualityProfileResource>

    @DELETE("api/v3/qualityprofile/{id}")
    suspend fun delete(
        @Path("id") id: Int
    ): Unit

    @PUT("api/v3/qualityprofile/{id}")
    suspend fun update(
        @Path("id") id: String,
        @Body body: QualityProfileResource
    ): QualityProfileResource

    @GET("api/v3/qualityprofile/{id}")
    suspend fun get(
        @Path("id") id: Int
    ): Response<QualityProfileResource>
}
