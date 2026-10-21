package dev.appolon.homelab.arr.ui.lce

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import dev.appolon.homelab.arr.core.ArrError

@Preview
@Composable
private fun ArrLceBoxLoadingPreview() {
    ArrThemeWrapper {
        ArrLceBox<String>(state = LceState.Loading, onRetry = {}) { _ -> }
    }
}

@Preview
@Composable
private fun ArrLceBoxErrorPreview() {
    ArrThemeWrapper {
        ArrLceBox<String>(
            state = LceState.Error(ArrError.Http(502, null)),
            onRetry = {},
        ) { _ -> }
    }
}

@Preview
@Composable
private fun ArrLceBoxEmptyPreview() {
    ArrThemeWrapper {
        ArrLceBox<String>(state = LceState.Empty, onRetry = {}) { _ -> }
    }
}

@Preview
@Composable
private fun ArrLceBoxContentPreview() {
    ArrThemeWrapper {
        ArrLceBox(
            state = LceState.Content(listOf("Radarr", "Sonarr", "Lidarr")),
            onRetry = {},
        ) { servers ->
            Column(modifier = Modifier.fillMaxSize()) {
                servers.forEach { Text(it) }
            }
        }
    }
}

@Composable
private fun ArrThemeWrapper(content: @Composable () -> Unit) {
    dev.appolon.homelab.arr.ui.theme.ArrTheme { content() }
}
