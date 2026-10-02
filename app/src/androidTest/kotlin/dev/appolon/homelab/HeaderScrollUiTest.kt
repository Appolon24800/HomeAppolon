package dev.appolon.homelab

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToIndex
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.appolon.homelab.data.HomeUiState
import dev.appolon.homelab.data.HomerConfig
import dev.appolon.homelab.data.ServiceGroup
import dev.appolon.homelab.data.ServiceItem
import dev.appolon.homelab.ui.HomeTopBar
import dev.appolon.homelab.ui.ServiceList
import dev.appolon.homelab.ui.theme.HomelabTheme
import dev.appolon.homelab.ui.theme.ThemeSource
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@OptIn(ExperimentalMaterial3Api::class)
@RunWith(AndroidJUnit4::class)
class HeaderScrollUiTest {
    @get:Rule val composeRule = createComposeRule()

    @Test fun headerAndActionsStayVisibleWhileServicesScroll() {
        val state = HomeUiState(config = HomerConfig(services = listOf(
            ServiceGroup("Media", items = List(60) { ServiceItem("Service $it", url = "https://example.com/$it") }),
        )))
        composeRule.setContent {
            HomelabTheme(themeSource = ThemeSource.SYSTEM, homerColors = null) {
                Scaffold(topBar = {
                    HomeTopBar(
                        state = state,
                        homerBarActive = false,
                        onSearch = {},
                        onLink = { _, _ -> },
                        onToggleTheme = {},
                        onOpenAccount = {},
                        modifier = Modifier.testTag("header"),
                    )
                }) { padding ->
                    ServiceList(
                        state = state,
                        contentPadding = padding,
                        onRefresh = {},
                        onServiceClick = { _, _ -> },
                    )
                }
            }
        }
        val initialBounds = composeRule.onNodeWithTag("header").fetchSemanticsNode().boundsInRoot
        composeRule.onNodeWithText("Service 0").assertIsDisplayed()
        composeRule.onNode(hasScrollAction()).performScrollToIndex(25)
        composeRule.onNodeWithText("Service 0").assertDoesNotExist()
        composeRule.onNodeWithText("LaPlateforme").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Search").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Links").assertIsDisplayed()
        assertEquals(initialBounds, composeRule.onNodeWithTag("header").fetchSemanticsNode().boundsInRoot)
    }
}
