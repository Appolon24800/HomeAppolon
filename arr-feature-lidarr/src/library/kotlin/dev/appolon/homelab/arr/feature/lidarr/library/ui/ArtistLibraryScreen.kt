package dev.appolon.homelab.arr.feature.lidarr.library.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.ImageLoader
import coil3.compose.AsyncImage
import dev.appolon.homelab.arr.api.lidarr.model.ArtistResource
import dev.appolon.homelab.arr.core.FilterValue
import dev.appolon.homelab.arr.core.Humanize
import dev.appolon.homelab.arr.feature.lidarr.R
import dev.appolon.homelab.arr.feature.lidarr.library.data.ArtistLibraryFilters
import dev.appolon.homelab.arr.feature.lidarr.library.data.ArtistLibrarySorts
import dev.appolon.homelab.arr.feature.lidarr.library.data.ArtistPosterUrls
import dev.appolon.homelab.arr.feature.lidarr.library.data.SavedArtistFilter
import dev.appolon.homelab.arr.feature.lidarr.library.vm.ArtistEditorForm
import dev.appolon.homelab.arr.feature.lidarr.library.vm.ArtistLibraryUiState
import dev.appolon.homelab.arr.feature.lidarr.library.vm.LibraryMessage
import dev.appolon.homelab.arr.feature.lidarr.library.vm.LibraryView
import dev.appolon.homelab.arr.ui.components.FilterChipRow
import dev.appolon.homelab.arr.ui.components.PosterCard
import dev.appolon.homelab.arr.ui.components.SortMenu
import dev.appolon.homelab.arr.ui.lce.ArrLceBox
import dev.appolon.homelab.arr.ui.lce.ArrPullToRefreshBox
import dev.appolon.homelab.arr.ui.lce.LceState
import dev.appolon.homelab.arr.ui.shell.ArrScaffold

