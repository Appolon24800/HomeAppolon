package dev.appolon.homelab.arr.feature.radarr.library.ui

import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.foundation.background
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
import dev.appolon.homelab.arr.api.radarr.model.MovieResource
import dev.appolon.homelab.arr.core.FilterValue
import dev.appolon.homelab.arr.core.Humanize
import dev.appolon.homelab.arr.feature.radarr.R
import dev.appolon.homelab.arr.feature.radarr.library.data.MovieLibraryFilters
import dev.appolon.homelab.arr.feature.radarr.library.data.MovieLibrarySorts
import dev.appolon.homelab.arr.feature.radarr.library.data.MoviePosterUrls
import dev.appolon.homelab.arr.feature.radarr.library.data.SavedMovieFilter
import dev.appolon.homelab.arr.feature.radarr.library.vm.LibraryMessage
import dev.appolon.homelab.arr.feature.radarr.library.vm.LibraryView
import dev.appolon.homelab.arr.feature.radarr.library.vm.MovieEditorForm
import dev.appolon.homelab.arr.feature.radarr.library.vm.MovieLibraryUiState
import dev.appolon.homelab.arr.ui.components.FilterChipRow
import dev.appolon.homelab.arr.ui.components.PosterCard
import dev.appolon.homelab.arr.ui.components.SortMenu
import dev.appolon.homelab.arr.ui.lce.ArrLceBox
import dev.appolon.homelab.arr.ui.lce.ArrPullToRefreshBox
import dev.appolon.homelab.arr.ui.lce.LceState
import dev.appolon.homelab.arr.ui.shell.ArrScaffold

