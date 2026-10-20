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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.ImageLoader
import dev.appolon.homelab.arr.api.radarr.model.CollectionResource
import dev.appolon.homelab.arr.api.radarr.model.ImportListMoviesResource
import dev.appolon.homelab.arr.api.radarr.model.MovieResource
import dev.appolon.homelab.arr.feature.radarr.R
import dev.appolon.homelab.arr.feature.radarr.addnew.data.AddNewLogic
import dev.appolon.homelab.arr.feature.radarr.addnew.vm.AddNewMessage
import dev.appolon.homelab.arr.feature.radarr.addnew.vm.AddNewTab
import dev.appolon.homelab.arr.feature.radarr.addnew.vm.AddNewUiState
import dev.appolon.homelab.arr.feature.radarr.addnew.vm.AddSheetUiState
import dev.appolon.homelab.arr.feature.radarr.addnew.vm.CollectionEditUiState
import dev.appolon.homelab.arr.feature.radarr.addnew.vm.CollectionsMessage
import dev.appolon.homelab.arr.feature.radarr.addnew.vm.CollectionsUiState
import dev.appolon.homelab.arr.ui.components.SectionHeader
import dev.appolon.homelab.arr.ui.lce.ArrLceBox
import dev.appolon.homelab.arr.ui.lce.ArrPullToRefreshBox
import dev.appolon.homelab.arr.ui.lce.LceState
import dev.appolon.homelab.arr.ui.shell.ArrScaffold

