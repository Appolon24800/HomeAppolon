package dev.appolon.homelab.arr.feature.lidarr.search.ui

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
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import dev.appolon.homelab.arr.api.lidarr.model.Quality
import dev.appolon.homelab.arr.core.Humanize
import dev.appolon.homelab.arr.feature.lidarr.R
import dev.appolon.homelab.arr.feature.lidarr.search.data.ImportFolder
import dev.appolon.homelab.arr.feature.lidarr.search.vm.ImportFileUi
import dev.appolon.homelab.arr.feature.lidarr.search.vm.ImportFlowLoad
import dev.appolon.homelab.arr.feature.lidarr.search.vm.ImportFlowMessage
import dev.appolon.homelab.arr.feature.lidarr.search.vm.InteractiveImportUiState
import dev.appolon.homelab.arr.ui.components.EmptyState
import dev.appolon.homelab.arr.ui.lce.arrErrorMessage
import dev.appolon.homelab.arr.ui.shell.ArrScaffold

/**
 * The interactive import decision flow for one artist: a folder browser, the
 * parsed candidate files with per-file quality editors, and the approve
 * action. Unmatched files stay listed but cannot be selected, because the
 * import replays each file's own artist, album, and release match. Tablets
 * (width from 840dp) show folders and files side by side; phones stack them.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InteractiveImportScreen(
    state: InteractiveImportUiState,
    onBack: () -> Unit,
    onRetryArtist: () -> Unit,
    onOpenFolder: (ImportFolder) -> Unit,
    onPickFolder: (String) -> Unit,
    onRetryFolders: () -> Unit,
    onRetryFiles: () -> Unit,
    onToggleFile: (Int) -> Unit,
    onSelectAll: (Boolean) -> Unit,
    onOpenEditor: (Int) -> Unit,
    onCloseEditor: () -> Unit,
    onApplyDecision: (id: Int, quality: Quality?) -> Unit,
    onApprove: () -> Unit,
    onConsumeNotice: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    // Resolved in composable scope: arrErrorMessage is composable and cannot
    // run inside the LaunchedEffect coroutine below. The selection survives
    // the approve, so selectedCount is the approved file count.
    val noticeText = importNoticeText(state.notice, state.selectedCount)
    LaunchedEffect(state.notice) {
        when (noticeText) {
            null -> Unit
            else -> {
                snackbarHostState.showSnackbar(noticeText)
                onConsumeNotice()
            }
        }
    }

    ArrScaffold(
        title = stringResource(R.string.arr_lidarr_import_flow_title),
        subtitle = state.artist?.artistName,
        onBack = onBack,
        modifier = modifier,
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            when (state.artistLoad) {
                ImportFlowLoad.Loading -> Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(24.dp),
                ) {
                    CircularProgressIndicator()
                }

                is ImportFlowLoad.Failed -> Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    EmptyState(
                        title = stringResource(R.string.arr_lidarr_import_flow_artist_error_title),
                        message = arrErrorMessage(state.artistLoad.error),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Button(onClick = onRetryArtist) {
                        Text(stringResource(dev.appolon.homelab.arr.ui.R.string.arr_retry))
                    }
                }

                ImportFlowLoad.Ready -> {
                    Column(modifier = Modifier.fillMaxSize()) {
                        if (state.approving) {
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        }
                        state.folder?.let { folder ->
                            Text(
                                text = stringResource(
                                    R.string.arr_lidarr_import_flow_current_folder,
                                    folder,
                                ),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            )
                        }
                        val wide = androidx.compose.ui.platform.LocalConfiguration.current
                            .screenWidthDp >= 840
                        if (wide) {
                            Row(modifier = Modifier.fillMaxSize()) {
                                FolderPane(
                                    state = state,
                                    onOpenFolder = onOpenFolder,
                                    onPickFolder = onPickFolder,
                                    onRetryFolders = onRetryFolders,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .weight(1f),
                                )
                                FilesPane(
                                    state = state,
                                    onRetryFiles = onRetryFiles,
                                    onToggleFile = onToggleFile,
                                    onOpenEditor = onOpenEditor,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .weight(1.4f),
                                )
                            }
                        } else {
                            FolderPane(
                                state = state,
                                onOpenFolder = onOpenFolder,
                                onPickFolder = onPickFolder,
                                onRetryFolders = onRetryFolders,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(0.42f),
                            )
                            FilesPane(
                                state = state,
                                onRetryFiles = onRetryFiles,
                                onToggleFile = onToggleFile,
                                onOpenEditor = onOpenEditor,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(0.58f),
                            )
                        }
                        SelectionBar(
                            state = state,
                            onSelectAll = onSelectAll,
                            onApprove = onApprove,
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

    val editing = state.files.firstOrNull { it.id == state.editingFileId }
    if (editing != null) {
        DecisionEditorDialog(
            file = editing,
            qualityOptions = state.qualityOptions,
            onSave = onApplyDecision,
            onDismiss = onCloseEditor,
        )
    }
}

@Composable
private fun PaneHeader(label: String) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

/** Snackbar text for the current one-shot import outcome; null when none. */
@Composable
private fun importNoticeText(notice: ImportFlowMessage?, approvedCount: Int): String? =
    when (notice) {
        null -> null
        ImportFlowMessage.Imported -> stringResource(
            R.string.arr_lidarr_import_flow_notice_imported,
            approvedCount,
        )

        ImportFlowMessage.ImportQueued ->
            stringResource(R.string.arr_lidarr_import_flow_notice_queued)

        is ImportFlowMessage.ImportFailed -> arrErrorMessage(notice.error)
    }

