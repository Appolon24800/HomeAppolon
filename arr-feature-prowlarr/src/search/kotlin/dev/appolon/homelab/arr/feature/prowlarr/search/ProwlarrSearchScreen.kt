package dev.appolon.homelab.arr.feature.prowlarr.search

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import dev.appolon.homelab.arr.api.prowlarr.model.IndexerCategory
import dev.appolon.homelab.arr.api.prowlarr.model.IndexerStatsResource
import dev.appolon.homelab.arr.api.prowlarr.model.ReleaseResource
import dev.appolon.homelab.arr.feature.prowlarr.R
import dev.appolon.homelab.arr.ui.components.SortMenu
import dev.appolon.homelab.arr.ui.lce.ArrLceBox
import dev.appolon.homelab.arr.ui.lce.LceState
import dev.appolon.homelab.arr.ui.lce.arrErrorMessage
import dev.appolon.homelab.arr.ui.shell.ArrScaffold

enum class SearchPage { SEARCH, HISTORY, STATS }

/** Native multi-indexer search, query history, and accessible period comparison charts. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProwlarrSearchScreen(
    state: ProwlarrSearchState,
    page: SearchPage,
    vm: ProwlarrSearchViewModel,
    onPage: (SearchPage) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var grabTarget by remember { mutableStateOf<ReleaseResource?>(null) }
    val sortLabels = mapOf(
        SearchSort.AGE to stringResource(R.string.prowlarr_search_age_sort),
        SearchSort.SIZE to stringResource(R.string.prowlarr_search_size_sort),
        SearchSort.SEEDERS to stringResource(R.string.prowlarr_search_seeders_sort),
    )
    val title = when (page) {
        SearchPage.SEARCH -> R.string.prowlarr_search_title
        SearchPage.HISTORY -> R.string.prowlarr_search_history
        SearchPage.STATS -> R.string.prowlarr_search_stats
    }
    val refreshError = when (page) {
        SearchPage.SEARCH -> state.optionsError ?: state.resultsError
        SearchPage.HISTORY -> state.historyError
        SearchPage.STATS -> state.statsError
    }
    val showingCached = when (page) {
        SearchPage.SEARCH -> state.results is LceState.Content || state.options is LceState.Content
        SearchPage.HISTORY -> state.history is LceState.Content
        SearchPage.STATS -> state.stats is LceState.Content
    }
    ArrScaffold(title = stringResource(title), onBack = onBack, modifier = modifier) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Row(Modifier.horizontalScroll(rememberScrollState())) {
                    SearchPage.entries.forEach { destination ->
                        TextButton(
                            onClick = { onPage(destination) }, enabled = destination != page,
                        ) {
                            Text(stringResource(when (destination) {
                                SearchPage.SEARCH -> R.string.prowlarr_search_title
                                SearchPage.HISTORY -> R.string.prowlarr_search_history
                                SearchPage.STATS -> R.string.prowlarr_search_stats
                            }))
                        }
                    }
                }
            }
            (state.error ?: refreshError)?.let { error ->
                item {
                    Text(arrErrorMessage(error), color = MaterialTheme.colorScheme.error)
                    if (state.error == null && showingCached) {
                        Text(stringResource(R.string.prowlarr_search_cached))
                    }
                }
            }
            if (page == SearchPage.SEARCH) {
                item {
                    OutlinedTextField(
                        value = state.request.query, onValueChange = vm::setQuery,
                        label = { Text(stringResource(R.string.prowlarr_search_query)) },
                        singleLine = true, modifier = Modifier.fillMaxWidth(),
                    )
                }
                item {
                    ArrLceBox(
                        state = state.options, onRetry = { vm.loadOptions(true) },
                        emptyTitle = stringResource(R.string.prowlarr_search_empty),
                        emptyMessage = stringResource(R.string.prowlarr_search_empty_message),
                    ) { options -> SearchControls(state, options, vm) }
                }
                item {
                    Row {
                        Button(onClick = { vm.search(true) }) {
                            Text(stringResource(R.string.prowlarr_search_submit))
                        }
                        SortMenu(
                            options = SearchSort.entries, selected = state.sort,
                            label = { sort -> sortLabels.getValue(sort) }, onSelect = vm::setSort,
                        )
                    }
                }
                val content = state.results as? LceState.Content
                if (content != null) {
                    items(sortedReleases(content.data, state.sort)) { release ->
                        ReleaseRow(release, state, onGrab = { grabTarget = release })
                    }
                } else {
                    item {
                        ArrLceBox(
                            state = state.results, onRetry = { vm.search(true) },
                            emptyTitle = stringResource(R.string.prowlarr_search_empty),
                            emptyMessage = stringResource(R.string.prowlarr_search_empty_message),
                        ) { }
                    }
                }
            }
            if (page == SearchPage.HISTORY) {
                item {
                    (state.options as? LceState.Content)?.data?.let { options ->
                        IndexerControls(state, options) { id ->
                            vm.toggleIndexer(id)
                            vm.loadHistory()
                        }
                    }
                    TextButton(onClick = { vm.loadHistory(force = true) }) {
                        Text(stringResource(R.string.prowlarr_search_retry))
                    }
                }
                item {
                    ArrLceBox(
                        state.history, onRetry = { vm.loadHistory(force = true) },
                        emptyTitle = stringResource(R.string.prowlarr_search_empty),
                        emptyMessage = stringResource(R.string.prowlarr_search_empty_message),
                    ) { history ->
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            history.records.orEmpty().forEach { event ->
                                Text(stringResource(
                                    R.string.prowlarr_search_history_row, event.date.orEmpty(),
                                    event.indexerId, stringResource(if (event.successful) {
                                        R.string.prowlarr_search_success
                                    } else R.string.prowlarr_search_failure),
                                ))
                                val unknown = stringResource(R.string.prowlarr_search_unknown)
                                Text(stringResource(
                                    R.string.prowlarr_search_history_data,
                                    event.data?.get("query") ?: unknown,
                                    event.data?.get("elapsedTime")
                                        ?: event.data?.get("responseTime") ?: unknown,
                                    event.data?.get("userAgent") ?: unknown,
                                ))
                                HorizontalDivider()
                            }
                        }
                    }
                }
                item {
                    val history = (state.history as? LceState.Content)?.data
                    Row {
                        TextButton(
                            onClick = { vm.loadHistory(state.page - 1) }, enabled = state.page > 1,
                        ) { Text(stringResource(R.string.prowlarr_search_previous)) }
                        Text(stringResource(R.string.prowlarr_search_page, state.page))
                        TextButton(
                            onClick = { vm.loadHistory(state.page + 1) },
                            enabled = history != null && state.page * 50 < history.totalRecords,
                        ) { Text(stringResource(R.string.prowlarr_search_next)) }
                    }
                }
            }
            if (page == SearchPage.STATS) {
                item {
                    Row(Modifier.horizontalScroll(rememberScrollState())) {
                        listOf(1, 7, 30, 90).forEach { days ->
                            FilterChip(
                                selected = state.days == days, onClick = { vm.loadStats(days) },
                                label = {
                                    Text(stringResource(R.string.prowlarr_search_period, days))
                                },
                                modifier = Modifier.heightIn(min = 48.dp),
                            )
                        }
                    }
                    TextButton(onClick = { vm.loadStats(force = true) }) {
                        Text(stringResource(R.string.prowlarr_search_retry))
                    }
                }
                item {
                    ArrLceBox(
                        state.stats, onRetry = { vm.loadStats(force = true) },
                        emptyTitle = stringResource(R.string.prowlarr_search_empty),
                        emptyMessage = stringResource(R.string.prowlarr_search_empty_message),
                    ) { stats -> StatsCharts(stats) }
                }
            }
        }
    }
    grabTarget?.let { release ->
        val options = (state.options as? LceState.Content)?.data
        val clients = options?.clients.orEmpty().filter {
            it.enable && it.protocol == release.protocol
        }
        AlertDialog(
            onDismissRequest = { grabTarget = null },
            title = { Text(stringResource(R.string.prowlarr_search_client)) },
            text = {
                Column {
                    if (clients.isEmpty()) Text(stringResource(R.string.prowlarr_search_no_clients))
                    clients.forEach { client ->
                        TextButton(onClick = {
                            vm.grab(release, client.id)
                            grabTarget = null
                        }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                            Text(client.name.orEmpty())
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { grabTarget = null }) {
                    Text(stringResource(R.string.prowlarr_search_cancel))
                }
            },
        )
    }
}

@Composable
private fun SearchControls(
    state: ProwlarrSearchState,
    options: SearchOptions,
    vm: ProwlarrSearchViewModel,
) {
    Column {
        IndexerControls(state, options, vm::toggleIndexer)
        Text(stringResource(R.string.prowlarr_search_categories))
        Row(Modifier.horizontalScroll(rememberScrollState())) {
            flattenCategories(options.categories).forEach { category ->
                FilterChip(
                    selected = category.id in state.request.categories,
                    onClick = { vm.toggleCategory(category.id) },
                    label = { Text(category.name.orEmpty()) },
                    modifier = Modifier.heightIn(min = 48.dp),
                )
            }
        }
    }
}

@Composable
private fun IndexerControls(
    state: ProwlarrSearchState,
    options: SearchOptions,
    onToggle: (Int) -> Unit,
) {
    Text(stringResource(R.string.prowlarr_search_indexers))
    Row(Modifier.horizontalScroll(rememberScrollState())) {
        options.indexers.forEach { indexer ->
            FilterChip(
                selected = indexer.id in state.request.indexerIds,
                onClick = { onToggle(indexer.id) },
                label = { Text(indexer.name.orEmpty()) },
                modifier = Modifier.heightIn(min = 48.dp),
            )
        }
    }
}

internal fun flattenCategories(categories: List<IndexerCategory>): List<IndexerCategory> =
    categories.flatMap { listOf(it) + flattenCategories(it.subCategories.orEmpty()) }
        .distinctBy { it.id }

@Suppress("DEPRECATION")
@Composable
private fun ReleaseRow(release: ReleaseResource, state: ProwlarrSearchState, onGrab: () -> Unit) {
    val clipboard = LocalClipboardManager.current
    val key = release.rowKey()
    val magnet = release.magnetUrl?.takeIf { it.startsWith("magnet:?") }
        ?: release.infoHash?.takeIf { it.isNotBlank() }?.let { "magnet:?xt=urn:btih:$it" }
    Surface(tonalElevation = 1.dp, shape = MaterialTheme.shapes.medium) {
        Column(Modifier.fillMaxWidth().padding(12.dp)) {
            Text(release.title.orEmpty(), style = MaterialTheme.typography.titleSmall)
            Text(stringResource(
                R.string.prowlarr_search_quality,
                release.qualityLabel() ?: stringResource(R.string.prowlarr_search_quality_unknown),
            ))
            Text(stringResource(
                R.string.prowlarr_search_metadata,
                if (release.age > 0) {
                    stringResource(R.string.prowlarr_search_age_days, release.age)
                } else {
                    stringResource(R.string.prowlarr_search_age_hours, release.ageHours)
                },
                sizeLabel(release.size),
                release.indexer ?: stringResource(R.string.prowlarr_search_unknown),
            ))
            release.seeders?.let { seeders ->
                Text(stringResource(R.string.prowlarr_search_peers, seeders, release.leechers ?: 0))
            }
            val status = when (key) {
                in state.queued -> R.string.prowlarr_search_queued
                in state.grabbing -> R.string.prowlarr_search_grabbing
                in state.grabbed -> R.string.prowlarr_search_grabbed
                else -> null
            }
            status?.let { Text(stringResource(it), color = MaterialTheme.colorScheme.primary) }
            Row {
                TextButton(onClick = onGrab, enabled = status == null) {
                    Text(stringResource(R.string.prowlarr_search_grab))
                }
                TextButton(
                    onClick = { magnet?.let { clipboard.setText(AnnotatedString(it)) } },
                    enabled = magnet != null,
                ) { Text(stringResource(R.string.prowlarr_search_copy)) }
            }
        }
    }
}

@Composable
private fun StatsCharts(stats: IndexerStatsResource) {
    val indexers = stats.indexers.orEmpty()
    val maxQueries = indexers.maxOfOrNull { it.numberOfQueries }?.coerceAtLeast(1) ?: 1
    val maxResponse = indexers.maxOfOrNull { it.averageResponseTime }?.coerceAtLeast(1) ?: 1
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        indexers.forEach { indexer ->
            val name = indexer.indexerName ?: stringResource(R.string.prowlarr_search_unknown)
            Text(name, style = MaterialTheme.typography.titleMedium)
            val ratio = if (indexer.numberOfQueries > 0) {
                indexer.numberOfGrabs * 100.0 / indexer.numberOfQueries
            } else 0.0
            Text(stringResource(
                R.string.prowlarr_search_stat_row, indexer.numberOfQueries,
                indexer.averageResponseTime, indexer.numberOfGrabs, ratio,
            ))
            val queryLabel = stringResource(
                R.string.prowlarr_search_query_chart, name, indexer.numberOfQueries,
            )
            Text(queryLabel)
            LinearProgressIndicator(
                progress = { (indexer.numberOfQueries.toFloat() / maxQueries).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().semantics { contentDescription = queryLabel },
            )
            val responseLabel = stringResource(
                R.string.prowlarr_search_response_chart, name, indexer.averageResponseTime,
            )
            Text(responseLabel)
            LinearProgressIndicator(
                progress = {
                    (indexer.averageResponseTime.toFloat() / maxResponse).coerceIn(0f, 1f)
                },
                modifier = Modifier.fillMaxWidth().semantics { contentDescription = responseLabel },
                color = MaterialTheme.colorScheme.tertiary,
            )
            HorizontalDivider()
        }
        Text(stringResource(R.string.prowlarr_search_user_agents))
        stats.userAgents.orEmpty().forEach { agent ->
            Text(stringResource(
                R.string.prowlarr_search_agent_row, agent.userAgent.orEmpty(),
                agent.numberOfQueries, agent.numberOfGrabs,
            ))
        }
    }
}
