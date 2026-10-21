package dev.appolon.homelab.arr.api.radarr

import kotlin.jvm.JvmSuppressWildcards

import dev.appolon.homelab.arr.api.radarr.model.DownloadClientBulkResource
import dev.appolon.homelab.arr.api.radarr.model.DownloadClientResource
import dev.appolon.homelab.arr.api.radarr.model.ProviderTestAllResult
import kotlinx.serialization.json.JsonObject
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.HTTP
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

/** Radarr endpoints tagged DownloadClient. */
@JvmSuppressWildcards
interface DownloadClientApi {
    @GET("api/v3/downloadclient")
    suspend fun downloadClients(): List<DownloadClientResource>

    @POST("api/v3/downloadclient")
    suspend fun createDownloadClient(
        @Query("forceSave") forceSave: Boolean? = null,
        @Body body: DownloadClientResource,
    ): Response<DownloadClientResource>

    @PUT("api/v3/downloadclient/{id}")
    suspend fun updateDownloadClient(
        @Path("id") id: Int,
        @Query("forceSave") forceSave: Boolean? = null,
        @Body body: DownloadClientResource,
    ): Response<DownloadClientResource>

    /** The spec leaves the response schema unspecified. */
    @DELETE("api/v3/downloadclient/{id}")
    suspend fun removeDownloadClient(
        @Path("id") id: Int,
    ): JsonObject

    @GET("api/v3/downloadclient/{id}")
    suspend fun downloadClient(
        @Path("id") id: Int,
    ): Response<DownloadClientResource>

    @PUT("api/v3/downloadclient/bulk")
    suspend fun updateDownloadClientBulk(
        @Body body: DownloadClientBulkResource,
    ): Response<List<DownloadClientResource>>

    /** The spec leaves the response schema unspecified. */
    @HTTP(method = "DELETE", path = "api/v3/downloadclient/bulk", hasBody = true)
    suspend fun removeDownloadClientBulk(
        @Body body: DownloadClientBulkResource,
    ): JsonObject

    @GET("api/v3/downloadclient/schema")
    suspend fun downloadClientSchema(): List<DownloadClientResource>

    @POST("api/v3/downloadclient/test")
    suspend fun testDownloadClient(
        @Query("forceTest") forceTest: Boolean? = null,
        @Body body: DownloadClientResource,
    ): Response<Unit>

    @POST("api/v3/downloadclient/testall")
    suspend fun testAllDownloadClients(): Response<List<ProviderTestAllResult>>

    /** The spec documents only a 400 response, so success carries no body. */
    @POST("api/v3/downloadclient/action/{name}")
    suspend fun downloadClientAction(
        @Path("name") name: String,
        @Body body: DownloadClientResource,
    ): Response<Unit>

}
