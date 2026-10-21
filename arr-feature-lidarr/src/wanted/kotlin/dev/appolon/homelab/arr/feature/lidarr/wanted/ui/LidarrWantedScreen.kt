package dev.appolon.homelab.arr.feature.lidarr.wanted.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.material.icons.filled.Album
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
import dev.appolon.homelab.arr.api.lidarr.model.AlbumResource
import dev.appolon.homelab.arr.core.ArrTime
import dev.appolon.homelab.arr.feature.lidarr.R
import dev.appolon.homelab.arr.feature.lidarr.wanted.data.WantedCounts
import dev.appolon.homelab.arr.feature.lidarr.wanted.data.WantedCoverUrls
import dev.appolon.homelab.arr.feature.lidarr.wanted.data.WantedFilters
import dev.appolon.homelab.arr.feature.lidarr.wanted.data.WantedTab
import dev.appolon.homelab.arr.feature.lidarr.wanted.data.albumTitleOf
import dev.appolon.homelab.arr.feature.lidarr.wanted.data.artistNameOf
import dev.appolon.homelab.arr.feature.lidarr.wanted.data.releaseTypeOf
import dev.appolon.homelab.arr.feature.lidarr.wanted.vm.WantedMessage
import dev.appolon.homelab.arr.feature.lidarr.wanted.vm.WantedUiState
import dev.appolon.homelab.arr.ui.lce.ArrLceBox
import dev.appolon.homelab.arr.ui.lce.ArrPullToRefreshBox
import dev.appolon.homelab.arr.ui.lce.LceState
import dev.appolon.homelab.arr.ui.shell.ArrScaffold
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * The Lidarr Wanted screen: missing and cutoff-unmet tabs over the same
 * shell, server-paged album lists with artist, release type, and release
 * date, monitored and release type filters, per-album search plus bulk search
 * dispatch with progress indication, and queued-write badges. The screen owns
 * no state; the ViewModel drives it and failures land in the LCE box.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun LidarrWantedScreen(
    state: WantedUiState,
    lce: LceState<WantedUiState>,
    counts: WantedCounts,
    baseUrl: String,
    imageLoader: ImageLoader?,
    onRefresh: () -> Unit,
    onLoadMore: () -> Unit,
    onTabSelect: (WantedTab) -> Unit,
    onToggleMonitored: () -> Unit,
    onReleaseTypeToggle: (String) -> Unit,
    onClearFilters: () -> Unit,
    onArtistOpen: (Int) -> Unit,
    onSearchAlbum: (Int) -> Unit,
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
        title = stringResource(R.string.arr_lidarr_wanted_title),
        modifier = modifier,
        actions = {
            if (state.queuedForServer > 0) {
                val queuedDescription =
                    stringResource(R.string.arr_lidarr_wanted_cd_queued, state.queuedForServer)
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
                    contentDescription = stringResource(R.string.arr_lidarr_wanted_cd_search_all),
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
                    onReleaseTypeToggle = onReleaseTypeToggle,
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
                            stringResource(R.string.arr_lidarr_wanted_empty_missing_title)
                        } else {
                            stringResource(R.string.arr_lidarr_wanted_empty_cutoff_title)
                        },
                        emptyMessage = if (state.tab == WantedTab.MISSING) {
                            stringResource(R.string.arr_lidarr_wanted_empty_missing_message)
                        } else {
                            stringResource(R.string.arr_lidarr_wanted_empty_cutoff_message)
                        },
                        modifier = Modifier.fillMaxSize(),
                    ) { content ->
                        WantedList(
                            state = content,
                            baseUrl = baseUrl,
                            imageLoader = imageLoader,
                            onArtistOpen = onArtistOpen,
                            onSearchAlbum = onSearchAlbum,
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
                WantedTab.MISSING -> stringResource(R.string.arr_lidarr_wanted_tab_missing)
                WantedTab.CUTOFF -> stringResource(R.string.arr_lidarr_wanted_tab_cutoff)
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

/** Monitored toggle chip, release type dropdown, and clear chip. */
@Composable
private fun WantedFilterBar(
    state: WantedUiState,
    onToggleMonitored: () -> Unit,
    onReleaseTypeToggle: (String) -> Unit,
    onClear: () -> Unit,
) {
    var releaseTypeMenuOpen by remember { mutableStateOf(false) }
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        FilterChip(
            selected = state.filterState.isActive(WantedFilters.MONITORED),
            onClick = onToggleMonitored,
            label = { Text(stringResource(R.string.arr_lidarr_wanted_filter_monitored)) },
        )
        Box {
            FilterChip(
                selected = state.filterState.isActive(WantedFilters.RELEASE_TYPE),
                onClick = { releaseTypeMenuOpen = true },
                label = { Text(stringResource(R.string.arr_lidarr_wanted_filter_release_type)) },
            )
            DropdownMenu(
                expanded = releaseTypeMenuOpen,
                onDismissRequest = { releaseTypeMenuOpen = false },
            ) {
                if (state.options.releaseTypes.isEmpty()) {
                    DropdownMenuItem(
                        text = {
                            Text(
                                stringResource(R.string.arr_lidarr_wanted_filter_empty_options),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        },
                        onClick = {},
                    )
                }
                state.options.releaseTypes.forEach { type ->
                    val selected = state.filterState
                        .value(WantedFilters.RELEASE_TYPE)
                        ?.let { it as? dev.appolon.homelab.arr.core.FilterValue.Choice }
                        ?.selected?.contains(type) == true
                    DropdownMenuItem(
                        text = { Text(releaseTypeLabel(type)) },
                        trailingIcon = if (selected) {
                            { Icon(Icons.Filled.Done, contentDescription = null) }
                        } else {
                            null
                        },
                        onClick = {
                            releaseTypeMenuOpen = false
                            onReleaseTypeToggle(type)
                        },
                    )
                }
            }
        }
        if (state.filterState.isActive(WantedFilters.MONITORED) ||
            state.filterState.isActive(WantedFilters.RELEASE_TYPE)
        ) {
            AssistChip(
                onClick = onClear,
                label = { Text(stringResource(R.string.arr_lidarr_wanted_filter_clear)) },
            )
        }
    }
}

/**
 * The paged album list. Phones render one column; tablets and landscape
 * widen into an adaptive grid of full-width row cards, so extra space buys
 * columns instead of stretched rows.
 */
@Composable
private fun WantedList(
    state: WantedUiState,
    baseUrl: String,
    imageLoader: ImageLoader?,
    onArtistOpen: (Int) -> Unit,
    onSearchAlbum: (Int) -> Unit,
    onLoadMore: () -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth >= 600.dp
        val albums = state.presentation.all
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
                items(albums, key = { it.id }) { album ->
                    WantedAlbumRow(
                        album = album,
                        state = state,
                        baseUrl = baseUrl,
                        imageLoader = imageLoader,
                        onOpen = onArtistOpen,
                        onSearchAlbum = onSearchAlbum,
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
                items(albums, key = { it.id }) { album ->
                    WantedAlbumRow(
                        album = album,
                        state = state,
                        baseUrl = baseUrl,
                        imageLoader = imageLoader,
                        onOpen = onArtistOpen,
                        onSearchAlbum = onSearchAlbum,
                    )
                }
                if (state.active.loadingMore) {
                    item { LoadingMoreRow() }
                }
            }
        }
        // Short filtered results may never scroll; keep fetching pages until
        // the list fills the viewport or the server total is reached.
        LaunchedEffect(state.canLoadMore, albums.size, state.active.loadMoreFailed) {
            if (state.canLoadMore && !state.active.loadMoreFailed && albums.size < 8) {
                onLoadMore()
            }
        }
    }
}

/**
 * One wanted album: cover thumb, artist name, album title and release type,
 * release date, and the per-album search action.
 */
@Composable
private fun WantedAlbumRow(
    album: AlbumResource,
    state: WantedUiState,
    baseUrl: String,
    imageLoader: ImageLoader?,
    onOpen: (Int) -> Unit,
    onSearchAlbum: (Int) -> Unit,
) {
    val artistName = artistNameOf(album)
    val albumTitle = albumTitleOf(album)
    val openDescription =
        stringResource(R.string.arr_lidarr_wanted_cd_open_artist, artistName)
    val searchAlbumDescription =
        stringResource(R.string.arr_lidarr_wanted_cd_search_album, artistName, albumTitle)
    val searching = album.id in state.searchingAlbumIds
    val queued = album.id in state.queuedAlbumIds
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier
            .fillMaxWidth()
            .minimumInteractiveComponentSize()
            .clickable(onClick = { onOpen(album.artistId) })
            .semantics { contentDescription = openDescription },
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            CoverThumb(
                coverUrl = WantedCoverUrls.coverUrlOf(album, baseUrl),
                imageLoader = imageLoader,
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp),
            ) {
                Text(
                    text = artistName,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val details = listOfNotNull(
                    albumTitle,
                    releaseTypeOf(album)?.let { releaseTypeLabel(it) },
                ).joinToString(" \u00b7 ")
                Text(
                    text = details,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = releaseDateText(album),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (queued) {
                Text(
                    text = stringResource(R.string.arr_lidarr_wanted_badge_queued),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.padding(end = 8.dp),
                )
            }
            Icon(
                imageVector = Icons.Filled.Done,
                contentDescription = if (album.monitored) {
                    stringResource(R.string.arr_lidarr_wanted_cd_monitored)
                } else {
                    stringResource(R.string.arr_lidarr_wanted_cd_unmonitored)
                },
                tint = if (album.monitored) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.outlineVariant
                },
                modifier = Modifier.padding(end = 4.dp),
            )
            RowSearchAction(
                searching = searching,
                description = searchAlbumDescription,
                onClick = { onSearchAlbum(album.id) },
            )
        }
    }
}

/** One 48dp search target, swapping to a spinner while its dispatch runs. */
@Composable
private fun RowSearchAction(
    searching: Boolean,
    description: String,
    onClick: () -> Unit,
) {
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
            IconButton(onClick = onClick) {
                Icon(
                    imageVector = Icons.Filled.Search,
                    contentDescription = description,
                )
            }
        }
    }
}

