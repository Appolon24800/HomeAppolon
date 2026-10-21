package dev.appolon.homelab.arr.feature.lidarr.detail.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.appolon.homelab.arr.api.lidarr.model.ReleaseResource
import dev.appolon.homelab.arr.api.lidarr.model.RenameTrackResource
import dev.appolon.homelab.arr.api.lidarr.model.RetagTrackResource
import dev.appolon.homelab.arr.core.ArrError
import dev.appolon.homelab.arr.core.Humanize
import dev.appolon.homelab.arr.feature.lidarr.R
import dev.appolon.homelab.arr.feature.lidarr.detail.vm.ImportState
import dev.appolon.homelab.arr.feature.lidarr.detail.vm.RenamePreviewState
import dev.appolon.homelab.arr.feature.lidarr.detail.vm.RetagPreviewState
import dev.appolon.homelab.arr.feature.lidarr.detail.vm.SearchTarget
import dev.appolon.homelab.arr.ui.components.EmptyState
import dev.appolon.homelab.arr.ui.lce.arrErrorMessage

/**
 * Rename preview: every file the server would rename under the current
 * naming scheme, old path above new path, with the rename as the confirm
 * action.
 */
@Composable
fun RenamePreviewDialog(
    preview: RenamePreviewState,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.arr_lidarr_detail_rename_title)) },
        text = {
            Column {
                when {
                    preview.loading -> Row(
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp),
                    ) {
                        CircularProgressIndicator()
                    }

                    preview.error != null -> Text(
                        text = arrErrorMessage(preview.error),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    preview.rows.isEmpty() -> Text(
                        text = stringResource(R.string.arr_lidarr_detail_rename_empty),
                        style = MaterialTheme.typography.bodyMedium,
                    )

                    else -> {
                        Text(
                            text = stringResource(R.string.arr_lidarr_detail_rename_note),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 8.dp),
                        )
                        LazyColumn(modifier = Modifier.heightIn(max = 320.dp)) {
                            items(preview.rows.size, key = { preview.rows[it].id }) { index ->
                                RenameRow(preview.rows[index])
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (!preview.loading && preview.error == null && preview.rows.isNotEmpty()) {
                Button(onClick = onConfirm) {
                    Text(stringResource(R.string.arr_lidarr_detail_rename_confirm))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.arr_lidarr_detail_cancel))
            }
        },
    )
}

@Composable
private fun RenameRow(row: RenameTrackResource) {
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Text(
            text = row.existingPath.orEmpty(),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(4.dp))
            Text(
                text = row.newPath.orEmpty(),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * Retag preview: every file whose MusicBrainz tags differ from the metadata,
 * each difference as "field: old to new", with the retag as the confirm
 * action.
 */
@Composable
fun RetagPreviewDialog(
    preview: RetagPreviewState,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.arr_lidarr_detail_retag_title)) },
        text = {
            Column {
                when {
                    preview.loading -> Row(
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp),
                    ) {
                        CircularProgressIndicator()
                    }

                    preview.error != null -> Text(
                        text = arrErrorMessage(preview.error),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    preview.rows.isEmpty() -> Text(
                        text = stringResource(R.string.arr_lidarr_detail_retag_empty),
                        style = MaterialTheme.typography.bodyMedium,
                    )

                    else -> {
                        Text(
                            text = stringResource(R.string.arr_lidarr_detail_retag_note),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 8.dp),
                        )
                        LazyColumn(modifier = Modifier.heightIn(max = 320.dp)) {
                            items(preview.rows.size, key = { preview.rows[it].id }) { index ->
                                RetagRow(preview.rows[index])
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (!preview.loading && preview.error == null && preview.rows.isNotEmpty()) {
                Button(onClick = onConfirm) {
                    Text(stringResource(R.string.arr_lidarr_detail_retag_confirm))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.arr_lidarr_detail_cancel))
            }
        },
    )
}

@Composable
private fun RetagRow(row: RetagTrackResource) {
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Text(
            text = row.path.orEmpty(),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        row.changes.orEmpty().forEach { change ->
            val old = change.oldValue.orEmpty().ifBlank {
                stringResource(R.string.arr_lidarr_detail_retag_blank)
            }
            val new = change.newValue.orEmpty().ifBlank {
                stringResource(R.string.arr_lidarr_detail_retag_blank)
            }
            Text(
                text = stringResource(
                    R.string.arr_lidarr_detail_retag_change,
                    change.field.orEmpty(),
                    old,
                    new,
                ),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

/**
 * Manual search sheet for one album or the whole artist: every release the
 * indexers returned, with a grab button per row.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManualSearchSheet(
    target: SearchTarget,
    releases: List<ReleaseResource>,
    loading: Boolean,
    error: ArrError?,
    onDismiss: () -> Unit,
    onRefresh: () -> Unit,
    onGrab: (ReleaseResource) -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(Modifier.fillMaxWidth()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 4.dp, bottom = 4.dp),
            ) {
                val title = when (target) {
                    is SearchTarget.Album -> stringResource(
                        R.string.arr_lidarr_detail_search_title_album,
                        target.label,
                    )

                    SearchTarget.Artist -> stringResource(
                        R.string.arr_lidarr_detail_search_title_artist,
                    )
                }
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onRefresh) {
                    Icon(
                        imageVector = Icons.Filled.Refresh,
                        contentDescription = stringResource(
                            R.string.arr_lidarr_detail_cd_refresh_releases,
                        ),
                    )
                }
            }
            when {
                loading -> Row(
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp),
                ) {
                    CircularProgressIndicator()
                }

                error != null -> Column(Modifier.padding(16.dp)) {
                    Text(
                        text = arrErrorMessage(error),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    TextButton(onClick = onRefresh) {
                        Text(stringResource(R.string.arr_lidarr_detail_cd_refresh_releases))
                    }
                }

                releases.isEmpty() -> EmptyState(
                    title = stringResource(R.string.arr_lidarr_detail_search_empty),
                    message = "",
                )

                else -> LazyColumn(Modifier.heightIn(max = 480.dp)) {
                    items(
                        releases.size,
                        key = {
                            releases[it].guid
                                ?: releases[it].id.toString() + releases[it].title
                        },
                    ) { index ->
                        ReleaseRow(release = releases[index], onGrab = { onGrab(releases[index]) })
                    }
                }
            }
            Spacer(Modifier.size(12.dp))
        }
    }
}

@Composable
private fun ReleaseRow(release: ReleaseResource, onGrab: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onGrab)
            .padding(start = 16.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = release.title.orEmpty(),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            val quality = release.quality?.quality?.name?.let(Humanize::qualityName).orEmpty()
            Text(
                text = stringResource(
                    R.string.arr_lidarr_detail_track_file_line,
                    quality,
                    Humanize.bytes(release.size),
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val details = listOfNotNull(
                release.indexer,
                release.seeders?.let {
                    stringResource(R.string.arr_lidarr_detail_release_seeders, it)
                },
            ).joinToString(" · ")
            if (details.isNotEmpty()) {
                Text(
                    text = details,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        IconButton(onClick = onGrab) {
            Icon(
                imageVector = Icons.Filled.CloudDownload,
                contentDescription = stringResource(
                    R.string.arr_lidarr_detail_cd_grab_release,
                    release.title.orEmpty(),
                ),
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

/**
 * Interactive import: every candidate the server found under the artist
 * folder, checked rows building the import payload.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InteractiveImportSheet(
    state: ImportState,
    onDismiss: () -> Unit,
    onToggleSelected: (Int) -> Unit,
    onImport: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(Modifier.fillMaxWidth()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.arr_lidarr_detail_import_title),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = stringResource(R.string.arr_lidarr_detail_import_scope),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    text = stringResource(
                        R.string.arr_lidarr_detail_import_selected_count,
                        state.selected.size,
                    ),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            when {
                state.loading -> Row(
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp),
                ) {
                    CircularProgressIndicator()
                }

                state.error != null -> Column(Modifier.padding(16.dp)) {
                    Text(
                        text = arrErrorMessage(state.error),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                state.candidates.isEmpty() -> EmptyState(
                    title = stringResource(R.string.arr_lidarr_detail_import_empty),
                    message = "",
                )

                else -> LazyColumn(Modifier.heightIn(max = 420.dp)) {
                    items(state.candidates.size, key = { state.candidates[it].id }) { index ->
                        val candidate = state.candidates[index]
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onToggleSelected(candidate.id) }
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                        ) {
                            Checkbox(
                                checked = candidate.id in state.selected,
                                onCheckedChange = { onToggleSelected(candidate.id) },
                            )
                            Column(Modifier.weight(1f)) {
                                Text(
                                    text = candidate.name
                                        ?: candidate.path.orEmpty(),
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                val quality =
                                    candidate.quality?.quality?.name?.let(Humanize::qualityName)
                                val line = quality?.let { q ->
                                    stringResource(
                                        R.string.arr_lidarr_detail_track_file_line,
                                        q,
                                        Humanize.bytes(candidate.size),
                                    )
                                } ?: Humanize.bytes(candidate.size)
                                Text(
                                    text = line,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
            Row(
                horizontalArrangement = Arrangement.End,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.arr_lidarr_detail_cancel))
                }
                Spacer(Modifier.width(8.dp))
                Button(onClick = onImport, enabled = state.selected.isNotEmpty()) {
                    Text(importActionLabel(state.selected.size))
                }
            }
        }
    }
}
