package dev.appolon.homelab.arr.feature.prowlarr.indexers.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Language
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.appolon.homelab.arr.api.prowlarr.model.AppProfileResource
import dev.appolon.homelab.arr.api.prowlarr.model.IndexerCategory
import dev.appolon.homelab.arr.api.prowlarr.model.IndexerResource
import dev.appolon.homelab.arr.feature.prowlarr.R
import dev.appolon.homelab.arr.feature.prowlarr.indexers.data.ProtocolFilter
import dev.appolon.homelab.arr.feature.prowlarr.indexers.vm.IndexerEditorMessage
import dev.appolon.homelab.arr.feature.prowlarr.indexers.vm.IndexerEditorUiState
import dev.appolon.homelab.arr.ui.components.LabeledSwitch
import dev.appolon.homelab.arr.ui.components.SectionHeader
import dev.appolon.homelab.arr.ui.lce.ArrLceBox
import dev.appolon.homelab.arr.ui.lce.LceState
import dev.appolon.homelab.arr.ui.lce.arrErrorMessage
import dev.appolon.homelab.arr.ui.shell.ArrScaffold
import kotlinx.serialization.json.JsonElement
import dev.appolon.homelab.arr.ui.R as ArrUiR

/**
 * Add and edit indexer screen. Add mode starts at the prescreened
 * definition picker; both modes share the dynamic field form, the seed
 * criteria section, and the categories editor. Wide screens put the form
 * and the category tree side by side.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun IndexerEditorScreen(
    state: IndexerEditorUiState,
    onBack: () -> Unit,
    onSchemaSearchChange: (String) -> Unit,
    onSchemaProtocolChange: (ProtocolFilter) -> Unit,
    onPickSchema: (IndexerResource) -> Unit,
    onBackToPicker: () -> Unit,
    onFieldValueChange: (String, JsonElement) -> Unit,
    onNameChange: (String) -> Unit,
    onEnableChange: (Boolean) -> Unit,
    onPriorityChange: (Int) -> Unit,
    onAppProfileChange: (Int?) -> Unit,
    onToggleTag: (String) -> Unit,
    onToggleCategory: (Int) -> Unit,
    onApplyPreset: (Int) -> Unit,
    onToggleAdvanced: () -> Unit,
    onTest: () -> Unit,
    onSave: () -> Unit,
    onConsumeMessage: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ArrScaffold(
        title = stringResource(
            if (state.editing != null) ArrUiR.string.arr_common_indexer_editor_title
            else ArrUiR.string.arr_common_indexers_add,
        ),
        onBack = if (state.editing == null && !state.pickingSchema) onBackToPicker else onBack,
        actions = {
            if (!state.pickingSchema && !state.loading && state.loadError == null) {
                val testingCd = stringResource(R.string.arr_prowlarr_cd_testing)
                TextButton(onClick = onTest, enabled = !state.testing) {
                    if (state.testing) {
                        CircularProgressIndicator(
                            Modifier
                                .heightIn(max = 16.dp)
                                .semantics { contentDescription = testingCd },
                        )
                    } else {
                        Text(stringResource(ArrUiR.string.arr_common_connect_test))
                    }
                }
                val savingCd = stringResource(R.string.arr_prowlarr_cd_saving)
                TextButton(onClick = onSave, enabled = !state.saving) {
                    if (state.saving) {
                        CircularProgressIndicator(
                            Modifier
                                .heightIn(max = 16.dp)
                                .semantics { contentDescription = savingCd },
                        )
                    } else {
                        Text(stringResource(ArrUiR.string.arr_common_save))
                    }
                }
            }
        },
        modifier = modifier,
    ) { padding ->
        when {
            state.loading -> Box(
                Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                val loadingCd = stringResource(R.string.arr_prowlarr_cd_loading)
                CircularProgressIndicator(
                    Modifier.semantics { contentDescription = loadingCd },
                )
            }

            state.loadError != null -> ArrLceBox(
                state = LceState.Error(state.loadError),
                onRetry = onRetry,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            ) { _ -> }

            state.pickingSchema -> SchemaPicker(
                state = state,
                onSchemaSearchChange = onSchemaSearchChange,
                onSchemaProtocolChange = onSchemaProtocolChange,
                onPickSchema = onPickSchema,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            )

            else -> EditorForm(
                state = state,
                onFieldValueChange = onFieldValueChange,
                onNameChange = onNameChange,
                onEnableChange = onEnableChange,
                onPriorityChange = onPriorityChange,
                onAppProfileChange = onAppProfileChange,
                onToggleTag = onToggleTag,
                onToggleCategory = onToggleCategory,
                onApplyPreset = onApplyPreset,
                onToggleAdvanced = onToggleAdvanced,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            )
        }
    }

    val messageText = state.message?.let { editorMessageText(it) }
    LaunchedEffect(messageText, state.saving, state.testing) {
        if (messageText != null && !state.saving && !state.testing) {
            onConsumeMessage()
        }
    }
    if (messageText != null && !state.saving && !state.testing) {
        AlertDialog(
            onDismissRequest = onConsumeMessage,
            title = { Text(stringResource(ArrUiR.string.arr_common_releases_details_indexer)) },
            text = { Text(messageText) },
            confirmButton = {
                TextButton(onClick = onConsumeMessage) {
                    Text(stringResource(R.string.arr_prowlarr_ok))
                }
            },
        )
    }
}

@Composable
private fun SchemaPicker(
    state: IndexerEditorUiState,
    onSchemaSearchChange: (String) -> Unit,
    onSchemaProtocolChange: (ProtocolFilter) -> Unit,
    onPickSchema: (IndexerResource) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        OutlinedTextField(
            value = state.schemaSearch,
            onValueChange = onSchemaSearchChange,
            label = { Text(stringResource(R.string.arr_prowlarr_editor_search_schema)) },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            ProtocolFilter.entries.forEach { filter ->
                FilterChip(
                    selected = state.schemaProtocol == filter,
                    onClick = { onSchemaProtocolChange(filter) },
                    label = { Text(protocolFilterLabel(filter)) },
                )
            }
        }
        if (state.filteredSchemas.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.arr_prowlarr_editor_no_schemas))
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(state.filteredSchemas, key = { it.definitionName ?: it.name ?: it.id }) { schema ->
                    SchemaCard(schema = schema, onPick = { onPickSchema(schema) })
                }
            }
        }
    }
}


@Composable
private fun SchemaCard(
    schema: IndexerResource,
    onPick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val cd = stringResource(ArrUiR.string.arr_common_add_cd_open_add, schema.name.orEmpty())
    ElevatedCard(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 72.dp)
            .clickable(onClick = onPick)
            .semantics { contentDescription = cd },
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(schema.name.orEmpty(), style = MaterialTheme.typography.titleMedium)
            if (!schema.description.isNullOrBlank()) {
                Text(
                    schema.description.orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AssistChip(
                    onClick = {},
                    enabled = false,
                    label = { Text(privacyLabel(schema.privacy)) },
                )
                AssistChip(
                    onClick = {},
                    enabled = false,
                    label = { Text(protocolLabel(schema.protocol)) },
                )
                if (schema.supportsRss) {
                    AssistChip(
                        onClick = {},
                        enabled = false,
                        label = { Text(stringResource(ArrUiR.string.arr_common_indexers_mode_rss)) },
                    )
                }
            }
        }
    }
}

@Composable
private fun EditorForm(
    state: IndexerEditorUiState,
    onFieldValueChange: (String, JsonElement) -> Unit,
    onNameChange: (String) -> Unit,
    onEnableChange: (Boolean) -> Unit,
    onPriorityChange: (Int) -> Unit,
    onAppProfileChange: (Int?) -> Unit,
    onToggleTag: (String) -> Unit,
    onToggleCategory: (Int) -> Unit,
    onApplyPreset: (Int) -> Unit,
    onToggleAdvanced: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier) {
        val twoPane = maxWidth >= 840.dp
        if (twoPane) {
            Row(Modifier.fillMaxSize().focusGroup()) {
                Column(
                    Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    BasicsSection(
                        state = state,
                        onNameChange = onNameChange,
                        onEnableChange = onEnableChange,
                        onPriorityChange = onPriorityChange,
                        onAppProfileChange = onAppProfileChange,
                        onToggleTag = onToggleTag,
                        onApplyPreset = onApplyPreset,
                    )
                    DynamicSection(
                        state = state,
                        onFieldValueChange = onFieldValueChange,
                        onToggleAdvanced = onToggleAdvanced,
                    )
                }
                Column(
                    Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                ) {
                    CategoriesSection(
                        state = state,
                        onToggleCategory = onToggleCategory,
                    )
                }
            }
        } else {
            Column(
                Modifier
                    .focusGroup()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                BasicsSection(
                    state = state,
                    onNameChange = onNameChange,
                    onEnableChange = onEnableChange,
                    onPriorityChange = onPriorityChange,
                    onAppProfileChange = onAppProfileChange,
                    onToggleTag = onToggleTag,
                    onApplyPreset = onApplyPreset,
                )
                DynamicSection(
                    state = state,
                    onFieldValueChange = onFieldValueChange,
                    onToggleAdvanced = onToggleAdvanced,
                )
                CategoriesSection(
                    state = state,
                    onToggleCategory = onToggleCategory,
                )
            }
        }
    }
}

@Composable
private fun BasicsSection(
    state: IndexerEditorUiState,
    onNameChange: (String) -> Unit,
    onEnableChange: (Boolean) -> Unit,
    onPriorityChange: (Int) -> Unit,
    onAppProfileChange: (Int?) -> Unit,
    onToggleTag: (String) -> Unit,
    onApplyPreset: (Int) -> Unit,
) {
    val uriHandler = LocalUriHandler.current
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(
            value = state.name,
            onValueChange = onNameChange,
            label = { Text(stringResource(ArrUiR.string.arr_common_autotag_name)) },
            isError = state.nameMissing,
            supportingText = if (state.nameMissing) {
                { Text(stringResource(ArrUiR.string.arr_common_tags_label_error)) }
            } else {
                null
            },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        LabeledSwitch(
            label = stringResource(ArrUiR.string.arr_common_general_cert_validation_enabled),
            checked = state.enable,
            onCheckedChange = onEnableChange,
        )
        var priorityText by remember(state.priority) {
            mutableStateOf(state.priority.toString())
        }
        OutlinedTextField(
            value = priorityText,
            onValueChange = { text ->
                priorityText = text
                text.trim().toIntOrNull()?.let(onPriorityChange)
            },
            label = { Text(stringResource(ArrUiR.string.arr_common_connect_priority_label)) },
            isError = priorityText.toIntOrNull() == null,
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        AppProfileSelect(
            profiles = state.appProfiles,
            selected = state.appProfileId,
            onSelect = onAppProfileChange,
        )
        TagLabelPicker(
            allTags = state.tagOptions,
            selected = state.tagLabels,
            onToggle = onToggleTag,
        )
        state.infoLink?.let { link ->
            TextButton(onClick = { runCatching { uriHandler.openUri(link) } }) {
                Icon(
                    imageVector = Icons.Filled.Language,
                    contentDescription = null,
                    modifier = Modifier.padding(end = 8.dp),
                )
                Text(stringResource(R.string.arr_prowlarr_editor_info_link))
            }
        }
        if (state.presets.isNotEmpty()) {
            SectionHeader(
                title = stringResource(R.string.arr_prowlarr_editor_presets),
                modifier = Modifier.semantics { heading() },
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                state.presets.forEachIndexed { index, preset ->
                    FilterChip(
                        selected = false,
                        onClick = { onApplyPreset(index) },
                        label = { Text(preset.label) },
                    )
                }
            }
        }
        SectionHeader(
            title = state.implementationName,
            modifier = Modifier.semantics { heading() },
        )
    }
}

@Composable
private fun DynamicSection(
    state: IndexerEditorUiState,
    onFieldValueChange: (String, JsonElement) -> Unit,
    onToggleAdvanced: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        FieldEditors(
            fields = state.generalFields,
            values = state.values,
            onValueChange = onFieldValueChange,
            tagOptions = state.tagOptions,
            showAdvanced = state.showAdvanced,
            onToggleAdvanced = onToggleAdvanced,
        )
        if (state.seedCriteriaFields.isNotEmpty()) {
            SectionHeader(
                title = stringResource(ArrUiR.string.arr_common_connect_section_seed_criteria),
                modifier = Modifier.semantics { heading() },
            )
            FieldEditors(
                fields = state.seedCriteriaFields,
                values = state.values,
                onValueChange = onFieldValueChange,
                tagOptions = state.tagOptions,
                showAdvanced = true,
            )
        }
    }
}

/** Category tree editor: checking a parent checks its whole subtree. */
@Composable
private fun CategoriesSection(
    state: IndexerEditorUiState,
    onToggleCategory: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        SectionHeader(
            title = stringResource(R.string.arr_prowlarr_editor_categories),
            modifier = Modifier.semantics { heading() },
        )
        state.defaultCategories.forEach { category ->
            CategoryNode(
                category = category,
                selected = state.selectedCategories,
                onToggleCategory = onToggleCategory,
                depth = 0,
            )
        }
    }
}

