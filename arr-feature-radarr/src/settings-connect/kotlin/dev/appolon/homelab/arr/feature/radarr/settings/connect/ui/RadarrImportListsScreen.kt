package dev.appolon.homelab.arr.feature.radarr.settings.connect.ui

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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.appolon.homelab.arr.api.radarr.model.ImportListConfigResource
import dev.appolon.homelab.arr.api.radarr.model.ImportListResource
import dev.appolon.homelab.arr.core.ServerId
import dev.appolon.homelab.arr.feature.radarr.R
import dev.appolon.homelab.arr.feature.radarr.settings.connect.RadarrConnectClients
import dev.appolon.homelab.arr.feature.radarr.settings.connect.vm.ImportListsNotice
import dev.appolon.homelab.arr.feature.radarr.settings.connect.vm.RadarrImportListsContent
import dev.appolon.homelab.arr.feature.radarr.settings.connect.vm.RadarrImportListsViewModel
import dev.appolon.homelab.arr.ui.components.LabeledSwitch
import dev.appolon.homelab.arr.ui.components.SectionHeader
import dev.appolon.homelab.arr.ui.lce.ArrLceBox
import dev.appolon.homelab.arr.ui.lce.ArrPullToRefreshBox
import dev.appolon.homelab.arr.ui.lce.LceState
import dev.appolon.homelab.arr.ui.lce.arrErrorMessage
import dev.appolon.homelab.arr.ui.shell.ArrScaffold
import dev.appolon.homelab.arr.ui.R as ArrUiR

/** Destination for the import lists route. */
@Composable
fun RadarrImportListsDestination(
    serverId: ServerId,
    clients: RadarrConnectClients,
    serverName: String? = null,
    onBack: (() -> Unit)? = null,
    onOpenList: (Int?) -> Unit,
    onOpenExclusions: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val repository = remember(serverId, clients) { connectRepository(serverId, clients) }
    val vm: RadarrImportListsViewModel =
        viewModel(key = "radarr-importlists-" + serverId.value) {
            RadarrImportListsViewModel(serverId, repository)
        }
    val state by vm.state.collectAsState()
    val lce by vm.lce.collectAsState()

    RadarrImportListsScreen(
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
 * Import lists list. Rows show the enabled/auto sync badges, quality and
 * root folder add options, and per-list test verdicts; the app bar offers
 * test-all, sync (server command), the sync preview sheet, exclusions,
 * and the list options card.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RadarrImportListsScreen(
    state: RadarrImportListsViewModel.UiState,
    lce: LceState<RadarrImportListsContent>,
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
    val content = (lce as? LceState.Content<RadarrImportListsContent>)?.data
    val refreshing = content != null && (lce as LceState.Content<RadarrImportListsContent>).refreshing

    NoticeHost(
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

            ImportListsNotice.OPTIONS_SAVED ->
                stringResource(ArrUiR.string.arr_common_connect_notice_saved)

            ImportListsNotice.OPTIONS_SAVED_QUEUED ->
                stringResource(ArrUiR.string.arr_common_system_notice_saved_queued)

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
                    contentDescription = stringResource(R.string.arr_radarr_lists_cd_preview),
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
            IconButton(onClick = { showOptions = true }) {
                Icon(
                    imageVector = Icons.Filled.Tune,
                    contentDescription = stringResource(ArrUiR.string.arr_common_lists_cd_options),
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
                    emptyMessage = stringResource(R.string.arr_radarr_lists_empty_message),
                    modifier = Modifier.fillMaxSize(),
                ) { data ->
                    LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.widthIn(max = ListMaxWidth + 32.dp),
                    ) {
                        if (data.pendingActions > 0) {
                            item { PendingBanner(data.pendingActions) }
                        }
                        item {
                            SectionHeader(
                                title = stringResource(ArrUiR.string.arr_common_lists_title),
                            )
                        }
                        items(data.lists, key = { it.id }, contentType = { "list" }) { list ->
                            ImportListRow(
                                list = list,
                                tags = data.tags,
                                testResult = data.testResults[list.id],
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

    state.preview?.let { preview ->
        ModalBottomSheet(onDismissRequest = onClosePreview) {
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
                when {
                    preview.loading -> Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        CircularProgressIndicator()
                    }

                    preview.error != null -> Text(
                        arrErrorMessage(preview.error),
                        color = MaterialTheme.colorScheme.error,
                    )

                    else -> {
                        Text(
                            stringResource(
                                R.string.arr_radarr_lists_preview_summary,
                                preview.addableCount,
                                preview.existingCount,
                                preview.excludedCount,
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        preview.movies.take(PREVIEW_ROW_LIMIT).forEach { movie ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 48.dp),
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        movie.title.orEmpty() + if (movie.year > 0) {
                                            " (" + movie.year + ")"
                                        } else {
                                            ""
                                        },
                                        style = MaterialTheme.typography.bodyLarge,
                                    )
                                }
                                val flag = when {
                                    movie.isExcluded -> stringResource(
                                        R.string.arr_radarr_lists_preview_excluded,
                                    )

                                    movie.isExisting -> stringResource(
                                        ArrUiR.string.arr_common_lists_preview_existing,
                                    )

                                    else -> stringResource(R.string.arr_radarr_lists_preview_addable)
                                }
                                Text(
                                    flag,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        if (preview.movies.size > PREVIEW_ROW_LIMIT) {
                            Text(
                                stringResource(
                                    ArrUiR.string.arr_common_lists_preview_more,
                                    preview.movies.size - PREVIEW_ROW_LIMIT,
                                ),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            }
        }
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
    tags: List<dev.appolon.homelab.arr.api.radarr.model.TagResource>,
    testResult: dev.appolon.homelab.arr.api.radarr.model.ProviderTestAllResult?,
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
                        if (list.enabled) {
                            stringResource(ArrUiR.string.arr_common_general_cert_validation_enabled)
                        } else {
                            null
                        },
                        if (list.enableAuto) {
                            stringResource(R.string.arr_radarr_lists_auto)
                        } else {
                            null
                        },
                        if (list.searchOnAdd) {
                            stringResource(ArrUiR.string.arr_common_lists_search_on_add)
                        } else {
                            null
                        },
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
                testResult?.let { TestResultBadge(it) }
            }
            val deleteCd = stringResource(ArrUiR.string.arr_common_lists_cd_delete, list.name.orEmpty())
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Filled.Delete,
                    contentDescription = deleteCd,
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun ImportListOptionsDialog(
    initial: ImportListConfigResource,
    busy: Boolean,
    onSave: (ImportListConfigResource) -> Unit,
    onDismiss: () -> Unit,
) {
    var syncLevel by remember { mutableStateOf(initial.listSyncLevel.orEmpty()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(ArrUiR.string.arr_common_lists_cd_options)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = syncLevel,
                    onValueChange = { syncLevel = it },
                    label = { Text(stringResource(ArrUiR.string.arr_common_lists_option_sync_level)) },
                    supportingText = {
                        Text(stringResource(R.string.arr_radarr_lists_option_sync_level_support))
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(initial.copy(listSyncLevel = syncLevel)) },
                enabled = !busy,
            ) {
                Text(stringResource(ArrUiR.string.arr_common_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(ArrUiR.string.arr_common_cancel))
            }
        },
    )
}

private const val PREVIEW_ROW_LIMIT = 25
