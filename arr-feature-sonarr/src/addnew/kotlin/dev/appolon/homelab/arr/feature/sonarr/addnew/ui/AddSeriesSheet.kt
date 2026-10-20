package dev.appolon.homelab.arr.feature.sonarr.addnew.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import dev.appolon.homelab.arr.api.sonarr.model.LanguageProfileResource
import dev.appolon.homelab.arr.api.sonarr.model.MonitorTypes
import dev.appolon.homelab.arr.api.sonarr.model.QualityProfileResource
import dev.appolon.homelab.arr.api.sonarr.model.RootFolderResource
import dev.appolon.homelab.arr.api.sonarr.model.SeriesTypes
import dev.appolon.homelab.arr.api.sonarr.model.TagResource
import dev.appolon.homelab.arr.core.Humanize
import dev.appolon.homelab.arr.feature.sonarr.R
import dev.appolon.homelab.arr.feature.sonarr.addnew.vm.AddSeriesForm
import dev.appolon.homelab.arr.feature.sonarr.addnew.vm.ExclusionDraft
import dev.appolon.homelab.arr.ui.components.LabeledSwitch

/**
 * The add-series sheet: root folder (with free space), quality and language
 * profiles, series type, monitoring strategy, per-season monitor picker,
 * season folders, and tags. Add sends the plain add; Add and search also
 * starts the missing-episode search once the series exists. The submit
 * buttons stay disabled until a root folder is chosen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddSeriesSheet(
    form: AddSeriesForm,
    rootFolders: List<RootFolderResource>,
    qualityProfiles: List<QualityProfileResource>,
    languageProfiles: List<LanguageProfileResource>,
    tags: List<TagResource>,
    submitting: Boolean,
    onDismiss: () -> Unit,
    onUpdate: (AddSeriesForm) -> Unit,
    onSetMonitor: (MonitorTypes) -> Unit,
    onToggleSeason: (Int) -> Unit,
    onToggleTag: (Int) -> Unit,
    onAdd: (Boolean) -> Unit,
) {
    val canSubmit = form.rootFolderPath.isNotBlank() && !submitting
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Text(
                text = form.series.title
                    ?: stringResource(R.string.arr_sonarr_add_untitled),
                style = MaterialTheme.typography.titleLarge,
            )
            Text(
                text = addSheetSubtitle(form),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 12.dp),
            )

            LabeledSwitch(
                label = stringResource(R.string.arr_sonarr_add_monitored),
                checked = form.monitored,
                onCheckedChange = { checked -> onUpdate(form.copy(monitored = checked)) },
            )

            Text(
                text = stringResource(R.string.arr_sonarr_add_root_folder),
                modifier = Modifier.padding(top = 8.dp),
            )
            if (rootFolders.isEmpty()) {
                Text(
                    text = stringResource(R.string.arr_sonarr_add_root_folder_none),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 12.dp),
                )
            } else {
                Column(Modifier.padding(bottom = 12.dp)) {
                    rootFolders.forEach { folder ->
                        PickerRow(
                            label = folder.path.orEmpty(),
                            supporting = stringResource(
                                R.string.arr_sonarr_add_root_folder_free,
                                Humanize.bytes(folder.freeSpace),
                            ),
                            selected = form.rootFolderPath == folder.path,
                            onClick = {
                                onUpdate(form.copy(rootFolderPath = folder.path.orEmpty()))
                            },
                        )
                    }
                }
            }

            Text(stringResource(R.string.arr_sonarr_add_quality))
            Column(Modifier.padding(bottom = 12.dp)) {
                qualityProfiles.forEach { profile ->
                    PickerRow(
                        label = profile.name ?: "#${profile.id}",
                        selected = form.qualityProfileId == profile.id,
                        onClick = { onUpdate(form.copy(qualityProfileId = profile.id)) },
                    )
                }
            }

            if (languageProfiles.isNotEmpty()) {
                Text(stringResource(R.string.arr_sonarr_add_language))
                Column(Modifier.padding(bottom = 12.dp)) {
                    languageProfiles.forEach { profile ->
                        PickerRow(
                            label = profile.name ?: "#${profile.id}",
                            selected = form.languageProfileId == profile.id,
                            onClick = { onUpdate(form.copy(languageProfileId = profile.id)) },
                        )
                    }
                }
            }

            Text(stringResource(R.string.arr_sonarr_add_series_type))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
            ) {
                seriesTypeOptions().forEach { (type, label) ->
                    FilterChip(
                        selected = form.seriesType == type,
                        onClick = { onUpdate(form.copy(seriesType = type)) },
                        label = { Text(label) },
                    )
                }
            }

            Text(stringResource(R.string.arr_sonarr_add_monitor_strategy))
            Column(Modifier.padding(bottom = 12.dp)) {
                monitorStrategyOptions().forEach { (strategy, label) ->
                    PickerRow(
                        label = label,
                        selected = form.monitor == strategy,
                        onClick = { onSetMonitor(strategy) },
                    )
                }
            }

            Text(stringResource(R.string.arr_sonarr_add_season_picker))
            SeasonChipRow(
                form = form,
                onToggleSeason = onToggleSeason,
                modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
            )

            LabeledSwitch(
                label = stringResource(R.string.arr_sonarr_add_season_folder),
                checked = form.seasonFolder,
                onCheckedChange = { checked -> onUpdate(form.copy(seasonFolder = checked)) },
            )

            if (tags.isNotEmpty()) {
                Text(
                    text = stringResource(R.string.arr_sonarr_add_tags),
                    modifier = Modifier.padding(top = 8.dp),
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
                ) {
                    tags.forEach { tag ->
                        FilterChip(
                            selected = tag.id in form.tagIds,
                            onClick = { onToggleTag(tag.id) },
                            label = { Text(tag.label ?: "#${tag.id}") },
                        )
                    }
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .align(Alignment.End)
                    .padding(vertical = 16.dp),
            ) {
                TextButton(onClick = onDismiss, enabled = !submitting) {
                    Text(stringResource(R.string.arr_sonarr_add_cancel))
                }
                OutlinedButton(onClick = { onAdd(false) }, enabled = canSubmit) {
                    Text(stringResource(R.string.arr_sonarr_add_action_add))
                }
                Button(onClick = { onAdd(true) }, enabled = canSubmit) {
                    Text(stringResource(R.string.arr_sonarr_add_action_add_search))
                }
            }
        }
    }
}

/** Per-season monitor chips; specials print their own label. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SeasonChipRow(
    form: AddSeriesForm,
    onToggleSeason: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val seasons = form.series.seasons.orEmpty().sortedBy { it.seasonNumber }
    if (seasons.isEmpty()) {
        Text(
            text = stringResource(R.string.arr_sonarr_add_season_none),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = modifier,
        )
        return
    }
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier,
    ) {
        seasons.forEach { season ->
            FilterChip(
                selected = season.seasonNumber in form.monitoredSeasons,
                onClick = { onToggleSeason(season.seasonNumber) },
                label = {
                    Text(
                        if (season.seasonNumber == 0) {
                            stringResource(R.string.arr_sonarr_add_season_specials)
                        } else {
                            stringResource(
                                R.string.arr_sonarr_add_season_number,
                                season.seasonNumber,
                            )
                        },
                    )
                },
            )
        }
    }
}

/** Full-height tappable radio row with optional supporting text, 48dp target. */
@Composable
private fun PickerRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    supporting: String? = null,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
    ) {
        RadioButton(selected = selected, onClick = null)
        Column {
            Text(text = label, style = MaterialTheme.typography.bodyLarge)
            if (supporting != null) {
                Text(
                    text = supporting,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** Dialog creating a new import list exclusion from a title and TVDB id. */
@Composable
fun AddExclusionDialog(
    draft: ExclusionDraft,
    onDismiss: () -> Unit,
    onUpdate: (ExclusionDraft) -> Unit,
    onSave: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.arr_sonarr_add_exclusion_dialog_title)) },
        text = {
            Column {
                OutlinedTextField(
                    value = draft.title,
                    onValueChange = { value ->
                        onUpdate(draft.copy(title = value, titleError = false))
                    },
                    label = { Text(stringResource(R.string.arr_sonarr_add_exclusion_title_label)) },
                    isError = draft.titleError,
                    supportingText = if (draft.titleError) {
                        { Text(stringResource(R.string.arr_sonarr_add_exclusion_title_error)) }
                    } else {
                        null
                    },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                )
                OutlinedTextField(
                    value = draft.tvdbText,
                    onValueChange = { value ->
                        onUpdate(draft.copy(tvdbText = value, tvdbError = false))
                    },
                    label = { Text(stringResource(R.string.arr_sonarr_add_exclusion_tvdb_label)) },
                    isError = draft.tvdbError,
                    supportingText = if (draft.tvdbError) {
                        { Text(stringResource(R.string.arr_sonarr_add_exclusion_tvdb_error)) }
                    } else {
                        null
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onSave) {
                Text(stringResource(R.string.arr_sonarr_add_exclusion_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.arr_sonarr_add_cancel))
            }
        },
    )
}

/** One-line summary under the sheet title: year plus season count. */
@Composable
private fun addSheetSubtitle(form: AddSeriesForm): String {
    val year = form.series.year.takeIf { it > 0 }
    val seasons = form.series.seasons.orEmpty().size
    val seasonLabel = pluralStringResource(
        R.plurals.arr_sonarr_add_season_count,
        seasons,
        seasons,
    )
    return if (year != null) "$year · $seasonLabel" else seasonLabel
}

/** The series types the add sheet offers, with display labels. */
@Composable
private fun seriesTypeOptions(): List<Pair<SeriesTypes, String>> = listOf(
    SeriesTypes.STANDARD to stringResource(R.string.arr_sonarr_add_type_standard),
    SeriesTypes.ANIME to stringResource(R.string.arr_sonarr_add_type_anime),
    SeriesTypes.DAILY to stringResource(R.string.arr_sonarr_add_type_daily),
)

/** Monitor strategies exposed by the sheet, wire order preserved. */
@Composable
private fun monitorStrategyOptions(): List<Pair<MonitorTypes, String>> = listOf(
    MonitorTypes.ALL to stringResource(R.string.arr_sonarr_add_strategy_all),
    MonitorTypes.FUTURE to stringResource(R.string.arr_sonarr_add_strategy_future),
    MonitorTypes.MISSING to stringResource(R.string.arr_sonarr_add_strategy_missing),
    MonitorTypes.EXISTING to stringResource(R.string.arr_sonarr_add_strategy_existing),
    MonitorTypes.FIRSTSEASON to stringResource(R.string.arr_sonarr_add_strategy_first_season),
    MonitorTypes.LASTSEASON to stringResource(R.string.arr_sonarr_add_strategy_last_season),
    MonitorTypes.LATESTSEASON to stringResource(R.string.arr_sonarr_add_strategy_latest_season),
    MonitorTypes.PILOT to stringResource(R.string.arr_sonarr_add_strategy_pilot),
    MonitorTypes.RECENT to stringResource(R.string.arr_sonarr_add_strategy_recent),
    MonitorTypes.MONITORSPECIALS to
        stringResource(R.string.arr_sonarr_add_strategy_monitor_specials),
    MonitorTypes.UNMONITORSPECIALS to
        stringResource(R.string.arr_sonarr_add_strategy_unmonitor_specials),
    MonitorTypes.NONE to stringResource(R.string.arr_sonarr_add_strategy_none),
)
