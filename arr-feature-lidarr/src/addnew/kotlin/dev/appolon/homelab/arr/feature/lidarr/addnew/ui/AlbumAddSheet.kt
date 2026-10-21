package dev.appolon.homelab.arr.feature.lidarr.addnew.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.ImageLoader
import coil3.compose.AsyncImage
import dev.appolon.homelab.arr.feature.lidarr.R
import dev.appolon.homelab.arr.feature.lidarr.addnew.data.AddNewLogic
import dev.appolon.homelab.arr.feature.lidarr.addnew.vm.AlbumAddSheetUiState
import androidx.compose.material3.Switch

/**
 * The standalone album add sheet opened from an album search result. Lidarr
 * files standalone albums under a library artist: when the candidate's
 * artist is already added the sheet offers add and add-and-search; otherwise
 * it explains the rule and offers to open the artist add flow instead.
 * Content is capped at 640dp and centered for tablets.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlbumAddSheet(
    sheet: AlbumAddSheetUiState,
    baseUrl: String,
    imageLoader: ImageLoader?,
    onChange: ((AlbumAddSheetUiState) -> AlbumAddSheetUiState) -> Unit,
    onDismiss: () -> Unit,
    onAdd: () -> Unit,
    onAddAndSearch: () -> Unit,
    onAddArtistFirst: () -> Unit,
) {
    val album = sheet.candidate
    val title = album.title.orEmpty().ifEmpty { album.foreignAlbumId.orEmpty() }
    val artistName = album.artist?.artistName.orEmpty()
        .ifEmpty { sheet.libraryArtist?.artistName.orEmpty() }
    val coverUrl = remember(album) { AddNewLogic.albumCoverUrl(album, baseUrl) }
    val artist = sheet.libraryArtist
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
        ) {
            Column(Modifier.widthIn(max = 640.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AsyncImage(
                        model = coverUrl,
                        contentDescription = stringResource(
                            R.string.arr_lidarr_addnew_cd_album_cover,
                            title,
                        ),
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(96.dp)
                            .clip(RoundedCornerShape(8.dp)),
                    )
                    Spacer(Modifier.width(16.dp))
                    Column {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (artistName.isNotBlank()) {
                            Text(
                                text = stringResource(
                                    R.string.arr_lidarr_addnew_result_by_artist,
                                    artistName,
                                    album.albumType.orEmpty(),
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        album.disambiguation?.takeIf { it.isNotBlank() }?.let {
                            Text(
                                text = it,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
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
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = album.overview.orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )

                Spacer(Modifier.height(16.dp))
                if (artist == null) {
                    Text(
                        text = stringResource(
                            R.string.arr_lidarr_addnew_album_artist_missing,
                            artistName.ifEmpty { stringResource(R.string.arr_lidarr_addnew_album_artist_label) },
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = onAddArtistFirst, modifier = Modifier.fillMaxWidth()) {
                        Text(
                            stringResource(
                                R.string.arr_lidarr_addnew_album_add_artist,
                            ),
                        )
                    }
                } else {
                    val monitorLabel = stringResource(
                        R.string.arr_lidarr_addnew_album_monitored,
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp)
                            .semantics(mergeDescendants = true) {
                                contentDescription = monitorLabel
                            },
                    ) {
                        Text(
                            text = monitorLabel,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.weight(1f),
                        )
                        Switch(
                            checked = sheet.monitored,
                            onCheckedChange = { value ->
                                onChange { it.copy(monitored = value) }
                            },
                        )
                    }
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        FilledTonalButton(
                            onClick = onAdd,
                            enabled = sheet.submittable,
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(stringResource(R.string.arr_lidarr_addnew_action_add))
                        }
                        Button(
                            onClick = onAddAndSearch,
                            enabled = sheet.submittable,
                            modifier = Modifier.weight(1f),
                        ) {
                            if (sheet.submitting) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                )
                            } else {
                                Text(
                                    stringResource(
                                        R.string.arr_lidarr_addnew_action_add_search,
                                    ),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
