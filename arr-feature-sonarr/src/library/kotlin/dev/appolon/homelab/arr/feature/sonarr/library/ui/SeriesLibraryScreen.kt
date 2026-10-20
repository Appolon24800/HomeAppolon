package dev.appolon.homelab.arr.feature.sonarr.library.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.SortByAlpha
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material3.*
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.ImageLoader
import coil3.compose.AsyncImage
import dev.appolon.homelab.arr.api.sonarr.model.SeriesResource
import dev.appolon.homelab.arr.core.FilterSpec
import dev.appolon.homelab.arr.core.FilterValue
import dev.appolon.homelab.arr.core.Humanize
import dev.appolon.homelab.arr.feature.sonarr.R
import dev.appolon.homelab.arr.feature.sonarr.library.data.SeriesLibraryFilters
import dev.appolon.homelab.arr.feature.sonarr.library.data.SeriesLibrarySorts
import dev.appolon.homelab.arr.feature.sonarr.library.data.SeriesPosterUrls
import dev.appolon.homelab.arr.feature.sonarr.library.vm.LibraryMessage
import dev.appolon.homelab.arr.feature.sonarr.library.vm.LibraryView
import dev.appolon.homelab.arr.feature.sonarr.library.vm.SeasonPassForm
import dev.appolon.homelab.arr.feature.sonarr.library.vm.SeriesEditorForm
import dev.appolon.homelab.arr.feature.sonarr.library.vm.SeriesLibraryUiState
import dev.appolon.homelab.arr.ui.components.PosterCard
import dev.appolon.homelab.arr.ui.components.SortMenu
import dev.appolon.homelab.arr.ui.lce.ArrLceBox
import dev.appolon.homelab.arr.ui.lce.ArrPullToRefreshBox
import dev.appolon.homelab.arr.ui.lce.LceState

