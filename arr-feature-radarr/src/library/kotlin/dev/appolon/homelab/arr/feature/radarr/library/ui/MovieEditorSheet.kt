package dev.appolon.homelab.arr.feature.radarr.library.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.appolon.homelab.arr.api.radarr.model.ApplyTags
import dev.appolon.homelab.arr.api.radarr.model.MovieStatusType
import dev.appolon.homelab.arr.api.radarr.model.QualityProfileResource
import dev.appolon.homelab.arr.feature.radarr.R
import dev.appolon.homelab.arr.feature.radarr.library.vm.MovieEditorForm

/**
 * Bulk movie editor sheet. Every field starts as "unchanged"; only the fields
 * the user touches travel in the MovieEditorResource, matching the endpoint's
 * null-means-keep semantics.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MovieEditorSheet(
    form: MovieEditorForm,
    targetCount: Int,
    qualityProfiles: List<QualityProfileResource>,
    onChange: ((MovieEditorForm) -> MovieEditorForm) -> Unit,
    onSubmit: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
        ) {
            Text(
                text = stringResource(R.string.arr_radarr_editor_title),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = stringResource(R.string.arr_radarr_editor_target_count, targetCount),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp, bottom = 12.dp),
            )

            TriStateRow(
                label = stringResource(R.string.arr_radarr_editor_monitored),
                value = form.monitored,
                onChange = { setting -> onChange { form -> form.copy(monitored = setting) } },
            )

            DropdownRow(
                label = stringResource(R.string.arr_radarr_editor_quality),
                selected = form.qualityProfileId?.let { id ->
                    qualityProfiles.firstOrNull { it.id == id }?.name ?: "#$id"
                },
                options = qualityProfiles.map { it.id.toString() to (it.name ?: "#${it.id}") },
                onSelect = { id, _ ->
                    onChange { form -> form.copy(qualityProfileId = id.toIntOrNull()) }
                },
            )

            DropdownRow(
                label = stringResource(R.string.arr_radarr_editor_availability),
                selected = form.minimumAvailability?.let { availabilityLabel(it) },
                options = availabilityOptions(),
                onSelect = { value, _ ->
                    onChange { form -> form.copy(minimumAvailability = availabilityOf(value)) }
                },
            )

            OutlinedTextField(
                value = form.rootFolderPath,
                onValueChange = { value ->
                    onChange { form -> form.copy(rootFolderPath = value) }
                },
                label = { Text(stringResource(R.string.arr_radarr_editor_root_folder)) },
                supportingText = {
                    Text(stringResource(R.string.arr_radarr_editor_root_folder_hint))
                },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
            )

            OutlinedTextField(
                value = form.tagsText,
                onValueChange = { value ->
                    onChange { form -> form.copy(tagsText = value, tagsError = false) }
                },
                label = { Text(stringResource(R.string.arr_radarr_editor_tags)) },
                supportingText = {
                    Text(
                        stringResource(
                            if (form.tagsError) {
                                R.string.arr_radarr_editor_tags_invalid
                            } else {
                                R.string.arr_radarr_editor_tags_hint
                            },
                        ),
                    )
                },
                isError = form.tagsError,
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
            )

            Text(
                stringResource(R.string.arr_radarr_editor_tags_apply),
                style = MaterialTheme.typography.titleSmall,
            )
            SingleChoiceSegmentedButtonRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp, bottom = 12.dp),
            ) {
                val modes = listOf(
                    ApplyTags.ADD to R.string.arr_radarr_editor_tags_add,
                    ApplyTags.REMOVE to R.string.arr_radarr_editor_tags_remove,
                    ApplyTags.REPLACE to R.string.arr_radarr_editor_tags_replace,
                )
                modes.forEachIndexed { index, (mode, res) ->
                    SegmentedButton(
                        selected = form.applyTags == mode,
                        onClick = { onChange { form -> form.copy(applyTags = mode) } },
                        shape = SegmentedButtonDefaults.itemShape(
                            index = index,
                            count = modes.size,
                        ),
                        label = { Text(stringResource(res)) },
                    )
                }
            }

            SwitchRow(
                label = stringResource(R.string.arr_radarr_editor_move_files),
                checked = form.moveFiles,
                onChange = { value -> onChange { form -> form.copy(moveFiles = value) } },
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
            ) {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.arr_radarr_editor_cancel))
                }
                Button(onClick = onSubmit) {
                    Text(stringResource(R.string.arr_radarr_editor_save))
                }
            }
        }
    }
}

/** Monitored picker: unchanged, yes, no. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TriStateRow(
    label: String,
    value: Boolean?,
    onChange: (Boolean?) -> Unit,
) {
    Text(label, style = MaterialTheme.typography.titleSmall)
    SingleChoiceSegmentedButtonRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 12.dp),
    ) {
        val options = listOf(
            null to R.string.arr_radarr_editor_unchanged,
            true to R.string.arr_radarr_filter_yes,
            false to R.string.arr_radarr_filter_no,
        )
        options.forEachIndexed { index, (option, res) ->
            SegmentedButton(
                selected = value == option,
                onClick = { onChange(option) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                label = { Text(stringResource(res)) },
            )
        }
    }
}

/** Dropdown row used for quality profile and minimum availability. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DropdownRow(
    label: String,
    selected: String?,
    options: List<Pair<String, String>>,
    onSelect: (String, String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp),
    ) {
        OutlinedTextField(
            value = selected ?: stringResource(R.string.arr_radarr_editor_unchanged),
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth(),
        )
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            if (selected != null) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.arr_radarr_editor_unchanged)) },
                    onClick = {
                        expanded = false
                        onSelect("", "")
                    },
                )
            }
            options.forEach { (value, text) ->
                DropdownMenuItem(
                    text = { Text(text) },
                    onClick = {
                        expanded = false
                        onSelect(value, text)
                    },
                )
            }
        }
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 4.dp),
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

/** Confirmation for bulk delete, with the two editor flags as switches. */
@Composable
fun MovieDeleteDialog(
    targetCount: Int,
    onConfirm: (deleteFiles: Boolean, addImportExclusion: Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    var deleteFiles by remember { mutableStateOf(false) }
    var addExclusion by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.arr_radarr_delete_title, targetCount)) },
        text = {
            Column {
                SwitchRow(
                    label = stringResource(R.string.arr_radarr_delete_files),
                    checked = deleteFiles,
                    onChange = { deleteFiles = it },
                )
                SwitchRow(
                    label = stringResource(R.string.arr_radarr_delete_exclusion),
                    checked = addExclusion,
                    onChange = { addExclusion = it },
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(deleteFiles, addExclusion) }) {
                Text(stringResource(R.string.arr_radarr_delete_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.arr_radarr_delete_cancel))
            }
        },
    )
}

