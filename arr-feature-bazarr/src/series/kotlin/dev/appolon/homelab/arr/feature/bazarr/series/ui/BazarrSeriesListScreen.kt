package dev.appolon.homelab.arr.feature.bazarr.series.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.ImageLoader
import dev.appolon.homelab.arr.core.FilterValue
import dev.appolon.homelab.arr.feature.bazarr.R
import dev.appolon.homelab.arr.feature.bazarr.series.data.BazarrSeries
import dev.appolon.homelab.arr.feature.bazarr.series.data.BazarrSeriesSorts
import dev.appolon.homelab.arr.feature.bazarr.series.vm.BazarrLibraryUiState
import dev.appolon.homelab.arr.feature.bazarr.series.vm.LibraryView
import dev.appolon.homelab.arr.ui.components.FilterChipRow
import dev.appolon.homelab.arr.ui.components.PosterCard
import dev.appolon.homelab.arr.ui.components.SortMenu
import dev.appolon.homelab.arr.ui.lce.ArrLceBox
import dev.appolon.homelab.arr.ui.lce.ArrPullToRefreshBox
import dev.appolon.homelab.arr.ui.lce.LceState
import dev.appolon.homelab.arr.ui.shell.ArrScaffold

/**
 * The Bazarr series list: poster grid or table over the presented list,
 * status badges computed from the episode counts, filter chips with the
 * sheet, bulk selection with the language-profile mass editor, and queued
 * indicators for offline assignments. The screen owns no state; the
 * ViewModel drives it and every failure lands in the LCE box.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun BazarrSeriesListScreen(
    state: BazarrLibraryUiState,
    lce: LceState<BazarrLibraryUiState>,
    imageLoader: ImageLoader?,
    onRefresh: () -> Unit,
    onLoadMore: () -> Unit,
    onViewChange: (LibraryView) -> Unit,
    onSortSelect: (String) -> Unit,
    onToggleSortDescending: () -> Unit,
    onFilterValueChange: (String, FilterValue) -> Unit,
    onClearFilters: () -> Unit,
    onSeriesActivate: (Int) -> Unit,
    onSeriesLongClick: (Int) -> Unit,
    onSelectAll: () -> Unit,
    onExitSelection: () -> Unit,
    onApplyProfile: (Int) -> Unit,
    onConsumeMessage: () -> Unit,
    modifier: Modifier = Modifier,
    tabs: (@Composable () -> Unit)? = null,
) {
    val snackbar = remember { SnackbarHostState() }
    val message = state.message
    val messageText = message?.let { libraryMessageText(it) }
    LaunchedEffect(messageText) {
        messageText?.let {
            snackbar.showSnackbar(it)
            onConsumeMessage()
        }
    }
    var filterSheetOpen by remember { mutableStateOf(false) }
    var profilePickerOpen by remember { mutableStateOf(false) }

    ArrScaffold(
        title = if (state.selectMode && state.selection.isNotEmpty()) {
            stringResource(R.string.arr_bazarr_lib_selected_count, state.selection.size)
        } else {
            stringResource(R.string.arr_bazarr_series_title)
        },
        modifier = modifier,
        actions = {
            if (!state.selectMode) {
                ListActions(
                    state = state,
                    sortLabels = seriesSortLabels(),
                    onSortSelect = onSortSelect,
                    onToggleSortDescending = onToggleSortDescending,
                    onViewChange = onViewChange,
                )
            }
        },
        filterBar = {
            if (!state.selectMode) {
                // Labels resolve in the composable scope; the row's label
                // lambda is not composable.
                val chips = state.filterSpecs.map { it.id }
                val labels = chips.associateWith { id -> filterLabel(id) }
                Column {
                    tabs?.let { it() }
                    state.badges?.let { BadgeSummary(it) }
                    FilterChipRow(
                        filters = chips,
                        selected = { state.filterState.isActive(it) },
                        label = { labels.getValue(it) },
                        onSelect = { filterSheetOpen = true },
                    )
                }
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
                        emptyTitle = stringResource(R.string.arr_bazarr_series_empty_title),
                        emptyMessage = stringResource(R.string.arr_bazarr_series_empty_message),
                        modifier = Modifier.fillMaxSize(),
                    ) { content ->
                        if (content.view == LibraryView.POSTERS) {
                            SeriesPosterGrid(
                                state = content,
                                imageLoader = imageLoader,
                                onSeriesActivate = onSeriesActivate,
                                onSeriesLongClick = onSeriesLongClick,
                                onLoadMore = onLoadMore,
                            )
                        } else {
                            SeriesTable(
                                state = content,
                                onSeriesActivate = onSeriesActivate,
                                onSeriesLongClick = onSeriesLongClick,
                                onLoadMore = onLoadMore,
                            )
                        }
                    }
                }
                if (state.selectMode) {
                    SelectionBar(
                        onSelectAll = onSelectAll,
                        onAssignProfile = { profilePickerOpen = true },
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
        BazarrFilterSheet(
            state = state,
            onValueChange = onFilterValueChange,
            onClear = onClearFilters,
            onDismiss = { filterSheetOpen = false },
        )
    }
    if (profilePickerOpen) {
        ProfilePickerDialog(
            state = state,
            onPick = { profileId ->
                profilePickerOpen = false
                onApplyProfile(profileId)
            },
            onDismiss = { profilePickerOpen = false },
        )
    }
}

/** Sort labels of the series list, resolved at composition for SortMenu. */
@Composable
private fun seriesSortLabels(): Map<String, String> = mapOf(
    BazarrSeriesSorts.TITLE to stringResource(R.string.arr_bazarr_lib_sort_title),
    BazarrSeriesSorts.YEAR to stringResource(R.string.arr_bazarr_lib_sort_year),
    BazarrSeriesSorts.MISSING to stringResource(R.string.arr_bazarr_lib_sort_missing),
    BazarrSeriesSorts.EPISODES to stringResource(R.string.arr_bazarr_lib_sort_episodes),
)

