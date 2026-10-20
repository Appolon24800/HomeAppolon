package dev.appolon.homelab.arr.feature.radarr.addnew.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Badge
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.ImageLoader
import dev.appolon.homelab.arr.api.radarr.model.CollectionResource
import dev.appolon.homelab.arr.api.radarr.model.ImportListMoviesResource
import dev.appolon.homelab.arr.api.radarr.model.MovieResource
import dev.appolon.homelab.arr.api.radarr.model.QualityProfileResource
import dev.appolon.homelab.arr.feature.radarr.R
import dev.appolon.homelab.arr.feature.radarr.addnew.data.AddNewLogic
import dev.appolon.homelab.arr.feature.radarr.addnew.vm.CollectionEditUiState
import dev.appolon.homelab.arr.feature.radarr.addnew.vm.CollectionsUiState
import dev.appolon.homelab.arr.ui.components.PosterCard
import dev.appolon.homelab.arr.ui.lce.ArrLceBox
import dev.appolon.homelab.arr.ui.R as ArrUiR

/**
 * The Collections tab: one row per collection with a monitoring toggle,
 * missing count, quality label, edit sheet (monitoring, quality, search on
 * add), and a search-missing action. Rows cap at 720dp and center on
 * tablets; every icon button carries a content description and a 48dp
 * target.
 */
@Composable
internal fun CollectionsArea(
    state: CollectionsUiState,
    imageLoader: ImageLoader?,
    onToggleMonitored: (CollectionResource) -> Unit,
    onOpenEditor: (CollectionResource) -> Unit,
    onEditorChange: ((CollectionEditUiState) -> CollectionEditUiState) -> Unit,
    onEditorDismiss: () -> Unit,
    onEditorSubmit: () -> Unit,
    onSearchCollection: (CollectionResource) -> Unit,
    onRetry: () -> Unit,
) {
    ArrLceBox(
        state = state.lce,
        onRetry = onRetry,
        emptyTitle = stringResource(R.string.arr_radarr_collections_empty_title),
        emptyMessage = stringResource(R.string.arr_radarr_collections_empty_message),
        modifier = Modifier.fillMaxSize(),
    ) { collections ->
        LazyColumn(
            contentPadding = PaddingValues(vertical = 8.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            items(collections, key = { it.id }, contentType = { "collection" }) { collection ->
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                    CollectionRow(
                        collection = collection,
                        state = state,
                        imageLoader = imageLoader,
                        onToggleMonitored = onToggleMonitored,
                        onOpenEditor = onOpenEditor,
                        onSearchCollection = onSearchCollection,
                        modifier = Modifier.widthIn(max = 720.dp),
                    )
                }
            }
        }
    }

    state.editSheet?.let { sheet ->
        CollectionEditSheet(
            sheet = sheet,
            qualityProfiles = state.qualityProfiles,
            onChange = onEditorChange,
            onDismiss = onEditorDismiss,
            onSubmit = onEditorSubmit,
        )
    }
}

