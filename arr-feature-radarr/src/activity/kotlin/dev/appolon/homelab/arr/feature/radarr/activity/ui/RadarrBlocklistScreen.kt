package dev.appolon.homelab.arr.feature.radarr.activity.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.appolon.homelab.arr.api.radarr.model.DownloadProtocol
import dev.appolon.homelab.arr.core.ArrError
import dev.appolon.homelab.arr.core.ServerId
import dev.appolon.homelab.arr.feature.radarr.R
import dev.appolon.homelab.arr.feature.radarr.activity.RadarrActivityClients
import dev.appolon.homelab.arr.feature.radarr.activity.data.RadarrBlocklistRepository
import dev.appolon.homelab.arr.feature.radarr.activity.logic.RadarrBlocklistEntryUi
import dev.appolon.homelab.arr.feature.radarr.activity.vm.BlocklistNotice
import dev.appolon.homelab.arr.feature.radarr.activity.vm.RadarrBlocklistContent
import dev.appolon.homelab.arr.feature.radarr.activity.vm.RadarrBlocklistViewModel
import dev.appolon.homelab.arr.ui.lce.ArrLceBox
import dev.appolon.homelab.arr.ui.lce.ArrPullToRefreshBox
import dev.appolon.homelab.arr.ui.lce.LceState
import dev.appolon.homelab.arr.ui.lce.arrErrorMessage
import dev.appolon.homelab.arr.ui.shell.ArrScaffold
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

/**
 * Destination for the blocklist route: wires the repository and ViewModel
 * for this route's server.
 */
@Composable
fun RadarrBlocklistDestination(
    serverId: ServerId,
    clients: RadarrActivityClients,
    refreshSignals: Flow<ServerId> = emptyFlow(),
    serverName: String? = null,
    onBack: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val repository = remember(serverId, clients) {
        RadarrBlocklistRepository(
            serverId = serverId,
            store = clients.store,
            blocklistApi = clients.blocklistApi,
            actionQueue = clients.actionQueue,
        )
    }
    val vm: RadarrBlocklistViewModel = viewModel(key = "radarr-blocklist-" + serverId.value) {
        RadarrBlocklistViewModel(
            serverId = serverId,
            repository = repository,
            refreshSignals = refreshSignals,
        )
    }
    val state by vm.state.collectAsState()
    val lce by vm.lce.collectAsState()

    RadarrBlocklistScreen(
        state = state,
        lce = lce,
        serverName = serverName,
        onBack = onBack,
        onRefresh = vm::refresh,
        onLoadMore = vm::loadMore,
        onRetryLoadMore = vm::loadMore,
        onRemove = vm::removeEntry,
        onClearAll = vm::clearAll,
        onConsumeNotice = vm::consumeNotice,
        onConsumeWriteError = vm::consumeWriteError,
        modifier = modifier,
    )
}