/** Poster grid with adaptive columns, so tablets naturally fit more columns. */
@Composable
private fun SeriesPosterGrid(
    state: BazarrLibraryUiState,
    imageLoader: ImageLoader?,
    onSeriesActivate: (Int) -> Unit,
    onSeriesLongClick: (Int) -> Unit,
    onLoadMore: () -> Unit,
) {
    val gridState = rememberLazyGridState()
    GridLoadMoreEffect(gridState, enabled = !state.seriesPresentation.endReached, onLoadMore)
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 128.dp),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        state = gridState,
        modifier = Modifier.fillMaxSize(),
    ) {
        items(state.seriesPresentation.visible, key = { it.seriesId }) { series ->
            SeriesPosterCard(
                series = series,
                imageLoader = imageLoader,
                selectMode = state.selectMode,
                selected = series.seriesId in state.selection,
                queued = series.seriesId in state.queuedIds,
                onActivate = { onSeriesActivate(series.seriesId) },
                onLongClick = { onSeriesLongClick(series.seriesId) },
            )
        }
    }
}

/**
 * One poster tile. The long-press wrapper sits outside [PosterCard]'s own
 * tap handling on purpose: taps resolve on the card, long presses time out
 * into the wrapper's combined clickable and enter selection mode.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SeriesPosterCard(
    series: BazarrSeries,
    imageLoader: ImageLoader?,
    selectMode: Boolean,
    selected: Boolean,
    queued: Boolean,
    onActivate: () -> Unit,
    onLongClick: () -> Unit,
) {
    val openDescription = stringResource(R.string.arr_bazarr_series_cd_open_episodes, series.title)
    Box(
        modifier = Modifier
            .semantics { contentDescription = openDescription }
            .combinedClickable(onClick = {}, onLongClick = onLongClick),
    ) {
        PosterCard(
            title = series.title,
            posterUrl = series.poster,
            imageLoader = imageLoader,
            onClick = onActivate,
            subtitle = series.year?.takeIf { it.isNotBlank() },
            badges = {
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    if (queued) QueuedBadge()
                    StatusBadge(series.status)
                }
            },
        )
        if (selectMode) {
            SelectionCheck(selected = selected, modifier = Modifier.align(Alignment.TopStart))
        }
    }
}

/** Compact table view: one row per series with counts, tags, and profile. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SeriesTable(
    state: BazarrLibraryUiState,
    onSeriesActivate: (Int) -> Unit,
    onSeriesLongClick: (Int) -> Unit,
    onLoadMore: () -> Unit,
) {
    val listState = rememberLazyListState()
    ListLoadMoreEffect(listState, enabled = !state.seriesPresentation.endReached, onLoadMore)
    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(vertical = 8.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        items(state.seriesPresentation.visible, key = { it.seriesId }) { series ->
            SeriesRow(
                series = series,
                profileLabel = state.profileLabel(series.profileId),
                queued = series.seriesId in state.queuedIds,
                onActivate = { onSeriesActivate(series.seriesId) },
                onLongClick = { onSeriesLongClick(series.seriesId) },
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SeriesRow(
    series: BazarrSeries,
    profileLabel: String,
    queued: Boolean,
    onActivate: () -> Unit,
    onLongClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .combinedClickable(onClick = onActivate, onLongClick = onLongClick),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = series.title,
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    series.year?.takeIf { it.isNotBlank() }?.let { year ->
                        Spacer(Modifier.padding(start = 6.dp))
                        Text(
                            text = year,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Spacer(Modifier.padding(top = 2.dp))
                Text(
                    text = stringResource(
                        R.string.arr_bazarr_series_episode_progress,
                        series.completeEpisodeCount,
                        series.episodeFileCount,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (series.tags.isNotEmpty()) {
                    Spacer(Modifier.padding(top = 2.dp))
                    Text(
                        text = series.tags.joinToString(", "),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.padding(top = 2.dp))
                Text(
                    text = profileLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (queued) QueuedBadge(Modifier.padding(horizontal = 8.dp))
            StatusBadge(series.status)
        }
    }
}

/** Selection check overlay for poster tiles in selection mode. */
@Composable
internal fun SelectionCheck(selected: Boolean, modifier: Modifier = Modifier) {
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

/** Bottom bar of selection mode: select all, assign profile, leave. */
@Composable
internal fun SelectionBar(
    onSelectAll: () -> Unit,
    onAssignProfile: () -> Unit,
    onClear: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        IconButton(onClick = onSelectAll) {
            Icon(
                imageVector = Icons.Filled.SelectAll,
                contentDescription = stringResource(R.string.arr_bazarr_lib_cd_select_all),
            )
        }
        TextButton(onClick = onAssignProfile, modifier = Modifier.weight(1f)) {
            Text(stringResource(R.string.arr_bazarr_lib_action_apply_profile))
        }
        IconButton(onClick = onClear) {
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = stringResource(R.string.arr_bazarr_lib_cd_exit_selection),
            )
        }
    }
}

/** Loads the next page when the grid nears the end of the visible window. */
@Composable
internal fun GridLoadMoreEffect(
    gridState: LazyGridState,
    enabled: Boolean,
    onLoadMore: () -> Unit,
) {
    val end by remember {
        androidx.compose.runtime.derivedStateOf {
            val info = gridState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: -1
            enabled && info.totalItemsCount > 0 && last >= info.totalItemsCount - 12
        }
    }
    LaunchedEffect(end) {
        if (end) onLoadMore()
    }
}

/** List counterpart of [GridLoadMoreEffect]. */
@Composable
internal fun ListLoadMoreEffect(
    listState: androidx.compose.foundation.lazy.LazyListState,
    enabled: Boolean,
    onLoadMore: () -> Unit,
) {
    val end by remember {
        androidx.compose.runtime.derivedStateOf {
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: -1
            enabled && info.totalItemsCount > 0 && last >= info.totalItemsCount - 12
        }
    }
    LaunchedEffect(end) {
        if (end) onLoadMore()
    }
}
