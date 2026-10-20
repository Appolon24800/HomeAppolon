package dev.appolon.homelab.arr.sync.scheduler

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import dev.appolon.homelab.arr.core.ServerId
import dev.appolon.homelab.arr.data.ArrServer
import dev.appolon.homelab.arr.data.registry.NotificationChannels
import dev.appolon.homelab.arr.sync.R

/**
 * Resolves the per-server GlobalSettings toggle for one kind. Pure so JVM
 * tests cover the mapping without Android.
 */
fun NotificationKind.isEnabled(toggles: NotificationChannels): Boolean = when (this) {
    NotificationKind.GRAB -> toggles.grab
    NotificationKind.IMPORT -> toggles.import
    NotificationKind.FAIL -> toggles.fail
    NotificationKind.HEALTH -> toggles.health
    NotificationKind.DISK -> toggles.disk
}

/**
 * Suppresses repeats of identical notifications. Identity is
 * (server, kind, dedupeKey). Bounded memory with LRU eviction: an occurrence
 * whose key fell out of the window may notify again, which is the intended
 * escape valve for long-lived warnings. In-memory only, so dedupe resets with
 * the process; poll cadences of fifteen minutes and up make that acceptable.
 */
class NotificationDedupe(private val capacity: Int = DEFAULT_CAPACITY) {

    private val seen = LinkedHashMap<Key, Unit>(16, 0.75f, true)

    /**
     * Returns the subset of [events] not yet notified for [serverId], and
     * records them as notified. Callers post exactly what comes back.
     */
    fun filter(serverId: ServerId, events: List<PollEvent>): List<PollEvent> {
        val fresh = ArrayList<PollEvent>(events.size)
        for (event in events) {
            val key = Key(serverId.value, event.kind, event.dedupeKey)
            if (seen.put(key, Unit) == null) fresh += event
        }
        while (seen.size > capacity) {
            seen.remove(seen.keys.first())
        }
        return fresh
    }

    fun clear() = seen.clear()

    private data class Key(val serverId: String, val kind: NotificationKind, val dedupeKey: String)

    companion object {
        const val DEFAULT_CAPACITY = 96
    }
}

/**
 * Turns poll counts into summary events. Pure: text comes from injected
 * resolvers so JVM tests need no resources. Kinds that already carry granular
 * events from the adapter get no summary, so one occurrence never notifies
 * twice. Summary keys encode the count, so a changed count notifies again
 * while an unchanged situation stays silent.
 */
class SummaryPlanner(
    private val title: (NotificationKind) -> String,
    private val body: (NotificationKind, count: Int, serverName: String) -> String,
) {
    fun plan(server: ArrServer, result: PollResult): List<PollEvent> {
        val granularKinds = result.events.mapTo(mutableSetOf()) { it.kind }
        return NotificationKind.entries.mapNotNull { kind ->
            val count = result.countOf(kind)
            if (count <= 0 || kind in granularKinds) return@mapNotNull null
            PollEvent(
                kind = kind,
                title = title(kind),
                body = body(kind, count, server.name),
                dedupeKey = "summary:${kind.name}:$count",
            )
        }
    }
}

internal fun PollResult.countOf(kind: NotificationKind): Int = when (kind) {
    NotificationKind.GRAB -> newGrabs
    NotificationKind.IMPORT -> newImports
    NotificationKind.FAIL -> failures
    NotificationKind.HEALTH -> healthIssues
    NotificationKind.DISK -> diskWarnings
}

/**
 * Channel bootstrap in the NotificationChannelsCompat style: one channel per
 * [NotificationKind], created idempotently whenever sync starts. minSdk is 26,
 * so NotificationChannel needs no version guard.
 */
object ArrNotificationChannels {

    fun ensureCreated(context: Context, manager: NotificationManager) {
        for (kind in NotificationKind.entries) {
            if (manager.getNotificationChannel(kind.channelId) != null) continue
            val channel = NotificationChannel(kind.channelId, name(context, kind), importance(kind))
            channel.description = description(context, kind)
            manager.createNotificationChannel(channel)
        }
    }

    fun name(context: Context, kind: NotificationKind): String = when (kind) {
        NotificationKind.GRAB -> context.getString(R.string.arr_channel_grab_name)
        NotificationKind.IMPORT -> context.getString(R.string.arr_channel_import_name)
        NotificationKind.FAIL -> context.getString(R.string.arr_channel_fail_name)
        NotificationKind.HEALTH -> context.getString(R.string.arr_channel_health_name)
        NotificationKind.DISK -> context.getString(R.string.arr_channel_disk_name)
    }

