package dev.appolon.homelab.arr.feature.bazarr.series.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import dev.appolon.homelab.arr.core.FilterSpec
import dev.appolon.homelab.arr.core.FilterState
import dev.appolon.homelab.arr.core.FilterValue
import dev.appolon.homelab.arr.feature.bazarr.R
import dev.appolon.homelab.arr.feature.bazarr.series.data.BazarrLanguage
import dev.appolon.homelab.arr.feature.bazarr.series.data.BazarrLibraryFilters
import dev.appolon.homelab.arr.feature.bazarr.series.data.SubtitleStatus
import dev.appolon.homelab.arr.feature.bazarr.series.vm.BazarrLibraryUiState
import dev.appolon.homelab.arr.feature.bazarr.series.vm.LibraryMessage
import dev.appolon.homelab.arr.feature.bazarr.series.vm.LibraryView
import dev.appolon.homelab.arr.ui.components.SortMenu

/** User-facing label of one subtitle status. */
@Composable
internal fun statusLabel(status: SubtitleStatus): String = when (status) {
    SubtitleStatus.COMPLETE -> stringResource(R.string.arr_bazarr_lib_status_complete)
    SubtitleStatus.PARTIAL -> stringResource(R.string.arr_bazarr_lib_status_partial)
    SubtitleStatus.MISSING -> stringResource(R.string.arr_bazarr_lib_status_missing)
    SubtitleStatus.NONE_WANTED -> stringResource(R.string.arr_bazarr_lib_status_none_wanted)
}

/**
 * Color-keyed status pill every Bazarr list row and poster carries. The label
 * doubles as the accessibility text so screen readers announce the state.
 */