/**
 * The artists library: poster grid or table over the presented list, filter
 * chips, saved filters, bulk selection with editor and delete, refresh and
 * scan commands, pull to refresh, and paged rendering. The screen owns no
 * state; the ViewModel drives it and every failure lands in the LCE box.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArtistLibraryScreen(
    state: ArtistLibraryUiState,
    lce: LceState<ArtistLibraryUiState>,
    baseUrl: String,
    imageLoader: ImageLoader?,
    onRefresh: () -> Unit,
    onLoadMore: () -> Unit,
    onViewChange: (LibraryView) -> Unit,
    onSortSelect: (String) -> Unit,
    onToggleSortDescending: () -> Unit,
    onFilterValueChange: (String, FilterValue) -> Unit,
    onClearFilters: () -> Unit,
    onSaveFilter: (String) -> Unit,
    onApplySavedFilter: (String) -> Unit,
    onDeleteSavedFilter: (String) -> Unit,
    onArtistActivate: (Int) -> Unit,
    onArtistLongClick: (Int) -> Unit,
    onSelectAll: () -> Unit,
    onExitSelection: () -> Unit,
    onOpenEditor: () -> Unit,
    onEditorChange: ((ArtistEditorForm) -> ArtistEditorForm) -> Unit,
    onEditorSubmit: () -> Unit,
    onEditorDismiss: () -> Unit,
    onRequestDelete: () -> Unit,
    onDeleteConfirm: (Boolean, Boolean) -> Unit,
    onDeleteDismiss: () -> Unit,
    onRefreshMetadata: () -> Unit,
    onScanDisk: () -> Unit,
    onConsumeMessage: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val snackbar = remember { SnackbarHostState() }
    val message = state.message
    // Resolved at composition so the effect below only deals with plain strings.
    val messageText = message?.let { libraryMessageText(it) }
    LaunchedEffect(messageText) {
        messageText?.let {
            snackbar.showSnackbar(it)
            onConsumeMessage()
        }
    }
    var filterSheetOpen by remember { mutableStateOf(false) }
    var saveDialogOpen by remember { mutableStateOf(false) }

    ArrScaffold(
        title = if (state.selectMode && state.selection.isNotEmpty()) {
            stringResource(R.string.arr_lidarr_library_selected_count, state.selection.size)
        } else {
            stringResource(R.string.arr_lidarr_library_title)
        },
        modifier = modifier,
        actions = {
            if (!state.selectMode) {
                LibraryActions(
                    state = state,
                    onViewChange = onViewChange,
                    onSortSelect = onSortSelect,
                    onToggleSortDescending = onToggleSortDescending,
                    onSaveFilterClick = { saveDialogOpen = true },
                    onApplySavedFilter = onApplySavedFilter,
                    onDeleteSavedFilter = onDeleteSavedFilter,
                )
            }
        },
        filterBar = {
            if (!state.selectMode) {
                FilterBar(
                    state = state,
                    onFilterClick = { filterSheetOpen = true },
                    onClear = onClearFilters,
                    onApplySavedFilter = onApplySavedFilter,
                )
            }
        },
    ) { padding ->
        ArrPullToRefreshBox(
            isRefreshing = state.refreshing,
            onRefresh = onRefresh,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            Column(Modifier.fillMaxSize()) {
                Box(Modifier.weight(1f)) {
                    ArrLceBox(
                        state = lce,
                        onRetry = onRefresh,
                        emptyTitle = stringResource(R.string.arr_lidarr_library_empty_title),
                        emptyMessage = stringResource(R.string.arr_lidarr_library_empty_message),
                        modifier = Modifier.fillMaxSize(),
                    ) { content ->
                        if (content.view == LibraryView.POSTERS) {
                            PosterGrid(
                                state = content,
                                baseUrl = baseUrl,
                                imageLoader = imageLoader,
                                onArtistActivate = onArtistActivate,
                                onArtistLongClick = onArtistLongClick,
                                onLoadMore = onLoadMore,
                            )
                        } else {
                            ArtistTable(
                                state = content,
                                baseUrl = baseUrl,
                                imageLoader = imageLoader,
                                onArtistActivate = onArtistActivate,
                                onArtistLongClick = onArtistLongClick,
                                onLoadMore = onLoadMore,
                            )
                        }
                    }
                }
                if (state.selectMode) {
                    SelectionBar(
                        onRefreshMetadata = onRefreshMetadata,
                        onScanDisk = onScanDisk,
                        onEdit = onOpenEditor,
                        onDelete = onRequestDelete,
                        onSelectAll = onSelectAll,
                        onClear = onExitSelection,
                    )
                }
            }
            SnackbarHost(
                hostState = snackbar,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }

    if (filterSheetOpen) {
        ArtistFilterSheet(
            filterState = state.filterState,
            specs = ArtistLibraryFilters.specs(state.options),
            qualityProfileLabel = state::qualityProfileLabel,
            metadataProfileLabel = state::metadataProfileLabel,
            onValueChange = onFilterValueChange,
            onClear = onClearFilters,
            onDismiss = { filterSheetOpen = false },
        )
    }
    if (saveDialogOpen) {
        SaveFilterDialog(onSave = onSaveFilter, onDismiss = { saveDialogOpen = false })
    }
    state.editorForm?.let { form ->
        ArtistEditorSheet(
            form = form,
            targetCount = state.bulkTargets.size,
            qualityProfiles = state.qualityProfiles,
            metadataProfiles = state.metadataProfiles,
            onChange = onEditorChange,
            onSubmit = onEditorSubmit,
            onDismiss = onEditorDismiss,
        )
    }
    if (state.confirmDelete) {
        ArtistDeleteDialog(
            targetCount = state.bulkTargets.size,
            onConfirm = onDeleteConfirm,
            onDismiss = onDeleteDismiss,
        )
    }
}

/** Toolbar outside selection mode: queue badge, view toggle, sort, saved filters. */
@Composable
private fun LibraryActions(
    state: ArtistLibraryUiState,
    onViewChange: (LibraryView) -> Unit,
    onSortSelect: (String) -> Unit,
    onToggleSortDescending: () -> Unit,
    onSaveFilterClick: () -> Unit,
    onApplySavedFilter: (String) -> Unit,
    onDeleteSavedFilter: (String) -> Unit,
) {
    if (state.queuedForServer > 0) {
        val queuedDescription =
            stringResource(R.string.arr_lidarr_library_cd_queued, state.queuedForServer)
        BadgedBox(
            badge = { Badge { Text(state.queuedForServer.coerceAtMost(99).toString()) } },
            modifier = Modifier.semantics { contentDescription = queuedDescription },
        ) {
            Icon(
                imageVector = Icons.Filled.CloudUpload,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.tertiary,
            )
        }
    }
    IconButton(onClick = {
        onViewChange(
            if (state.view == LibraryView.POSTERS) LibraryView.TABLE else LibraryView.POSTERS,
        )
    }) {
        Icon(
            imageVector = if (state.view == LibraryView.POSTERS) {
                Icons.AutoMirrored.Filled.ViewList
            } else {
                Icons.Filled.GridView
            },
            contentDescription = if (state.view == LibraryView.POSTERS) {
                stringResource(R.string.arr_lidarr_library_cd_view_table)
            } else {
                stringResource(R.string.arr_lidarr_library_cd_view_posters)
            },
        )
    }
    // Sort labels resolve here, not inside SortMenu's plain value lambda.
    val sortLabels = mapOf(
        ArtistLibrarySorts.TITLE to stringResource(R.string.arr_lidarr_sort_title),
        ArtistLibrarySorts.ADDED to stringResource(R.string.arr_lidarr_sort_added),
        ArtistLibrarySorts.ALBUMS to stringResource(R.string.arr_lidarr_sort_albums),
        ArtistLibrarySorts.SIZE to stringResource(R.string.arr_lidarr_sort_size),
    )
    SortMenu(
        options = ArtistLibrarySorts.ALL,
        selected = state.sortId,
        label = { sortLabels.getValue(it) },
        onSelect = onSortSelect,
    )
    IconButton(onClick = onToggleSortDescending) {
        Icon(
            imageVector = if (state.sortDescending) {
                Icons.Filled.ArrowDownward
            } else {
                Icons.Filled.ArrowUpward
            },
            contentDescription = stringResource(R.string.arr_lidarr_library_cd_sort_descending),
        )
    }
    SavedFiltersMenu(
        saved = state.savedFilters,
        activeId = state.activeSavedFilterId,
        onSaveClick = onSaveFilterClick,
        onApply = onApplySavedFilter,
        onDelete = onDeleteSavedFilter,
    )
}

