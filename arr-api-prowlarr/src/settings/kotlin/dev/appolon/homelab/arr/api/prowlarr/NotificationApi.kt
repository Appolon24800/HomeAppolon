package dev.appolon.homelab.arr.api.prowlarr

import dev.appolon.homelab.arr.api.prowlarr.model.NotificationResource
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
 * Notification tag: CRUD on notification instances plus the definition
 * schemas and test or named-action escapes. Prowlarr exposes no bulk routes
 * here. PUT path ids are strings because the spec declares that type on
 * update routes. Body-less calls return [Response] because the status code
 * is the whole result.
 */
@JvmSuppressWildcards
interface NotificationApi {
    @GET("api/v1/notification")
    suspend fun getNotifications(): List<NotificationResource>

    @GET("api/v1/notification/{id}")
    suspend fun getNotification(@Path("id") id: Int): NotificationResource

    @GET("api/v1/notification/schema")
    suspend fun getNotificationSchemas(): List<NotificationResource>

    @POST("api/v1/notification")
    suspend fun createNotification(
        @Query("forceSave") forceSave: Boolean? = null,
        @Body resource: NotificationResource,
    ): NotificationResource

    @PUT("api/v1/notification/{id}")
    suspend fun updateNotification(
        @Path("id") id: String,
        @Query("forceSave") forceSave: Boolean? = null,
        @Body resource: NotificationResource,
    ): NotificationResource

    @DELETE("api/v1/notification/{id}")
    suspend fun deleteNotification(@Path("id") id: Int): Response<Unit>

    @POST("api/v1/notification/test")
    suspend fun testNotification(
        @Query("forceTest") forceTest: Boolean? = null,
        @Body resource: NotificationResource,
    ): Response<Unit>

    @POST("api/v1/notification/testall")
    suspend fun testAllNotifications(): Response<Unit>

    @POST("api/v1/notification/action/{name}")
    suspend fun notificationAction(
        @Path("name") name: String,
        @Body resource: NotificationResource,
    ): Response<Unit>
}