@Composable
private fun FolderPane(
    state: InteractiveImportUiState,
    onOpenFolder: (ImportFolder) -> Unit,
    onPickFolder: (String) -> Unit,
    onRetryFolders: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        PaneHeader(stringResource(R.string.arr_lidarr_import_flow_folders_label))
        when (val load = state.foldersLoad) {
            ImportFlowLoad.Loading -> Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(16.dp),
            ) {
                CircularProgressIndicator()
            }

            is ImportFlowLoad.Failed -> Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth(),
            ) {
                EmptyState(
                    title = stringResource(R.string.arr_lidarr_import_flow_folders_error_title),
                    message = arrErrorMessage(load.error),
                    modifier = Modifier.fillMaxWidth(),
                )
                Button(onClick = onRetryFolders) {
                    Text(stringResource(dev.appolon.homelab.arr.ui.R.string.arr_retry))
                }
            }

            ImportFlowLoad.Ready -> LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(state.folders, key = { it.path }) { folder ->
                    FolderRow(
                        folder = folder,
                        current = folder.path == state.folder,
                        onOpenFolder = onOpenFolder,
                        onPickFolder = onPickFolder,
                    )
                }
            }
        }
    }
}

@Composable
private fun FolderRow(
    folder: ImportFolder,
    current: Boolean,
    onOpenFolder: (ImportFolder) -> Unit,
    onPickFolder: (String) -> Unit,
) {
    val openCd = if (folder.isParent) {
        stringResource(R.string.arr_lidarr_import_flow_cd_parent)
    } else {
        stringResource(R.string.arr_lidarr_import_flow_cd_folder, folder.name)
    }
    val pickCd = stringResource(R.string.arr_lidarr_import_flow_cd_pick_folder, folder.name)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable { onOpenFolder(folder) }
            .semantics { contentDescription = openCd },
    ) {
        Icon(
            imageVector = if (folder.isParent) Icons.Filled.ArrowUpward else Icons.Filled.Folder,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 16.dp),
        )
        Text(
            text = folder.name,
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp),
        )
        if (!folder.isParent) {
            IconButton(
                onClick = { onPickFolder(folder.path) },
                modifier = Modifier.semantics { contentDescription = pickCd },
            ) {
                Icon(
                    imageVector = Icons.Filled.DownloadDone,
                    contentDescription = null,
                    tint = if (current) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
        }
    }
}

