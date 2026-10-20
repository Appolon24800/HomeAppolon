package dev.appolon.homelab.arr.feature.lidarr.addnew.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.ImageLoader
import coil3.compose.AsyncImage
import dev.appolon.homelab.arr.api.lidarr.model.MetadataProfileResource
import dev.appolon.homelab.arr.api.lidarr.model.MonitorTypes
import dev.appolon.homelab.arr.api.lidarr.model.QualityProfileResource
import dev.appolon.homelab.arr.api.lidarr.model.RootFolderResource
import dev.appolon.homelab.arr.api.lidarr.model.TagResource
import dev.appolon.homelab.arr.core.Humanize
import dev.appolon.homelab.arr.feature.lidarr.R
import dev.appolon.homelab.arr.feature.lidarr.addnew.data.AddNewLogic
import dev.appolon.homelab.arr.feature.lidarr.addnew.vm.ArtistAddSheetUiState

/**
 * The artist add sheet opened from a search result: root folder with free
 * space, quality and metadata profiles, monitoring strategy, tags, and the
 * add / add-and-search actions. Content is capped at 640dp and centered so
 * tablets do not stretch the form.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArtistAddSheet(
    sheet: ArtistAddSheetUiState,
    baseUrl: String,
    imageLoader: ImageLoader?,
    rootFolders: List<RootFolderResource>,
    qualityProfiles: List<QualityProfileResource>,
    metadataProfiles: List<MetadataProfileResource>,
    tags: List<TagResource>,
    onChange: ((ArtistAddSheetUiState) -> ArtistAddSheetUiState) -> Unit,
    onDismiss: () -> Unit,
    onAdd: () -> Unit,
    onAddAndSearch: () -> Unit,
) {
    val title = sheet.candidate.artistName.orEmpty()
        .ifEmpty { sheet.candidate.foreignArtistId.orEmpty() }
    val coverUrl = remember(sheet.candidate) {
        AddNewLogic.artistCoverUrl(sheet.candidate, baseUrl)
    }
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
                            R.string.arr_lidarr_addnew_cd_artist_cover,
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
                        sheet.candidate.disambiguation?.takeIf { it.isNotBlank() }?.let {
                            Text(
                                text = it,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        sheet.candidate.genres.orEmpty().takeIf { it.isNotEmpty() }?.let {
                            Text(
                                text = it.joinToString(separator = ", "),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = sheet.candidate.overview.orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )

                Spacer(Modifier.height(16.dp))
                ChoiceDropdown(
                    label = stringResource(R.string.arr_lidarr_addnew_sheet_root_folder),
                    selectedLabel = sheet.rootFolderPath
                        ?: stringResource(R.string.arr_lidarr_addnew_sheet_pick_folder),
                    choices = rootFolders.map { folder ->
                        Choice(
                            value = folder.path.orEmpty(),
                            label = folder.path.orEmpty(),
                            secondary = folder.freeSpace?.let {
                                stringResource(
                                    R.string.arr_lidarr_addnew_sheet_free_space,
                                    Humanize.bytes(it),
                                )
                            },
                        )
                    },
                    selected = sheet.rootFolderPath,
                    onSelectedChange = { path -> onChange { it.copy(rootFolderPath = path) } },
                )

                val qualityChoices = qualityProfiles.map { profile ->
                    Choice(value = profile.id.toString(), label = profile.name.orEmpty())
                }
                ChoiceDropdown(
                    label = stringResource(R.string.arr_lidarr_addnew_sheet_quality_profile),
                    selectedLabel = sheet.qualityProfileId
                        ?.let { id -> qualityProfiles.firstOrNull { it.id == id }?.name }
                        ?: stringResource(R.string.arr_lidarr_addnew_sheet_pick_profile),
                    choices = qualityChoices,
                    selected = sheet.qualityProfileId?.toString(),
                    onSelectedChange = { value ->
                        onChange { it.copy(qualityProfileId = value?.toIntOrNull()) }
                    },
                )

                val metadataChoices = metadataProfiles.map { profile ->
                    Choice(value = profile.id.toString(), label = profile.name.orEmpty())
                }
                ChoiceDropdown(
                    label = stringResource(R.string.arr_lidarr_addnew_sheet_metadata_profile),
                    selectedLabel = sheet.metadataProfileId
                        ?.let { id -> metadataProfiles.firstOrNull { it.id == id }?.name }
                        ?: stringResource(R.string.arr_lidarr_addnew_sheet_pick_metadata_profile),
                    choices = metadataChoices,
                    selected = sheet.metadataProfileId?.toString(),
                    onSelectedChange = { value ->
                        onChange { it.copy(metadataProfileId = value?.toIntOrNull()) }
                    },
                )

                ChoiceDropdown(
                    label = stringResource(R.string.arr_lidarr_addnew_sheet_monitor),
                    selectedLabel = monitorLabel(sheet.monitor),
                    choices = AddNewLogic.MONITOR_STRATEGIES.map { strategy ->
                        Choice(strategy, monitorLabel(strategy))
                    },
                    selected = sheet.monitor,
                    onSelectedChange = { monitor -> onChange { it.copy(monitor = monitor) } },
                )

                Text(
                    text = stringResource(R.string.arr_lidarr_addnew_sheet_tags),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
                )
                if (tags.isEmpty()) {
                    Text(
                        text = stringResource(R.string.arr_lidarr_addnew_sheet_no_tags),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    TagChipFlow(
                        tags = tags,
                        selected = sheet.selectedTags,
                        onToggle = { tagId ->
                            onChange {
                                it.copy(
                                    selectedTags = if (tagId in it.selectedTags) {
                                        it.selectedTags - tagId
                                    } else {
                                        it.selectedTags + tagId
                                    },
                                )
                            }
                        },
                    )
                }

                Spacer(Modifier.height(20.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    FilledTonalButton(
                        onClick = onAdd,
                        enabled = sheet.submittable && !sheet.submitting,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(stringResource(R.string.arr_lidarr_addnew_action_add))
                    }
                    Button(
                        onClick = onAddAndSearch,
                        enabled = sheet.submittable && !sheet.submitting,
                        modifier = Modifier.weight(1f),
                    ) {
                        if (sheet.submitting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                            )
                        } else {
                            Text(stringResource(R.string.arr_lidarr_addnew_action_add_search))
                        }
                    }
                }
            }
        }
    }
}

/** Monitoring strategy labels; every option ships its own translation. */
@Composable
internal fun monitorLabel(monitor: MonitorTypes): String = when (monitor) {
    MonitorTypes.ALL -> stringResource(R.string.arr_lidarr_addnew_monitor_all)
    MonitorTypes.FUTURE -> stringResource(R.string.arr_lidarr_addnew_monitor_future)
    MonitorTypes.MISSING -> stringResource(R.string.arr_lidarr_addnew_monitor_missing)
    MonitorTypes.LATEST -> stringResource(R.string.arr_lidarr_addnew_monitor_latest)
    MonitorTypes.NONE -> stringResource(R.string.arr_lidarr_addnew_monitor_none)
    else -> stringResource(R.string.arr_lidarr_addnew_monitor_all)
}

/** Wrapping chip row for tag selection; chips meet the 48dp minimum target. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TagChipFlow(
    tags: List<TagResource>,
    selected: Set<Int>,
    onToggle: (Int) -> Unit,
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        tags.forEach { tag ->
            FilterChip(
                selected = tag.id in selected,
                onClick = { onToggle(tag.id) },
                label = { Text(tag.label.orEmpty()) },
            )
        }
    }
}
