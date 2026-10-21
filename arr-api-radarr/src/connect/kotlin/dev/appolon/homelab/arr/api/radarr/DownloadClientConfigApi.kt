package dev.appolon.homelab.arr.api.radarr

import kotlin.jvm.JvmSuppressWildcards

import dev.appolon.homelab.arr.api.radarr.model.DownloadClientConfigResource
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.PUT
import retrofit2.http.Path

/** Radarr endpoints tagged DownloadClientConfig. */
@JvmSuppressWildcards
interface DownloadClientConfigApi {
    @GET("api/v3/config/downloadclient")
    suspend fun downloadClientConfig(): DownloadClientConfigResource

    @PUT("api/v3/config/downloadclient/{id}")
    suspend fun updateDownloadClientConfig(
        @Path("id") id: String,
        @Body body: DownloadClientConfigResource,
    ): Response<DownloadClientConfigResource>

    @GET("api/v3/config/downloadclient/{id}")
    suspend fun downloadClientConfigById(
        @Path("id") id: Int,
    ): Response<DownloadClientConfigResource>

}
