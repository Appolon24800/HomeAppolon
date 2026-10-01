package dev.appolon.homelab.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Fake cipher: identity function, so tests exercise the codec, not Keystore. */
private class PlainCipher : CookieCipher {
    override fun seal(plain: String): String = "v1:$plain"
    override fun open(sealed: String): String? = sealed.removePrefix("v1:").takeIf { sealed.startsWith("v1:") }
}

class AccountStateCodecTest {

    private val cipher = PlainCipher()

    @Test
    fun `round trip preserves accounts and active id`() {
        val state = AccountState(
            accounts = listOf(
                PocketIdAccount(
                    id = "u1",
                    username = "appolon",
                    email = "me@example.com",
                    displayName = "Appolon",
                    sessionCookies = listOf("__Host-access_token=jwt"),
                ),
                PocketIdAccount(id = "u2", username = "guest"),
            ),
            activeAccountId = "u1",
        )
        val decoded = AccountStateCodec.decode(AccountStateCodec.encode(state, cipher), cipher)
        assertEquals(state, decoded)
    }

    @Test
    fun `empty state round trips`() {
        val decoded = AccountStateCodec.decode(AccountStateCodec.encode(AccountState(), cipher), cipher)
        assertEquals(AccountState(), decoded)
    }

    @Test
    fun `tampered or undecodable payload yields null`() {
        assertNull(AccountStateCodec.decode("garbage", cipher))
        assertNull(AccountStateCodec.decode("v1:not json", cipher))
    }

    @Test
    fun `unknown keys are ignored on decode`() {
        val sealed = "v1:" + """
            {"accounts":[{"id":"u1","username":"a","futureField":true}],"activeAccountId":"u1"}
        """.trimIndent()
        val decoded = AccountStateCodec.decode(sealed, cipher)!!
        assertEquals("u1", decoded.accounts.single().id)
        assertEquals("a", decoded.accounts.single().label)
    }

    @Test
    fun `active lookup finds the account`() {
        val state = AccountState(
            accounts = listOf(PocketIdAccount(id = "a", username = "x")),
            activeAccountId = "a",
        )
        assertEquals("a", state.active?.id)
        assertTrue(AccountState(activeAccountId = "zz").active == null)
    }
}
