package dev.appolon.homelab.arr.feature.lidarr.addnew.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.ImageLoader
import dev.appolon.homelab.arr.api.lidarr.model.AlbumResource
import dev.appolon.homelab.arr.api.lidarr.model.MonitorTypes
import dev.appolon.homelab.arr.feature.lidarr.R
import dev.appolon.homelab.arr.feature.lidarr.addnew.data.AddNewLogic
import dev.appolon.homelab.arr.feature.lidarr.addnew.vm.StudioPickerUiState
import dev.appolon.homelab.arr.ui.components.SectionHeader
import dev.appolon.homelab.arr.ui.lce.ArrLceBox
import dev.appolon.homelab.arr.ui.lce.LceState

/**
 * The AlbumStudio-style discography picker for one library artist: a bulk
 * preselection strategy, per-album monitored checkboxes grouped by album
 * type, and one Apply that sends the exact selection. Rows are 48dp tall
 * and read as one merged semantics node each.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudioPickerSheet(
    picker: StudioPickerUiState,
    baseUrl: String,
    imageLoader: ImageLoader?,
    onToggleAlbum: (Int) -> Unit,
    onStrategyChange: (MonitorTypes) -> Unit,
    onSelectAll: () -> Unit,
    onSelectNone: () -> Unit,
    onApply: () -> Unit,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
) {
    val artistTitle = picker.artist.artistName.orEmpty()
        .ifEmpty { picker.artist.foreignArtistId.orEmpty() }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(horizontal = 24.dp)) {
                Text(
                    text = stringResource(R.string.arr_lidarr_addnew_studio_title),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = artistTitle,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                ChoiceDropdown(
                    label = stringResource(R.string.arr_lidarr_addnew_studio_strategy),
                    selectedLabel = monitorLabel(picker.strategy),
                    choices = AddNewLogic.STUDIO_STRATEGIES.map { strategy ->
                        Choice(strategy, monitorLabel(strategy))
                    },
                    selected = picker.strategy,
                    onSelectedChange = onStrategyChange,
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    TextButton(onClick = onSelectAll) {
                        Text(stringResource(R.string.arr_lidarr_addnew_studio_select_all))
                    }
                    TextButton(onClick = onSelectNone) {
                        Text(stringResource(R.string.arr_lidarr_addnew_studio_select_none))
                    }
                }
            }
            val lce = if (picker.loading) {
                LceState.Loading
            } else if (picker.error != null && picker.albums.isEmpty()) {
                LceState.Error(picker.error)
            } else if (picker.albums.isEmpty()) {
                LceState.Empty
            } else {
                LceState.Content(picker.albums)
            }
            ArrLceBox(
                state = lce,
                onRetry = onRetry,
                emptyTitle = stringResource(R.string.arr_lidarr_addnew_studio_empty_title),
                emptyMessage = stringResource(R.string.arr_lidarr_addnew_studio_empty_message),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false),
            ) { albums ->
                LazyColumn(
                    contentPadding = PaddingValues(bottom = 8.dp),
                    modifier = Modifier.heightIn(max = 440.dp),
                ) {
                    val grouped = albums.groupBy { it.albumType.orEmpty().ifEmpty { "Album" } }
                    grouped.forEach { (type, group) ->
                        item(key = "header-$type") {
                            SectionHeader(
                                title = stringResource(
                                    R.string.arr_lidarr_addnew_studio_type_header,
                                    type.replaceFirstChar { it.uppercase() },
                                    group.size,
                                ),
                            )
                        }
                        items(group, key = { it.id }) { album ->
                            StudioAlbumRow(
                                album = album,
                                checked = picker.targetOf(album.id, album.monitored),
                                baseUrl = baseUrl,
                                imageLoader = imageLoader,
                                onToggle = { onToggleAlbum(album.id) },
                            )
                        }
                    }
                }
            }
            Button(
                onClick = onApply,
                enabled = !picker.applying && picker.albums.isNotEmpty(),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 24.dp, top = 4.dp),
            ) {
                if (picker.applying) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                    )
                } else if (picker.changedCount > 0) {
                    Text(
                        stringResource(
                            R.string.arr_lidarr_addnew_studio_apply,
                            picker.changedCount,
                        ),
                    )
                } else {
                    Text(stringResource(R.string.arr_lidarr_addnew_studio_apply_none))
                }
            }
        }
    }
}

/** One album row: cover, title, date, and a monitored checkbox on a 48dp target. */
@Composable
private fun StudioAlbumRow(
    album: AlbumResource,
    checked: Boolean,
    baseUrl: String,
    imageLoader: ImageLoader?,
    onToggle: () -> Unit,
) {
    val title = album.title.orEmpty().ifEmpty { album.foreignAlbumId.orEmpty() }
    val toggleLabel = stringResource(R.string.arr_lidarr_addnew_studio_cd_toggle_album, title)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(onClick = onToggle)
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .semantics(mergeDescendants = true) { contentDescription = toggleLabel },
    ) {
        CoverImage(
            url = AddNewLogic.albumCoverUrl(album, baseUrl),
            contentDescription = stringResource(
                R.string.arr_lidarr_addnew_cd_album_cover,
                title,
            ),
            imageLoader = imageLoader,
            width = 40.dp,
            height = 40.dp,
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            album.releaseDate?.takeIf { it.isNotBlank() }?.let {
                Text(
                    text = stringResource(
                        R.string.arr_lidarr_addnew_album_release_date,
                        it.take(10),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        Checkbox(checked = checked, onCheckedChange = null)
    }
}
