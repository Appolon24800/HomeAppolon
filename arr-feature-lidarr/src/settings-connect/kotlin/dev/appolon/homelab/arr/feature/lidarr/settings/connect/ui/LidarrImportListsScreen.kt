package dev.appolon.homelab.arr.feature.lidarr.settings.connect.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.appolon.homelab.arr.api.lidarr.model.ImportListResource
import dev.appolon.homelab.arr.core.ServerId
import dev.appolon.homelab.arr.feature.lidarr.R
import dev.appolon.homelab.arr.feature.lidarr.settings.connect.LidarrConnectClients
import dev.appolon.homelab.arr.feature.lidarr.settings.connect.vm.ImportListsNotice
import dev.appolon.homelab.arr.feature.lidarr.settings.connect.vm.LidarrImportListsContent
import dev.appolon.homelab.arr.feature.lidarr.settings.connect.vm.LidarrImportListsViewModel
import dev.appolon.homelab.arr.ui.components.SectionHeader
import dev.appolon.homelab.arr.ui.lce.ArrLceBox
import dev.appolon.homelab.arr.ui.lce.ArrPullToRefreshBox
import dev.appolon.homelab.arr.ui.lce.LceState
import dev.appolon.homelab.arr.ui.shell.ArrScaffold
import dev.appolon.homelab.arr.api.lidarr.model.TagResource
import dev.appolon.homelab.arr.ui.R as ArrUiR

/** Destination for the import lists route. */
@Composable
fun LidarrImportListsDestination(
    serverId: ServerId,
    clients: LidarrConnectClients,
    serverName: String? = null,
    onBack: (() -> Unit)? = null,
    onOpenList: (Int?) -> Unit,
    onOpenExclusions: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val repository = remember(serverId, clients) { connectRepository(serverId, clients) }
    val vm: LidarrImportListsViewModel =
        viewModel(key = "lidarr-importlists-" + serverId.value) {
            LidarrImportListsViewModel(serverId, repository)
        }
    val state by vm.state.collectAsState()
    val lce by vm.lce.collectAsState()

    LidarrImportListsScreen(
        state = state,
        lce = lce,
        serverName = serverName,
        onBack = onBack,
        onRefresh = vm::refresh,
        onOpen = onOpenList,
        onAdd = { onOpenList(null) },
        onOpenExclusions = onOpenExclusions,
        onTestAll = vm::testAll,
        onSync = vm::sync,
        onOpenPreview = vm::openPreview,
        onClosePreview = vm::closePreview,
        onDeleteRequest = vm::requestDelete,
        onDeleteCancelled = vm::deleteCancelled,
        onDeleteConfirmed = vm::deleteConfirmed,
        onConsumeNotice = vm::consumeNotice,
        onConsumeWriteError = vm::consumeWriteError,
        modifier = modifier,
    )
}

