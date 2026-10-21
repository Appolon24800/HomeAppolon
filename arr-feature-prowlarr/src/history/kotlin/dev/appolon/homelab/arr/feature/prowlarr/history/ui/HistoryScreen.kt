package dev.appolon.homelab.arr.feature.prowlarr.history.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.RssFeed
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.appolon.homelab.arr.api.prowlarr.model.HistoryEventType
import dev.appolon.homelab.arr.core.ArrError
import dev.appolon.homelab.arr.core.ArrTime
import dev.appolon.homelab.arr.feature.prowlarr.R
import dev.appolon.homelab.arr.feature.prowlarr.history.data.HistoryRow
import dev.appolon.homelab.arr.feature.prowlarr.history.vm.HistoryMessage
import dev.appolon.homelab.arr.feature.prowlarr.history.vm.HistoryUiState
import dev.appolon.homelab.arr.ui.components.FilterChipRow
import dev.appolon.homelab.arr.ui.lce.ArrLceBox
import dev.appolon.homelab.arr.ui.lce.ArrPullToRefreshBox
import dev.appolon.homelab.arr.ui.lce.LceState
import dev.appolon.homelab.arr.ui.lce.arrErrorMessage
import dev.appolon.homelab.arr.ui.shell.ArrScaffold
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import dev.appolon.homelab.arr.ui.R as ArrUiR

/** Spoken label for one event type, shared by rows and their icons. */
@Composable
private fun eventLabel(type: HistoryEventType): String = stringResource(
    when (type) {
        HistoryEventType.INDEXER_QUERY -> R.string.arr_prowlarr_history_event_query
        HistoryEventType.INDEXER_RSS -> R.string.arr_prowlarr_history_event_rss
        HistoryEventType.RELEASE_GRABBED -> ArrUiR.string.arr_common_grab_sheet_action
        HistoryEventType.INDEXER_AUTH -> R.string.arr_prowlarr_history_event_auth
        HistoryEventType.INDEXER_INFO -> ArrUiR.string.arr_common_general_log_level_info
        HistoryEventType.UNKNOWN -> ArrUiR.string.arr_common_status_unknown
    },
)

private fun eventIcon(type: HistoryEventType): ImageVector = when (type) {
    HistoryEventType.INDEXER_QUERY -> Icons.Filled.Search
    HistoryEventType.INDEXER_RSS -> Icons.Filled.RssFeed
    HistoryEventType.RELEASE_GRABBED -> Icons.Filled.Download
    HistoryEventType.INDEXER_AUTH -> Icons.Filled.Verified
    HistoryEventType.INDEXER_INFO, HistoryEventType.UNKNOWN -> Icons.Filled.Info
}

