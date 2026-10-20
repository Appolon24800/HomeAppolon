package dev.appolon.homelab.arr.feature.sonarr.addnew.ui

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.ImageLoader
import coil3.compose.AsyncImage
import dev.appolon.homelab.arr.api.sonarr.model.ImportListExclusionResource
import dev.appolon.homelab.arr.api.sonarr.model.ImportListResource
import dev.appolon.homelab.arr.api.sonarr.model.MediaCoverTypes
import dev.appolon.homelab.arr.api.sonarr.model.MonitorTypes
import dev.appolon.homelab.arr.api.sonarr.model.SeriesResource
import dev.appolon.homelab.arr.core.ArrImages
import dev.appolon.homelab.arr.feature.sonarr.R
import dev.appolon.homelab.arr.feature.sonarr.addnew.vm.AddMessage
import dev.appolon.homelab.arr.feature.sonarr.addnew.vm.AddSeriesForm
import dev.appolon.homelab.arr.feature.sonarr.addnew.vm.AddSeriesUiState
import dev.appolon.homelab.arr.feature.sonarr.addnew.vm.AddTab
import dev.appolon.homelab.arr.feature.sonarr.addnew.vm.ExclusionDraft
import dev.appolon.homelab.arr.ui.components.SectionHeader
import dev.appolon.homelab.arr.ui.lce.ArrLceBox
import dev.appolon.homelab.arr.ui.lce.ArrPullToRefreshBox
import dev.appolon.homelab.arr.ui.lce.LceState
import dev.appolon.homelab.arr.ui.R as ArrUiR

