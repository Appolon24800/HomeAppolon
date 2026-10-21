package dev.appolon.homelab.arr.feature.radarr.settings.connect.ui

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
import androidx.compose.material.icons.filled.Delete
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
import dev.appolon.homelab.arr.api.radarr.model.ImportListExclusionResource
import dev.appolon.homelab.arr.core.ServerId
import dev.appolon.homelab.arr.feature.radarr.R
import dev.appolon.homelab.arr.feature.radarr.settings.connect.RadarrConnectClients
import dev.appolon.homelab.arr.feature.radarr.settings.connect.vm.ImportListExclusionDraft
import dev.appolon.homelab.arr.feature.radarr.settings.connect.vm.ImportListExclusionNotice
import dev.appolon.homelab.arr.feature.radarr.settings.connect.vm.RadarrImportListExclusionsContent
import dev.appolon.homelab.arr.feature.radarr.settings.connect.vm.RadarrImportListExclusionsViewModel
import dev.appolon.homelab.arr.ui.lce.ArrLceBox
import dev.appolon.homelab.arr.ui.lce.ArrPullToRefreshBox
import dev.appolon.homelab.arr.ui.lce.LceState
import dev.appolon.homelab.arr.ui.shell.ArrScaffold
import dev.appolon.homelab.arr.ui.R as ArrUiR

