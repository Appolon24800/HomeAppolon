package dev.appolon.homelab.arr.feature.bazarr.episodes.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.appolon.homelab.arr.feature.bazarr.R
import dev.appolon.homelab.arr.feature.bazarr.episodes.model.BazarrHistoryEvent
import dev.appolon.homelab.arr.feature.bazarr.episodes.vm.SubtitleHistoryUiState
import dev.appolon.homelab.arr.ui.components.FilterChipRow
import dev.appolon.homelab.arr.ui.lce.ArrLceBox
import dev.appolon.homelab.arr.ui.lce.ArrPullToRefreshBox
import dev.appolon.homelab.arr.ui.lce.LceState
import dev.appolon.homelab.arr.ui.shell.ArrScaffold
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * History of subtitle decisions: the merged episode and movie feed, newest
 * first, with language chips from the languages endpoint and action chips
 * derived from the actions present in the feed.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun SubtitleHistoryScreen(
    state: SubtitleHistoryUiState,
    lce: LceState<SubtitleHistoryUiState>,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onLanguageFilter: (String?) -> Unit,
    onActionFilter: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    ArrScaffold(
        title = stringResource(R.string.arr_bazarr_history_title),
        onBack = onBack,
        actions = {
            val cd = stringResource(R.string.arr_bazarr_cd_refresh)
            IconButton(
                onClick = onRefresh,
                modifier = Modifier.semantics { contentDescription = cd },
            ) {
                Icon(Icons.Filled.Refresh, contentDescription = cd)
            }
        },
        filterBar = {
            LanguageFilterChips(
                languages = state.languages,
                selected = state.selectedLanguage,
                onSelect = onLanguageFilter,
            )
            if (state.actions.isNotEmpty()) {
                FilterChipRow(
                    filters = state.actions,
                    selected = { it == state.selectedAction },
                    label = { it },
                    onSelect = onActionFilter,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        },
        modifier = modifier,
    ) { padding ->
        BoxWithConstraints(modifier = Modifier.fillMaxSize().padding(padding)) {
            val contentWidth = if (maxWidth >= 840.dp) 800.dp else maxWidth
            ArrLceBox(
                state = lce,
                onRetry = onRefresh,
                emptyTitle = stringResource(R.string.arr_bazarr_history_empty_title),
                emptyMessage = stringResource(R.string.arr_bazarr_history_empty_message),
                modifier = Modifier.fillMaxSize(),
            ) { _ ->
                ArrPullToRefreshBox(
                    isRefreshing = state.refreshing,
                    onRefresh = onRefresh,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        items(state.presented, key = { "${it.timestampMillis}_${it.episodeId ?: 0}_${it.radarrId ?: 0}_${it.description}" }) { event ->
                            HistoryEventRow(
                                event = event,
                                modifier = Modifier.fillMaxWidth().widthIn(max = contentWidth),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoryEventRow(event: BazarrHistoryEvent, modifier: Modifier = Modifier) {
    Card(modifier = modifier) {
        ListItem(
            leadingContent = {
                val cd = if (event.isMovie) {
                    stringResource(R.string.arr_bazarr_cd_movie_event)
                } else {
                    stringResource(R.string.arr_bazarr_cd_episode_event)
                }
                if (event.isMovie) {
                    Icon(Icons.Filled.Movie, contentDescription = cd)
                } else {
                    Icon(Icons.Filled.Tv, contentDescription = cd)
                }
            },
            headlineContent = {
                Text(
                    text = buildString {
                        append(event.action.ifBlank { stringResource(R.string.arr_bazarr_history_unknown_action) })
                        if (event.languageCode2.isNotBlank()) append(" · ").append(event.languageCode2)
                        event.score?.let { append(" · ").append(stringResource(R.string.arr_bazarr_score, it.toInt())) }
                    },
                    style = MaterialTheme.typography.titleSmall,
                )
            },
            supportingContent = {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    if (event.description.isNotBlank()) {
                        Text(
                            text = event.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        val label = if (event.isMovie) {
                            event.title
                        } else {
                            listOfNotNull(
                                event.seriesTitle.ifBlank { null },
                                event.seasonNumber?.let { s ->
                                    event.episodeNumber?.let { e ->
                                        String.format(java.util.Locale.ROOT, "S%02dE%02d", s, e)
                                    }
                                },
                                event.title.ifBlank { null },
                            ).joinToString(" ")
                        }
                        if (label.isNotBlank()) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                        if (event.timestampMillis > 0) {
                            Text(
                                text = formatTimestamp(event.timestampMillis),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (event.provider.isNotBlank()) {
                            Text(
                                text = stringResource(R.string.arr_bazarr_provider_name, event.provider),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            },
        )
    }
}

/** Local-time short timestamp; epoch 0 events render no date at all. */
private fun formatTimestamp(millis: Long): String =
    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
        .withZone(ZoneId.systemDefault())
        .format(Instant.ofEpochMilli(millis))
