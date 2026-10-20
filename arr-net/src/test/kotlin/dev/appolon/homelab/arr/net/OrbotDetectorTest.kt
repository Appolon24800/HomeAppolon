package dev.appolon.homelab.arr.net

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OrbotDetectorTest {
    @Test
    fun `resolves useOrbot to local socks5 when Orbot is installed`() {
        val detector = OrbotDetector(isPackageInstalled = { it == "org.torproject.android" })

        val resolved = detector.resolveProxy(ProxyConfig(useOrbot = true))

        assertEquals(ProxyConfig.Type.SOCKS5, resolved.type)
        assertEquals("127.0.0.1", resolved.host)
        assertEquals(9050, resolved.port)
        assertTrue(resolved.isConfigured)
    }

    @Test
    fun `routes direct when Orbot is toggled but not installed`() {
        val detector = OrbotDetector(isPackageInstalled = { false })

        val resolved = detector.resolveProxy(ProxyConfig(useOrbot = true))

        assertEquals(ProxyConfig.Type.NONE, resolved.type)
        assertFalse(resolved.isConfigured)
    }

    @Test
    fun `passes explicit proxy config through untouched`() {
        val detector = OrbotDetector(isPackageInstalled = { true })
        val config = ProxyConfig(
            type = ProxyConfig.Type.HTTP,
            host = "proxy.lan",
            port = 3128,
            username = "u",
            password = "p",
        )

        assertEquals(config, detector.resolveProxy(config))
    }

    @Test
    fun `orbot toggle wins over an unset explicit proxy`() {
        val detector = OrbotDetector(isPackageInstalled = { true })

        val resolved = detector.resolveProxy(ProxyConfig(useOrbot = true))

        assertNull(resolved.username)
        assertNull(resolved.password)
    }

    @Test
    fun `probes the exact Orbot package name`() {
        var probed: String? = null
        val detector = OrbotDetector(isPackageInstalled = { probed = it; true })

        assertTrue(detector.isOrbotInstalled())
        assertEquals("org.torproject.android", probed)
    }

    @Test
    fun `default probe reports Orbot absent`() {
        assertFalse(OrbotDetector().isOrbotInstalled())
    }
}
