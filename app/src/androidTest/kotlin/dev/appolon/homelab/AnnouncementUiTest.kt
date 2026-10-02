package dev.appolon.homelab

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.appolon.homelab.data.HomeUiState
import dev.appolon.homelab.data.HomerConfig
import dev.appolon.homelab.data.MessageInfo
import dev.appolon.homelab.ui.ServiceList
import dev.appolon.homelab.ui.theme.HomelabTheme
import dev.appolon.homelab.ui.theme.ThemeSource
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AnnouncementUiTest {
    @get:Rule val composeRule = createComposeRule()

    @Test fun announcementCannotBeDismissedAndStaysVisibleDuringSearch() {
        var state by mutableStateOf(HomeUiState(
            config = HomerConfig(),
            message = MessageInfo("Information", "Planned maintenance tonight"),
        ))
        composeRule.setContent {
            HomelabTheme(themeSource = ThemeSource.HOMER, homerColors = null) {
                ServiceList(
                    state = state,
                    contentPadding = PaddingValues(),
                    onRefresh = {},
                    onServiceClick = { _, _ -> },
                )
            }
        }
        composeRule.onNodeWithText("Planned maintenance tonight").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Dismiss message").assertDoesNotExist()
        composeRule.runOnIdle { state = state.copy(query = "music") }
        composeRule.onNodeWithText("Planned maintenance tonight").assertIsDisplayed()
        composeRule.runOnIdle { state = state.copy(message = MessageInfo("Information", "Maintenance complete")) }
        composeRule.onNodeWithText("Maintenance complete").assertIsDisplayed()
        composeRule.onNodeWithText("Planned maintenance tonight").assertDoesNotExist()
    }
}
