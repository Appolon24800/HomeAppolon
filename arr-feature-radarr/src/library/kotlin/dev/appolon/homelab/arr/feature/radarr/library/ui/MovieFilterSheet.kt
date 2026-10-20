package dev.appolon.homelab.arr.feature.radarr.library.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
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
import dev.appolon.homelab.arr.core.FilterSpec
import dev.appolon.homelab.arr.core.FilterState
import dev.appolon.homelab.arr.core.FilterValue
import dev.appolon.homelab.arr.feature.radarr.R
import dev.appolon.homelab.arr.feature.radarr.library.data.MovieLibraryFilters

/**
 * Filter sheet of the movies library: one section per filter of the brief
 * (monitored, quality, genre, year, folder, tags, missing), driven entirely
 * by the `:arr-core` Filters framework specs. Choices render as multi-select
 * chips, toggles as a three way segmented row, the year range as two fields.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun MovieFilterSheet(
    filterState: FilterState,
    specs: List<FilterSpec>,
    profileLabel: (Int) -> String,
    onValueChange: (String, FilterValue) -> Unit,
    onClear: () -> Unit,
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
                text = stringResource(R.string.arr_radarr_filter_sheet_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 12.dp),
            )
            specs.forEach { spec -> FilterSection(spec, filterState, profileLabel, onValueChange) }
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
            ) {
                TextButton(onClick = onClear) {
                    Text(stringResource(R.string.arr_radarr_filter_clear))
                }
                Button(onClick = onDismiss) {
                    Text(stringResource(R.string.arr_radarr_saved_filter_cancel))
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FilterSection(
    spec: FilterSpec,
    state: FilterState,
    profileLabel: (Int) -> String,
    onValueChange: (String, FilterValue) -> Unit,
) {
    when (spec) {
        is FilterSpec.Toggle -> ToggleSection(spec, state, onValueChange)
        is FilterSpec.Choice -> ChoiceSection(spec, state, profileLabel, onValueChange)
        is FilterSpec.Range -> RangeSection(spec, state, onValueChange)
    }
    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
}

@Composable
private fun ToggleSection(
    spec: FilterSpec.Toggle,
    state: FilterState,
    onValueChange: (String, FilterValue) -> Unit,
) {
    val current = (state.value(spec.id) as? FilterValue.Toggle)?.on
    val label = filterLabel(spec.id)
    Text(
        label,
        style = MaterialTheme.typography.titleSmall,
        modifier = Modifier.padding(bottom = 8.dp),
    )
    val options = listOf(
        true to R.string.arr_radarr_filter_yes,
        false to R.string.arr_radarr_filter_no,
    )
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        options.forEachIndexed { index, (value, res) ->
            SegmentedButton(
                selected = current == value,
                onClick = {
                    val next = if (current == value) null else value
                    onValueChange(spec.id, FilterValue.Toggle(next))
                },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size + 1),
                label = { Text(stringResource(res)) },
            )
        }
        SegmentedButton(
            selected = current == null,
            onClick = { onValueChange(spec.id, FilterValue.Toggle(null)) },
            shape = SegmentedButtonDefaults.itemShape(
                index = options.size,
                count = options.size + 1,
            ),
            label = { Text(stringResource(R.string.arr_radarr_filter_any)) },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChoiceSection(
    spec: FilterSpec.Choice,
    state: FilterState,
    profileLabel: (Int) -> String,
    onValueChange: (String, FilterValue) -> Unit,
) {
    val selected = (state.value(spec.id) as? FilterValue.Choice)?.selected ?: emptySet()
    Text(
        filterLabel(spec.id),
        style = MaterialTheme.typography.titleSmall,
        modifier = Modifier.padding(bottom = 8.dp),
    )
    if (spec.options.isEmpty()) {
        Text(
            stringResource(R.string.arr_radarr_filter_empty_options),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        spec.options.forEach { option ->
            val optionLabel = optionLabel(spec.id, option, profileLabel)
            FilterChip(
                selected = option in selected,
                onClick = {
                    val next = if (option in selected) selected - option else selected + option
                    onValueChange(spec.id, FilterValue.Choice(next))
                },
                label = { Text(optionLabel) },
            )
        }
    }
}

@Composable
private fun RangeSection(
    spec: FilterSpec.Range,
    state: FilterState,
    onValueChange: (String, FilterValue) -> Unit,
) {
    val range = state.value(spec.id) as? FilterValue.Range ?: FilterValue.Range(null, null)
    var from by remember(spec.id, range.min, range.max) {
        mutableStateOf(range.min?.toInt()?.toString().orEmpty())
    }
    var to by remember(spec.id, range.min, range.max) {
        mutableStateOf(range.max?.toInt()?.toString().orEmpty())
    }

    fun commit(nextFrom: Int?, nextTo: Int?) {
        onValueChange(spec.id, FilterValue.Range(nextFrom?.toDouble(), nextTo?.toDouble()))
    }

    val fromParsed = from.trim().toIntOrNull()?.takeIf { it in 1800..3000 }
    val toParsed = to.trim().toIntOrNull()?.takeIf { it in 1800..3000 }
    val valid = (from.isBlank() || fromParsed != null) && (to.isBlank() || toParsed != null)

    Text(
        filterLabel(spec.id),
        style = MaterialTheme.typography.titleSmall,
        modifier = Modifier.padding(bottom = 8.dp),
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = from,
            onValueChange = { value ->
                from = value.filter(Char::isDigit).take(4)
                commit(from.toIntOrNull()?.takeIf { from.isNotBlank() }, toParsed)
            },
            label = { Text(stringResource(R.string.arr_radarr_filter_year_from)) },
            isError = from.isNotBlank() && from.toIntOrNull() == null,
            singleLine = true,
            modifier = Modifier.weight(1f),
        )
        OutlinedTextField(
            value = to,
            onValueChange = { value ->
                to = value.filter(Char::isDigit).take(4)
                commit(fromParsed, to.toIntOrNull()?.takeIf { to.isNotBlank() })
            },
            label = { Text(stringResource(R.string.arr_radarr_filter_year_to)) },
            isError = to.isNotBlank() && to.toIntOrNull() == null,
            singleLine = true,
            modifier = Modifier.weight(1f),
        )
    }
    if (!valid) {
        Text(
            stringResource(R.string.arr_radarr_filter_invalid_year),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
    }
}

@Composable
internal fun filterLabel(id: String): String = when (id) {
    MovieLibraryFilters.MONITORED -> stringResource(R.string.arr_radarr_filter_monitored)
    MovieLibraryFilters.MISSING -> stringResource(R.string.arr_radarr_filter_missing)
    MovieLibraryFilters.QUALITY -> stringResource(R.string.arr_radarr_filter_quality)
    MovieLibraryFilters.GENRE -> stringResource(R.string.arr_radarr_filter_genre)
    MovieLibraryFilters.YEAR -> stringResource(R.string.arr_radarr_filter_year)
    MovieLibraryFilters.FOLDER -> stringResource(R.string.arr_radarr_filter_folder)
    MovieLibraryFilters.TAGS -> stringResource(R.string.arr_radarr_filter_tags)
    else -> id
}

@Composable
internal fun optionLabel(
    id: String,
    option: String,
    profileLabel: (Int) -> String,
): String = when (id) {
    MovieLibraryFilters.QUALITY -> option.toIntOrNull()?.let(profileLabel) ?: option
    MovieLibraryFilters.TAGS -> stringResource(
        R.string.arr_radarr_filter_tag_option,
        option.toIntOrNull() ?: 0,
    )
    else -> option
}