/**
 * The series library screen body: saved filters, filter chips, poster grid or
 * table, bulk selection with editor and season pass sheets. Stateless; every
 * mutation goes through the ViewModel callbacks. The grid adapts its column
 * count and the table gains a second line on wide (tablet) widths.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun SeriesLibraryContent(
    state: SeriesLibraryUiState,
    baseUrl: String,
    imageLoader: ImageLoader?,
    onRefresh: () -> Unit,
    onSetView: (LibraryView) -> Unit,
    onSetSort: (String) -> Unit,
    onToggleSortDescending: () -> Unit,
    onLoadMore: () -> Unit,
    onFilterValuePicked: (String, FilterValue) -> Unit,
    onClearFilters: () -> Unit,
    onApplySavedFilter: (String) -> Unit,
    onDeleteSavedFilter: (String) -> Unit,
    onSaveFilter: (String) -> Unit,
    onEnterSelectMode: (Int) -> Unit,
    onExitSelectMode: () -> Unit,
    onToggleSelected: (Int) -> Unit,
    onSelectAllPresented: () -> Unit,
    onOpenSeries: (Int) -> Unit,
    onOpenEditor: () -> Unit,
    onCloseEditor: () -> Unit,
    onUpdateEditorForm: (SeriesEditorForm) -> Unit,
    onSubmitEditor: () -> Unit,
    onOpenSeasonPass: () -> Unit,
    onCloseSeasonPass: () -> Unit,
    onUpdateSeasonPassForm: (SeasonPassForm) -> Unit,
    onSubmitSeasonPass: () -> Unit,
    onRequestDelete: () -> Unit,
    onDismissDelete: () -> Unit,
    onConfirmDelete: (Boolean, Boolean) -> Unit,
    onRefreshMetadata: () -> Unit,
    onScanDisk: () -> Unit,
    onConsumeMessage: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val messageText = libraryMessageText(state.message)
    LaunchedEffect(state.message) {
        if (messageText != null) {
            snackbarHostState.showSnackbar(messageText)
            onConsumeMessage()
        }
    }

    var openFilterId by remember { mutableStateOf<String?>(null) }
    var showSaveFilter by remember { mutableStateOf(false) }
    var deleteFiles by remember { mutableStateOf(false) }
    var addExclusion by remember { mutableStateOf(false) }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            if (state.selectMode) {
                SelectionTopBar(
                    state = state,
                    onExit = onExitSelectMode,
                    onSelectAll = onSelectAllPresented,
                    onEdit = onOpenEditor,
                    onSeasonPass = onOpenSeasonPass,
                    onRefreshMetadata = onRefreshMetadata,
                    onScanDisk = onScanDisk,
                    onDelete = onRequestDelete,
                )
            } else {
                LibraryTopBar(
                    state = state,
                    onSetView = onSetView,
                    onSetSort = onSetSort,
                    onToggleSortDescending = onToggleSortDescending,
                    onRefreshMetadata = onRefreshMetadata,
                    onScanDisk = onScanDisk,
                    onSaveFilter = { showSaveFilter = true },
                )
            }
        },
        modifier = modifier.fillMaxSize(),
    ) { padding ->
        ArrLceBox(
            state = state.lceFor(),
            onRetry = onRefresh,
            modifier = Modifier.padding(padding),
            emptyTitle = stringResource(R.string.arr_sonarr_empty_title),
            emptyMessage = stringResource(R.string.arr_sonarr_empty_message),
        ) { content ->
            ArrPullToRefreshBox(
                isRefreshing = content.refreshing,
                onRefresh = onRefresh,
                modifier = Modifier.fillMaxSize(),
            ) {
                Column(Modifier.fillMaxSize()) {
                    SavedFilterRow(
                        state = content,
                        onApply = onApplySavedFilter,
                        onDelete = onDeleteSavedFilter,
                    )
                    FilterChipBar(
                        state = content,
                        onOpenFilter = { openFilterId = it },
                        onClear = onClearFilters,
                    )
                    if (content.queuedForServer > 0) {
                        QueuedBanner(count = content.queuedForServer)
                    }
                    if (content.view == LibraryView.POSTERS) {
                        PosterGrid(
                            state = content,
                            baseUrl = baseUrl,
                            imageLoader = imageLoader,
                            onLoadMore = onLoadMore,
                            onOpenSeries = onOpenSeries,
                            onEnterSelectMode = onEnterSelectMode,
                            onToggleSelected = onToggleSelected,
                        )
                    } else {
                        SeriesTable(
                            state = content,
                            baseUrl = baseUrl,
                            imageLoader = imageLoader,
                            onLoadMore = onLoadMore,
                            onOpenSeries = onOpenSeries,
                            onEnterSelectMode = onEnterSelectMode,
                            onToggleSelected = onToggleSelected,
                        )
                    }
                }
            }
        }
    }

    openFilterId?.let { id ->
        val spec = specOf(state, id)
        if (spec != null) {
            FilterValueDialog(
                title = filterTitleOf(id),
                spec = spec,
                value = state.filterState.value(id) ?: emptyValueFor(spec),
                optionLabel = { option -> optionLabelOf(state, id, option) },
                onDismiss = { openFilterId = null },
                onApply = { value ->
                    onFilterValuePicked(id, value)
                    openFilterId = null
                },
            )
        }
    }

    if (showSaveFilter) {
        SaveFilterDialog(
            onDismiss = { showSaveFilter = false },
            onSave = { label ->
                showSaveFilter = false
                onSaveFilter(label)
            },
        )
    }

    state.editorForm?.let { form ->
        SeriesEditorSheet(
            form = form,
            targets = state.bulkTargets.size,
            qualityProfiles = state.qualityProfiles,
            onDismiss = onCloseEditor,
            onUpdate = onUpdateEditorForm,
            onSubmit = onSubmitEditor,
        )
    }

    state.seasonPassForm?.let { form ->
        SeasonPassSheet(
            form = form,
            targets = state.bulkTargets.size,
            onDismiss = onCloseSeasonPass,
            onUpdate = onUpdateSeasonPassForm,
            onSubmit = onSubmitSeasonPass,
        )
    }

    if (state.confirmDelete) {
        DeleteSeriesDialog(
            count = state.bulkTargets.size,
            deleteFiles = deleteFiles,
            addImportListExclusion = addExclusion,
            onOptionChange = { files, exclusion ->
                deleteFiles = files
                addExclusion = exclusion
            },
            onDismiss = onDismissDelete,
            onConfirm = { onConfirmDelete(deleteFiles, addExclusion) },
        )
    }
}

/** The LCE projection the scaffold body renders. */
private fun SeriesLibraryUiState.lceFor(): LceState<SeriesLibraryUiState> = when {
    loadError != null && series.isEmpty() -> LceState.Error(loadError)
    !loaded -> LceState.Loading
    series.isEmpty() -> LceState.Empty
    else -> LceState.Content(this, refreshing = refreshing)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LibraryTopBar(
    state: SeriesLibraryUiState,
    onSetView: (LibraryView) -> Unit,
    onSetSort: (String) -> Unit,
    onToggleSortDescending: () -> Unit,
    onRefreshMetadata: () -> Unit,
    onScanDisk: () -> Unit,
    onSaveFilter: () -> Unit,
) {
    TopAppBar(
        title = { Text(stringResource(R.string.arr_sonarr_library_title)) },
        actions = {
            val nextView =
                if (state.view == LibraryView.POSTERS) LibraryView.TABLE else LibraryView.POSTERS
            IconButton(onClick = { onSetView(nextView) }) {
                Icon(
                    imageVector = if (state.view == LibraryView.POSTERS) {
                        Icons.Filled.ViewList
                    } else {
                        Icons.Filled.GridView
                    },
                    contentDescription = stringResource(
                        if (state.view == LibraryView.POSTERS) {
                            R.string.arr_sonarr_cd_view_table
                        } else {
                            R.string.arr_sonarr_cd_view_posters
                        },
                    ),
                )
            }
            val sortLabels = SeriesLibrarySorts.ALL.associateWith { id -> sortLabelOf(id) }
            SortMenu(
                options = SeriesLibrarySorts.ALL,
                selected = state.sortId,
                label = { id -> sortLabels[id].orEmpty() },
                onSelect = onSetSort,
            )
            IconButton(onClick = onToggleSortDescending) {
                Icon(
                    imageVector = Icons.Filled.SortByAlpha,
                    contentDescription = stringResource(R.string.arr_sonarr_cd_toggle_descending),
                )
            }
            var menuOpen by remember { mutableStateOf(false) }
            IconButton(onClick = { menuOpen = true }) {
                Icon(
                    imageVector = Icons.Filled.MoreVert,
                    contentDescription = stringResource(R.string.arr_sonarr_cd_more_options),
                )
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.arr_sonarr_menu_refresh)) },
                    onClick = {
                        menuOpen = false
                        onRefreshMetadata()
                    },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.arr_sonarr_menu_scan)) },
                    onClick = {
                        menuOpen = false
                        onScanDisk()
                    },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.arr_sonarr_menu_save_filter)) },
                    onClick = {
                        menuOpen = false
                        onSaveFilter()
                    },
                )
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SelectionTopBar(
    state: SeriesLibraryUiState,
    onExit: () -> Unit,
    onSelectAll: () -> Unit,
    onEdit: () -> Unit,
    onSeasonPass: () -> Unit,
    onRefreshMetadata: () -> Unit,
    onScanDisk: () -> Unit,
    onDelete: () -> Unit,
) {
    TopAppBar(
        title = { Text(stringResource(R.string.arr_sonarr_selected_count, state.selection.size)) },
        navigationIcon = {
            IconButton(onClick = onExit) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = stringResource(R.string.arr_sonarr_cd_exit_select),
                )
            }
        },
        actions = {
            IconButton(onClick = onSelectAll) {
                Icon(
                    imageVector = Icons.Filled.SelectAll,
                    contentDescription = stringResource(R.string.arr_sonarr_cd_select_all),
                )
            }
            IconButton(onClick = onEdit) {
                Icon(
                    imageVector = Icons.Filled.Edit,
                    contentDescription = stringResource(R.string.arr_sonarr_cd_open_editor),
                )
            }
            IconButton(onClick = onSeasonPass) {
                Icon(
                    imageVector = Icons.Filled.DoneAll,
                    contentDescription = stringResource(R.string.arr_sonarr_cd_open_season_pass),
                )
            }
            IconButton(onClick = onRefreshMetadata) {
                Icon(
                    imageVector = Icons.Filled.Refresh,
                    contentDescription = stringResource(R.string.arr_sonarr_cd_refresh_selected),
                )
            }
            IconButton(onClick = onScanDisk) {
                Icon(
                    imageVector = Icons.Filled.Storage,
                    contentDescription = stringResource(R.string.arr_sonarr_cd_scan_selected),
                )
            }
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Filled.Delete,
                    contentDescription = stringResource(R.string.arr_sonarr_cd_delete_selected),
                )
            }
        },
    )
}

