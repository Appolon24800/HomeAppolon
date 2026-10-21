package dev.appolon.homelab.arr.feature.radarr.wanted.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.ManageSearch
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.ImageLoader
import coil3.compose.AsyncImage
import dev.appolon.homelab.arr.api.radarr.model.MovieResource
import dev.appolon.homelab.arr.core.Humanize
import dev.appolon.homelab.arr.feature.radarr.R
import dev.appolon.homelab.arr.feature.radarr.library.data.MoviePosterUrls
import dev.appolon.homelab.arr.feature.radarr.wanted.data.WantedCounts
import dev.appolon.homelab.arr.feature.radarr.wanted.data.WantedFilters
import dev.appolon.homelab.arr.feature.radarr.wanted.data.WantedTab
import dev.appolon.homelab.arr.feature.radarr.wanted.vm.WantedMessage
import dev.appolon.homelab.arr.feature.radarr.wanted.vm.WantedUiState
import dev.appolon.homelab.arr.ui.lce.ArrLceBox
import dev.appolon.homelab.arr.ui.lce.ArrPullToRefreshBox
import dev.appolon.homelab.arr.ui.lce.LceState
import dev.appolon.homelab.arr.ui.shell.ArrScaffold
import dev.appolon.homelab.arr.ui.R as ArrUiR

/**
 * The Wanted screen: missing and cutoff-unmet tabs over the same shell,
 * server-paged lists, monitored and quality filters, per-item and bulk
 * `MoviesSearch` dispatch with progress indication, and queued-write badges.
 * The screen owns no state; the ViewModel drives it and failures land in
 * the LCE box.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun RadarrWantedScreen(
    state: WantedUiState,
    lce: LceState<WantedUiState>,
    counts: WantedCounts,
    baseUrl: String,
    imageLoader: ImageLoader?,
    onRefresh: () -> Unit,
    onLoadMore: () -> Unit,
    onTabSelect: (WantedTab) -> Unit,
    onToggleMonitored: () -> Unit,
    onQualityToggle: (String) -> Unit,
    onClearFilters: () -> Unit,
    onMovieOpen: (Int) -> Unit,
    onSearchMovie: (Int) -> Unit,
    onSearchAll: () -> Unit,
    onConsumeMessage: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val snackbar = remember { SnackbarHostState() }
    val message = state.message
    val messageText = message?.let { wantedMessageText(it) }
    LaunchedEffect(messageText) {
        messageText?.let {
            snackbar.showSnackbar(it)
            onConsumeMessage()
        }
    }

    ArrScaffold(
        title = stringResource(ArrUiR.string.arr_common_wanted_title),
        modifier = modifier,
        actions = {
            if (state.queuedForServer > 0) {
                val queuedDescription =
                    stringResource(ArrUiR.string.arr_common_system_pending_banner, state.queuedForServer)
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
            IconButton(onClick = onSearchAll, enabled = state.presentation.all.isNotEmpty()) {
                Icon(
                    imageVector = Icons.Filled.ManageSearch,
                    contentDescription = stringResource(R.string.arr_radarr_wanted_cd_search_all),
                )
            }
        },
        filterBar = {
            Column {
                if (state.bulkSearching) {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                }
                WantedFilterBar(
                    state = state,
                    onToggleMonitored = onToggleMonitored,
                    onQualityToggle = onQualityToggle,
                    onClear = onClearFilters,
                )
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            WantedTabRow(state = state, counts = counts, onSelect = onTabSelect)
            ArrPullToRefreshBox(
                isRefreshing = state.refreshing,
                onRefresh = onRefresh,
                modifier = Modifier.fillMaxSize(),
            ) {
                Box(Modifier.fillMaxSize()) {
                    ArrLceBox(
                        state = lce,
                        onRetry = onRefresh,
                        emptyTitle = if (state.tab == WantedTab.MISSING) {
                            stringResource(ArrUiR.string.arr_common_wanted_empty_missing_title)
                        } else {
                            stringResource(ArrUiR.string.arr_common_wanted_empty_cutoff_title)
                        },
                        emptyMessage = if (state.tab == WantedTab.MISSING) {
                            stringResource(R.string.arr_radarr_wanted_empty_missing_message)
                        } else {
                            stringResource(R.string.arr_radarr_wanted_empty_cutoff_message)
                        },
                        modifier = Modifier.fillMaxSize(),
                    ) { content ->
                        WantedList(
                            state = content,
                            baseUrl = baseUrl,
                            imageLoader = imageLoader,
                            onMovieOpen = onMovieOpen,
                            onSearchMovie = onSearchMovie,
                            onLoadMore = onLoadMore,
                        )
                    }
                    SnackbarHost(
                        hostState = snackbar,
                        modifier = Modifier.align(Alignment.BottomCenter),
                    )
                }
            }
        }
    }
}

/** The two wanted tabs, each carrying its server-reported total as a badge. */
@Composable
private fun WantedTabRow(
    state: WantedUiState,
    counts: WantedCounts,
    onSelect: (WantedTab) -> Unit,
) {
    TabRow(selectedTabIndex = state.tab.ordinal) {
        WantedTab.entries.forEach { tab ->
            val label = when (tab) {
                WantedTab.MISSING -> stringResource(ArrUiR.string.arr_common_wanted_tab_missing)
                WantedTab.CUTOFF -> stringResource(ArrUiR.string.arr_common_wanted_tab_cutoff)
            }
            val total = when (tab) {
                WantedTab.MISSING -> counts.missing
                WantedTab.CUTOFF -> counts.cutoff
            }
            Tab(
                selected = state.tab == tab,
                onClick = { onSelect(tab) },
                text = {
                    if (total != null && total > 0) {
                        BadgedBox(badge = { Badge { Text(total.coerceAtMost(99).toString()) } }) {
                            Text(label)
                        }
                    } else {
                        Text(label)
                    }
                },
            )
        }
    }
}

