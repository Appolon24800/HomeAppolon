package dev.appolon.homelab.arr.api.lidarr

import dev.appolon.homelab.arr.api.lidarr.model.QualityProfileResource
import kotlin.jvm.JvmSuppressWildcards
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path

@JvmSuppressWildcards
interface QualityProfileApi {
    @POST("api/v1/qualityprofile")
    suspend fun createQualityProfile(@Body resource: QualityProfileResource): QualityProfileResource

    @GET("api/v1/qualityprofile")
    suspend fun getQualityProfiles(): List<QualityProfileResource>

    @DELETE("api/v1/qualityprofile/{id}")
    suspend fun deleteQualityProfile(@Path("id") id: Int)

    @PUT("api/v1/qualityprofile/{id}")
    suspend fun updateQualityProfile(
        @Path("id") id: String,
        @Body resource: QualityProfileResource,
    ): QualityProfileResource

    @GET("api/v1/qualityprofile/{id}")
    suspend fun getQualityProfileById(@Path("id") id: Int): QualityProfileResource
}
