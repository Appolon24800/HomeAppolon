package dev.appolon.homelab.arr.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ArrServerLinksTest {
    @Test
    fun serverIdsRemainOnePathArgumentAndDecodeOnce() {
        for (value in listOf("server-1", "server + / %25 ü", "a?b#c&d=1", "literal%2Fescape")) {
            val id = ServerId(value)
            val link = ArrServerLinks.encode(id)
            assertEquals(3, uriPath(link).split('/').size)
            assertEquals(id, ArrServerLinks.decode(link))
        }
    }

    @Test
    fun aLiteralPlusInAnExistingLinkDoesNotBecomeASpace() {
        assertEquals(ServerId("server+one"), ArrServerLinks.decode("homelab://arr/server/server+one"))
        assertEquals(ServerId("server one"), ArrServerLinks.decode("homelab://arr/server/server%20one"))
    }

    @Test
    fun malformedAndForeignLinksDoNotSelectAServer() {
        listOf(
            "https://arr/server/one", "homelab://other/server/one",
            "homelab://user@arr/server/one", "homelab://arr:123/server/one",
            "homelab://arr/server/one?apikey=key", "homelab://arr/server/one#fragment",
            "homelab://arr/server/one/two", "homelab://arr/server/", "homelab://arr/server/%20",
            "homelab://arr/server/bad%zz",
        ).forEach { assertNull(it, ArrServerLinks.decode(it)) }
    }

    private fun uriPath(value: String) = java.net.URI(value).rawPath
}
