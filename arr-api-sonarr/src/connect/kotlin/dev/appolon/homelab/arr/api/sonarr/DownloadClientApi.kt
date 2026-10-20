package dev.appolon.homelab.arr.api.sonarr

import dev.appolon.homelab.arr.api.sonarr.model.DownloadClientBulkResource
import dev.appolon.homelab.arr.api.sonarr.model.DownloadClientResource
import kotlin.jvm.JvmSuppressWildcards
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.HTTP
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

@JvmSuppressWildcards
interface DownloadClientApi {
    @GET("api/v3/downloadclient")
    suspend fun list(): List<DownloadClientResource>

    @POST("api/v3/downloadclient")
    suspend fun create(
        @Query("forceSave") forceSave: Boolean? = null,
        @Body body: DownloadClientResource
    ): DownloadClientResource

    @PUT("api/v3/downloadclient/{id}")
    suspend fun update(
        @Path("id") id: Int,
        @Query("forceSave") forceSave: Boolean? = null,
        @Body body: DownloadClientResource
    ): DownloadClientResource

    @DELETE("api/v3/downloadclient/{id}")
    suspend fun delete(
        @Path("id") id: Int
    ): Unit

    @GET("api/v3/downloadclient/{id}")
    suspend fun get(
        @Path("id") id: Int
    ): Response<DownloadClientResource>

    @PUT("api/v3/downloadclient/bulk")
    suspend fun updateBulk(
        @Body body: DownloadClientBulkResource
    ): DownloadClientResource

    @HTTP(method = "DELETE", path = "api/v3/downloadclient/bulk", hasBody = true)
    suspend fun deleteBulk(
        @Body body: DownloadClientBulkResource
    ): Unit

    @GET("api/v3/downloadclient/schema")
    suspend fun schema(): List<DownloadClientResource>

    @POST("api/v3/downloadclient/test")
    suspend fun test(
        @Query("forceTest") forceTest: Boolean? = null,
        @Body body: DownloadClientResource
    ): Unit

    @POST("api/v3/downloadclient/testall")
    suspend fun testAll(): Unit

    @POST("api/v3/downloadclient/action/{name}")
    suspend fun action(
        @Path("name") name: String,
        @Body body: DownloadClientResource
    ): Unit
}
