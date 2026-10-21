package dev.appolon.homelab.arr.feature.radarr.settings.connect.ui

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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.appolon.homelab.arr.api.radarr.model.ImportListResource
import dev.appolon.homelab.arr.core.ServerId
import dev.appolon.homelab.arr.feature.radarr.R
import dev.appolon.homelab.arr.feature.radarr.settings.connect.RadarrConnectClients
import dev.appolon.homelab.arr.feature.radarr.settings.connect.vm.ImportListDraft
import dev.appolon.homelab.arr.feature.radarr.settings.connect.vm.ImportListEditorNotice
import dev.appolon.homelab.arr.feature.radarr.settings.connect.vm.RadarrImportListEditorViewModel
import dev.appolon.homelab.arr.ui.components.LabeledSwitch
import dev.appolon.homelab.arr.ui.components.SectionHeader
import dev.appolon.homelab.arr.ui.lce.ArrLceBox
import dev.appolon.homelab.arr.ui.lce.LceState
import dev.appolon.homelab.arr.ui.shell.ArrScaffold
import dev.appolon.homelab.arr.ui.R as ArrUiR

/** Destination for the import list editor route (listId null creates). */
@Composable
fun RadarrImportListEditorDestination(
    serverId: ServerId,
    listId: Int?,
    clients: RadarrConnectClients,
    serverName: String? = null,
    onBack: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val repository = remember(serverId, clients) { connectRepository(serverId, clients) }
    val vm: RadarrImportListEditorViewModel = viewModel(
        key = "radarr-list-editor-" + serverId.value + "-" + (listId ?: "new"),
    ) {
        RadarrImportListEditorViewModel(serverId, listId, repository)
    }
    val state by vm.state.collectAsState()
    val lce by vm.lce.collectAsState()

    RadarrImportListEditorScreen(
        state = state,
        lce = lce,
        serverName = serverName,
        onBack = onBack,
        onPick = vm::pickSchema,
        onNameChange = { name -> vm.updateDraft { it.copy(name = name) } },
        onToggleEnabled = { vm.updateDraft { d -> d.copy(enabled = !d.enabled) } },
        onToggleAuto = { vm.updateDraft { d -> d.copy(enableAuto = !d.enableAuto) } },
        onToggleSearchOnAdd = { vm.updateDraft { d -> d.copy(searchOnAdd = !d.searchOnAdd) } },
        onRootFolderChange = { path -> vm.updateDraft { it.copy(rootFolderPath = path) } },
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
 * Import list add/edit. Add renders the definition picker first; after a
 * pick (or when editing) the form shows name, the enable/auto/search
 * toggles, the root folder path, tags, and the dynamic provider fields.
 * Test and Save probe or write the current draft.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RadarrImportListEditorScreen(
    state: RadarrImportListEditorViewModel.UiState,
    lce: LceState<RadarrImportListEditorViewModel.UiState>,
    serverName: String?,
    onBack: (() -> Unit)?,
    onPick: (ImportListResource) -> Unit,
    onNameChange: (String) -> Unit,
    onToggleEnabled: () -> Unit,
    onToggleAuto: () -> Unit,
    onToggleSearchOnAdd: () -> Unit,
    onRootFolderChange: (String) -> Unit,
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

    NoticeHost(
        noticeMessage = when (state.notice) {
            ImportListEditorNotice.SAVED ->
                stringResource(ArrUiR.string.arr_common_connect_notice_saved)

            ImportListEditorNotice.SAVED_QUEUED ->
                stringResource(ArrUiR.string.arr_common_system_notice_saved_queued)

            ImportListEditorNotice.DELETED ->
                stringResource(ArrUiR.string.arr_common_connect_notice_deleted)

            ImportListEditorNotice.DELETED_QUEUED ->
                stringResource(ArrUiR.string.arr_common_backup_notice_deleted_queued)

            ImportListEditorNotice.TEST_PASSED ->
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
            if (state.isNew) ArrUiR.string.arr_common_lists_add
            else ArrUiR.string.arr_common_list_editor_title,
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
                    data.picker -> ListSchemaPicker(
                        schemas = data.schemas,
                        onPick = onPick,
                    )

                    data.draft != null -> ListForm(
                        state = data,
                        draft = data.draft,
                        onNameChange = onNameChange,
                        onToggleEnabled = onToggleEnabled,
                        onToggleAuto = onToggleAuto,
                        onToggleSearchOnAdd = onToggleSearchOnAdd,
                        onRootFolderChange = onRootFolderChange,
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
private fun ListSchemaPicker(
    schemas: List<ImportListResource>,
    onPick: (ImportListResource) -> Unit,
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
private fun ListForm(
    state: RadarrImportListEditorViewModel.UiState,
    draft: ImportListDraft,
    onNameChange: (String) -> Unit,
    onToggleEnabled: () -> Unit,
    onToggleAuto: () -> Unit,
    onToggleSearchOnAdd: () -> Unit,
    onRootFolderChange: (String) -> Unit,
    onValueChange: (String, kotlinx.serialization.json.JsonElement) -> Unit,
    onToggleTag: (String) -> Unit,
    onSave: () -> Unit,
) {
    var showAdvanced by rememberSaveable { mutableStateOf(false) }
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
                SectionHeader(title = stringResource(ArrUiR.string.arr_common_list_section_add_options))
                LabeledSwitch(
                    label = stringResource(ArrUiR.string.arr_common_general_cert_validation_enabled),
                    checked = draft.enabled,
                    onCheckedChange = { onToggleEnabled() },
                )
                LabeledSwitch(
                    label = stringResource(R.string.arr_radarr_lists_auto),
                    checked = draft.enableAuto,
                    onCheckedChange = { onToggleAuto() },
                )
                LabeledSwitch(
                    label = stringResource(ArrUiR.string.arr_common_lists_search_on_add),
                    checked = draft.searchOnAdd,
                    onCheckedChange = { onToggleSearchOnAdd() },
                )
            }
        }
        item {
            OutlinedTextField(
                value = draft.rootFolderPath,
                onValueChange = onRootFolderChange,
                label = { Text(stringResource(ArrUiR.string.arr_common_list_root_folder)) },
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