/**
 * The add-new screen body: a search tab (query with year disambiguation,
 * existing-in-library badges) and an import-lists tab (list browse with sync,
 * exclusions management). Stateless; every mutation goes through the
 * ViewModel callbacks. Wide screens render results as a card grid.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddSeriesContent(
    state: AddSeriesUiState,
    imageLoader: ImageLoader?,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onYearPicked: (Int?) -> Unit,
    onRefresh: () -> Unit,
    onRefreshLists: () -> Unit,
    onOpenAddForm: (SeriesResource) -> Unit,
    onCloseAddForm: () -> Unit,
    onUpdateAddForm: (AddSeriesForm) -> Unit,
    onSetMonitor: (MonitorTypes) -> Unit,
    onToggleSeason: (Int) -> Unit,
    onToggleTag: (Int) -> Unit,
    onAdd: (Boolean) -> Unit,
    onSyncList: (ImportListResource) -> Unit,
    onOpenExclusionDraft: () -> Unit,
    onCloseExclusionDraft: () -> Unit,
    onUpdateExclusionDraft: (ExclusionDraft) -> Unit,
    onSubmitExclusion: () -> Unit,
    onDeleteExclusion: (ImportListExclusionResource) -> Unit,
    onConsumeMessage: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val messageText = addMessageText(state.message)
    LaunchedEffect(state.message) {
        if (messageText != null) {
            snackbarHostState.showSnackbar(messageText)
            onConsumeMessage()
        }
    }

    var tab by rememberSaveable { mutableIntStateOf(AddTab.SEARCH.ordinal) }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.arr_sonarr_add_title)) },
                actions = {
                    IconButton(onClick = onRefresh) {
                        Icon(
                            imageVector = Icons.Filled.Refresh,
                            contentDescription = stringResource(R.string.arr_sonarr_add_cd_refresh),
                        )
                    }
                },
            )
        },
        modifier = modifier.fillMaxSize(),
    ) { padding ->
        Column(Modifier.padding(padding)) {
            TabRow(selectedTabIndex = tab) {
                Tab(
                    selected = tab == AddTab.SEARCH.ordinal,
                    onClick = { tab = AddTab.SEARCH.ordinal },
                    text = { Text(stringResource(ArrUiR.string.arr_common_indexers_mode_search)) },
                )
                Tab(
                    selected = tab == AddTab.LISTS.ordinal,
                    onClick = {
                        tab = AddTab.LISTS.ordinal
                        onRefreshLists()
                    },
                    text = { Text(stringResource(ArrUiR.string.arr_common_lists_title)) },
                )
            }
            if (tab == AddTab.SEARCH.ordinal) {
                SearchTab(
                    state = state,
                    imageLoader = imageLoader,
                    onQueryChange = onQueryChange,
                    onSearch = onSearch,
                    onYearPicked = onYearPicked,
                    onOpenAddForm = onOpenAddForm,
                )
            } else {
                ListsTab(
                    state = state,
                    onRefresh = onRefreshLists,
                    onSyncList = onSyncList,
                    onOpenExclusionDraft = onOpenExclusionDraft,
                    onDeleteExclusion = onDeleteExclusion,
                )
            }
        }
    }

    state.form?.let { form ->
        AddSeriesSheet(
            form = form,
            rootFolders = state.rootFolders,
            qualityProfiles = state.qualityProfiles,
            languageProfiles = state.languageProfiles,
            tags = state.tags,
            submitting = state.submitting,
            onDismiss = onCloseAddForm,
            onUpdate = onUpdateAddForm,
            onSetMonitor = onSetMonitor,
            onToggleSeason = onToggleSeason,
            onToggleTag = onToggleTag,
            onAdd = onAdd,
        )
    }

    state.exclusionDraft?.let { draft ->
        AddExclusionDialog(
            draft = draft,
            onDismiss = onCloseExclusionDraft,
            onUpdate = onUpdateExclusionDraft,
            onSave = onSubmitExclusion,
        )
    }
}

/** Query field, year disambiguation chips, and the results list or grid. */
@Composable
private fun SearchTab(
    state: AddSeriesUiState,
    imageLoader: ImageLoader?,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onYearPicked: (Int?) -> Unit,
    onOpenAddForm: (SeriesResource) -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = state.query,
            onValueChange = onQueryChange,
            label = { Text(stringResource(R.string.arr_sonarr_add_search_hint)) },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Filled.Search,
                    contentDescription = null,
                )
            },
            trailingIcon = {
                if (state.query.isNotEmpty()) {
                    IconButton(onClick = { onQueryChange("") }) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = stringResource(
                                R.string.arr_sonarr_add_cd_clear_search,
                            ),
                        )
                    }
                }
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { onSearch() }),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        )

        if (state.availableYears.size > 1 || state.yearFilter != null) {
            YearChipRow(state = state, onYearPicked = onYearPicked)
        }
        if (state.queuedForServer > 0) {
            QueuedBanner(count = state.queuedForServer)
        }

        ArrLceBox(
            state = searchLceOf(state),
            onRetry = onSearch,
            modifier = Modifier.fillMaxSize(),
            emptyTitle = stringResource(
                if (state.searched) {
                    ArrUiR.string.arr_common_addnew_search_empty_title
                } else {
                    R.string.arr_sonarr_add_results_start_title
                },
            ),
            emptyMessage = stringResource(
                if (state.searched) {
                    R.string.arr_sonarr_add_results_none_message
                } else {
                    R.string.arr_sonarr_add_results_start_message
                },
            ),
        ) { results ->
            BoxWithConstraints(Modifier.fillMaxSize()) {
                if (maxWidth >= 600.dp) {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 320.dp),
                        contentPadding = PaddingValues(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        gridItems(results, key = { resultKey(it) }) { series ->
                            ResultRow(
                                series = series,
                                state = state,
                                imageLoader = imageLoader,
                                onOpenAddForm = onOpenAddForm,
                                modifier = Modifier.clip(RoundedCornerShape(12.dp)),
                            )
                        }
                    }
                } else {
                    LazyColumn(contentPadding = PaddingValues(vertical = 8.dp)) {
                        items(
                            results,
                            key = { resultKey(it) },
                            contentType = { "search-result" },
                        ) { series ->
                            ResultRow(
                                series = series,
                                state = state,
                                imageLoader = imageLoader,
                                onOpenAddForm = onOpenAddForm,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Year chips disambiguating same-titled series; the first chip clears. */
@Composable
private fun YearChipRow(
    state: AddSeriesUiState,
    onYearPicked: (Int?) -> Unit,
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
    ) {
        item(key = "all") {
            FilterChip(
                selected = state.yearFilter == null,
                onClick = { onYearPicked(null) },
                label = { Text(stringResource(R.string.arr_sonarr_add_year_all)) },
            )
        }
        items(state.availableYears, key = { it }, contentType = { "year" }) { year ->
            FilterChip(
                selected = state.yearFilter == year,
                onClick = { onYearPicked(if (state.yearFilter == year) null else year) },
                label = { Text(year.toString()) },
            )
        }
    }
}

/** Offline queue indicator, shown while actions wait to sync. */
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
            text = stringResource(ArrUiR.string.arr_common_system_pending_banner, count),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.tertiary,
        )
    }
}

/**
 * One search result: remote poster, title, year, seasons, network, and an
 * add action. Series already in the library (or with a queued add) show a
 * badge instead of a second add button.
 */
@Composable
private fun ResultRow(
    series: SeriesResource,
    state: AddSeriesUiState,
    imageLoader: ImageLoader?,
    onOpenAddForm: (SeriesResource) -> Unit,
    modifier: Modifier = Modifier,
) {
    val title = series.title.orEmpty().ifEmpty { "#${series.tvdbId}" }
    val inLibrary = series.tvdbId in state.libraryTvdbIds
    val queued = series.tvdbId in state.queuedTvdbIds
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Box(
            modifier = Modifier
                .width(56.dp)
                .aspectRatio(2f / 3f)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.primaryContainer),
        ) {
            lookupPosterUrl(series)?.let { url ->
                val loader = imageLoader
                if (loader != null) {
                    AsyncImage(
                        model = url,
                        contentDescription = stringResource(
                            ArrUiR.string.arr_common_add_cd_result_poster,
                            title,
                        ),
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
            val seasons = pluralStringResource(
                R.plurals.arr_sonarr_add_season_count,
                series.seasons.orEmpty().size,
                series.seasons.orEmpty().size,
            )
            Text(
                text = listOfNotNull(
                    series.year.takeIf { it > 0 }?.toString(),
                    seasons,
                    series.network?.takeIf { it.isNotBlank() },
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (inLibrary) {
                InLibraryBadge()
            }
        }
        when {
            inLibrary -> Icon(
                imageVector = Icons.Filled.CheckCircle,
                contentDescription = stringResource(
                    R.string.arr_sonarr_add_cd_in_library,
                    title,
                ),
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .size(28.dp)
                    .padding(start = 4.dp),
            )

            queued -> Icon(
                imageVector = Icons.Filled.Schedule,
                contentDescription = stringResource(R.string.arr_sonarr_add_cd_queued_add),
                tint = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier
                    .size(28.dp)
                    .padding(start = 4.dp),
            )

            else -> IconButton(onClick = { onOpenAddForm(series) }) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = stringResource(
                        ArrUiR.string.arr_common_add_cd_open_add,
                        title,
                    ),
                )
            }
        }
    }
}

/** Small "In library" tag under a result's subtitle. */
@Composable
private fun InLibraryBadge() {
    Text(
        text = stringResource(ArrUiR.string.arr_common_lists_preview_existing),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onPrimaryContainer,
        modifier = Modifier
            .padding(top = 4.dp)
            .background(
                MaterialTheme.colorScheme.primaryContainer,
                RoundedCornerShape(8.dp),
            )
            .padding(horizontal = 8.dp, vertical = 2.dp),
    )
}

/** Import lists with per-list sync plus the exclusions list. */
@Composable
private fun ListsTab(
    state: AddSeriesUiState,
    onRefresh: () -> Unit,
    onSyncList: (ImportListResource) -> Unit,
    onOpenExclusionDraft: () -> Unit,
    onDeleteExclusion: (ImportListExclusionResource) -> Unit,
) {
    ArrLceBox(
        state = listsLceOf(state),
        onRetry = onRefresh,
        modifier = Modifier.fillMaxSize(),
        emptyTitle = stringResource(ArrUiR.string.arr_common_lists_empty_title),
        emptyMessage = stringResource(R.string.arr_sonarr_add_lists_empty_message),
    ) { content ->
        ArrPullToRefreshBox(
            isRefreshing = content.listsLoading,
            onRefresh = onRefresh,
            modifier = Modifier.fillMaxSize(),
        ) {
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
            ) {
                if (content.queuedForServer > 0) {
                    QueuedBanner(count = content.queuedForServer)
                }
                SectionHeader(title = stringResource(ArrUiR.string.arr_common_lists_title))
                content.importLists.forEach { list ->
                    ImportListRow(list = list, onSyncList = onSyncList)
                    HorizontalDivider()
                }
                SectionHeader(
                    title = stringResource(R.string.arr_sonarr_exclusions_section),
                    action = {
                        TextButton(onClick = onOpenExclusionDraft) {
                            Text(stringResource(ArrUiR.string.arr_common_exclusions_add))
                        }
                    },
                )
                if (content.exclusions.isEmpty()) {
                    Text(
                        text = stringResource(R.string.arr_sonarr_add_exclusions_empty),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                }
                content.exclusions.forEach { exclusion ->
                    ExclusionRow(exclusion = exclusion, onDeleteExclusion = onDeleteExclusion)
                    HorizontalDivider()
                }
                Spacer(Modifier.size(16.dp))
            }
        }
    }
}

/** One configured import list with its sync action. */
@Composable
private fun ImportListRow(
    list: ImportListResource,
    onSyncList: (ImportListResource) -> Unit,
) {
    val name = list.name.orEmpty().ifEmpty { "#${list.id}" }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = listOfNotNull(
                    list.implementationName?.takeIf { it.isNotBlank() },
                    list.rootFolderPath?.takeIf { it.isNotBlank() },
                    if (list.enableAutomaticAdd) {
                        stringResource(R.string.arr_sonarr_add_list_automatic)
                    } else {
                        null
                    },
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        IconButton(onClick = { onSyncList(list) }) {
            Icon(
                imageVector = Icons.Filled.Sync,
                contentDescription = stringResource(
                    R.string.arr_sonarr_add_cd_sync_list,
                    name,
                ),
            )
        }
    }
}

/** One exclusion with its delete action. */
@Composable
private fun ExclusionRow(
    exclusion: ImportListExclusionResource,
    onDeleteExclusion: (ImportListExclusionResource) -> Unit,
) {
    val title = exclusion.title.orEmpty().ifEmpty { "#${exclusion.tvdbId}" }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = stringResource(R.string.arr_sonarr_exclusions_tvdb_value, exclusion.tvdbId),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = { onDeleteExclusion(exclusion) }) {
            Icon(
                imageVector = Icons.Filled.Delete,
                contentDescription = stringResource(
                    R.string.arr_sonarr_add_exclusion_delete_cd,
                    title,
                ),
            )
        }
    }
}