/** Monitored toggle chip, quality dropdown, and clear chip. */
@Composable
private fun WantedFilterBar(
    state: WantedUiState,
    onToggleMonitored: () -> Unit,
    onQualityToggle: (String) -> Unit,
    onClear: () -> Unit,
) {
    var qualityMenuOpen by remember { mutableStateOf(false) }
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        FilterChip(
            selected = state.filterState.isActive(WantedFilters.MONITORED),
            onClick = onToggleMonitored,
            label = { Text(stringResource(ArrUiR.string.arr_common_monitored)) },
        )
        Box {
            FilterChip(
                selected = state.filterState.isActive(WantedFilters.QUALITY),
                onClick = { qualityMenuOpen = true },
                label = { Text(stringResource(ArrUiR.string.arr_common_wanted_filter_quality)) },
            )
            DropdownMenu(
                expanded = qualityMenuOpen,
                onDismissRequest = { qualityMenuOpen = false },
            ) {
                if (state.options.qualityProfileIds.isEmpty()) {
                    DropdownMenuItem(
                        text = {
                            Text(
                                stringResource(ArrUiR.string.arr_common_wanted_filter_empty_options),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        },
                        onClick = {},
                    )
                }
                state.options.qualityProfileIds.forEach { id ->
                    val selected = state.filterState
                        .value(WantedFilters.QUALITY)
                        ?.let { it as? dev.appolon.homelab.arr.core.FilterValue.Choice }
                        ?.selected?.contains(id.toString()) == true
                    DropdownMenuItem(
                        text = {
                            Text(
                                stringResource(
                                    R.string.arr_radarr_wanted_filter_profile_option,
                                    state.profileLabel(id),
                                ),
                            )
                        },
                        trailingIcon = if (selected) {
                            { Icon(Icons.Filled.Done, contentDescription = null) }
                        } else {
                            null
                        },
                        onClick = {
                            qualityMenuOpen = false
                            onQualityToggle(id.toString())
                        },
                    )
                }
            }
        }
        if (state.filterState.isActive(WantedFilters.MONITORED) ||
            state.filterState.isActive(WantedFilters.QUALITY)
        ) {
            AssistChip(
                onClick = onClear,
                label = { Text(stringResource(ArrUiR.string.arr_common_wanted_filter_clear)) },
            )
        }
    }
}

/**
 * The paged list. Phones render one column; tablets and landscape widen into
 * an adaptive grid of full-width row cards, so extra space buys columns
 * instead of stretched rows.
 */
