package dev.appolon.homelab.arr.feature.sonarr.search.ui

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
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.appolon.homelab.arr.api.sonarr.model.DownloadProtocol
import dev.appolon.homelab.arr.feature.sonarr.R
import dev.appolon.homelab.arr.feature.sonarr.search.data.ReleaseGroupFilter
import dev.appolon.homelab.arr.feature.sonarr.search.data.ReleaseRowUi
import dev.appolon.homelab.arr.feature.sonarr.search.data.ReleaseSort
import dev.appolon.homelab.arr.feature.sonarr.search.vm.ExternalPushInput
import dev.appolon.homelab.arr.feature.sonarr.search.vm.ReleaseListUiState
import dev.appolon.homelab.arr.feature.sonarr.search.vm.ReleaseMessage
import dev.appolon.homelab.arr.ui.components.EmptyState
import dev.appolon.homelab.arr.ui.components.FilterChipRow
import dev.appolon.homelab.arr.ui.components.SortMenu
import dev.appolon.homelab.arr.ui.lce.ArrLceBox
import dev.appolon.homelab.arr.ui.lce.ArrPullToRefreshBox
import dev.appolon.homelab.arr.ui.lce.LceState
import dev.appolon.homelab.arr.ui.lce.arrErrorMessage
import dev.appolon.homelab.arr.ui.shell.ArrScaffold
import dev.appolon.homelab.arr.ui.R as ArrUiR