/** Saved custom filters: tapping applies; the active one gets a delete button. */
@Composable
private fun SavedFilterRow(
    state: SeriesLibraryUiState,
    onApply: (String) -> Unit,
    onDelete: (String) -> Unit,
) {
    if (state.savedFilters.isEmpty()) return
    Row(verticalAlignment = Alignment.CenterVertically) {
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
            modifier = Modifier.weight(1f, fill = false),
        ) {
            items(state.savedFilters, key = { it.id }) { filter ->
                FilterChip(
                    selected = state.activeSavedFilterId == filter.id,
                    onClick = { onApply(filter.id) },
                    label = { Text(filter.label) },
                )
            }
        }
        state.activeSavedFilterId?.let { activeId ->
            val active = state.savedFilters.firstOrNull { it.id == activeId }
            if (active != null) {
                IconButton(onClick = { onDelete(activeId) }) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = stringResource(
                            R.string.arr_sonarr_saved_delete_cd,
                            active.label,
                        ),
                    )
                }
            }
        }
    }
}

/** Filter chips for every spec, plus a clear chip while any filter is active. */
@Composable
private fun FilterChipBar(
    state: SeriesLibraryUiState,
    onOpenFilter: (String) -> Unit,
    onClear: () -> Unit,
) {
    val specs = SeriesLibraryFilters.specs(state.options)
    val anyActive = specs.any { state.filterState.isActive(it.id) }
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
    ) {
        items(specs, key = { it.id }) { spec ->
            FilterChip(
                selected = state.filterState.isActive(spec.id),
                onClick = { onOpenFilter(spec.id) },
                label = {
                    Text(
                        if (state.filterState.isActive(spec.id)) {
                            filterTitleOf(spec.id) + " · " + activeSummary(state, spec)
                        } else {
                            filterTitleOf(spec.id)
                        },
                    )
                },
            )
        }
        if (anyActive) {
            item(key = "clear") {
                FilterChip(
                    selected = false,
                    onClick = onClear,
                    label = { Text(stringResource(R.string.arr_sonarr_filter_clear)) },
                )
            }
        }
    }
}

