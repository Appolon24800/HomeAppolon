package dev.appolon.homelab.arr.ui.lce

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

/**
 * Single wrapper around material3's still-experimental PullToRefreshBox so
 * that when the API signature settles, the fix lands in one file instead of
 * every screen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArrPullToRefreshBox(
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    contentAlignment: Alignment = Alignment.TopStart,
    content: @Composable BoxScope.() -> Unit,
) {
    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
        modifier = modifier,
        contentAlignment = contentAlignment,
        content = content,
    )
}
