package dev.appolon.homelab.arr.feature.prowlarr.settings.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Science
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.appolon.homelab.arr.api.prowlarr.model.DownloadClientResource
import dev.appolon.homelab.arr.feature.prowlarr.R
import dev.appolon.homelab.arr.feature.prowlarr.indexers.ui.FieldEditors
import dev.appolon.homelab.arr.feature.prowlarr.indexers.ui.TagLabelPicker
import dev.appolon.homelab.arr.feature.prowlarr.settings.data.GrabTarget
import dev.appolon.homelab.arr.feature.prowlarr.settings.vm.DownloadClientEditorNotice
import dev.appolon.homelab.arr.feature.prowlarr.settings.vm.ProwlarrDownloadClientEditorViewModel
import dev.appolon.homelab.arr.ui.components.LabeledSwitch
import dev.appolon.homelab.arr.ui.components.SectionHeader
import dev.appolon.homelab.arr.ui.lce.ArrLceBox
import dev.appolon.homelab.arr.ui.lce.LceState
import dev.appolon.homelab.arr.ui.shell.ArrScaffold
import dev.appolon.homelab.arr.ui.R as ArrUiR

/**
 * Download client add/edit. Add renders the definition picker first; after
 * a pick (or when editing) the form shows name, enable, priority, tags, the
 * per-connection dynamic fields, and, when the implementation supports
 * categories, the grab-target mapping editor routing newznab categories to
 * client-side categories.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProwlarrDownloadClientEditorScreen(
    state: ProwlarrDownloadClientEditorViewModel.UiState,
    lce: LceState<ProwlarrDownloadClientEditorViewModel.UiState>,
    serverName: String?,
    onBack: (() -> Unit)?,
    onPick: (DownloadClientResource) -> Unit,
    onNameChange: (String) -> Unit,
    onEnableChange: (Boolean) -> Unit,
    onPriorityChange: (Int) -> Unit,
    onValueChange: (String, kotlinx.serialization.json.JsonElement) -> Unit,
    onToggleTag: (String) -> Unit,
    onAddGrabTarget: () -> Unit,
    onRemoveGrabTarget: (Int) -> Unit,
    onGrabTargetCategoryChange: (Int, String) -> Unit,
    onToggleGrabTargetNewznab: (Int, Int) -> Unit,
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
            DownloadClientEditorNotice.SAVED ->
                stringResource(ArrUiR.string.arr_common_connect_notice_saved)

            DownloadClientEditorNotice.SAVED_QUEUED ->
                stringResource(R.string.arr_prowlarr_settings_notice_saved_queued)

            DownloadClientEditorNotice.DELETED ->
                stringResource(ArrUiR.string.arr_common_connect_notice_deleted)

            DownloadClientEditorNotice.DELETED_QUEUED ->
                stringResource(R.string.arr_prowlarr_settings_notice_deleted_queued)

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
                        contentDescription = stringResource(R.string.arr_prowlarr_settings_cd_test),
                    )
                }
                if (!state.isNew) {
                    val deleteCd = stringResource(ArrUiR.string.arr_common_delete)
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
                emptyMessage = stringResource(R.string.arr_prowlarr_settings_picker_empty_message),
                modifier = Modifier.fillMaxSize(),
            ) { data ->
                when {
                    data.picker -> ClientSchemaPicker(
                        schemas = data.schemas,
                        onPick = onPick,
                    )

                    data.draft != null -> ClientForm(
                        state = data,
                        onNameChange = onNameChange,
                        onEnableChange = onEnableChange,
                        onPriorityChange = onPriorityChange,
                        onValueChange = onValueChange,
                        onToggleTag = onToggleTag,
                        onAddGrabTarget = onAddGrabTarget,
                        onRemoveGrabTarget = onRemoveGrabTarget,
                        onGrabTargetCategoryChange = onGrabTargetCategoryChange,
                        onToggleGrabTargetNewznab = onToggleGrabTargetNewznab,
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
            title = { Text(stringResource(R.string.arr_prowlarr_settings_delete_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.arr_prowlarr_settings_delete_message,
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
                stringResource(R.string.arr_prowlarr_settings_picker_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        items(schemas, key = { it.implementation ?: it.id.toString() }) { schema ->
            val cd = stringResource(
                R.string.arr_prowlarr_settings_picker_cd_pick,
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
    state: ProwlarrDownloadClientEditorViewModel.UiState,
    onNameChange: (String) -> Unit,
    onEnableChange: (Boolean) -> Unit,
    onPriorityChange: (Int) -> Unit,
    onValueChange: (String, kotlinx.serialization.json.JsonElement) -> Unit,
    onToggleTag: (String) -> Unit,
    onAddGrabTarget: () -> Unit,
    onRemoveGrabTarget: (Int) -> Unit,
    onGrabTargetCategoryChange: (Int, String) -> Unit,
    onToggleGrabTargetNewznab: (Int, Int) -> Unit,
    onSave: () -> Unit,
) {
    val draft = state.draft ?: return
    var showAdvanced by rememberSaveable { mutableStateOf(false) }
    var priorityText by rememberSaveable(draft.id.toString()) {
        mutableStateOf(draft.priority.toString())
    }
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .widthIn(max = ListMaxWidth + 32.dp)
            .focusGroup(),
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
            LabeledSwitch(
                label = stringResource(ArrUiR.string.arr_common_general_cert_validation_enabled),
                checked = draft.enable,
                onCheckedChange = onEnableChange,
            )
        }
        item {
            OutlinedTextField(
                value = priorityText,
                onValueChange = { next ->
                    priorityText = next
                    next.toIntOrNull()?.let(onPriorityChange)
                },
                label = { Text(stringResource(ArrUiR.string.arr_common_connect_priority_label)) },
                supportingText = {
                    Text(stringResource(R.string.arr_prowlarr_settings_priority_help))
                },
                isError = priorityText.toIntOrNull() == null,
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item {
            TagLabelPicker(
                allTags = state.tags,
                selected = draft.tagLabels,
                onToggle = onToggleTag,
            )
        }
        if (draft.supportsCategories) {
            item {
                GrabTargetEditors(
                    targets = draft.targets,
                    categoryOptions = state.categoryOptions,
                    onAdd = onAddGrabTarget,
                    onRemove = onRemoveGrabTarget,
                    onCategoryChange = onGrabTargetCategoryChange,
                    onToggleNewznab = onToggleGrabTargetNewznab,
                )
            }
        }
        item {
            FieldEditors(
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

/**
 * The grab-target mapping editor: one card per mapping, naming the
 * client-side category and picking the newznab categories routed to it.
 * Category chips fall back to raw ids when the category table is empty.
 */
