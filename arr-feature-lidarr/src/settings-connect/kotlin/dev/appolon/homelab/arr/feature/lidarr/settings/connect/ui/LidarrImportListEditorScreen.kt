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
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
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
import dev.appolon.homelab.arr.api.lidarr.model.ImportListMonitorType
import dev.appolon.homelab.arr.api.lidarr.model.ImportListResource
import dev.appolon.homelab.arr.core.ServerId
import dev.appolon.homelab.arr.feature.lidarr.R
import dev.appolon.homelab.arr.feature.lidarr.settings.connect.LidarrConnectClients
import dev.appolon.homelab.arr.feature.lidarr.settings.connect.vm.ImportListDraft
import dev.appolon.homelab.arr.feature.lidarr.settings.connect.vm.ImportListEditorNotice
import dev.appolon.homelab.arr.feature.lidarr.settings.connect.vm.LidarrImportListEditorViewModel
import dev.appolon.homelab.arr.ui.components.LabeledSwitch
import dev.appolon.homelab.arr.ui.components.SectionHeader
import dev.appolon.homelab.arr.ui.lce.ArrLceBox
import dev.appolon.homelab.arr.ui.lce.LceState
import dev.appolon.homelab.arr.ui.shell.ArrScaffold
import dev.appolon.homelab.arr.ui.R as ArrUiR

