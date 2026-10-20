package dev.appolon.homelab.arr.api.radarr

import kotlin.jvm.JvmSuppressWildcards

import dev.appolon.homelab.arr.api.radarr.model.NotificationResource
import dev.appolon.homelab.arr.api.radarr.model.ProviderTestAllResult
import kotlinx.serialization.json.JsonObject
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

/** Radarr endpoints tagged Notification. */
@JvmSuppressWildcards
interface NotificationApi {
    @GET("api/v3/notification")
    suspend fun notifications(): List<NotificationResource>

    @POST("api/v3/notification")
    suspend fun createNotification(
        @Query("forceSave") forceSave: Boolean? = null,
        @Body body: NotificationResource,
    ): Response<NotificationResource>

    @PUT("api/v3/notification/{id}")
    suspend fun updateNotification(
        @Path("id") id: Int,
        @Query("forceSave") forceSave: Boolean? = null,
        @Body body: NotificationResource,
    ): Response<NotificationResource>

    /** The spec leaves the response schema unspecified. */
    @DELETE("api/v3/notification/{id}")
    suspend fun removeNotification(
        @Path("id") id: Int,
    ): JsonObject

    @GET("api/v3/notification/{id}")
    suspend fun notification(
        @Path("id") id: Int,
    ): Response<NotificationResource>

    @GET("api/v3/notification/schema")
    suspend fun notificationSchema(): List<NotificationResource>

    @POST("api/v3/notification/test")
    suspend fun testNotification(
        @Query("forceTest") forceTest: Boolean? = null,
        @Body body: NotificationResource,
    ): Response<Unit>

    @POST("api/v3/notification/testall")
    suspend fun testAllNotifications(): Response<List<ProviderTestAllResult>>

    /** The spec documents only a 400 response, so success carries no body. */
    @POST("api/v3/notification/action/{name}")
    suspend fun notificationAction(
        @Path("name") name: String,
        @Body body: NotificationResource,
    ): Response<Unit>

}
