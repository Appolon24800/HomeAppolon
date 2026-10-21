package dev.appolon.homelab.arr.feature.prowlarr.indexers.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import dev.appolon.homelab.arr.api.prowlarr.model.ApplicationSyncLevel
import dev.appolon.homelab.arr.api.prowlarr.model.DownloadProtocol
import dev.appolon.homelab.arr.api.prowlarr.model.Field
import dev.appolon.homelab.arr.api.prowlarr.model.IndexerPrivacy
import dev.appolon.homelab.arr.api.prowlarr.model.IndexerResource
import dev.appolon.homelab.arr.api.prowlarr.model.IndexerStatusResource
import dev.appolon.homelab.arr.api.prowlarr.model.TagResource
import dev.appolon.homelab.arr.core.ArrTime
import dev.appolon.homelab.arr.feature.prowlarr.R
import dev.appolon.homelab.arr.feature.prowlarr.indexers.data.FieldForms
import dev.appolon.homelab.arr.feature.prowlarr.indexers.data.FieldRenderKind
import dev.appolon.homelab.arr.ui.components.LabeledSwitch
import dev.appolon.homelab.arr.ui.components.SectionHeader
import kotlinx.serialization.json.JsonElement
import dev.appolon.homelab.arr.ui.R as ArrUiR

/** Protocol label for chips and table cells. */
@Composable
fun protocolLabel(protocol: DownloadProtocol): String = when (protocol) {
    DownloadProtocol.TORRENT -> stringResource(ArrUiR.string.arr_common_quality_delay_torrent)
    DownloadProtocol.USENET -> stringResource(ArrUiR.string.arr_common_quality_delay_usenet)
    DownloadProtocol.UNKNOWN -> stringResource(ArrUiR.string.arr_common_status_unknown)
}

/** Privacy label for chips and table cells. */
@Composable
fun privacyLabel(privacy: IndexerPrivacy): String = when (privacy) {
    IndexerPrivacy.PUBLIC -> stringResource(R.string.arr_prowlarr_privacy_public)
    IndexerPrivacy.SEMI_PRIVATE -> stringResource(R.string.arr_prowlarr_privacy_semi_private)
    IndexerPrivacy.PRIVATE -> stringResource(R.string.arr_prowlarr_privacy_private)
}

/** Sync level label for badges. */
@Composable
fun syncLevelLabel(level: ApplicationSyncLevel): String = when (level) {
    ApplicationSyncLevel.DISABLED -> stringResource(R.string.arr_prowlarr_sync_disabled)
    ApplicationSyncLevel.ADD_ONLY -> stringResource(R.string.arr_prowlarr_sync_add_only)
    ApplicationSyncLevel.FULL_SYNC -> stringResource(R.string.arr_prowlarr_sync_full)
}

/**
 * Health badges derived from IndexerStatus: throttled shows the cooldown
 * end, and a most recent failure marks the indexer failing. Rendered only
 * when there is something to say.
 */
@Composable
fun IndexerStatusBadges(
    indexer: IndexerResource,
    status: IndexerStatusResource?,
    modifier: Modifier = Modifier,
) {
    val throttledTill = ArrTime.parse(status?.disabledTill)
    val failed = status?.mostRecentFailure != null
    if (throttledTill == null && !failed) return
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (throttledTill != null) {
            AssistChip(
                onClick = {},
                enabled = false,
                label = {
                    Text(
                        stringResource(
                            R.string.arr_prowlarr_badge_throttled,
                            ArrTime.dateTimeShort(throttledTill),
                        ),
                    )
                },
            )
        }
        if (failed) {
            AssistChip(
                onClick = {},
                enabled = false,
                label = { Text(stringResource(R.string.arr_prowlarr_badge_failing)) },
            )
        }
    }
}

/** Chip marking rows with queued offline writes. */
@Composable
fun QueuedBadge(modifier: Modifier = Modifier) {
    val description = stringResource(R.string.arr_prowlarr_badge_queued_cd)
    AssistChip(
        onClick = {},
        enabled = false,
        modifier = modifier.semantics { contentDescription = description },
        label = { Text(stringResource(ArrUiR.string.arr_common_connect_badge_queued)) },
    )
}

/** Tag chips resolved against the cached tag list. */
@Composable
fun TagChips(
    tags: List<Int>,
    allTags: List<TagResource>,
    modifier: Modifier = Modifier,
) {
    if (tags.isEmpty()) return
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        tags.forEach { id ->
            val label = allTags.firstOrNull { it.id == id }?.label ?: "#$id"
            AssistChip(onClick = {}, enabled = false, label = { Text(label) })
        }
    }
}

