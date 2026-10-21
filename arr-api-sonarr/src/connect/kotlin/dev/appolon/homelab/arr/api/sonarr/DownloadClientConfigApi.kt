package dev.appolon.homelab.arr.api.sonarr

import dev.appolon.homelab.arr.api.sonarr.model.DownloadClientConfigResource
import kotlin.jvm.JvmSuppressWildcards
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.PUT
import retrofit2.http.Path

@JvmSuppressWildcards
interface DownloadClientConfigApi {
    @GET("api/v3/config/downloadclient")
    suspend fun get(): DownloadClientConfigResource

    @PUT("api/v3/config/downloadclient/{id}")
    suspend fun update(
        @Path("id") id: String,
        @Body body: DownloadClientConfigResource
    ): DownloadClientConfigResource

    @GET("api/v3/config/downloadclient/{id}")
    suspend fun get(
        @Path("id") id: Int
    ): Response<DownloadClientConfigResource>
}
