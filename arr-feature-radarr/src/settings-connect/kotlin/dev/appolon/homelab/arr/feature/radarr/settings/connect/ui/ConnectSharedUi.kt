package dev.appolon.homelab.arr.feature.radarr.settings.connect.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import dev.appolon.homelab.arr.api.radarr.model.Field
import dev.appolon.homelab.arr.api.radarr.model.ProviderTestAllResult
import dev.appolon.homelab.arr.api.radarr.model.TagResource
import dev.appolon.homelab.arr.feature.radarr.R
import dev.appolon.homelab.arr.feature.radarr.settings.connect.data.ConnectFieldForms
import dev.appolon.homelab.arr.feature.radarr.settings.connect.data.ConnectFieldRenderKind
import dev.appolon.homelab.arr.ui.components.LabeledSwitch
import dev.appolon.homelab.arr.ui.components.SectionHeader
import kotlinx.serialization.json.JsonElement
import dev.appolon.homelab.arr.ui.R as ArrUiR

/** Chip marking rows with queued offline writes. */
@Composable
fun ConnectQueuedBadge(modifier: Modifier = Modifier) {
    val description = stringResource(ArrUiR.string.arr_common_system_cd_queued_indicator)
    AssistChip(
        onClick = {},
        enabled = false,
        modifier = modifier.semantics { contentDescription = description },
        label = { Text(stringResource(ArrUiR.string.arr_common_connect_badge_queued)) },
    )
}

/**
 * Renders the per-provider verdict of a test-all run. Failures list the
 * server's validation messages under the row.
 */
@Composable
fun TestResultBadge(result: ProviderTestAllResult, modifier: Modifier = Modifier) {
    val passText = stringResource(R.string.arr_radarr_connect_test_pass)
    val failText = stringResource(R.string.arr_radarr_connect_test_fail)
    val label = if (result.isValid) passText else failText
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Icon(
            imageVector = if (result.isValid) Icons.Filled.CheckCircle else Icons.Filled.Error,
            contentDescription = label,
            tint = if (result.isValid) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.error
            },
            modifier = Modifier.size(18.dp),
        )
        if (!result.isValid) {
            Text(
                text = result.validationFailures.orEmpty()
                    .mapNotNull { it.errorMessage }
                    .joinToString()
                    .ifEmpty { label },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

/** Tag chips resolved against the cached tag list. */
@Composable
fun ConnectTagChips(tags: List<Int>, allTags: List<TagResource>, modifier: Modifier = Modifier) {
    if (tags.isEmpty()) return
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        tags.forEach { id ->
            val label = allTags.firstOrNull { it.id == id }?.label ?: "#$id"
            AssistChip(onClick = {}, enabled = false, label = { Text(label) })
        }
    }
}

/**
 * Renders the dynamic provider fields from the generic Field model,
 * mirroring the committed Prowlarr indexers renderer: fields split by
 * their declared section, advanced fields behind a toggle, categories
 * and seed criteria rendered as chip multi-selects and number fields.
 */
@Composable
fun ConnectFieldEditors(
    fields: List<Field>,
    values: Map<String, JsonElement>,
    onValueChange: (String, JsonElement) -> Unit,
    tagOptions: List<TagResource>,
    modifier: Modifier = Modifier,
    showAdvanced: Boolean = false,
    onToggleAdvanced: (() -> Unit)? = null,
) {
    val visible = fields.filter { showAdvanced || !it.advanced }
    val sections = visible.groupBy { it.section.orEmpty() }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        sections[""].orEmpty().forEach { field ->
            ConnectFieldEditor(field, values, onValueChange, tagOptions)
        }
        sections.keys.filter { it.isNotEmpty() }.sorted().forEach { section ->
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SectionHeader(title = sectionTitle(section))
                sections.getValue(section).forEach { field ->
                    ConnectFieldEditor(field, values, onValueChange, tagOptions)
                }
            }
        }
        if (fields.any { it.advanced } && onToggleAdvanced != null) {
            TextButton(onClick = onToggleAdvanced) {
                Text(
                    stringResource(
                        if (showAdvanced) ArrUiR.string.arr_common_connect_fields_hide_advanced
                        else ArrUiR.string.arr_common_connect_fields_show_advanced,
                    ),
                )
            }
        }
    }
}

