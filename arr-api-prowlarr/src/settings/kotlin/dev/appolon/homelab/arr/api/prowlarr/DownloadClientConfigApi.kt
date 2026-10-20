package dev.appolon.homelab.arr.api.prowlarr

import dev.appolon.homelab.arr.api.prowlarr.model.DownloadClientConfigResource
import kotlin.jvm.JvmSuppressWildcards
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.PUT
import retrofit2.http.Path

/**
 * DownloadClientConfig tag: the download-client settings singleton. GET with
 * any id returns the one row; PUT path ids are strings because the spec
 * declares that type on update routes.
 */
@JvmSuppressWildcards
interface DownloadClientConfigApi {
    @GET("api/v1/config/downloadclient")
    suspend fun getDownloadClientConfig(): DownloadClientConfigResource

    @GET("api/v1/config/downloadclient/{id}")
    suspend fun getDownloadClientConfig(@Path("id") id: Int): DownloadClientConfigResource

    @PUT("api/v1/config/downloadclient/{id}")
    suspend fun updateDownloadClientConfig(
        @Path("id") id: String,
        @Body resource: DownloadClientConfigResource,
    ): DownloadClientConfigResource
}
