package dev.appolon.homelab.arr.feature.lidarr.queue.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.runtime.collectAsState
import dev.appolon.homelab.arr.core.AppType
import dev.appolon.homelab.arr.core.ArrError
import dev.appolon.homelab.arr.core.Humanize
import dev.appolon.homelab.arr.core.ServerId
import dev.appolon.homelab.arr.feature.lidarr.R
import dev.appolon.homelab.arr.feature.lidarr.queue.LidarrQueueClients
import dev.appolon.homelab.arr.feature.lidarr.queue.LidarrQueueFeature
import dev.appolon.homelab.arr.feature.lidarr.queue.data.QueueRemoveOptions
import dev.appolon.homelab.arr.feature.lidarr.queue.data.LidarrQueueRepository
import dev.appolon.homelab.arr.feature.lidarr.queue.logic.LidarrQueueFilter
import dev.appolon.homelab.arr.feature.lidarr.queue.logic.LidarrQueueItemUi
import dev.appolon.homelab.arr.feature.lidarr.queue.logic.LidarrQueueOverview
import dev.appolon.homelab.arr.feature.lidarr.queue.vm.QueueNotice
import dev.appolon.homelab.arr.feature.lidarr.queue.vm.LidarrQueueContent
import dev.appolon.homelab.arr.feature.lidarr.queue.vm.LidarrQueueViewModel
import dev.appolon.homelab.arr.ui.components.EmptyState
import dev.appolon.homelab.arr.ui.components.FilterChipRow
import dev.appolon.homelab.arr.ui.components.QueueRow
import dev.appolon.homelab.arr.ui.components.formatEta
import dev.appolon.homelab.arr.ui.lce.ArrLceBox
import dev.appolon.homelab.arr.ui.lce.ArrPullToRefreshBox
import dev.appolon.homelab.arr.ui.lce.LceState
import dev.appolon.homelab.arr.ui.lce.arrErrorMessage
import dev.appolon.homelab.arr.ui.nav.ArrRoute
import dev.appolon.homelab.arr.ui.shell.ArrScaffold
import dev.appolon.homelab.arr.ui.shell.LocalArrShellNavigator
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

/**
 * Built-in queue slot destination: wires the ViewModel for this route's
 * server and reports aggregate progress through [onOverallProgress] for the
 * shell's server switcher badge.
 */
@Composable
fun LidarrQueueDestination(
    route: ArrRoute.Queue,
    clients: (ServerId) -> LidarrQueueClients,
    refreshSignals: Flow<ServerId> = emptyFlow(),
    serverNames: (ServerId) -> String? = { null },
    onOverallProgress: (ServerId, Float?) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier,
) {
    if (route.app != AppType.LIDARR) {
        ForeignAppPlaceholder(route.app.wireName, modifier)
        return
    }
    val serverClients = clients(route.serverId)
    val repository = remember(route.serverId, serverClients) {
        LidarrQueueRepository(
            serverId = route.serverId,
            store = serverClients.store,
            queueDetailsApi = serverClients.queueDetailsApi,
            queueApi = serverClients.queueApi,
            queueActionApi = serverClients.queueActionApi,
            queueStatusApi = serverClients.queueStatusApi,
            commandRunner = serverClients.commandRunner,
            actionQueue = serverClients.actionQueue,
        )
    }
    val vm: LidarrQueueViewModel = viewModel(key = "lidarr-queue-" + route.serverId.value) {
        LidarrQueueViewModel(
            serverId = route.serverId,
            repository = repository,
            refreshSignals = refreshSignals,
        )
    }
    val state by vm.state.collectAsState()
    val lce by vm.lce.collectAsState()
    val overall by vm.overallProgress.collectAsState()
    val navigator = LocalArrShellNavigator.current

    LaunchedEffect(route.serverId, overall) {
        onOverallProgress(route.serverId, overall)
    }

    LidarrQueueScreen(
        state = state,
        lce = lce,
        serverName = serverNames(route.serverId),
        onRefresh = vm::refresh,
        onFilter = vm::setFilter,
        onRemove = vm::removeItem,
        onRetryImport = vm::retryImport,
        onGrab = vm::grab,
        onOpenManualImport = { downloadId ->
            LidarrQueueFeature.openManualImport(navigator, route.serverId, downloadId)
        },
        onConsumeNotice = vm::consumeNotice,
        onConsumeWriteError = vm::consumeWriteError,
        modifier = modifier,
    )
}