@Composable
private fun CategoryNode(
    category: IndexerCategory,
    selected: Set<Int>,
    onToggleCategory: (Int) -> Unit,
    depth: Int,
) {
    var expanded by remember(category.id) { mutableStateOf(false) }
    val hasChildren = !category.subCategories.isNullOrEmpty()
    val rowCd = stringResource(
        R.string.arr_prowlarr_cd_toggle_category,
        category.name.orEmpty(),
    )
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .semantics { contentDescription = rowCd },
    ) {
        Checkbox(
            checked = category.id in selected,
            onCheckedChange = { onToggleCategory(category.id) },
        )
        Text(
                            stringResource(
                                ArrUiR.string.arr_common_metadata_pair,
                                category.id.toString(),
                                category.name.orEmpty(),
                            ),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier
                .weight(1f)
                .padding(start = 4.dp),
        )
        if (hasChildren) {
            val expandCd = stringResource(
                if (expanded) R.string.arr_prowlarr_cd_collapse_category
                else R.string.arr_prowlarr_cd_expand_category,
            )
            IconButton(onClick = { expanded = !expanded }) {
                Icon(
                    imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = expandCd,
                )
            }
        }
    }
    if (expanded) {
        category.subCategories.orEmpty().forEach { child ->
            Row(Modifier.padding(start = ((depth + 1) * 16).dp)) {
                CategoryNode(
                    category = child,
                    selected = selected,
                    onToggleCategory = onToggleCategory,
                    depth = depth + 1,
                )
            }
        }
    }
}