/** Saved filters anchor; the icon is a 48dp target per the accessibility contract. */
@Composable
private fun SavedFiltersMenu(
    saved: List<SavedArtistFilter>,
    activeId: String?,
    onSaveClick: () -> Unit,
    onApply: (String) -> Unit,
    onDelete: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    IconButton(onClick = { expanded = true }) {
        Icon(
            imageVector = Icons.Filled.SaveAlt,
            contentDescription = stringResource(R.string.arr_lidarr_saved_filters),
        )
    }
    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
        DropdownMenuItem(
            text = { Text(stringResource(R.string.arr_lidarr_saved_filter_save)) },
            onClick = {
                expanded = false
                onSaveClick()
            },
        )
        if (saved.isEmpty()) {
            DropdownMenuItem(
                text = {
                    Text(
                        stringResource(R.string.arr_lidarr_saved_filter_empty),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
                onClick = {},
            )
        }
        saved.forEach { filter ->
            DropdownMenuItem(
                text = { Text(filter.label) },
                trailingIcon = {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = stringResource(
                            R.string.arr_lidarr_saved_filter_delete_cd,
                            filter.label,
                        ),
                        modifier = Modifier.minimumInteractiveComponentSize(),
                    )
                },
                leadingIcon = if (filter.id == activeId) {
                    { Icon(Icons.Filled.Done, contentDescription = null) }
                } else {
                    null
                },
                onClick = {
                    expanded = false
                    onApply(filter.id)
                },
            )
        }
    }
}

