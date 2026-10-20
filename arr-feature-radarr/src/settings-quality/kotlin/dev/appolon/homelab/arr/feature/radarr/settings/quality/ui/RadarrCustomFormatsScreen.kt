package dev.appolon.homelab.arr.feature.radarr.settings.quality.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.appolon.homelab.arr.api.radarr.model.CustomFormatResource
import dev.appolon.homelab.arr.api.radarr.model.Modifier as QualityModifier
import dev.appolon.homelab.arr.core.ServerId
import dev.appolon.homelab.arr.feature.radarr.R
import dev.appolon.homelab.arr.feature.radarr.settings.quality.RadarrQualityClients
import dev.appolon.homelab.arr.feature.radarr.settings.quality.data.CustomFormatCondition
import dev.appolon.homelab.arr.feature.radarr.settings.quality.data.CustomFormatConditionKind
import dev.appolon.homelab.arr.feature.radarr.settings.quality.vm.CustomFormatDraft
import dev.appolon.homelab.arr.feature.radarr.settings.quality.vm.CustomFormatsNotice
import dev.appolon.homelab.arr.feature.radarr.settings.quality.vm.FormatScoreRow
import dev.appolon.homelab.arr.feature.radarr.settings.quality.vm.RadarrCustomFormatEditorContent
import dev.appolon.homelab.arr.feature.radarr.settings.quality.vm.RadarrCustomFormatEditorViewModel
import dev.appolon.homelab.arr.feature.radarr.settings.quality.vm.RadarrCustomFormatsContent
import dev.appolon.homelab.arr.feature.radarr.settings.quality.vm.RadarrCustomFormatsViewModel
import dev.appolon.homelab.arr.ui.components.EmptyState
import dev.appolon.homelab.arr.ui.components.LabeledSwitch
import dev.appolon.homelab.arr.ui.lce.ArrLceBox
import dev.appolon.homelab.arr.ui.lce.ArrPullToRefreshBox
import dev.appolon.homelab.arr.ui.lce.LceState
import dev.appolon.homelab.arr.ui.shell.ArrScaffold