@Composable
private fun WantedList(
    state: WantedUiState,
    baseUrl: String,
    imageLoader: ImageLoader?,
    onMovieOpen: (Int) -> Unit,
    onSearchMovie: (Int) -> Unit,
    onLoadMore: () -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth >= 600.dp
        val movies = state.presentation.all
        if (wide) {
            val gridState = rememberLazyGridState()
            GridLoadMoreEffect(gridState, enabled = state.canLoadMore, onLoadMore = onLoadMore)
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 520.dp),
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                state = gridState,
                modifier = Modifier.fillMaxSize(),
            ) {
                items(movies, key = { it.id }, contentType = { "movie" }) { movie ->
                    WantedMovieRow(
                        movie = movie,
                        state = state,
                        baseUrl = baseUrl,
                        imageLoader = imageLoader,
                        onOpen = onMovieOpen,
                        onSearch = onSearchMovie,
                    )
                }
                if (state.active.loadingMore) {
                    item(span = { GridItemSpan(maxCurrentLineSpan) }) {
                        LoadingMoreRow()
                    }
                }
            }
        } else {
            val listState = rememberLazyListState()
            ListLoadMoreEffect(listState, enabled = state.canLoadMore, onLoadMore = onLoadMore)
            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(movies, key = { it.id }, contentType = { "movie" }) { movie ->
                    WantedMovieRow(
                        movie = movie,
                        state = state,
                        baseUrl = baseUrl,
                        imageLoader = imageLoader,
                        onOpen = onMovieOpen,
                        onSearch = onSearchMovie,
                    )
                }
                if (state.active.loadingMore) {
                    item { LoadingMoreRow() }
                }
            }
        }
        // Short filtered results may never scroll; keep fetching pages until
        // the list fills the viewport or the server total is reached.
        LaunchedEffect(state.canLoadMore, movies.size, state.active.loadMoreFailed) {
            if (state.canLoadMore && !state.active.loadMoreFailed && movies.size < 8) {
                onLoadMore()
            }
        }
    }
}

/** One wanted movie: thumb, title and year, profile and size, search action. */
@Composable
private fun WantedMovieRow(
    movie: MovieResource,
    state: WantedUiState,
    baseUrl: String,
    imageLoader: ImageLoader?,
    onOpen: (Int) -> Unit,
    onSearch: (Int) -> Unit,
) {
    val title = movie.title.orEmpty().ifEmpty { "#" + movie.id }
    val openDescription =
        stringResource(ArrUiR.string.arr_common_mm_browser_cd_open, title)
    val searchDescription =
        stringResource(ArrUiR.string.arr_common_wanted_cd_search_movie, title)
    val searching = movie.id in state.searchingIds
    val queued = movie.id in state.queuedIds
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier
            .fillMaxWidth()
            .minimumInteractiveComponentSize()
            .clickable(onClick = { onOpen(movie.id) })
            .semantics { contentDescription = openDescription },
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            PosterThumb(
                posterUrl = MoviePosterUrls.posterUrlOf(movie, baseUrl),
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
            if (queued) {
                Text(
                    text = stringResource(ArrUiR.string.arr_common_connect_badge_queued),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.padding(end = 8.dp),
                )
            }
            Icon(
                imageVector = Icons.Filled.Done,
                contentDescription = if (movie.monitored) {
                    stringResource(ArrUiR.string.arr_common_monitored)
                } else {
                    stringResource(ArrUiR.string.arr_common_wanted_cd_unmonitored)
                },
                tint = if (movie.monitored) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.outlineVariant
                },
                modifier = Modifier.padding(end = 4.dp),
            )
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(48.dp),
            ) {
                if (searching) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp,
                    )
                } else {
                    IconButton(onClick = { onSearch(movie.id) }) {
                        Icon(
                            imageVector = Icons.Filled.Search,
                            contentDescription = searchDescription,
                        )
                    }
                }
            }
        }
    }
}

/** Fixed size 2:3 thumbnail for list rows. */
@Composable
private fun PosterThumb(
    posterUrl: String?,
    imageLoader: ImageLoader?,
) {
    val shape = RoundedCornerShape(6.dp)
    Box(
        modifier = Modifier
            .width(44.dp)
            .aspectRatio(2f / 3f)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant),
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

/** Footer shown while the next server page streams in. */
@Composable
private fun LoadingMoreRow() {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
    ) {
        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
        Text(
            text = stringResource(ArrUiR.string.arr_common_loading_more),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 12.dp),
        )
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
            enabled && total > 0 && last >= total - 6
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
            enabled && total > 0 && last >= total - 6
        }
    }
    LaunchedEffect(shouldLoad) {
        if (shouldLoad) onLoadMore()
    }
}

@Composable
private fun wantedMessageText(message: WantedMessage): String = when (message) {
    is WantedMessage.SearchStarted -> if (message.count == 1) {
        stringResource(ArrUiR.string.arr_common_detail_msg_search_started)
    } else {
        stringResource(R.string.arr_radarr_wanted_msg_search_started_many, message.count)
    }

    is WantedMessage.SearchQueued -> if (message.count == 1) {
        stringResource(ArrUiR.string.arr_common_wanted_msg_search_queued_one)
    } else {
        stringResource(R.string.arr_radarr_wanted_msg_search_queued_many, message.count)
    }

    WantedMessage.SearchFailed -> stringResource(ArrUiR.string.arr_common_wanted_msg_search_failed)
}
