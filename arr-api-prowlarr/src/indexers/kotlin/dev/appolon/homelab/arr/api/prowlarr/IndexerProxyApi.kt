package dev.appolon.homelab.arr.api.prowlarr

import dev.appolon.homelab.arr.api.prowlarr.model.IndexerProxyResource
import kotlin.jvm.JvmSuppressWildcards
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * IndexerProxy tag: CRUD on request proxies plus schemas and test routes. PUT
 * path ids are strings because the spec declares that type on update routes.
 */
@JvmSuppressWildcards
interface IndexerProxyApi {
    @GET("api/v1/indexerproxy")
    suspend fun getIndexerProxies(): List<IndexerProxyResource>

    @GET("api/v1/indexerproxy/{id}")
    suspend fun getIndexerProxy(@Path("id") id: Int): IndexerProxyResource

    @GET("api/v1/indexerproxy/schema")
    suspend fun getIndexerProxySchemas(): List<IndexerProxyResource>

    @POST("api/v1/indexerproxy")
    suspend fun createIndexerProxy(
        @Query("forceSave") forceSave: Boolean? = null,
        @Body resource: IndexerProxyResource,
    ): IndexerProxyResource

    @PUT("api/v1/indexerproxy/{id}")
    suspend fun updateIndexerProxy(
        @Path("id") id: String,
        @Query("forceSave") forceSave: Boolean? = null,
        @Body resource: IndexerProxyResource,
    ): IndexerProxyResource

    @DELETE("api/v1/indexerproxy/{id}")
    suspend fun deleteIndexerProxy(@Path("id") id: Int): Response<Unit>

    @POST("api/v1/indexerproxy/test")
    suspend fun testIndexerProxy(
        @Query("forceTest") forceTest: Boolean? = null,
        @Body resource: IndexerProxyResource,
    ): Response<Unit>

    @POST("api/v1/indexerproxy/testall")
    suspend fun testAllIndexerProxies(): Response<Unit>

    @POST("api/v1/indexerproxy/action/{name}")
    suspend fun indexerProxyAction(
        @Path("name") name: String,
        @Body resource: IndexerProxyResource,
    ): Response<Unit>
}
