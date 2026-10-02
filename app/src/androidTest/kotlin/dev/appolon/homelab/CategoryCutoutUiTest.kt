package dev.appolon.homelab

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.appolon.homelab.data.HomeUiState
import dev.appolon.homelab.data.HomerConfig
import dev.appolon.homelab.data.ServiceGroup
import dev.appolon.homelab.data.ServiceItem
import dev.appolon.homelab.ui.ServiceList
import dev.appolon.homelab.ui.theme.HomelabTheme
import dev.appolon.homelab.ui.theme.ThemeSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CategoryCutoutUiTest {
    @get:Rule val composeRule = createComposeRule()

    @Test fun categoryKeepsCameraClearanceWithoutDoublingExpandedHeaderPadding() {
        var headerPixels by mutableStateOf(180)
        val state = HomeUiState(config = HomerConfig(services = listOf(
            ServiceGroup("Media", items = listOf(ServiceItem("Jellyfin", url = "https://example.com/jellyfin"))),
        )))
        composeRule.setContent {
            val padding = with(LocalDensity.current) { headerPixels.toDp() }
            HomelabTheme(themeSource = ThemeSource.SYSTEM, homerColors = null) {
                ServiceList(
                    state = state,
                    contentPadding = PaddingValues(top = padding),
                    topSafeInsets = WindowInsets(top = 120),
                    onRefresh = {},
                    onServiceClick = { _, _ -> },
                )
            }
        }
        fun categoryTop() = composeRule.onNodeWithText("MEDIA").fetchSemanticsNode().boundsInRoot.top
        val expandedTop = categoryTop()
        composeRule.runOnIdle { headerPixels = 0 }
        composeRule.onNodeWithText("MEDIA").assertIsDisplayed()
        val collapsedTop = categoryTop()
        assertTrue(collapsedTop >= 120f)
        assertEquals(60f, expandedTop - collapsedTop, 1f)
        composeRule.runOnIdle { headerPixels = 180 }
        assertEquals(expandedTop, categoryTop(), 1f)
    }
}
