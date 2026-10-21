package dev.appolon.homelab.arr.feature.sonarr.settings.system.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
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
import dev.appolon.homelab.arr.api.sonarr.model.AutoTaggingSpecificationSchema
import dev.appolon.homelab.arr.core.ArrError
import dev.appolon.homelab.arr.core.ServerId
import dev.appolon.homelab.arr.feature.sonarr.settings.connect.data.ConnectFieldRenderKind
import dev.appolon.homelab.arr.feature.sonarr.settings.connect.data.SonarrConnectFieldForms
import dev.appolon.homelab.arr.feature.sonarr.settings.system.SonarrSettingsSystemClients
import dev.appolon.homelab.arr.feature.sonarr.settings.system.data.SonarrAutoTaggingConditions
import dev.appolon.homelab.arr.feature.sonarr.settings.system.vm.AutoTaggingConditionDraft
import dev.appolon.homelab.arr.feature.sonarr.settings.system.vm.AutoTaggingDraft
import dev.appolon.homelab.arr.feature.sonarr.settings.system.vm.SonarrAutoTaggingEditorViewModel
import dev.appolon.homelab.arr.ui.components.LabeledSwitch
import dev.appolon.homelab.arr.ui.lce.ArrLceBox
import dev.appolon.homelab.arr.ui.lce.ArrPullToRefreshBox
import dev.appolon.homelab.arr.ui.lce.LceState
import dev.appolon.homelab.arr.ui.shell.ArrScaffold
import dev.appolon.homelab.arr.ui.R as ArrUiR

/**
 * Destination for the auto tagging editor route. A null [ruleId] opens the
 * blank draft for a new rule.
 */
@Composable
fun SonarrAutoTaggingEditorDestination(
    serverId: ServerId,
    ruleId: Int?,
    clients: SonarrSettingsSystemClients,
    serverName: String? = null,
    onBack: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val repository = remember(serverId, clients) { systemRepository(serverId, clients) }
    val vm: SonarrAutoTaggingEditorViewModel = viewModel(
        key = "sonarr-system-autotagging-editor-" + serverId.value + "-" + (ruleId ?: "new"),
    ) {
        SonarrAutoTaggingEditorViewModel(ruleId, repository)
    }
    val state by vm.state.collectAsState()
    val noticeMessage = state.notice?.let { autoTaggingNoticeText(it) }

    SonarrAutoTaggingEditorScreen(
        state = state,
        serverName = serverName,
        onBack = onBack,
        onRefresh = vm::refresh,
        onEdit = vm::edit,
        onToggleTag = vm::toggleTag,
        onAddCondition = vm::addCondition,
        onRemoveCondition = vm::removeCondition,
        onEditCondition = vm::editCondition,
        onSave = vm::save,
        noticeMessage = noticeMessage,
        writeError = state.writeError,
        onConsumeNotice = vm::consumeNotice,
        onConsumeWriteError = vm::consumeWriteError,
        modifier = modifier,
    )
}

