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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.ImageLoader
import dev.appolon.homelab.arr.core.FilterValue
import dev.appolon.homelab.arr.feature.bazarr.R
import dev.appolon.homelab.arr.feature.bazarr.series.data.BazarrMovie
import dev.appolon.homelab.arr.feature.bazarr.series.data.BazarrMovieSorts
import dev.appolon.homelab.arr.feature.bazarr.series.vm.BazarrLibraryUiState
import dev.appolon.homelab.arr.feature.bazarr.series.vm.LibraryView
import dev.appolon.homelab.arr.ui.components.FilterChipRow
import dev.appolon.homelab.arr.ui.components.PosterCard
import dev.appolon.homelab.arr.ui.lce.ArrLceBox
import dev.appolon.homelab.arr.ui.lce.ArrPullToRefreshBox
import dev.appolon.homelab.arr.ui.lce.LceState
import dev.appolon.homelab.arr.ui.shell.ArrScaffold

/**
 * The Bazarr movies list: poster grid or table over the presented list,
 * status badges computed from the subtitle and missing-language lists, movie
 * rows with subtitle counts, filters with the sheet, bulk selection with the
 * language-profile mass editor, and queued indicators. The screen owns no
 * state; the ViewModel drives it and every failure lands in the LCE box.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun BazarrMoviesListScreen(
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
    onMovieActivate: (Int) -> Unit,
    onMovieLongClick: (Int) -> Unit,
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
            stringResource(R.string.arr_bazarr_movies_title)
        },
        modifier = modifier,
        actions = {
            if (!state.selectMode) {
                ListActions(
                    state = state,
                    sortLabels = movieSortLabels(),
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
                        emptyTitle = stringResource(R.string.arr_bazarr_movies_empty_title),
                        emptyMessage = stringResource(R.string.arr_bazarr_movies_empty_message),
                        modifier = Modifier.fillMaxSize(),
                    ) { content ->
                        if (content.view == LibraryView.POSTERS) {
                            MoviePosterGrid(
                                state = content,
                                imageLoader = imageLoader,
                                onMovieActivate = onMovieActivate,
                                onMovieLongClick = onMovieLongClick,
                                onLoadMore = onLoadMore,
                            )
                        } else {
                            MovieTable(
                                state = content,
                                onMovieActivate = onMovieActivate,
                                onMovieLongClick = onMovieLongClick,
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

/** Sort labels of the movies list, resolved at composition for SortMenu. */
@Composable
private fun movieSortLabels(): Map<String, String> = mapOf(
    BazarrMovieSorts.TITLE to stringResource(R.string.arr_bazarr_lib_sort_title),
    BazarrMovieSorts.YEAR to stringResource(R.string.arr_bazarr_lib_sort_year),
    BazarrMovieSorts.MISSING to stringResource(R.string.arr_bazarr_lib_sort_missing),
    BazarrMovieSorts.SUBTITLES to stringResource(R.string.arr_bazarr_lib_sort_subtitles),
)

/** Poster grid with adaptive columns, so tablets naturally fit more columns. */
@Composable
private fun MoviePosterGrid(
    state: BazarrLibraryUiState,
    imageLoader: ImageLoader?,
    onMovieActivate: (Int) -> Unit,
    onMovieLongClick: (Int) -> Unit,
    onLoadMore: () -> Unit,
) {
    val gridState = rememberLazyGridState()
    GridLoadMoreEffect(gridState, enabled = !state.moviePresentation.endReached, onLoadMore)
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 128.dp),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        state = gridState,
        modifier = Modifier.fillMaxSize(),
    ) {
        items(state.moviePresentation.visible, key = { it.radarrId }) { movie ->
            MoviePosterCard(
                movie = movie,
                imageLoader = imageLoader,
                selectMode = state.selectMode,
                selected = movie.radarrId in state.selection,
                queued = movie.radarrId in state.queuedIds,
                onActivate = { onMovieActivate(movie.radarrId) },
                onLongClick = { onMovieLongClick(movie.radarrId) },
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
private fun MoviePosterCard(
    movie: BazarrMovie,
    imageLoader: ImageLoader?,
    selectMode: Boolean,
    selected: Boolean,
    queued: Boolean,
    onActivate: () -> Unit,
    onLongClick: () -> Unit,
) {
    val openDescription = stringResource(R.string.arr_bazarr_movies_cd_open, movie.title)
    // Selection mode changes the tile's meaning from open to select.
    val selectDescription = stringResource(R.string.arr_bazarr_lib_cd_select, movie.title)
    Box(
        modifier = Modifier
            .semantics {
                contentDescription = if (selectMode) selectDescription else openDescription
            }
            .combinedClickable(onClick = {}, onLongClick = onLongClick),
    ) {
        PosterCard(
            title = movie.title,
            posterUrl = movie.poster,
            imageLoader = imageLoader,
            onClick = onActivate,
            subtitle = movie.year?.takeIf { it.isNotBlank() },
            badges = {
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    if (queued) QueuedBadge()
                    StatusBadge(movie.status)
                }
            },
        )
        if (selectMode) {
            SelectionCheck(selected = selected, modifier = Modifier.align(Alignment.TopStart))
        }
    }
}

/** Table view: one row per movie with subtitle count and missing languages. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MovieTable(
    state: BazarrLibraryUiState,
    onMovieActivate: (Int) -> Unit,
    onMovieLongClick: (Int) -> Unit,
    onLoadMore: () -> Unit,
) {
    val listState = rememberLazyListState()
    ListLoadMoreEffect(listState, enabled = !state.moviePresentation.endReached, onLoadMore)
    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(vertical = 8.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        items(state.moviePresentation.visible, key = { it.radarrId }) { movie ->
            MovieRow(
                movie = movie,
                queued = movie.radarrId in state.queuedIds,
                onActivate = { onMovieActivate(movie.radarrId) },
                onLongClick = { onMovieLongClick(movie.radarrId) },
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MovieRow(
    movie: BazarrMovie,
    queued: Boolean,
    onActivate: () -> Unit,
    onLongClick: () -> Unit,
) {
    val missing = movie.missingSubtitles.map { it.label }
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
                        text = movie.title,
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    movie.year?.takeIf { it.isNotBlank() }?.let { year ->
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
                    text = pluralStringResource(
                        R.plurals.arr_bazarr_movies_subtitle_count,
                        movie.subtitles.size,
                        movie.subtitles.size,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (missing.isNotEmpty()) {
                    Spacer(Modifier.padding(top = 2.dp))
                    Text(
                        text = stringResource(
                            R.string.arr_bazarr_movies_missing_languages,
                            missing.joinToString(", "),
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (movie.tags.isNotEmpty()) {
                    Spacer(Modifier.padding(top = 2.dp))
                    Text(
                        text = movie.tags.joinToString(", "),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (queued) QueuedBadge(Modifier.padding(horizontal = 8.dp))
            StatusBadge(movie.status)
        }
    }
}
