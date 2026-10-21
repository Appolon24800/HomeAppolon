package dev.appolon.homelab.arr.servers.notify

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import dev.appolon.homelab.arr.servers.R

/**
 * Posts the sample notification behind the preview action on the
 * notification preferences screen. Uses the framework builder because minSdk
 * 26 guarantees channels and this module has no core-ktx or :arr-sync
 * dependency. A missing POST_NOTIFICATIONS runtime grant turns notify() into
 * a silent no-op: the preview must never crash settings.
 */
object NotificationPreview {

    /** True when the app may show notifications at all; gates the preview button. */
    fun canNotify(context: Context): Boolean =
        context.getSystemService(NotificationManager::class.java)
            ?.areNotificationsEnabled() == true

    fun post(context: Context, serverName: String) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.arr_servers_notify_preview_channel),
                NotificationManager.IMPORTANCE_DEFAULT,
            ),
        )
        val notification = Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.arr_servers_notify_preview)
            .setContentTitle(context.getString(R.string.arr_servers_notify_preview_title))
            .setContentText(
                context.getString(R.string.arr_servers_notify_preview_text, serverName),
            )
            .setAutoCancel(true)
            .build()
        runCatching { manager.notify(NOTIFICATION_ID, notification) }
    }

    private const val CHANNEL_ID = "arr.servers.preview"
    private const val NOTIFICATION_ID = 4401
}