@Composable
private fun CollectionRow(
    collection: CollectionResource,
    state: CollectionsUiState,
    imageLoader: ImageLoader?,
    onToggleMonitored: (CollectionResource) -> Unit,
    onOpenEditor: (CollectionResource) -> Unit,
    onSearchCollection: (CollectionResource) -> Unit,
    modifier: Modifier = Modifier,
) {
    val title = collection.title.orEmpty().ifEmpty { collection.tmdbId.toString() }
    val posterUrl = remember(collection.id) { AddNewLogic.collectionPosterUrl(collection) }
    val total = collection.movies?.size ?: 0
    val missing = collection.missingMovies
    val queued = collection.id in state.queuedCollectionIds
    val searching = collection.id in state.searchingIds

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        PosterImage(
            url = posterUrl,
            contentDescription = stringResource(ArrUiR.string.arr_common_addnew_cd_poster, title),
            imageLoader = imageLoader,
            width = 64.dp,
            height = 96.dp,
        )
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val countsText = if (total > 0) {
                stringResource(
                    R.string.arr_radarr_collections_in_library,
                    total - missing,
                    total,
                )
            } else {
                ""
            }
            if (countsText.isNotEmpty()) {
                Text(
                    text = countsText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (missing > 0) {
                    Text(
                        text = stringResource(R.string.arr_radarr_collections_missing, missing),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                Text(
                    text = stringResource(
                        ArrUiR.string.arr_common_import_flow_quality_line,
                        state.profileLabel(collection.qualityProfileId),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (queued) {
                AssistChip(
                    onClick = {},
                    enabled = false,
                    label = { Text(stringResource(ArrUiR.string.arr_common_connect_badge_queued)) },
                    leadingIcon = {
                        Icon(
                            Icons.Filled.CloudUpload,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                    },
                )
            }
        }
        Spacer(Modifier.width(4.dp))
        if (searching) {
            CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
        } else {
            val monitorDescription = stringResource(
                R.string.arr_radarr_collections_cd_toggle_monitored,
                title,
            )
            IconButton(onClick = { onToggleMonitored(collection) }) {
                Icon(
                    imageVector = if (collection.monitored) {
                        Icons.Filled.Visibility
                    } else {
                        Icons.Filled.VisibilityOff
                    },
                    contentDescription = monitorDescription,
                )
            }
        }
        val searchDescription = stringResource(
            R.string.arr_radarr_collections_cd_search_missing,
            title,
        )
        IconButton(
            onClick = { onSearchCollection(collection) },
            enabled = missing > 0,
        ) {
            Icon(Icons.Filled.Search, contentDescription = searchDescription)
        }
        val editDescription = stringResource(R.string.arr_radarr_collections_cd_edit, title)
        IconButton(onClick = { onOpenEditor(collection) }) {
            Icon(Icons.Filled.Edit, contentDescription = editDescription)
        }
    }
}

/** Edit sheet for one collection: monitoring, quality profile, search on add. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CollectionEditSheet(
    sheet: CollectionEditUiState,
    qualityProfiles: List<QualityProfileResource>,
    onChange: ((CollectionEditUiState) -> CollectionEditUiState) -> Unit,
    onDismiss: () -> Unit,
    onSubmit: () -> Unit,
) {
    val title = sheet.collection.title.orEmpty().ifEmpty { sheet.collection.tmdbId.toString() }
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
                Text(
                    text = stringResource(R.string.arr_radarr_collections_edit_title),
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(bottom = 12.dp),
                )

                EditSwitchRow(
                    label = stringResource(ArrUiR.string.arr_common_monitored),
                    checked = sheet.monitored,
                    onCheckedChange = { value -> onChange { it.copy(monitored = value) } },
                )
                EditSwitchRow(
                    label = stringResource(ArrUiR.string.arr_common_lists_search_on_add),
                    checked = sheet.searchOnAdd,
                    onCheckedChange = { value -> onChange { it.copy(searchOnAdd = value) } },
                )

                QualityDropdown(
                    qualityProfiles = qualityProfiles,
                    selectedId = sheet.qualityProfileId,
                    onSelect = { id -> onChange { it.copy(qualityProfileId = id) } },
                )

                Spacer(Modifier.height(20.dp))
                Button(
                    onClick = onSubmit,
                    enabled = !sheet.submitting,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(ArrUiR.string.arr_common_save))
                }
            }
        }
    }
}

@Composable
private fun EditSwitchRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QualityDropdown(
    qualityProfiles: List<QualityProfileResource>,
    selectedId: Int,
    onSelect: (Int) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedLabel = qualityProfiles.firstOrNull { it.id == selectedId }?.name
        ?: "#$selectedId"
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp),
    ) {
        OutlinedTextField(
            value = selectedLabel.orEmpty(),
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(ArrUiR.string.arr_common_wanted_filter_quality)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth(),
        )
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            qualityProfiles.forEach { profile ->
                DropdownMenuItem(
                    text = { Text(profile.name.orEmpty()) },
                    onClick = {
                        expanded = false
                        onSelect(profile.id)
                    },
                )
            }
        }
    }
}

/**
 * One discovery poster tile: tap opens the add sheet; an existing library
 * movie shows a check badge, a queued add shows the sync badge.
 */
@Composable
internal fun DiscoveryCard(
    item: ImportListMoviesResource,
    existing: Boolean,
    queued: Boolean,
    imageLoader: ImageLoader?,
    onOpenAddSheet: (MovieResource) -> Unit,
) {
    val title = item.title.orEmpty().ifEmpty { item.tmdbId.toString() }
    val posterUrl = remember(item.tmdbId) { AddNewLogic.discoveryPosterUrl(item) }
    PosterCard(
        title = title,
        posterUrl = posterUrl,
        subtitle = if (item.year > 0) item.year.toString() else null,
        imageLoader = imageLoader,
        onClick = { onOpenAddSheet(AddNewLogic.toCandidate(item)) },
        badges = {
            when {
                existing -> Badge {
                    Icon(
                        Icons.Filled.Check,
                        contentDescription = stringResource(
                            R.string.arr_radarr_addnew_cd_in_library,
                        ),
                        modifier = Modifier.size(14.dp),
                    )
                }

                queued -> Badge {
                    Icon(
                        Icons.Filled.CloudUpload,
                        contentDescription = stringResource(
                            R.string.arr_radarr_addnew_cd_queued_indicator,
                        ),
                        modifier = Modifier.size(14.dp),
                    )
                }
            }
        },
    )
}
