package dev.appolon.homelab.arr.servers.logic

import dev.appolon.homelab.arr.net.ProxyConfig
import dev.appolon.homelab.arr.net.TrustSpec
import dev.appolon.homelab.arr.servers.logic.ProxyForm.Problem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProxyFormTest {

    @Test
    fun `direct default is valid and means no proxy`() {
        val form = ProxyForm()
        assertTrue(form.problems.isEmpty())
        assertEquals(ProxyConfig.Type.NONE, form.toProxyConfig().type)
        assertNull(form.toTrustSpec())
    }

    @Test
    fun `explicit proxy needs host and a usable port`() {
        val missingHost = ProxyForm(type = ProxyConfig.Type.HTTP, port = "8080")
        assertTrue(Problem.HOST_BLANK in missingHost.problems)

        val badPorts = listOf("", "abc", "0", "70000")
        badPorts.forEach { port ->
            val problems = ProxyForm(type = ProxyConfig.Type.SOCKS5, host = "proxy", port = port)
                .problems
            val expected = if (port.isBlank()) Problem.PORT_BLANK else Problem.PORT_INVALID
            assertTrue("port '$port' should flag $expected", expected in problems)
        }

        val good = ProxyForm(type = ProxyConfig.Type.HTTP, host = "proxy.lan", port = "3128")
        assertTrue(good.problems.isEmpty())
        val config = good.toProxyConfig()
        assertEquals(ProxyConfig.Type.HTTP, config.type)
        assertEquals("proxy.lan", config.host)
        assertEquals(3128, config.port)
        assertNull(config.username)
        assertNull(config.password)
    }

    @Test
    fun `credentials must come as a pair`() {
        val problems = ProxyForm(
            type = ProxyConfig.Type.HTTP,
            host = "proxy",
            port = "3128",
            username = "user",
        ).problems
        assertEquals(setOf<Problem>(Problem.CREDENTIALS_INCOMPLETE), problems)
    }

    @Test
    fun `orbot mode skips host port and credential requirements`() {
        val form = ProxyForm(useOrbot = true)
        assertTrue(form.problems.isEmpty())
        val config = form.toProxyConfig()
        assertTrue(config.useOrbot)
        assertEquals(ProxyConfig.Type.NONE, config.type)
        assertEquals("", config.host)
    }

    @Test
    fun `orbot mode is exclusive with an explicit proxy`() {
        val form = ProxyForm(
            type = ProxyConfig.Type.SOCKS5,
            host = "x",
            port = "1",
            useOrbot = true,
        )
        assertTrue(form.problems.isEmpty())
        assertTrue(form.toProxyConfig().useOrbot)
        assertEquals("", form.toProxyConfig().host)
    }

    @Test
    fun `fingerprint accepts colon separated hex and normalizes to lowercase`() {
        val withColons = (0 until 64).joinToString("") { "a" }
            .chunked(2).joinToString(":")
        val form = ProxyForm(acceptSelfSigned = true, fingerprint = withColons.uppercase())
        assertTrue(form.problems.isEmpty())
        assertEquals("a".repeat(64), form.normalizedFingerprint())
        assertEquals(TrustSpec.Pinned("a".repeat(64)), form.toTrustSpec())
    }

    @Test
    fun `short or non hex fingerprints are invalid`() {
        listOf("ab", "z".repeat(64), "a".repeat(63)).forEach { fingerprint ->
            val problems = ProxyForm(acceptSelfSigned = true, fingerprint = fingerprint).problems
            assertTrue(
                "fingerprint '$fingerprint' should be invalid",
                Problem.FINGERPRINT_INVALID in problems,
            )
        }
    }

    @Test
    fun `accepting without a fingerprint accepts any self signed certificate`() {
        val form = ProxyForm(acceptSelfSigned = true)
        assertTrue(form.problems.isEmpty())
        assertEquals(TrustSpec.AcceptAny, form.toTrustSpec())
    }

    @Test
    fun `fingerprint is ignored unless self signed is accepted`() {
        val form = ProxyForm(acceptSelfSigned = false, fingerprint = "garbage")
        assertTrue(form.problems.isEmpty())
        assertNull(form.toTrustSpec())
    }

    @Test
    fun `fromConfig round trips a stored proxy`() {
        val stored = ProxyConfig(
            type = ProxyConfig.Type.SOCKS5,
            host = "127.0.0.1",
            port = 9050,
            username = "u",
            password = "p",
        )
        val form = ProxyForm.fromConfig(stored)
        assertEquals(stored, form.toProxyConfig())
    }

    @Test
    fun `fromConfig maps a zero port to a blank field`() {
        val form = ProxyForm.fromConfig(ProxyConfig())
        assertEquals("", form.port)
        assertTrue(form.problems.isEmpty())
    }
}