/** Row of filter chips plus the active saved filter, under the app bar. */
@Composable
private fun FilterBar(
    state: ArtistLibraryUiState,
    onFilterClick: () -> Unit,
    onClear: () -> Unit,
    onApplySavedFilter: (String) -> Unit,
) {
    val chips = listOf(
        ArtistLibraryFilters.MONITORED,
        ArtistLibraryFilters.MISSING,
        ArtistLibraryFilters.QUALITY,
        ArtistLibraryFilters.METADATA,
        ArtistLibraryFilters.GENRE,
        ArtistLibraryFilters.ALBUMS,
        ArtistLibraryFilters.FOLDER,
        ArtistLibraryFilters.TAGS,
    )
    val activeSaved = state.activeSavedFilterId
        ?.let { id -> state.savedFilters.firstOrNull { it.id == id } }
    val labels = chips.associateWith { id -> filterLabel(id) }
    Column {
        FilterChipRow(
            filters = chips,
            selected = { state.filterState.isActive(it) },
            label = { labels.getValue(it) },
            onSelect = { onFilterClick() },
        )
        if (activeSaved != null) {
            Row(modifier = Modifier.padding(start = 16.dp, top = 4.dp)) {
                AssistChip(
                    onClick = { onApplySavedFilter(activeSaved.id) },
                    label = { Text(activeSaved.label) },
                )
            }
        }
        if (chips.any { state.filterState.isActive(it) }) {
            Row(modifier = Modifier.padding(start = 16.dp, top = 4.dp, bottom = 4.dp)) {
                AssistChip(
                    onClick = onClear,
                    label = { Text(stringResource(R.string.arr_lidarr_filter_clear)) },
                )
            }
        }
    }
}

/** Poster grid with adaptive columns, so tablets naturally fit more columns. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PosterGrid(
    state: ArtistLibraryUiState,
    baseUrl: String,
    imageLoader: ImageLoader?,
    onArtistActivate: (Int) -> Unit,
    onArtistLongClick: (Int) -> Unit,
    onLoadMore: () -> Unit,
) {
    val gridState = rememberLazyGridState()
    GridLoadMoreEffect(gridState, enabled = !state.presentation.endReached, onLoadMore = onLoadMore)
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 128.dp),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        state = gridState,
        modifier = Modifier.fillMaxSize(),
    ) {
        items(state.presentation.visible, key = { it.id }) { artist ->
            ArtistPosterCard(
                artist = artist,
                state = state,
                baseUrl = baseUrl,
                imageLoader = imageLoader,
                onActivate = { onArtistActivate(artist.id) },
                onLongClick = { onArtistLongClick(artist.id) },
            )
        }
    }
}

/**
 * One poster tile. The long-press wrapper sits outside [PosterCard]'s own
 * tap handling on purpose: taps resolve on the card, long presses time out
 * into the wrapper's [combinedClickable] and enter selection mode.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ArtistPosterCard(
    artist: ArtistResource,
    state: ArtistLibraryUiState,
    baseUrl: String,
    imageLoader: ImageLoader?,
    onActivate: () -> Unit,
    onLongClick: () -> Unit,
) {
    val title = artist.artistName.orEmpty().ifEmpty { "#" + artist.id }
    val description = if (state.selectMode) {
        stringResource(R.string.arr_lidarr_library_cd_select_artist, title)
    } else {
        stringResource(R.string.arr_lidarr_library_cd_open_artist, title)
    }
    val summary = state.summaries[artist.id]
    val queued = artist.id in state.queuedArtistIds
    Box(
        modifier = Modifier
            .semantics { contentDescription = description }
            .combinedClickable(onClick = {}, onLongClick = onLongClick),
    ) {
        PosterCard(
            title = title,
            posterUrl = ArtistPosterUrls.posterUrlOf(artist, baseUrl),
            imageLoader = imageLoader,
            onClick = onActivate,
            subtitle = albumCountSubtitle(
                albumCount = summary?.albumCount ?: artist.statistics?.albumCount ?: 0,
                missing = summary?.missing ?: 0,
            ),
            badges = {
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    if (ArtistLibraryFilters.isMissing(artist, state.summaries)) {
                        PosterBadge(
                            stringResource(R.string.arr_lidarr_library_badge_missing),
                            MaterialTheme.colorScheme.errorContainer,
                        )
                    }
                    if (queued) {
                        PosterBadge(
                            stringResource(R.string.arr_lidarr_library_badge_queued),
                            MaterialTheme.colorScheme.tertiaryContainer,
                        )
                    }
                }
            },
        )
        if (state.selectMode) {
            SelectionCheck(selected = artist.id in state.selection, modifier = Modifier.align(Alignment.TopStart))
        }
    }
}

/** Poster subtitle: album count with the missing split when albums loaded. */
@Composable
private fun albumCountSubtitle(albumCount: Int, missing: Int): String =
    if (missing > 0) {
        stringResource(R.string.arr_lidarr_library_albums_with_missing, albumCount, missing)
    } else {
        stringResource(R.string.arr_lidarr_library_albums, albumCount)
    }

