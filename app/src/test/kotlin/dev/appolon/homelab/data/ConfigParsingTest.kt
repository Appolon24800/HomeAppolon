package dev.appolon.homelab.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ConfigParsingTest {

    private fun fixture(): String =
        javaClass.classLoader!!.getResource("config.fixture.yml")!!.readText()

    @Test
    fun `parses header fields`() {
        val config = HomerConfigParser.parse(fixture())
        assertEquals("Homelab", config.title)
        assertEquals("La Plateforme", config.subtitle)
    }

    @Test
    fun `parses services groups and items`() {
        val config = HomerConfigParser.parse(fixture())
        assertEquals(2, config.services.size)

        val media = config.services[0]
        assertEquals("Photos, Videos and Music", media.name)
        assertEquals(2, media.items.size)

        val jellyfin = media.items[0]
        assertEquals("Jellyfin", jellyfin.name)
        assertEquals("Media Server", jellyfin.subtitle)
        assertEquals("media", jellyfin.tag)
        assertEquals("jellyfin media movies tv shows", jellyfin.keywords)
        assertEquals("https://home.appolon.dev/jellyfin", jellyfin.url)
        assertEquals(
            "https://raw.githubusercontent.com/walkxcode/dashboard-icons/main/svg/jellyfin.svg",
            jellyfin.logo,
        )
    }

    @Test
    fun `tolerates unknown keys like colors theme defaults`() {
        // The fixture contains `theme`, `colors`, `defaults`, `header`, `footer`,
        // `logo`, `icon` — none of which exist on HomerConfig. Parsing must succeed.
        val config = HomerConfigParser.parse(fixture())
        assertEquals(2, config.services.size)
    }

    @Test
    fun `parses links`() {
        val config = HomerConfigParser.parse(fixture())
        assertEquals(2, config.links.size)
        assertEquals("TrueNAS", config.links[0].name)
        assertEquals("https://appolon.dev/", config.links[1].url)
    }

    @Test
    fun `parses message config with mapping and interval`() {
        val message = HomerConfigParser.parse(fixture()).message!!
        assertEquals("https://home.appolon.dev/message", message.url)
        assertEquals("title", message.mapping.title)
        assertEquals("value", message.mapping.content)
        assertEquals(10_000, message.refreshInterval)
    }

    @Test
    fun `minimal config uses defaults`() {
        val yaml = """
            services:
              - items:
                  - name: "Solo"
                    url: "https://example.com"
        """.trimIndent()
        val config = HomerConfigParser.parse(yaml)
        assertNull(config.title)
        assertNull(config.subtitle)
        assertEquals(1, config.services.size)
        assertEquals("", config.services[0].name)
        assertEquals("Solo", config.services[0].items[0].name)
        assertNull(config.services[0].items[0].logo)
        assertNull(config.message)
    }

    @Test(expected = Exception::class)
    fun `malformed yaml throws`() {
        HomerConfigParser.parse("services: [unclosed")
    }
}