/** Compact one-line summary of an active filter for its chip. */
@Composable
private fun activeSummary(state: SeriesLibraryUiState, spec: FilterSpec): String = when (spec.id) {
    SeriesLibraryFilters.MONITORED, SeriesLibraryFilters.MISSING -> {
        when ((state.filterState.values[spec.id] as? FilterValue.Toggle)?.on) {
            true -> stringResource(R.string.arr_sonarr_filter_toggle_on)
            false -> stringResource(R.string.arr_sonarr_filter_toggle_off)
            else -> stringResource(R.string.arr_sonarr_filter_toggle_any)
        }
    }

    SeriesLibraryFilters.YEAR, SeriesLibraryFilters.SEASONS -> {
        val range = state.filterState.values[spec.id] as? FilterValue.Range
        val span = if (spec.id == SeriesLibraryFilters.YEAR) {
            state.options.yearSpan
        } else {
            state.options.seasonSpan
        }
        val min = range?.min?.toInt() ?: span.start.toInt()
        val max = range?.max?.toInt() ?: span.endInclusive.toInt()
        "$min–$max"
    }

    else -> {
        val selected = (state.filterState.values[spec.id] as? FilterValue.Choice)?.selected
        stringResource(R.string.arr_sonarr_filter_active_count, selected?.size ?: 0)
    }
}