/**
 * The Add new screen: a search field with year disambiguation, Discover and
 * Collections tabs, in-library and queued badges on every result, the add
 * sheet, and collection monitoring, quality, and search actions. The screen
 * owns no state; both ViewModels drive it and failures land in LCE boxes.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun RadarrAddNewScreen(
    state: AddNewUiState,
    collectionsState: CollectionsUiState,
    baseUrl: String,
    imageLoader: ImageLoader?,
    onQueryChange: (String) -> Unit,
    onSearchSubmit: () -> Unit,
    onRefresh: () -> Unit,
    onTabSelect: (AddNewTab) -> Unit,
    onOpenAddSheet: (MovieResource) -> Unit,
    onAddSheetChange: ((AddSheetUiState) -> AddSheetUiState) -> Unit,
    onAddSheetDismiss: () -> Unit,
    onAddSubmit: (Boolean) -> Unit,
    onConsumeMessage: () -> Unit,
    onCollectionsRefresh: () -> Unit,
    onToggleCollectionMonitored: (CollectionResource) -> Unit,
    onOpenCollectionEditor: (CollectionResource) -> Unit,
    onCollectionEditorChange: ((CollectionEditUiState) -> CollectionEditUiState) -> Unit,
    onCollectionEditorDismiss: () -> Unit,
    onCollectionEditorSubmit: () -> Unit,
    onSearchCollection: (CollectionResource) -> Unit,
    onConsumeCollectionsMessage: () -> Unit,
    modifier: Modifier = Modifier,
    serverName: String? = null,
) {
    val snackbar = remember { SnackbarHostState() }
    val messageText = state.message?.let { addNewMessageText(it) }
    LaunchedEffect(messageText) {
        messageText?.let {
            snackbar.showSnackbar(it)
            onConsumeMessage()
        }
    }
    val collectionsMessageText = collectionsState.message?.let { collectionsMessageText(it) }
    LaunchedEffect(collectionsMessageText) {
        collectionsMessageText?.let {
            snackbar.showSnackbar(it)
            onConsumeCollectionsMessage()
        }
    }

    val refreshing = if (state.tab == AddNewTab.COLLECTIONS) {
        collectionsState.refreshing
    } else {
        state.discoveryRefreshing
    }

    ArrScaffold(
        title = stringResource(R.string.arr_radarr_addnew_title),
        subtitle = serverName,
        modifier = modifier,
        actions = {
            val refreshLabel = if (state.tab == AddNewTab.COLLECTIONS) {
                stringResource(R.string.arr_radarr_addnew_cd_refresh_collections)
            } else {
                stringResource(R.string.arr_radarr_addnew_cd_refresh_discover)
            }
            IconButton(
                onClick = when (state.tab) {
                    AddNewTab.COLLECTIONS -> onCollectionsRefresh
                    AddNewTab.DISCOVER -> onRefresh
                },
            ) {
                Icon(Icons.Filled.Refresh, contentDescription = refreshLabel)
            }
        },
        filterBar = {
            SearchField(
                query = state.query,
                onQueryChange = onQueryChange,
                onSearchSubmit = onSearchSubmit,
            )
        },
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            ArrPullToRefreshBox(
                isRefreshing = refreshing,
                onRefresh = when (state.tab) {
                    AddNewTab.COLLECTIONS -> onCollectionsRefresh
                    AddNewTab.DISCOVER -> onRefresh
                },
                modifier = Modifier.fillMaxSize(),
            ) {
                Column(Modifier.fillMaxSize()) {
                    if (state.pendingCount > 0) {
                        PendingBanner(count = state.pendingCount)
                    }
                    if (state.searchActive) {
                        SearchResultsArea(
                            state = state,
                            imageLoader = imageLoader,
                            onRetry = onSearchSubmit,
                            onOpenAddSheet = onOpenAddSheet,
                        )
                    } else {
                        TabRow(selectedTabIndex = state.tab.ordinal) {
                            AddNewTab.entries.forEach { tab ->
                                Tab(
                                    selected = state.tab == tab,
                                    onClick = { onTabSelect(tab) },
                                    text = {
                                        Text(
                                            when (tab) {
                                                AddNewTab.DISCOVER -> stringResource(
                                                    R.string.arr_radarr_addnew_tab_discover,
                                                )

                                                AddNewTab.COLLECTIONS -> stringResource(
                                                    R.string.arr_radarr_addnew_tab_collections,
                                                )
                                            },
                                        )
                                    },
                                )
                            }
                        }
                        Box(Modifier.weight(1f)) {
                            when (state.tab) {
                                AddNewTab.DISCOVER -> DiscoverArea(
                                    state = state,
                                    imageLoader = imageLoader,
                                    onRefresh = onRefresh,
                                    onOpenAddSheet = onOpenAddSheet,
                                )

                                AddNewTab.COLLECTIONS -> CollectionsArea(
                                    state = collectionsState,
                                    onToggleMonitored = onToggleCollectionMonitored,
                                    onOpenEditor = onOpenCollectionEditor,
                                    onEditorChange = onCollectionEditorChange,
                                    onEditorDismiss = onCollectionEditorDismiss,
                                    onEditorSubmit = onCollectionEditorSubmit,
                                    onSearchCollection = onSearchCollection,
                                    onRetry = onCollectionsRefresh,
                                )
                            }
                        }
                    }
                }
            }
            SnackbarHost(
                hostState = snackbar,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }

    state.addSheet?.let { sheet ->
        AddMovieSheet(
            sheet = sheet,
            rootFolders = state.rootFolders,
            qualityProfiles = state.qualityProfiles,
            tags = state.tags,
            onChange = onAddSheetChange,
            onDismiss = onAddSheetDismiss,
            onAdd = { onAddSubmit(false) },
            onAddAndSearch = { onAddSubmit(true) },
        )
    }
}

@Composable
private fun SearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    onSearchSubmit: () -> Unit,
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        singleLine = true,
        placeholder = { Text(stringResource(R.string.arr_radarr_addnew_search_hint)) },
        leadingIcon = {
            Icon(Icons.Filled.Search, contentDescription = null)
        },
        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
            imeAction = androidx.compose.ui.text.input.ImeAction.Search,
        ),
        keyboardActions = androidx.compose.foundation.text.KeyboardActions(
            onSearch = { onSearchSubmit() },
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .testTag("addnew_search"),
    )
}

@Composable
private fun PendingBanner(count: Int) {
    AssistChip(
        onClick = {},
        enabled = false,
        label = { Text(stringResource(R.string.arr_radarr_addnew_pending_banner, count)) },
        leadingIcon = {
            Icon(
                Icons.Filled.CloudUpload,
                contentDescription = stringResource(R.string.arr_radarr_addnew_cd_queued_indicator),
                modifier = Modifier.size(18.dp),
            )
        },
        modifier = Modifier
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .fillMaxWidth(),
    )
}

@Composable
private fun SearchResultsArea(
    state: AddNewUiState,
    imageLoader: ImageLoader?,
    onRetry: () -> Unit,
    onOpenAddSheet: (MovieResource) -> Unit,
) {
    val lce = state.searchLce
    if (lce == null) {
        Box(Modifier.fillMaxSize())
        return
    }
    ArrLceBox(
        state = lce,
        onRetry = onRetry,
        emptyTitle = stringResource(R.string.arr_radarr_addnew_search_empty_title),
        emptyMessage = stringResource(R.string.arr_radarr_addnew_search_empty_message, state.query),
        modifier = Modifier.fillMaxSize(),
    ) { results ->
        LazyColumn(
            contentPadding = PaddingValues(vertical = 8.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            items(results, key = { it.tmdbId }) { movie ->
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                    SearchResultRow(
                        movie = movie,
                        existing = movie.tmdbId in state.existingTmdbIds,
                        queued = movie.tmdbId in state.queuedTmdbIds,
                        imageLoader = imageLoader,
                        onOpenAddSheet = onOpenAddSheet,
                        modifier = Modifier.widthIn(max = 720.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchResultRow(
    movie: MovieResource,
    existing: Boolean,
    queued: Boolean,
    imageLoader: ImageLoader?,
    onOpenAddSheet: (MovieResource) -> Unit,
    modifier: Modifier = Modifier,
) {
    val title = movie.title.orEmpty().ifEmpty { movie.tmdbId.toString() }
    val posterUrl = remember(movie.tmdbId) { AddNewLogic.lookupPosterUrl(movie) }
    val addDescription = stringResource(R.string.arr_radarr_addnew_cd_result_add, title)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        PosterImage(
            url = posterUrl,
            contentDescription = stringResource(R.string.arr_radarr_addnew_cd_poster, title),
            imageLoader = imageLoader,
            width = 64.dp,
            height = 96.dp,
        )
        Spacer(Modifier.width(16.dp))
        Column(
            Modifier
                .weight(1f)
                .semantics(mergeDescendants = true) {},
        ) {
            Text(
                text = stringResource(R.string.arr_radarr_addnew_title_year, title, movie.year),
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            movie.studio?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(2.dp))
            Text(
                text = movie.overview.orEmpty(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(8.dp))
        when {
            existing -> AssistChip(
                onClick = {},
                enabled = false,
                label = { Text(stringResource(R.string.arr_radarr_addnew_existing_chip)) },
            )

            queued -> AssistChip(
                onClick = {},
                enabled = false,
                label = { Text(stringResource(R.string.arr_radarr_addnew_queued_chip)) },
                leadingIcon = {
                    Icon(
                        Icons.Filled.CloudUpload,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                },
            )

            else -> IconButton(
                onClick = { onOpenAddSheet(movie) },
                modifier = Modifier.semantics { contentDescription = addDescription },
            ) {
                Icon(Icons.Filled.Add, contentDescription = null)
            }
        }
    }
}

@Composable
private fun DiscoverArea(
    state: AddNewUiState,
    imageLoader: ImageLoader?,
    onRefresh: () -> Unit,
    onOpenAddSheet: (MovieResource) -> Unit,
) {
    ArrLceBox(
        state = state.discoveryLce,
        onRetry = onRefresh,
        emptyTitle = stringResource(R.string.arr_radarr_addnew_discover_empty_title),
        emptyMessage = stringResource(R.string.arr_radarr_addnew_discover_empty_message),
        modifier = Modifier.fillMaxSize(),
    ) { feed ->
        val sections = remember(feed) { AddNewLogic.discoverySections(feed) }
        val recommendedTitle = stringResource(R.string.arr_radarr_addnew_discover_recommended)
        val trendingTitle = stringResource(R.string.arr_radarr_addnew_discover_trending)
        val popularTitle = stringResource(R.string.arr_radarr_addnew_discover_popular)
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 120.dp),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = 4.dp,
                bottom = 16.dp,
            ),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            fun section(title: String, items: List<ImportListMoviesResource>) {
                if (items.isEmpty()) return
                item(span = { GridItemSpan(maxLineSpan) }, key = "header-$title") {
                    SectionHeader(title = title)
                }
                items(items, key = { it.tmdbId }) { item ->
                    DiscoveryCard(
                        item = item,
                        existing = item.tmdbId in state.existingTmdbIds,
                        queued = item.tmdbId in state.queuedTmdbIds,
                        imageLoader = imageLoader,
                        onOpenAddSheet = onOpenAddSheet,
                    )
                }
            }
            section(recommendedTitle, sections.recommended)
            section(trendingTitle, sections.trending)
            section(popularTitle, sections.popular)
        }
    }
}

/** Snackbar text for one add-flow message, resolved where resources live. */
@Composable
internal fun addNewMessageText(message: AddNewMessage): String = when (message) {
    is AddNewMessage.Added ->
        stringResource(R.string.arr_radarr_addnew_notice_added, message.title)

    is AddNewMessage.AddedAndSearching ->
        stringResource(R.string.arr_radarr_addnew_notice_added_searching, message.title)

    is AddNewMessage.AddQueued ->
        stringResource(R.string.arr_radarr_addnew_notice_add_queued, message.title)

    is AddNewMessage.AddFailed ->
        stringResource(R.string.arr_radarr_addnew_notice_add_failed, message.title)
}

/** Snackbar text for one collections message, resolved where resources live. */
@Composable
internal fun collectionsMessageText(message: CollectionsMessage): String = when (message) {
    is CollectionsMessage.Updated ->
        stringResource(R.string.arr_radarr_collections_notice_updated, message.title)

    is CollectionsMessage.UpdateQueued ->
        stringResource(R.string.arr_radarr_collections_notice_update_queued, message.title)

    is CollectionsMessage.UpdateFailed ->
        stringResource(R.string.arr_radarr_collections_notice_update_failed, message.title)

    is CollectionsMessage.SearchStarted ->
        stringResource(
            R.string.arr_radarr_collections_notice_search_started,
            message.count,
            message.title,
        )

    is CollectionsMessage.SearchQueued ->
        stringResource(R.string.arr_radarr_collections_notice_search_queued, message.title)

    is CollectionsMessage.SearchFailed ->
        stringResource(R.string.arr_radarr_collections_notice_search_failed, message.title)

    is CollectionsMessage.NothingToSearch ->
        stringResource(R.string.arr_radarr_collections_notice_search_nothing, message.title)
}
