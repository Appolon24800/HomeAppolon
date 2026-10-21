package dev.appolon.homelab.arr.feature.lidarr.settings.connect.ui

import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Science
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.appolon.homelab.arr.api.lidarr.model.DownloadClientResource
import dev.appolon.homelab.arr.core.ServerId
import dev.appolon.homelab.arr.feature.lidarr.settings.connect.LidarrConnectClients
import dev.appolon.homelab.arr.feature.lidarr.settings.connect.vm.DownloadClientDraft
import dev.appolon.homelab.arr.feature.lidarr.settings.connect.vm.DownloadClientEditorNotice
import dev.appolon.homelab.arr.feature.lidarr.settings.connect.vm.LidarrDownloadClientEditorViewModel
import dev.appolon.homelab.arr.ui.components.LabeledSwitch
import dev.appolon.homelab.arr.ui.components.SectionHeader
import dev.appolon.homelab.arr.ui.lce.ArrLceBox
import dev.appolon.homelab.arr.ui.lce.LceState
import dev.appolon.homelab.arr.ui.shell.ArrScaffold
import dev.appolon.homelab.arr.ui.R as ArrUiR

/** Destination for the download client editor route (clientId null creates). */
@Composable
fun LidarrDownloadClientEditorDestination(
    serverId: ServerId,
    clientId: Int?,
    clients: LidarrConnectClients,
    serverName: String? = null,
    onBack: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val repository = remember(serverId, clients) { connectRepository(serverId, clients) }
    val vm: LidarrDownloadClientEditorViewModel = viewModel(
        key = "lidarr-downloadclient-editor-" + serverId.value + "-" + (clientId ?: "new"),
    ) {
        LidarrDownloadClientEditorViewModel(serverId, clientId, repository)
    }
    val state by vm.state.collectAsState()
    val lce by vm.lce.collectAsState()

    LidarrDownloadClientEditorScreen(
        state = state,
        lce = lce,
        serverName = serverName,
        onBack = onBack,
        onPick = vm::pickSchema,
        onNameChange = { name -> vm.updateDraft { it.copy(name = name) } },
        onToggleEnabled = { vm.updateDraft { it.copy(enable = !it.enable) } },
        onToggleRemoveCompleted = {
            vm.updateDraft { it.copy(removeCompletedDownloads = !it.removeCompletedDownloads) }
        },
        onToggleRemoveFailed = {
            vm.updateDraft { it.copy(removeFailedDownloads = !it.removeFailedDownloads) }
        },
        onPriorityChange = { priority -> vm.updateDraft { it.copy(priority = priority) } },
        onValueChange = vm::onValueChange,
        onToggleTag = vm::toggleTag,
        onTest = vm::test,
        onSave = { vm.save() },
        onDeleteRequest = vm::requestDelete,
        onDeleteCancelled = vm::deleteCancelled,
        onDeleteConfirmed = { vm.deleteConfirmed(onBack ?: {}) },
        onConsumeNotice = vm::consumeNotice,
        onConsumeWriteError = vm::consumeWriteError,
        modifier = modifier,
    )
}