@Composable
internal fun availabilityLabel(status: MovieStatusType): String = when (status) {
    MovieStatusType.TBA -> stringResource(R.string.arr_radarr_status_tba)
    MovieStatusType.ANNOUNCED -> stringResource(R.string.arr_radarr_status_announced)
    MovieStatusType.IN_CINEMAS -> stringResource(R.string.arr_radarr_status_in_cinemas)
    MovieStatusType.RELEASED -> stringResource(R.string.arr_radarr_status_released)
    MovieStatusType.DELETED -> stringResource(R.string.arr_radarr_status_deleted)
}

/** Options with the label doubling as the value, so [availabilityOf] can invert it. */
@Composable
private fun availabilityOptions(): List<Pair<String, String>> = listOf(
    MovieStatusType.TBA,
    MovieStatusType.ANNOUNCED,
    MovieStatusType.IN_CINEMAS,
    MovieStatusType.RELEASED,
).map { it.name to availabilityLabel(it) }

private fun availabilityOf(label: String): MovieStatusType? = when (label) {
    MovieStatusType.TBA.name -> MovieStatusType.TBA
    MovieStatusType.ANNOUNCED.name -> MovieStatusType.ANNOUNCED
    MovieStatusType.IN_CINEMAS.name -> MovieStatusType.IN_CINEMAS
    MovieStatusType.RELEASED.name -> MovieStatusType.RELEASED
    else -> null
}
