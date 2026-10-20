package dev.appolon.homelab.arr.sync.scheduler

import dev.appolon.homelab.arr.data.registry.NotificationChannels
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationTogglesTest {

    @Test
    fun everyKindDefaultsToEnabled() {
        val toggles = NotificationChannels()
        for (kind in NotificationKind.entries) {
            assertTrue("expected ${kind} enabled by default", kind.isEnabled(toggles))
        }
    }

    @Test
    fun eachToggleDisablesOnlyItsOwnKind() {
        for (kind in NotificationKind.entries) {
            val toggles = NotificationChannels(
                grab = kind != NotificationKind.GRAB,
                import = kind != NotificationKind.IMPORT,
                fail = kind != NotificationKind.FAIL,
                health = kind != NotificationKind.HEALTH,
                disk = kind != NotificationKind.DISK,
            )
            assertFalse(kind.isEnabled(toggles))
            for (other in NotificationKind.entries) {
                if (other != kind) assertTrue(other.isEnabled(toggles))
            }
        }
    }

    @Test
    fun allDisabledSuppressesEveryKind() {
        val toggles = NotificationChannels(
            grab = false,
            import = false,
            fail = false,
            health = false,
            disk = false,
        )
        for (kind in NotificationKind.entries) {
            assertFalse(kind.isEnabled(toggles))
        }
    }
}