/** Offline queue indicator, shown above the list while actions wait to sync. */
@Composable
private fun QueuedBanner(count: Int) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        Icon(
            imageVector = Icons.Filled.Schedule,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.tertiary,
            modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = stringResource(R.string.arr_sonarr_queued_banner, count),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.tertiary,
        )
    }
}

/**
 * Adaptive poster grid. The wrapper's long-press enters selection mode; plain
 * taps are consumed by the card's own clickable and never double-fire.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PosterGrid(
    state: SeriesLibraryUiState,
    baseUrl: String,
    imageLoader: ImageLoader?,
    onLoadMore: () -> Unit,
    onOpenSeries: (Int) -> Unit,
    onEnterSelectMode: (Int) -> Unit,
    onToggleSelected: (Int) -> Unit,
) {
    val gridState = rememberLazyGridState()
    val nearEnd by remember {
        derivedStateOf {
            val info = gridState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: -1
            info.totalItemsCount > 0 && last >= info.totalItemsCount - 12
        }
    }
    LaunchedEffect(nearEnd) {
        if (nearEnd) onLoadMore()
    }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 120.dp),
        state = gridState,
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        items(state.presentation.visible, key = { it.id }) { series ->
            val title = series.title.orEmpty().ifEmpty { "#" + series.id }
            Box(
                modifier = Modifier.combinedClickable(
                    onClick = {},
                    onLongClick = { onEnterSelectMode(series.id) },
                ),
            ) {
                PosterCard(
                    title = title,
                    posterUrl = SeriesPosterUrls.posterUrlOf(series, baseUrl),
                    imageLoader = imageLoader,
                    subtitle = posterSubtitleOf(series),
                    onClick = {
                        if (state.selectMode) onToggleSelected(series.id) else onOpenSeries(series.id)
                    },
                    badges = {
                        PosterBadges(
                            series = series,
                            queued = series.id in state.queuedSeriesIds,
                            selected = series.id in state.selection && state.selectMode,
                        )
                    },
                )
            }
        }
    }
}

/** Series table rows with banner art; wide screens get a second detail line. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SeriesTable(
    state: SeriesLibraryUiState,
    baseUrl: String,
    imageLoader: ImageLoader?,
    onLoadMore: () -> Unit,
    onOpenSeries: (Int) -> Unit,
    onEnterSelectMode: (Int) -> Unit,
    onToggleSelected: (Int) -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val expanded = maxWidth >= 600.dp
        val scrollState = rememberScrollState()
        val nearEnd by remember {
            derivedStateOf { scrollState.value >= scrollState.maxValue - 800 }
        }
        LaunchedEffect(nearEnd) {
            if (nearEnd) onLoadMore()
        }
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(scrollState),
        ) {
            state.presentation.visible.forEach { series ->
                SeriesTableRow(
                    series = series,
                    state = state,
                    baseUrl = baseUrl,
                    imageLoader = imageLoader,
                    expanded = expanded,
                    onOpenSeries = onOpenSeries,
                    onEnterSelectMode = onEnterSelectMode,
                    onToggleSelected = onToggleSelected,
                )
                HorizontalDivider()
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SeriesTableRow(
    series: SeriesResource,
    state: SeriesLibraryUiState,
    baseUrl: String,
    imageLoader: ImageLoader?,
    expanded: Boolean,
    onOpenSeries: (Int) -> Unit,
    onEnterSelectMode: (Int) -> Unit,
    onToggleSelected: (Int) -> Unit,
) {
    val title = series.title.orEmpty().ifEmpty { "#" + series.id }
    val bannerDescription = stringResource(R.string.arr_sonarr_cd_series_banner, title)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = {
                    if (state.selectMode) onToggleSelected(series.id) else onOpenSeries(series.id)
                },
                onLongClick = { onEnterSelectMode(series.id) },
            )
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        if (state.selectMode) {
            Checkbox(
                checked = series.id in state.selection,
                onCheckedChange = { onToggleSelected(series.id) },
                modifier = Modifier.padding(end = 12.dp),
            )
        }
        Box(
            modifier = Modifier
                .width(104.dp)
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = title.take(1).uppercase().ifEmpty { "?" },
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            val loader = imageLoader
            SeriesPosterUrls.bannerUrlOf(series, baseUrl)?.let { url ->
                if (loader != null) {
                    AsyncImage(
                        model = url,
                        contentDescription = bannerDescription,
                        imageLoader = loader,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val seasons = pluralSeasons(SeriesLibraryFilters.seasonCountOf(series))
            val missing = SeriesLibraryFilters.missingEpisodeCount(series)
            val missingText = if (missing > 0) " · " + pluralMissing(missing) else ""
            Text(
                text = seasons +
                    (series.year.takeIf { it > 0 }?.let { " · $it" } ?: "") +
                    missingText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (expanded) {
                Text(
                    text = listOfNotNull(
                        series.network?.takeIf { it.isNotBlank() },
                        state.profileLabel(series.qualityProfileId),
                        Humanize.bytes(series.statistics.sizeOnDisk)
                            .takeIf { series.statistics.sizeOnDisk > 0 },
                        SeriesLibraryFilters.folderOf(series),
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (series.id in state.queuedSeriesIds) {
            Icon(
                imageVector = Icons.Filled.Schedule,
                contentDescription = stringResource(R.string.arr_sonarr_cd_queued_change),
                tint = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier
                    .size(24.dp)
                    .padding(start = 4.dp),
            )
        }
        MissingBadge(series = series)
        Icon(
            imageVector = if (series.monitored) {
                Icons.Filled.Visibility
            } else {
                Icons.Filled.VisibilityOff
            },
            contentDescription = stringResource(
                if (series.monitored) {
                    R.string.arr_sonarr_cd_monitored
                } else {
                    R.string.arr_sonarr_cd_not_monitored
                },
                title,
            ),
            tint = if (series.monitored) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.outline
            },
            modifier = Modifier
                .size(24.dp)
                .padding(start = 8.dp),
        )
    }
}

/** Overlays a poster's top-end corner: missing count, selection, queued, monitored. */
@Composable
private fun PosterBadges(series: SeriesResource, queued: Boolean, selected: Boolean) {
    Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        MissingBadge(series = series)
        if (queued) {
            Icon(
                imageVector = Icons.Filled.Schedule,
                contentDescription = stringResource(R.string.arr_sonarr_cd_queued_change),
                tint = Color.White,
                modifier = Modifier
                    .size(20.dp)
                    .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(10.dp)),
            )
        }
        Icon(
            imageVector = if (series.monitored) {
                Icons.Filled.Visibility
            } else {
                Icons.Filled.VisibilityOff
            },
            contentDescription = stringResource(
                if (series.monitored) {
                    R.string.arr_sonarr_cd_monitored
                } else {
                    R.string.arr_sonarr_cd_not_monitored
                },
                series.title.orEmpty().ifEmpty { "#" + series.id },
            ),
            tint = if (selected) MaterialTheme.colorScheme.primary else Color.White,
            modifier = Modifier
                .size(20.dp)
                .background(
                    if (selected) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        Color.Black.copy(alpha = 0.6f)
                    },
                    RoundedCornerShape(10.dp),
                ),
        )
    }
}