/** The LCE projection of the results section. */
private fun searchLceOf(state: AddSeriesUiState): LceState<List<SeriesResource>> = when {
    state.searching -> LceState.Loading
    state.searchError != null && state.results.isEmpty() ->
        LceState.Error(state.searchError)

    state.searched && state.visibleResults.isEmpty() -> LceState.Empty
    !state.searched -> LceState.Empty
    else -> LceState.Content(state.visibleResults)
}

/** The LCE projection of the lists tab; data wins over a partial error. */
private fun listsLceOf(state: AddSeriesUiState): LceState<AddSeriesUiState> = when {
    state.listsError != null && state.importLists.isEmpty() && state.exclusions.isEmpty() ->
        LceState.Error(state.listsError)

    !state.listsLoading && state.importLists.isEmpty() && state.exclusions.isEmpty() ->
        LceState.Empty

    else -> LceState.Content(state, refreshing = state.listsLoading)
}

/**
 * Stable result key. Lookup results carry no server id, so the tvdb id (with
 * the title as a last resort) keys the lazy collections.
 */
private fun resultKey(series: SeriesResource): String =
    if (series.tvdbId > 0) "tvdb-${series.tvdbId}" else "t-${series.title}"

/**
 * Lookup results only ever carry remote art (the server has no local cover
 * for a series it does not track), so this resolves straight to the remote
 * URL; MediaCover endpoints are never fetched through Retrofit.
 */