/**
 * Renders the dynamic provider fields from the generic Field model. Fields
 * split by their declared section; advanced fields hide behind a toggle.
 */
@Composable
fun FieldEditors(
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
            FieldEditor(field, values, onValueChange, tagOptions)
        }
        sections.keys.filter { it.isNotEmpty() }.sorted().forEach { section ->
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SectionHeader(
                    title = section,
                    modifier = Modifier.semantics { heading() },
                )
                sections.getValue(section).forEach { field ->
                    FieldEditor(field, values, onValueChange, tagOptions)
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

/** One field editor, dispatching on the rendered kind. */
@Composable
fun FieldEditor(
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
        when (FieldForms.renderKind(field)) {
            FieldRenderKind.CHECKBOX -> LabeledSwitch(
                label = field.label ?: name,
                checked = FieldForms.boolOf(element),
                onCheckedChange = { onValueChange(name, FieldForms.bool(it)) },
                supportingText = field.helpText,
            )

            FieldRenderKind.SELECT -> SelectField(
                field = field,
                selected = FieldForms.intOf(element),
                onSelect = { onValueChange(name, FieldForms.int(it)) },
            )

            FieldRenderKind.TAG_SELECT -> TagSelectField(
                field = field,
                selected = FieldForms.idsOf(element),
                options = tagOptions,
                onChange = { onValueChange(name, FieldForms.ids(it)) },
            )

            FieldRenderKind.PASSWORD -> PasswordField(
                field = field,
                value = FieldForms.textOf(element),
                onChange = { onValueChange(name, FieldForms.text(it)) },
            )

            FieldRenderKind.NUMBER -> NumberField(
                field = field,
                element = element,
                onChange = { onValueChange(name, it) },
            )

            FieldRenderKind.TEXT -> OutlinedTextField(
                value = FieldForms.textOf(element),
                onValueChange = { onValueChange(name, FieldForms.text(it)) },
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
                Text(stringResource(R.string.arr_prowlarr_fields_help_link))
            }
        }
    }
}

/** Dropdown over the field's declared select options. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SelectField(
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

/** Multi select of tag ids rendered as filter chips. */
@Composable
fun TagSelectField(
    field: Field,
    selected: List<Int>,
    options: List<TagResource>,
    onChange: (List<Int>) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        Text(
            field.label ?: field.name.orEmpty(),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(bottom = 4.dp),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEach { tag ->
                FilterChip(
                    selected = tag.id in selected,
                    onClick = {
                        onChange(if (tag.id in selected) selected - tag.id else selected + tag.id)
                    },
                    label = { Text(tag.label.orEmpty()) },
                )
            }
        }
        field.helpText?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
    }
}

/** Secret field that stays masked until the eye is tapped. */
@Composable
fun PasswordField(
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
                        if (visible) R.string.arr_prowlarr_fields_hide_password
                        else R.string.arr_prowlarr_fields_show_password,
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
fun NumberField(
    field: Field,
    element: JsonElement?,
    onChange: (JsonElement) -> Unit,
    modifier: Modifier = Modifier,
) {
    val canonical = FieldForms.textOf(element)
    var text by rememberSaveable(field.name) { mutableStateOf(canonical) }
    LaunchedEffect(element) {
        if (text.isNotBlank() && text.trim().toDoubleOrNull() == null) return@LaunchedEffect
        if (text.trim().toDoubleOrNull() != FieldForms.doubleOf(element)) {
            text = if (canonical == "null") "" else canonical
        }
    }
    OutlinedTextField(
        value = text,
        onValueChange = { next ->
            text = next
            val trimmed = next.trim()
            when {
                trimmed.isEmpty() -> onChange(FieldForms.int(null))
                field.isFloat -> trimmed.toDoubleOrNull()
                    ?.let { onChange(FieldForms.double(it)) }

                else -> trimmed.toIntOrNull()?.let { onChange(FieldForms.int(it)) }
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
fun TagLabelPicker(
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
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            chips.forEach { label ->
                FilterChip(
                    selected = label in selected,
                    onClick = { onToggle(label) },
                    label = { Text(label) },
                )
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
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
                Text(stringResource(ArrUiR.string.arr_common_roots_add_confirm))
            }
        }
    }
}
