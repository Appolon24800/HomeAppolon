package dev.appolon.homelab.arr.feature.prowlarr.settings.data

import dev.appolon.homelab.arr.api.prowlarr.model.NotificationResource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationEventsTest {

    @Test
    fun `matrix lists only supported events in UI order`() {
        val resource = NotificationResource(
            supportsOnGrab = true,
            onGrab = true,
            supportsOnHealthIssue = true,
            supportsOnApplicationUpdate = true,
        )
        val keys = NotificationEvents.of(resource).map { it.key }
        assertEquals(
            listOf(
                "onGrab",
                "includeManualGrabs",
                "onHealthIssue",
                "includeHealthWarnings",
                "onApplicationUpdate",
            ),
            keys,
        )
    }

    @Test
    fun `unsupported events never render`() {
        val resource = NotificationResource(supportsOnGrab = true)
        val keys = NotificationEvents.of(resource).map { it.key }
        assertEquals(listOf("onGrab", "includeManualGrabs"), keys)
    }

    @Test
    fun `toggle flips one flag and leaves the rest`() {
        var resource = NotificationResource(
            onGrab = true,
            onHealthIssue = false,
            includeManualGrabs = false,
        )
        resource = NotificationEvents.toggle(resource, "includeManualGrabs")
        assertTrue(resource.includeManualGrabs)
        assertTrue(resource.onGrab)
        resource = NotificationEvents.toggle(resource, "onGrab")
        assertEquals(false, resource.onGrab)
    }

    @Test
    fun `unknown keys are ignored`() {
        val resource = NotificationResource(onGrab = true)
        assertEquals(resource, NotificationEvents.toggle(resource, "onRename"))
    }

    @Test
    fun `summary lists enabled top-level events`() {
        val resource = NotificationResource(
            supportsOnGrab = true,
            onGrab = true,
            includeManualGrabs = true,
            supportsOnHealthRestored = true,
            onHealthRestored = true,
        )
        assertEquals(listOf("onGrab", "onHealthRestored"), NotificationEvents.summary(resource))
    }
}
