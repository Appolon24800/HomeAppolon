package dev.appolon.homelab.arr.data.registry

import dev.appolon.homelab.arr.core.ServerId
import dev.appolon.homelab.arr.net.ProxyConfig
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GlobalSettingsTest {

    private val store = FakePreferencesStore()
    private val settings = GlobalSettings(store)

    @Test
    fun unsetSettingsFallBackToDefaults() = runTest {
        assertEquals(ProxyConfig(), settings.defaultProxy())
        assertEquals(NotificationChannels(), settings.notificationChannels(ServerId("s1")))
        assertEquals(0, settings.storedSchemaVersion())
    }

    @Test
    fun defaultProxyRoundTrips() = runTest {
        val proxy = ProxyConfig(
            type = ProxyConfig.Type.SOCKS5,
            host = "127.0.0.1",
            port = 9050,
            username = "user",
            password = "pass",
            useOrbot = true,
        )

        settings.setDefaultProxy(proxy)

        assertEquals(proxy, settings.defaultProxy())
        assertEquals(proxy, settings.observeDefaultProxy().first())
    }

    @Test
    fun notificationChannelsArePerServerAndClearable() = runTest {
        val radarr = ServerId("radarr")
        val sonarr = ServerId("sonarr")

        settings.setNotificationChannels(radarr, NotificationChannels(grab = false, disk = false))
        settings.setNotificationChannels(sonarr, NotificationChannels(import = false))

        val radarrChannels = settings.notificationChannels(radarr)
        assertFalse(radarrChannels.grab)
        assertTrue(radarrChannels.import)
        assertFalse(radarrChannels.disk)
        assertTrue(settings.notificationChannels(sonarr).grab)
        assertFalse(settings.notificationChannels(sonarr).import)

        settings.clearServer(radarr)
        assertEquals(NotificationChannels(), settings.notificationChannels(radarr))
        assertFalse(settings.notificationChannels(sonarr).import)
    }

    @Test
    fun observeNotificationChannelsEmitsUpdates() = runTest {
        val serverId = ServerId("s1")
        settings.setNotificationChannels(serverId, NotificationChannels(health = false))

        val current = settings.observeNotificationChannels(serverId).first()

        assertEquals(false, current.health)
    }

    @Test
    fun ensureSchemaVersionStampsOnceAndStaysIdempotent() = runTest {
        settings.ensureSchemaVersion()
        assertEquals(GlobalSettings.SCHEMA_VERSION, settings.storedSchemaVersion())

        // A second run must not downgrade or churn storage.
        settings.ensureSchemaVersion()
        assertEquals(GlobalSettings.SCHEMA_VERSION, settings.storedSchemaVersion())
        assertTrue(GlobalSettings.SCHEMA_VERSION >= 1)
    }
}
