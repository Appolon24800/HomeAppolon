package dev.appolon.homelab.arr.api.radarr

import kotlin.jvm.JvmSuppressWildcards

import dev.appolon.homelab.arr.api.radarr.model.MediaManagementConfigResource
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.PUT
import retrofit2.http.Path

/** Radarr endpoints tagged MediaManagementConfig. */
@JvmSuppressWildcards
interface MediaManagementConfigApi {
    @GET("api/v3/config/mediamanagement")
    suspend fun mediaManagementConfig(): MediaManagementConfigResource

    /** The spec declares this path id as a string. */
    @PUT("api/v3/config/mediamanagement/{id}")
    suspend fun updateMediaManagementConfig(
        @Path("id") id: String,
        @Body body: MediaManagementConfigResource,
    ): Response<MediaManagementConfigResource>

    @GET("api/v3/config/mediamanagement/{id}")
    suspend fun mediaManagementConfigById(
        @Path("id") id: Int,
    ): Response<MediaManagementConfigResource>
}
