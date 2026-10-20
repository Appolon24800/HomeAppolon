package dev.appolon.homelab.arr.feature.sonarr.detail.ui

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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import dev.appolon.homelab.arr.api.sonarr.model.LanguageResource
import dev.appolon.homelab.arr.api.sonarr.model.MonitorTypes
import dev.appolon.homelab.arr.api.sonarr.model.Quality
import dev.appolon.homelab.arr.api.sonarr.model.QualityProfileResource
import dev.appolon.homelab.arr.api.sonarr.model.SeriesTypes
import dev.appolon.homelab.arr.feature.sonarr.R
import dev.appolon.homelab.arr.feature.sonarr.detail.vm.EpisodeFileForm
import dev.appolon.homelab.arr.feature.sonarr.detail.vm.SeasonPassForm
import dev.appolon.homelab.arr.feature.sonarr.detail.vm.SeriesEditForm
import dev.appolon.homelab.arr.feature.sonarr.library.ui.SelectableRow
import dev.appolon.homelab.arr.ui.components.LabeledSwitch

/**
 * Series edit sheet: monitoring, series type, quality profile, path, season
 * folders, tags, and the move-files flag. The "monitoring strategy" button
 * hands off through [onOpenSeasonPass] so the host decides where the season
 * pass flow lives.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditSeriesSheet(
    form: SeriesEditForm,
    qualityProfiles: List<QualityProfileResource>,
    tagLabels: (Int) -> String,
    onDismiss: () -> Unit,
    onUpdate: (SeriesEditForm) -> Unit,
    onSubmit: () -> Unit,
    onOpenSeasonPass: () -> Unit,
) {
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
                text = stringResource(R.string.arr_sonarr_detail_edit_title),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(bottom = 8.dp),
            )

            LabeledSwitch(
                label = stringResource(R.string.arr_sonarr_detail_edit_monitored),
                checked = form.monitored,
                onCheckedChange = { checked -> onUpdate(form.copy(monitored = checked)) },
            )

            Text(
                text = stringResource(R.string.arr_sonarr_detail_edit_type),
                modifier = Modifier.padding(top = 8.dp),
            )
            Column {
                seriesTypeOptions().forEach { (type, label) ->
                    SelectableRow(
                        label = label,
                        selected = form.seriesType == type,
                        onClick = { onUpdate(form.copy(seriesType = type)) },
                    )
                }
            }

            Text(
                text = stringResource(R.string.arr_sonarr_detail_edit_quality),
                modifier = Modifier.padding(top = 8.dp),
            )
            Column {
                qualityProfiles.forEach { profile ->
                    SelectableRow(
                        label = profile.name ?: "#${profile.id}",
                        selected = form.qualityProfileId == profile.id,
                        onClick = { onUpdate(form.copy(qualityProfileId = profile.id)) },
                    )
                }
            }

            OutlinedTextField(
                value = form.path,
                onValueChange = { value -> onUpdate(form.copy(path = value)) },
                label = { Text(stringResource(R.string.arr_sonarr_detail_edit_path)) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
            )

            LabeledSwitch(
                label = stringResource(R.string.arr_sonarr_detail_edit_season_folder),
                checked = form.seasonFolder,
                onCheckedChange = { checked -> onUpdate(form.copy(seasonFolder = checked)) },
            )

            OutlinedTextField(
                value = form.tagsText,
                onValueChange = { value -> onUpdate(form.copy(tagsText = value, tagsError = false)) },
                label = { Text(stringResource(R.string.arr_sonarr_detail_edit_tags)) },
                isError = form.tagsError,
                supportingText = if (form.tagsError) {
                    { Text(stringResource(R.string.arr_sonarr_detail_edit_tags_error)) }
                } else {
                    null
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
            )

            LabeledSwitch(
                label = stringResource(R.string.arr_sonarr_detail_edit_move_files),
                checked = form.moveFiles,
                onCheckedChange = { checked -> onUpdate(form.copy(moveFiles = checked)) },
            )

            OutlinedButton(
                onClick = onOpenSeasonPass,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
            ) {
                Text(stringResource(R.string.arr_sonarr_detail_edit_season_pass))
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .align(Alignment.End)
                    .padding(vertical = 16.dp),
            ) {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.arr_sonarr_detail_cancel))
                }
                Button(onClick = onSubmit) {
                    Text(stringResource(R.string.arr_sonarr_detail_edit_save))
                }
            }
        }
    }
}

@Composable
private fun seriesTypeOptions(): List<Pair<SeriesTypes, String>> = listOf(
    SeriesTypes.STANDARD to stringResource(R.string.arr_sonarr_type_standard),
    SeriesTypes.ANIME to stringResource(R.string.arr_sonarr_type_anime),
    SeriesTypes.DAILY to stringResource(R.string.arr_sonarr_type_daily),
)

/**
 * Monitoring strategy sheet scoped to this one series: the season pass
 * endpoint applied to a single show.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SeasonPassStrategySheet(
    form: SeasonPassForm,
    onDismiss: () -> Unit,
    onUpdate: (SeasonPassForm) -> Unit,
    onSubmit: () -> Unit,
) {
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
                text = stringResource(R.string.arr_sonarr_detail_season_pass_title),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(bottom = 8.dp),
            )
            LabeledSwitch(
                label = stringResource(R.string.arr_sonarr_detail_season_pass_monitored),
                checked = form.monitored,
                onCheckedChange = { checked -> onUpdate(form.copy(monitored = checked)) },
            )
            Text(
                text = stringResource(R.string.arr_sonarr_detail_season_pass_strategy),
                modifier = Modifier.padding(top = 8.dp),
            )
            Column {
                monitorStrategyOptions().forEach { (type, label) ->
                    SelectableRow(
                        label = label,
                        selected = form.monitor == type,
                        onClick = { onUpdate(form.copy(monitor = type)) },
                    )
                }
            }
            LabeledSwitch(
                label = stringResource(R.string.arr_sonarr_detail_season_pass_ignore_with_files),
                checked = form.ignoreEpisodesWithFiles,
                onCheckedChange = { checked ->
                    onUpdate(form.copy(ignoreEpisodesWithFiles = checked))
                },
            )
            LabeledSwitch(
                label = stringResource(R.string.arr_sonarr_detail_season_pass_ignore_without_files),
                checked = form.ignoreEpisodesWithoutFiles,
                onCheckedChange = { checked ->
                    onUpdate(form.copy(ignoreEpisodesWithoutFiles = checked))
                },
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .align(Alignment.End)
                    .padding(vertical = 16.dp),
            ) {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.arr_sonarr_detail_cancel))
                }
                Button(onClick = onSubmit) {
                    Text(stringResource(R.string.arr_sonarr_detail_season_pass_submit))
                }
            }
        }
    }
}

@Composable
private fun monitorStrategyOptions(): List<Pair<MonitorTypes, String>> = listOf(
    MonitorTypes.ALL to stringResource(R.string.arr_sonarr_strategy_all),
    MonitorTypes.FUTURE to stringResource(R.string.arr_sonarr_strategy_future),
    MonitorTypes.MISSING to stringResource(R.string.arr_sonarr_strategy_missing),
    MonitorTypes.EXISTING to stringResource(R.string.arr_sonarr_strategy_existing),
    MonitorTypes.FIRSTSEASON to stringResource(R.string.arr_sonarr_strategy_first_season),
    MonitorTypes.LASTSEASON to stringResource(R.string.arr_sonarr_strategy_last_season),
    MonitorTypes.LATESTSEASON to stringResource(R.string.arr_sonarr_strategy_latest_season),
    MonitorTypes.PILOT to stringResource(R.string.arr_sonarr_strategy_pilot),
    MonitorTypes.RECENT to stringResource(R.string.arr_sonarr_strategy_recent),
    MonitorTypes.MONITORSPECIALS to stringResource(R.string.arr_sonarr_strategy_monitor_specials),
    MonitorTypes.UNMONITORSPECIALS to stringResource(R.string.arr_sonarr_strategy_unmonitor_specials),
    MonitorTypes.NONE to stringResource(R.string.arr_sonarr_strategy_none),
)

/**
 * Episode file editor: quality picker, language chips, release group, and a
 * guarded delete. Deleting asks again because it removes the file from disk.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun EpisodeFileEditorSheet(
    form: EpisodeFileForm,
    qualities: List<Quality>,
    languages: List<LanguageResource>,
    qualityLabel: (Int) -> String,
    languageLabel: (Int) -> String,
    confirmDelete: Boolean,
    onConfirmDeleteChange: (Boolean) -> Unit,
    onDismiss: () -> Unit,
    onUpdate: (EpisodeFileForm) -> Unit,
    onSubmit: () -> Unit,
    onDelete: () -> Unit,
) {
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
                text = stringResource(R.string.arr_sonarr_detail_file_title),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(bottom = 8.dp),
            )

            Text(stringResource(R.string.arr_sonarr_detail_file_quality))
            Column {
                qualities.forEach { quality ->
                    SelectableRow(
                        label = quality.name ?: "#${quality.id}",
                        selected = form.qualityId == quality.id,
                        onClick = { onUpdate(form.copy(qualityId = quality.id)) },
                    )
                }
            }

            Text(
                text = stringResource(R.string.arr_sonarr_detail_file_languages),
                modifier = Modifier.padding(top = 8.dp),
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(bottom = 8.dp),
            ) {
                languages.forEach { language ->
                    FilterChip(
                        selected = language.id in form.languageIds,
                        onClick = {
                            val next = if (language.id in form.languageIds) {
                                form.languageIds - language.id
                            } else {
                                form.languageIds + language.id
                            }
                            onUpdate(form.copy(languageIds = next))
                        },
                        label = { Text(language.name ?: "#${language.id}") },
                    )
                }
            }

            OutlinedTextField(
                value = form.releaseGroup,
                onValueChange = { value -> onUpdate(form.copy(releaseGroup = value)) },
                label = { Text(stringResource(R.string.arr_sonarr_detail_file_release_group)) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
            )

            TextButton(
                onClick = { onConfirmDeleteChange(true) },
                modifier = Modifier.align(Alignment.Start),
            ) {
                Text(
                    text = stringResource(R.string.arr_sonarr_detail_file_delete),
                    color = MaterialTheme.colorScheme.error,
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .align(Alignment.End)
                    .padding(vertical = 16.dp),
            ) {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.arr_sonarr_detail_cancel))
                }
                Button(onClick = onSubmit) {
                    Text(stringResource(R.string.arr_sonarr_detail_file_save))
                }
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { onConfirmDeleteChange(false) },
            title = { Text(stringResource(R.string.arr_sonarr_detail_file_delete_title)) },
            text = { Text(stringResource(R.string.arr_sonarr_detail_file_delete_message)) },
            confirmButton = {
                TextButton(onClick = {
                    onConfirmDeleteChange(false)
                    onDelete()
                }) {
                    Text(
                        text = stringResource(R.string.arr_sonarr_detail_file_delete),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { onConfirmDeleteChange(false) }) {
                    Text(stringResource(R.string.arr_sonarr_detail_cancel))
                }
            },
        )
    }
}

/** Series delete confirmation with the two web UI flags. */
@Composable
fun DeleteSeriesDialog(
    title: String,
    deleteFiles: Boolean,
    addExclusion: Boolean,
    onDeleteFilesChange: (Boolean) -> Unit,
    onAddExclusionChange: (Boolean) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.arr_sonarr_detail_delete_title)) },
        text = {
            Column {
                Text(stringResource(R.string.arr_sonarr_detail_delete_message, title))
                LabeledSwitch(
                    label = stringResource(R.string.arr_sonarr_detail_delete_files),
                    checked = deleteFiles,
                    onCheckedChange = onDeleteFilesChange,
                )
                LabeledSwitch(
                    label = stringResource(R.string.arr_sonarr_detail_delete_exclusion),
                    checked = addExclusion,
                    onCheckedChange = onAddExclusionChange,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    text = stringResource(R.string.arr_sonarr_detail_delete_confirm),
                    color = MaterialTheme.colorScheme.error,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.arr_sonarr_detail_cancel))
            }
        },
    )
}