/** The seed criteria section label, so the wire name reads as English. */
@Composable
private fun sectionTitle(section: String): String = when (section) {
    "seedCriteria" -> stringResource(ArrUiR.string.arr_common_connect_section_seed_criteria)
    else -> section
}

/** One field editor, dispatching on the rendered kind. */
@Composable
fun ConnectFieldEditor(
    field: Field,
    values: Map<String, JsonElement>,
    onValueChange: (String, JsonElement) -> Unit,
    tagOptions: List<TagResource>,
    modifier: Modifier = Modifier,
) {
    val name = field.name ?: return
    val element = values[name]
    val uriHandler = LocalUriHandler.current
    Column(modifier) {
        when (ConnectFieldForms.renderKind(field)) {
            ConnectFieldRenderKind.CHECKBOX -> LabeledSwitch(
                label = field.label ?: name,
                checked = ConnectFieldForms.boolOf(element),
                onCheckedChange = { onValueChange(name, ConnectFieldForms.bool(it)) },
                supportingText = field.helpText,
            )

            ConnectFieldRenderKind.SELECT -> ConnectSelectField(
                field = field,
                selected = ConnectFieldForms.intOf(element),
                onSelect = { onValueChange(name, ConnectFieldForms.int(it)) },
            )

            ConnectFieldRenderKind.TAG_SELECT -> ConnectIdSelectField(
                field = field,
                selected = ConnectFieldForms.idsOf(element),
                options = field.selectOptions.orEmpty().sortedBy { it.order },
                onChange = { onValueChange(name, ConnectFieldForms.ids(it)) },
            )

            ConnectFieldRenderKind.PASSWORD -> ConnectPasswordField(
                field = field,
                value = ConnectFieldForms.textOf(element),
                onChange = { onValueChange(name, ConnectFieldForms.text(it)) },
            )

            ConnectFieldRenderKind.NUMBER -> ConnectNumberField(
                field = field,
                element = element,
                onChange = { onValueChange(name, it) },
            )

            ConnectFieldRenderKind.TEXT -> OutlinedTextField(
                value = ConnectFieldForms.textOf(element),
                onValueChange = { onValueChange(name, ConnectFieldForms.text(it)) },
                label = { Text(field.label ?: name) },
                supportingText = field.helpText?.let { text -> { Text(text) } },
                placeholder = field.placeholder?.let { text -> { Text(text) } },
                suffix = field.unit?.let { unit -> { Text(unit) } },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        field.helpTextWarning?.let { warning ->
            Text(
                warning,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(start = 4.dp),
            )
        }
        field.helpLink?.let { link ->
            TextButton(onClick = { runCatching { uriHandler.openUri(link) } }) {
                Text(stringResource(ArrUiR.string.arr_common_connect_fields_help_link))
            }
        }
    }
}

/** Dropdown over the field's declared select options. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConnectSelectField(
    field: Field,
    selected: Int?,
    onSelect: (Int?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val options = field.selectOptions.orEmpty().sortedBy { it.order }
    val selectedOption = options.firstOrNull { it.value == selected }
    var expanded by rememberSaveable { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier.fillMaxWidth(),
    ) {
        OutlinedTextField(
            value = selectedOption?.name ?: "",
            onValueChange = {},
            readOnly = true,
            label = { Text(field.label ?: field.name.orEmpty()) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            supportingText = field.helpText?.let { text -> { Text(text) } },
            modifier = Modifier
                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth(),
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.name ?: option.value.toString()) },
                    onClick = {
                        onSelect(option.value)
                        expanded = false
                    },
                )
            }
        }
    }
}

/**
 * Multi-select over the field's declared options: categories pickers and
 * provider tag selects both carry id lists on the wire.
 */
@Composable
fun ConnectIdSelectField(
    field: Field,
    selected: List<Int>,
    options: List<dev.appolon.homelab.arr.api.radarr.model.SelectOption>,
    onChange: (List<Int>) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        Text(
            field.label ?: field.name.orEmpty(),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(bottom = 4.dp),
        )
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            options.chunked(CHIP_ROW_COUNT).forEach { rowOptions ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    rowOptions.forEach { option ->
                        FilterChip(
                            selected = option.value in selected,
                            onClick = {
                                onChange(
                                    if (option.value in selected) {
                                        selected - option.value
                                    } else {
                                        selected + option.value
                                    },
                                )
                            },
                            label = { Text(option.name ?: option.value.toString()) },
                        )
                    }
                }
            }
        }
        field.helpText?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
    }
}

