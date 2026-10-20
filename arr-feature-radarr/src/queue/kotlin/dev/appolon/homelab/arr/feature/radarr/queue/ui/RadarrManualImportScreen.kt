package dev.appolon.homelab.arr.feature.radarr.queue.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.appolon.homelab.arr.core.Humanize
import dev.appolon.homelab.arr.core.ServerId
import dev.appolon.homelab.arr.feature.radarr.R
import dev.appolon.homelab.arr.feature.radarr.queue.RadarrQueueClients
import dev.appolon.homelab.arr.feature.radarr.queue.data.ImportFolder
import dev.appolon.homelab.arr.feature.radarr.queue.data.RadarrManualImportRepository
import dev.appolon.homelab.arr.feature.radarr.queue.vm.ImportLoad
import dev.appolon.homelab.arr.feature.radarr.queue.vm.ManualImportNotice
import dev.appolon.homelab.arr.feature.radarr.queue.vm.ManualImportViewModel
import dev.appolon.homelab.arr.ui.components.EmptyState
import dev.appolon.homelab.arr.ui.lce.arrErrorMessage
import dev.appolon.homelab.arr.ui.shell.ArrScaffold
import dev.appolon.homelab.arr.ui.R as ArrUiR

/**
 * Feature-slot destination for manual import. With a [downloadId] it lists
 * that download's files; otherwise it starts the interactive folder browser
 * at the server root and scans whichever folder the user picks.
 */
@Composable
fun RadarrManualImportDestination(
    serverId: ServerId,
    clients: RadarrQueueClients,
    downloadId: String?,
    serverName: String?,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val repository = remember(serverId, clients) {
        RadarrManualImportRepository(
            serverId = serverId,
            manualImportApi = clients.manualImportApi,
            fileSystemCall = { path, includeFiles ->
                clients.fileSystemApi.fileSystem(path, includeFiles)
            },
            actionQueue = clients.actionQueue,
        )
    }
    val vm: ManualImportViewModel = viewModel(
        key = "radarr-manual-import-" + serverId.value + "-" + (downloadId ?: "folder"),
    ) {
        ManualImportViewModel(
            repository = repository,
            downloadId = downloadId,
            outputPath = null,
        )
    }
    val state by vm.state.collectAsState()

    ManualImportScreen(
        state = state,
        serverName = serverName,
        onBack = onBack,
        onOpenFolder = { folder -> vm.browse(folder.path) },
        onScanFolder = vm::loadFolderItems,
        onToggle = vm::toggle,
        onSelectAll = vm::selectAll,
        onClearSelection = vm::clearSelection,
        onImport = vm::importSelected,
        onRetryItems = vm::loadFolderItems,
        onRetryFolders = vm::browse,
        onConsumeNotice = vm::consumeNotice,
        onConsumeWriteError = vm::consumeWriteError,
        modifier = modifier,
    )
}