/**
 * Interactive search screen: one scope's releases with quality, custom
 * format score, languages, peers, age, size, and indexer; season packs and
 * individual episodes split by the toggle; rejections expand in place; the
 * sort menu reorders client-side; the grab button opens the grab sheet and
 * the toolbar push action hands Sonarr an external release. Tablets (width
 * from 840dp) get the list plus a details pane for the expanded release.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReleaseListScreen(
    state: ReleaseListUiState,
    queued: Boolean,
    showGroupFilter: Boolean,
    onRefresh: () -> Unit,
    onRetry: () -> Unit,
    onBack: () -> Unit,
    onSort: (ReleaseSort) -> Unit,
    onGroupFilter: (ReleaseGroupFilter) -> Unit,
    onToggleRejections: (String) -> Unit,
    onGrab: (ReleaseRowUi) -> Unit,
    onPushExternal: () -> Unit,
    onPushDialogDismiss: () -> Unit,
    onPushConfirm: (ExternalPushInput) -> Unit,
    onConsumeMessage: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    // Resolved in composable scope: arrErrorMessage is itself composable and
    // cannot run inside the LaunchedEffect coroutine below.
    val messageText = releaseMessageText(state.message)
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
    val groupLabels = ReleaseGroupFilter.entries.associateWith { filter ->
        stringResource(filter.labelRes)
    }
    Box(modifier) {
        ArrScaffold(
            title = stringResource(ArrUiR.string.arr_common_indexers_mode_interactive),
            onBack = onBack,
            actions = {
                IconButton(onClick = onPushExternal) {
                    Icon(
                        imageVector = Icons.Filled.IosShare,
                        contentDescription = stringResource(
                            R.string.arr_sonarr_releases_cd_push,
                        ),
                    )
                }
                IconButton(onClick = onRefresh) {
                    Icon(
                        imageVector = Icons.Filled.Refresh,
                        contentDescription = stringResource(
                            ArrUiR.string.arr_common_releases_cd_refresh,
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
            filterBar = {
                if (showGroupFilter) {
                    FilterChipRow(
                        filters = ReleaseGroupFilter.entries,
                        selected = { filter -> filter == state.groupFilter },
                        label = { filter -> groupLabels[filter].orEmpty() },
                        onSelect = onGroupFilter,
                    )
                }
            },
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            ) {
                if (queued) {
                    Banner(stringResource(ArrUiR.string.arr_common_releases_pending_banner))
                }
                if (state.stale) {
                    Banner(stringResource(ArrUiR.string.arr_common_releases_stale_banner))
                }
                ArrPullToRefreshBox(
                    isRefreshing = (state.releases as? LceState.Content)?.refreshing == true,
                    onRefresh = onRefresh,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    ArrLceBox(
                        state = state.releases,
                        onRetry = onRetry,
                        emptyTitle = stringResource(ArrUiR.string.arr_common_releases_empty_title),
                        emptyMessage = stringResource(R.string.arr_sonarr_releases_empty_message),
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

    if (state.pushDialogOpen) {
        PushReleaseDialog(
            busy = state.pushing,
            onConfirm = onPushConfirm,
            onDismiss = onPushDialogDismiss,
        )
    }
}

private val ReleaseSort.labelRes: Int
    get() = when (this) {
        ReleaseSort.QUALITY_SCORE -> ArrUiR.string.arr_common_releases_sort_quality
        ReleaseSort.SEEDERS -> ArrUiR.string.arr_common_releases_sort_seeders
        ReleaseSort.AGE -> ArrUiR.string.arr_common_releases_sort_age
        ReleaseSort.SIZE -> ArrUiR.string.arr_common_size
    }

private val ReleaseGroupFilter.labelRes: Int
    get() = when (this) {
        ReleaseGroupFilter.ALL -> R.string.arr_sonarr_releases_filter_all
        ReleaseGroupFilter.SEASON_PACKS -> R.string.arr_sonarr_releases_filter_packs
        ReleaseGroupFilter.EPISODES -> R.string.arr_sonarr_releases_filter_episodes
    }

/** Snackbar text for the current one-shot outcome; null when none. */
@Composable
private fun releaseMessageText(message: ReleaseMessage?): String? = when (message) {
    null -> null
    ReleaseMessage.Grabbed -> stringResource(ArrUiR.string.arr_common_releases_notice_grabbed)
    ReleaseMessage.GrabQueued -> stringResource(ArrUiR.string.arr_common_releases_notice_grab_queued)
    is ReleaseMessage.GrabFailed -> arrErrorMessage(message.error)
    ReleaseMessage.Pushed -> stringResource(R.string.arr_sonarr_releases_notice_pushed)
    ReleaseMessage.PushQueued -> stringResource(R.string.arr_sonarr_releases_notice_push_queued)
    is ReleaseMessage.PushFailed -> arrErrorMessage(message.error)
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
        items(rows, key = { it.release.guid ?: it.title }, contentType = { "release" }) { row ->
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
        ArrUiR.string.arr_common_releases_cd_rejections,
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
                        ArrUiR.string.arr_common_releases_cd_grab,
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
            if (row.seasonPack) {
                Spacer(Modifier.width(8.dp))
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.secondaryContainer,
                ) {
                    Text(
                        text = stringResource(R.string.arr_sonarr_releases_season_pack),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }
            } else if (row.episodeLabel != null) {
                Spacer(Modifier.width(8.dp))
                Text(
                    text = row.episodeLabel.orEmpty(),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(8.dp))
            Text(
                text = stringResource(ArrUiR.string.arr_common_quality_format_score_row_support, row.qualityScore),
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
            if (row.languageNames.isNotBlank()) {
                Spacer(Modifier.width(12.dp))
                Text(
                    text = row.languageNames,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (row.hasRejections) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .clickable { row.release.guid?.let(onToggleRejections) }
                    .semantics { contentDescription = showRejectionsCd }
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
                        ArrUiR.string.arr_common_releases_rejections_count,
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
    stringResource(ArrUiR.string.arr_common_releases_age_days, row.ageDays)
} else {
    stringResource(ArrUiR.string.arr_common_releases_age_hours, row.ageHoursRounded)
}

@Composable
private fun peersText(row: ReleaseRowUi): String {
    val seeders = row.seeders
    val leechers = row.leechers
    return when {
        seeders == null -> stringResource(ArrUiR.string.arr_common_releases_no_seeders)
        leechers == null -> stringResource(ArrUiR.string.arr_common_releases_seeders, seeders)
        else -> stringResource(ArrUiR.string.arr_common_releases_seeders_leechers, seeders, leechers)
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
            title = stringResource(ArrUiR.string.arr_common_releases_details_pane_hint_title),
            message = stringResource(ArrUiR.string.arr_common_releases_details_pane_hint_message),
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
            label = stringResource(ArrUiR.string.arr_common_quality),
            value = row.qualityName,
        )
        DetailLine(
            label = stringResource(R.string.arr_sonarr_releases_details_kind),
            value = if (row.seasonPack) {
                stringResource(R.string.arr_sonarr_releases_season_pack)
            } else {
                row.episodeLabel.orEmpty()
            },
        )
        DetailLine(
            label = stringResource(ArrUiR.string.arr_common_quality_custom_formats_title),
            value = row.customFormats,
        )
        DetailLine(
            label = stringResource(ArrUiR.string.arr_common_quality_languages_title),
            value = row.languageNames,
        )
        DetailLine(
            label = stringResource(ArrUiR.string.arr_common_releases_details_indexer),
            value = row.indexer,
        )
        DetailLine(
            label = stringResource(ArrUiR.string.arr_common_size),
            value = row.sizeText,
        )
        DetailLine(
            label = stringResource(ArrUiR.string.arr_common_releases_sort_age),
            value = ageText(row),
        )
        DetailLine(
            label = stringResource(R.string.arr_sonarr_releases_details_approval),
            value = stringResource(
                if (row.approved) {
                    R.string.arr_sonarr_releases_approved
                } else {
                    R.string.arr_sonarr_releases_pending_approval
                },
            ),
        )
        if (row.hasRejections) {
            Spacer(Modifier.padding(top = 8.dp))
            Text(
                text = stringResource(ArrUiR.string.arr_common_releases_details_rejections),
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
                    ArrUiR.string.arr_common_releases_cd_grab,
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

/**
 * External-release push: title and download link of a release Sonarr did not
 * find itself. Magnet links ride the magnetUrl field; everything else is
 * posted as downloadUrl.
 */
@Composable
private fun PushReleaseDialog(
    busy: Boolean,
    onConfirm: (ExternalPushInput) -> Unit,
    onDismiss: () -> Unit,
) {
    var title by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var indexer by remember { mutableStateOf("") }
    var protocol by remember { mutableStateOf(DownloadProtocol.TORRENT) }
    val protocolLabels = listOf(
        DownloadProtocol.TORRENT to stringResource(ArrUiR.string.arr_common_quality_delay_torrent),
        DownloadProtocol.USENET to stringResource(ArrUiR.string.arr_common_quality_delay_usenet),
    )
    val canPush = title.isNotBlank() && url.isNotBlank() && !busy
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.arr_sonarr_push_title)) },
        text = {
            Column {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text(stringResource(R.string.arr_sonarr_push_label_title)) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp),
                )
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text(stringResource(R.string.arr_sonarr_push_label_url)) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp),
                )
                OutlinedTextField(
                    value = indexer,
                    onValueChange = { indexer = it },
                    label = { Text(stringResource(R.string.arr_sonarr_push_label_indexer)) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp),
                )
                ProtocolDropdown(
                    selected = protocol,
                    labels = protocolLabels,
                    onSelect = { protocol = it },
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm(
                        ExternalPushInput(
                            title = title,
                            downloadUrl = url,
                            indexer = indexer,
                            protocol = protocol,
                        ),
                    )
                },
                enabled = canPush,
                modifier = Modifier.heightIn(min = 48.dp),
            ) {
                Text(stringResource(R.string.arr_sonarr_push_action))
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !busy,
                modifier = Modifier.heightIn(min = 48.dp),
            ) {
                Text(stringResource(ArrUiR.string.arr_common_cancel))
            }
        },
    )
}

@Composable
private fun ProtocolDropdown(
    selected: DownloadProtocol,
    labels: List<Pair<DownloadProtocol, String>>,
    onSelect: (DownloadProtocol) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val chosenLabel = labels.firstOrNull { it.first == selected }?.second.orEmpty()
    OutlinedButton(
        onClick = { expanded = true },
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .heightIn(min = 48.dp)
            .semantics {
                contentDescription = chosenLabel
            },
    ) {
        Text(
            text = chosenLabel,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
    }
    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
        labels.forEach { (candidate, label) ->
            DropdownMenuItem(
                text = { Text(label) },
                onClick = {
                    expanded = false
                    onSelect(candidate)
                },
            )
        }
    }
}
