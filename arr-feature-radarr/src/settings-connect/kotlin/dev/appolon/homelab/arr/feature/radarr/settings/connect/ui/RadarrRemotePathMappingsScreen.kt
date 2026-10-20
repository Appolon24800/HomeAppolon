package dev.appolon.homelab.arr.feature.radarr.settings.connect.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import dev.appolon.homelab.arr.api.radarr.model.RemotePathMappingResource
import dev.appolon.homelab.arr.core.ServerId
import dev.appolon.homelab.arr.feature.radarr.R
import dev.appolon.homelab.arr.feature.radarr.settings.connect.RadarrConnectClients
import dev.appolon.homelab.arr.feature.radarr.settings.connect.vm.RadarrRemotePathMappingsContent
import dev.appolon.homelab.arr.feature.radarr.settings.connect.vm.RadarrRemotePathMappingsViewModel
import dev.appolon.homelab.arr.feature.radarr.settings.connect.vm.RemotePathMappingDraft
import dev.appolon.homelab.arr.feature.radarr.settings.connect.vm.RemotePathMappingNotice
import dev.appolon.homelab.arr.ui.lce.ArrLceBox
import dev.appolon.homelab.arr.ui.lce.ArrPullToRefreshBox
import dev.appolon.homelab.arr.ui.lce.LceState
import dev.appolon.homelab.arr.ui.shell.ArrScaffold

/** Destination for the remote path mappings route. */
@Composable
fun RadarrRemotePathMappingsDestination(
    serverId: ServerId,
    clients: RadarrConnectClients,
    serverName: String? = null,
    onBack: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val repository = remember(serverId, clients) { connectRepository(serverId, clients) }
    val vm: RadarrRemotePathMappingsViewModel =
        viewModel(key = "radarr-remotepaths-" + serverId.value) {
            RadarrRemotePathMappingsViewModel(serverId, repository)
        }
    val state by vm.state.collectAsState()
    val lce by vm.lce.collectAsState()

    RadarrRemotePathMappingsScreen(
        state = state,
        lce = lce,
        serverName = serverName,
        onBack = onBack,
        onRefresh = vm::refresh,
        onAdd = vm::startAdd,
        onEdit = vm::startEdit,
        onDraftChange = vm::updateDraft,
        onCancelEdit = vm::cancelEdit,
        onSave = vm::save,
        onDeleteRequest = vm::requestDelete,
        onDeleteCancelled = vm::deleteCancelled,
        onDeleteConfirmed = vm::deleteConfirmed,
        onConsumeNotice = vm::consumeNotice,
        onConsumeWriteError = vm::consumeWriteError,
        modifier = modifier,
    )
}