/** Destination for the import list exclusions route. */
@Composable
fun RadarrImportListExclusionsDestination(
    serverId: ServerId,
    clients: RadarrConnectClients,
    serverName: String? = null,
    onBack: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val repository = remember(serverId, clients) { connectRepository(serverId, clients) }
    val vm: RadarrImportListExclusionsViewModel =
        viewModel(key = "radarr-exclusions-" + serverId.value) {
            RadarrImportListExclusionsViewModel(serverId, repository)
        }
    val state by vm.state.collectAsState()
    val lce by vm.lce.collectAsState()

    RadarrImportListExclusionsScreen(
        state = state,
        lce = lce,
        serverName = serverName,
        onBack = onBack,
        onRefresh = vm::refresh,
        onAdd = vm::startAdd,
        onDraftChange = vm::updateDraft,
        onCancelAdd = vm::cancelAdd,
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
 * Import list exclusions: one row per excluded movie with a delete
 * action, and an add dialog taking the TMDb id, title, and year. Writes
 * queue optimistically when offline, with queued badges on affected rows.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RadarrImportListExclusionsScreen(
    state: RadarrImportListExclusionsViewModel.UiState,
    lce: LceState<RadarrImportListExclusionsContent>,
    serverName: String?,
    onBack: (() -> Unit)?,
    onRefresh: () -> Unit,
    onAdd: () -> Unit,
    onDraftChange: ((ImportListExclusionDraft) -> ImportListExclusionDraft) -> Unit,
    onCancelAdd: () -> Unit,
    onSave: () -> Unit,
    onDeleteRequest: (ImportListExclusionResource) -> Unit,
    onDeleteCancelled: () -> Unit,
    onDeleteConfirmed: () -> Unit,
    onConsumeNotice: () -> Unit,
    onConsumeWriteError: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val content = (lce as? LceState.Content<RadarrImportListExclusionsContent>)?.data
    val refreshing =
        content != null && (lce as LceState.Content<RadarrImportListExclusionsContent>).refreshing

    NoticeHost(
        noticeMessage = when (state.notice) {
            ImportListExclusionNotice.ADDED ->
                stringResource(ArrUiR.string.arr_common_exclusions_notice_added)

            ImportListExclusionNotice.ADDED_QUEUED ->
                stringResource(ArrUiR.string.arr_common_system_notice_saved_queued)

            ImportListExclusionNotice.DELETED ->
                stringResource(ArrUiR.string.arr_common_connect_notice_deleted)

            ImportListExclusionNotice.DELETED_QUEUED ->
                stringResource(ArrUiR.string.arr_common_backup_notice_deleted_queued)

            null -> null
        },
        writeError = state.writeError,
        snackbarHostState = snackbarHostState,
        onConsumeNotice = onConsumeNotice,
        onConsumeWriteError = onConsumeWriteError,
    )

    ArrScaffold(
        title = stringResource(ArrUiR.string.arr_common_exclusions_title),
        subtitle = serverName,
        onBack = onBack,
        actions = {
            IconButton(onClick = onRefresh) {
                Icon(
                    imageVector = Icons.Filled.Refresh,
                    contentDescription = stringResource(ArrUiR.string.arr_common_exclusions_cd_refresh),
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
                    emptyTitle = stringResource(ArrUiR.string.arr_common_exclusions_empty_title),
                    emptyMessage = stringResource(R.string.arr_radarr_exclusions_empty_message),
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
                        items(
                            data.exclusions,
                            key = { it.id },
                            contentType = { "exclusion" },
                        ) { exclusion ->
                            ExclusionRow(
                                exclusion = exclusion,
                                queued = exclusion.id in data.queuedRemovedIds,
                                onDelete = { onDeleteRequest(exclusion) },
                            )
                        }
                    }
                }
            }
            ExtendedFloatingActionButton(
                onClick = onAdd,
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text(stringResource(ArrUiR.string.arr_common_exclusions_add)) },
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

    state.adding?.let { draft ->
        var tmdbText by remember { mutableStateOf(draft.tmdbId.toString()) }
        var yearText by remember { mutableStateOf(draft.year.toString()) }
        AlertDialog(
            onDismissRequest = onCancelAdd,
            title = { Text(stringResource(ArrUiR.string.arr_common_exclusions_add)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = draft.title,
                        onValueChange = { title -> onDraftChange { it.copy(title = title) } },
                        label = { Text(stringResource(R.string.arr_radarr_exclusions_title_label)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = tmdbText,
                        onValueChange = { text ->
                            tmdbText = text
                            text.toIntOrNull()?.let { id -> onDraftChange { it.copy(tmdbId = id) } }
                        },
                        label = { Text(stringResource(R.string.arr_radarr_exclusions_tmdb_label)) },
                        isError = tmdbText.toIntOrNull() == null || tmdbText.toIntOrNull()!! <= 0,
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = yearText,
                        onValueChange = { text ->
                            yearText = text
                            text.toIntOrNull()?.let { year -> onDraftChange { it.copy(year = year) } }
                        },
                        label = { Text(stringResource(ArrUiR.string.arr_common_exclusions_year_label)) },
                        isError = yearText.isNotBlank() && yearText.toIntOrNull() == null,
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = onSave,
                    enabled = !state.busy && draft.title.isNotBlank() && draft.tmdbId > 0,
                ) {
                    Text(stringResource(ArrUiR.string.arr_common_save))
                }
            },
            dismissButton = {
                TextButton(onClick = onCancelAdd) {
                    Text(stringResource(ArrUiR.string.arr_common_cancel))
                }
            },
        )
    }

    state.confirmDeleteFor?.let { target ->
        AlertDialog(
            onDismissRequest = onDeleteCancelled,
            title = { Text(stringResource(ArrUiR.string.arr_common_delete)) },
            text = {
                Text(
                    stringResource(
                        ArrUiR.string.arr_common_exclusions_delete_message,
                        target.movieTitle ?: target.tmdbId.toString(),
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
}

@Composable
private fun ExclusionRow(
    exclusion: ImportListExclusionResource,
    queued: Boolean,
    onDelete: () -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 64.dp)
                .padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 8.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    exclusion.movieTitle.orEmpty() + if (exclusion.movieYear > 0) {
                        " (" + exclusion.movieYear + ")"
                    } else {
                        ""
                    },
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    text = stringResource(
                        R.string.arr_radarr_exclusions_tmdb_value,
                        exclusion.tmdbId,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (queued) ConnectQueuedBadge()
            }
            val deleteCd = stringResource(
                R.string.arr_radarr_exclusions_cd_delete,
                exclusion.movieTitle.orEmpty(),
            )
            IconButton(
                onClick = onDelete,
                modifier = Modifier.semantics { contentDescription = deleteCd },
            ) {
                Icon(
                    imageVector = Icons.Filled.Delete,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}
