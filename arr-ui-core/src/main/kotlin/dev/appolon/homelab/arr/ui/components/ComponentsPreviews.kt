package dev.appolon.homelab.arr.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material3.IconButton
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.appolon.homelab.arr.ui.theme.ArrTheme

@Preview
@Composable
private fun PosterCardPreview() {
    ArrTheme {
        Box(Modifier.width(140.dp)) {
            PosterCard(
                title = "Blade Runner 2049",
                subtitle = "2017",
                posterUrl = null,
                badges = {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.tertiaryContainer,
                    ) {
                        Text(
                            text = "4K",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    }
                },
                onClick = {},
            )
        }
    }
}

@Preview
@Composable
private fun FilterChipRowPreview() {
    val filters = listOf("All", "Monitored", "Missing", "Continuing")
    ArrTheme {
        var selected by remember { mutableStateOf("All") }
        FilterChipRow(
            filters = filters,
            selected = { it == selected },
            label = { it },
            onSelect = { selected = it },
        )
    }
}

@Preview
@Composable
private fun SortMenuPreview() {
    val options = listOf("By title", "By added date", "By release date")
    ArrTheme {
        var selected by remember { mutableStateOf(options.first()) }
        Row(modifier = Modifier.padding(8.dp)) {
            SortMenu(
                options = options,
                selected = selected,
                label = { it },
                onSelect = { selected = it },
            )
        }
    }
}

@Preview
@Composable
private fun EmptyStatePreview() {
    ArrTheme {
        EmptyState(
            title = "No movies monitored",
            message = "Add a movie to start tracking downloads.",
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Preview
@Composable
private fun SectionHeaderPreview() {
    ArrTheme {
        SectionHeader(title = "In theatres") {
            TextButton(onClick = {}) {
                Text(text = "See all")
            }
        }
    }
}

@Preview
@Composable
private fun StatTilePreview() {
    ArrTheme {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(16.dp),
        ) {
            StatTile(label = "Movies", value = "128", icon = Icons.Filled.Movie, modifier = Modifier.weight(1f))
            StatTile(label = "Missing", value = "3", modifier = Modifier.weight(1f))
        }
    }
}

@Preview
@Composable
private fun QueueRowDetermininatePreview() {
    ArrTheme {
        QueueRow(
            title = "Dune Part Two",
            status = QueueRowStatus.DOWNLOADING,
            subtitle = "WEBDL-1080p",
            progress = 0.42f,
            speedText = "12.4 MB/s, 3 min left",
            actions = {
                IconButton(onClick = {}) {
                    Icon(
                        imageVector = Icons.Filled.Pause,
                        contentDescription = "Pause Dune Part Two",
                    )
                }
            },
        )
    }
}

@Preview
@Composable
private fun QueueRowIndeterminatePreview() {
    ArrTheme {
        QueueRow(
            title = "Unknown release",
            status = QueueRowStatus.QUEUED,
            subtitle = "Searching for release",
            progress = null,
            onClick = {},
        )
    }
}

@Preview
@Composable
private fun CalendarDayHeaderPreview() {
    ArrTheme {
        Column {
            CalendarDayHeader(dayLabel = "Mon", dateLabel = "20", isToday = true)
            CalendarDayHeader(dayLabel = "Tue", dateLabel = "21")
        }
    }
}

@Preview
@Composable
private fun LabeledSwitchPreview() {
    ArrTheme {
        Column {
            LabeledSwitch(
                label = "Monitor this series",
                checked = true,
                supportingText = "New episodes are grabbed automatically.",
                onCheckedChange = {},
            )
            LabeledSwitch(label = "Season pack", checked = false, onCheckedChange = {})
        }
    }
}
