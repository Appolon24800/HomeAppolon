package dev.appolon.homelab.arr.feature.sonarr.settings.mm.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DriveFileMove
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import dev.appolon.homelab.arr.api.sonarr.model.RootFolderResource
import dev.appolon.homelab.arr.core.ArrError
import dev.appolon.homelab.arr.core.Humanize
import dev.appolon.homelab.arr.core.ServerId
import dev.appolon.homelab.arr.feature.sonarr.R
import dev.appolon.homelab.arr.feature.sonarr.settings.mm.SonarrSettingsMmClients
import dev.appolon.homelab.arr.feature.sonarr.settings.mm.data.RootFolderWithDisk
import dev.appolon.homelab.arr.feature.sonarr.settings.mm.data.SonarrFileSystemRepository
import dev.appolon.homelab.arr.feature.sonarr.settings.mm.data.SonarrRootFolderRepository
import dev.appolon.homelab.arr.feature.sonarr.settings.mm.vm.RootFolderNotice
import dev.appolon.homelab.arr.feature.sonarr.settings.mm.vm.SonarrRootFoldersContent
import dev.appolon.homelab.arr.feature.sonarr.settings.mm.vm.SonarrRootFoldersViewModel
import dev.appolon.homelab.arr.ui.lce.ArrLceBox
import dev.appolon.homelab.arr.ui.lce.ArrPullToRefreshBox
import dev.appolon.homelab.arr.ui.lce.LceState
import dev.appolon.homelab.arr.ui.lce.arrErrorMessage
import dev.appolon.homelab.arr.ui.shell.ArrScaffold

/**
 * Destination for the root folders route: wires the root folder
 * repository, the filesystem browser repository, and the ViewModel for
 * this route's server.
 */
@Composable
fun SonarrRootFoldersDestination(
    serverId: ServerId,
    clients: SonarrSettingsMmClients,
    serverName: String? = null,
    onBack: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val repository = remember(serverId, clients) {
        SonarrRootFolderRepository(
            serverId = serverId,
            store = clients.store,
            rootFolderApi = clients.apis.rootFolderApi,
            diskSpaceLoader = clients.apis.diskSpaceApi::list,
            actionQueue = clients.actionQueue,
        )
    }
    val fileSystemRepository = remember(serverId, clients) {
        SonarrFileSystemRepository(clients.apis.fileSystemApi)
    }
    val vm: SonarrRootFoldersViewModel = viewModel(key = "sonarr-rootfolders-" + serverId.value) {
        SonarrRootFoldersViewModel(
            serverId = serverId,
            repository = repository,
        )
    }
    val state by vm.state.collectAsState()
    val lce by vm.lce.collectAsState()

    SonarrRootFoldersScreen(
        state = state,
        lce = lce,
        serverName = serverName,
        onBack = onBack,
        onRefresh = vm::refresh,
        onSelect = vm::select,
        onAdd = vm::add,
        onRemove = vm::remove,
        onMove = vm::move,
        fileSystemRepository = fileSystemRepository,
        onConsumeNotice = vm::consumeNotice,
        onConsumeWriteError = vm::consumeWriteError,
        modifier = modifier,
    )
}