/** Fixed size square thumbnail for album rows. */
@Composable
private fun CoverThumb(
    coverUrl: String?,
    imageLoader: ImageLoader?,
) {
    val shape = RoundedCornerShape(6.dp)
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        if (coverUrl != null) {
            if (imageLoader != null) {
                AsyncImage(
                    model = coverUrl,
                    contentDescription = null,
                    imageLoader = imageLoader,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                AsyncImage(
                    model = coverUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        } else {
            Icon(
                imageVector = Icons.Filled.Album,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.outlineVariant,
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
            text = stringResource(R.string.arr_lidarr_wanted_loading_more),
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

/** "Oct 20, 2026" for the release date; falls back to a placeholder when absent. */
@Composable
private fun releaseDateText(album: AlbumResource): String {
    val instant: Instant? = ArrTime.parse(album.releaseDate)
    return if (instant == null) {
        stringResource(R.string.arr_lidarr_wanted_release_date_unknown)
    } else {
        remember(instant) {
            DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.getDefault())
                .format(instant.atZone(ZoneId.systemDefault()))
        }
    }
}

/** Display label of one release type option id. */
@Composable
private fun releaseTypeLabel(type: String): String = when (type) {
    "album" -> stringResource(R.string.arr_lidarr_wanted_release_type_album)
    "single" -> stringResource(R.string.arr_lidarr_wanted_release_type_single)
    "ep" -> stringResource(R.string.arr_lidarr_wanted_release_type_ep)
    else -> type.replaceFirstChar { it.uppercase(Locale.ROOT) }
}

@Composable
private fun wantedMessageText(message: WantedMessage): String = when (message) {
    is WantedMessage.AlbumSearchStarted -> if (message.count == 1) {
        stringResource(R.string.arr_lidarr_wanted_msg_search_started_one)
    } else {
        stringResource(R.string.arr_lidarr_wanted_msg_search_started_many, message.count)
    }

    is WantedMessage.SearchQueued -> if (message.count == 1) {
        stringResource(R.string.arr_lidarr_wanted_msg_search_queued_one)
    } else {
        stringResource(R.string.arr_lidarr_wanted_msg_search_queued_many, message.count)
    }

    WantedMessage.SearchFailed -> stringResource(R.string.arr_lidarr_wanted_msg_search_failed)
}