@Composable
private fun ForeignAppPlaceholder(app: String, modifier: Modifier = Modifier) {
    EmptyState(
        title = stringResource(dev.appolon.homelab.arr.ui.R.string.arr_shell_unavailable_title),
        message = stringResource(dev.appolon.homelab.arr.ui.R.string.arr_shell_unavailable_message, app),
        modifier = modifier,
    )
}

/**
 * The queue list: filter chips, pending-sync banner, artist/album rows with
 * per-item actions, a remove dialog with blocklist and re-search options,
 * and a details dialog for status messages. Every icon button carries a
 * content description and Material's 48dp minimum touch target.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LidarrQueueScreen(
    state: LidarrQueueViewModel.UiState,
    lce: LceState<LidarrQueueContent>,
    serverName: String?,
    onRefresh: () -> Unit,
    onFilter: (LidarrQueueFilter) -> Unit,
    onRemove: (LidarrQueueItemUi, QueueRemoveOptions) -> Unit,
    onRetryImport: (LidarrQueueItemUi) -> Unit,
    onGrab: (LidarrQueueItemUi) -> Unit,
    onOpenManualImport: (downloadId: String?) -> Unit,
    onConsumeNotice: () -> Unit,
    onConsumeWriteError: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    var removeTargetId by rememberSaveable { mutableStateOf<Int?>(null) }
    var detailsTargetId by rememberSaveable { mutableStateOf<Int?>(null) }
    val content = (lce as? LceState.Content<LidarrQueueContent>)?.data
    val refreshing = content != null && (lce as LceState.Content<LidarrQueueContent>).refreshing
    val labels = filterLabels()

    NoticeHost(
        notice = state.notice,
        writeError = state.writeError,
        snackbarHostState = snackbarHostState,
        onConsumeNotice = onConsumeNotice,
        onConsumeWriteError = onConsumeWriteError,
    )

    ArrScaffold(
        title = stringResource(R.string.arr_lidarr_queue_title),
        subtitle = serverName,
        actions = {
            IconButton(onClick = { onOpenManualImport(null) }) {
                Icon(
                    imageVector = Icons.Filled.FolderOpen,
                    contentDescription = stringResource(R.string.arr_lidarr_queue_cd_manual_import),
                )
            }
            IconButton(onClick = onRefresh) {
                Icon(
                    imageVector = Icons.Filled.Refresh,
                    contentDescription = stringResource(R.string.arr_lidarr_queue_cd_refresh),
                )
            }
        },
        filterBar = {
            FilterChipRow(
                filters = LidarrQueueFilter.entries,
                selected = { it == state.filter },
                label = { labels.getValue(it) },
                onSelect = onFilter,
                modifier = Modifier.padding(bottom = 4.dp),
            )
        },
        modifier = modifier,
    ) { padding ->
        ArrPullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = onRefresh,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            ArrLceBox(
                state = lce,
                onRetry = onRefresh,
                emptyTitle = stringResource(R.string.arr_lidarr_queue_empty_title),
                emptyMessage = stringResource(R.string.arr_lidarr_queue_empty_message),
                modifier = Modifier.fillMaxSize(),
            ) { shown ->
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    if (shown.pendingActions > 0) {
                        item(key = PendingBannerKey) { PendingSyncBanner(shown.pendingActions) }
                    }
                    shown.overview?.let { overview ->
                        item(key = OverviewKey) { OverviewRow(overview) }
                    }
                    items(shown.items, key = { it.id }) { item ->
                        QueueListItem(
                            item = item,
                            onRemove = { removeTargetId = item.id },
                            onRetryImport = { onRetryImport(item) },
                            onGrab = { onGrab(item) },
                            onShowDetails = { detailsTargetId = item.id },
                        )
                    }
                }
            }
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }

    content?.items?.find { it.id == removeTargetId }?.let { target ->
        RemoveQueueDialog(
            target = target,
            onConfirm = { options ->
                onRemove(target, options)
                removeTargetId = null
            },
            onDismiss = { removeTargetId = null },
        )
    }

    content?.items?.find { it.id == detailsTargetId }?.let { target ->
        QueueItemDetailsDialog(
            item = target,
            onRetryImport = {
                onRetryImport(target)
                detailsTargetId = null
            },
            onManualImport = {
                onOpenManualImport(target.downloadId)
                detailsTargetId = null
            },
            onDismiss = { detailsTargetId = null },
        )
    }
}

@Composable
private fun QueueListItem(
    item: LidarrQueueItemUi,
    onRemove: () -> Unit,
    onRetryImport: () -> Unit,
    onGrab: () -> Unit,
    onShowDetails: () -> Unit,
) {
    QueueRow(
        title = item.title,
        status = item.status,
        subtitle = item.subtitleText(),
        progress = item.progress,
        speedText = item.speedText(),
        onClick = onShowDetails,
        actions = {
            if (item.queuedOffline) {
                Icon(
                    imageVector = Icons.Filled.CloudUpload,
                    contentDescription = stringResource(R.string.arr_lidarr_queue_cd_queued_indicator),
                    tint = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
            if (item.canRetryImport) {
                IconButton(onClick = onRetryImport) {
                    Icon(
                        imageVector = Icons.Filled.Replay,
                        contentDescription = stringResource(
                            R.string.arr_lidarr_queue_cd_retry_import,
                            item.title,
                        ),
                    )
                }
            }
            if (item.canGrab) {
                IconButton(onClick = onGrab) {
                    Icon(
                        imageVector = Icons.Filled.PlayArrow,
                        contentDescription = stringResource(
                            R.string.arr_lidarr_queue_cd_grab,
                            item.title,
                        ),
                    )
                }
            }
            IconButton(onClick = onRemove) {
                Icon(
                    imageVector = Icons.Filled.Delete,
                    contentDescription = stringResource(R.string.arr_lidarr_queue_cd_remove, item.title),
                )
            }
        },
    )
}

@Composable
private fun PendingSyncBanner(pending: Int) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Icon(
            imageVector = Icons.Filled.CloudUpload,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.tertiary,
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = stringResource(R.string.arr_lidarr_queue_pending_banner, pending),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.tertiary,
        )
    }
}

@Composable
private fun OverviewRow(overview: LidarrQueueOverview) {
    var text = stringResource(
        R.string.arr_lidarr_queue_overview_counts,
        overview.downloadingCount,
        overview.importingCount,
        overview.failedCount,
    )
    if (overview.unknownCount > 0) {
        text += stringResource(R.string.arr_lidarr_queue_overview_unknown, overview.unknownCount)
    }
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
    )
}

@Composable
private fun RemoveQueueDialog(
    target: LidarrQueueItemUi,
    onConfirm: (QueueRemoveOptions) -> Unit,
    onDismiss: () -> Unit,
) {
    var removeFromClient by rememberSaveable { mutableStateOf(true) }
    var blocklist by rememberSaveable { mutableStateOf(false) }
    var searchReplacement by rememberSaveable { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.arr_lidarr_queue_remove_title)) },
        text = {
            Column {
                Text(stringResource(R.string.arr_lidarr_queue_remove_message, target.title))
                SwitchRow(
                    label = stringResource(R.string.arr_lidarr_queue_remove_from_client),
                    checked = removeFromClient,
                    onCheckedChange = { removeFromClient = it },
                )
                SwitchRow(
                    label = stringResource(R.string.arr_lidarr_queue_remove_blocklist),
                    checked = blocklist,
                    onCheckedChange = { blocklist = it },
                )
                SwitchRow(
                    label = stringResource(R.string.arr_lidarr_queue_remove_search),
                    checked = searchReplacement,
                    onCheckedChange = { searchReplacement = it },
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm(
                        QueueRemoveOptions(
                            removeFromClient = removeFromClient,
                            blocklist = blocklist,
                            searchReplacement = searchReplacement,
                        ),
                    )
                },
            ) {
                Text(stringResource(R.string.arr_lidarr_queue_action_remove))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.arr_lidarr_queue_action_cancel))
            }
        },
    )
}

@Composable
private fun QueueItemDetailsDialog(
    item: LidarrQueueItemUi,
    onRetryImport: () -> Unit,
    onManualImport: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.arr_lidarr_queue_details_title)) },
        text = {
            Column {
                Text(item.title, style = MaterialTheme.typography.titleSmall)
                item.artistTitle?.let { artist ->
                    DetailLine(
                        label = stringResource(R.string.arr_lidarr_queue_details_artist),
                        value = artist,
                    )
                }
                item.albumTitle?.let { album ->
                    DetailLine(
                        label = stringResource(R.string.arr_lidarr_queue_details_album),
                        value = album,
                    )
                }
                if (item.trackTotal != null) {
                    DetailLine(
                        label = stringResource(R.string.arr_lidarr_queue_details_tracks),
                        value = stringResource(
                            R.string.arr_lidarr_queue_details_track_count,
                            item.trackImported ?: 0,
                            item.trackTotal,
                        ),
                    )
                }
                item.qualityName?.let { quality ->
                    DetailLine(label = stringResource(R.string.arr_lidarr_queue_details_quality), value = quality)
                }
                item.downloadClient?.let { client ->
                    DetailLine(
                        label = stringResource(R.string.arr_lidarr_queue_details_download_client),
                        value = client,
                    )
                }
                item.indexer?.let { indexer ->
                    DetailLine(label = stringResource(R.string.arr_lidarr_queue_details_indexer), value = indexer)
                }
                item.totalBytes?.let { total ->
                    DetailLine(
                        label = stringResource(R.string.arr_lidarr_queue_details_size),
                        value = Humanize.bytes(total),
                    )
                }
                item.remainingBytes?.let { remaining ->
                    DetailLine(
                        label = stringResource(R.string.arr_lidarr_queue_details_remaining),
                        value = Humanize.bytes(remaining),
                    )
                }
                item.etaSeconds?.let { eta ->
                    DetailLine(
                        label = stringResource(R.string.arr_lidarr_queue_details_eta),
                        value = formatEta(eta),
                    )
                }
                item.outputPath?.let { path ->
                    DetailLine(label = stringResource(R.string.arr_lidarr_queue_details_output), value = path)
                }
                item.errorMessage?.let { error ->
                    Text(
                        text = stringResource(R.string.arr_lidarr_queue_details_error),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    Text(error, style = MaterialTheme.typography.bodySmall)
                }
                if (item.statusMessages.isNotEmpty()) {
                    Text(
                        text = stringResource(R.string.arr_lidarr_queue_details_status_messages),
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    item.statusMessages.forEach { message ->
                        Text(
                            text = listOfNotNull(message.title, message.messages?.joinToString(" • "))
                                .joinToString(": "),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        },
        confirmButton = {
            if (item.canRetryImport) {
                TextButton(onClick = onRetryImport) {
                    Text(stringResource(R.string.arr_lidarr_queue_details_action_retry))
                }
            } else {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.arr_lidarr_queue_action_cancel))
                }
            }
        },
        dismissButton = {
            if (item.canManualImport) {
                TextButton(onClick = onManualImport) {
                    Text(stringResource(R.string.arr_lidarr_queue_details_action_manual_import))
                }
            }
        },
    )
}

@Composable
private fun DetailLine(label: String, value: String) {
    Text(
        text = stringResource(R.string.arr_lidarr_queue_details_line, label, value),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun SwitchRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun NoticeHost(
    notice: QueueNotice?,
    writeError: ArrError?,
    snackbarHostState: SnackbarHostState,
    onConsumeNotice: () -> Unit,
    onConsumeWriteError: () -> Unit,
) {
    val message = when (notice) {
        QueueNotice.REMOVED -> stringResource(R.string.arr_lidarr_queue_notice_removed)
        QueueNotice.REMOVE_QUEUED -> stringResource(R.string.arr_lidarr_queue_notice_remove_queued)
        QueueNotice.RETRY_SENT -> stringResource(R.string.arr_lidarr_queue_notice_retry_sent)
        QueueNotice.RETRY_QUEUED -> stringResource(R.string.arr_lidarr_queue_notice_retry_queued)
        QueueNotice.GRABBED -> stringResource(R.string.arr_lidarr_queue_notice_grabbed)
        QueueNotice.GRAB_QUEUED -> stringResource(R.string.arr_lidarr_queue_notice_grab_queued)
        null -> null
    }
    LaunchedEffect(message) {
        if (message != null) {
            snackbarHostState.showSnackbar(message)
            onConsumeNotice()
        }
    }
    val errorText = writeError?.let { arrErrorMessage(it) }
    LaunchedEffect(errorText) {
        if (errorText != null) {
            snackbarHostState.showSnackbar(errorText)
            onConsumeWriteError()
        }
    }
}

@Composable
private fun filterLabels(): Map<LidarrQueueFilter, String> = mapOf(
    LidarrQueueFilter.ALL to stringResource(R.string.arr_lidarr_queue_filter_all),
    LidarrQueueFilter.DOWNLOADING to stringResource(R.string.arr_lidarr_queue_filter_downloading),
    LidarrQueueFilter.IMPORTING to stringResource(R.string.arr_lidarr_queue_filter_importing),
    LidarrQueueFilter.FAILED to stringResource(R.string.arr_lidarr_queue_filter_failed),
    LidarrQueueFilter.UNKNOWN to stringResource(R.string.arr_lidarr_queue_filter_unknown),
)

/**
 * Row subtitle: the album line first (title plus imported-over-total track
 * counts), then quality, the client or indexer, and the custom format score.
 */
