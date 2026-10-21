package dev.appolon.homelab.arr.feature.sonarr.library.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.appolon.homelab.arr.core.FilterSpec
import dev.appolon.homelab.arr.core.FilterValue
import dev.appolon.homelab.arr.feature.sonarr.R
import dev.appolon.homelab.arr.ui.components.LabeledSwitch
import dev.appolon.homelab.arr.ui.R as ArrUiR

/**
 * Edits one filter's value: choice filters list every option with checkboxes,
 * toggles offer any/yes/no, ranges offer two sliders clamped to the option
 * span. Applying the empty value clears the filter, matching the ViewModel's
 * normalization.
 */
@Composable
fun FilterValueDialog(
    title: String,
    spec: FilterSpec,
    value: FilterValue,
    optionLabel: (String) -> String,
    onDismiss: () -> Unit,
    onApply: (FilterValue) -> Unit,
) {
    when (spec) {
        is FilterSpec.Choice -> ChoiceFilterDialog(title, spec, value as? FilterValue.Choice, optionLabel, onDismiss, onApply)
        is FilterSpec.Toggle -> ToggleFilterDialog(title, value as? FilterValue.Toggle, onDismiss, onApply)
        is FilterSpec.Range -> RangeFilterDialog(title, spec, value as? FilterValue.Range, onDismiss, onApply)
    }
}

@Composable
private fun ChoiceFilterDialog(
    title: String,
    spec: FilterSpec.Choice,
    value: FilterValue.Choice?,
    optionLabel: (String) -> String,
    onDismiss: () -> Unit,
    onApply: (FilterValue) -> Unit,
) {
    val selected = remember(spec) { mutableStateOf(value?.selected ?: emptySet()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = { onApply(FilterValue.Choice(selected.value)) }) {
                Text(stringResource(ArrUiR.string.arr_common_filter_dialog_apply))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(ArrUiR.string.arr_common_cancel))
            }
        },
        title = { Text(title) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                spec.options.forEach { option ->
                    val checked = option in selected.value
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                selected.value = if (checked) {
                                    selected.value - option
                                } else {
                                    selected.value + option
                                }
                            }
                            .padding(vertical = 6.dp),
                    ) {
                        Checkbox(
                            checked = checked,
                            onCheckedChange = { isChecked ->
                                selected.value = if (isChecked) {
                                    selected.value + option
                                } else {
                                    selected.value - option
                                }
                            },
                        )
                        Text(text = optionLabel(option), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        },
    )
}

@Composable
private fun ToggleFilterDialog(
    title: String,
    value: FilterValue.Toggle?,
    onDismiss: () -> Unit,
    onApply: (FilterValue) -> Unit,
) {
    val on = remember { mutableStateOf(value?.on) }
    val anyLabel = stringResource(ArrUiR.string.arr_common_quality_language_any)
    val yesLabel = stringResource(ArrUiR.string.arr_common_yes)
    val noLabel = stringResource(ArrUiR.string.arr_common_no)
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = { onApply(FilterValue.Toggle(on.value)) }) {
                Text(stringResource(ArrUiR.string.arr_common_filter_dialog_apply))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(ArrUiR.string.arr_common_cancel))
            }
        },
        title = { Text(title) },
        text = {
            Column {
                listOf(anyLabel to null, yesLabel to true, noLabel to false).forEach { (label, choice) ->
                    SelectableRow(
                        label = label,
                        selected = on.value == choice,
                        onClick = { on.value = choice },
                    )
                }
            }
        },
    )
}