/**
 * The auto tagging rule editor: name, remove-automatically toggle, applied
 * tag chips, and the condition list. Conditions are added from the static
 * Sonarr catalog picker and each condition's fields render by declared
 * type; unknown conditions loaded from the server round-trip untouched.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SonarrAutoTaggingEditorScreen(
    state: SonarrAutoTaggingEditorViewModel.UiState,
    serverName: String?,
    onBack: (() -> Unit)?,
    onRefresh: () -> Unit,
    onEdit: (transform: (AutoTaggingDraft) -> AutoTaggingDraft) -> Unit,
    onToggleTag: (Int) -> Unit,
    onAddCondition: (AutoTaggingSpecificationSchema) -> Unit,
    onRemoveCondition: (Int) -> Unit,
    onEditCondition: (Int, transform: (AutoTaggingConditionDraft) -> AutoTaggingConditionDraft) -> Unit,
    onSave: () -> Unit,
    noticeMessage: String?,
    writeError: ArrError?,
    onConsumeNotice: () -> Unit,
    onConsumeWriteError: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val lce = if (state.loaded) {
        LceState.Content(state)
    } else {
        LceState.Loading
    }

    SystemNoticeHost(
        noticeMessage = noticeMessage,
        writeError = writeError,
        snackbarHostState = snackbarHostState,
        onConsumeNotice = onConsumeNotice,
        onConsumeWriteError = onConsumeWriteError,
    )

    ArrScaffold(
        title = stringResource(
            if (state.isNew) {
                ArrUiR.string.arr_common_autotag_editor_title_new
            } else {
                ArrUiR.string.arr_common_autotag_editor_title_edit
            },
        ),
        subtitle = serverName,
        onBack = onBack,
        actions = {
            IconButton(onClick = onRefresh) {
                Icon(
                    imageVector = Icons.Filled.Refresh,
                    contentDescription = stringResource(ArrUiR.string.arr_common_system_cd_refresh),
                )
            }
            IconButton(onClick = onSave, enabled = state.dirty && !state.saving) {
                Icon(
                    imageVector = Icons.Filled.Save,
                    contentDescription = stringResource(ArrUiR.string.arr_common_save_changes),
                )
            }
        },
        modifier = modifier,
    ) { padding ->
        ArrPullToRefreshBox(
            isRefreshing = state.refreshing,
            onRefresh = onRefresh,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            Box(Modifier.fillMaxSize()) {
                ArrLceBox(
                    state = lce,
                    onRetry = onRefresh,
                    modifier = Modifier.fillMaxSize(),
                ) { data ->
                    EditorBody(
                        state = data,
                        onEdit = onEdit,
                        onToggleTag = onToggleTag,
                        onAddCondition = onAddCondition,
                        onRemoveCondition = onRemoveCondition,
                        onEditCondition = onEditCondition,
                    )
                }
                SnackbarHost(
                    hostState = snackbarHostState,
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
            }
        }
    }
}

@Composable
private fun EditorBody(
    state: SonarrAutoTaggingEditorViewModel.UiState,
    onEdit: (transform: (AutoTaggingDraft) -> AutoTaggingDraft) -> Unit,
    onToggleTag: (Int) -> Unit,
    onAddCondition: (AutoTaggingSpecificationSchema) -> Unit,
    onRemoveCondition: (Int) -> Unit,
    onEditCondition: (Int, transform: (AutoTaggingConditionDraft) -> AutoTaggingConditionDraft) -> Unit,
) {
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.widthIn(max = SectionMaxWidth + 32.dp),
    ) {
        if (state.pendingActions > 0) {
            item { SystemPendingSyncBanner(state.pendingActions) }
        }
        item {
            SystemCard {
                Column {
                    SystemTextField(
                        value = state.draft.name,
                        label = stringResource(ArrUiR.string.arr_common_autotag_name),
                        onValue = { value -> onEdit { it.copy(name = value) } },
                        isError = state.nameError,
                        errorMessage = stringResource(ArrUiR.string.arr_common_tags_label_error),
                    )
                    LabeledSwitch(
                        label = stringResource(ArrUiR.string.arr_common_autotag_remove_automatically),
                        supportingText = stringResource(
                            ArrUiR.string.arr_common_autotag_remove_automatically_support,
                        ),
                        checked = state.draft.removeTagsAutomatically,
                        onCheckedChange = { checked ->
                            onEdit { it.copy(removeTagsAutomatically = checked) }
                        },
                    )
                }
            }
        }
        item {
            SystemCard {
                Column {
                    SystemSectionHeader(stringResource(ArrUiR.string.arr_common_autotag_section_tags))
                    if (state.tags.isEmpty()) {
                        Text(
                            text = stringResource(ArrUiR.string.arr_common_autotag_no_tags_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    FlowChipRow(
                        chips = state.tags.map { it.id to (it.label.orEmpty()) },
                        selectedIds = state.draft.tagIds,
                        onToggle = onToggleTag,
                    )
                }
            }
        }
        item {
            SystemCard {
                Column {
                    SystemSectionHeader(stringResource(ArrUiR.string.arr_common_autotag_section_conditions))
                    ConditionAddRow(SonarrAutoTaggingConditions.entries, onAddCondition)
                }
            }
        }
        itemsIndexed(
            state.draft.conditions,
            key = { _, condition -> condition.hashCode() },
            contentType = { _, _ -> "condition" },
        ) { index, condition ->
            ConditionCard(
                index = index,
                condition = condition,
                onRemoveCondition = onRemoveCondition,
                onEditCondition = onEditCondition,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConditionAddRow(
    catalog: List<AutoTaggingSpecificationSchema>,
    onAddCondition: (AutoTaggingSpecificationSchema) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(vertical = 4.dp),
    ) {
        FilterChip(
            selected = expanded,
            onClick = { expanded = !expanded },
            label = {
                Text(stringResource(ArrUiR.string.arr_common_autotag_condition_add))
            },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = null,
                )
            },
        )
    }
    if (expanded) {
        Column {
            catalog.forEach { entry ->
                SystemLinkRow(
                    title = entry.implementationName ?: entry.name ?: entry.implementation.orEmpty(),
                    onClick = {
                        onAddCondition(entry)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun ConditionCard(
    index: Int,
    condition: AutoTaggingConditionDraft,
    onRemoveCondition: (Int) -> Unit,
    onEditCondition: (Int, transform: (AutoTaggingConditionDraft) -> AutoTaggingConditionDraft) -> Unit,
) {
    val title = condition.specification.implementationName
        ?: condition.specification.name
        ?: condition.specification.implementation
        ?: ""
    SystemCard {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.AutoAwesome,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 12.dp),
                )
                IconButton(onClick = { onRemoveCondition(index) }) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = stringResource(
                            ArrUiR.string.arr_common_autotag_cd_remove_condition,
                            title,
                        ),
                    )
                }
            }
            LabeledSwitch(
                label = stringResource(ArrUiR.string.arr_common_autotag_condition_negate),
                checked = condition.specification.negate,
                onCheckedChange = { checked ->
                    onEditCondition(index) { it.copy(specification = it.specification.copy(negate = checked)) }
                },
            )
            LabeledSwitch(
                label = stringResource(ArrUiR.string.arr_common_autotag_condition_required),
                checked = condition.specification.required,
                onCheckedChange = { checked ->
                    onEditCondition(index) { it.copy(specification = it.specification.copy(required = checked)) }
                },
            )
            condition.specification.fields.orEmpty().forEach { field ->
                val name = field.name ?: return@forEach
                val value = condition.fieldValues[name]
                if (SonarrConnectFieldForms.renderKind(field) == ConnectFieldRenderKind.CHECKBOX) {
                    LabeledSwitch(
                        label = field.label ?: name,
                        checked = SonarrConnectFieldForms.boolOf(value),
                        onCheckedChange = { checked ->
                            onEditCondition(index) {
                                it.copy(
                                    fieldValues = it.fieldValues +
                                        (name to SonarrConnectFieldForms.bool(checked)),
                                )
                            }
                        },
                    )
                } else {
                    SystemTextField(
                        value = SonarrConnectFieldForms.textOf(value),
                        label = field.label ?: name,
                        onValue = { text ->
                            onEditCondition(index) {
                                it.copy(
                                    fieldValues = it.fieldValues +
                                        (name to SonarrConnectFieldForms.text(text)),
                                )
                            }
                        },
                    )
                }
            }
        }
    }
}

/** Simple wrapping chip row for the applied tags. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FlowChipRow(
    chips: List<Pair<Int, String>>,
    selectedIds: Set<Int>,
    onToggle: (Int) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        chips.chunked(3).forEach { rowChips ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                rowChips.forEach { (id, label) ->
                    FilterChip(
                        selected = id in selectedIds,
                        onClick = { onToggle(id) },
                        label = { Text(label) },
                        modifier = Modifier.padding(vertical = 4.dp),
                    )
                }
            }
        }
    }
}
