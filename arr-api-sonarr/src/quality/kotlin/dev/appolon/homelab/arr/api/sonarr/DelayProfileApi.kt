package dev.appolon.homelab.arr.api.sonarr

import dev.appolon.homelab.arr.api.sonarr.model.DelayProfileResource
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

/** Operations in the Sonarr DelayProfile OpenAPI tag. */
@JvmSuppressWildcards
interface DelayProfileApi {
    @POST("api/v3/delayprofile")
    suspend fun create(
        @Body body: DelayProfileResource
    ): DelayProfileResource

    @GET("api/v3/delayprofile")
    suspend fun list(): List<DelayProfileResource>

    @PUT("api/v3/delayprofile/reorder/{id}")
    suspend fun reorder(
        @Path("id") id: Int,
        @Query("after") after: Int? = null
    ): List<DelayProfileResource>

    @DELETE("api/v3/delayprofile/{id}")
    suspend fun delete(
        @Path("id") id: Int
    ): Unit

    @PUT("api/v3/delayprofile/{id}")
    suspend fun update(
        @Path("id") id: String,
        @Body body: DelayProfileResource
    ): DelayProfileResource

    @GET("api/v3/delayprofile/{id}")
    suspend fun get(
        @Path("id") id: Int
    ): Response<DelayProfileResource>
}
