package dev.appolon.homelab.arr.api.lidarr

import dev.appolon.homelab.arr.api.lidarr.model.DelayProfileResource
import kotlin.jvm.JvmSuppressWildcards
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

@JvmSuppressWildcards
interface DelayProfileApi {
    @POST("api/v1/delayprofile")
    suspend fun createDelayProfile(@Body resource: DelayProfileResource): DelayProfileResource

    @GET("api/v1/delayprofile")
    suspend fun getDelayProfiles(): List<DelayProfileResource>

    @DELETE("api/v1/delayprofile/{id}")
    suspend fun deleteDelayProfile(@Path("id") id: Int)

    @PUT("api/v1/delayprofile/{id}")
    suspend fun updateDelayProfile(
        @Path("id") id: String,
        @Body resource: DelayProfileResource,
    ): DelayProfileResource

    @GET("api/v1/delayprofile/{id}")
    suspend fun getDelayProfileById(@Path("id") id: Int): DelayProfileResource

    @PUT("api/v1/delayprofile/reorder/{id}")
    suspend fun reorderDelayProfile(@Path("id") id: Int, @Query("afterId") afterId: Int? = null)
}
