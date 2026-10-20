package dev.appolon.homelab.arr.feature.radarr.search.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.appolon.homelab.arr.feature.radarr.R
import dev.appolon.homelab.arr.feature.radarr.search.data.ReleaseRowUi
import dev.appolon.homelab.arr.feature.radarr.search.data.ReleaseSort
import dev.appolon.homelab.arr.feature.radarr.search.vm.GrabMessage
import dev.appolon.homelab.arr.feature.radarr.search.vm.ReleaseListUiState
import dev.appolon.homelab.arr.ui.components.EmptyState
import dev.appolon.homelab.arr.ui.components.SortMenu
import dev.appolon.homelab.arr.ui.lce.ArrLceBox
import dev.appolon.homelab.arr.ui.lce.ArrPullToRefreshBox
import dev.appolon.homelab.arr.ui.lce.LceState
import dev.appolon.homelab.arr.ui.lce.arrErrorMessage
import dev.appolon.homelab.arr.ui.shell.ArrScaffold

/**
 * Interactive search screen: one movie's releases with quality, custom
 * format score, peers, age, size, and indexer; rejections expand in place;
 * the sort menu reorders client-side; the grab button opens the grab sheet.
 * Tablets (width from 840dp) get the list plus a details pane for the
 * expanded release.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReleaseListScreen(
    state: ReleaseListUiState,
    queued: Boolean,
    onRefresh: () -> Unit,
    onRetry: () -> Unit,
    onBack: () -> Unit,
    onSort: (ReleaseSort) -> Unit,
    onToggleRejections: (String) -> Unit,
    onGrab: (ReleaseRowUi) -> Unit,
    onConsumeMessage: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    // Resolved in composable scope: arrErrorMessage is itself composable and
    // cannot run inside the LaunchedEffect coroutine below.
    val messageText = grabMessageText(state.message)
    LaunchedEffect(state.message) {
        when (messageText) {
            null -> Unit
            else -> {
                snackbarHostState.showSnackbar(messageText)
                onConsumeMessage()
            }
        }
    }
    val sortLabels = ReleaseSort.entries.associateWith { sort ->
        stringResource(sort.labelRes)
    }
    Box(modifier) {
        ArrScaffold(
            title = stringResource(R.string.arr_radarr_releases_title),
            onBack = onBack,
            actions = {
                IconButton(onClick = onRefresh) {
                    Icon(
                        imageVector = Icons.Filled.Refresh,
                        contentDescription = stringResource(
                            R.string.arr_radarr_releases_cd_refresh,
                        ),
                    )
                }
                SortMenu(
                    options = ReleaseSort.entries,
                    selected = state.sort,
                    label = { sort -> sortLabels[sort].orEmpty() },
                    onSelect = onSort,
                )
            },
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            ) {
                if (queued) {
                    Banner(stringResource(R.string.arr_radarr_releases_pending_banner))
                }
                if (state.stale) {
                    Banner(stringResource(R.string.arr_radarr_releases_stale_banner))
                }
                ArrPullToRefreshBox(
                    isRefreshing = (state.releases as? LceState.Content)?.refreshing == true,
                    onRefresh = onRefresh,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    ArrLceBox(
                        state = state.releases,
                        onRetry = onRetry,
                        emptyTitle = stringResource(R.string.arr_radarr_releases_empty_title),
                        emptyMessage = stringResource(
                            R.string.arr_radarr_releases_empty_message,
                        ),
                        modifier = Modifier.fillMaxSize(),
                    ) { rows ->
                        ReleaseListBody(
                            rows = rows,
                            state = state,
                            onToggleRejections = onToggleRejections,
                            onGrab = onGrab,
                        )
                    }
                }
            }
        }
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

private val ReleaseSort.labelRes: Int
    get() = when (this) {
        ReleaseSort.QUALITY_SCORE -> R.string.arr_radarr_releases_sort_quality
        ReleaseSort.SEEDERS -> R.string.arr_radarr_releases_sort_seeders
        ReleaseSort.AGE -> R.string.arr_radarr_releases_sort_age
        ReleaseSort.SIZE -> R.string.arr_radarr_releases_sort_size
    }

/** Snackbar text for the current one-shot grab outcome; null when none. */
@Composable
private fun grabMessageText(message: GrabMessage?): String? = when (message) {
    null -> null
    GrabMessage.Grabbed -> stringResource(R.string.arr_radarr_releases_notice_grabbed)
    GrabMessage.GrabQueued -> stringResource(R.string.arr_radarr_releases_notice_grab_queued)
    is GrabMessage.GrabFailed -> arrErrorMessage(message.error)
}

