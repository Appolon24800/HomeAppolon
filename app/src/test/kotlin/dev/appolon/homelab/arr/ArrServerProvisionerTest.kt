package dev.appolon.homelab.arr

import dev.appolon.homelab.arr.core.AppType
import dev.appolon.homelab.arr.core.ArrError
import dev.appolon.homelab.arr.core.ServerId
import dev.appolon.homelab.arr.data.ArrServer
import dev.appolon.homelab.arr.data.ServerRegistry
import dev.appolon.homelab.arr.data.secrets.ArrSecrets
import dev.appolon.homelab.arr.net.ProxyConfig
import dev.appolon.homelab.arr.net.ServerEndpoint
import dev.appolon.homelab.arr.net.TrustSpec
import dev.appolon.homelab.arr.servers.net.ConnectionOutcome
import dev.appolon.homelab.arr.servers.net.ConnectionTester
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ArrServerProvisionerTest {
    private class Registry : ServerRegistry {
        val rows = MutableStateFlow<List<ArrServer>>(emptyList())
        override suspend fun servers() = rows.value
        override suspend fun serversOfType(type: AppType) = rows.value.filter { it.type == type }
        override suspend fun byId(id: ServerId) = rows.value.firstOrNull { it.id == id }
        override suspend fun upsert(server: ArrServer): ArrServer {
            rows.value = rows.value.filterNot { it.id == server.id } + server
            return server
        }
        override suspend fun delete(id: ServerId) { rows.value = rows.value.filterNot { it.id == id } }
        override suspend fun reorder(idsInOrder: List<ServerId>) = Unit
        override fun observe() = rows
    }

    private val secrets = object : ArrSecrets {
        override fun encrypt(plaintext: String) = "encrypted:$plaintext"
        override fun decrypt(envelope: String) = envelope.removePrefix("encrypted:")
    }

    @Test
    fun credentialUpdatePreservesAndVerifiesStoredConnectionPolicy() = runTest {
        val registry = Registry()
        val existing = ArrServer(
            id = ServerId("existing"), type = AppType.SONARR, name = "My shows",
            baseUrl = "https://example.test/sonarr", apiKeyEncrypted = "encrypted:old",
            basicAuthUser = "user", basicAuthPasswordEncrypted = "encrypted:password",
            proxy = ProxyConfig(), trust = TrustSpec.Pinned("fingerprint"), order = 3,
        )
        registry.upsert(existing)
        var verified: ServerEndpoint? = null
        val provisioner = ArrServerProvisioner(registry, secrets, ConnectionTester { endpoint, app ->
            assertEquals(AppType.SONARR, app)
            verified = endpoint
            ConnectionOutcome.Success("1.0")
        }, defaultProxy = { error("Explicit direct policy must win") })

        assertEquals(existing.id, provisioner.provision(AppType.SONARR, "https://example.test/sonarr/", "new"))
        assertEquals("new", verified!!.apiKey)
        assertEquals("password", verified!!.basicAuthPassword)
        assertEquals(existing.proxy, verified!!.proxy)
        assertEquals(existing.trust, verified!!.trust)
        assertEquals(listOf(existing.copy(apiKeyEncrypted = "encrypted:new")), registry.servers())
    }

    @Test
    fun newServerVerificationReadsThePersistedDefaultProxy() = runTest {
        val registry = Registry()
        val proxy = ProxyConfig(ProxyConfig.Type.SOCKS5, "proxy.test", 9050)
        val provisioner = ArrServerProvisioner(registry, secrets, ConnectionTester { endpoint, _ ->
            assertEquals(proxy, endpoint.proxy)
            assertEquals("http://example.test/", endpoint.baseUrl)
            ConnectionOutcome.Success(null)
        }, defaultProxy = { proxy })

        val id = provisioner.provision(AppType.RADARR, "example.test", "key")
        assertEquals(id, registry.servers().single().id)
        // Keep inheritance when persisting, so later global edits still apply.
        assertNull(registry.servers().single().proxy)
    }

    @Test
    fun unsuccessfulVerificationDoesNotStoreCredentials() = runTest {
        val registry = Registry()
        val provisioner = ArrServerProvisioner(registry, secrets, ConnectionTester { _, _ ->
            ConnectionOutcome.Failure(ArrError.Auth(401))
        })
        assertNull(provisioner.provision(AppType.RADARR, "http://example.test", "bad"))
        assertEquals(emptyList<ArrServer>(), registry.servers())
    }

    @Test
    fun cancellationDoesNotProvisionAndDoesNotBecomeASetupFailure() = runTest {
        val registry = Registry()
        val provisioner = ArrServerProvisioner(registry, secrets, ConnectionTester { _, _ ->
            throw CancellationException("Closed")
        })
        try {
            provisioner.provision(AppType.RADARR, "http://example.test", "key")
            throw AssertionError("Cancellation must propagate")
        } catch (_: CancellationException) {
            assertEquals(emptyList<ArrServer>(), registry.servers())
        }
    }

    @Test
    fun simultaneousLinksCannotCreateTwoCopiesOfTheSameServer() = runTest {
        val registry = Registry()
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        var probes = 0
        val provisioner = ArrServerProvisioner(registry, secrets, ConnectionTester { _, _ ->
            if (++probes == 1) {
                started.complete(Unit)
                release.await()
            }
            ConnectionOutcome.Success(null)
        })
        val first = async { provisioner.provision(AppType.RADARR, "http://example.test", "first") }
        started.await()
        val second = async { provisioner.provision(AppType.RADARR, "http://example.test/", "second") }
        release.complete(Unit)
        assertEquals(first.await(), second.await())
        assertEquals("encrypted:second", registry.servers().single().apiKeyEncrypted)
    }
}