/** Small label chip drawn over a poster's corner. */
@Composable
private fun PosterBadge(text: String, container: Color) {
    Surface(color = container, shape = RoundedCornerShape(6.dp)) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
        )
    }
}

@Composable
private fun SelectionCheck(selected: Boolean, modifier: Modifier = Modifier) {
    Surface(
        color = if (selected) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f)
        },
        shape = CircleShape,
        modifier = modifier
            .padding(6.dp)
            .size(28.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (selected) {
                Icon(
                    imageVector = Icons.Filled.Done,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

/** Compact table view: thumb, name, album counts, profiles, size, badges. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ArtistTable(
    state: ArtistLibraryUiState,
    baseUrl: String,
    imageLoader: ImageLoader?,
    onArtistActivate: (Int) -> Unit,
    onArtistLongClick: (Int) -> Unit,
    onLoadMore: () -> Unit,
) {
    val listState = rememberLazyListState()
    ListLoadMoreEffect(listState, enabled = !state.presentation.endReached, onLoadMore = onLoadMore)
    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        items(state.presentation.visible, key = { it.id }) { artist ->
            ArtistTableRow(
                artist = artist,
                state = state,
                baseUrl = baseUrl,
                imageLoader = imageLoader,
                onActivate = { onArtistActivate(artist.id) },
                onLongClick = { onArtistLongClick(artist.id) },
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ArtistTableRow(
    artist: ArtistResource,
    state: ArtistLibraryUiState,
    baseUrl: String,
    imageLoader: ImageLoader?,
    onActivate: () -> Unit,
    onLongClick: () -> Unit,
) {
    val title = artist.artistName.orEmpty().ifEmpty { "#" + artist.id }
    val description = if (state.selectMode) {
        stringResource(R.string.arr_lidarr_library_cd_select_artist, title)
    } else {
        stringResource(R.string.arr_lidarr_library_cd_open_artist, title)
    }
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (artist.id in state.selection) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            MaterialTheme.colorScheme.surface
        },
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onActivate,
                onLongClick = onLongClick,
            )
            .semantics { contentDescription = description },
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .minimumInteractiveComponentSize()
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            PosterThumb(
                posterUrl = ArtistPosterUrls.posterUrlOf(artist, baseUrl),
                contentDescription = null,
                imageLoader = imageLoader,
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val summary = state.summaries[artist.id]
                val albumCount = summary?.albumCount ?: artist.statistics?.albumCount ?: 0
                val details = listOfNotNull(
                    stringResource(R.string.arr_lidarr_library_albums, albumCount),
                    summary?.takeIf { it.released > 0 || it.upcoming > 0 }?.let {
                        stringResource(R.string.arr_lidarr_library_release_status, it.released, it.upcoming)
                    },
                    state.qualityProfileLabel(artist.qualityProfileId),
                    state.metadataProfileLabel(artist.metadataProfileId),
                    artist.statistics?.sizeOnDisk?.takeIf { it > 0 }?.let { Humanize.bytes(it) },
                ).joinToString(" \u00b7 ")
                Text(
                    text = details,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (ArtistLibraryFilters.isMissing(artist, state.summaries)) {
                PosterBadge(
                    stringResource(R.string.arr_lidarr_library_badge_missing),
                    MaterialTheme.colorScheme.errorContainer,
                )
            }
            if (artist.id in state.queuedArtistIds) {
                PosterBadge(
                    stringResource(R.string.arr_lidarr_library_badge_queued),
                    MaterialTheme.colorScheme.tertiaryContainer,
                )
            }
            Icon(
                imageVector = if (artist.monitored) {
                    Icons.Filled.LibraryMusic
                } else {
                    Icons.Outlined.LibraryMusic
                },
                contentDescription = if (artist.monitored) {
                    stringResource(R.string.arr_lidarr_library_cd_monitored)
                } else {
                    stringResource(R.string.arr_lidarr_library_cd_unmonitored)
                },
                tint = if (artist.monitored) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.outlineVariant
                },
                modifier = Modifier.padding(start = 8.dp),
            )
        }
    }
}

/** Fixed size 2:3 thumbnail for table rows. */
@Composable
private fun PosterThumb(
    posterUrl: String?,
    contentDescription: String?,
    imageLoader: ImageLoader?,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(6.dp)
    Box(
        modifier = modifier
            .width(44.dp)
            .aspectRatio(2f / 3f)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .semantics { this.contentDescription = contentDescription ?: "" },
    ) {
        if (posterUrl != null && imageLoader != null) {
            AsyncImage(
                model = posterUrl,
                contentDescription = null,
                imageLoader = imageLoader,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else if (posterUrl != null) {
            AsyncImage(
                model = posterUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/** Bottom action bar while selecting: select all, clear, refresh, scan, edit, delete. */
@Composable
private fun SelectionBar(
    onRefreshMetadata: () -> Unit,
    onScanDisk: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onSelectAll: () -> Unit,
    onClear: () -> Unit,
) {
    Surface(tonalElevation = 3.dp) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
        ) {
            IconButton(onClick = onSelectAll) {
                Icon(
                    imageVector = Icons.Filled.SelectAll,
                    contentDescription = stringResource(R.string.arr_lidarr_library_cd_select_all),
                )
            }
            IconButton(onClick = onClear) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = stringResource(R.string.arr_lidarr_library_cd_clear_selection),
                )
            }
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onRefreshMetadata) {
                Icon(
                    imageVector = Icons.Filled.Refresh,
                    contentDescription = stringResource(R.string.arr_lidarr_library_cd_refresh_metadata),
                )
            }
            IconButton(onClick = onScanDisk) {
                Icon(
                    imageVector = Icons.Filled.GridView,
                    contentDescription = stringResource(R.string.arr_lidarr_library_cd_scan_disk),
                )
            }
            IconButton(onClick = onEdit) {
                Icon(
                    imageVector = Icons.Filled.Edit,
                    contentDescription = stringResource(R.string.arr_lidarr_library_cd_edit_selected),
                )
            }
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Filled.Delete,
                    contentDescription = stringResource(R.string.arr_lidarr_library_cd_delete_selected),
                )
            }
        }
    }
}

/** Triggers [onLoadMore] when the grid nears the end of the loaded window. */
@Composable
private fun GridLoadMoreEffect(
    gridState: LazyGridState,
    enabled: Boolean,
    onLoadMore: () -> Unit,
) {
    val shouldLoad by remember {
        derivedStateOf {
            val last = gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            val total = gridState.layoutInfo.totalItemsCount
            enabled && total > 0 && last >= total - 12
        }
    }
    LaunchedEffect(shouldLoad) {
        if (shouldLoad) onLoadMore()
    }
}

@Composable
private fun ListLoadMoreEffect(
    listState: LazyListState,
    enabled: Boolean,
    onLoadMore: () -> Unit,
) {
    val shouldLoad by remember {
        derivedStateOf {
            val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            val total = listState.layoutInfo.totalItemsCount
            enabled && total > 0 && last >= total - 12
        }
    }
    LaunchedEffect(shouldLoad) {
        if (shouldLoad) onLoadMore()
    }
}

@Composable
private fun SaveFilterDialog(onSave: (String) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.arr_lidarr_saved_filter_dialog_title)) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.arr_lidarr_saved_filter_name_hint)) },
                singleLine = true,
            )
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(name)
                    onDismiss()
                },
                enabled = name.isNotBlank(),
            ) {
                Text(stringResource(R.string.arr_lidarr_saved_filter_save_button))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.arr_lidarr_saved_filter_cancel))
            }
        },
    )
}

@Composable
private fun libraryMessageText(message: LibraryMessage): String = when (message) {
    LibraryMessage.EditSynced -> stringResource(R.string.arr_lidarr_msg_edit_synced)
    LibraryMessage.EditQueued -> stringResource(R.string.arr_lidarr_msg_edit_queued)
    LibraryMessage.DeleteSynced -> stringResource(R.string.arr_lidarr_msg_delete_synced)
    LibraryMessage.DeleteQueued -> stringResource(R.string.arr_lidarr_msg_delete_queued)
    LibraryMessage.CommandStarted -> stringResource(R.string.arr_lidarr_msg_command_started)
    LibraryMessage.CommandQueued -> stringResource(R.string.arr_lidarr_msg_command_queued)
    is LibraryMessage.WriteFailed -> stringResource(R.string.arr_lidarr_msg_write_failed)
}