/**
 * Prowlarr history: the server's query log, newest first. Indexer filtering
 * and paging run server-side, the free-text filter narrows the loaded window
 * locally, and rows expand to timing, cache, and result details. Wide layouts
 * inline the details instead.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    state: HistoryUiState,
    lce: LceState<HistoryUiState>,
    onRefresh: () -> Unit,
    onLoadMore: () -> Unit,
    onQueryChange: (String) -> Unit,
    onIndexerFilterSelect: (Int?) -> Unit,
    onRequestClear: () -> Unit,
    onClearConfirm: () -> Unit,
    onClearDismiss: () -> Unit,
    onConsumeMessage: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val messageText = state.message?.let { historyMessageText(it) }
    LaunchedEffect(messageText) {
        if (messageText != null) {
            snackbarHostState.showSnackbar(messageText)
            onConsumeMessage()
        }
    }

    ArrScaffold(
        title = stringResource(ArrUiR.string.arr_common_history_title),
        subtitle = subtitleFor(state),
        actions = {
            val clearCd = stringResource(R.string.arr_prowlarr_history_cd_clear)
            IconButton(
                onClick = onRequestClear,
                modifier = Modifier.semantics { contentDescription = clearCd },
            ) {
                Icon(imageVector = Icons.Filled.DeleteSweep, contentDescription = null)
            }
        },
        filterBar = {
            Column {
                OutlinedTextField(
                    value = state.query,
                    onValueChange = onQueryChange,
                    placeholder = {
                        Text(stringResource(R.string.arr_prowlarr_history_search_hint))
                    },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, top = 4.dp),
                )
                val allIndexersLabel = stringResource(R.string.arr_prowlarr_history_filter_all)
                FilterChipRow(
                    filters = listOf<HistoryFilterOption>(HistoryFilterOption.All) +
                        state.indexers.map { HistoryFilterOption(it.id, it.name ?: "#${it.id}") },
                    selected = { option ->
                        option.id == state.indexerFilter
                    },
                    label = { option ->
                        if (option.id == null) allIndexersLabel else option.label
                    },
                    onSelect = { option -> onIndexerFilterSelect(option.id) },
                )
            }
        },
        modifier = modifier,
    ) { padding ->
        ArrPullToRefreshBox(
            isRefreshing = state.refreshing,
            onRefresh = onRefresh,
            modifier = Modifier.fillMaxSize(),
        ) {
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(padding),
            ) {
                ArrLceBox(
                    state = lce,
                    onRetry = onRefresh,
                    emptyTitle = stringResource(R.string.arr_prowlarr_history_empty_title),
                    emptyMessage = stringResource(R.string.arr_prowlarr_history_empty_message),
                    modifier = Modifier.weight(1f),
                ) { _ ->
                    HistoryList(state, onLoadMore)
                }
                if (state.clearQueued) {
                    // Polite live region: the queued-clear state changes without
                    // focus moving, so it must announce itself.
                    AssistChip(
                        onClick = {},
                        enabled = false,
                        label = {
                            Text(stringResource(R.string.arr_prowlarr_history_clear_queued))
                        },
                        modifier = Modifier
                            .padding(bottom = 4.dp)
                            .align(Alignment.CenterHorizontally)
                            .semantics { liveRegion = LiveRegionMode.Polite },
                    )
                }
                SnackbarHost(hostState = snackbarHostState) { data ->
                    Snackbar(snackbarData = data)
                }
            }
        }
    }

    if (state.confirmClear) {
        AlertDialog(
            onDismissRequest = onClearDismiss,
            title = { Text(stringResource(R.string.arr_prowlarr_history_clear_title)) },
            text = { Text(stringResource(R.string.arr_prowlarr_history_clear_message)) },
            confirmButton = {
                TextButton(onClick = onClearConfirm) {
                    Text(stringResource(ArrUiR.string.arr_common_import_flow_clear_selection))
                }
            },
            dismissButton = {
                TextButton(onClick = onClearDismiss) {
                    Text(stringResource(ArrUiR.string.arr_common_cancel))
                }
            },
        )
    }
}

/** One selectable indexer filter chip; [HistoryFilterOption.All] clears it. */
private data class HistoryFilterOption(val id: Int?, val label: String) {
    companion object {
        val All = HistoryFilterOption(null, "")
    }
}

@Composable
private fun subtitleFor(state: HistoryUiState): String = if (state.query.isBlank()) {
    stringResource(R.string.arr_prowlarr_history_subtitle, state.totalCount)
} else {
    stringResource(
        R.string.arr_prowlarr_history_subtitle_filtered,
        state.rows.size,
        state.totalCount,
    )
}

/** The scrollable log with its infinite-scroll trigger and paging footer. */
@Composable
private fun HistoryList(state: HistoryUiState, onLoadMore: () -> Unit) {
    val listState = rememberLazyListState()
    val rows = state.rows
    LaunchedEffect(listState, rows.size, state.totalCount, state.endReached) {
        snapshotFlow { listState.layoutInfo }
            .map { info ->
                val lastVisible = info.visibleItemsInfo.lastOrNull()?.index ?: 0
                lastVisible >= info.totalItemsCount - LOAD_MORE_THRESHOLD
            }
            .distinctUntilChanged()
            .collect { nearEnd -> if (nearEnd) onLoadMore() }
    }

    BoxWithConstraints {
        val wide = maxWidth >= 600.dp
        LazyColumn(
            state = listState,
            contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 8.dp),
            modifier = Modifier
                .fillMaxSize()
                // One traversal group: collapsed rows and expanded details
                // belong to the same logical list.
                .focusGroup(),
        ) {
            items(rows, key = { it.id }) { row ->
                HistoryEventRow(row, wide)
                HorizontalDivider()
            }
            if (state.loadingMore) {
                item(key = "loading-more") {
                    val loadingCd = stringResource(ArrUiR.string.arr_common_loading_more)
                    Row(
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .size(24.dp)
                                .semantics { contentDescription = loadingCd },
                        )
                    }
                }
            }
            state.appendError?.let { error ->
                item(key = "append-error") {
                    AppendErrorRow(error, onRetry = onLoadMore)
                }
            }
        }
    }
}

