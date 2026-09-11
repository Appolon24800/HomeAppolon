package dev.appolon.homelab.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class IconUrlResolverTest {

    private val base = "https://home.appolon.dev/"

    @Test
    fun `absolute urls pass through`() {
        val url = "https://raw.githubusercontent.com/walkxcode/dashboard-icons/main/svg/jellyfin.svg"
        assertEquals(url, IconUrlResolver.resolve(url, base))
        assertEquals("http://x.dev/a.png", IconUrlResolver.resolve("http://x.dev/a.png", base))
    }

    @Test
    fun `relative path resolves against base`() {
        assertEquals(
            "https://home.appolon.dev/assets/icons/kavita.svg",
            IconUrlResolver.resolve("assets/icons/kavita.svg", base),
        )
    }

    @Test
    fun `leading slash and dot-slash also resolve against base`() {
        assertEquals("https://home.appolon.dev/icons/a.svg", IconUrlResolver.resolve("/icons/a.svg", base))
        assertEquals("https://home.appolon.dev/logo.png", IconUrlResolver.resolve("./logo.png", base))
    }

    @Test
    fun `data uris pass through`() {
        assertEquals("data:image/svg+xml,<svg/>", IconUrlResolver.resolve("data:image/svg+xml,<svg/>", base))
    }

    @Test
    fun `null and blank return null`() {
        assertNull(IconUrlResolver.resolve(null, base))
        assertNull(IconUrlResolver.resolve("", base))
        assertNull(IconUrlResolver.resolve("   ", base))
    }
}
