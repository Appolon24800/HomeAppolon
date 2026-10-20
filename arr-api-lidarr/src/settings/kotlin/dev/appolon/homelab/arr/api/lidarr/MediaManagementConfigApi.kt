package dev.appolon.homelab.arr.api.lidarr

import dev.appolon.homelab.arr.api.lidarr.model.MediaManagementConfigResource
import kotlin.jvm.JvmSuppressWildcards
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.PUT
import retrofit2.http.Path

/**
 * Lidarr endpoints tagged MediaManagementConfig. The update path id is a string
 * in the spec, unlike the integer id on the single get.
 */
@JvmSuppressWildcards
interface MediaManagementConfigApi {
    @GET("api/v1/config/mediamanagement")
    suspend fun getMediaManagementConfig(): MediaManagementConfigResource

    @GET("api/v1/config/mediamanagement/{id}")
    suspend fun getMediaManagementConfigById(
        @Path("id") id: Int,
    ): MediaManagementConfigResource

    @PUT("api/v1/config/mediamanagement/{id}")
    suspend fun updateMediaManagementConfig(
        @Path("id") id: String,
        @Body resource: MediaManagementConfigResource,
    ): MediaManagementConfigResource
}
