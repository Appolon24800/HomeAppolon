package dev.appolon.homelab.arr.api.lidarr

import dev.appolon.homelab.arr.api.lidarr.model.NotificationResource
import kotlin.jvm.JvmSuppressWildcards
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Lidarr endpoints tagged Notification. Like MetadataApi, the update path id
 * is an integer in the spec.
 */
@JvmSuppressWildcards
interface NotificationApi {
    @GET("api/v1/notification")
    suspend fun getNotifications(): List<NotificationResource>

    @GET("api/v1/notification/{id}")
    suspend fun getNotification(@Path("id") id: Int): NotificationResource

    @POST("api/v1/notification")
    suspend fun addNotification(
        @Query("forceSave") forceSave: Boolean? = null,
        @Body resource: NotificationResource,
    ): NotificationResource

    @PUT("api/v1/notification/{id}")
    suspend fun updateNotification(
        @Path("id") id: Int,
        @Query("forceSave") forceSave: Boolean? = null,
        @Body resource: NotificationResource,
    ): NotificationResource

    @DELETE("api/v1/notification/{id}")
    suspend fun deleteNotification(@Path("id") id: Int)

    @GET("api/v1/notification/schema")
    suspend fun getNotificationSchema(): List<NotificationResource>

    @POST("api/v1/notification/test")
    suspend fun testNotification(
        @Query("forceTest") forceTest: Boolean? = null,
        @Body resource: NotificationResource,
    )

    @POST("api/v1/notification/testall")
    suspend fun testAllNotifications()

    @POST("api/v1/notification/action/{name}")
    suspend fun notificationAction(
        @Path("name") name: String,
        @Body resource: NotificationResource,
    )
}
