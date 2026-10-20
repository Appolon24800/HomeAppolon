package dev.appolon.homelab.arr.feature.radarr.detail

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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
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
import dev.appolon.homelab.arr.core.Humanize
import dev.appolon.homelab.arr.feature.radarr.R
import dev.appolon.homelab.arr.ui.components.LabeledSwitch

/**
 * Edit sheet: monitoring, quality profile, path (with a move-files toggle),
 * and tags. Saves build a full Movie update from the current resource.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RadarrMovieEditSheet(
    movie: MovieDetailUi,
    options: EditOptionsUi,
    working: Boolean,
    onSave: (EditableMovieUi, Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    var monitored by remember { mutableStateOf(movie.monitored) }
    var qualityProfileId by remember { mutableStateOf(movie.qualityProfileId) }
    var path by remember { mutableStateOf(movie.path.orEmpty()) }
    var moveFiles by remember { mutableStateOf(false) }
    var tags by remember { mutableStateOf(movie.tags) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.arr_radarr_detail_edit_title)) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.verticalScroll(rememberScrollState()),
            ) {
                LabeledSwitch(
                    label = stringResource(R.string.arr_radarr_detail_monitored),
                    checked = monitored,
                    onCheckedChange = { monitored = it },
                    supportingText = stringResource(R.string.arr_radarr_detail_monitored_support),
                )
                Text(
                    text = stringResource(R.string.arr_radarr_detail_edit_quality),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 8.dp),
                )
                options.qualityProfiles.forEach { profile ->
                    QualityRadioRow(
                        label = profile.name,
                        selected = qualityProfileId == profile.id,
                        onSelect = { qualityProfileId = profile.id },
                    )
                }
                OutlinedTextField(
                    value = path,
                    onValueChange = { path = it },
                    label = { Text(stringResource(R.string.arr_radarr_detail_edit_path)) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                )
                LabeledSwitch(
                    label = stringResource(R.string.arr_radarr_detail_edit_move),
                    checked = moveFiles,
                    onCheckedChange = { moveFiles = it },
                    supportingText = stringResource(R.string.arr_radarr_detail_edit_move_support),
                )
                if (options.tags.isNotEmpty()) {
                    Text(
                        text = stringResource(R.string.arr_radarr_detail_edit_tags),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        options.tags.forEach { tag ->
                            FilterChip(
                                selected = tag.id in tags,
                                onClick = {
                                    tags = if (tag.id in tags) tags - tag.id else tags + tag.id
                                },
                                label = { Text(tag.name) },
                            )
                        }
                    }
                }
                if (working) {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(
                        EditableMovieUi(
                            monitored = monitored,
                            qualityProfileId = qualityProfileId,
                            minimumAvailability = movie.minimumAvailability,
                            path = path,
                            tags = tags,
                        ),
                        moveFiles,
                    )
                },
            ) {
                Text(stringResource(R.string.arr_radarr_detail_edit_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.arr_radarr_detail_cancel))
            }
        },
    )
}

@Composable
private fun QualityRadioRow(label: String, selected: Boolean, onSelect: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(start = 12.dp),
        )
    }
}

/** Delete confirmation: with or without files, plus import exclusion. */
@Composable
fun RadarrMovieDeleteDialog(
    title: String,
    hasFiles: Boolean,
    working: Boolean,
    onConfirm: (deleteFiles: Boolean, addImportExclusion: Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    var deleteFiles by remember { mutableStateOf(false) }
    var addExclusion by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.arr_radarr_detail_delete_title)) },
        text = {
            Column {
                Text(stringResource(R.string.arr_radarr_detail_delete_message, title))
                if (hasFiles) {
                    LabeledSwitch(
                        label = stringResource(R.string.arr_radarr_detail_delete_files),
                        checked = deleteFiles,
                        onCheckedChange = { deleteFiles = it },
                        supportingText = stringResource(
                            R.string.arr_radarr_detail_delete_files_support,
                        ),
                    )
                }
                LabeledSwitch(
                    label = stringResource(R.string.arr_radarr_detail_delete_exclusion),
                    checked = addExclusion,
                    onCheckedChange = { addExclusion = it },
                    supportingText = stringResource(
                        R.string.arr_radarr_detail_delete_exclusion_support,
                    ),
                )
                if (working) {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(deleteFiles, addExclusion) }) {
                Text(
                    text = stringResource(R.string.arr_radarr_detail_delete_confirm),
                    color = MaterialTheme.colorScheme.error,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.arr_radarr_detail_cancel))
            }
        },
    )
}

/**
 * Rename preview: lists every file that the rename command would touch.
 * Radarr computes final names server-side from its naming settings, so the
 * preview shows the affected files and their folder.
 */
@Composable
fun RadarrMovieRenameDialog(
    files: List<MovieFileUi>,
    working: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.arr_radarr_detail_rename_title)) },
        text = {
            Column {
                Text(stringResource(R.string.arr_radarr_detail_rename_message))
                files.take(PREVIEW_LIMIT).forEach { file ->
                    Text(
                        text = file.relativePath,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
                if (files.size > PREVIEW_LIMIT) {
                    Text(
                        text = stringResource(
                            R.string.arr_radarr_detail_rename_more,
                            files.size - PREVIEW_LIMIT,
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (files.isNotEmpty()) {
                    Text(
                        text = Humanize.bytes(files.sumOf { file -> file.sizeBytes }),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
                if (working) {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.arr_radarr_detail_rename_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.arr_radarr_detail_cancel))
            }
        },
    )
}

private const val PREVIEW_LIMIT = 8
