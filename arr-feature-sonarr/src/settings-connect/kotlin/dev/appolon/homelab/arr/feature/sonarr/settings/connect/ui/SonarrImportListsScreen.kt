package dev.appolon.homelab.arr.feature.sonarr.settings.connect.ui

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
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.appolon.homelab.arr.api.sonarr.model.ImportListConfigResource
import dev.appolon.homelab.arr.api.sonarr.model.ImportListResource
import dev.appolon.homelab.arr.api.sonarr.model.ListSyncLevelType
import dev.appolon.homelab.arr.core.ServerId
import dev.appolon.homelab.arr.feature.sonarr.R
import dev.appolon.homelab.arr.feature.sonarr.settings.connect.SonarrConnectClients
import dev.appolon.homelab.arr.feature.sonarr.settings.connect.vm.ImportListsNotice
import dev.appolon.homelab.arr.feature.sonarr.settings.connect.vm.SonarrImportListsContent
import dev.appolon.homelab.arr.feature.sonarr.settings.connect.vm.SonarrImportListsViewModel
import dev.appolon.homelab.arr.ui.components.SectionHeader
import dev.appolon.homelab.arr.ui.lce.ArrLceBox
import dev.appolon.homelab.arr.ui.lce.ArrPullToRefreshBox
import dev.appolon.homelab.arr.ui.lce.LceState
import dev.appolon.homelab.arr.ui.shell.ArrScaffold

/** Destination for the import lists route. */
@Composable
fun SonarrImportListsDestination(
    serverId: ServerId,
    clients: SonarrConnectClients,
    serverName: String? = null,
    onBack: (() -> Unit)? = null,
    onOpenList: (Int?) -> Unit,
    onOpenExclusions: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val repository = remember(serverId, clients) { connectRepository(serverId, clients) }
    val vm: SonarrImportListsViewModel =
        viewModel(key = "sonarr-importlists-" + serverId.value) {
            SonarrImportListsViewModel(serverId, repository)
        }
    val state by vm.state.collectAsState()
    val lce by vm.lce.collectAsState()

    SonarrImportListsScreen(
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
        onSaveOptions = vm::updateOptions,
        onConsumeNotice = vm::consumeNotice,
        onConsumeWriteError = vm::consumeWriteError,
        modifier = modifier,
    )
}

