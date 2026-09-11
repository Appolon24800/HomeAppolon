package dev.appolon.homelab.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchTest {

    private val jellyfin = ServiceItem(
        name = "Jellyfin",
        subtitle = "Media Server",
        keywords = "jellyfin media movies tv shows",
        url = "https://home.appolon.dev/jellyfin",
    )

    private val navidrome = ServiceItem(
        name = "Navidrome",
        subtitle = "Music Server",
        keywords = "music musique",
        url = "https://home.appolon.dev/navidrome",
    )

    private val vaultwarden = ServiceItem(
        name = "Vaultwarden",
        subtitle = "Gestionnaire de mots de passe",
        keywords = "vaultwarden bitwarden password vault",
        url = "https://home.appolon.dev/vault",
    )

    private val qbittorrent = ServiceItem(
        name = "qBittorrent",
        subtitle = "Client Torrent",
        keywords = "téléchargement download",
        url = "https://home.appolon.dev/qbittorrent",
    )

    private val groups = listOf(
        ServiceGroup(name = "Media", items = listOf(jellyfin, navidrome)),
        ServiceGroup(name = "Infra", items = listOf(vaultwarden, qbittorrent)),
    )

    @Test
    fun `empty query matches everything`() {
        assertTrue(Search.matches(jellyfin, ""))
        assertTrue(Search.matches(jellyfin, "   "))
    }

    @Test
    fun `matches name case-insensitively`() {
        assertTrue(Search.matches(jellyfin, "jelly"))
        assertTrue(Search.matches(jellyfin, "JELLYFIN"))
    }

    @Test
    fun `matches keywords`() {
        assertTrue(Search.matches(navidrome, "musique"))
        assertTrue(Search.matches(vaultwarden, "bitwarden"))
    }

    @Test
    fun `matches subtitle`() {
        assertTrue(Search.matches(vaultwarden, "mots de passe"))
    }

    @Test
    fun `diacritics-insensitive matching`() {
        assertTrue(Search.matches(qbittorrent, "telechargement"))
        assertTrue(Search.matches(qbittorrent, "TÉLÉCHARGEMENT"))
        assertTrue(Search.matches(navidrome, "musique"))
    }

    @Test
    fun `multi-token queries require all tokens`() {
        assertTrue(Search.matches(jellyfin, "jellyfin media"))
        assertFalse(Search.matches(jellyfin, "jellyfin pizza"))
    }

    @Test
    fun `no match returns false`() {
        assertFalse(Search.matches(jellyfin, "photos"))
    }

    @Test
    fun `filterGroups keeps only groups with matches`() {
        assertEquals(2, Search.filterGroups(groups, "").size)
        val filtered = Search.filterGroups(groups, "navidrome")
        assertEquals(1, filtered.size)
        assertEquals("Media", filtered[0].first.name)
        assertEquals(listOf(navidrome), filtered[0].second)
    }
}
