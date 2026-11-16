package dev.appolon.homelab.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PocketIdCookiesTest {
    @Test
    fun `passkey challenge cookie alone does not prove sign-in`() {
        assertFalse(PocketIdCookies.isAccessCookie("__Host-session=challenge"))
        assertFalse(PocketIdCookies.isAccessCookie("__Host-access_token="))
        assertTrue(PocketIdCookies.isAccessCookie("__Host-access_token=abc"))
    }
    @Test
    fun `sessionPairs filters unrelated and empty cookies`() {
        assertEquals(
            listOf("__Host-access_token=abc", "session=def"),
            PocketIdCookies.sessionPairs("theme=dark; __Host-access_token=abc; session=def; access_token="),
        )
    }

    @Test
    fun `restoreHeaders adds webview attributes`() {
        assertEquals(
            listOf("__Host-access_token=abc; Path=/; HttpOnly; SameSite=Lax; Secure"),
            PocketIdCookies.restoreHeaders("https://auth.example", listOf("__Host-access_token=abc")),
        )
    }

    @Test
    fun `prefixed secure cookies cannot be restored to http`() {
        assertThrows(IllegalArgumentException::class.java) {
            PocketIdCookies.restoreHeaders("http://auth.example", listOf("__Host-session=abc"))
        }
    }
}