/**
 * Import lists list. Rows show the automatic-add and search badges, the
 * add options a synced series inherits, and tag chips; the app bar offers
 * test-all, sync (the server-side ImportListSync command), the sync
 * preview sheet, exclusions, and the list options card (sync level).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SonarrImportListsScreen(
    state: SonarrImportListsViewModel.UiState,
    lce: LceState<SonarrImportListsContent>,
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
    onSaveOptions: (ImportListConfigResource) -> Unit,
    onConsumeNotice: () -> Unit,
    onConsumeWriteError: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    var showOptions by remember { mutableStateOf(false) }
    val content = (lce as? LceState.Content<SonarrImportListsContent>)?.data
    val refreshing = content != null && (lce as LceState.Content<SonarrImportListsContent>).refreshing

    ConnectNoticeHost(
        noticeMessage = when (state.notice) {
            ImportListsNotice.DELETED ->
                stringResource(R.string.arr_sonarr_connect_notice_deleted)

            ImportListsNotice.DELETED_QUEUED ->
                stringResource(R.string.arr_sonarr_connect_notice_deleted_queued)

            ImportListsNotice.TEST_ALL_DONE ->
                stringResource(R.string.arr_sonarr_connect_notice_test_all_done)

            ImportListsNotice.SYNC_STARTED ->
                stringResource(R.string.arr_sonarr_lists_notice_sync_started)

            ImportListsNotice.SYNC_FAILED ->
                stringResource(R.string.arr_sonarr_lists_notice_sync_failed)

            ImportListsNotice.OPTIONS_SAVED ->
                stringResource(R.string.arr_sonarr_connect_notice_saved)

            ImportListsNotice.OPTIONS_SAVED_QUEUED ->
                stringResource(R.string.arr_sonarr_connect_notice_saved_queued)

            null -> null
        },
        writeError = state.writeError,
        snackbarHostState = snackbarHostState,
        onConsumeNotice = onConsumeNotice,
        onConsumeWriteError = onConsumeWriteError,
    )

    ArrScaffold(
        title = stringResource(R.string.arr_sonarr_lists_title),
        subtitle = serverName,
        onBack = onBack,
        actions = {
            IconButton(onClick = onOpenExclusions) {
                Icon(
                    imageVector = Icons.Filled.Block,
                    contentDescription = stringResource(R.string.arr_sonarr_lists_cd_exclusions),
                )
            }
            IconButton(onClick = onOpenPreview) {
                Icon(
                    imageVector = Icons.Filled.Visibility,
                    contentDescription = stringResource(R.string.arr_sonarr_lists_cd_preview),
                )
            }
            IconButton(onClick = onSync, enabled = !state.syncing) {
                Icon(
                    imageVector = Icons.Filled.Sync,
                    contentDescription = stringResource(R.string.arr_sonarr_lists_cd_sync),
                )
            }
            IconButton(onClick = onTestAll, enabled = !state.testingAll && content != null) {
                Icon(
                    imageVector = Icons.Filled.PlayArrow,
                    contentDescription = stringResource(R.string.arr_sonarr_lists_cd_test_all),
                )
            }
            IconButton(onClick = { showOptions = true }) {
                Icon(
                    imageVector = Icons.Filled.Tune,
                    contentDescription = stringResource(R.string.arr_sonarr_lists_cd_options),
                )
            }
            IconButton(onClick = onRefresh) {
                Icon(
                    imageVector = Icons.Filled.Refresh,
                    contentDescription = stringResource(R.string.arr_sonarr_lists_cd_refresh),
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
                    emptyTitle = stringResource(R.string.arr_sonarr_lists_empty_title),
                    emptyMessage = stringResource(R.string.arr_sonarr_lists_empty_message),
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
                            SectionHeader(title = stringResource(R.string.arr_sonarr_lists_section_lists))
                        }
                        items(data.lists, key = { it.id }) { list ->
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
                text = { Text(stringResource(R.string.arr_sonarr_lists_add)) },
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
            title = { Text(stringResource(R.string.arr_sonarr_connect_delete_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.arr_sonarr_connect_delete_message,
                        target.name ?: target.id.toString(),
                    ),
                )
            },
            confirmButton = {
                TextButton(onClick = onDeleteConfirmed) {
                    Text(stringResource(R.string.arr_sonarr_connect_delete_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = onDeleteCancelled) {
                    Text(stringResource(R.string.arr_sonarr_connect_delete_cancel))
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

    if (showOptions) {
        ImportListOptionsDialog(
            initial = state.options ?: ImportListConfigResource(),
            busy = state.busy,
            onSave = {
                onSaveOptions(it)
                showOptions = false
            },
            onDismiss = { showOptions = false },
        )
    }
}

@Composable
private fun ImportListRow(
    list: ImportListResource,
    tags: List<dev.appolon.homelab.arr.api.sonarr.model.TagResource>,
    queued: Boolean,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
) {
    val openCd = stringResource(R.string.arr_sonarr_lists_cd_open, list.name.orEmpty())
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
                        list.listType.name.lowercase(),
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = listOfNotNull(
                        if (list.enableAutomaticAdd) {
                            stringResource(R.string.arr_sonarr_lists_auto)
                        } else {
                            null
                        },
                        if (list.searchForMissingEpisodes) {
                            stringResource(R.string.arr_sonarr_lists_search_on_add)
                        } else {
                            null
                        },
                        list.rootFolderPath,
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (queued) ConnectQueuedBadge()
                    ConnectTagChips(list.tags.orEmpty(), tags)
                }
            }
            val deleteCd = stringResource(R.string.arr_sonarr_lists_cd_delete, list.name.orEmpty())
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
 * The sync preview sheet. Sonarr's API has no discover endpoint, so the
 * preview shows what each configured list will do on the next sync: the
 * add options every discovered series inherits, the exclusion count, and
 * the sync level that governs series leaving the lists.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SyncPreviewSheet(
    preview: dev.appolon.homelab.arr.feature.sonarr.settings.connect.vm.ImportListSyncPreview?,
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
                stringResource(R.string.arr_sonarr_lists_preview_title),
                style = MaterialTheme.typography.titleMedium,
            )
            if (preview == null) {
                Text(stringResource(R.string.arr_sonarr_lists_preview_empty))
            } else {
                Text(
                    stringResource(R.string.arr_sonarr_lists_preview_summary, preview.rows.size),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    stringResource(
                        R.string.arr_sonarr_lists_preview_sync_level,
                        syncLevelLabel(preview.syncLevel),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                preview.rows.take(PREVIEW_ROW_LIMIT).forEach { row ->
                    Column(Modifier.heightIn(min = 48.dp)) {
                        Text(row.name, style = MaterialTheme.typography.bodyLarge)
                        Text(
                            text = listOfNotNull(
                                if (row.automaticAdd) {
                                    stringResource(R.string.arr_sonarr_lists_preview_auto_add)
                                } else {
                                    stringResource(R.string.arr_sonarr_lists_preview_manual_add)
                                },
                                stringResource(R.string.arr_sonarr_lists_preview_monitor, row.monitor),
                                stringResource(
                                    R.string.arr_sonarr_lists_preview_quality_profile,
                                    row.qualityProfileId,
                                ),
                                row.rootFolderPath,
                                if (row.searchForMissing) {
                                    stringResource(R.string.arr_sonarr_lists_search_on_add)
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
                        stringResource(R.string.arr_sonarr_lists_preview_more, preview.rows.size - PREVIEW_ROW_LIMIT),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                if (preview.exclusionCount > 0) {
                    Text(
                        stringResource(R.string.arr_sonarr_lists_preview_exclusions, preview.exclusionCount),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextButton(onClick = onSync) {
                    Text(stringResource(R.string.arr_sonarr_lists_preview_sync_now))
                }
            }
        }
    }
}

@Composable
private fun syncLevelLabel(level: ListSyncLevelType): String = when (level) {
    ListSyncLevelType.DISABLED -> stringResource(R.string.arr_sonarr_lists_level_disabled)
    ListSyncLevelType.LOG_ONLY -> stringResource(R.string.arr_sonarr_lists_level_log_only)
    ListSyncLevelType.KEEP_AND_UNMONITOR ->
        stringResource(R.string.arr_sonarr_lists_level_keep_and_unmonitor)

    ListSyncLevelType.KEEP_AND_TAG -> stringResource(R.string.arr_sonarr_lists_level_keep_and_tag)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ImportListOptionsDialog(
    initial: ImportListConfigResource,
    busy: Boolean,
    onSave: (ImportListConfigResource) -> Unit,
    onDismiss: () -> Unit,
) {
    var syncLevel by remember { mutableStateOf(initial.listSyncLevel) }
    val entries = ListSyncLevelType.entries.filterNot { it == ListSyncLevelType.DISABLED }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.arr_sonarr_lists_options_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                var expanded by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = it },
                ) {
                    OutlinedTextField(
                        value = syncLevelLabel(syncLevel),
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(stringResource(R.string.arr_sonarr_lists_option_sync_level)) },
                        supportingText = {
                            Text(stringResource(R.string.arr_sonarr_lists_option_sync_level_support))
                        },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable),
                    )
                    ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        entries.forEach { entry ->
                            DropdownMenuItem(
                                text = { Text(syncLevelLabel(entry)) },
                                onClick = {
                                    syncLevel = entry
                                    expanded = false
                                },
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(initial.copy(listSyncLevel = syncLevel)) },
                enabled = !busy,
            ) {
                Text(stringResource(R.string.arr_sonarr_connect_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.arr_sonarr_connect_cancel))
            }
        },
    )
}

private const val PREVIEW_ROW_LIMIT = 25
