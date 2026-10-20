package dev.appolon.homelab.arr.ui.theme

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.appolon.homelab.arr.ui.components.StatTile

@Preview
@Composable
private fun ArrThemeLightPreview() {
    ArrThemeSample(dark = false)
}

@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ArrThemeDarkPreview() {
    ArrThemeSample(dark = true)
}

@Composable
private fun ArrThemeSample(dark: Boolean) {
    ArrTheme(darkTheme = dark) {
        Surface(modifier = Modifier.fillMaxSize()) {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(16.dp),
            ) {
                Text(text = "Radarr", style = MaterialTheme.typography.headlineMedium)
                Text(
                    text = "Library and queue overview",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                StatTile(label = "Movies", value = "128")
                Button(onClick = {}) {
                    Text(text = "Sample action")
                }
            }
        }
    }
}
