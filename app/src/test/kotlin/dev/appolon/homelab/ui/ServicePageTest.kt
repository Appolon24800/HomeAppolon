package dev.appolon.homelab.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ServicePageTest {
    private val service = "https://home.appolon.dev/lidarr"

    @Test
    fun `matches the service and pages below it`() {
        assertTrue(isServicePage("https://home.appolon.dev/lidarr", service))
        assertTrue(isServicePage("https://home.appolon.dev/lidarr/", service))
        assertTrue(isServicePage("https://home.appolon.dev/lidarr/artist/1?x=1", service))
    }

    @Test
    fun `rejects the sign-in hops and lookalike paths`() {
        assertFalse(isServicePage("https://home.appolon.dev/oauth2/callback?code=x", service))
        assertFalse(isServicePage("https://auth.appolon.dev/authorize", service))
        assertFalse(isServicePage("https://home.appolon.dev/lidarr2", service))
        assertFalse(isServicePage("http://home.appolon.dev/lidarr", service))
    }

    @Test
    fun `malformed urls never match`() {
        assertFalse(isServicePage("not a url", service))
        assertFalse(isServicePage("about:blank", service))
    }
}