/**
 * The root folders list. Each row joins its folder to the hosting disk
 * for a used-space bar plus the free bytes, shows the unmapped folder
 * count, and carries a queued badge while a removal waits to sync. Add
 * and edit dialogs pick paths through the filesystem browser. On tablet
 * widths the selected folder's detail opens as a side pane instead of a
 * bottom sheet.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SonarrRootFoldersScreen(
    state: SonarrRootFoldersViewModel.UiState,
    lce: LceState<SonarrRootFoldersContent>,
    serverName: String?,
    onBack: (() -> Unit)?,
    onRefresh: () -> Unit,
    onSelect: (Int?) -> Unit,
    onAdd: (path: String) -> Unit,
    onRemove: (folderId: Int) -> Unit,
    onMove: (folder: RootFolderResource, newPath: String) -> Unit,
    fileSystemRepository: SonarrFileSystemRepository,
    onConsumeNotice: () -> Unit,
    onConsumeWriteError: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    var showAdd by remember { mutableStateOf(false) }
    var showMoveFor by remember { mutableStateOf<RootFolderWithDisk?>(null) }
    var confirmDeleteFor by remember { mutableStateOf<RootFolderWithDisk?>(null) }
    val content = (lce as? LceState.Content<SonarrRootFoldersContent>)?.data
    val refreshing = content != null && (lce as LceState.Content<SonarrRootFoldersContent>).refreshing

    NoticeHost(
        notice = state.notice,
        writeError = state.writeError,
        snackbarHostState = snackbarHostState,
        onConsumeNotice = onConsumeNotice,
        onConsumeWriteError = onConsumeWriteError,
    )

    ArrScaffold(
        title = stringResource(R.string.arr_sonarr_roots_title),
        subtitle = serverName,
        onBack = onBack,
        actions = {
            IconButton(onClick = onRefresh) {
                Icon(
                    imageVector = Icons.Filled.Refresh,
                    contentDescription = stringResource(R.string.arr_sonarr_roots_cd_refresh),
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
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val twoPane = maxWidth >= ExpandedPaneWidth
                if (twoPane) {
                    Row(Modifier.fillMaxSize()) {
                        FolderList(
                            state = state,
                            content = content,
                            onSelect = onSelect,
                            modifier = Modifier.weight(1f),
                        )
                        state.selected?.let { selected ->
                            FolderDetailPane(
                                folder = selected,
                                queued = selected.folder.id in state.queuedRemovedIds,
                                onMove = { showMoveFor = selected },
                                onDelete = { confirmDeleteFor = selected },
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(16.dp),
                            )
                        }
                    }
                } else {
                    FolderList(
                        state = state,
                        content = content,
                        onSelect = onSelect,
                        modifier = Modifier.fillMaxSize(),
                    )
                    // Compact widths: the selected folder opens as a sheet.
                    content?.folders?.firstOrNull { it.folder.id == state.selectedFolderId }
                        ?.let { selected ->
                            ModalBottomSheet(
                                onDismissRequest = { onSelect(null) },
                                sheetState = rememberModalBottomSheetState(),
                            ) {
                                FolderDetailPane(
                                    folder = selected,
                                    queued = selected.folder.id in state.queuedRemovedIds,
                                    onMove = { showMoveFor = selected },
                                    onDelete = { confirmDeleteFor = selected },
                                    modifier = Modifier
                                        .padding(horizontal = 24.dp)
                                        .padding(bottom = 24.dp),
                                )
                            }
                        }
                }
                SnackbarHost(
                    hostState = snackbarHostState,
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
                ExtendedFloatingActionButton(
                    onClick = { showAdd = true },
                    icon = {
                        Icon(
                            imageVector = Icons.Filled.Add,
                            contentDescription = null,
                        )
                    },
                    text = { Text(stringResource(R.string.arr_sonarr_roots_add)) },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(16.dp),
                )
            }
        }
    }

    if (showAdd) {
        FolderPathDialog(
            title = stringResource(R.string.arr_sonarr_roots_add_title),
            initialPath = "",
            fileSystemRepository = fileSystemRepository,
            confirmLabel = stringResource(R.string.arr_sonarr_roots_add_confirm),
            onConfirm = { path ->
                showAdd = false
                onAdd(path)
            },
            onDismiss = { showAdd = false },
        )
    }

    showMoveFor?.let { target ->
        FolderPathDialog(
            title = stringResource(R.string.arr_sonarr_roots_move_title, target.folder.path ?: ""),
            initialPath = target.folder.path ?: "",
            fileSystemRepository = fileSystemRepository,
            confirmLabel = stringResource(R.string.arr_sonarr_roots_move_confirm),
            supporting = stringResource(R.string.arr_sonarr_roots_move_hint),
            onConfirm = { path ->
                val folder = target.folder
                showMoveFor = null
                onMove(folder, path)
            },
            onDismiss = { showMoveFor = null },
        )
    }

    confirmDeleteFor?.let { target ->
        AlertDialog(
            onDismissRequest = { confirmDeleteFor = null },
            title = { Text(stringResource(R.string.arr_sonarr_roots_delete_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.arr_sonarr_roots_delete_message,
                        target.folder.path ?: "",
                    ),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val id = target.folder.id
                        confirmDeleteFor = null
                        onRemove(id)
                    },
                ) {
                    Text(stringResource(R.string.arr_sonarr_roots_delete_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDeleteFor = null }) {
                    Text(stringResource(R.string.arr_sonarr_roots_delete_cancel))
                }
            },
        )
    }
}

@Composable
private fun FolderList(
    state: SonarrRootFoldersViewModel.UiState,
    content: SonarrRootFoldersContent?,
    onSelect: (Int?) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (content == null) return
    LazyColumn(
        contentPadding = PaddingValues(
            top = 8.dp,
            bottom = 96.dp,
        ),
        modifier = modifier,
    ) {
        if (content.pendingActions > 0) {
            item(key = PendingBannerKey) { PendingSyncBanner(content.pendingActions) }
        }
        items(content.folders, key = { it.folder.id }) { entry ->
            FolderRow(
                entry = entry,
                selected = entry.folder.id == state.selectedFolderId,
                queued = entry.folder.id in content.queuedRemovedIds,
                onClick = { onSelect(entry.folder.id) },
            )
        }
    }
}

@Composable
private fun FolderRow(
    entry: RootFolderWithDisk,
    selected: Boolean,
    queued: Boolean,
    onClick: () -> Unit,
) {
    val path = entry.folder.path ?: ""
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.secondaryContainer
            } else {
                MaterialTheme.colorScheme.surface
            },
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .clickable(
                    onClickLabel = stringResource(
                        R.string.arr_sonarr_roots_cd_open,
                        path,
                    ),
                    onClick = onClick,
                )
                .padding(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.Folder,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    text = path,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (queued) {
                    Icon(
                        imageVector = Icons.Filled.CloudUpload,
                        contentDescription = stringResource(R.string.arr_sonarr_roots_cd_queued_indicator),
                        tint = MaterialTheme.colorScheme.tertiary,
                    )
                }
            }
            if (entry.unmappedCount > 0) {
                Text(
                    text = stringResource(R.string.arr_sonarr_roots_unmapped, entry.unmappedCount),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            if (entry.totalSpace > 0) {
                LinearProgressIndicator(
                    progress = { entry.usedFraction ?: 0f },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                )
                Text(
                    text = stringResource(
                        R.string.arr_sonarr_roots_free_of_total,
                        Humanize.bytes(entry.freeSpace),
                        Humanize.bytes(entry.totalSpace),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            } else {
                Text(
                    text = stringResource(R.string.arr_sonarr_roots_free_only, Humanize.bytes(entry.freeSpace)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }
}

/** Shared detail layout for the tablet side pane and the phone bottom sheet. */
@Composable
private fun FolderDetailPane(
    folder: RootFolderWithDisk,
    queued: Boolean,
    onMove: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.widthIn(max = 640.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = folder.folder.path ?: "",
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (queued) {
                Icon(
                    imageVector = Icons.Filled.CloudUpload,
                    contentDescription = stringResource(R.string.arr_sonarr_roots_cd_queued_indicator),
                    tint = MaterialTheme.colorScheme.tertiary,
                )
            }
        }
        if (queued) {
            Text(
                text = stringResource(R.string.arr_sonarr_roots_queued_row),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        if (folder.totalSpace > 0) {
            Text(
                text = stringResource(
                    R.string.arr_sonarr_roots_free_of_total,
                    Humanize.bytes(folder.freeSpace),
                    Humanize.bytes(folder.totalSpace),
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        } else {
            Text(
                text = stringResource(R.string.arr_sonarr_roots_free_only, Humanize.bytes(folder.freeSpace)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        val unmapped = folder.folder.unmappedFolders.orEmpty()
        if (unmapped.isNotEmpty()) {
            Text(
                text = stringResource(R.string.arr_sonarr_roots_detail_unmapped),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(top = 16.dp),
            )
            unmapped.take(UnmappedPreviewCount).forEach { item ->
                Text(
                    text = item.name ?: item.relativePath ?: item.path ?: "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            if (unmapped.size > UnmappedPreviewCount) {
                Text(
                    text = stringResource(
                        R.string.arr_sonarr_roots_detail_unmapped_more,
                        unmapped.size - UnmappedPreviewCount,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(top = 16.dp),
        ) {
            AssistChip(
                onClick = onMove,
                label = { Text(stringResource(R.string.arr_sonarr_roots_action_move)) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Filled.DriveFileMove,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                },
            )
            AssistChip(
                onClick = onDelete,
                label = { Text(stringResource(R.string.arr_sonarr_roots_action_delete)) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Filled.Delete,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                },
            )
        }
    }
}

/** Add/move dialog: free-typed path plus the server filesystem browser. */
@Composable
private fun FolderPathDialog(
    title: String,
    initialPath: String,
    fileSystemRepository: SonarrFileSystemRepository,
    confirmLabel: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
    supporting: String? = null,
) {
    var path by remember { mutableStateOf(initialPath) }
    var browsing by remember { mutableStateOf(false) }

    if (browsing) {
        FileSystemBrowserDialog(
            repository = fileSystemRepository,
            initialPath = path.ifBlank { "/" },
            onPick = { picked ->
                browsing = false
                path = picked
            },
            onDismiss = { browsing = false },
        )
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                if (supporting != null) {
                    Text(
                        text = supporting,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 12.dp),
                    )
                }
                OutlinedTextField(
                    value = path,
                    onValueChange = { path = it },
                    singleLine = true,
                    label = { Text(stringResource(R.string.arr_sonarr_roots_path_label)) },
                    trailingIcon = {
                        IconButton(onClick = { browsing = true }) {
                            Icon(
                                imageVector = Icons.Filled.FolderOpen,
                                contentDescription = stringResource(R.string.arr_sonarr_roots_cd_browse),
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(path) },
                enabled = path.isNotBlank(),
            ) {
                Text(confirmLabel)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.arr_sonarr_roots_dialog_cancel))
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
            contentDescription = stringResource(R.string.arr_sonarr_roots_cd_queued_indicator),
            tint = MaterialTheme.colorScheme.tertiary,
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = stringResource(R.string.arr_sonarr_roots_pending_banner, pending),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.tertiary,
        )
    }
}

@Composable
private fun NoticeHost(
    notice: RootFolderNotice?,
    writeError: ArrError?,
    snackbarHostState: SnackbarHostState,
    onConsumeNotice: () -> Unit,
    onConsumeWriteError: () -> Unit,
) {
    val message = when (notice) {
        RootFolderNotice.ADDED -> stringResource(R.string.arr_sonarr_roots_notice_added)
        RootFolderNotice.ADDED_QUEUED ->
            stringResource(R.string.arr_sonarr_roots_notice_added_queued)
        RootFolderNotice.REMOVED -> stringResource(R.string.arr_sonarr_roots_notice_removed)
        RootFolderNotice.REMOVED_QUEUED ->
            stringResource(R.string.arr_sonarr_roots_notice_removed_queued)
        RootFolderNotice.MOVED -> stringResource(R.string.arr_sonarr_roots_notice_moved)
        RootFolderNotice.MOVED_QUEUED ->
            stringResource(R.string.arr_sonarr_roots_notice_moved_queued)
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

private const val PendingBannerKey = "__sonarr_roots_pending"

/** Unmapped folders listed in the detail pane before the "and N more" line. */
private const val UnmappedPreviewCount = 5

/** Material window size class breakpoint for expanded tablet widths. */
private val ExpandedPaneWidth = 840.dp