@Composable
private fun GrabTargetEditors(
    targets: List<GrabTarget>,
    categoryOptions: List<dev.appolon.homelab.arr.api.prowlarr.model.IndexerCategory>,
    onAdd: () -> Unit,
    onRemove: (Int) -> Unit,
    onCategoryChange: (Int, String) -> Unit,
    onToggleNewznab: (Int, Int) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionHeader(
            title = stringResource(R.string.arr_prowlarr_settings_targets_title),
            modifier = Modifier.semantics { heading() },
        )
        Text(
            stringResource(R.string.arr_prowlarr_settings_targets_help),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        targets.forEachIndexed { index, target ->
            GrabTargetCard(
                index = index,
                target = target,
                categoryOptions = categoryOptions,
                onRemove = onRemove,
                onCategoryChange = onCategoryChange,
                onToggleNewznab = onToggleNewznab,
            )
        }
        TextButton(onClick = onAdd) {
            Icon(
                imageVector = Icons.Filled.Add,
                contentDescription = null,
                modifier = Modifier.padding(end = 4.dp),
            )
            Text(stringResource(R.string.arr_prowlarr_settings_targets_add))
        }
    }
}

@Composable
private fun GrabTargetCard(
    index: Int,
    target: GrabTarget,
    categoryOptions: List<dev.appolon.homelab.arr.api.prowlarr.model.IndexerCategory>,
    onRemove: (Int) -> Unit,
    onCategoryChange: (Int, String) -> Unit,
    onToggleNewznab: (Int, Int) -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                value = target.clientCategory,
                onValueChange = { onCategoryChange(index, it) },
                label = { Text(stringResource(R.string.arr_prowlarr_settings_targets_category)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    stringResource(R.string.arr_prowlarr_settings_targets_newznab),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    categoryOptions.chunked(CHIP_ROW_COUNT).forEach { rowOptions ->
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            rowOptions.forEach { category ->
                                FilterChip(
                                    selected = category.id in target.categories,
                                    onClick = { onToggleNewznab(index, category.id) },
                                    label = {
                                        Text(
                                            category.name ?: category.id.toString(),
                                        )
                                    },
                                )
                            }
                        }
                    }
                    if (target.categories.isNotEmpty() && categoryOptions.isEmpty()) {
                        // Offline fallback: ids without names still render.
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            target.categories.forEach { id ->
                                FilterChip(
                                    selected = true,
                                    onClick = { onToggleNewznab(index, id) },
                                    label = { Text(id.toString()) },
                                )
                            }
                        }
                    }
                }
            }
            val removeCd = stringResource(
                R.string.arr_prowlarr_settings_targets_cd_remove,
                target.clientCategory.ifBlank { index.toString() },
            )
            TextButton(
                onClick = { onRemove(index) },
                modifier = Modifier.semantics { contentDescription = removeCd },
            ) {
                Text(
                    stringResource(ArrUiR.string.arr_common_queue_action_remove),
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

private const val CHIP_ROW_COUNT = 3
