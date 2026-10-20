package dev.appolon.homelab.arr.feature.radarr.addnew.ui

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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import dev.appolon.homelab.arr.api.radarr.model.MonitorTypes
import dev.appolon.homelab.arr.api.radarr.model.MovieStatusType
import dev.appolon.homelab.arr.api.radarr.model.QualityProfileResource
import dev.appolon.homelab.arr.api.radarr.model.RootFolderResource
import dev.appolon.homelab.arr.api.radarr.model.TagResource
import dev.appolon.homelab.arr.core.Humanize
import dev.appolon.homelab.arr.feature.radarr.R
import dev.appolon.homelab.arr.feature.radarr.addnew.data.AddNewLogic
import dev.appolon.homelab.arr.feature.radarr.addnew.vm.AddSheetUiState
import dev.appolon.homelab.arr.ui.R as ArrUiR

/**
 * The add sheet opened from a search result or discovery card: root folder
 * picker with free space, quality profile, monitoring strategy, minimum
 * availability, tags, and the add / add-and-search actions. Content is
 * capped at 640dp and centered so tablets do not stretch the form.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddMovieSheet(
    sheet: AddSheetUiState,
    rootFolders: List<RootFolderResource>,
    qualityProfiles: List<QualityProfileResource>,
    tags: List<TagResource>,
    onChange: ((AddSheetUiState) -> AddSheetUiState) -> Unit,
    onDismiss: () -> Unit,
    onAdd: () -> Unit,
    onAddAndSearch: () -> Unit,
) {
    val title = sheet.candidate.title.orEmpty().ifEmpty { sheet.candidate.tmdbId.toString() }
    val posterUrl = remember(sheet.candidate.tmdbId) {
        AddNewLogic.lookupPosterUrl(sheet.candidate)
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
                        model = posterUrl,
                        contentDescription = stringResource(
                            ArrUiR.string.arr_common_addnew_cd_poster,
                            title,
                        ),
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(width = 84.dp, height = 126.dp)
                            .clip(RoundedCornerShape(8.dp)),
                    )
                    Spacer(Modifier.width(16.dp))
                    Column {
                        Text(
                            text = stringResource(
                                R.string.arr_radarr_addnew_title_year,
                                title,
                                sheet.candidate.year,
                            ),
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        sheet.candidate.studio?.let {
                            Text(
                                text = it,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                    label = stringResource(ArrUiR.string.arr_common_addnew_sheet_root_folder),
                    selectedLabel = sheet.rootFolderPath
                        ?: stringResource(R.string.arr_radarr_addnew_sheet_pick_folder),
                    choices = rootFolders.map { folder ->
                        Choice(
                            value = folder.path.orEmpty(),
                            label = folder.path.orEmpty(),
                            secondary = folder.freeSpace?.let {
                                stringResource(
                                    ArrUiR.string.arr_common_roots_free_only,
                                    Humanize.bytes(it),
                                )
                            },
                        )
                    },
                    selected = sheet.rootFolderPath,
                    onSelectedChange = { path -> onChange { it.copy(rootFolderPath = path) } },
                )

                val profileChoices = qualityProfiles.map { profile ->
                    Choice(
                        value = profile.id.toString(),
                        label = profile.name.orEmpty(),
                        secondary = null,
                    )
                }
                ChoiceDropdown(
                    label = stringResource(ArrUiR.string.arr_common_wanted_filter_quality),
                    selectedLabel = sheet.qualityProfileId
                        ?.let { id -> qualityProfiles.firstOrNull { it.id == id }?.name }
                        ?: stringResource(R.string.arr_radarr_addnew_sheet_pick_profile),
                    choices = profileChoices,
                    selected = sheet.qualityProfileId?.toString(),
                    onSelectedChange = { value ->
                        onChange { it.copy(qualityProfileId = value?.toIntOrNull()) }
                    },
                )

                ChoiceDropdown(
                    label = stringResource(R.string.arr_radarr_addnew_sheet_monitor),
                    selectedLabel = monitorLabel(sheet.monitor),
                    choices = listOf(
                        Choice(MonitorTypes.MOVIE_ONLY, monitorLabel(MonitorTypes.MOVIE_ONLY)),
                        Choice(
                            MonitorTypes.MOVIE_AND_COLLECTION,
                            monitorLabel(MonitorTypes.MOVIE_AND_COLLECTION),
                        ),
                        Choice(MonitorTypes.NONE, monitorLabel(MonitorTypes.NONE)),
                    ),
                    selected = sheet.monitor,
                    onSelectedChange = { monitor -> onChange { it.copy(monitor = monitor) } },
                )

                ChoiceDropdown(
                    label = stringResource(R.string.arr_radarr_addnew_sheet_minimum_availability),
                    selectedLabel = availabilityLabel(sheet.minimumAvailability),
                    choices = listOf(
                        Choice(MovieStatusType.TBA, availabilityLabel(MovieStatusType.TBA)),
                        Choice(
                            MovieStatusType.ANNOUNCED,
                            availabilityLabel(MovieStatusType.ANNOUNCED),
                        ),
                        Choice(
                            MovieStatusType.IN_CINEMAS,
                            availabilityLabel(MovieStatusType.IN_CINEMAS),
                        ),
                        Choice(
                            MovieStatusType.RELEASED,
                            availabilityLabel(MovieStatusType.RELEASED),
                        ),
                    ),
                    selected = sheet.minimumAvailability,
                    onSelectedChange = { value ->
                        onChange { it.copy(minimumAvailability = value) }
                    },
                )

                Text(
                    text = stringResource(ArrUiR.string.arr_common_tags_title),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
                )
                if (tags.isEmpty()) {
                    Text(
                        text = stringResource(R.string.arr_radarr_addnew_sheet_no_tags),
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
                        Text(stringResource(ArrUiR.string.arr_common_roots_add_confirm))
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
                            Text(stringResource(ArrUiR.string.arr_common_addnew_action_add_search))
                        }
                    }
                }
            }
        }
    }
}

/** Monitoring strategy labels; every option ships its own translation. */
@Composable
private fun monitorLabel(monitor: MonitorTypes): String = when (monitor) {
    MonitorTypes.MOVIE_ONLY -> stringResource(R.string.arr_radarr_addnew_monitor_movie_only)
    MonitorTypes.MOVIE_AND_COLLECTION ->
        stringResource(R.string.arr_radarr_addnew_monitor_movie_and_collection)

    MonitorTypes.NONE -> stringResource(ArrUiR.string.arr_common_general_auth_method_none)
}

