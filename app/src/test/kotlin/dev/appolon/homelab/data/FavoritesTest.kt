package dev.appolon.homelab.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FavoritesTest {
    private val jellyfin = ServiceItem("Jellyfin", subtitle = "Media Server", url = "https://home.appolon.dev/jellyfin")
    private val kavita = ServiceItem("Kavita", url = "https://home.appolon.dev/kavita")
    private val groups = listOf(ServiceGroup("Media", items = listOf(jellyfin, kavita)))

    @Test fun togglingAddsAndRemovesPin() {
        val saved = Favorites.toggle(emptySet(), Favorites.id(jellyfin))
        assertEquals(setOf(jellyfin.url), saved)
        assertTrue(Favorites.toggle(saved, Favorites.id(jellyfin)).isEmpty())
    }

    @Test fun blankUrlsCannotBePinned() {
        assertTrue(Favorites.toggle(emptySet(), "").isEmpty())
    }

    @Test fun renamedOrRegroupedServiceKeepsPin() {
        val renamed = jellyfin.copy(name = "Movies")
        assertEquals(
            listOf(renamed),
            Favorites.items(listOf(ServiceGroup("New group", items = listOf(renamed))), setOf(jellyfin.url), ""),
        )
    }

    @Test fun favoritesFollowConfigOrderAndSearch() {
        val saved = setOf(kavita.url, jellyfin.url)
        assertEquals(listOf(jellyfin, kavita), Favorites.items(groups, saved, ""))
        assertEquals(listOf(jellyfin), Favorites.items(groups, saved, "media"))
        assertTrue(Favorites.items(groups, saved, "missing").isEmpty())
    }

    @Test fun missingServicesStaySavedButDoNotAppear() {
        assertEquals(listOf(kavita), Favorites.items(groups, setOf("https://removed", kavita.url), ""))
    }

    @Test fun duplicateUrlsAppearOnceInFavorites() {
        assertEquals(listOf(jellyfin), Favorites.items(groups + groups, setOf(jellyfin.url), ""))
    }
}
