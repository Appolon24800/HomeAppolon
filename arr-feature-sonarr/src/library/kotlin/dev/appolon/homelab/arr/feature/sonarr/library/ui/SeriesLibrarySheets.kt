package dev.appolon.homelab.arr.feature.sonarr.library.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
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
import dev.appolon.homelab.arr.api.sonarr.model.ApplyTags
import dev.appolon.homelab.arr.api.sonarr.model.MonitorTypes
import dev.appolon.homelab.arr.api.sonarr.model.QualityProfileResource
import dev.appolon.homelab.arr.api.sonarr.model.SeriesTypes
import dev.appolon.homelab.arr.feature.sonarr.R
import dev.appolon.homelab.arr.feature.sonarr.library.vm.SeasonPassForm
import dev.appolon.homelab.arr.feature.sonarr.library.vm.SeriesEditorForm
import dev.appolon.homelab.arr.ui.components.LabeledSwitch
import dev.appolon.homelab.arr.ui.R as ArrUiR

/**
 * Bulk series editor sheet. Null form fields mean "leave unchanged"; the
 * series type has no unset wire form, so leaving it unchosen keeps each
 * selection's current type when uniform and falls back to standard for mixed
 * selections (the ViewModel documents the exact rule).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SeriesEditorSheet(
    form: SeriesEditorForm,
    targets: Int,
    qualityProfiles: List<QualityProfileResource>,
    onDismiss: () -> Unit,
    onUpdate: (SeriesEditorForm) -> Unit,
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
                text = stringResource(R.string.arr_sonarr_detail_menu_edit),
                style = MaterialTheme.typography.titleLarge,
            )
            Text(
                text = stringResource(R.string.arr_sonarr_editor_targets, targets),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 12.dp),
            )

            Text(stringResource(ArrUiR.string.arr_common_monitored))
            TriStateChips(
                value = form.monitored,
                onChange = { value -> onUpdate(form.copy(monitored = value)) },
                labelFor = { option ->
                    stringResource(
                        when (option) {
                            true -> ArrUiR.string.arr_common_yes
                            false -> ArrUiR.string.arr_common_no
                            else -> R.string.arr_sonarr_editor_monitored_unset
                        },
                    )
                },
                modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
            )

            Text(stringResource(ArrUiR.string.arr_common_wanted_filter_quality))
            Column(Modifier.padding(bottom = 12.dp)) {
                SelectableRow(
                    label = stringResource(R.string.arr_sonarr_editor_monitored_unset),
                    selected = form.qualityProfileId == null,
                    onClick = { onUpdate(form.copy(qualityProfileId = null)) },
                )
                qualityProfiles.forEach { profile ->
                    SelectableRow(
                        label = profile.name ?: "#${profile.id}",
                        selected = form.qualityProfileId == profile.id,
                        onClick = { onUpdate(form.copy(qualityProfileId = profile.id)) },
                    )
                }
            }

            Text(stringResource(R.string.arr_sonarr_list_series_type))
            Column(Modifier.padding(bottom = 12.dp)) {
                seriesTypeOptions().forEach { (type, label) ->
                    SelectableRow(
                        label = label,
                        selected = form.seriesType == type,
                        onClick = {
                            onUpdate(
                                form.copy(seriesType = if (form.seriesType == type) null else type),
                            )
                        },
                    )
                }
            }

            Text(stringResource(R.string.arr_sonarr_add_season_folder))
            TriStateChips(
                value = form.seasonFolder,
                onChange = { value -> onUpdate(form.copy(seasonFolder = value)) },
                labelFor = { option ->
                    stringResource(
                        when (option) {
                            true -> ArrUiR.string.arr_common_yes
                            false -> ArrUiR.string.arr_common_no
                            else -> R.string.arr_sonarr_editor_monitored_unset
                        },
                    )
                },
                modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
            )

            OutlinedTextField(
                value = form.rootFolderPath,
                onValueChange = { value -> onUpdate(form.copy(rootFolderPath = value)) },
                label = { Text(stringResource(ArrUiR.string.arr_common_list_root_folder)) },
                singleLine = true,
                isError = false,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
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
                    .padding(bottom = 12.dp),
            )

            Text(stringResource(R.string.arr_sonarr_editor_apply_tags))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
            ) {
                listOf(
                    ApplyTags.ADD to ArrUiR.string.arr_common_roots_add_confirm,
                    ApplyTags.REMOVE to ArrUiR.string.arr_common_queue_action_remove,
                    ApplyTags.REPLACE to ArrUiR.string.arr_common_editor_tags_replace,
                ).forEach { (mode, labelRes) ->
                    FilterChip(
                        selected = form.applyTags == mode,
                        onClick = { onUpdate(form.copy(applyTags = mode)) },
                        label = { Text(stringResource(labelRes)) },
                    )
                }
            }

            LabeledSwitch(
                label = stringResource(ArrUiR.string.arr_common_detail_edit_move),
                checked = form.moveFiles,
                onCheckedChange = { checked -> onUpdate(form.copy(moveFiles = checked)) },
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .align(Alignment.End)
                    .padding(vertical = 16.dp),
            ) {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(ArrUiR.string.arr_common_cancel))
                }
                Button(onClick = onSubmit) {
                    Text(stringResource(R.string.arr_sonarr_editor_submit))
                }
            }
        }
    }
}

/** The series types Sonarr's editor can bulk-assign, with display labels. */
@Composable
private fun seriesTypeOptions(): List<Pair<SeriesTypes, String>> = listOf(
    SeriesTypes.STANDARD to stringResource(R.string.arr_sonarr_add_type_standard),
    SeriesTypes.ANIME to stringResource(R.string.arr_sonarr_add_type_anime),
    SeriesTypes.DAILY to stringResource(R.string.arr_sonarr_add_type_daily),
)

/**
 * Season pass sheet: bulk monitoring across selections. The monitor strategy
 * travels in the request; the server recomputes per-episode state from it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SeasonPassSheet(
    form: SeasonPassForm,
    targets: Int,
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
                text = stringResource(R.string.arr_sonarr_season_pass_title),
                style = MaterialTheme.typography.titleLarge,
            )
            Text(
                text = stringResource(R.string.arr_sonarr_season_pass_targets, targets),
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
                text = stringResource(R.string.arr_sonarr_list_monitor),
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
                    Text(stringResource(ArrUiR.string.arr_common_cancel))
                }
                Button(onClick = onSubmit) {
                    Text(stringResource(R.string.arr_sonarr_detail_season_pass_submit))
                }
            }
        }
    }
}

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
    MonitorTypes.MONITORSPECIALS to stringResource(R.string.arr_sonarr_add_strategy_monitor_specials),
    MonitorTypes.UNMONITORSPECIALS to stringResource(R.string.arr_sonarr_add_strategy_unmonitor_specials),
    MonitorTypes.NONE to stringResource(R.string.arr_sonarr_add_strategy_none),
)
