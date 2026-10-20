package dev.appolon.homelab.arr.api.lidarr

import dev.appolon.homelab.arr.api.lidarr.model.DownloadClientBulkResource
import dev.appolon.homelab.arr.api.lidarr.model.DownloadClientResource
import kotlin.jvm.JvmSuppressWildcards
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.HTTP
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Lidarr endpoints tagged DownloadClient. The bulk PUT response is typed as a
 * single resource in the spec even though it edits many.
 */
@JvmSuppressWildcards
interface DownloadClientApi {
    @GET("api/v1/downloadclient")
    suspend fun getDownloadClients(): List<DownloadClientResource>

    @GET("api/v1/downloadclient/{id}")
    suspend fun getDownloadClient(@Path("id") id: Int): DownloadClientResource

    @POST("api/v1/downloadclient")
    suspend fun addDownloadClient(
        @Query("forceSave") forceSave: Boolean? = null,
        @Body resource: DownloadClientResource,
    ): DownloadClientResource

    @PUT("api/v1/downloadclient/{id}")
    suspend fun updateDownloadClient(
        @Path("id") id: Int,
        @Query("forceSave") forceSave: Boolean? = null,
        @Body resource: DownloadClientResource,
    ): DownloadClientResource

    @DELETE("api/v1/downloadclient/{id}")
    suspend fun deleteDownloadClient(@Path("id") id: Int)

    @PUT("api/v1/downloadclient/bulk")
    suspend fun updateDownloadClients(
        @Body resource: DownloadClientBulkResource,
    ): DownloadClientResource

    @HTTP(method = "DELETE", path = "api/v1/downloadclient/bulk", hasBody = true)
    suspend fun deleteDownloadClients(@Body resource: DownloadClientBulkResource)

    @GET("api/v1/downloadclient/schema")
    suspend fun getDownloadClientSchema(): List<DownloadClientResource>

    @POST("api/v1/downloadclient/test")
    suspend fun testDownloadClient(
        @Query("forceTest") forceTest: Boolean? = null,
        @Body resource: DownloadClientResource,
    )

    @POST("api/v1/downloadclient/testall")
    suspend fun testAllDownloadClients()

    @POST("api/v1/downloadclient/action/{name}")
    suspend fun downloadClientAction(
        @Path("name") name: String,
        @Body resource: DownloadClientResource,
    )
}