/**
 * The blocklist list: rows with the reason Radarr recorded and the release
 * info, per-entry remove, and clear-all behind a confirmation dialog. Every
 * icon button carries a content description and Material's 48dp minimum
 * touch target.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RadarrBlocklistScreen(
    state: RadarrBlocklistViewModel.UiState,
    lce: LceState<RadarrBlocklistContent>,
    serverName: String?,
    onBack: (() -> Unit)?,
    onRefresh: () -> Unit,
    onLoadMore: () -> Unit,
    onRetryLoadMore: () -> Unit,
    onRemove: (RadarrBlocklistEntryUi) -> Unit,
    onClearAll: () -> Unit,
    onConsumeNotice: () -> Unit,
    onConsumeWriteError: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    var confirmClear by remember { mutableStateOf(false) }
    val content = (lce as? LceState.Content<RadarrBlocklistContent>)?.data
    val refreshing = content != null && (lce as LceState.Content<RadarrBlocklistContent>).refreshing

    NoticeHost(
        notice = state.notice,
        writeError = state.writeError,
        snackbarHostState = snackbarHostState,
        onConsumeNotice = onConsumeNotice,
        onConsumeWriteError = onConsumeWriteError,
    )

    ArrScaffold(
        title = stringResource(R.string.arr_radarr_blocklist_title),
        subtitle = serverName,
        onBack = onBack,
        actions = {
            IconButton(
                onClick = { confirmClear = true },
                enabled = content?.entries?.isNotEmpty() == true,
            ) {
                Icon(
                    imageVector = Icons.Filled.DeleteSweep,
                    contentDescription = stringResource(R.string.arr_radarr_blocklist_cd_clear),
                )
            }
            IconButton(onClick = onRefresh) {
                Icon(
                    imageVector = Icons.Filled.Refresh,
                    contentDescription = stringResource(R.string.arr_radarr_blocklist_cd_refresh),
                )
            }
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
            BlocklistList(
                state = state,
                content = content,
                onRemove = onRemove,
                onLoadMore = onLoadMore,
                onRetryLoadMore = onRetryLoadMore,
                modifier = Modifier.fillMaxSize(),
            )
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text(stringResource(R.string.arr_radarr_blocklist_clear_title)) },
            text = { Text(stringResource(R.string.arr_radarr_blocklist_clear_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        onClearAll()
                        confirmClear = false
                    },
                ) {
                    Text(stringResource(R.string.arr_radarr_blocklist_clear_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) {
                    Text(stringResource(R.string.arr_radarr_blocklist_clear_cancel))
                }
            },
        )
    }
}

@Composable
private fun BlocklistList(
    state: RadarrBlocklistViewModel.UiState,
    content: RadarrBlocklistContent?,
    onRemove: (RadarrBlocklistEntryUi) -> Unit,
    onLoadMore: () -> Unit,
    onRetryLoadMore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    InfiniteScrollEffect(
        listState = listState,
        enabled = content?.canLoadMore == true,
        onLoadMore = onLoadMore,
    )
    LazyColumn(
        state = listState,
        modifier = modifier,
        contentPadding = PaddingValues(bottom = 16.dp),
    ) {
        if (content == null) return@LazyColumn
        if (content.pendingActions > 0) {
            item(key = PendingBannerKey) { PendingSyncBanner(content.pendingActions) }
        }
        items(content.entries, key = { it.id }) { entry ->
            BlocklistEntryRow(
                entry = entry,
                onRemove = { onRemove(entry) },
            )
        }
        when {
            content.loadingMore -> item(key = LoadingMoreKey) { LoadingMoreRow() }
            state.loadMoreFailed -> item(key = LoadMoreFailedKey) {
                TextButton(onClick = onRetryLoadMore) {
                    Text(stringResource(R.string.arr_radarr_blocklist_load_more_retry))
                }
            }
        }
    }
}

@Composable
private fun InfiniteScrollEffect(
    listState: LazyListState,
    enabled: Boolean,
    onLoadMore: () -> Unit,
) {
    val nearEnd by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val lastVisible = info.visibleItemsInfo.lastOrNull()?.index ?: -1
            info.totalItemsCount > 0 && lastVisible >= info.totalItemsCount - LoadMoreTriggerMargin
        }
    }
    LaunchedEffect(nearEnd, enabled) {
        if (nearEnd && enabled) onLoadMore()
    }
}

@Composable
private fun BlocklistEntryRow(
    entry: RadarrBlocklistEntryUi,
    onRemove: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
    ) {
        Icon(
            imageVector = Icons.Filled.Block,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = entry.title + (entry.movieYear?.let { " ($it)" } ?: ""),
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = listOfNotNull(
                    entry.sourceTitle,
                    entry.qualityName,
                    protocolLabel(entry.protocol),
                ).joinToString(" • "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = listOfNotNull(
                    entry.indexer,
                    entry.relativeDate,
                ).joinToString(" • "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            entry.reason?.let { reason ->
                Text(
                    text = reason,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (entry.queuedOffline) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.CloudUpload,
                        contentDescription = stringResource(
                            R.string.arr_radarr_blocklist_cd_queued_indicator,
                        ),
                        tint = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = stringResource(R.string.arr_radarr_blocklist_queued_row),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                }
            }
        }
        IconButton(onClick = onRemove) {
            Icon(
                imageVector = Icons.Filled.Delete,
                contentDescription = stringResource(
                    R.string.arr_radarr_blocklist_cd_remove,
                    entry.title,
                ),
            )
        }
    }
}

@Composable
private fun protocolLabel(protocol: DownloadProtocol): String = when (protocol) {
    DownloadProtocol.UNKNOWN -> ""
    DownloadProtocol.USENET -> stringResource(R.string.arr_radarr_blocklist_protocol_usenet)
    DownloadProtocol.TORRENT -> stringResource(R.string.arr_radarr_blocklist_protocol_torrent)
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
            text = stringResource(R.string.arr_radarr_blocklist_pending_banner, pending),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.tertiary,
        )
    }
}

@Composable
private fun LoadingMoreRow() {
    Row(
        horizontalArrangement = Arrangement.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
    ) {
        CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
    }
}

@Composable
private fun NoticeHost(
    notice: BlocklistNotice?,
    writeError: ArrError?,
    snackbarHostState: SnackbarHostState,
    onConsumeNotice: () -> Unit,
    onConsumeWriteError: () -> Unit,
) {
    val message = when (notice) {
        BlocklistNotice.REMOVED -> stringResource(R.string.arr_radarr_blocklist_notice_removed)
        BlocklistNotice.REMOVE_QUEUED ->
            stringResource(R.string.arr_radarr_blocklist_notice_remove_queued)
        BlocklistNotice.CLEARED -> stringResource(R.string.arr_radarr_blocklist_notice_cleared)
        BlocklistNotice.CLEAR_QUEUED ->
            stringResource(R.string.arr_radarr_blocklist_notice_clear_queued)
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

private const val PendingBannerKey = "__radarr_blocklist_pending"
private const val LoadingMoreKey = "__radarr_blocklist_loading_more"
private const val LoadMoreFailedKey = "__radarr_blocklist_load_more_failed"
private const val LoadMoreTriggerMargin = 6
