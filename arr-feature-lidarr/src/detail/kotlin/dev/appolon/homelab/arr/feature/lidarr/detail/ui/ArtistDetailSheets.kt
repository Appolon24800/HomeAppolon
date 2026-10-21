package dev.appolon.homelab.arr.feature.lidarr.detail.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
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
import dev.appolon.homelab.arr.api.lidarr.model.MetadataProfileResource
import dev.appolon.homelab.arr.api.lidarr.model.NewItemMonitorTypes
import dev.appolon.homelab.arr.api.lidarr.model.Quality
import dev.appolon.homelab.arr.api.lidarr.model.QualityProfileResource
import dev.appolon.homelab.arr.feature.lidarr.R
import dev.appolon.homelab.arr.feature.lidarr.detail.vm.ArtistEditForm
import dev.appolon.homelab.arr.feature.lidarr.detail.vm.TrackFileForm
import dev.appolon.homelab.arr.ui.components.LabeledSwitch

/**
 * Artist edit sheet: monitoring, monitor-new-items strategy, quality and
 * metadata profiles, path, root folder, tags, and the move-files flag.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditArtistSheet(
    form: ArtistEditForm,
    qualityProfiles: List<QualityProfileResource>,
    metadataProfiles: List<MetadataProfileResource>,
    onDismiss: () -> Unit,
    onUpdate: (ArtistEditForm) -> Unit,
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
                text = stringResource(R.string.arr_lidarr_detail_edit_title),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(bottom = 8.dp),
            )

            LabeledSwitch(
                label = stringResource(R.string.arr_lidarr_detail_edit_monitored),
                checked = form.monitored,
                onCheckedChange = { checked -> onUpdate(form.copy(monitored = checked)) },
            )

            Text(
                text = stringResource(R.string.arr_lidarr_detail_edit_monitor_new),
                modifier = Modifier.padding(top = 8.dp),
            )
            Column {
                monitorNewItemsOptions().forEach { (type, label) ->
                    SelectableRow(
                        label = label,
                        selected = form.monitorNewItems == type,
                        onClick = { onUpdate(form.copy(monitorNewItems = type)) },
                    )
                }
            }

            Text(
                text = stringResource(R.string.arr_lidarr_detail_edit_quality),
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

            Text(
                text = stringResource(R.string.arr_lidarr_detail_edit_metadata),
                modifier = Modifier.padding(top = 8.dp),
            )
            Column {
                metadataProfiles.forEach { profile ->
                    SelectableRow(
                        label = profile.name ?: "#${profile.id}",
                        selected = form.metadataProfileId == profile.id,
                        onClick = { onUpdate(form.copy(metadataProfileId = profile.id)) },
                    )
                }
            }

            OutlinedTextField(
                value = form.path,
                onValueChange = { value -> onUpdate(form.copy(path = value)) },
                label = { Text(stringResource(R.string.arr_lidarr_detail_edit_path)) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
            )

            OutlinedTextField(
                value = form.rootFolderPath,
                onValueChange = { value -> onUpdate(form.copy(rootFolderPath = value)) },
                label = { Text(stringResource(R.string.arr_lidarr_detail_edit_root_folder)) },
                supportingText = {
                    Text(stringResource(R.string.arr_lidarr_detail_edit_root_folder_hint))
                },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
            )

            OutlinedTextField(
                value = form.tagsText,
                onValueChange = { value ->
                    onUpdate(form.copy(tagsText = value, tagsError = false))
                },
                label = { Text(stringResource(R.string.arr_lidarr_detail_edit_tags)) },
                isError = form.tagsError,
                supportingText = if (form.tagsError) {
                    { Text(stringResource(R.string.arr_lidarr_detail_edit_tags_error)) }
                } else {
                    null
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp, top = 8.dp),
            )

            LabeledSwitch(
                label = stringResource(R.string.arr_lidarr_detail_edit_move_files),
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
                    Text(stringResource(R.string.arr_lidarr_detail_cancel))
                }
                Button(onClick = onSubmit) {
                    Text(stringResource(R.string.arr_lidarr_detail_edit_save))
                }
            }
        }
    }
}

@Composable
private fun monitorNewItemsOptions(): List<Pair<NewItemMonitorTypes, String>> = listOf(
    NewItemMonitorTypes.ALL to stringResource(R.string.arr_lidarr_monitor_new_all),
    NewItemMonitorTypes.NEW to stringResource(R.string.arr_lidarr_monitor_new_new),
    NewItemMonitorTypes.NONE to stringResource(R.string.arr_lidarr_monitor_new_none),
)

/**
 * Track file editor: path and media info read-only, quality picker, release
 * group, and a guarded delete. Deleting asks again because it removes the
 * file from disk.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackFileEditorSheet(
    form: TrackFileForm,
    qualities: List<Quality>,
    confirmDelete: Boolean,
    onConfirmDeleteChange: (Boolean) -> Unit,
    onDismiss: () -> Unit,
    onUpdate: (TrackFileForm) -> Unit,
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
                text = stringResource(R.string.arr_lidarr_detail_file_title),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(bottom = 8.dp),
            )

            form.original.path?.takeIf { it.isNotBlank() }?.let { path ->
                Text(
                    text = path,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            }
            form.original.mediaInfo?.let { info ->
                val line = listOfNotNull(
                    info.audioCodec,
                    info.audioBitRate,
                    info.audioSampleRate,
                ).joinToString(" · ")
                if (line.isNotEmpty()) {
                    Text(
                        text = line,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                }
            }

            Text(stringResource(R.string.arr_lidarr_detail_file_quality))
            Column {
                qualities.forEach { quality ->
                    SelectableRow(
                        label = quality.name ?: "#${quality.id}",
                        selected = form.qualityId == quality.id,
                        onClick = { onUpdate(form.copy(qualityId = quality.id)) },
                    )
                }
            }

            OutlinedTextField(
                value = form.releaseGroup,
                onValueChange = { value -> onUpdate(form.copy(releaseGroup = value)) },
                label = { Text(stringResource(R.string.arr_lidarr_detail_file_release_group)) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp, bottom = 8.dp),
            )

            TextButton(
                onClick = { onConfirmDeleteChange(true) },
                modifier = Modifier.align(Alignment.Start),
            ) {
                Text(
                    text = stringResource(R.string.arr_lidarr_detail_file_delete),
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
                    Text(stringResource(R.string.arr_lidarr_detail_cancel))
                }
                Button(onClick = onSubmit) {
                    Text(stringResource(R.string.arr_lidarr_detail_file_save))
                }
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { onConfirmDeleteChange(false) },
            title = { Text(stringResource(R.string.arr_lidarr_detail_file_delete_title)) },
            text = { Text(stringResource(R.string.arr_lidarr_detail_file_delete_message)) },
            confirmButton = {
                TextButton(onClick = {
                    onConfirmDeleteChange(false)
                    onDelete()
                }) {
                    Text(
                        text = stringResource(R.string.arr_lidarr_detail_file_delete),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { onConfirmDeleteChange(false) }) {
                    Text(stringResource(R.string.arr_lidarr_detail_cancel))
                }
            },
        )
    }
}

/** Artist delete confirmation with the two web UI flags. */
@Composable
fun DeleteArtistDialog(
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
        title = { Text(stringResource(R.string.arr_lidarr_detail_delete_title)) },
        text = {
            Column {
                Text(stringResource(R.string.arr_lidarr_detail_delete_message, title))
                LabeledSwitch(
                    label = stringResource(R.string.arr_lidarr_detail_delete_files),
                    checked = deleteFiles,
                    onCheckedChange = onDeleteFilesChange,
                )
                LabeledSwitch(
                    label = stringResource(R.string.arr_lidarr_detail_delete_exclusion),
                    checked = addExclusion,
                    onCheckedChange = onAddExclusionChange,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    text = stringResource(R.string.arr_lidarr_detail_delete_confirm),
                    color = MaterialTheme.colorScheme.error,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.arr_lidarr_detail_cancel))
            }
        },
    )
}

/** Full-width single-choice row with a leading radio, at least 48dp tall. */
@Composable
private fun SelectableRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(start = 4.dp),
        )
    }
}
