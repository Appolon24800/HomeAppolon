package dev.appolon.homelab

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.appolon.homelab.data.HomeUiState
import dev.appolon.homelab.data.HomerConfigParser
import dev.appolon.homelab.ui.ServiceList
import dev.appolon.homelab.ui.theme.HomelabTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Guards against stale rows surviving an incremental query change
 * (type "m", then extend to "mus" — rows that no longer match must vanish).
 */
@RunWith(AndroidJUnit4::class)
class SearchUiTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val config = HomerConfigParser.parse(
        """
        services:
          - name: "Photos, Videos and Music"
            items:
              - name: "Navidrome"
                subtitle: "Music Server"
                keywords: "music musique"
                url: "https://example.com/navidrome"
              - name: "Jellyfin"
                subtitle: "Media Server"
                keywords: "jellyfin media movies"
                url: "https://example.com/jellyfin"
          - name: "Downloads & Automation"
            items:
              - name: "Lidarr"
                subtitle: "Music Management"
                keywords: "lidarr music albums"
                url: "https://example.com/lidarr"
              - name: "Bookshelf"
                subtitle: "Book Collection Manager"
                keywords: "readarr bookshelf books"
                url: "https://example.com/bookshelf"
              - name: "Kapowarr"
                subtitle: "Manga Collection Manager"
                keywords: "kapowarr manga comics"
                url: "https://example.com/kapowarr"
              - name: "AutoBrr"
                subtitle: "IRC/RSS Auto downloader"
                keywords: "irc downlaoder"
                url: "https://example.com/autobrr"
        """.trimIndent(),
    )

    @Test
    fun incrementalQueryShrinksListCorrectly() {
        var query by mutableStateOf("m")

        composeRule.setContent {
            HomelabTheme {
                ServiceList(
                    state = HomeUiState(config = config, query = query),
                    contentPadding = PaddingValues(),
                    onRefresh = {},
                    onServiceClick = {},
                    onDismissMessage = {},
                )
            }
        }

        // "m": every item here legitimately contains an m
        composeRule.onNodeWithText("Navidrome").assertExists()
        composeRule.onNodeWithText("Lidarr").assertExists()
        composeRule.onNodeWithText("Bookshelf").assertExists()
        composeRule.onNodeWithText("Kapowarr").assertExists()

        // Extend the query: "m" -> "mus"
        composeRule.runOnUiThread { query = "mus" }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Navidrome").assertExists()
        composeRule.onNodeWithText("Lidarr").assertExists()
        composeRule.onNodeWithText("Jellyfin").assertDoesNotExist()
        composeRule.onNodeWithText("Bookshelf").assertDoesNotExist()
        composeRule.onNodeWithText("Kapowarr").assertDoesNotExist()
        composeRule.onNodeWithText("AutoBrr").assertDoesNotExist()
    }
}