@Composable
internal fun StatusBadge(status: SubtitleStatus, modifier: Modifier = Modifier) {
    val label = statusLabel(status)
    val (container, content) = when (status) {
        SubtitleStatus.COMPLETE ->
            MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
        SubtitleStatus.PARTIAL ->
            MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.onTertiaryContainer
        SubtitleStatus.MISSING ->
            MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
        SubtitleStatus.NONE_WANTED ->
            MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
    }
    val description = stringResource(R.string.arr_bazarr_lib_cd_status, label)
    Surface(
        color = container,
        contentColor = content,
        shape = MaterialTheme.shapes.small,
        modifier = modifier.semantics { contentDescription = description },
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}

/** Small cloud badge marking items with a change waiting to sync. */
@Composable
internal fun QueuedBadge(modifier: Modifier = Modifier) {
    val description = stringResource(R.string.arr_bazarr_lib_cd_item_queued)
    Icon(
        imageVector = Icons.Filled.CloudUpload,
        contentDescription = description,
        tint = MaterialTheme.colorScheme.tertiary,
        modifier = modifier,
    )
}

/** Queued-count toolbar indicator; the badge text carries the full count. */
@Composable
internal fun QueuedCountIndicator(count: Int, modifier: Modifier = Modifier) {
    val description = stringResource(R.string.arr_bazarr_lib_cd_queued, count)
    BadgedBox(
        badge = { Badge { Text(count.coerceAtMost(99).toString()) } },
        modifier = modifier.semantics { contentDescription = description },
    ) {
        Icon(
            imageVector = Icons.Filled.CloudUpload,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.tertiary,
        )
    }
}

/** Compact chips of language names; HI tracks carry their own annotation. */
@Composable
internal fun LanguageChips(
    languages: List<BazarrLanguage>,
    modifier: Modifier = Modifier,
    highlight: Boolean = false,
) {
    if (languages.isEmpty()) return
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = modifier,
    ) {
        languages.forEach { language ->
            val label = if (language.hearingImpaired) {
                stringResource(
                    R.string.arr_bazarr_series_episodes_cd_hearing_impaired, language.label,
                )
            } else {
                language.label
            }
            Surface(
                color = if (highlight) {
                    MaterialTheme.colorScheme.errorContainer
                } else {
                    MaterialTheme.colorScheme.secondaryContainer
                },
                contentColor = if (highlight) {
                    MaterialTheme.colorScheme.onErrorContainer
                } else {
                    MaterialTheme.colorScheme.onSecondaryContainer
                },
                shape = MaterialTheme.shapes.small,
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
        }
    }
}

/** Snackbar text for one write result. */
@Composable
internal fun libraryMessageText(message: LibraryMessage): String = when (message) {
    LibraryMessage.ProfileSynced -> stringResource(R.string.arr_bazarr_profile_applied)
    LibraryMessage.ProfileQueued -> stringResource(R.string.arr_bazarr_profile_queued)
    is LibraryMessage.WriteFailed -> stringResource(R.string.arr_bazarr_write_failed)
}

/** Option label of one filter choice value: language names win over raw codes. */
@Composable
private fun optionLabel(
    specId: String,
    option: String,
    state: BazarrLibraryUiState,
): String = when (specId) {
    BazarrLibraryFilters.SUBTITLES ->
        state.options.languageLabels[option] ?: option
    BazarrLibraryFilters.STATUS ->
        runCatching { SubtitleStatus.valueOf(option) }.getOrNull()?.let { statusLabel(it) } ?: option
    else -> option
}

/** Filter label of one filter id, shared by the chip row and the sheet. */
@Composable
internal fun filterLabel(id: String): String = when (id) {
    BazarrLibraryFilters.SUBTITLES -> stringResource(R.string.arr_bazarr_lib_filter_subtitles)
    BazarrLibraryFilters.HEARING_IMPAIRED ->
        stringResource(R.string.arr_bazarr_lib_filter_hearing_impaired)
    BazarrLibraryFilters.MONOLINGUAL -> stringResource(R.string.arr_bazarr_lib_filter_monolingual)
    BazarrLibraryFilters.TAGS -> stringResource(R.string.arr_bazarr_lib_filter_tags)
    BazarrLibraryFilters.BLACKLIST -> stringResource(R.string.arr_bazarr_lib_filter_blacklist)
    BazarrLibraryFilters.STATUS -> stringResource(R.string.arr_bazarr_lib_filter_status)
    else -> id
}

/**
 * Filter sheet of the Bazarr lists: one section per spec of the active kind.
 * Choices render as multi-select chips, toggles as a three-way row of
 * Any/Yes/No, all driven by the `:arr-core` Filters framework.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun BazarrFilterSheet(
    state: BazarrLibraryUiState,
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
                text = stringResource(R.string.arr_bazarr_lib_filter_sheet_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier
                    .padding(bottom = 12.dp)
                    .semantics { heading() },
            )
            state.filterSpecs.forEach { spec ->
                FilterSection(
                    spec = spec,
                    state = state,
                    onValueChange = onValueChange,
                )
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
            ) {
                TextButton(onClick = onClear) {
                    Text(stringResource(R.string.arr_bazarr_lib_filter_clear))
                }
                Button(onClick = onDismiss) {
                    Text(stringResource(R.string.arr_bazarr_lib_filter_done))
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FilterSection(
    spec: FilterSpec,
    state: BazarrLibraryUiState,
    onValueChange: (String, FilterValue) -> Unit,
) {
    Column(Modifier.padding(bottom = 4.dp)) {
        Text(
            text = filterLabel(spec.id),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .padding(bottom = 8.dp)
                .semantics { heading() },
        )
        when (spec) {
            is FilterSpec.Choice -> {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    spec.options.forEach { option ->
                        val selected = state.filterState.value(spec.id)
                        val current = (selected as? FilterValue.Choice)?.selected ?: emptySet()
                        val next = if (option in current) current - option else current + option
                        FilterChip(
                            selected = option in current,
                            onClick = {
                                onValueChange(spec.id, FilterValue.Choice(next))
                            },
                            label = { Text(optionLabel(spec.id, option, state)) },
                        )
                    }
                }
            }

            is FilterSpec.Toggle -> {
                val current = (state.filterState.value(spec.id) as? FilterValue.Toggle)?.on
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = current == null,
                        onClick = {
                            onValueChange(spec.id, FilterValue.Toggle(null))
                        },
                        label = { Text(stringResource(R.string.arr_bazarr_lib_filter_any)) },
                    )
                    FilterChip(
                        selected = current == true,
                        onClick = {
                            onValueChange(spec.id, FilterValue.Toggle(true))
                        },
                        label = { Text(stringResource(R.string.arr_bazarr_lib_filter_yes)) },
                    )
                    FilterChip(
                        selected = current == false,
                        onClick = {
                            onValueChange(spec.id, FilterValue.Toggle(false))
                        },
                        label = { Text(stringResource(R.string.arr_bazarr_lib_filter_no)) },
                    )
                }
            }

            is FilterSpec.Range -> Unit
        }
    }
}

/**
 * Mass editor dialog: assigns one language profile to the bulk targets. The
 * whole row is one selectable 48dp target carrying the radio role, so the
 * label and the button announce together.
 */
@Composable
internal fun ProfilePickerDialog(
    state: BazarrLibraryUiState,
    onPick: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.arr_bazarr_profile_picker_title)) },
        text = {
            Column {
                Text(
                    text = stringResource(R.string.arr_bazarr_profile_picker_count, state.bulkTargets.size),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                if (state.profiles.isEmpty()) {
                    Text(stringResource(R.string.arr_bazarr_profile_picker_none))
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 320.dp),
                    ) {
                        items(state.profiles, key = { it.profileId }) { profile ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 48.dp)
                                    .selectable(
                                        selected = false,
                                        onClick = { onPick(profile.profileId) },
                                        role = Role.RadioButton,
                                    )
                                    .padding(vertical = 2.dp),
                            ) {
                                RadioButton(
                                    selected = false,
                                    onClick = null,
                                )
                                Text(
                                    text = profile.label,
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(end = 8.dp),
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.arr_bazarr_profile_picker_cancel))
            }
        },
    )
}

