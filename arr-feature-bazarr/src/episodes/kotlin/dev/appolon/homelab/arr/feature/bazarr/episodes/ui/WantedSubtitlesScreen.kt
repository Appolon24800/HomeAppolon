package dev.appolon.homelab.arr.feature.bazarr.episodes.ui

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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.appolon.homelab.arr.feature.bazarr.R
import dev.appolon.homelab.arr.feature.bazarr.episodes.model.BazarrWantedEpisode
import dev.appolon.homelab.arr.feature.bazarr.episodes.model.BazarrWantedMovie
import dev.appolon.homelab.arr.feature.bazarr.episodes.vm.SubtitlesMessage
import dev.appolon.homelab.arr.feature.bazarr.episodes.vm.WantedSubtitlesUiState
import dev.appolon.homelab.arr.feature.bazarr.episodes.vm.WantedTab
import dev.appolon.homelab.arr.ui.lce.ArrLceBox
import dev.appolon.homelab.arr.ui.lce.ArrPullToRefreshBox
import dev.appolon.homelab.arr.ui.lce.LceState
import dev.appolon.homelab.arr.ui.shell.ArrScaffold

/**
 * Wanted subtitles: episodes and movies missing subtitles, tabbed, each row
 * listing its missing languages with a search-and-download action that
 * requests all of them. Language chips filter both tabs.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun WantedSubtitlesScreen(
    state: WantedSubtitlesUiState,
    lce: LceState<WantedSubtitlesUiState>,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onTab: (WantedTab) -> Unit,
    onLanguageFilter: (String?) -> Unit,
    onSearchEpisode: (BazarrWantedEpisode) -> Unit,
    onSearchMovie: (BazarrWantedMovie) -> Unit,
    onOpenEpisodeSeries: (BazarrWantedEpisode) -> Unit,
    onOpenMovie: (BazarrWantedMovie) -> Unit,
    onConsumeMessage: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val snackbar = remember { SnackbarHostState() }
    val messageText = state.message?.let { subtitlesMessageText(it) }
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
                WantedTab.entries.forEach { tab ->
                    Tab(
                        selected = state.tab == tab,
                        onClick = { onTab(tab) },
                        text = {
                            Text(
                                when (tab) {
                                    WantedTab.EPISODES -> stringResource(R.string.arr_bazarr_wanted_tab_episodes)
                                    WantedTab.MOVIES -> stringResource(R.string.arr_bazarr_wanted_tab_movies)
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
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        if (state.tab == WantedTab.EPISODES) {
                            items(state.presentedEpisodes, key = { "e${it.episodeId}" }) { row ->
                                WantedEpisodeRow(
                                    row = row,
                                    queued = row.episodeId in state.queuedEpisodeIds,
                                    busy = row.episodeId in state.busyEpisodeIds,
                                    onSearch = { onSearchEpisode(row) },
                                    onOpenSeries = { onOpenEpisodeSeries(row) },
                                    modifier = Modifier.fillMaxWidth().widthIn(max = contentWidth),
                                )
                            }
                        } else {
                            items(state.presentedMovies, key = { "m${it.radarrId}" }) { row ->
                                WantedMovieRow(
                                    row = row,
                                    queued = row.radarrId in state.queuedMovieIds,
                                    busy = row.radarrId in state.busyMovieIds,
                                    onSearch = { onSearchMovie(row) },
                                    onOpenMovie = { onOpenMovie(row) },
                                    modifier = Modifier.fillMaxWidth().widthIn(max = contentWidth),
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
}

@Composable
private fun WantedEpisodeRow(
    row: BazarrWantedEpisode,
    queued: Boolean,
    busy: Boolean,
    onSearch: () -> Unit,
    onOpenSeries: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier) {
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
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
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
                }
            },
            modifier = Modifier.toggleableRow(onOpenSeries),
        )
    }
}

@Composable
private fun WantedMovieRow(
    row: BazarrWantedMovie,
    queued: Boolean,
    busy: Boolean,
    onSearch: () -> Unit,
    onOpenMovie: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier) {
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
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
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
                }
            },
            modifier = Modifier.toggleableRow(onOpenMovie),
        )
    }
}

/** Row activation opens the underlying media; icon buttons keep their targets. */
private fun Modifier.toggleableRow(onActivate: () -> Unit): Modifier =
    this.clickable(onClick = onActivate)