/**
 * Manual import screen: a folder browser (or a download's file listing),
 * candidate files with checkboxes, and the import action. Folder and file
 * rows are full-width targets well above 48dp; every control carries a
 * content description.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManualImportScreen(
    state: ManualImportViewModel.UiState,
    serverName: String?,
    onBack: () -> Unit,
    onOpenFolder: (ImportFolder) -> Unit,
    onScanFolder: (String) -> Unit,
    onToggle: (Int) -> Unit,
    onSelectAll: () -> Unit,
    onClearSelection: () -> Unit,
    onImport: () -> Unit,
    onRetryItems: (String) -> Unit,
    onRetryFolders: (String?) -> Unit,
    onConsumeNotice: () -> Unit,
    onConsumeWriteError: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val importedText = stringResource(
        ArrUiR.string.arr_common_manual_import_notice_imported,
        state.importedCount,
    )
    val queuedText = stringResource(ArrUiR.string.arr_common_import_flow_notice_queued)
    LaunchedEffect(state.notice) {
        when (state.notice) {
            ManualImportNotice.IMPORTED -> snackbarHostState.showSnackbar(importedText)
            ManualImportNotice.QUEUED -> snackbarHostState.showSnackbar(queuedText)
            null -> Unit
        }
        if (state.notice != null) onConsumeNotice()
    }
    val errorText = state.writeError?.let { arrErrorMessage(it) }
    LaunchedEffect(state.writeError) {
        if (errorText != null) {
            snackbarHostState.showSnackbar(errorText)
            onConsumeWriteError()
        }
    }

    ArrScaffold(
        title = stringResource(ArrUiR.string.arr_common_history_details_action_manual_import),
        subtitle = serverName,
        onBack = onBack,
        modifier = modifier,
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                if (state.importing) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
                state.folder?.let { folder ->
                    Text(
                        text = stringResource(
                            ArrUiR.string.arr_common_import_flow_current_folder,
                            folder,
                        ),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
                FolderBrowser(
                    state = state,
                    onOpenFolder = onOpenFolder,
                    onScanFolder = onScanFolder,
                    onRetryFolders = onRetryFolders,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(0.45f),
                )
                ImportItems(
                    state = state,
                    onToggle = onToggle,
                    onRetryItems = onRetryItems,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(0.55f),
                )
                SelectionBar(
                    state = state,
                    onSelectAll = onSelectAll,
                    onClearSelection = onClearSelection,
                    onImport = onImport,
                )
            }
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }
}

@Composable
private fun FolderBrowser(
    state: ManualImportViewModel.UiState,
    onOpenFolder: (ImportFolder) -> Unit,
    onScanFolder: (String) -> Unit,
    onRetryFolders: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (state.downloadId != null) return
    when (val load = state.foldersLoad) {
        ImportLoad.Loading -> Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = modifier.padding(16.dp),
        ) {
            CircularProgressIndicator()
        }

        is ImportLoad.Failed -> Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = modifier,
        ) {
            EmptyState(
                title = stringResource(ArrUiR.string.arr_common_import_flow_folders_error_title),
                message = arrErrorMessage(load.error),
                modifier = Modifier.fillMaxWidth(),
            )
            Button(onClick = { onRetryFolders(state.folder) }) {
                Text(stringResource(ArrUiR.string.arr_retry))
            }
        }

        ImportLoad.Ready -> LazyColumn(modifier = modifier) {
            items(state.folders, key = { it.path }, contentType = { "folder" }) { folder ->
                val folderCd = if (folder.isParent) {
                    stringResource(ArrUiR.string.arr_common_import_flow_cd_parent)
                } else {
                    stringResource(ArrUiR.string.arr_common_import_flow_cd_folder, folder.name)
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenFolder(folder) }
                        .semantics { contentDescription = folderCd }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                ) {
                    Icon(
                        imageVector = if (folder.isParent) {
                            Icons.Filled.ArrowUpward
                        } else {
                            Icons.Filled.Folder
                        },
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(text = folder.name, style = MaterialTheme.typography.bodyLarge)
                }
            }
            item(key = ScanButtonKey) {
                state.folder?.let { folder ->
                    Button(
                        onClick = { onScanFolder(folder) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                    ) {
                        Text(stringResource(ArrUiR.string.arr_common_manual_import_scan_folder))
                    }
                }
            }
        }
    }
}

@Composable
private fun ImportItems(
    state: ManualImportViewModel.UiState,
    onToggle: (Int) -> Unit,
    onRetryItems: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    when (val load = state.itemsLoad) {
        ImportLoad.Loading -> Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = modifier.padding(16.dp),
        ) {
            CircularProgressIndicator()
        }

        is ImportLoad.Failed -> Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = modifier,
        ) {
            EmptyState(
                title = stringResource(ArrUiR.string.arr_common_import_flow_files_error_title),
                message = arrErrorMessage(load.error),
                modifier = Modifier.fillMaxWidth(),
            )
            state.folder?.let { folder ->
                Button(onClick = { onRetryItems(folder) }) {
                    Text(stringResource(ArrUiR.string.arr_retry))
                }
            }
        }

        ImportLoad.Ready -> {
            if (state.items.isEmpty() && state.folder != null) {
                EmptyState(
                    title = stringResource(ArrUiR.string.arr_common_import_flow_no_files_title),
                    message = stringResource(ArrUiR.string.arr_common_manual_import_no_files_message),
                    modifier = modifier.fillMaxWidth(),
                )
            } else {
                LazyColumn(modifier = modifier) {
                    items(state.items, key = { it.id }, contentType = { "import-item" }) { item ->
                        val itemName = item.relativePath ?: item.name ?: item.path.orEmpty()
                        val itemCd = stringResource(
                            ArrUiR.string.arr_common_import_flow_cd_file,
                            itemName,
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onToggle(item.id) }
                                .semantics { contentDescription = itemCd }
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                        ) {
                            Checkbox(
                                checked = item.id in state.selected,
                                onCheckedChange = { onToggle(item.id) },
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = itemName,
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                val matched = item.movie.title
                                if (item.movie.id == 0 || matched == null) {
                                    Text(
                                        text = stringResource(R.string.arr_radarr_manual_import_unmatched),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.error,
                                    )
                                } else {
                                    Text(
                                        text = matched,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                item.size.takeIf { it > 0 }?.let { size ->
                                    Text(
                                        text = Humanize.bytes(size),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                item.rejections?.takeIf { it.isNotEmpty() }?.let { rejections ->
                                    Text(
                                        text = stringResource(
                                            ArrUiR.string.arr_common_manual_import_rejections,
                                            rejections.joinToString(" • ") { it.reason.orEmpty() },
                                        ),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.error,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SelectionBar(
    state: ManualImportViewModel.UiState,
    onSelectAll: () -> Unit,
    onClearSelection: () -> Unit,
    onImport: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Text(
                text = stringResource(
                    ArrUiR.string.arr_common_import_flow_selected,
                    state.selected.size,
                ),
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.weight(1f),
            )
            if (state.selected.isNotEmpty()) {
                OutlinedButton(onClick = onClearSelection) {
                    Text(stringResource(ArrUiR.string.arr_common_manual_import_clear_selection))
                }
                Spacer(Modifier.width(8.dp))
            } else if (state.items.isNotEmpty()) {
                OutlinedButton(onClick = onSelectAll) {
                    Text(stringResource(ArrUiR.string.arr_common_import_flow_select_all))
                }
                Spacer(Modifier.width(8.dp))
            }
            Button(onClick = onImport, enabled = state.canImport && !state.importing) {
                Text(stringResource(ArrUiR.string.arr_common_import_flow_approve))
            }
        }
        if (state.selectionIncomplete) {
            Text(
                text = stringResource(R.string.arr_radarr_manual_import_need_movie),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }
    }
}

private const val ScanButtonKey = "__radarr_manual_import_scan"