/** Header line with the badge counts, shown above the filter chips. */
@Composable
internal fun BadgeSummary(badges: dev.appolon.homelab.arr.feature.bazarr.series.data.BazarrBadges) {
    Text(
        text = stringResource(
            R.string.arr_bazarr_lib_badge_summary,
            badges.episodesMissing,
            badges.moviesMissing,
        ),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 16.dp, top = 4.dp),
    )
}

/** Toolbar outside selection mode, shared by both lists; all targets 48dp. */
@Composable
internal fun ListActions(
    state: BazarrLibraryUiState,
    sortLabels: Map<String, String>,
    onSortSelect: (String) -> Unit,
    onToggleSortDescending: () -> Unit,
    onViewChange: (LibraryView) -> Unit,
) {
    if (state.queuedForServer > 0) {
        QueuedCountIndicator(count = state.queuedForServer)
    }
    IconButton(onClick = {
        onViewChange(
            if (state.view == LibraryView.POSTERS) LibraryView.TABLE else LibraryView.POSTERS,
        )
    }) {
        Icon(
            imageVector = if (state.view == LibraryView.POSTERS) {
                Icons.Filled.ViewList
            } else {
                Icons.Filled.GridView
            },
            contentDescription = if (state.view == LibraryView.POSTERS) {
                stringResource(R.string.arr_bazarr_lib_cd_view_table)
            } else {
                stringResource(R.string.arr_bazarr_lib_cd_view_posters)
            },
        )
    }
    SortMenu(
        options = state.sortOptions,
        selected = state.sortId,
        label = { sortLabels[it] ?: it },
        onSelect = onSortSelect,
    )
    val sortState = stringResource(
        if (state.sortDescending) R.string.arr_bazarr_sort_state_descending
        else R.string.arr_bazarr_sort_state_ascending,
    )
    IconButton(
        onClick = onToggleSortDescending,
        modifier = Modifier.semantics { stateDescription = sortState },
    ) {
        Icon(
            imageVector = if (state.sortDescending) {
                Icons.Filled.ArrowDownward
            } else {
                Icons.Filled.ArrowUpward
            },
            contentDescription = stringResource(R.string.arr_bazarr_lib_cd_sort_descending),
        )
    }
}