/**
 * The movies library: poster grid or table over the presented list, filter
 * chips, saved filters, bulk selection with editor and delete, refresh and
 * scan commands, pull to refresh, and paged rendering. The screen owns no
 * state; the ViewModel drives it and every failure lands in the LCE box.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun MovieLibraryScreen(
    state: MovieLibraryUiState,
    lce: LceState<MovieLibraryUiState>,
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
    onMovieActivate: (Int) -> Unit,
    onMovieLongClick: (Int) -> Unit,
    onSelectAll: () -> Unit,
    onExitSelection: () -> Unit,
    onOpenEditor: () -> Unit,
    onEditorChange: ((MovieEditorForm) -> MovieEditorForm) -> Unit,
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
            stringResource(R.string.arr_radarr_library_selected_count, state.selection.size)
        } else {
            stringResource(R.string.arr_radarr_library_title)
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
                        emptyTitle = stringResource(R.string.arr_radarr_library_empty_title),
                        emptyMessage = stringResource(R.string.arr_radarr_library_empty_message),
                        modifier = Modifier.fillMaxSize(),
                    ) { content ->
                        if (content.view == LibraryView.POSTERS) {
                            PosterGrid(
                                state = content,
                                baseUrl = baseUrl,
                                imageLoader = imageLoader,
                                onMovieActivate = onMovieActivate,
                                onMovieLongClick = onMovieLongClick,
                                onLoadMore = onLoadMore,
                            )
                        } else {
                            MovieTable(
                                state = content,
                                baseUrl = baseUrl,
                                imageLoader = imageLoader,
                                onMovieActivate = onMovieActivate,
                                onMovieLongClick = onMovieLongClick,
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
        MovieFilterSheet(
            filterState = state.filterState,
            specs = MovieLibraryFilters.specs(state.options),
            profileLabel = state::profileLabel,
            onValueChange = onFilterValueChange,
            onClear = onClearFilters,
            onDismiss = { filterSheetOpen = false },
        )
    }
    if (saveDialogOpen) {
        SaveFilterDialog(onSave = onSaveFilter, onDismiss = { saveDialogOpen = false })
    }
    state.editorForm?.let { form ->
        MovieEditorSheet(
            form = form,
            targetCount = state.bulkTargets.size,
            qualityProfiles = state.qualityProfiles,
            onChange = onEditorChange,
            onSubmit = onEditorSubmit,
            onDismiss = onEditorDismiss,
        )
    }
    if (state.confirmDelete) {
        MovieDeleteDialog(
            targetCount = state.bulkTargets.size,
            onConfirm = onDeleteConfirm,
            onDismiss = onDeleteDismiss,
        )
    }
}

/** Toolbar outside selection mode: queue badge, view toggle, sort, saved filters. */
@Composable
private fun LibraryActions(
    state: MovieLibraryUiState,
    onViewChange: (LibraryView) -> Unit,
    onSortSelect: (String) -> Unit,
    onToggleSortDescending: () -> Unit,
    onSaveFilterClick: () -> Unit,
    onApplySavedFilter: (String) -> Unit,
    onDeleteSavedFilter: (String) -> Unit,
) {
    // SortMenu's label lambda is not composable, so labels resolve here.
    val sortLabels = MovieLibrarySorts.ALL.associateWith { id -> sortLabel(id) }
    if (state.queuedForServer > 0) {
        val queuedDescription =
            stringResource(R.string.arr_radarr_library_cd_queued, state.queuedForServer)
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
                Icons.Filled.ViewList
            } else {
                Icons.Filled.GridView
            },
            contentDescription = if (state.view == LibraryView.POSTERS) {
                stringResource(R.string.arr_radarr_library_cd_view_table)
            } else {
                stringResource(R.string.arr_radarr_library_cd_view_posters)
            },
        )
    }
    SortMenu(
        options = MovieLibrarySorts.ALL,
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
            contentDescription = stringResource(R.string.arr_radarr_library_cd_sort_descending),
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

@Composable
private fun sortLabel(id: String): String = when (id) {
    MovieLibrarySorts.YEAR -> stringResource(R.string.arr_radarr_sort_year)
    MovieLibrarySorts.ADDED -> stringResource(R.string.arr_radarr_sort_added)
    MovieLibrarySorts.SIZE -> stringResource(R.string.arr_radarr_sort_size)
    else -> stringResource(R.string.arr_radarr_sort_title)
}

/** Saved filters anchor; the icon is a 48dp target per the accessibility contract. */
@Composable
private fun SavedFiltersMenu(
    saved: List<SavedMovieFilter>,
    activeId: String?,
    onSaveClick: () -> Unit,
    onApply: (String) -> Unit,
    onDelete: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    IconButton(onClick = { expanded = true }) {
        Icon(
            imageVector = Icons.Filled.SaveAlt,
            contentDescription = stringResource(R.string.arr_radarr_saved_filters),
        )
    }
    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
        DropdownMenuItem(
            text = { Text(stringResource(R.string.arr_radarr_saved_filter_save)) },
            onClick = {
                expanded = false
                onSaveClick()
            },
        )
        if (saved.isEmpty()) {
            DropdownMenuItem(
                text = {
                    Text(
                        stringResource(R.string.arr_radarr_saved_filter_empty),
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
                            R.string.arr_radarr_saved_filter_delete_cd,
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
    state: MovieLibraryUiState,
    onFilterClick: () -> Unit,
    onClear: () -> Unit,
    onApplySavedFilter: (String) -> Unit,
) {
    val chips = listOf(
        MovieLibraryFilters.MONITORED,
        MovieLibraryFilters.MISSING,
        MovieLibraryFilters.QUALITY,
        MovieLibraryFilters.GENRE,
        MovieLibraryFilters.YEAR,
        MovieLibraryFilters.FOLDER,
        MovieLibraryFilters.TAGS,
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
                    label = { Text(stringResource(R.string.arr_radarr_filter_clear)) },
                )
            }
        }
    }
}

/** Poster grid with adaptive columns, so tablets naturally fit more columns. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PosterGrid(
    state: MovieLibraryUiState,
    baseUrl: String,
    imageLoader: ImageLoader?,
    onMovieActivate: (Int) -> Unit,
    onMovieLongClick: (Int) -> Unit,
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
        items(state.presentation.visible, key = { it.id }) { movie ->
            MoviePosterCard(
                movie = movie,
                baseUrl = baseUrl,
                imageLoader = imageLoader,
                selectMode = state.selectMode,
                selected = movie.id in state.selection,
                queued = movie.id in state.queuedMovieIds,
                onActivate = { onMovieActivate(movie.id) },
                onLongClick = { onMovieLongClick(movie.id) },
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
private fun MoviePosterCard(
    movie: MovieResource,
    baseUrl: String,
    imageLoader: ImageLoader?,
    selectMode: Boolean,
    selected: Boolean,
    queued: Boolean,
    onActivate: () -> Unit,
    onLongClick: () -> Unit,
) {
    val title = movie.title.orEmpty().ifEmpty { "#" + movie.id }
    val description = if (selectMode) {
        stringResource(R.string.arr_radarr_library_cd_select_movie, title)
    } else {
        stringResource(R.string.arr_radarr_library_cd_open_movie, title)
    }
    Box(
        modifier = Modifier
            .semantics { contentDescription = description }
            .combinedClickable(onClick = {}, onLongClick = onLongClick),
    ) {
        PosterCard(
            title = title,
            posterUrl = MoviePosterUrls.posterUrlOf(movie, baseUrl),
            imageLoader = imageLoader,
            onClick = onActivate,
            subtitle = if (movie.year > 0) movie.year.toString() else null,
            badges = {
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    if (MovieLibraryFilters.isMissing(movie)) {
                        PosterBadge(
                            stringResource(R.string.arr_radarr_library_badge_missing),
                            MaterialTheme.colorScheme.errorContainer,
                        )
                    }
                    if (queued) {
                        PosterBadge(
                            stringResource(R.string.arr_radarr_library_badge_queued),
                            MaterialTheme.colorScheme.tertiaryContainer,
                        )
                    }
                }
            },
        )
        if (selectMode) {
            SelectionCheck(selected = selected, modifier = Modifier.align(Alignment.TopStart))
        }
    }
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

/** Compact table view: thumb, title and year, profile, size, badges. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MovieTable(
    state: MovieLibraryUiState,
    baseUrl: String,
    imageLoader: ImageLoader?,
    onMovieActivate: (Int) -> Unit,
    onMovieLongClick: (Int) -> Unit,
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
        items(state.presentation.visible, key = { it.id }) { movie ->
            MovieTableRow(
                movie = movie,
                state = state,
                baseUrl = baseUrl,
                imageLoader = imageLoader,
                onActivate = { onMovieActivate(movie.id) },
                onLongClick = { onMovieLongClick(movie.id) },
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MovieTableRow(
    movie: MovieResource,
    state: MovieLibraryUiState,
    baseUrl: String,
    imageLoader: ImageLoader?,
    onActivate: () -> Unit,
    onLongClick: () -> Unit,
) {
    val title = movie.title.orEmpty().ifEmpty { "#" + movie.id }
    val description = if (state.selectMode) {
        stringResource(R.string.arr_radarr_library_cd_select_movie, title)
    } else {
        stringResource(R.string.arr_radarr_library_cd_open_movie, title)
    }
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (movie.id in state.selection) {
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
                posterUrl = MoviePosterUrls.posterUrlOf(movie, baseUrl),
                contentDescription = null,
                imageLoader = imageLoader,
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp),
            ) {
                Text(
                    text = buildString {
                        append(title)
                        if (movie.year > 0) append(" (").append(movie.year).append(')')
                    },
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val size = movie.statistics?.sizeOnDisk ?: movie.sizeOnDisk
                val details = listOfNotNull(
                    state.profileLabel(movie.qualityProfileId),
                    size?.takeIf { it > 0 }?.let { Humanize.bytes(it) },
                ).joinToString(" \u00b7 ")
                Text(
                    text = details,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (MovieLibraryFilters.isMissing(movie)) {
                PosterBadge(
                    stringResource(R.string.arr_radarr_library_badge_missing),
                    MaterialTheme.colorScheme.errorContainer,
                )
            }
            if (movie.id in state.queuedMovieIds) {
                PosterBadge(
                    stringResource(R.string.arr_radarr_library_badge_queued),
                    MaterialTheme.colorScheme.tertiaryContainer,
                )
            }
            Icon(
                imageVector = Icons.Filled.Done,
                contentDescription = if (movie.monitored) {
                    stringResource(R.string.arr_radarr_library_cd_monitored)
                } else {
                    stringResource(R.string.arr_radarr_library_cd_unmonitored)
                },
                tint = if (movie.monitored) {
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
                    contentDescription = stringResource(R.string.arr_radarr_library_cd_select_all),
                )
            }
            IconButton(onClick = onClear) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = stringResource(R.string.arr_radarr_library_cd_clear_selection),
                )
            }
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onRefreshMetadata) {
                Icon(
                    imageVector = Icons.Filled.Refresh,
                    contentDescription = stringResource(R.string.arr_radarr_library_cd_refresh_metadata),
                )
            }
            IconButton(onClick = onScanDisk) {
                Icon(
                    imageVector = Icons.Filled.GridView,
                    contentDescription = stringResource(R.string.arr_radarr_library_cd_scan_disk),
                )
            }
            IconButton(onClick = onEdit) {
                Icon(
                    imageVector = Icons.Filled.Edit,
                    contentDescription = stringResource(R.string.arr_radarr_library_cd_edit_selected),
                )
            }
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Filled.Delete,
                    contentDescription = stringResource(R.string.arr_radarr_library_cd_delete_selected),
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
        title = { Text(stringResource(R.string.arr_radarr_saved_filter_dialog_title)) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.arr_radarr_saved_filter_name_hint)) },
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
                Text(stringResource(R.string.arr_radarr_saved_filter_save_button))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.arr_radarr_saved_filter_cancel))
            }
        },
    )
}

@Composable
private fun libraryMessageText(message: LibraryMessage): String = when (message) {
    LibraryMessage.EditSynced -> stringResource(R.string.arr_radarr_msg_edit_synced)
    LibraryMessage.EditQueued -> stringResource(R.string.arr_radarr_msg_edit_queued)
    LibraryMessage.DeleteSynced -> stringResource(R.string.arr_radarr_msg_delete_synced)
    LibraryMessage.DeleteQueued -> stringResource(R.string.arr_radarr_msg_delete_queued)
    LibraryMessage.CommandStarted -> stringResource(R.string.arr_radarr_msg_command_started)
    LibraryMessage.CommandQueued -> stringResource(R.string.arr_radarr_msg_command_queued)
    is LibraryMessage.WriteFailed -> stringResource(R.string.arr_radarr_msg_write_failed)
}