/** Destination for the import list editor route (listId null creates). */
@Composable
fun LidarrImportListEditorDestination(
    serverId: ServerId,
    listId: Int?,
    clients: LidarrConnectClients,
    serverName: String? = null,
    onBack: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val repository = remember(serverId, clients) { connectRepository(serverId, clients) }
    val vm: LidarrImportListEditorViewModel = viewModel(
        key = "lidarr-importlist-editor-" + serverId.value + "-" + (listId ?: "new"),
    ) {
        LidarrImportListEditorViewModel(serverId, listId, repository)
    }
    val state by vm.state.collectAsState()
    val lce by vm.lce.collectAsState()

    LidarrImportListEditorScreen(
        state = state,
        lce = lce,
        serverName = serverName,
        onBack = onBack,
        onPick = vm::pickSchema,
        onNameChange = { name -> vm.updateDraft { it.copy(name = name) } },
        onToggleAutomaticAdd = {
            vm.updateDraft { it.copy(enableAutomaticAdd = !it.enableAutomaticAdd) }
        },
        onToggleSearch = { vm.updateDraft { it.copy(shouldSearch = !it.shouldSearch) } },
        onToggleMonitorExisting = {
            vm.updateDraft { it.copy(shouldMonitorExisting = !it.shouldMonitorExisting) }
        },
        onMonitorChange = { monitor -> vm.updateDraft { it.copy(shouldMonitor = monitor) } },
        onRootFolderChange = { path -> vm.updateDraft { it.copy(rootFolderPath = path) } },
        onQualityProfileChange = { id -> vm.updateDraft { it.copy(qualityProfileId = id) } },
        onMetadataProfileChange = { id -> vm.updateDraft { it.copy(metadataProfileId = id) } },
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
 * pick (or when editing) the form shows name, the add options every
 * synced artist inherits (monitor strategy, existing-artist monitoring,
 * search on add, quality and metadata profiles, root folder), tags, and
 * the per-connection dynamic fields.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LidarrImportListEditorScreen(
    state: LidarrImportListEditorViewModel.UiState,
    lce: LceState<LidarrImportListEditorViewModel.UiState>,
    serverName: String?,
    onBack: (() -> Unit)?,
    onPick: (ImportListResource) -> Unit,
    onNameChange: (String) -> Unit,
    onToggleAutomaticAdd: () -> Unit,
    onToggleSearch: () -> Unit,
    onToggleMonitorExisting: () -> Unit,
    onMonitorChange: (ImportListMonitorType) -> Unit,
    onRootFolderChange: (String) -> Unit,
    onQualityProfileChange: (Int) -> Unit,
    onMetadataProfileChange: (Int) -> Unit,
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
                        onToggleAutomaticAdd = onToggleAutomaticAdd,
                        onToggleSearch = onToggleSearch,
                        onToggleMonitorExisting = onToggleMonitorExisting,
                        onMonitorChange = onMonitorChange,
                        onRootFolderChange = onRootFolderChange,
                        onQualityProfileChange = onQualityProfileChange,
                        onMetadataProfileChange = onMetadataProfileChange,
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
    state: LidarrImportListEditorViewModel.UiState,
    draft: ImportListDraft,
    onNameChange: (String) -> Unit,
    onToggleAutomaticAdd: () -> Unit,
    onToggleSearch: () -> Unit,
    onToggleMonitorExisting: () -> Unit,
    onMonitorChange: (ImportListMonitorType) -> Unit,
    onRootFolderChange: (String) -> Unit,
    onQualityProfileChange: (Int) -> Unit,
    onMetadataProfileChange: (Int) -> Unit,
    onValueChange: (String, kotlinx.serialization.json.JsonElement) -> Unit,
    onToggleTag: (String) -> Unit,
    onSave: () -> Unit,
) {
    var showAdvanced by rememberSaveable { mutableStateOf(false) }
    var qualityProfileText by rememberSaveable { mutableStateOf(draft.qualityProfileId.toString()) }
    var metadataProfileText by rememberSaveable { mutableStateOf(draft.metadataProfileId.toString()) }
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
                    label = stringResource(R.string.arr_lidarr_list_automatic_add),
                    checked = draft.enableAutomaticAdd,
                    onCheckedChange = { onToggleAutomaticAdd() },
                )
                LabeledSwitch(
                    label = stringResource(R.string.arr_lidarr_list_search_for_new),
                    checked = draft.shouldSearch,
                    onCheckedChange = { onToggleSearch() },
                )
                LabeledSwitch(
                    label = stringResource(R.string.arr_lidarr_list_monitor_existing),
                    checked = draft.shouldMonitorExisting,
                    onCheckedChange = { onToggleMonitorExisting() },
                )
            }
        }
        item {
            ConnectEnumDropdown(
                label = stringResource(R.string.arr_lidarr_list_monitor),
                selected = draft.shouldMonitor,
                entries = ImportListMonitorType.entries.toList(),
                onSelect = onMonitorChange,
                entryLabel = { monitor -> monitorLabel(monitor) },
            )
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = draft.rootFolderPath,
                    onValueChange = onRootFolderChange,
                    label = { Text(stringResource(ArrUiR.string.arr_common_list_root_folder)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = qualityProfileText,
                    onValueChange = { next ->
                        qualityProfileText = next
                        next.trim().toIntOrNull()?.let(onQualityProfileChange)
                    },
                    label = { Text(stringResource(R.string.arr_lidarr_list_quality_profile)) },
                    supportingText = {
                        Text(stringResource(R.string.arr_lidarr_list_quality_profile_support))
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = metadataProfileText,
                    onValueChange = { next ->
                        metadataProfileText = next
                        next.trim().toIntOrNull()?.let(onMetadataProfileChange)
                    },
                    label = { Text(stringResource(R.string.arr_lidarr_list_metadata_profile)) },
                    supportingText = {
                        Text(stringResource(R.string.arr_lidarr_list_metadata_profile_support))
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
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

/** Reads the monitor strategy as Lidarr's own labels instead of wire names. */
@Composable
internal fun monitorLabel(monitor: ImportListMonitorType): String = when (monitor) {
    ImportListMonitorType.NONE -> stringResource(R.string.arr_lidarr_list_monitor_none)
    ImportListMonitorType.SPECIFIC_ALBUM ->
        stringResource(R.string.arr_lidarr_list_monitor_specific_album)

    ImportListMonitorType.ENTIRE_ARTIST ->
        stringResource(R.string.arr_lidarr_list_monitor_entire_artist)
}

/**
 * Dropdown over a closed set of enum values, labeled through
 * [entryLabel] so callers can render the wire names as English.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun <T : Enum<T>> ConnectEnumDropdown(
    label: String,
    selected: T,
    entries: List<T>,
    onSelect: (T) -> Unit,
    entryLabel: @Composable (T) -> String = { it.name.lowercase() },
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
    ) {
        OutlinedTextField(
            value = entryLabel(selected),
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(MenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            entries.forEach { entry ->
                DropdownMenuItem(
                    text = { Text(entryLabel(entry)) },
                    onClick = {
                        onSelect(entry)
                        expanded = false
                    },
                )
            }
        }
    }
}