@Composable
private fun FilesPane(
    state: InteractiveImportUiState,
    onRetryFiles: () -> Unit,
    onToggleFile: (Int) -> Unit,
    onOpenEditor: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        PaneHeader(stringResource(R.string.arr_lidarr_import_flow_files_label))
        when (val load = state.filesLoad) {
            ImportFlowLoad.Loading -> Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(16.dp),
            ) {
                CircularProgressIndicator()
            }

            is ImportFlowLoad.Failed -> Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth(),
            ) {
                EmptyState(
                    title = stringResource(R.string.arr_lidarr_import_flow_files_error_title),
                    message = arrErrorMessage(load.error),
                    modifier = Modifier.fillMaxWidth(),
                )
                Button(onClick = onRetryFiles) {
                    Text(stringResource(dev.appolon.homelab.arr.ui.R.string.arr_retry))
                }
            }

            ImportFlowLoad.Ready -> {
                if (state.files.isEmpty()) {
                    val hint = state.folder?.let {
                        stringResource(R.string.arr_lidarr_import_flow_no_files_message)
                    }
                    EmptyState(
                        title = stringResource(R.string.arr_lidarr_import_flow_no_files_title),
                        message = hint
                            ?: stringResource(R.string.arr_lidarr_import_flow_pick_folder_hint),
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(state.files, key = { it.id }) { file ->
                            FileRow(
                                file = file,
                                artistName = state.artist?.artistName,
                                onToggleFile = onToggleFile,
                                onOpenEditor = onOpenEditor,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FileRow(
    file: ImportFileUi,
    artistName: String?,
    onToggleFile: (Int) -> Unit,
    onOpenEditor: (Int) -> Unit,
) {
    val toggleCd = stringResource(R.string.arr_lidarr_import_flow_cd_file, file.name)
    val editCd = stringResource(R.string.arr_lidarr_import_flow_cd_edit_decision, file.name)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable { onToggleFile(file.id) }
            .semantics { contentDescription = toggleCd },
    ) {
        Checkbox(
            checked = file.selected,
            onCheckedChange = { onToggleFile(file.id) },
            enabled = file.matched,
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = file.name,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (!file.matched) {
                Text(
                    text = stringResource(R.string.arr_lidarr_import_flow_no_match),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                )
            } else {
                val serverMatch = file.matchedArtistTitle
                if (serverMatch != null && serverMatch != artistName) {
                    Text(
                        text = stringResource(
                            R.string.arr_lidarr_import_flow_server_match,
                            serverMatch,
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                }
            }
            Text(
                text = listOfNotNull(
                    file.matchedAlbumTitle,
                    if (file.trackCount > 0) {
                        stringResource(R.string.arr_lidarr_import_flow_track_count, file.trackCount)
                    } else {
                        null
                    },
                    file.qualityName?.let {
                        stringResource(R.string.arr_lidarr_import_flow_quality_line, it)
                    },
                ).joinToString(" · "),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row {
                Text(
                    text = Humanize.bytes(file.sizeBytes),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (file.rejections.isNotEmpty()) {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = stringResource(
                            R.string.arr_lidarr_import_flow_rejections,
                            file.rejections.size,
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
        IconButton(
            onClick = { onOpenEditor(file.id) },
            enabled = file.matched,
            modifier = Modifier.semantics { contentDescription = editCd },
        ) {
            Icon(
                imageVector = Icons.Filled.Edit,
                contentDescription = null,
                tint = if (file.matched) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.outline
                },
            )
        }
    }
}

@Composable
private fun SelectionBar(
    state: InteractiveImportUiState,
    onSelectAll: (Boolean) -> Unit,
    onApprove: () -> Unit,
) {
    val matchedCount = state.files.count { it.matched }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Text(
            text = stringResource(R.string.arr_lidarr_import_flow_selected, state.selectedCount),
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.weight(1f),
        )
        if (matchedCount > 0) {
            if (state.selectedCount == matchedCount) {
                OutlinedButton(
                    onClick = { onSelectAll(false) },
                    modifier = Modifier.heightIn(min = 48.dp),
                ) {
                    Text(stringResource(R.string.arr_lidarr_import_flow_clear_selection))
                }
            } else {
                OutlinedButton(
                    onClick = { onSelectAll(true) },
                    modifier = Modifier.heightIn(min = 48.dp),
                ) {
                    Text(stringResource(R.string.arr_lidarr_import_flow_select_all))
                }
            }
            Spacer(Modifier.width(8.dp))
        }
        Button(
            onClick = onApprove,
            enabled = state.canApprove,
            modifier = Modifier.heightIn(min = 48.dp),
        ) {
            Text(stringResource(R.string.arr_lidarr_import_flow_approve))
        }
    }
}

/** Per-file decision editor: the quality dropdown over a dialog. */
@Composable
private fun DecisionEditorDialog(
    file: ImportFileUi,
    qualityOptions: List<Quality>,
    onSave: (id: Int, quality: Quality?) -> Unit,
    onDismiss: () -> Unit,
) {
    var quality by remember(file.id) { mutableStateOf<Quality?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.arr_lidarr_import_flow_editor_title)) },
        text = {
            Column {
                Text(
                    text = file.name,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                DecisionDropdown(
                    label = stringResource(R.string.arr_lidarr_import_flow_editor_quality),
                    parsedName = file.qualityName,
                    optionNames = qualityOptions.mapNotNull { it.name },
                    selectedName = quality?.name,
                    onSelect = { name -> quality = qualityOptions.firstOrNull { it.name == name } },
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(file.id, quality) }) {
                Text(stringResource(R.string.arr_lidarr_import_flow_editor_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.arr_lidarr_import_flow_editor_cancel))
            }
        },
    )
}

@Composable
private fun DecisionDropdown(
    label: String,
    parsedName: String?,
    optionNames: List<String>,
    selectedName: String?,
    onSelect: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Column(modifier = Modifier.padding(top = 8.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedButton(
            onClick = { expanded = true },
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp),
        ) {
            Text(
                text = selectedName
                    ?: parsedName?.let {
                        stringResource(R.string.arr_lidarr_import_flow_editor_as_parsed, it)
                    }
                    ?: stringResource(R.string.arr_lidarr_grab_sheet_as_parsed),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            optionNames.forEach { name ->
                DropdownMenuItem(
                    text = { Text(name) },
                    onClick = {
                        expanded = false
                        onSelect(name)
                    },
                )
            }
        }
    }
}