/**
 * Download client add/edit. Add renders the definition picker first; after
 * a pick (or when editing) the form shows name, enable, priority, the
 * completed/failed removal toggles, tags, and the per-connection dynamic
 * fields. Test sends a probe through the connection.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LidarrDownloadClientEditorScreen(
    state: LidarrDownloadClientEditorViewModel.UiState,
    lce: LceState<LidarrDownloadClientEditorViewModel.UiState>,
    serverName: String?,
    onBack: (() -> Unit)?,
    onPick: (DownloadClientResource) -> Unit,
    onNameChange: (String) -> Unit,
    onToggleEnabled: () -> Unit,
    onToggleRemoveCompleted: () -> Unit,
    onToggleRemoveFailed: () -> Unit,
    onPriorityChange: (Int) -> Unit,
    onValueChange: (String, kotlinx.serialization.json.JsonElement) -> Unit,
    onToggleTag: (String) -> Unit,
    onTest: () -> Unit,
    onSave: () -> Unit,
    onDeleteRequest: () -> Unit,
    onDeleteCancelled: () -> Unit,
    onDeleteConfirmed: () -> Unit,
    onConsumeNotice: () -> Unit,
    onConsumeWriteError: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val snackbarHostState = remember { SnackbarHostState() }

    ConnectNoticeHost(
        noticeMessage = when (state.notice) {
            DownloadClientEditorNotice.SAVED ->
                stringResource(ArrUiR.string.arr_common_connect_notice_saved)

            DownloadClientEditorNotice.SAVED_QUEUED ->
                stringResource(ArrUiR.string.arr_common_system_notice_saved_queued)

            DownloadClientEditorNotice.DELETED ->
                stringResource(ArrUiR.string.arr_common_connect_notice_deleted)

            DownloadClientEditorNotice.DELETED_QUEUED ->
                stringResource(ArrUiR.string.arr_common_backup_notice_deleted_queued)

            DownloadClientEditorNotice.TEST_PASSED ->
                stringResource(ArrUiR.string.arr_common_connect_notice_test_passed)

            null -> null
        },
        writeError = state.writeError,
        snackbarHostState = snackbarHostState,
        onConsumeNotice = onConsumeNotice,
        onConsumeWriteError = onConsumeWriteError,
    )

    ArrScaffold(
        title = stringResource(
            if (state.isNew) ArrUiR.string.arr_common_clients_add
            else ArrUiR.string.arr_common_client_editor_title,
        ),
        subtitle = serverName,
        onBack = onBack,
        actions = {
            if (state.draft != null) {
                IconButton(onClick = onTest, enabled = !state.testing) {
                    Icon(
                        imageVector = Icons.Filled.Science,
                        contentDescription = stringResource(ArrUiR.string.arr_common_connect_cd_test),
                    )
                }
                if (!state.isNew) {
                    val deleteCd = stringResource(ArrUiR.string.arr_common_connect_cd_delete)
                    IconButton(
                        onClick = onDeleteRequest,
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
        },
        modifier = modifier,
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            ArrLceBox(
                state = lce,
                onRetry = onBack,
                emptyTitle = stringResource(ArrUiR.string.arr_common_connect_picker_empty_title),
                emptyMessage = stringResource(ArrUiR.string.arr_common_connect_picker_empty_message),
                modifier = Modifier.fillMaxSize(),
            ) { data ->
                when {
                    data.picker -> ClientSchemaPicker(
                        schemas = data.schemas,
                        onPick = onPick,
                    )

                    data.draft != null -> ClientForm(
                        state = data,
                        draft = data.draft,
                        onNameChange = onNameChange,
                        onToggleEnabled = onToggleEnabled,
                        onToggleRemoveCompleted = onToggleRemoveCompleted,
                        onToggleRemoveFailed = onToggleRemoveFailed,
                        onPriorityChange = onPriorityChange,
                        onValueChange = onValueChange,
                        onToggleTag = onToggleTag,
                        onSave = onSave,
                    )
                }
            }
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }

    if (state.confirmDelete) {
        AlertDialog(
            onDismissRequest = onDeleteCancelled,
            title = { Text(stringResource(ArrUiR.string.arr_common_delete)) },
            text = {
                Text(
                    stringResource(
                        ArrUiR.string.arr_common_connect_delete_message,
                        state.draft?.name.orEmpty(),
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
private fun ClientSchemaPicker(
    schemas: List<DownloadClientResource>,
    onPick: (DownloadClientResource) -> Unit,
) {
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.widthIn(max = ListMaxWidth + 32.dp),
    ) {
        item {
            Text(
                stringResource(ArrUiR.string.arr_common_connect_picker_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        items(
            schemas,
            key = { it.implementation ?: it.id.toString() },
            contentType = { "schema" },
        ) { schema ->
            val cd = stringResource(
                ArrUiR.string.arr_common_connect_picker_cd_pick,
                schema.implementationName.orEmpty(),
            )
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClickLabel = cd) { onPick(schema) },
            ) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp)
                        .padding(16.dp),
                ) {
                    Text(
                        schema.implementationName.orEmpty(),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }
        }
    }
}

@Composable
private fun ClientForm(
    state: LidarrDownloadClientEditorViewModel.UiState,
    draft: DownloadClientDraft,
    onNameChange: (String) -> Unit,
    onToggleEnabled: () -> Unit,
    onToggleRemoveCompleted: () -> Unit,
    onToggleRemoveFailed: () -> Unit,
    onPriorityChange: (Int) -> Unit,
    onValueChange: (String, kotlinx.serialization.json.JsonElement) -> Unit,
    onToggleTag: (String) -> Unit,
    onSave: () -> Unit,
) {
    var showAdvanced by rememberSaveable { mutableStateOf(false) }
    var priorityText by rememberSaveable { mutableStateOf(draft.priority.toString()) }
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.widthIn(max = ListMaxWidth + 32.dp),
    ) {
        item {
            OutlinedTextField(
                value = draft.name,
                onValueChange = onNameChange,
                label = { Text(stringResource(ArrUiR.string.arr_common_autotag_name)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item {
            Column {
                SectionHeader(title = stringResource(ArrUiR.string.arr_common_client_section_client))
                LabeledSwitch(
                    label = stringResource(ArrUiR.string.arr_common_general_cert_validation_enabled),
                    checked = draft.enable,
                    onCheckedChange = { onToggleEnabled() },
                )
                LabeledSwitch(
                    label = stringResource(ArrUiR.string.arr_common_client_remove_completed),
                    checked = draft.removeCompletedDownloads,
                    onCheckedChange = { onToggleRemoveCompleted() },
                )
                LabeledSwitch(
                    label = stringResource(ArrUiR.string.arr_common_client_remove_failed),
                    checked = draft.removeFailedDownloads,
                    onCheckedChange = { onToggleRemoveFailed() },
                )
            }
        }
        item {
            OutlinedTextField(
                value = priorityText,
                onValueChange = { next ->
                    priorityText = next
                    next.trim().toIntOrNull()?.let(onPriorityChange)
                },
                label = { Text(stringResource(ArrUiR.string.arr_common_connect_priority_label)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item {
            ConnectTagPicker(
                allTags = state.tags,
                selected = draft.tagLabels,
                onToggle = onToggleTag,
            )
        }
        item {
            ConnectFieldEditors(
                fields = draft.fields,
                values = draft.values,
                onValueChange = onValueChange,
                tagOptions = state.tags,
                showAdvanced = showAdvanced,
                onToggleAdvanced = { showAdvanced = !showAdvanced },
            )
        }
        item {
            ExtendedFloatingActionButton(
                onClick = onSave,
                modifier = if (state.busy || draft.name.isBlank()) {
                    Modifier.alpha(0.5f)
                } else {
                    Modifier
                },
            ) {
                Text(stringResource(ArrUiR.string.arr_common_save))
            }
        }
    }
}
