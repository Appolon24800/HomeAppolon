package dev.appolon.homelab.arr.api.sonarr

import dev.appolon.homelab.arr.api.sonarr.model.NotificationResource
import kotlin.jvm.JvmSuppressWildcards
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

@JvmSuppressWildcards
interface NotificationApi {
    @GET("api/v3/notification")
    suspend fun list(): List<NotificationResource>

    @POST("api/v3/notification")
    suspend fun create(
        @Query("forceSave") forceSave: Boolean? = null,
        @Body body: NotificationResource
    ): NotificationResource

    @PUT("api/v3/notification/{id}")
    suspend fun update(
        @Path("id") id: Int,
        @Query("forceSave") forceSave: Boolean? = null,
        @Body body: NotificationResource
    ): NotificationResource

    @DELETE("api/v3/notification/{id}")
    suspend fun delete(
        @Path("id") id: Int
    ): Unit

    @GET("api/v3/notification/{id}")
    suspend fun get(
        @Path("id") id: Int
    ): Response<NotificationResource>

    @GET("api/v3/notification/schema")
    suspend fun schema(): List<NotificationResource>

    @POST("api/v3/notification/test")
    suspend fun test(
        @Query("forceTest") forceTest: Boolean? = null,
        @Body body: NotificationResource
    ): Unit

    @POST("api/v3/notification/testall")
    suspend fun testAll(): Unit

    @POST("api/v3/notification/action/{name}")
    suspend fun action(
        @Path("name") name: String,
        @Body body: NotificationResource
    ): Unit
}
