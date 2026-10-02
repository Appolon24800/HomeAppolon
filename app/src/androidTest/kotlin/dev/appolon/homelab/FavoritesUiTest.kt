package dev.appolon.homelab

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.appolon.homelab.data.ConfigRepository
import dev.appolon.homelab.data.Favorites
import dev.appolon.homelab.data.HomeUiState
import dev.appolon.homelab.data.HomerConfig
import dev.appolon.homelab.data.ServiceGroup
import dev.appolon.homelab.data.ServiceItem
import dev.appolon.homelab.ui.ServiceList
import dev.appolon.homelab.ui.theme.HomelabTheme
import dev.appolon.homelab.ui.theme.ThemeSource
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FavoritesUiTest {
    @get:Rule val composeRule = createComposeRule()

    @Test fun starPinsWithoutOpeningServiceAndUnpinsFromFavorites() {
        val item = ServiceItem("Jellyfin", url = "https://example.com/jellyfin")
        var state by mutableStateOf(HomeUiState(
            config = HomerConfig(services = listOf(ServiceGroup("Media", items = listOf(item)))),
        ))
        var opened = 0
        composeRule.setContent {
            HomelabTheme(themeSource = ThemeSource.HOMER, homerColors = null) {
                ServiceList(
                    state = state,
                    contentPadding = PaddingValues(),
                    onRefresh = {},
                    onServiceClick = { _, _ -> opened++ },
                    onToggleFavorite = { service ->
                        state = state.copy(favorites = Favorites.toggle(state.favorites, Favorites.id(service)))
                    },
                )
            }
        }
        composeRule.onNodeWithContentDescription("Pin Jellyfin").performClick()
        composeRule.onNodeWithText("FAVORITES").assertExists()
        composeRule.onAllNodesWithContentDescription("Unpin Jellyfin")[0].assertIsSelected()
        composeRule.runOnIdle { assertEquals(0, opened) }
        composeRule.onAllNodesWithContentDescription("Unpin Jellyfin")[0].performClick()
        composeRule.onNodeWithText("FAVORITES").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("Pin Jellyfin").assertExists()
    }

    @Test fun favoritesPersistAcrossRepositoryInstances() = runBlocking {
        val context = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext
        val first = ConfigRepository(context)
        val previous = first.loadFavorites()
        val id = "https://example.com/favorites-persistence-test"
        try {
            if (id in first.loadFavorites()) first.toggleFavorite(id)
            assertEquals(previous - id + id, first.toggleFavorite(id))
            val second = ConfigRepository(context)
            assertEquals(previous - id + id, second.loadFavorites())
            assertEquals(previous - id, second.toggleFavorite(id))
        } finally {
            if ((id in first.loadFavorites()) != (id in previous)) first.toggleFavorite(id)
        }
    }
}