@Composable
private fun LidarrQueueItemUi.subtitleText(): String? = listOfNotNull(
    listOfNotNull(
        albumTitle,
        trackTotal?.let {
            stringResource(R.string.arr_lidarr_queue_track_count, trackImported ?: 0, it)
        },
    ).joinToString(" · ").takeIf { it.isNotEmpty() },
    qualityName,
    downloadClient ?: indexer,
    customFormatScore.takeIf { it != 0 }?.let { score -> "+$score" },
).joinToString(" • ").takeIf { it.isNotEmpty() }

@Composable
private fun LidarrQueueItemUi.speedText(): String? {
    val remaining = remainingBytes?.let { Humanize.bytes(it) }
    val eta = etaSeconds?.let { formatEta(it) }
    return when {
        remaining != null && eta != null -> stringResource(
            R.string.arr_lidarr_queue_progress_eta,
            remaining,
            eta,
        )

        remaining != null && totalBytes != null -> stringResource(
            R.string.arr_lidarr_queue_progress_total,
            remaining,
            Humanize.bytes(totalBytes),
        )

        eta != null -> stringResource(R.string.arr_lidarr_queue_eta_left, eta)
        else -> null
    }
}

private const val PendingBannerKey = "__lidarr_queue_pending"
private const val OverviewKey = "__lidarr_queue_overview"
