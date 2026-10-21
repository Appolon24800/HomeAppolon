package dev.appolon.homelab.arr.api.prowlarr

import dev.appolon.homelab.arr.api.prowlarr.model.AppProfileResource
import kotlin.jvm.JvmSuppressWildcards
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path

/**
 * AppProfile tag: CRUD on application profiles plus the single-item schema
 * template. [getAppProfile] returns [Response] because the spec documents 404
 * and 500 alongside 200, so the not-found status carries meaning. PUT path ids
 * are strings because the spec declares that type on update routes.
 */
@JvmSuppressWildcards
interface AppProfileApi {
    @GET("api/v1/appprofile")
    suspend fun getAppProfiles(): List<AppProfileResource>

    @GET("api/v1/appprofile/{id}")
    suspend fun getAppProfile(@Path("id") id: Int): Response<AppProfileResource>

    @GET("api/v1/appprofile/schema")
    suspend fun getAppProfileSchema(): AppProfileResource

    @POST("api/v1/appprofile")
    suspend fun createAppProfile(@Body resource: AppProfileResource): AppProfileResource

    @PUT("api/v1/appprofile/{id}")
    suspend fun updateAppProfile(
        @Path("id") id: String,
        @Body resource: AppProfileResource,
    ): AppProfileResource

    @DELETE("api/v1/appprofile/{id}")
    suspend fun deleteAppProfile(@Path("id") id: Int): Response<Unit>
}
