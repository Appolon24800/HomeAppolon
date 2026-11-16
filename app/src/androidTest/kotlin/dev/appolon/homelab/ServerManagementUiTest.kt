package dev.appolon.homelab

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.appolon.homelab.arr.core.AppType
import dev.appolon.homelab.arr.core.ServerId
import dev.appolon.homelab.arr.data.ArrServer
import dev.appolon.homelab.arr.servers.ui.ServerListScreen
import dev.appolon.homelab.arr.ui.lce.LceState
import dev.appolon.homelab.arr.ui.theme.ArrTheme
import dev.appolon.homelab.data.HomeUiState
import dev.appolon.homelab.ui.HomeTopBar
import dev.appolon.homelab.ui.theme.HomelabTheme
import dev.appolon.homelab.ui.theme.ThemeSource
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ServerManagementUiTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun dashboardMenuOpensServerManagementWithoutAConfiguredDashboard() {
        var opened = 0
        composeRule.setContent {
            HomelabTheme(themeSource = ThemeSource.SYSTEM, homerColors = null) {
                HomeTopBar(
                    state = HomeUiState(),
                    homerBarActive = false,
                    onSearch = {},
                    onLink = { _, _ -> },
                    onToggleTheme = {},
                    onOpenAccount = {},
                    onOpenServers = { opened++ },
                )
            }
        }

        composeRule.onNodeWithContentDescription("Links").performClick()
        composeRule.onNodeWithText("Manage servers").assertIsDisplayed().performClick()
        composeRule.runOnIdle { assertEquals(1, opened) }
    }

    @Test
    fun serverListAddAndEditControlsInvokeTheirActions() {
        val id = ServerId("test-server")
        val server = ArrServer(id, AppType.RADARR, "Test Radarr", "http://example.test/", "test-key")
        var added = 0
        var edited: ServerId? = null
        composeRule.setContent {
            ArrTheme {
                ServerListScreen(
                    lce = LceState.Content(listOf(server)),
                    health = emptyMap(),
                    probing = false,
                    onAdd = { added++ },
                    onEdit = { edited = it },
                    onMoveUp = {},
                    onMoveDown = {},
                    onDelete = {},
                    onOpenProxy = {},
                    onOpenNotifications = {},
                    onOpenSync = {},
                    onRetry = {},
                )
            }
        }

        composeRule.onNodeWithContentDescription("Add server").performClick()
        composeRule.onNodeWithText("Test Radarr").performClick()
        composeRule.runOnIdle {
            assertEquals(1, added)
            assertEquals(id, edited)
        }
    }

    @Test
    fun emptyServerListStillAllowsAddingAServer() {
        var added = 0
        composeRule.setContent {
            ArrTheme {
                ServerListScreen(
                    lce = LceState.Empty,
                    health = emptyMap(),
                    probing = false,
                    onAdd = { added++ },
                    onEdit = {},
                    onMoveUp = {},
                    onMoveDown = {},
                    onDelete = {},
                    onOpenProxy = {},
                    onOpenNotifications = {},
                    onOpenSync = {},
                    onRetry = {},
                )
            }
        }

        composeRule.onNodeWithContentDescription("Add server").assertIsDisplayed().performClick()
        composeRule.runOnIdle { assertEquals(1, added) }
    }
}
