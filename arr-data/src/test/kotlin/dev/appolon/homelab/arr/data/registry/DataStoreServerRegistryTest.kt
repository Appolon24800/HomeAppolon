package dev.appolon.homelab.arr.data.registry

import dev.appolon.homelab.arr.core.AppType
import dev.appolon.homelab.arr.core.ServerId
import dev.appolon.homelab.arr.data.ArrDataJson
import dev.appolon.homelab.arr.data.ArrServer
import dev.appolon.homelab.arr.data.secrets.ArrSecrets
import dev.appolon.homelab.arr.net.ProxyConfig
import java.security.GeneralSecurityException
import java.util.UUID
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DataStoreServerRegistryTest {

    private var now = 1_000L
    private var nextId = 1
    private val writeRecorder = WriteRecordingPreferencesStore()

    private fun registry(): DataStoreServerRegistry =
        DataStoreServerRegistry(
            store = writeRecorder,
            secrets = FakeArrSecrets(),
            clock = { now },
            idGenerator = { "id-${nextId++}" },
        )

    private fun server(
        id: String = "",
        name: String = "server",
        type: AppType = AppType.RADARR,
        order: Int = 0,
        createdAt: Long = 0,
        apiKey: String = "plain-api-key",
        basicAuthPassword: String? = null,
    ): ArrServer = ArrServer(
        id = ServerId(id),
        type = type,
        name = name,
        baseUrl = "http://host/",
        apiKeyEncrypted = apiKey,
        basicAuthPasswordEncrypted = basicAuthPassword,
        order = order,
        createdAtMillis = createdAt,
    )

    @Test
    fun upsertAssignsIdOrderAndTimestampToNewServers() = runTest {
        val registry = registry()

        val stored = registry.upsert(server())

        assertNotEquals("", stored.id.value)
        // The id generator is injectable, but the production default is a UUID.
        assertEquals("id-1", stored.id.value)
        assertEquals(1, stored.order)
        assertEquals(now, stored.createdAtMillis)
        assertEquals("v1:fake:plain-api-key", stored.apiKeyEncrypted)
    }

    @Test
    fun defaultConstructorGeneratesUuidIds() = runTest {
        // Default clock and UUID generator, as production wires them.
        val registry = DataStoreServerRegistry(FakePreferencesStore(), FakeArrSecrets())

        val stored = registry.upsert(server())

        // Throws IllegalArgumentException if the id is not a UUID.
        UUID.fromString(stored.id.value)
        assertTrue(stored.createdAtMillis > 0)
    }

    @Test
    fun upsertEncryptsBasicAuthPasswordOnlyOnce() = runTest {
        val store = FakePreferencesStore()
        val secrets = FakeArrSecrets()
        val registry = DataStoreServerRegistry(store, secrets, { now }, { "id-1" })

        val created = registry.upsert(server(basicAuthPassword = "secret-password"))
        assertEquals("v1:fake:secret-password", created.basicAuthPasswordEncrypted)

        // A read-modify-write cycle must not double-encrypt.
        val renamed = created.copy(name = "renamed")
        val updated = registry.upsert(renamed)
        assertEquals("v1:fake:secret-password", updated.basicAuthPasswordEncrypted)
        assertEquals("renamed", updated.name)
        assertEquals(1, secrets.encryptedPlaintexts.count { it == "secret-password" })
    }

    @Test
    fun upsertKeepsStoredOrderAndTimestampWhenIncomingDefaultsAreZero() = runTest {
        val registry = registry()
        val created = registry.upsert(server(name = "first", order = 3, createdAt = 500))
        now = 9_999

        // A caller rebuilding the model from scratch with defaults must not
        // reset placement or history.
        val updated = registry.upsert(
            created.copy(name = "renamed", order = 0, createdAtMillis = 0),
        )

        assertEquals(3, updated.order)
        assertEquals(500, updated.createdAtMillis)
        assertEquals("renamed", updated.name)
    }

    @Test
    fun upsertRespectsExplicitOrderAndTimestamp() = runTest {
        val registry = registry()

        val stored = registry.upsert(server(order = 7, createdAt = 42))

        assertEquals(7, stored.order)
        assertEquals(42, stored.createdAtMillis)
    }

    @Test
    fun newServersAppendInOrder() = runTest {
        val registry = registry()
        registry.upsert(server(name = "a"))
        registry.upsert(server(name = "b"))
        registry.upsert(server(name = "c"))

        assertEquals(listOf(1, 2, 3), registry.servers().map { it.order })
        assertEquals(listOf("a", "b", "c"), registry.servers().map { it.name })
    }

    @Test
    fun serversAreSortedByOrderAcrossRestarts() = runTest {
        val first = registry()
        first.upsert(server(name = "a"))
        first.upsert(server(name = "b"))

        // A second registry instance over the same storage must read back sorted.
        val second = DataStoreServerRegistry(writeRecorder, FakeArrSecrets(), { now }, { "id-9" })
        assertEquals(listOf("a", "b"), second.servers().map { it.name })
    }

    @Test
    fun byIdRoundTripsAndDeleteRemoves() = runTest {
        val registry = registry()
        val created = registry.upsert(server())

        assertEquals(created, registry.byId(created.id))

        registry.delete(created.id)
        assertNull(registry.byId(created.id))
        assertTrue(registry.servers().isEmpty())
    }

    @Test
    fun serversOfTypeFilters() = runTest {
        val registry = registry()
        registry.upsert(server(name = "radarr", type = AppType.RADARR))
        registry.upsert(server(name = "sonarr", type = AppType.SONARR))

        assertEquals(listOf("sonarr"), registry.serversOfType(AppType.SONARR).map { it.name })
        assertTrue(registry.serversOfType(AppType.LIDARR).isEmpty())
    }

    @Test
    fun reorderPutsListedFirstAndAppendsTheRest() = runTest {
        val registry = registry()
        val a = registry.upsert(server(name = "a"))
        val b = registry.upsert(server(name = "b"))
        val c = registry.upsert(server(name = "c"))

        registry.reorder(listOf(c.id, a.id))

        assertEquals(listOf("c", "a", "b"), registry.servers().map { it.name })
        assertEquals(listOf(1, 2, 3), registry.servers().map { it.order })
    }

    @Test
    fun reorderRewritesOnlyRowsWhosePositionChanged() = runTest {
        val registry = registry()
        val a = registry.upsert(server(name = "a"))
        val b = registry.upsert(server(name = "b"))
        registry.upsert(server(name = "c"))
        writeRecorder.writes.clear()

        // c already sits last, so it must not be rewritten.
        registry.reorder(listOf(b.id, a.id))

        assertEquals(
            setOf("arr_server_v1_${b.id.value}", "arr_server_v1_${a.id.value}"),
            writeRecorder.writes.toSet(),
        )
    }

    @Test
    fun reorderIgnoresUnknownIds() = runTest {
        val registry = registry()
        val a = registry.upsert(server(name = "a"))

        registry.reorder(listOf(ServerId("missing"), a.id))

        assertEquals(listOf("a"), registry.servers().map { it.name })
    }

    @Test
    fun persistedJsonRoundTripsThroughTheStoredBlob() = runTest {
        val registry = registry()
        val created = registry.upsert(server(basicAuthPassword = "pw"))

        val blob = writeRecorder.strings.getValue("arr_server_v1_${created.id.value}")
        val decoded = ArrDataJson.decodeFromString(ArrServer.serializer(), blob)

        assertEquals(created, decoded)
        assertTrue(blob.contains("\"type\":\"RADARR\""))
    }

    @Test
    fun proxyPasswordIsEncryptedAtRestAndUsableOnRead() = runTest {
        val store = FakePreferencesStore()
        val secrets = FakeArrSecrets()
        val registry = DataStoreServerRegistry(store, secrets)
        val proxy = ProxyConfig(
            type = ProxyConfig.Type.SOCKS5,
            host = "proxy.lan",
            port = 1080,
            username = "u",
            password = "plain-proxy-pass",
        )

        val created = registry.upsert(server().copy(proxy = proxy))

        // The stored blob carries an envelope, not the plaintext password.
        val blob = store.strings.getValue("arr_server_v1_${created.id.value}")
        val atRest = ArrDataJson.decodeFromString(ArrServer.serializer(), blob)
        assertEquals("v1:fake:plain-proxy-pass", atRest.proxy!!.password)

        // Reads hand back usable credentials, and upsert returns the caller's row.
        assertEquals("plain-proxy-pass", registry.byId(created.id)!!.proxy!!.password)
        assertEquals("plain-proxy-pass", created.proxy!!.password)
        assertEquals(1, secrets.encryptedPlaintexts.count { it == "plain-proxy-pass" })
    }

    @Test
    fun proxyPasswordRewritesNeverDoubleEncrypt() = runTest {
        val registry = registry()
        val created = registry.upsert(
            server().copy(
                proxy = ProxyConfig(type = ProxyConfig.Type.HTTP, host = "p", port = 1, password = "pw"),
            ),
        )

        val renamed = created.copy(name = "renamed")
        registry.upsert(renamed)

        assertEquals("pw", registry.byId(created.id)!!.proxy!!.password)
    }

    @Test
    fun undecryptableProxyPasswordIsDroppedInsteadOfFailingReads() = runTest {
        val store = FakePreferencesStore()
        val writer = DataStoreServerRegistry(store, FakeArrSecrets())
        val created = writer.upsert(
            server().copy(
                proxy = ProxyConfig(type = ProxyConfig.Type.HTTP, host = "p", port = 1, password = "pw"),
            ),
        )
        // A different key cannot open the envelope written by the first one.
        val rotatedReader = DataStoreServerRegistry(
            store,
            object : ArrSecrets {
                override fun encrypt(plaintext: String): String = ArrSecrets.ENVELOPE_PREFIX + plaintext

                override fun decrypt(envelope: String): String =
                    throw GeneralSecurityException("key rotated")
            },
        )

        val read = rotatedReader.byId(created.id)

        assertEquals("p", read!!.proxy!!.host)
        assertNull(read.proxy!!.password)
    }

    @Test
    fun observeEmitsInitialListThenChanges() = runTest {
        val registry = registry()
        registry.upsert(server(name = "a"))
        val collected = mutableListOf<List<ArrServer>>()

        val collector = launch {
            registry.observe().take(2).toList(collected)
        }
        runCurrent()
        registry.upsert(server(name = "b"))
        runCurrent()
        collector.join()

        assertEquals(2, collected.size)
        assertEquals(listOf("a"), collected[0].map { it.name })
        assertEquals(listOf("a", "b"), collected[1].map { it.name })
    }
}

/** Records every key written so tests can assert which rows a flow rewrote. */
private class WriteRecordingPreferencesStore : FakePreferencesStore() {
    val writes = mutableListOf<String>()

    override suspend fun putString(key: String, value: String) {
        writes.add(key)
        super.putString(key, value)
    }
}
