package dev.appolon.homelab.arr.feature.prowlarr.settings.data

import dev.appolon.homelab.arr.api.prowlarr.model.NotificationResource

/**
 * One event-matrix toggle of a Prowlarr notification: the wire flag, whether
 * the implementation supports it, and the current state. Sub-toggles (manual
 * grabs under onGrab, warnings under onHealthIssue) render as their own rows.
 */
data class NotificationEventToggle(
    val key: String,
    val supported: Boolean,
    val enabled: Boolean,
    val isSubToggle: Boolean = false,
)

/** Pure builders for the notification event matrix, shared by VM and tests. */
object NotificationEvents {

    /** The matrix rows in Prowlarr UI order; unsupported events are excluded. */
    fun of(resource: NotificationResource): List<NotificationEventToggle> = listOf(
        NotificationEventToggle("onGrab", resource.supportsOnGrab, resource.onGrab),
        NotificationEventToggle(
            "includeManualGrabs",
            resource.supportsOnGrab,
            resource.includeManualGrabs,
            isSubToggle = true,
        ),
        NotificationEventToggle(
            "onHealthIssue",
            resource.supportsOnHealthIssue,
            resource.onHealthIssue,
        ),
        NotificationEventToggle(
            "includeHealthWarnings",
            resource.supportsOnHealthIssue,
            resource.includeHealthWarnings,
            isSubToggle = true,
        ),
        NotificationEventToggle(
            "onHealthRestored",
            resource.supportsOnHealthRestored,
            resource.onHealthRestored,
        ),
        NotificationEventToggle(
            "onApplicationUpdate",
            resource.supportsOnApplicationUpdate,
            resource.onApplicationUpdate,
        ),
    ).filter { it.supported }

    /** Flips one event flag on the resource by its wire key. */
    fun toggle(resource: NotificationResource, key: String): NotificationResource =
        when (key) {
            "onGrab" -> resource.copy(onGrab = !resource.onGrab)
            "includeManualGrabs" -> resource.copy(includeManualGrabs = !resource.includeManualGrabs)
            "onHealthIssue" -> resource.copy(onHealthIssue = !resource.onHealthIssue)
            "includeHealthWarnings" ->
                resource.copy(includeHealthWarnings = !resource.includeHealthWarnings)

            "onHealthRestored" -> resource.copy(onHealthRestored = !resource.onHealthRestored)
            "onApplicationUpdate" ->
                resource.copy(onApplicationUpdate = !resource.onApplicationUpdate)

            else -> resource
        }

    /** True when any supported event is on, for list-row summaries. */
    fun summary(resource: NotificationResource): List<String> =
        of(resource).filter { it.enabled && !it.isSubToggle }.map { it.key }
}
