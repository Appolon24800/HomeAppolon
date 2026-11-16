package dev.appolon.homelab

import android.webkit.CookieManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.appolon.homelab.data.PocketId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PocketIdCookiePersistenceTest {
    @Test
    fun restoredHostCookiesAreAvailableBeforeAccountPersistence() = runBlocking {
        val url = "https://pocketid-cookie-audit.invalid"
        val cookies = listOf("__Host-access_token=fixture-access", "__Host-session=fixture-session")
        try {
            PocketId.installSessionCookies(url, cookies)
            assertEquals(cookies.toSet(), PocketId.sessionCookies(url).toSet())
            assertTrue(PocketId.hasSession(url))
        } finally {
            withContext(Dispatchers.Main.immediate) {
                val manager = CookieManager.getInstance()
                cookies.forEach {
                    manager.setCookie(url, "${it.substringBefore('=')}=; Max-Age=0; Path=/; Secure; HttpOnly")
                }
                manager.flush()
            }
        }
    }
}