/**
 * Import lists list. Rows show the automatic-add and search badges, the
 * quality and metadata profiles a synced artist inherits, and tag chips;
 * the app bar offers test-all, sync (the server-side ImportListSync
 * command), the sync preview sheet, exclusions, and refresh. Lidarr has
 * no list options card: the per-list add options live on each list's
 * editor.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LidarrImportListsScreen(
    state: LidarrImportListsViewModel.UiState,
    lce: LceState<LidarrImportListsContent>,
    serverName: String?,
    onBack: (() -> Unit)?,
    onRefresh: () -> Unit,
    onOpen: (Int) -> Unit,
    onAdd: () -> Unit,
    onOpenExclusions: () -> Unit,
    onTestAll: () -> Unit,
    onSync: () -> Unit,
    onOpenPreview: () -> Unit,
    onClosePreview: () -> Unit,
    onDeleteRequest: (ImportListResource) -> Unit,
    onDeleteCancelled: () -> Unit,
    onDeleteConfirmed: () -> Unit,
    onConsumeNotice: () -> Unit,
    onConsumeWriteError: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val content = (lce as? LceState.Content<LidarrImportListsContent>)?.data
    val refreshing = content != null && (lce as LceState.Content<LidarrImportListsContent>).refreshing

    ConnectNoticeHost(
        noticeMessage = when (state.notice) {
            ImportListsNotice.DELETED ->
                stringResource(ArrUiR.string.arr_common_connect_notice_deleted)

            ImportListsNotice.DELETED_QUEUED ->
                stringResource(ArrUiR.string.arr_common_backup_notice_deleted_queued)

            ImportListsNotice.TEST_ALL_DONE ->
                stringResource(ArrUiR.string.arr_common_connect_notice_test_all_done)

            ImportListsNotice.SYNC_STARTED ->
                stringResource(ArrUiR.string.arr_common_lists_notice_sync_started)

            ImportListsNotice.SYNC_FAILED ->
                stringResource(ArrUiR.string.arr_common_lists_notice_sync_failed)

            null -> null
        },
        writeError = state.writeError,
        snackbarHostState = snackbarHostState,
        onConsumeNotice = onConsumeNotice,
        onConsumeWriteError = onConsumeWriteError,
    )

    ArrScaffold(
        title = stringResource(ArrUiR.string.arr_common_lists_title),
        subtitle = serverName,
        onBack = onBack,
        actions = {
            IconButton(onClick = onOpenExclusions) {
                Icon(
                    imageVector = Icons.Filled.Block,
                    contentDescription = stringResource(ArrUiR.string.arr_common_lists_cd_exclusions),
                )
            }
            IconButton(onClick = onOpenPreview) {
                Icon(
                    imageVector = Icons.Filled.Visibility,
                    contentDescription = stringResource(R.string.arr_lidarr_lists_cd_preview),
                )
            }
            IconButton(onClick = onSync, enabled = !state.syncing) {
                Icon(
                    imageVector = Icons.Filled.Sync,
                    contentDescription = stringResource(ArrUiR.string.arr_common_lists_cd_sync),
                )
            }
            IconButton(onClick = onTestAll, enabled = !state.testingAll && content != null) {
                Icon(
                    imageVector = Icons.Filled.PlayArrow,
                    contentDescription = stringResource(ArrUiR.string.arr_common_lists_cd_test_all),
                )
            }
            IconButton(onClick = onRefresh) {
                Icon(
                    imageVector = Icons.Filled.Refresh,
                    contentDescription = stringResource(ArrUiR.string.arr_common_lists_cd_refresh),
                )
            }
        },
        modifier = modifier,
    ) { padding ->
        Box(Modifier.fillMaxSize()) {
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
                    emptyTitle = stringResource(ArrUiR.string.arr_common_lists_empty_title),
                    emptyMessage = stringResource(R.string.arr_lidarr_lists_empty_message),
                    modifier = Modifier.fillMaxSize(),
                ) { data ->
                    LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.widthIn(max = ListMaxWidth + 32.dp),
                    ) {
                        if (data.pendingActions > 0) {
                            item { ConnectPendingBanner(data.pendingActions) }
                        }
                        item {
                            SectionHeader(title = stringResource(ArrUiR.string.arr_common_lists_title))
                        }
                        items(data.lists, key = { it.id }, contentType = { "list" }) { list ->
                            ImportListRow(
                                list = list,
                                tags = data.tags,
                                queued = list.id in data.queuedRemovedIds,
                                onOpen = { onOpen(list.id) },
                                onDelete = { onDeleteRequest(list) },
                            )
                        }
                    }
                }
            }
            ExtendedFloatingActionButton(
                onClick = onAdd,
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text(stringResource(ArrUiR.string.arr_common_lists_add)) },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp),
            )
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }

    state.confirmDeleteFor?.let { target ->
        AlertDialog(
            onDismissRequest = onDeleteCancelled,
            title = { Text(stringResource(ArrUiR.string.arr_common_delete)) },
            text = {
                Text(
                    stringResource(
                        ArrUiR.string.arr_common_connect_delete_message,
                        target.name ?: target.id.toString(),
                    ),
                )
            },
            confirmButton = {
                TextButton(onClick = onDeleteConfirmed) {
                    Text(stringResource(ArrUiR.string.arr_common_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = onDeleteCancelled) {
                    Text(stringResource(ArrUiR.string.arr_common_cancel))
                }
            },
        )
    }

    if (state.previewOpen) {
        SyncPreviewSheet(
            preview = content?.preview,
            onSync = onSync,
            onDismiss = onClosePreview,
        )
    }
}

@Composable
private fun ImportListRow(
    list: ImportListResource,
    tags: List<TagResource>,
    queued: Boolean,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
) {
    val openCd = stringResource(ArrUiR.string.arr_common_lists_cd_open, list.name.orEmpty())
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClickLabel = openCd, onClick = onOpen),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 72.dp)
                .padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 8.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(list.name.orEmpty(), style = MaterialTheme.typography.bodyLarge)
                Text(
                    text = listOfNotNull(
                        list.implementationName,
                        list.listType?.name?.lowercase(),
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = listOfNotNull(
                        if (list.enableAutomaticAdd) {
                            stringResource(R.string.arr_lidarr_lists_auto)
                        } else {
                            null
                        },
                        stringResource(
                            R.string.arr_lidarr_lists_quality_profile,
                            list.qualityProfileId,
                        ),
                        stringResource(
                            R.string.arr_lidarr_lists_metadata_profile,
                            list.metadataProfileId,
                        ),
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (queued) ConnectQueuedBadge()
                    ConnectTagChips(list.tags.orEmpty().toList(), tags)
                }
            }
            val deleteCd = stringResource(ArrUiR.string.arr_common_lists_cd_delete, list.name.orEmpty())
            IconButton(onClick = onDelete, modifier = Modifier.semantics { contentDescription = deleteCd }) {
                Icon(
                    imageVector = Icons.Filled.Delete,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

/**
 * The sync preview sheet. Lidarr's API has no discover endpoint, so the
 * preview shows what each configured list will do on the next sync: the
 * add options every discovered artist inherits and the exclusion count.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SyncPreviewSheet(
    preview: dev.appolon.homelab.arr.feature.lidarr.settings.connect.vm.ImportListSyncPreview?,
    onSync: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                stringResource(ArrUiR.string.arr_common_lists_preview_title),
                style = MaterialTheme.typography.titleMedium,
            )
            if (preview == null) {
                Text(stringResource(R.string.arr_lidarr_lists_preview_empty))
            } else {
                Text(
                    stringResource(R.string.arr_lidarr_lists_preview_summary, preview.rows.size),
                    style = MaterialTheme.typography.bodyMedium,
                )
                preview.rows.take(PREVIEW_ROW_LIMIT).forEach { row ->
                    Column(Modifier.heightIn(min = 48.dp)) {
                        Text(row.name, style = MaterialTheme.typography.bodyLarge)
                        Text(
                            text = listOfNotNull(
                                if (row.automaticAdd) {
                                    stringResource(R.string.arr_lidarr_lists_preview_auto_add)
                                } else {
                                    stringResource(R.string.arr_lidarr_lists_preview_manual_add)
                                },
                                row.monitor?.let { monitor ->
                                    stringResource(R.string.arr_lidarr_lists_preview_monitor, monitorLabel(monitor))
                                },
                                stringResource(
                                    R.string.arr_lidarr_lists_preview_quality_profile,
                                    row.qualityProfileId,
                                ),
                                stringResource(
                                    R.string.arr_lidarr_lists_preview_metadata_profile,
                                    row.metadataProfileId,
                                ),
                                row.rootFolderPath,
                                if (row.searchForNew) {
                                    stringResource(ArrUiR.string.arr_common_lists_search_on_add)
                                } else {
                                    null
                                },
                            ).joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (preview.rows.size > PREVIEW_ROW_LIMIT) {
                    Text(
                        stringResource(
                            ArrUiR.string.arr_common_lists_preview_more,
                            preview.rows.size - PREVIEW_ROW_LIMIT,
                        ),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                if (preview.exclusionCount > 0) {
                    Text(
                        stringResource(
                            R.string.arr_lidarr_lists_preview_exclusions,
                            preview.exclusionCount,
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextButton(onClick = onSync) {
                    Text(stringResource(R.string.arr_lidarr_lists_preview_sync_now))
                }
            }
        }
    }
}

private const val PREVIEW_ROW_LIMIT = 25
