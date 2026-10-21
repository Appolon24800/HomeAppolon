package dev.appolon.homelab.arr.api.prowlarr

import dev.appolon.homelab.arr.api.prowlarr.model.DownloadClientBulkResource
import dev.appolon.homelab.arr.api.prowlarr.model.DownloadClientResource
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

/**
 * DownloadClient tag: CRUD on configured download clients, the definition
 * schemas, and the test or named-action escapes. PUT path ids are strings
 * because the spec declares that type on update routes. Body-bearing calls
 * return the decoded resource directly; body-less calls return [Response]
 * because the status code is the whole result. Bulk delete uses [HTTP]
 * because Retrofit otherwise rejects a body on DELETE even though the spec
 * documents one.
 */
@JvmSuppressWildcards
interface DownloadClientApi {
    @GET("api/v1/downloadclient")
    suspend fun getDownloadClients(): List<DownloadClientResource>

    @GET("api/v1/downloadclient/{id}")
    suspend fun getDownloadClient(@Path("id") id: Int): DownloadClientResource

    @GET("api/v1/downloadclient/schema")
    suspend fun getDownloadClientSchemas(): List<DownloadClientResource>

    @POST("api/v1/downloadclient")
    suspend fun createDownloadClient(
        @Query("forceSave") forceSave: Boolean? = null,
        @Body resource: DownloadClientResource,
    ): DownloadClientResource

    @PUT("api/v1/downloadclient/{id}")
    suspend fun updateDownloadClient(
        @Path("id") id: String,
        @Query("forceSave") forceSave: Boolean? = null,
        @Body resource: DownloadClientResource,
    ): DownloadClientResource

    @PUT("api/v1/downloadclient/bulk")
    suspend fun updateDownloadClients(
        @Body resource: DownloadClientBulkResource,
    ): DownloadClientResource

    @DELETE("api/v1/downloadclient/{id}")
    suspend fun deleteDownloadClient(@Path("id") id: Int): Response<Unit>

    @HTTP(method = "DELETE", path = "api/v1/downloadclient/bulk", hasBody = true)
    suspend fun deleteDownloadClients(
        @Body resource: DownloadClientBulkResource,
    ): Response<Unit>

    @POST("api/v1/downloadclient/test")
    suspend fun testDownloadClient(
        @Query("forceTest") forceTest: Boolean? = null,
        @Body resource: DownloadClientResource,
    ): Response<Unit>

    @POST("api/v1/downloadclient/testall")
    suspend fun testAllDownloadClients(): Response<Unit>

    @POST("api/v1/downloadclient/action/{name}")
    suspend fun downloadClientAction(
        @Path("name") name: String,
        @Body resource: DownloadClientResource,
    ): Response<Unit>
}
