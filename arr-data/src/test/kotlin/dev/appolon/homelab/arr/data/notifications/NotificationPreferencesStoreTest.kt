package dev.appolon.homelab.arr.data.notifications

import dev.appolon.homelab.arr.core.EntityType
import dev.appolon.homelab.arr.core.ServerId
import dev.appolon.homelab.arr.data.testing.FakeEntityStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationPreferencesStoreTest {
    @Test
    fun disabledNotificationsStayDisabledAcrossStoreInstances() = runTest {
        val entities = FakeEntityStore()
        val settings = NotificationPreferencesStore(entities)
        assertTrue(settings.notificationsEnabled())

        settings.setNotificationsEnabled(false)

        val workerSettings = NotificationPreferencesStore(entities)
        assertFalse(workerSettings.notificationsEnabled())
        assertFalse(workerSettings.observeNotificationsEnabled().first())
        workerSettings.setNotificationsEnabled(true)
        assertTrue(settings.notificationsEnabled())
    }

    @Test
    fun readsTheExistingSettingsScreenPreference() = runTest {
        val entities = FakeEntityStore()
        entities.put(
            ServerId("__arr_servers_settings__"), EntityType("servers.settings"), "global",
            NotificationPreferences(false), NotificationPreferences.serializer(),
        )

        assertFalse(NotificationPreferencesStore(entities).notificationsEnabled())
    }
}