@Composable
private fun Banner(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSecondaryContainer,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

@Composable
private fun ReleaseListBody(
    rows: List<ReleaseRowUi>,
    state: ReleaseListUiState,
    onToggleRejections: (String) -> Unit,
    onGrab: (ReleaseRowUi) -> Unit,
) {
    val wide = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp >= 840
    if (wide) {
        Row(modifier = Modifier.fillMaxSize()) {
            ReleaseList(
                rows = rows,
                expandedGuids = state.expandedGuids,
                onToggleRejections = onToggleRejections,
                onGrab = onGrab,
                modifier = Modifier
                    .fillMaxSize()
                    .weight(0.58f),
            )
            val expanded = rows.firstOrNull {
                it.release.guid != null && it.release.guid in state.expandedGuids
            }
            ReleaseDetailsPane(
                row = expanded,
                onGrab = onGrab,
                modifier = Modifier
                    .fillMaxSize()
                    .weight(0.42f),
            )
        }
    } else {
        ReleaseList(
            rows = rows,
            expandedGuids = state.expandedGuids,
            onToggleRejections = onToggleRejections,
            onGrab = onGrab,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Composable
private fun ReleaseList(
    rows: List<ReleaseRowUi>,
    expandedGuids: Set<String>,
    onToggleRejections: (String) -> Unit,
    onGrab: (ReleaseRowUi) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(modifier = modifier) {
        items(rows, key = { it.release.guid ?: it.title }) { row ->
            ReleaseRow(
                row = row,
                expanded = row.release.guid != null && row.release.guid in expandedGuids,
                onToggleRejections = onToggleRejections,
                onGrab = onGrab,
            )
            HorizontalDivider()
        }
    }
}

@Composable
private fun ReleaseRow(
    row: ReleaseRowUi,
    expanded: Boolean,
    onToggleRejections: (String) -> Unit,
    onGrab: (ReleaseRowUi) -> Unit,
) {
    val showRejectionsCd = stringResource(
        R.string.arr_radarr_releases_cd_rejections,
        row.title,
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = row.title,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = row.indexer,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(onClick = { onGrab(row) }) {
                Icon(
                    imageVector = Icons.Filled.Download,
                    contentDescription = stringResource(
                        R.string.arr_radarr_releases_cd_grab,
                        row.title,
                    ),
                )
            }
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(top = 4.dp),
        ) {
            Surface(
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.semantics { contentDescription = row.qualityName },
            ) {
                Text(
                    text = row.qualityName.ifBlank { row.protocolLabel },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
            Spacer(Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.arr_radarr_releases_score, row.qualityScore),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (row.customFormats.isNotBlank()) {
                Spacer(Modifier.width(8.dp))
                Text(
                    text = row.customFormats,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(top = 2.dp),
        ) {
            Text(
                text = row.sizeText,
                style = MaterialTheme.typography.labelMedium,
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = ageText(row),
                style = MaterialTheme.typography.labelMedium,
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = peersText(row),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (row.hasRejections) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .clickable { row.release.guid?.let(onToggleRejections) }
                    .semantics {
                        contentDescription = showRejectionsCd
                    }
                    .padding(vertical = 4.dp),
            ) {
                Icon(
                    imageVector = if (expanded) {
                        Icons.Filled.ExpandLess
                    } else {
                        Icons.Filled.ExpandMore
                    },
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = stringResource(
                        R.string.arr_radarr_releases_rejections_count,
                        row.rejections.size,
                    ),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            if (expanded) {
                row.rejections.forEach { reason ->
                    Text(
                        text = reason,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 32.dp, bottom = 2.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun ageText(row: ReleaseRowUi): String = if (row.ageDays > 0) {
    stringResource(R.string.arr_radarr_releases_age_days, row.ageDays)
} else {
    stringResource(R.string.arr_radarr_releases_age_hours, row.ageHoursRounded)
}

@Composable
private fun peersText(row: ReleaseRowUi): String {
    val seeders = row.seeders
    val leechers = row.leechers
    return when {
        seeders == null -> stringResource(R.string.arr_radarr_releases_no_seeders)
        leechers == null -> stringResource(R.string.arr_radarr_releases_seeders, seeders)
        else -> stringResource(R.string.arr_radarr_releases_seeders_leechers, seeders, leechers)
    }
}

/** Tablet-only right pane: the expanded release's full details. */
@Composable
private fun ReleaseDetailsPane(
    row: ReleaseRowUi?,
    onGrab: (ReleaseRowUi) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (row == null) {
        EmptyState(
            title = stringResource(R.string.arr_radarr_releases_details_pane_hint_title),
            message = stringResource(R.string.arr_radarr_releases_details_pane_hint_message),
            modifier = modifier,
        )
        return
    }
    Column(modifier.padding(24.dp)) {
        Text(
            text = row.title,
            style = MaterialTheme.typography.titleMedium,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.padding(top = 12.dp))
        DetailLine(
            label = stringResource(R.string.arr_radarr_releases_details_quality),
            value = row.qualityName,
        )
        DetailLine(
            label = stringResource(R.string.arr_radarr_releases_details_custom_formats),
            value = row.customFormats,
        )
        DetailLine(
            label = stringResource(R.string.arr_radarr_releases_details_languages),
            value = row.languageNames,
        )
        DetailLine(
            label = stringResource(R.string.arr_radarr_releases_details_indexer),
            value = row.indexer,
        )
        DetailLine(
            label = stringResource(R.string.arr_radarr_releases_details_size),
            value = row.sizeText,
        )
        DetailLine(
            label = stringResource(R.string.arr_radarr_releases_details_age),
            value = ageText(row),
        )
        if (row.hasRejections) {
            Spacer(Modifier.padding(top = 8.dp))
            Text(
                text = stringResource(R.string.arr_radarr_releases_details_rejections),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.error,
            )
            row.rejections.forEach { reason ->
                Text(
                    text = reason,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.padding(top = 16.dp))
        IconButton(onClick = { onGrab(row) }) {
            Icon(
                imageVector = Icons.Filled.Download,
                contentDescription = stringResource(
                    R.string.arr_radarr_releases_cd_grab,
                    row.title,
                ),
            )
        }
    }
}

@Composable
private fun DetailLine(label: String, value: String) {
    if (value.isBlank()) return
    Row(modifier = Modifier.padding(vertical = 2.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(120.dp),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}
