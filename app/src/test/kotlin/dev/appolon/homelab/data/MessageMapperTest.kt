package dev.appolon.homelab.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MessageMapperTest {

    private val defaultMapping = MessageMapping(title = "title", content = "value")

    @Test
    fun `maps default title and value fields`() {
        val info = MessageMapper.map(
            """{"value": "Removed Cloudflare from our stack!", "title": "Informations"}""",
            defaultMapping,
        )!!
        assertEquals("Informations", info.title)
        assertEquals("Removed Cloudflare from our stack!", info.content)
    }

    @Test
    fun `honours custom mapping keys`() {
        val info = MessageMapper.map(
            """{"t": "Alert", "b": "Disk almost full"}""",
            MessageMapping(title = "t", content = "b"),
        )!!
        assertEquals("Alert", info.title)
        assertEquals("Disk almost full", info.content)
    }

    @Test
    fun `missing title is null`() {
        val info = MessageMapper.map("""{"value": "only content"}""", defaultMapping)!!
        assertNull(info.title)
        assertEquals("only content", info.content)
    }

    @Test
    fun `null or invalid json returns null`() {
        assertNull(MessageMapper.map("not json", defaultMapping))
        assertNull(MessageMapper.map("[]", defaultMapping))
        assertNull(MessageMapper.map("{}", defaultMapping))
    }
}
