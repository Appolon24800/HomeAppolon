package dev.appolon.homelab.arr.data.registry

import dev.appolon.homelab.arr.core.ServerId
import dev.appolon.homelab.arr.data.ArrDataJson
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
    fun defaultProxyPasswordIsEncryptedAtRestAndUsableOnRead() = runTest {
        val store = FakePreferencesStore()
        val secrets = FakeArrSecrets()
        val encrypted = GlobalSettings(store, secrets)
        val proxy = ProxyConfig(
            type = ProxyConfig.Type.HTTP,
            host = "proxy.lan",
            port = 3128,
            username = "user",
            password = "plain-proxy-pass",
        )

        encrypted.setDefaultProxy(proxy)

        val atRest = ArrDataJson.decodeFromString(
            ProxyConfig.serializer(),
            store.strings.getValue("arr_global_v1_default_proxy"),
        )
        assertEquals("v1:fake:plain-proxy-pass", atRest.password)

        assertEquals("plain-proxy-pass", encrypted.defaultProxy().password)
        assertEquals("plain-proxy-pass", encrypted.observeDefaultProxy().first().password)
        assertEquals(proxy, encrypted.defaultProxy())
    }

    @Test
    fun defaultProxyPasswordSurvivesRewritesWithoutDoubleEncrypting() = runTest {
        val store = FakePreferencesStore()
        val secrets = FakeArrSecrets()
        val encrypted = GlobalSettings(store, secrets)
        encrypted.setDefaultProxy(
            ProxyConfig(type = ProxyConfig.Type.HTTP, host = "p", port = 1, password = "pw"),
        )

        // A read-modify-write cycle: the value read back is already plaintext,
        // so the isEncrypted guard is what protects the second write.
        encrypted.setDefaultProxy(encrypted.defaultProxy().copy(port = 2))

        val atRest = ArrDataJson.decodeFromString(
            ProxyConfig.serializer(),
            store.strings.getValue("arr_global_v1_default_proxy"),
        )
        assertEquals("v1:fake:pw", atRest.password)
        assertEquals(2, encrypted.defaultProxy().port)
        assertEquals("pw", encrypted.defaultProxy().password)
    }

    @Test
    fun defaultProxyWithoutSecretsRoundTripsVerbatim() = runTest {
        // Existing graph wiring constructs GlobalSettings without secrets;
        // behavior stays unchanged until that call site passes graph.secrets.
        val legacy = GlobalSettings(FakePreferencesStore())

        legacy.setDefaultProxy(ProxyConfig(type = ProxyConfig.Type.HTTP, host = "p", port = 1, password = "pw"))

        assertEquals("pw", legacy.defaultProxy().password)
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
