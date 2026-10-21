package dev.appolon.homelab.arr.feature.bazarr.wanted.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.appolon.homelab.arr.feature.bazarr.R
import dev.appolon.homelab.arr.feature.bazarr.episodes.model.BazarrWantedEpisode
import dev.appolon.homelab.arr.feature.bazarr.episodes.model.BazarrWantedMovie
import dev.appolon.homelab.arr.feature.bazarr.episodes.ui.LanguageFilterChips
import dev.appolon.homelab.arr.feature.bazarr.episodes.ui.QueuedBadge
import dev.appolon.homelab.arr.feature.bazarr.episodes.ui.languageLabel
import dev.appolon.homelab.arr.feature.bazarr.wanted.vm.BazarrWantedUiState
import dev.appolon.homelab.arr.feature.bazarr.wanted.vm.WantedListTab
import dev.appolon.homelab.arr.feature.bazarr.wanted.vm.WantedMessage
import dev.appolon.homelab.arr.ui.lce.ArrLceBox
import dev.appolon.homelab.arr.ui.lce.ArrPullToRefreshBox
import dev.appolon.homelab.arr.ui.lce.LceState
import dev.appolon.homelab.arr.ui.shell.ArrScaffold

/**
 * Wanted subtitles: episodes and movies missing subtitles, tabbed, each row
 * listing its missing languages with a per-row search-and-download action.
 * Long-press starts a selection for bulk search; the badges line shows the
 * server's total missing counts. Language chips filter both tabs.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun BazarrWantedScreen(
    state: BazarrWantedUiState,
    lce: LceState<BazarrWantedUiState>,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onTab: (WantedListTab) -> Unit,
    onLanguageFilter: (String?) -> Unit,
    onSearchEpisode: (BazarrWantedEpisode) -> Unit,
    onSearchMovie: (BazarrWantedMovie) -> Unit,
    onToggleEpisodeSelected: (Int) -> Unit,
    onToggleMovieSelected: (Int) -> Unit,
    onSelectAll: () -> Unit,
    onClearSelection: () -> Unit,
    onSearchSelected: () -> Unit,
    onSearchAll: () -> Unit,
    onOpenEpisodeSeries: (BazarrWantedEpisode) -> Unit,
    onOpenMovie: (BazarrWantedMovie) -> Unit,
    onConsumeMessage: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val snackbar = remember { SnackbarHostState() }
    val messageText = state.message?.let { wantedMessageText(it) }
    LaunchedEffect(messageText) {
        messageText?.let {
            snackbar.showSnackbar(it)
            onConsumeMessage()
        }
    }

    ArrScaffold(
        title = stringResource(R.string.arr_bazarr_wanted_title),
        onBack = onBack,
        actions = {
            val cd = stringResource(R.string.arr_bazarr_cd_refresh)
            IconButton(
                onClick = onRefresh,
                modifier = Modifier.semantics { contentDescription = cd },
            ) {
                Icon(Icons.Filled.Refresh, contentDescription = cd)
            }
        },
        filterBar = {
            PrimaryTabRow(selectedTabIndex = state.tab.ordinal) {
                WantedListTab.entries.forEach { tab ->
                    Tab(
                        selected = state.tab == tab,
                        onClick = { onTab(tab) },
                        text = {
                            Text(
                                when (tab) {
                                    WantedListTab.EPISODES ->
                                        stringResource(R.string.arr_bazarr_wanted_tab_episodes)

                                    WantedListTab.MOVIES ->
                                        stringResource(R.string.arr_bazarr_wanted_tab_movies)
                                },
                            )
                        },
                    )
                }
            }
            LanguageFilterChips(
                languages = state.languages,
                selected = state.selectedLanguage,
                onSelect = onLanguageFilter,
                modifier = Modifier.padding(top = 4.dp),
            )
            state.badges?.let { badges ->
                val badgesText = stringResource(
                    R.string.arr_bazarr_wanted_badges_line,
                    badges.episodes,
                    badges.movies,
                )
                Text(
                    text = badgesText,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                        .semantics { contentDescription = badgesText },
                )
            }
        },
        modifier = modifier,
    ) { padding ->
        BoxWithConstraints(modifier = Modifier.fillMaxSize().padding(padding)) {
            val contentWidth = if (maxWidth >= 840.dp) 800.dp else maxWidth
            ArrLceBox(
                state = lce,
                onRetry = onRefresh,
                emptyTitle = stringResource(R.string.arr_bazarr_wanted_empty_title),
                emptyMessage = stringResource(R.string.arr_bazarr_wanted_empty_message),
                modifier = Modifier.fillMaxSize(),
            ) { _ ->
                ArrPullToRefreshBox(
                    isRefreshing = state.refreshing,
                    onRefresh = onRefresh,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    LazyColumn(
                        contentPadding = PaddingValues(
                            start = 16.dp,
                            end = 16.dp,
                            top = 16.dp,
                            bottom = if (state.selectionActive || state.bulkRunning) 96.dp else 16.dp,
                        ),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        if (state.tab == WantedListTab.EPISODES) {
                            items(state.presentedEpisodes, key = { "e${it.episodeId}" }) { row ->
                                WantedEpisodeRow(
                                    row = row,
                                    selected = row.episodeId in state.selectedEpisodeIds,
                                    selectionActive = state.selectionActive,
                                    queued = row.episodeId in state.queuedEpisodeIds,
                                    busy = row.episodeId in state.busyEpisodeIds,
                                    onToggleSelected = { onToggleEpisodeSelected(row.episodeId) },
                                    onSearch = { onSearchEpisode(row) },
                                    onOpenSeries = { onOpenEpisodeSeries(row) },
                                    modifier = Modifier.fillMaxWidth().widthIn(max = contentWidth),
                                )
                            }
                        } else {
                            items(state.presentedMovies, key = { "m${it.radarrId}" }) { row ->
                                WantedMovieRow(
                                    row = row,
                                    selected = row.radarrId in state.selectedMovieIds,
                                    selectionActive = state.selectionActive,
                                    queued = row.radarrId in state.queuedMovieIds,
                                    busy = row.radarrId in state.busyMovieIds,
                                    onToggleSelected = { onToggleMovieSelected(row.radarrId) },
                                    onSearch = { onSearchMovie(row) },
                                    onOpenMovie = { onOpenMovie(row) },
                                    modifier = Modifier.fillMaxWidth().widthIn(max = contentWidth),
                                )
                            }
                        }
                    }
                }
            }
            if (state.selectionActive || state.bulkRunning) {
                WantedBulkBar(
                    selectedCount = state.selectionCount,
                    bulkRunning = state.bulkRunning,
                    onSelectAll = onSelectAll,
                    onClear = onClearSelection,
                    onSearchSelected = onSearchSelected,
                    onSearchAll = onSearchAll,
                    modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
                )
            }
            SnackbarHost(
                hostState = snackbar,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }
}

/** Bottom action bar for bulk search-and-download. */
@Composable
private fun WantedBulkBar(
    selectedCount: Int,
    bulkRunning: Boolean,
    onSelectAll: () -> Unit,
    onClear: () -> Unit,
    onSearchSelected: () -> Unit,
    onSearchAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        tonalElevation = 4.dp,
        shadowElevation = 8.dp,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = modifier,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .widthIn(max = 800.dp),
        ) {
            val selectAllCd = stringResource(R.string.arr_bazarr_wanted_cd_select_all)
            IconButton(
                onClick = onSelectAll,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .semantics { contentDescription = selectAllCd },
            ) {
                Icon(Icons.Filled.SelectAll, contentDescription = selectAllCd)
            }
            Text(
                text = if (bulkRunning) {
                    stringResource(R.string.arr_bazarr_wanted_bulk_running)
                } else {
                    stringResource(R.string.arr_bazarr_wanted_selected_count, selectedCount)
                },
                style = MaterialTheme.typography.labelLarge,
                // Polite live region: the count and the bulk-run state change
                // without focus moving here.
                modifier = Modifier
                    .weight(1f)
                    .semantics { liveRegion = LiveRegionMode.Polite },
            )
            val clearCd = stringResource(R.string.arr_bazarr_wanted_cd_clear_selection)
            IconButton(
                onClick = onClear,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .semantics { contentDescription = clearCd },
            ) {
                Icon(Icons.Filled.Close, contentDescription = clearCd)
            }
            OutlinedButton(
                onClick = onSearchAll,
                enabled = !bulkRunning,
                modifier = Modifier.minimumInteractiveComponentSize(),
            ) {
                Text(stringResource(R.string.arr_bazarr_wanted_search_all))
            }
            Button(
                onClick = onSearchSelected,
                enabled = !bulkRunning && selectedCount > 0,
                modifier = Modifier.minimumInteractiveComponentSize(),
            ) {
                Text(stringResource(R.string.arr_bazarr_wanted_search_selected))
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun WantedEpisodeRow(
    row: BazarrWantedEpisode,
    selected: Boolean,
    selectionActive: Boolean,
    queued: Boolean,
    busy: Boolean,
    onToggleSelected: () -> Unit,
    onSearch: () -> Unit,
    onOpenSeries: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val selectCd = stringResource(R.string.arr_bazarr_wanted_cd_select)
    Card(
        colors = if (selected) {
            CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
        } else {
            CardDefaults.cardColors()
        },
        modifier = modifier.combinedClickable(
            onClick = onToggleSelected.takeIf { selectionActive } ?: onOpenSeries,
            onLongClick = onToggleSelected,
        ),
    ) {
        ListItem(
            headlineContent = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(row.code, style = MaterialTheme.typography.titleSmall)
                    Text(row.title, style = MaterialTheme.typography.titleSmall, maxLines = 1)
                }
            },
            supportingContent = {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    if (row.seriesTitle.isNotBlank()) {
                        Text(
                            text = row.seriesTitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    Text(
                        text = stringResource(
                            R.string.arr_bazarr_missing_list,
                            row.missingLanguages.joinToString { languageLabel(it.code2, it.name) },
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            trailingContent = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (queued) QueuedBadge()
                    if (busy) {
                        busySpinner()
                    }
                    val cd = stringResource(R.string.arr_bazarr_cd_search_download)
                    IconButton(
                        onClick = onSearch,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .semantics { contentDescription = cd },
                    ) {
                        Icon(Icons.Filled.Download, contentDescription = cd)
                    }
                    if (selectionActive) {
                        Checkbox(
                            checked = selected,
                            onCheckedChange = { onToggleSelected() },
                            modifier = Modifier.semantics { contentDescription = selectCd },
                        )
                    }
                }
            },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun WantedMovieRow(
    row: BazarrWantedMovie,
    selected: Boolean,
    selectionActive: Boolean,
    queued: Boolean,
    busy: Boolean,
    onToggleSelected: () -> Unit,
    onSearch: () -> Unit,
    onOpenMovie: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val selectCd = stringResource(R.string.arr_bazarr_wanted_cd_select)
    Card(
        colors = if (selected) {
            CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
        } else {
            CardDefaults.cardColors()
        },
        modifier = modifier.combinedClickable(
            onClick = onToggleSelected.takeIf { selectionActive } ?: onOpenMovie,
            onLongClick = onToggleSelected,
        ),
    ) {
        ListItem(
            headlineContent = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(row.title, style = MaterialTheme.typography.titleSmall, maxLines = 1)
                    row.year?.let {
                        Text(it.toString(), style = MaterialTheme.typography.titleSmall)
                    }
                }
            },
            supportingContent = {
                Text(
                    text = stringResource(
                        R.string.arr_bazarr_missing_list,
                        row.missingLanguages.joinToString { languageLabel(it.code2, it.name) },
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            },
            trailingContent = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (queued) QueuedBadge()
                    if (busy) {
                        busySpinner()
                    }
                    val cd = stringResource(R.string.arr_bazarr_cd_search_download)
                    IconButton(
                        onClick = onSearch,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .semantics { contentDescription = cd },
                    ) {
                        Icon(Icons.Filled.Download, contentDescription = cd)
                    }
                    if (selectionActive) {
                        Checkbox(
                            checked = selected,
                            onCheckedChange = { onToggleSelected() },
                            modifier = Modifier.semantics { contentDescription = selectCd },
                        )
                    }
                }
            },
        )
    }
}

/** Busy-row spinner announcing its state instead of a bare progress bar. */
@Composable
private fun busySpinner() {
    val busyCd = stringResource(R.string.arr_bazarr_cd_loading)
    CircularProgressIndicator(
        modifier = Modifier
            .size(24.dp)
            .semantics { contentDescription = busyCd },
        strokeWidth = 2.dp,
    )
}

/** Snackbar text for every search outcome the screen reports. */
@Composable
internal fun wantedMessageText(message: WantedMessage): String = when (message) {
    is WantedMessage.SearchSynced ->
        stringResource(R.string.arr_bazarr_wanted_bulk_synced, message.count)

    is WantedMessage.SearchQueued ->
        stringResource(R.string.arr_bazarr_wanted_bulk_queued, message.count)

    is WantedMessage.SearchMixed ->
        stringResource(
            R.string.arr_bazarr_wanted_bulk_mixed,
            message.synced,
            message.queued,
            message.failed,
        )

    is WantedMessage.SearchFailed ->
        dev.appolon.homelab.arr.ui.lce.arrErrorMessage(message.error)
}
