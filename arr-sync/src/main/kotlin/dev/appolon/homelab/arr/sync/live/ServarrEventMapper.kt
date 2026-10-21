package dev.appolon.homelab.arr.sync.live

import dev.appolon.homelab.arr.core.AppType
import dev.appolon.homelab.arr.core.ServerId
import dev.appolon.homelab.arr.sync.ArrEvent

/**
 * Maps Servarr SignalR `receiveMessage` payloads and Bazarr socket.io event
 * names onto [ArrEvent]. Every Servarr app broadcasts on one hub method and
 * carries the app-specific name inside the payload; this table is that
 * dictionary. Names outside the table drop on the floor: the hubs also emit
 * high-frequency chatter ("system", "command") that would only burn refresh
 * cycles downstream.
 */
internal object ServarrEventMapper {
    /** Wire names that carry a per-entity invalidation, by app family. */
    private val entityNames: Map<AppType, Set<String>> = mapOf(
        AppType.RADARR to setOf(
            "movie", "moviefile", "collection", "importlist", "indexer",
            "downloadclient", "notification", "qualityprofile", "delayprofile",
            "customformat", "restriction", "remotepathmapping", "blacklist", "tag",
        ),
        AppType.SONARR to setOf(
            "series", "episode", "episodefile", "seasonpass", "importlist", "indexer",
            "downloadclient", "notification", "qualityprofile", "delayprofile",
            "restriction", "remotepathmapping", "blacklist", "tag",
        ),
        AppType.LIDARR to setOf(
            "artist", "album", "track", "trackfile", "importlist", "indexer",
            "downloadclient", "notification", "qualityprofile", "delayprofile",
            "restriction", "remotepathmapping", "blacklist", "tag", "rootfolder",
        ),
        AppType.PROWLARR to setOf(
            "indexer", "indexerproxy", "downloadclient", "notification", "application", "tag",
        ),
        AppType.BAZARR to setOf(
            "movie", "series", "episode", "movieids_changed", "episodes_changed",
            "history", "languages",
        ),
    )

    /**
     * Returns the typed event for a wire message name, or null when the name is
     * unknown or pure chatter. [action] rides along for entity events, where it
     * distinguishes created/updated/deleted for repositories that care.
     */
    fun map(serverId: ServerId, appType: AppType, messageName: String?, action: String?): ArrEvent? {
        if (messageName == null) return null
        return when (messageName) {
            "queue" -> ArrEvent.QueueChanged(serverId)
            "calendar" -> ArrEvent.CalendarChanged(serverId)
            "health" -> ArrEvent.HealthChanged(serverId)
            "diskspace" -> ArrEvent.DiskSpaceChanged(serverId)
            in entityNames.getValue(appType) ->
                ArrEvent.EntitiesChanged(serverId, entity = messageName, action = action)
            else -> null
        }
    }
}
