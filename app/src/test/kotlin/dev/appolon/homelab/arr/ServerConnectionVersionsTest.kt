package dev.appolon.homelab.arr

import dev.appolon.homelab.arr.core.AppType
import dev.appolon.homelab.arr.core.ServerId
import dev.appolon.homelab.arr.data.ArrServer
import dev.appolon.homelab.arr.net.ProxyConfig
import dev.appolon.homelab.arr.net.TrustSpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class ServerConnectionVersionsTest {
    private val server = ArrServer(ServerId("one"), AppType.RADARR, "Movies", "http://example.test/", "encrypted-key")
    private val proxy = ProxyConfig(ProxyConfig.Type.HTTP, "proxy.test", 8080)

    @Test
    fun displayEditsPreserveTheConnectionVersion() {
        val versions = ServerConnectionVersions()
        val before = versions.update(listOf(server), null)
        assertEquals(before, versions.update(listOf(server.copy(name = "New name", order = 9, createdAtMillis = 42)), null))
    }

    @Test
    fun connectionEditsRetireOnlyTheAffectedServer() {
        val versions = ServerConnectionVersions()
        val other = server.copy(id = ServerId("two"))
        var previous = versions.update(listOf(server, other), null)
        for (changed in listOf(
            server.copy(baseUrl = "https://new.test/"),
            server.copy(apiKeyEncrypted = "new-key"),
            server.copy(basicAuthPasswordEncrypted = "new-password"),
            server.copy(trust = TrustSpec.AcceptAny),
            server.copy(proxy = proxy),
        )) {
            val current = versions.update(listOf(changed, other), null)
            assertNotEquals(previous[server.id], current[server.id])
            assertEquals(previous[other.id], current[other.id])
            previous = current
        }
    }

    @Test
    fun globalProxyChangesRetireInheritedConnectionsOnly() {
        val versions = ServerConnectionVersions()
        val direct = server.copy(id = ServerId("direct"), proxy = ProxyConfig())
        val fixed = server.copy(id = ServerId("fixed"), proxy = proxy)
        val servers = listOf(server, direct, fixed)
        val before = versions.update(servers, null)
        val after = versions.update(servers, proxy)
        assertNotEquals(before[server.id], after[server.id])
        assertEquals(before[direct.id], after[direct.id])
        assertEquals(before[fixed.id], after[fixed.id])
    }

    @Test
    fun deletedServerCannotReuseItsRetiredConnection() {
        val versions = ServerConnectionVersions()
        val before = versions.update(listOf(server), null)
        assertEquals(emptyMap<ServerId, Long>(), versions.update(emptyList(), null))
        assertNotEquals(before, versions.update(listOf(server), null))
    }
}
