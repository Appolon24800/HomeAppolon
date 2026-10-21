package dev.appolon.homelab.arr.api.sonarr

import dev.appolon.homelab.arr.api.sonarr.model.MediaManagementConfigResource
import kotlin.jvm.JvmSuppressWildcards
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.PUT
import retrofit2.http.Path

/** Operations in the Sonarr MediaManagementConfig OpenAPI tag. */
@JvmSuppressWildcards
interface MediaManagementConfigApi {
    @GET("api/v3/config/mediamanagement")
    suspend fun get(): MediaManagementConfigResource

    @PUT("api/v3/config/mediamanagement/{id}")
    suspend fun update(
        @Path("id") id: String,
        @Body body: MediaManagementConfigResource
    ): MediaManagementConfigResource

    @GET("api/v3/config/mediamanagement/{id}")
    suspend fun get(@Path("id") id: Int): Response<MediaManagementConfigResource>
}
