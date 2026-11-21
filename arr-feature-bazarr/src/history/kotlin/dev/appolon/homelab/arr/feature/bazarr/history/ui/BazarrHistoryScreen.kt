package dev.appolon.homelab.arr.feature.bazarr.history.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.appolon.homelab.arr.feature.bazarr.R
import dev.appolon.homelab.arr.feature.bazarr.episodes.model.BazarrHistoryEvent
import dev.appolon.homelab.arr.feature.bazarr.episodes.ui.LanguageFilterChips
import dev.appolon.homelab.arr.feature.bazarr.history.vm.BazarrHistoryUiState
import dev.appolon.homelab.arr.feature.bazarr.history.vm.HistoryMediaFilter
import dev.appolon.homelab.arr.ui.components.FilterChipRow
import dev.appolon.homelab.arr.ui.lce.ArrLceBox
import dev.appolon.homelab.arr.ui.lce.ArrPullToRefreshBox
import dev.appolon.homelab.arr.ui.lce.LceState
import dev.appolon.homelab.arr.ui.shell.ArrScaffold

/**
 * History of subtitle decisions: the paged merged episode and movie feed.
 * Every row carries the action taken, its language, the provider, and the
 * match score; chips filter by media kind and language, and the list loads
 * the next page as its end scrolls into view.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun BazarrHistoryScreen(
    state: BazarrHistoryUiState,
    lce: LceState<BazarrHistoryUiState>,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onLoadMore: () -> Unit,
    onLanguageFilter: (String?) -> Unit,
    onMediaFilter: (HistoryMediaFilter) -> Unit,
    onOpenBlacklist: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    // Load the next page when the last visible row comes within sight of the end.
    val nearEnd by remember {
        derivedStateOf {
            val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            val total = listState.layoutInfo.totalItemsCount
            total > 0 && last >= total - 6
        }
    }
    LaunchedEffect(nearEnd) {
        if (nearEnd) onLoadMore()
    }

    ArrScaffold(
        title = stringResource(R.string.arr_bazarr_history_title),
        onBack = onBack,
        actions = {
            val blacklistCd = stringResource(R.string.arr_bazarr_history_cd_open_blacklist)
            IconButton(
                onClick = onOpenBlacklist,
                modifier = Modifier.semantics { contentDescription = blacklistCd },
            ) {
                Icon(Icons.Filled.Block, contentDescription = blacklistCd)
            }
            val cd = stringResource(R.string.arr_bazarr_cd_refresh)
            IconButton(
                onClick = onRefresh,
                modifier = Modifier.semantics { contentDescription = cd },
            ) {
                Icon(Icons.Filled.Refresh, contentDescription = cd)
            }
        },
        filterBar = {
            val mediaLabels = mapOf(
                HistoryMediaFilter.ALL to stringResource(R.string.arr_bazarr_history_filter_all),
                HistoryMediaFilter.EPISODES to stringResource(R.string.arr_bazarr_history_filter_series),
                HistoryMediaFilter.MOVIES to stringResource(R.string.arr_bazarr_history_filter_movies),
            )
            FilterChipRow(
                filters = HistoryMediaFilter.entries,
                selected = { it == state.mediaFilter },
                label = { mediaLabels.getValue(it) },
                onSelect = { onMediaFilter(it) },
            )
            LanguageFilterChips(
                languages = state.languages,
                selected = state.selectedLanguage,
                onSelect = onLanguageFilter,
                modifier = Modifier.padding(top = 4.dp),
            )
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
                        state = listState,
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        items(
                            state.presented,
                            key = { "${it.timestampMillis}_${it.episodeId ?: 0}_${it.radarrId ?: 0}_${it.description}" },
                        ) { event ->
                            HistoryEventRow(
                                event = event,
                                modifier = Modifier.fillMaxWidth().widthIn(max = contentWidth),
                            )
                        }
                        item(key = "history_footer") {
                            HistoryFooter(
                                loadingMore = state.loadingMore,
                                endReached = state.endReached,
                                onLoadMore = onLoadMore,
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
private fun HistoryFooter(
    loadingMore: Boolean,
    endReached: Boolean,
    onLoadMore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
        when {
            loadingMore -> {
                val cd = stringResource(R.string.arr_bazarr_history_loading_more)
                CircularProgressIndicator(
                    modifier = Modifier
                        .semantics { contentDescription = cd }
                        .padding(12.dp),
                    strokeWidth = 2.dp,
                )
            }

            !endReached -> TextButton(onClick = onLoadMore) {
                Text(stringResource(R.string.arr_bazarr_history_load_more))
            }

            else -> Text(
                text = stringResource(R.string.arr_bazarr_history_end),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
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
                        append(historyActionLabel(event.action))
                        if (event.languageCode2.isNotBlank()) {
                            append(" · ").append(event.languageCode2.uppercase(java.util.Locale.ROOT))
                        }
                        event.score?.let { append(" · ").append(formatScore(it)) }
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
                    // Title on its own line, then time and provider: side by
                    // side in a Row, a long episode title crushed the others
                    // into one-letter columns.
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
                            maxLines = 2,
                        )
                    }
                    val meta = listOfNotNull(
                        event.timestampMillis.takeIf { it > 0 }?.let(::formatTimestamp),
                        event.provider.takeIf { it.isNotBlank() }?.let {
                            stringResource(R.string.arr_bazarr_provider_name, it)
                        },
                    ).joinToString(" · ")
                    if (meta.isNotEmpty()) {
                        Text(
                            text = meta,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            },
        )
    }
}

/**
 * Bazarr reports the action as a number (its web UI maps it to an icon):
 * 0 deleted, 1 downloaded, 2 manual, 3 upgraded, 4 uploaded, 5 synced,
 * 6 translated. Unknown codes and text fall back to a neutral label.
 */
@Composable
private fun historyActionLabel(action: String): String = when (action.trim()) {
    "0" -> stringResource(R.string.arr_bazarr_history_action_deleted)
    "1" -> stringResource(R.string.arr_bazarr_history_action_downloaded)
    "2" -> stringResource(R.string.arr_bazarr_history_action_manual)
    "3" -> stringResource(R.string.arr_bazarr_history_action_upgraded)
    "4" -> stringResource(R.string.arr_bazarr_history_action_uploaded)
    "5" -> stringResource(R.string.arr_bazarr_history_action_synced)
    "6" -> stringResource(R.string.arr_bazarr_history_action_translated)
    "" -> stringResource(R.string.arr_bazarr_history_unknown_action)
    else -> if (action.all { it.isDigit() }) {
        stringResource(R.string.arr_bazarr_history_unknown_action)
    } else {
        action
    }
}

/** Scores print as integers when whole, one decimal otherwise. */
private fun formatScore(score: Double): String =
    if (score == score.toLong().toDouble()) {
        score.toLong().toString()
    } else {
        String.format(java.util.Locale.ROOT, "%.1f", score)
    }

/** Relative time ("3h ago") like every other history feed in the app. */
private fun formatTimestamp(millis: Long): String =
    dev.appolon.homelab.arr.core.ArrTime.relative(millis)