private fun lookupPosterUrl(series: SeriesResource): String? {
    val remote = series.images.orEmpty()
        .firstOrNull { it.coverType == MediaCoverTypes.POSTER }?.remoteUrl
    return ArrImages.tmdb(remote) ?: ArrImages.tmdb(series.remotePoster)
}

/** Snackbar text for the transient write result. */
@Composable
private fun addMessageText(message: AddMessage?): String? = when (message) {
    null -> null
    is AddMessage.Added -> stringResource(R.string.arr_sonarr_add_message_added, message.title)
    is AddMessage.AddQueued -> stringResource(
        ArrUiR.string.arr_common_add_notice_queued,
        message.title,
    )

    AddMessage.AddFailed -> stringResource(R.string.arr_sonarr_add_message_add_failed)
    AddMessage.ListSynced -> stringResource(R.string.arr_sonarr_add_message_list_synced)
    AddMessage.ListSyncQueued -> stringResource(R.string.arr_sonarr_add_message_list_queued)
    AddMessage.ListSyncFailed -> stringResource(R.string.arr_sonarr_add_message_list_failed)
    AddMessage.ExclusionSaved -> stringResource(R.string.arr_sonarr_add_message_exclusion_saved)
    AddMessage.ExclusionDeleted -> stringResource(R.string.arr_sonarr_add_message_exclusion_deleted)
    AddMessage.ExclusionQueued -> stringResource(R.string.arr_sonarr_add_message_exclusion_queued)
    AddMessage.ExclusionFailed -> stringResource(R.string.arr_sonarr_add_message_exclusion_failed)
}