/** Retry row shown when a page load fails; the list above stays intact. */
@Composable
private fun AppendErrorRow(error: ArrError, onRetry: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp),
    ) {
        Text(
            arrErrorMessage(error),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        TextButton(onClick = onRetry) {
            Text(stringResource(ArrUiR.string.arr_common_retry))
        }
    }
}

/** One event: icon, query line, indexer and time, expandable details. */
@Composable
private fun HistoryEventRow(row: HistoryRow, wide: Boolean) {
    var expanded by remember(row.id) { mutableStateOf(false) }
    val cd = stringResource(
        if (expanded) R.string.arr_prowlarr_history_cd_collapse
        else ArrUiR.string.arr_common_roots_cd_open,
        row.title.ifBlank { eventLabel(row.event.eventType) },
    )
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clickable { expanded = !expanded }
            .semantics { contentDescription = cd }
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Icon(
            imageVector = eventIcon(row.event.eventType),
            contentDescription = eventLabel(row.event.eventType),
            tint = if (row.event.successful) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.error
            },
            modifier = Modifier.size(24.dp),
        )
        Spacer(Modifier.size(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                row.title.ifBlank { eventLabel(row.event.eventType) },
                style = MaterialTheme.typography.bodyLarge,
                maxLines = if (expanded) 3 else 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                stringResource(
                    ArrUiR.string.arr_common_metadata_pair,
                    row.indexerName,
                    row.occurredAt?.let { ArrTime.relative(it) }.orEmpty(),
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!row.event.successful) {
                Text(
                    stringResource(ArrUiR.string.arr_common_history_filter_failed),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
        if (wide) {
            InlineDetail(
                label = stringResource(R.string.arr_prowlarr_history_timing),
                value = row.elapsedMs?.let { ms ->
                    stringResource(R.string.arr_prowlarr_history_ms_value, ms)
                },
            )
            InlineDetail(
                label = stringResource(R.string.arr_prowlarr_history_results),
                value = row.resultCount?.toString(),
            )
        }
        Icon(
            imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    if (expanded) {
        HistoryDetails(row)
    }
}

@Composable
private fun InlineDetail(label: String, value: String?) {
    Column(
        horizontalAlignment = Alignment.End,
        modifier = Modifier.padding(start = 12.dp),
    ) {
        Text(value ?: "-", style = MaterialTheme.typography.bodyMedium)
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Expanded per-row details: timing, cache state, response size, origin. */
@Composable
private fun HistoryDetails(row: HistoryRow) {
    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 52.dp, end = 16.dp, bottom = 12.dp),
    ) {
        DetailLine(
            stringResource(R.string.arr_prowlarr_history_timing),
            row.elapsedMs?.let { stringResource(R.string.arr_prowlarr_history_ms_value, it) },
        )
        DetailLine(
            stringResource(R.string.arr_prowlarr_history_cached),
            when (row.cached) {
                true -> stringResource(ArrUiR.string.arr_common_yes)
                false -> stringResource(ArrUiR.string.arr_common_no)
                null -> null
            },
        )
        DetailLine(
            stringResource(R.string.arr_prowlarr_history_results),
            row.resultCount?.toString(),
        )
        DetailLine(
            stringResource(R.string.arr_prowlarr_history_query_type),
            row.queryType,
        )
        DetailLine(stringResource(ArrUiR.string.arr_common_history_details_source), row.source)
        DetailLine(stringResource(ArrUiR.string.arr_common_general_section_host), row.host)
    }
}

@Composable
private fun DetailLine(label: String, value: String?) {
    Row {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(
            value ?: stringResource(ArrUiR.string.arr_common_wanted_size_none),
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

/** One-line snackbar text for a screen message. */
@Composable
private fun historyMessageText(message: HistoryMessage): String = when (message) {
    HistoryMessage.Cleared -> stringResource(R.string.arr_prowlarr_history_cleared)
    HistoryMessage.ClearQueued -> stringResource(R.string.arr_prowlarr_history_clear_queued)
    is HistoryMessage.ClearFailed -> arrErrorMessage(message.error)
}

private const val LOAD_MORE_THRESHOLD = 6