    fun description(context: Context, kind: NotificationKind): String = when (kind) {
        NotificationKind.GRAB -> context.getString(R.string.arr_channel_grab_description)
        NotificationKind.IMPORT -> context.getString(R.string.arr_channel_import_description)
        NotificationKind.FAIL -> context.getString(R.string.arr_channel_fail_description)
        NotificationKind.HEALTH -> context.getString(R.string.arr_channel_health_description)
        NotificationKind.DISK -> context.getString(R.string.arr_channel_disk_description)
    }

    /** Failures interrupt; health drift can wait; the rest are routine. */
    private fun importance(kind: NotificationKind): Int = when (kind) {
        NotificationKind.GRAB -> NotificationManager.IMPORTANCE_DEFAULT
        NotificationKind.IMPORT -> NotificationManager.IMPORTANCE_DEFAULT
        NotificationKind.FAIL -> NotificationManager.IMPORTANCE_HIGH
        NotificationKind.HEALTH -> NotificationManager.IMPORTANCE_LOW
        NotificationKind.DISK -> NotificationManager.IMPORTANCE_DEFAULT
    }
}

/**
 * Builds the final system notification for one planned event. Thin on
 * purpose: everything testable happens before this in [SummaryPlanner],
 * toggle resolution, and [NotificationDedupe].
 */
class NotificationFactory(private val context: Context) {

    fun create(server: ArrServer, event: PollEvent): Notification {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(DEEP_LINK_PREFIX + server.id.value))
            .setPackage(context.packageName)
        val contentIntent = PendingIntent.getActivity(
            context,
            server.id.value.hashCode(),
            intent,
            PendingIntent.FLAG_IMMUTABLE,
        )
        return Notification.Builder(context, event.kind.channelId)
            .setSmallIcon(R.drawable.ic_arr_notification)
            .setContentTitle(event.title)
            .setContentText(event.body)
            .setStyle(Notification.BigTextStyle().bigText(event.body))
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .build()
    }

    /** Stable notification id for one occurrence on one server. */
    fun notificationId(server: ArrServer, event: PollEvent): Int {
        var h = server.id.value.hashCode()
        h = 31 * h + event.kind.name.hashCode()
        h = 31 * h + event.dedupeKey.hashCode()
        return h
    }

    companion object {
        /**
         * Placeholder deep link until :app owns the arr shell navigation
         * route. The intent stays in this app's package so it can only
         * resolve to our own activity.
         */
        const val DEEP_LINK_PREFIX = "homelab://arr/server/"
    }
}

/** Posting seam over NotificationManager so the worker stays testable. */
fun interface NotificationPoster {
    /**
     * Returns false when the system would suppress the notification:
     * notifications disabled app-wide (missing POST_NOTIFICATIONS grant on
     * API 33+), or the channel is blocked. Never throws for state reasons.
     */
    suspend fun post(
        notificationId: Int,
        kind: NotificationKind,
        notification: Notification,
    ): Boolean
}

internal class AndroidNotificationPoster(
    private val manager: NotificationManager,
) : NotificationPoster {
    override suspend fun post(
        notificationId: Int,
        kind: NotificationKind,
        notification: Notification,
    ): Boolean {
        if (!manager.areNotificationsEnabled()) return false
        val channel = manager.getNotificationChannel(kind.channelId)
        if (channel != null && channel.importance == NotificationManager.IMPORTANCE_NONE) {
            return false
        }
        manager.notify(notificationId, notification)
        return true
    }
}

/** Planner wired to this module's quantity strings. */
fun defaultSummaryPlanner(context: Context): SummaryPlanner = SummaryPlanner(
    title = { kind -> context.getString(SummaryTexts.titleRes(kind)) },
    body = { kind, count, serverName ->
        context.resources.getQuantityString(SummaryTexts.bodyRes(kind), count, count, serverName)
    },
)

private object SummaryTexts {
    fun titleRes(kind: NotificationKind): Int = when (kind) {
        NotificationKind.GRAB -> R.string.arr_summary_grab_title
        NotificationKind.IMPORT -> R.string.arr_summary_import_title
        NotificationKind.FAIL -> R.string.arr_summary_fail_title
        NotificationKind.HEALTH -> R.string.arr_summary_health_title
        NotificationKind.DISK -> R.string.arr_summary_disk_title
    }

    fun bodyRes(kind: NotificationKind): Int = when (kind) {
        NotificationKind.GRAB -> R.plurals.arr_summary_grab_body
        NotificationKind.IMPORT -> R.plurals.arr_summary_import_body
        NotificationKind.FAIL -> R.plurals.arr_summary_fail_body
        NotificationKind.HEALTH -> R.plurals.arr_summary_health_body
        NotificationKind.DISK -> R.plurals.arr_summary_disk_body
    }
}