@Composable
private fun RangeFilterDialog(
    title: String,
    spec: FilterSpec.Range,
    value: FilterValue.Range?,
    onDismiss: () -> Unit,
    onApply: (FilterValue) -> Unit,
) {
    val minLabel = stringResource(R.string.arr_sonarr_filter_range_min)
    val maxLabel = stringResource(R.string.arr_sonarr_filter_range_max)
    var min by remember(spec) {
        mutableFloatStateOf((value?.min ?: spec.min).toFloat())
    }
    var max by remember(spec) {
        mutableFloatStateOf((value?.max ?: spec.max).toFloat())
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    val appliedMin = if (min <= spec.min.toFloat()) null else min.toDouble()
                    val appliedMax = if (max >= spec.max.toFloat()) null else max.toDouble()
                    onApply(FilterValue.Range(appliedMin, appliedMax))
                },
            ) {
                Text(stringResource(ArrUiR.string.arr_common_filter_dialog_apply))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(ArrUiR.string.arr_common_cancel))
            }
        },
        title = { Text(title) },
        text = {
            Column {
                Text(
                    text = formatRangeValue(min, max, spec),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                Text(minLabel, style = MaterialTheme.typography.labelMedium)
                Slider(
                    value = min,
                    onValueChange = { new -> min = new.coerceAtMost(max) },
                    valueRange = spec.min.toFloat()..spec.max.toFloat(),
                )
                Text(maxLabel, style = MaterialTheme.typography.labelMedium)
                Slider(
                    value = max,
                    onValueChange = { new -> max = new.coerceAtLeast(min) },
                    valueRange = spec.min.toFloat()..spec.max.toFloat(),
                )
            }
        },
    )
}

/** Whole-number ranges (years, season counts) print without decimals. */
private fun formatRangeValue(min: Float, max: Float, spec: FilterSpec.Range): String {
    val isWhole = spec.min == spec.min.toInt().toDouble() && spec.max == spec.max.toInt().toDouble()
    return if (isWhole) {
        "${min.toInt()} – ${max.toInt()}"
    } else {
        "$min – $max"
    }
}

/** Full-height tappable radio row meeting the 48 dp target. */
@Composable
fun SelectableRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(text = label, style = MaterialTheme.typography.bodyLarge)
    }
}

/** Chip triplet for yes/no/unset choices inside sheets. */
@Composable
fun TriStateChips(
    labelFor: @Composable (Boolean?) -> String,
    value: Boolean?,
    onChange: (Boolean?) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier) {
        listOf(null, true, false).forEach { option ->
            FilterChip(
                selected = value == option,
                onClick = { onChange(option) },
                label = { Text(labelFor(option)) },
            )
        }
    }
}

/** Saves the current filter state under a fresh label. */
@Composable
fun SaveFilterDialog(
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    var label by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = { onSave(label) },
                enabled = label.isNotBlank(),
            ) {
                Text(stringResource(ArrUiR.string.arr_common_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(ArrUiR.string.arr_common_cancel))
            }
        },
        title = { Text(stringResource(ArrUiR.string.arr_common_saved_filter_dialog_title)) },
        text = {
            OutlinedTextField(
                value = label,
                onValueChange = { label = it },
                label = { Text(stringResource(ArrUiR.string.arr_common_autotag_name)) },
                singleLine = true,
            )
        },
    )
}

/** Bulk delete confirmation with the two destructive options Sonarr offers. */
@Composable
fun DeleteSeriesDialog(
    count: Int,
    deleteFiles: Boolean,
    addImportListExclusion: Boolean,
    onOptionChange: (deleteFiles: Boolean, addImportListExclusion: Boolean) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(ArrUiR.string.arr_common_delete))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(ArrUiR.string.arr_common_cancel))
            }
        },
        title = { Text(stringResource(R.string.arr_sonarr_detail_delete_title)) },
        text = {
            Column {
                Text(stringResource(R.string.arr_sonarr_delete_message, count))
                LabeledSwitch(
                    label = stringResource(R.string.arr_sonarr_detail_delete_files),
                    checked = deleteFiles,
                    onCheckedChange = { checked -> onOptionChange(checked, addImportListExclusion) },
                )
                LabeledSwitch(
                    label = stringResource(R.string.arr_sonarr_detail_delete_exclusion),
                    checked = addImportListExclusion,
                    onCheckedChange = { checked -> onOptionChange(deleteFiles, checked) },
                )
            }
        },
    )
}
