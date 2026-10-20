package dev.appolon.homelab.arr.api.lidarr

import dev.appolon.homelab.arr.api.lidarr.model.DownloadClientConfigResource
import kotlin.jvm.JvmSuppressWildcards
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.PUT
import retrofit2.http.Path

/**
 * Lidarr endpoints tagged DownloadClientConfig. The update path id is a string
 * in the spec, unlike the integer id on the single get.
 */
@JvmSuppressWildcards
interface DownloadClientConfigApi {
    @GET("api/v1/config/downloadclient")
    suspend fun getDownloadClientConfig(): DownloadClientConfigResource

    @GET("api/v1/config/downloadclient/{id}")
    suspend fun getDownloadClientConfigById(
        @Path("id") id: Int,
    ): DownloadClientConfigResource

    @PUT("api/v1/config/downloadclient/{id}")
    suspend fun updateDownloadClientConfig(
        @Path("id") id: String,
        @Body resource: DownloadClientConfigResource,
    ): DownloadClientConfigResource
}