/** Destination for the custom formats list route. */
@Composable
fun RadarrCustomFormatsDestination(
    serverId: ServerId,
    clients: RadarrQualityClients,
    serverName: String? = null,
    onBack: (() -> Unit)? = null,
    onOpenFormat: (Int?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val repository = remember(serverId, clients) { qualityRepository(serverId, clients) }
    val vm: RadarrCustomFormatsViewModel =
        viewModel(key = "radarr-custom-formats-" + serverId.value) {
            RadarrCustomFormatsViewModel(repository)
        }
    val state by vm.state.collectAsState()
    val lce by vm.lce.collectAsState()

    RadarrCustomFormatsScreen(
        state = state,
        lce = lce,
        serverName = serverName,
        onBack = onBack,
        onRefresh = vm::refresh,
        onAdd = { onOpenFormat(null) },
        onOpenFormat = onOpenFormat,
        onRequestDelete = vm::requestDelete,
        onDeleteConfirmed = vm::deleteConfirmed,
        onConsumeNotice = vm::consumeNotice,
        onConsumeWriteError = vm::consumeWriteError,
        modifier = modifier,
    )
}

/** Destination for the custom format builder route. */
@Composable
fun RadarrCustomFormatEditorDestination(
    serverId: ServerId,
    formatId: Int?,
    clients: RadarrQualityClients,
    serverName: String? = null,
    onBack: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val repository = remember(serverId, clients) { qualityRepository(serverId, clients) }
    val vm: RadarrCustomFormatEditorViewModel = viewModel(
        key = "radarr-custom-format-editor-" + serverId.value + "-" + (formatId ?: "new"),
    ) {
        RadarrCustomFormatEditorViewModel(formatId, repository)
    }
    val state by vm.state.collectAsState()
    val lce by vm.lce.collectAsState()

    RadarrCustomFormatEditorScreen(
        state = state,
        lce = lce,
        serverName = serverName,
        onBack = onBack,
        onRefresh = vm::refresh,
        onEdit = vm::edit,
        onAddCondition = vm::addCondition,
        onEditCondition = vm::editCondition,
        onRemoveCondition = vm::removeCondition,
        onSetRegexTester = vm::setRegexTester,
        onSetScore = vm::setScore,
        onSave = vm::save,
        onConsumeSavedNotice = vm::consumeSavedNotice,
        onConsumeWriteError = vm::consumeWriteError,
        modifier = modifier,
    )
}

/** Custom formats list: rows open the builder; delete confirms. */
@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun RadarrCustomFormatsScreen(
    state: RadarrCustomFormatsViewModel.UiState,
    lce: LceState<RadarrCustomFormatsContent>,
    serverName: String?,
    onBack: (() -> Unit)?,
    onRefresh: () -> Unit,
    onAdd: () -> Unit,
    onOpenFormat: (Int) -> Unit,
    onRequestDelete: (Int?) -> Unit,
    onDeleteConfirmed: () -> Unit,
    onConsumeNotice: () -> Unit,
    onConsumeWriteError: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val content = (lce as? LceState.Content<RadarrCustomFormatsContent>)?.data
    val refreshing = content != null && (lce as LceState.Content<RadarrCustomFormatsContent>).refreshing

    val noticeMessage = when (state.notice) {
        CustomFormatsNotice.DELETED -> stringResource(R.string.arr_radarr_quality_notice_deleted)
        CustomFormatsNotice.QUEUED ->
            stringResource(R.string.arr_radarr_quality_notice_saved_queued)
        else -> null
    }
    QualityNoticeHost(
        noticeMessage = noticeMessage,
        writeError = state.writeError,
        snackbarHostState = snackbarHostState,
        onConsumeNotice = onConsumeNotice,
        onConsumeWriteError = onConsumeWriteError,
    )

    ArrScaffold(
        title = stringResource(R.string.arr_radarr_quality_custom_formats_title),
        subtitle = serverName,
        onBack = onBack,
        actions = {
            IconButton(onClick = onAdd) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = stringResource(R.string.arr_radarr_quality_cd_add_format),
                )
            }
            IconButton(onClick = onRefresh) {
                Icon(
                    imageVector = Icons.Filled.Refresh,
                    contentDescription = stringResource(R.string.arr_radarr_quality_cd_refresh),
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
            Box(Modifier.fillMaxSize()) {
                if (content != null && content.formats.isEmpty()) {
                    EmptyState(
                        title = stringResource(R.string.arr_radarr_quality_formats_empty_title),
                        message = stringResource(R.string.arr_radarr_quality_formats_empty_message),
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    ArrLceBox(state = lce, onRetry = onRefresh, modifier = Modifier.fillMaxSize()) { data ->
                        LazyColumn(
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                                horizontal = 16.dp,
                                vertical = 8.dp,
                            ),
                            modifier = Modifier.widthIn(max = SectionMaxWidth + 32.dp),
                        ) {
                            if (data.pendingActions > 0) {
                                item { QualityPendingSyncBanner(data.pendingActions) }
                            }
                            items(data.formats, key = { it.id }) { format ->
                                FormatRow(
                                    format = format,
                                    onOpen = { onOpenFormat(format.id) },
                                    onDelete = { onRequestDelete(format.id) },
                                )
                            }
                        }
                    }
                }
                SnackbarHost(
                    hostState = snackbarHostState,
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
            }
        }
    }

    if (state.confirmDeleteId != null) {
        AlertDialog(
            onDismissRequest = { onRequestDelete(null) },
            title = { Text(stringResource(R.string.arr_radarr_quality_delete_format_title)) },
            text = { Text(stringResource(R.string.arr_radarr_quality_delete_format_message)) },
            confirmButton = {
                TextButton(onClick = onDeleteConfirmed) {
                    Text(stringResource(R.string.arr_radarr_quality_action_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { onRequestDelete(null) }) {
                    Text(stringResource(R.string.arr_radarr_quality_action_cancel))
                }
            },
        )
    }
}

@Composable
private fun FormatRow(
    format: CustomFormatResource,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(onClickLabel = format.name.orEmpty(), onClick = onOpen)
            .padding(vertical = 12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(format.name.orEmpty(), style = MaterialTheme.typography.bodyLarge)
            Text(
                text = stringResource(
                    R.string.arr_radarr_quality_formats_row_support,
                    format.specifications.orEmpty().size,
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onDelete) {
            Icon(
                imageVector = Icons.Filled.Delete,
                contentDescription = stringResource(
                    R.string.arr_radarr_quality_cd_delete_format,
                    format.name.orEmpty(),
                ),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Custom format builder: name, include-when-renaming, the condition list
 * with per-kind editors and a regex tester, and the per-profile score rows.
 */
@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun RadarrCustomFormatEditorScreen(
    state: RadarrCustomFormatEditorViewModel.UiState,
    lce: LceState<RadarrCustomFormatEditorContent>,
    serverName: String?,
    onBack: (() -> Unit)?,
    onRefresh: () -> Unit,
    onEdit: (transform: (CustomFormatDraft) -> CustomFormatDraft) -> Unit,
    onAddCondition: (CustomFormatConditionKind) -> Unit,
    onEditCondition: (
        condition: CustomFormatCondition,
        transform: (CustomFormatCondition) -> CustomFormatCondition,
    ) -> Unit,
    onRemoveCondition: (CustomFormatCondition) -> Unit,
    onSetRegexTester: (String, String) -> Unit,
    onSetScore: (Int, Int) -> Unit,
    onSave: () -> Unit,
    onConsumeSavedNotice: () -> Unit,
    onConsumeWriteError: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val content = (lce as? LceState.Content<RadarrCustomFormatEditorContent>)?.data

    val savedMessage = when {
        state.savedNotice -> stringResource(R.string.arr_radarr_quality_notice_saved)
        state.savedQueued -> stringResource(R.string.arr_radarr_quality_notice_saved_queued)
        else -> null
    }
    QualityNoticeHost(
        noticeMessage = savedMessage,
        writeError = state.writeError,
        snackbarHostState = snackbarHostState,
        onConsumeNotice = onConsumeSavedNotice,
        onConsumeWriteError = onConsumeWriteError,
    )

    ArrScaffold(
        title = stringResource(
            if (state.draft.isExisting) {
                R.string.arr_radarr_quality_format_editor_title_edit
            } else {
                R.string.arr_radarr_quality_format_editor_title_new
            },
        ),
        subtitle = serverName,
        onBack = onBack,
        actions = {
            if (content?.dirty == true) {
                IconButton(onClick = onSave, enabled = !state.saving) {
                    Icon(
                        imageVector = Icons.Filled.Save,
                        contentDescription = stringResource(R.string.arr_radarr_quality_cd_save),
                    )
                }
            }
            IconButton(onClick = onRefresh) {
                Icon(
                    imageVector = Icons.Filled.Refresh,
                    contentDescription = stringResource(R.string.arr_radarr_quality_cd_refresh),
                )
            }
        },
        modifier = modifier,
    ) { padding ->
        ArrPullToRefreshBox(
            isRefreshing = false,
            onRefresh = onRefresh,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            Box(Modifier.fillMaxSize()) {
                ArrLceBox(state = lce, onRetry = onRefresh, modifier = Modifier.fillMaxSize()) { data ->
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .fillMaxSize()
                            .widthIn(max = SectionMaxWidth)
                            .verticalScroll(rememberScrollState())
                            .padding(16.dp),
                    ) {
                        if (data.pendingActions > 0) QualityPendingSyncBanner(data.pendingActions)
                        QualityTextField(
                            value = data.draft.name,
                            label = stringResource(R.string.arr_radarr_quality_format_name),
                            onValue = { text -> onEdit { it.copy(name = text) } },
                        )
                        LabeledSwitch(
                            label = stringResource(R.string.arr_radarr_quality_format_rename),
                            supportingText = stringResource(
                                R.string.arr_radarr_quality_format_rename_support,
                            ),
                            checked = data.draft.includeWhenRenaming,
                            onCheckedChange = { value ->
                                onEdit { it.copy(includeWhenRenaming = value) }
                            },
                        )
                        QualitySectionHeader(stringResource(R.string.arr_radarr_quality_format_section_conditions))
                        data.draft.conditions.forEach { condition ->
                            ConditionCard(
                                condition = condition,
                                regexPattern = data.regexPattern,
                                regexSample = data.regexSample,
                                onEditCondition = onEditCondition,
                                onRemove = onRemoveCondition,
                                onSetRegexTester = onSetRegexTester,
                            )
                        }
                        if (data.draft.passthroughConditions.isNotEmpty()) {
                            Text(
                                text = stringResource(
                                    R.string.arr_radarr_quality_format_passthrough,
                                    data.draft.passthroughConditions.size,
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        AddConditionRow(onAddCondition)
                        if (data.draft.isExisting && data.scoreRows.isNotEmpty()) {
                            QualitySectionHeader(
                                stringResource(R.string.arr_radarr_quality_format_section_scores),
                            )
                            data.scoreRows.forEach { row -> ScoreRow(row, onSetScore) }
                        }
                        Spacer(Modifier.heightIn(min = 24.dp))
                    }
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
private fun ConditionCard(
    condition: CustomFormatCondition,
    regexPattern: String,
    regexSample: String,
    onEditCondition: (
        condition: CustomFormatCondition,
        transform: (CustomFormatCondition) -> CustomFormatCondition,
    ) -> Unit,
    onRemove: (CustomFormatCondition) -> Unit,
    onSetRegexTester: (String, String) -> Unit,
) {
    QualityCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = conditionKindLabel(condition.kind),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = { onRemove(condition) }) {
                Icon(
                    imageVector = Icons.Filled.Delete,
                    contentDescription = stringResource(
                        R.string.arr_radarr_quality_cd_remove_condition,
                        conditionKindLabel(condition.kind),
                    ),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        LabeledSwitch(
            label = stringResource(R.string.arr_radarr_quality_condition_negate),
            checked = condition.negate,
            onCheckedChange = { value ->
                onEditCondition(condition) { it.copy(negate = value) }
            },
        )
        LabeledSwitch(
            label = stringResource(R.string.arr_radarr_quality_condition_required),
            checked = condition.required,
            onCheckedChange = { value ->
                onEditCondition(condition) { it.copy(required = value) }
            },
        )
        when (condition.kind) {
            CustomFormatConditionKind.RELEASE_NAME -> {
                QualityTextField(
                    value = condition.value,
                    label = stringResource(R.string.arr_radarr_quality_condition_regex),
                    supporting = stringResource(R.string.arr_radarr_quality_condition_regex_support),
                    onValue = { text ->
                        onEditCondition(condition) { it.copy(value = text) }
                        onSetRegexTester(text, regexSample)
                    },
                )
                RegexTester(
                    pattern = regexPattern,
                    sample = regexSample,
                    onSetRegexTester = onSetRegexTester,
                )
            }

            CustomFormatConditionKind.EDITION -> QualityTextField(
                value = condition.value,
                label = stringResource(R.string.arr_radarr_quality_condition_edition),
                onValue = { text -> onEditCondition(condition) { it.copy(value = text) } },
            )

            CustomFormatConditionKind.QUALITY_MODIFIER -> {
                // Modifier ids follow the wire enum's declaration order,
                // matching Radarr's QualityModifier value ids.
                val selected = QualityModifier.entries.firstOrNull {
                    it.ordinal.toString() == condition.value
                } ?: QualityModifier.NONE
                QualityDropdown(
                    label = stringResource(R.string.arr_radarr_quality_condition_quality_modifier),
                    selected = selected,
                    entries = QualityModifier.entries,
                    labelOf = { it.toString() },
                    onSelect = { choice ->
                        onEditCondition(condition) { it.copy(value = choice.ordinal.toString()) }
                    },
                )
            }

            CustomFormatConditionKind.SIZE_RANGE -> {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Column(Modifier.weight(1f)) {
                        QualityTextField(
                            value = condition.minSize,
                            label = stringResource(R.string.arr_radarr_quality_definitions_min),
                            onValue = { text ->
                                onEditCondition(condition) { it.copy(minSize = text) }
                            },
                        )
                    }
                    Column(Modifier.weight(1f)) {
                        QualityTextField(
                            value = condition.maxSize,
                            label = stringResource(R.string.arr_radarr_quality_definitions_max),
                            onValue = { text ->
                                onEditCondition(condition) { it.copy(maxSize = text) }
                            },
                        )
                    }
                }
                Text(
                    text = stringResource(R.string.arr_radarr_quality_condition_size_unit),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            else -> QualityTextField(
                value = condition.value,
                label = conditionNumericLabel(condition.kind),
                supporting = stringResource(R.string.arr_radarr_quality_condition_id_support),
                onValue = { text -> onEditCondition(condition) { it.copy(value = text) } },
            )
        }
    }
}

@Composable
private fun RegexTester(
    pattern: String,
    sample: String,
    onSetRegexTester: (String, String) -> Unit,
) {
    Column {
        QualityTextField(
            value = sample,
            label = stringResource(R.string.arr_radarr_quality_regex_tester_sample),
            onValue = { text -> onSetRegexTester(pattern, text) },
        )
        val status = androidx.compose.runtime.remember(pattern, sample) { regexStatus(pattern, sample) }
        val (text, color) = when (status) {
            RegexStatus.IDLE -> stringResource(R.string.arr_radarr_quality_regex_tester_idle) to
                MaterialTheme.colorScheme.onSurfaceVariant

            RegexStatus.INVALID -> stringResource(R.string.arr_radarr_quality_regex_tester_invalid) to
                MaterialTheme.colorScheme.error

            RegexStatus.MATCH -> stringResource(R.string.arr_radarr_quality_regex_tester_match) to
                MaterialTheme.colorScheme.tertiary

            RegexStatus.NO_MATCH -> stringResource(R.string.arr_radarr_quality_regex_tester_no_match) to
                MaterialTheme.colorScheme.error
        }
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            color = color,
        )
    }
}

private enum class RegexStatus { IDLE, INVALID, MATCH, NO_MATCH }

private fun regexStatus(pattern: String, sample: String): RegexStatus = when {
    pattern.isBlank() || sample.isBlank() -> RegexStatus.IDLE
    else -> when (
        dev.appolon.homelab.arr.feature.radarr.settings.quality.data.CustomFormatConditions
            .regexMatch(pattern, sample)
    ) {
        null -> RegexStatus.INVALID
        true -> RegexStatus.MATCH
        false -> RegexStatus.NO_MATCH
    }
}

@Composable
private fun AddConditionRow(onAddCondition: (CustomFormatConditionKind) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    androidx.compose.material3.OutlinedButton(
        onClick = { expanded = !expanded },
        modifier = Modifier.heightIn(min = 48.dp),
    ) {
        Text(stringResource(R.string.arr_radarr_quality_condition_add))
    }
    if (expanded) {
        CustomFormatConditionKind.entries.forEach { kind ->
            QualityLinkRow(
                title = conditionKindLabel(kind),
                onClick = {
                    expanded = false
                    onAddCondition(kind)
                },
            )
        }
    }
}

@Composable
private fun ScoreRow(row: FormatScoreRow, onSetScore: (Int, Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(row.profile.name.orEmpty(), style = MaterialTheme.typography.bodyMedium)
            Text(
                text = stringResource(
                    R.string.arr_radarr_quality_format_score_row_support,
                    row.score,
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(16.dp))
        QualityTextField(
            value = row.score.toString(),
            label = stringResource(R.string.arr_radarr_quality_profile_score_label),
            onValue = { text -> onSetScore(row.profile.id, text.toIntOrNull() ?: 0) },
            modifier = Modifier.width(120.dp),
        )
    }
}

@Composable
private fun conditionKindLabel(kind: CustomFormatConditionKind): String = when (kind) {
    CustomFormatConditionKind.RELEASE_NAME ->
        stringResource(R.string.arr_radarr_quality_condition_release_name)
    CustomFormatConditionKind.EDITION ->
        stringResource(R.string.arr_radarr_quality_condition_edition)
    CustomFormatConditionKind.LANGUAGE ->
        stringResource(R.string.arr_radarr_quality_condition_language)
    CustomFormatConditionKind.INDEXER_FLAG ->
        stringResource(R.string.arr_radarr_quality_condition_indexer_flag)
    CustomFormatConditionKind.QUALITY_MODIFIER ->
        stringResource(R.string.arr_radarr_quality_condition_quality_modifier)
    CustomFormatConditionKind.SIZE_RANGE ->
        stringResource(R.string.arr_radarr_quality_condition_size_range)
}

@Composable
private fun conditionNumericLabel(kind: CustomFormatConditionKind): String = when (kind) {
    CustomFormatConditionKind.LANGUAGE ->
        stringResource(R.string.arr_radarr_quality_condition_language)
    CustomFormatConditionKind.INDEXER_FLAG ->
        stringResource(R.string.arr_radarr_quality_condition_indexer_flag)
    CustomFormatConditionKind.QUALITY_MODIFIER ->
        stringResource(R.string.arr_radarr_quality_condition_quality_modifier)
    else -> ""
}