/** Secret field that stays masked until the eye is tapped. */
@Composable
fun ConnectPasswordField(
    field: Field,
    value: String,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var visible by rememberSaveable { mutableStateOf(false) }
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(field.label ?: field.name.orEmpty()) },
        supportingText = field.helpText?.let { text -> { Text(text) } },
        placeholder = field.placeholder?.let { text -> { Text(text) } },
        singleLine = true,
        visualTransformation =
            if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        trailingIcon = {
            IconButton(onClick = { visible = !visible }) {
                Icon(
                    imageVector = if (visible) {
                        Icons.Filled.VisibilityOff
                    } else {
                        Icons.Filled.Visibility
                    },
                    contentDescription = stringResource(
                        if (visible) ArrUiR.string.arr_common_connect_fields_hide_password
                        else ArrUiR.string.arr_common_connect_fields_show_password,
                    ),
                )
            }
        },
        modifier = modifier.fillMaxWidth(),
    )
}

/**
 * Number field keeping raw text while typing. The canonical value resyncs
 * only when the parsed text diverges, so intermediate states like "0."
 * survive.
 */
@Composable
fun ConnectNumberField(
    field: Field,
    element: JsonElement?,
    onChange: (JsonElement) -> Unit,
    modifier: Modifier = Modifier,
) {
    val canonical = ConnectFieldForms.textOf(element)
    var text by rememberSaveable(field.name) { mutableStateOf(canonical) }
    LaunchedEffect(element) {
        if (text.isNotBlank() && text.trim().toDoubleOrNull() == null) return@LaunchedEffect
        if (text.trim().toDoubleOrNull() != ConnectFieldForms.doubleOf(element)) {
            text = if (canonical == "null") "" else canonical
        }
    }
    OutlinedTextField(
        value = text,
        onValueChange = { next ->
            text = next
            val trimmed = next.trim()
            when {
                trimmed.isEmpty() -> onChange(ConnectFieldForms.int(null))
                field.isFloat -> trimmed.toDoubleOrNull()
                    ?.let { onChange(ConnectFieldForms.double(it)) }

                else -> trimmed.toIntOrNull()?.let { onChange(ConnectFieldForms.int(it)) }
            }
        },
        label = { Text(field.label ?: field.name.orEmpty()) },
        supportingText = field.helpText?.let { help -> { Text(help) } },
        suffix = field.unit?.let { unit -> { Text(unit) } },
        isError = text.isNotBlank() && text.trim().toDoubleOrNull() == null,
        singleLine = true,
        modifier = modifier.fillMaxWidth(),
    )
}

/** Tag picker over labels, with a free text row for tags that do not exist yet. */
@Composable
fun ConnectTagPicker(
    allTags: List<TagResource>,
    selected: List<String>,
    onToggle: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var newText by rememberSaveable { mutableStateOf("") }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            stringResource(ArrUiR.string.arr_common_tags_title),
            style = MaterialTheme.typography.bodyMedium,
        )
        val chips = (allTags.mapNotNull { it.label } + selected).distinct().sorted()
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            chips.chunked(CHIP_ROW_COUNT).forEach { rowLabels ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    rowLabels.forEach { label ->
                        FilterChip(
                            selected = label in selected,
                            onClick = { onToggle(label) },
                            label = { Text(label) },
                        )
                    }
                }
            }
        }
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            OutlinedTextField(
                value = newText,
                onValueChange = { newText = it },
                label = { Text(stringResource(ArrUiR.string.arr_common_tags_add_title)) },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            TextButton(
                onClick = {
                    val label = newText.trim()
                    if (label.isNotEmpty()) {
                        onToggle(label)
                        newText = ""
                    }
                },
                enabled = newText.trim().isNotEmpty(),
            ) {
                Text(stringResource(ArrUiR.string.arr_common_connect_fields_add_tag))
            }
        }
    }
}

private const val CHIP_ROW_COUNT = 3