/** Dropdown over app profiles with an explicit unset option. */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun AppProfileSelect(
    profiles: List<AppProfileResource>,
    selected: Int?,
    onSelect: (Int?) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = Modifier.fillMaxWidth(),
    ) {
        val selectedLabel = selected
            ?.let { id -> profiles.firstOrNull { it.id == id }?.name ?: "#$id" }
            .orEmpty()
        OutlinedTextField(
            value = selectedLabel,
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(R.string.arr_prowlarr_bulk_app_profile)) },
            trailingIcon = {
                ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
            },
            modifier = Modifier
                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth(),
        )
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            DropdownMenuItem(
                text = { Text(stringResource(ArrUiR.string.arr_common_general_auth_method_none)) },
                onClick = {
                    expanded = false
                    onSelect(null)
                },
            )
            profiles.forEach { profile ->
                DropdownMenuItem(
                    text = { Text(profile.name.orEmpty()) },
                    onClick = {
                        expanded = false
                        onSelect(profile.id)
                    },
                )
            }
        }
    }
}

/** Dialog text for an editor message. */
@Composable
internal fun editorMessageText(message: IndexerEditorMessage): String = when (message) {
    IndexerEditorMessage.Saved -> stringResource(ArrUiR.string.arr_common_connect_notice_saved)
    IndexerEditorMessage.SavedQueued ->
        stringResource(R.string.arr_prowlarr_message_saved_queued)

    IndexerEditorMessage.TestPassed ->
        stringResource(R.string.arr_prowlarr_message_test_passed)

    is IndexerEditorMessage.TestFailed ->
        stringResource(R.string.arr_prowlarr_message_test_failed, arrErrorMessage(message.error))

    is IndexerEditorMessage.WriteFailed ->
        stringResource(R.string.arr_prowlarr_message_write_failed, arrErrorMessage(message.error))

    IndexerEditorMessage.NameMissing ->
        stringResource(ArrUiR.string.arr_common_tags_label_error)
}