/**
 * Remote path mappings CRUD: one row per mapping (host, remote path,
 * local path), an add/edit dialog, and delete with confirmation. Writes
 * queue optimistically when offline, with queued badges on affected rows.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RadarrRemotePathMappingsScreen(
    state: RadarrRemotePathMappingsViewModel.UiState,
    lce: LceState<RadarrRemotePathMappingsContent>,
    serverName: String?,
    onBack: (() -> Unit)?,
    onRefresh: () -> Unit,
    onAdd: () -> Unit,
    onEdit: (RemotePathMappingResource) -> Unit,
    onDraftChange: ((RemotePathMappingDraft) -> RemotePathMappingDraft) -> Unit,
    onCancelEdit: () -> Unit,
    onSave: () -> Unit,
    onDeleteRequest: (RemotePathMappingResource) -> Unit,
    onDeleteCancelled: () -> Unit,
    onDeleteConfirmed: () -> Unit,
    onConsumeNotice: () -> Unit,
    onConsumeWriteError: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val content = (lce as? LceState.Content<RadarrRemotePathMappingsContent>)?.data
    val refreshing =
        content != null && (lce as LceState.Content<RadarrRemotePathMappingsContent>).refreshing

    NoticeHost(
        noticeMessage = when (state.notice) {
            RemotePathMappingNotice.SAVED ->
                stringResource(R.string.arr_radarr_connect_notice_saved)

            RemotePathMappingNotice.SAVED_QUEUED ->
                stringResource(R.string.arr_radarr_connect_notice_saved_queued)

            RemotePathMappingNotice.DELETED ->
                stringResource(R.string.arr_radarr_connect_notice_deleted)

            RemotePathMappingNotice.DELETED_QUEUED ->
                stringResource(R.string.arr_radarr_connect_notice_deleted_queued)

            null -> null
        },
        writeError = state.writeError,
        snackbarHostState = snackbarHostState,
        onConsumeNotice = onConsumeNotice,
        onConsumeWriteError = onConsumeWriteError,
    )

    ArrScaffold(
        title = stringResource(R.string.arr_radarr_paths_title),
        subtitle = serverName,
        onBack = onBack,
        actions = {
            IconButton(onClick = onRefresh) {
                Icon(
                    imageVector = Icons.Filled.Refresh,
                    contentDescription = stringResource(R.string.arr_radarr_paths_cd_refresh),
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
                    emptyTitle = stringResource(R.string.arr_radarr_paths_empty_title),
                    emptyMessage = stringResource(R.string.arr_radarr_paths_empty_message),
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
                        items(data.mappings, key = { it.id }) { mapping ->
                            MappingRow(
                                mapping = mapping,
                                onEdit = { onEdit(mapping) },
                                onDelete = { onDeleteRequest(mapping) },
                            )
                        }
                    }
                }
            }
            ExtendedFloatingActionButton(
                onClick = onAdd,
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.arr_radarr_paths_add)) },
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

    state.editing?.let { draft ->
        AlertDialog(
            onDismissRequest = onCancelEdit,
            title = {
                Text(
                    stringResource(
                        if (draft.id == 0) R.string.arr_radarr_paths_add_title
                        else R.string.arr_radarr_paths_edit_title,
                    ),
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = draft.host,
                        onValueChange = { host -> onDraftChange { it.copy(host = host) } },
                        label = { Text(stringResource(R.string.arr_radarr_paths_host)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = draft.remotePath,
                        onValueChange = { path -> onDraftChange { it.copy(remotePath = path) } },
                        label = { Text(stringResource(R.string.arr_radarr_paths_remote)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = draft.localPath,
                        onValueChange = { path -> onDraftChange { it.copy(localPath = path) } },
                        label = { Text(stringResource(R.string.arr_radarr_paths_local)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = onSave,
                    enabled = !state.busy &&
                        draft.host.isNotBlank() &&
                        draft.remotePath.isNotBlank() &&
                        draft.localPath.isNotBlank(),
                ) {
                    Text(stringResource(R.string.arr_radarr_connect_save))
                }
            },
            dismissButton = {
                TextButton(onClick = onCancelEdit) {
                    Text(stringResource(R.string.arr_radarr_connect_cancel))
                }
            },
        )
    }

    state.confirmDeleteFor?.let { target ->
        AlertDialog(
            onDismissRequest = onDeleteCancelled,
            title = { Text(stringResource(R.string.arr_radarr_connect_delete_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.arr_radarr_paths_delete_message,
                        target.remotePath.orEmpty(),
                        target.localPath.orEmpty(),
                    ),
                )
            },
            confirmButton = {
                TextButton(onClick = onDeleteConfirmed) {
                    Text(stringResource(R.string.arr_radarr_connect_delete_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = onDeleteCancelled) {
                    Text(stringResource(R.string.arr_radarr_connect_delete_cancel))
                }
            },
        )
    }
}

@Composable
private fun MappingRow(
    mapping: RemotePathMappingResource,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        androidx.compose.foundation.layout.Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 64.dp)
                .padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 8.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    mapping.host.orEmpty(),
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    text = stringResource(
                        R.string.arr_radarr_paths_row_paths,
                        mapping.remotePath.orEmpty(),
                        mapping.localPath.orEmpty(),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            val editCd = stringResource(R.string.arr_radarr_paths_cd_edit, mapping.host.orEmpty())
            IconButton(
                onClick = onEdit,
                modifier = Modifier.semantics { contentDescription = editCd },
            ) {
                Icon(Icons.Filled.Edit, contentDescription = null)
            }
            val deleteCd = stringResource(
                R.string.arr_radarr_paths_cd_delete,
                mapping.host.orEmpty(),
            )
            IconButton(
                onClick = onDelete,
                modifier = Modifier.semantics { contentDescription = deleteCd },
            ) {
                Icon(
                    Icons.Filled.Delete,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}
