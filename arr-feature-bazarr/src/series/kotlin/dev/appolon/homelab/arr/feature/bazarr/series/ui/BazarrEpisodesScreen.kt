package dev.appolon.homelab.arr.feature.bazarr.series.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.appolon.homelab.arr.feature.bazarr.R
import dev.appolon.homelab.arr.feature.bazarr.series.data.BazarrEpisode
import dev.appolon.homelab.arr.feature.bazarr.series.data.BazarrSeries
import dev.appolon.homelab.arr.feature.bazarr.series.vm.BazarrEpisodesUiState
import dev.appolon.homelab.arr.ui.components.SectionHeader
import dev.appolon.homelab.arr.ui.lce.ArrLceBox
import dev.appolon.homelab.arr.ui.lce.ArrPullToRefreshBox
import dev.appolon.homelab.arr.ui.lce.LceState
import dev.appolon.homelab.arr.ui.shell.ArrScaffold

/**
 * The episodes browser of one series: a header row with the series-level
 * status, then every season with one row per episode carrying its own
 * subtitle state, present language chips, and missing-language chips.
 * Activating an episode hands off to the subtitle detail through a callback
 * slot the later subtitles feature wires up.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun BazarrEpisodesScreen(
    state: BazarrEpisodesUiState,
    lce: LceState<BazarrEpisodesUiState>,
    onRefresh: () -> Unit,
    onBack: (() -> Unit)? = null,
    onEpisodeActivate: (seriesId: Int, episodeId: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val seriesTitle = state.series?.title
    ArrScaffold(
        title = seriesTitle ?: stringResource(R.string.arr_bazarr_series_episodes_title),
        subtitle = stringResource(R.string.arr_bazarr_series_episodes_title),
        onBack = onBack,
        modifier = modifier,
    ) { padding ->
        ArrPullToRefreshBox(
            isRefreshing = state.refreshing,
            onRefresh = onRefresh,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            ArrLceBox(
                state = lce,
                onRetry = onRefresh,
                emptyTitle = stringResource(R.string.arr_bazarr_series_episodes_empty_title),
                emptyMessage = stringResource(R.string.arr_bazarr_series_episodes_empty_message),
                modifier = Modifier.fillMaxSize(),
            ) { content ->
                EpisodesList(
                    state = content,
                    onEpisodeActivate = onEpisodeActivate,
                )
            }
        }
    }
}

@Composable
private fun EpisodesList(
    state: BazarrEpisodesUiState,
    onEpisodeActivate: (seriesId: Int, episodeId: Int) -> Unit,
) {
    val listState = rememberLazyListState()
    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(bottom = 16.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        state.series?.let { series ->
            item(key = "header") { SeriesHeader(series) }
        }
        state.seasons.forEach { (season, episodes) ->
            item(key = "season-$season") {
                SectionHeader(
                    title = stringResource(R.string.arr_bazarr_series_episodes_season, season),
                    modifier = Modifier.semantics { heading() },
                )
            }
            items(episodes, key = { it.episodeId }) { episode ->
                EpisodeRow(
                    episode = episode,
                    onActivate = {
                        onEpisodeActivate(episode.seriesId, episode.episodeId)
                    },
                )
            }
        }
    }
}

/** Series summary header: status badge plus the episode progress line. */
@Composable
private fun SeriesHeader(series: BazarrSeries) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Column(Modifier.weight(1f)) {
                if (series.overview?.isNotBlank() == true) {
                    Text(
                        text = series.overview.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.padding(top = 4.dp))
                }
                Text(
                    text = stringResource(
                        R.string.arr_bazarr_series_episode_progress,
                        series.completeEpisodeCount,
                        series.episodeFileCount,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            StatusBadge(series.status)
        }
    }
}

/** One episode row: code, title, status, present and missing languages. */
@Composable
private fun EpisodeRow(
    episode: BazarrEpisode,
    onActivate: () -> Unit,
) {
    val openDescription = stringResource(
        R.string.arr_bazarr_series_episodes_cd_open,
        "${episode.code} ${episode.title}",
    )
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .semantics { contentDescription = openDescription }
            .clickable(onClick = onActivate),
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = episode.code,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.padding(start = 8.dp))
                Text(
                    text = episode.title,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                StatusBadge(episode.status, Modifier.padding(start = 8.dp))
            }
            if (episode.subtitles.isNotEmpty() || episode.missingSubtitles.isNotEmpty()) {
                Spacer(Modifier.padding(top = 6.dp))
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (episode.subtitles.isNotEmpty()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = stringResource(
                                    R.string.arr_bazarr_series_episodes_present,
                                ) + ":",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(end = 8.dp),
                            )
                            LanguageChips(languages = episode.subtitles)
                        }
                    }
                    if (episode.missingSubtitles.isNotEmpty()) {
                        val missingLabel = stringResource(
                            R.string.arr_bazarr_series_episodes_missing,
                        )
                        Text(
                            text = "$missingLabel:",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                        LanguageChips(
                            languages = episode.missingSubtitles,
                            highlight = true,
                        )
                    }
                }
            }
        }
    }
}