/** Minimum availability labels; every option ships its own translation. */
@Composable
private fun availabilityLabel(availability: MovieStatusType): String = when (availability) {
    MovieStatusType.TBA -> stringResource(R.string.arr_radarr_addnew_availability_tba)
    MovieStatusType.ANNOUNCED -> stringResource(R.string.arr_radarr_addnew_availability_announced)
    MovieStatusType.IN_CINEMAS -> stringResource(R.string.arr_radarr_addnew_availability_in_cinemas)
    MovieStatusType.RELEASED -> stringResource(R.string.arr_radarr_addnew_availability_released)
    MovieStatusType.DELETED -> stringResource(R.string.arr_radarr_addnew_availability_released)
}

/** One selectable row of a [ChoiceDropdown], with optional secondary text. */
private data class Choice<T>(val value: T, val label: String, val secondary: String? = null)

/**
 * Read-only labeled dropdown with pre-resolved choice labels, mirroring the
 * library editor's dropdown so both sheets read identically.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> ChoiceDropdown(
    label: String,
    selectedLabel: String,
    choices: List<Choice<T>>,
    selected: T?,
    onSelectedChange: (T) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp),
    ) {
        OutlinedTextField(
            value = selectedLabel,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth(),
        )
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            choices.forEach { choice ->
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(choice.label)
                            choice.secondary?.let {
                                Text(
                                    text = it,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    },
                    onClick = {
                        expanded = false
                        onSelectedChange(choice.value)
                    },
                )
            }
        }
    }
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