/** Missing-episode count chip driven by episodeCount versus episodeFileCount. */
@Composable
private fun MissingBadge(series: SeriesResource) {
    val missing = SeriesLibraryFilters.missingEpisodeCount(series)
    if (missing <= 0 || !series.monitored) return
    val description = stringResource(R.string.arr_sonarr_cd_missing_episodes, missing)
    Text(
        text = missing.toString(),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onErrorContainer,
        modifier = Modifier
            .background(MaterialTheme.colorScheme.errorContainer, RoundedCornerShape(8.dp))
            .semantics { contentDescription = description }
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

@Composable
private fun posterSubtitleOf(series: SeriesResource): String {
    val seasons = pluralSeasons(SeriesLibraryFilters.seasonCountOf(series))
    val missing = SeriesLibraryFilters.missingEpisodeCount(series)
    return if (missing > 0 && series.monitored) {
        "$seasons · " + pluralMissing(missing)
    } else {
        seasons
    }
}

@Composable
private fun pluralSeasons(count: Int): String =
    pluralStringResource(R.plurals.arr_sonarr_season_count, count, count)

@Composable
private fun pluralMissing(count: Int): String =
    pluralStringResource(R.plurals.arr_sonarr_missing_count, count, count)

/** Snackbar text for the transient write result. */
@Composable
private fun libraryMessageText(message: LibraryMessage?): String? = when (message) {
    null -> null
    LibraryMessage.EditSynced -> stringResource(R.string.arr_sonarr_message_edit_synced)
    LibraryMessage.EditQueued -> stringResource(R.string.arr_sonarr_message_edit_queued)
    LibraryMessage.SeasonPassSynced -> stringResource(R.string.arr_sonarr_message_season_pass_synced)
    LibraryMessage.SeasonPassQueued -> stringResource(R.string.arr_sonarr_message_season_pass_queued)
    LibraryMessage.DeleteSynced -> stringResource(R.string.arr_sonarr_message_delete_synced)
    LibraryMessage.DeleteQueued -> stringResource(R.string.arr_sonarr_message_delete_queued)
    LibraryMessage.CommandStarted -> stringResource(R.string.arr_sonarr_message_command_started)
    LibraryMessage.CommandQueued -> stringResource(R.string.arr_sonarr_message_command_queued)
    is LibraryMessage.WriteFailed -> stringResource(R.string.arr_sonarr_message_write_failed)
}

/** Resolves the spec for a filter id against the state's current options. */
private fun specOf(state: SeriesLibraryUiState, id: String): FilterSpec? =
    SeriesLibraryFilters.specs(state.options).firstOrNull { it.id == id }

@Composable
private fun filterTitleOf(id: String): String = stringResource(
    when (id) {
        SeriesLibraryFilters.MONITORED -> R.string.arr_sonarr_filter_monitored
        SeriesLibraryFilters.MISSING -> R.string.arr_sonarr_filter_missing
        SeriesLibraryFilters.QUALITY -> R.string.arr_sonarr_filter_quality
        SeriesLibraryFilters.GENRE -> R.string.arr_sonarr_filter_genre
        SeriesLibraryFilters.NETWORK -> R.string.arr_sonarr_filter_network
        SeriesLibraryFilters.SEASONS -> R.string.arr_sonarr_filter_seasons
        SeriesLibraryFilters.YEAR -> R.string.arr_sonarr_filter_year
        SeriesLibraryFilters.FOLDER -> R.string.arr_sonarr_filter_folder
        else -> R.string.arr_sonarr_filter_tags
    },
)

/** Option display label: ids resolve through profile or tag labels when known. */
private fun optionLabelOf(state: SeriesLibraryUiState, id: String, option: String): String =
    when (id) {
        SeriesLibraryFilters.QUALITY -> option.toIntOrNull()?.let { state.profileLabel(it) } ?: option
        SeriesLibraryFilters.TAGS -> option.toIntOrNull()?.let { state.tagLabel(it) } ?: option
        else -> option
    }

private fun emptyValueFor(spec: FilterSpec): FilterValue = when (spec) {
    is FilterSpec.Choice -> FilterValue.Choice(emptySet())
    is FilterSpec.Range -> FilterValue.Range(null, null)
    is FilterSpec.Toggle -> FilterValue.Toggle(null)
}

@Composable
private fun sortLabelOf(id: String): String = stringResource(
    when (id) {
        SeriesLibrarySorts.YEAR -> R.string.arr_sonarr_sort_year
        SeriesLibrarySorts.SEASONS -> R.string.arr_sonarr_sort_seasons
        SeriesLibrarySorts.ADDED -> R.string.arr_sonarr_sort_added
        SeriesLibrarySorts.SIZE -> R.string.arr_sonarr_sort_size
        else -> R.string.arr_sonarr_sort_title
    },
)
