package dev.appolon.homelab.arr.feature.prowlarr.stats.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.appolon.homelab.arr.api.prowlarr.model.HostStatistics
import dev.appolon.homelab.arr.api.prowlarr.model.IndexerStatistics
import dev.appolon.homelab.arr.api.prowlarr.model.UserAgentStatistics
import dev.appolon.homelab.arr.feature.prowlarr.R
import dev.appolon.homelab.arr.feature.prowlarr.stats.data.StatsPeriod
import dev.appolon.homelab.arr.feature.prowlarr.stats.data.StatsPresentation
import dev.appolon.homelab.arr.feature.prowlarr.stats.vm.StatsUiState
import dev.appolon.homelab.arr.ui.components.FilterChipRow
import dev.appolon.homelab.arr.ui.components.SectionHeader
import dev.appolon.homelab.arr.ui.components.StatTile
import dev.appolon.homelab.arr.ui.lce.ArrLceBox
import dev.appolon.homelab.arr.ui.lce.ArrPullToRefreshBox
import dev.appolon.homelab.arr.ui.lce.LceState
import dev.appolon.homelab.arr.ui.shell.ArrScaffold

/** Period chip label; plain words, shared by the selector and the subtitle. */
@Composable
fun periodLabel(period: StatsPeriod): String = stringResource(
    when (period) {
        StatsPeriod.DAY -> R.string.arr_prowlarr_stats_period_day
        StatsPeriod.WEEK -> R.string.arr_prowlarr_stats_period_week
        StatsPeriod.MONTH -> R.string.arr_prowlarr_stats_period_month
        StatsPeriod.YEAR -> R.string.arr_prowlarr_stats_period_year
    },
)

/**
 * Prowlarr stats: per-indexer query and grab counters, grab ratio, response
 * times, user agent breakdown and host totals for the selected window. The
 * charts are plain Canvas draws in theme colors, so nothing new is depended
 * on. Wide layouts put the summary tiles and both charts side by side.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(
    state: StatsUiState,
    lce: LceState<StatsUiState>,
    onRefresh: () -> Unit,
    onPeriodSelect: (StatsPeriod) -> Unit,
    modifier: Modifier = Modifier,
) {
    ArrScaffold(
        title = stringResource(R.string.arr_prowlarr_stats_title),
        subtitle = stringResource(
            R.string.arr_prowlarr_stats_subtitle,
            periodLabel(state.period),
        ),
        modifier = modifier,
        filterBar = {
            val periodLabels = StatsPeriod.entries.associateWith { periodLabel(it) }
            FilterChipRow(
                filters = StatsPeriod.entries,
                selected = { it == state.period },
                label = { periodLabels.getValue(it) },
                onSelect = onPeriodSelect,
            )
        },
    ) { padding ->
        ArrPullToRefreshBox(
            isRefreshing = state.refreshing,
            onRefresh = onRefresh,
            modifier = Modifier.fillMaxWidth(),
        ) {
            ArrLceBox(
                state = lce,
                onRetry = onRefresh,
                emptyTitle = stringResource(R.string.arr_prowlarr_stats_empty_title),
                emptyMessage = stringResource(R.string.arr_prowlarr_stats_empty_message),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(padding),
            ) { content ->
                StatsContent(content, onRefresh)
            }
        }
    }
}

@Composable
private fun StatsContent(state: StatsUiState, onRefresh: () -> Unit) {
    BoxWithConstraints {
        val wide = maxWidth >= 600.dp
        LazyColumn(
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                horizontal = 16.dp, vertical = 12.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            item {
                val totals = state.totals
                val ratioText = stringResource(
                    R.string.arr_prowlarr_stats_ratio_value,
                    totals.grabRatioPercent,
                )
                val responseText = stringResource(
                    R.string.arr_prowlarr_stats_ms_value,
                    totals.averageResponseTimeMs,
                )
                if (wide) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        StatTile(
                            stringResource(R.string.arr_prowlarr_stats_total_queries),
                            totals.queries.toString(),
                            Modifier.weight(1f),
                        )
                        StatTile(
                            stringResource(R.string.arr_prowlarr_stats_total_grabs),
                            totals.grabs.toString(),
                            Modifier.weight(1f),
                        )
                        StatTile(
                            stringResource(R.string.arr_prowlarr_stats_grab_ratio),
                            ratioText,
                            Modifier.weight(1f),
                        )
                        StatTile(
                            stringResource(R.string.arr_prowlarr_stats_avg_response),
                            responseText,
                            Modifier.weight(1f),
                        )
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            StatTile(
                                stringResource(R.string.arr_prowlarr_stats_total_queries),
                                totals.queries.toString(),
                                Modifier.weight(1f),
                            )
                            StatTile(
                                stringResource(R.string.arr_prowlarr_stats_total_grabs),
                                totals.grabs.toString(),
                                Modifier.weight(1f),
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            StatTile(
                                stringResource(R.string.arr_prowlarr_stats_grab_ratio),
                                ratioText,
                                Modifier.weight(1f),
                            )
                            StatTile(
                                stringResource(R.string.arr_prowlarr_stats_avg_response),
                                responseText,
                                Modifier.weight(1f),
                            )
                        }
                    }
                }
            }

            item { SectionHeader(title = stringResource(R.string.arr_prowlarr_stats_per_indexer)) }

            item {
                val rows = state.indexerRows
                if (wide) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        QueriesBarChart(rows, Modifier.weight(1f))
                        ResponseLineChart(rows, Modifier.weight(1f))
                    }
                } else {
                    QueriesBarChart(rows, Modifier.fillMaxWidth())
                    Spacer(Modifier.size(8.dp))
                    ResponseLineChart(rows, Modifier.fillMaxWidth())
                }
            }

            items(state.indexerRows, key = { it.indexerId }) { stat ->
                IndexerStatRow(stat, wide)
                HorizontalDivider()
            }

            val agents = state.stats?.let(StatsPresentation::userAgents).orEmpty()
            if (agents.isNotEmpty()) {
                item {
                    SectionHeader(title = stringResource(R.string.arr_prowlarr_stats_user_agents))
                }
                val totalQueries = agents.sumOf { it.numberOfQueries }
                items(agents, key = { it.userAgent.orEmpty() + it.numberOfQueries }) { agent ->
                    UserAgentRow(agent, totalQueries)
                    HorizontalDivider()
                }
            }

            val hosts = state.stats?.let(StatsPresentation::hosts).orEmpty()
            if (hosts.isNotEmpty()) {
                item { SectionHeader(title = stringResource(R.string.arr_prowlarr_stats_hosts)) }
                items(hosts, key = { it.host.orEmpty() + it.numberOfQueries }) { host ->
                    HostStatRow(host, wide)
                    HorizontalDivider()
                }
            }
        }
    }
}

/** One indexer line: name, counters, grab ratio and response time. */
@Composable
private fun IndexerStatRow(stat: IndexerStatistics, wide: Boolean) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                stat.indexerName ?: "#${stat.indexerId}",
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val failed = stat.numberOfFailedQueries + stat.numberOfFailedGrabs
            Text(
                if (failed > 0) {
                    stringResource(R.string.arr_prowlarr_stats_failed_value, failed)
                } else {
                    stringResource(R.string.arr_prowlarr_stats_no_failures)
                },
                style = MaterialTheme.typography.bodySmall,
                color = if (failed > 0) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
        ValueColumn(
            label = stringResource(R.string.arr_prowlarr_stats_queries),
            value = stat.numberOfQueries.toString(),
        )
        ValueColumn(
            label = stringResource(R.string.arr_prowlarr_stats_grabs),
            value = stat.numberOfGrabs.toString(),
        )
        if (wide) {
            ValueColumn(
                label = stringResource(R.string.arr_prowlarr_stats_grab_ratio),
                value = stringResource(
                    R.string.arr_prowlarr_stats_ratio_value,
                    StatsPresentation.ratioPercent(stat.numberOfGrabs, stat.numberOfQueries),
                ),
            )
        }
        ValueColumn(
            label = stringResource(R.string.arr_prowlarr_stats_response),
            value = stringResource(
                R.string.arr_prowlarr_stats_ms_value,
                stat.averageResponseTime,
            ),
        )
    }
}

/** Right-aligned label over value used by the stat rows. */
@Composable
private fun ValueColumn(label: String, value: String) {
    Column(
        horizontalAlignment = Alignment.End,
        modifier = Modifier.padding(start = 16.dp),
    ) {
        Text(value, style = MaterialTheme.typography.bodyLarge)
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** User agent row with its share of all queries as a thin bar. */
@Composable
private fun UserAgentRow(agent: UserAgentStatistics, totalQueries: Int) {
    val share = StatsPresentation.sharePercent(agent.numberOfQueries, totalQueries)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
    ) {
        Column(Modifier.weight(1f)) {
            val unknownAgent = stringResource(R.string.arr_prowlarr_stats_unknown_agent)
            Text(
                agent.userAgent.orEmpty().ifBlank { unknownAgent },
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            val barCd = stringResource(
                R.string.arr_prowlarr_stats_cd_share_bar,
                agent.userAgent.orEmpty(),
                share,
            )
            Box(
                modifier = Modifier
                    .padding(top = 4.dp)
                    .fillMaxWidth()
                    .height(6.dp)
                    .semantics { contentDescription = barCd },
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(share.coerceIn(0, 100) / 100f)
                        .height(6.dp)
                        .background(
                            MaterialTheme.colorScheme.secondary,
                            RoundedCornerShape(3.dp),
                        ),
                )
            }
        }
        ValueColumn(
            label = stringResource(R.string.arr_prowlarr_stats_queries),
            value = agent.numberOfQueries.toString(),
        )
        ValueColumn(
            label = stringResource(R.string.arr_prowlarr_stats_grabs),
            value = agent.numberOfGrabs.toString(),
        )
    }
}

/** Host-level totals row. */
@Composable
private fun HostStatRow(host: HostStatistics, wide: Boolean) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
    ) {
        val unknownHost = stringResource(R.string.arr_prowlarr_stats_unknown_host)
        Text(
            host.host.orEmpty().ifBlank { unknownHost },
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text(
            stringResource(R.string.arr_prowlarr_stats_queries_value, host.numberOfQueries),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(start = 16.dp),
        )
        Text(
            stringResource(R.string.arr_prowlarr_stats_grabs_value, host.numberOfGrabs),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(start = if (wide) 24.dp else 16.dp),
        )
    }
}

/** Color dot plus label, the legend under each chart. */
@Composable
private fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(color, CircleShape),
        )
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp),
        )
    }
}

/**
 * Stacked bar chart of queries and grabs per indexer, tallest stack first.
 * Canvas only; the row list beneath carries the exact numbers and the chart
 * exposes a spoken summary through its content description.
 */
@Composable
private fun QueriesBarChart(rows: List<IndexerStatistics>, modifier: Modifier = Modifier) {
    val queriesColor = MaterialTheme.colorScheme.primary
    val grabsColor = MaterialTheme.colorScheme.tertiary
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val maxStack = maxOf(rows.maxOfOrNull { it.numberOfQueries + it.numberOfGrabs } ?: 0, 1)
    val summary = stringResource(R.string.arr_prowlarr_stats_cd_queries_chart, rows.size)
    ChartCard(
        title = stringResource(R.string.arr_prowlarr_stats_chart_queries_title),
        legend = {
            LegendDot(queriesColor, stringResource(R.string.arr_prowlarr_stats_queries))
            Spacer(Modifier.width(12.dp))
            LegendDot(grabsColor, stringResource(R.string.arr_prowlarr_stats_grabs))
        },
        modifier = modifier,
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(140.dp)
                .semantics { contentDescription = summary },
        ) {
            if (rows.isEmpty()) return@Canvas
            listOf(0.25f, 0.5f, 0.75f).forEach { fraction ->
                drawLine(
                    color = gridColor,
                    start = Offset(0f, size.height * fraction),
                    end = Offset(size.width, size.height * fraction),
                    strokeWidth = 1.dp.toPx(),
                )
            }
            val slot = size.width / rows.size
            val barWidth = slot * 0.6f
            rows.forEachIndexed { index, stat ->
                val center = slot * index + slot / 2f
                val queriesHeight = size.height * stat.numberOfQueries / maxStack
                val grabsHeight = size.height * stat.numberOfGrabs / maxStack
                drawRect(
                    color = queriesColor,
                    topLeft = Offset(center - barWidth / 2f, size.height - queriesHeight),
                    size = Size(barWidth, queriesHeight),
                )
                drawRect(
                    color = grabsColor,
                    topLeft = Offset(
                        center - barWidth / 2f,
                        size.height - queriesHeight - grabsHeight,
                    ),
                    size = Size(barWidth, grabsHeight),
                )
            }
        }
    }
}

/**
 * Line chart of average response time per indexer over the same row order as
 * the bar chart, so the two read left to right as one table.
 */
@Composable
private fun ResponseLineChart(rows: List<IndexerStatistics>, modifier: Modifier = Modifier) {
    val lineColor = MaterialTheme.colorScheme.secondary
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val maxResponse = maxOf(rows.maxOfOrNull { it.averageResponseTime } ?: 0, 1)
    val slowest = rows.maxByOrNull { it.averageResponseTime }
    val summary = stringResource(
        R.string.arr_prowlarr_stats_cd_response_chart,
        slowest?.indexerName.orEmpty(),
        slowest?.averageResponseTime ?: 0,
    )
    ChartCard(
        title = stringResource(R.string.arr_prowlarr_stats_chart_response_title),
        legend = {
            LegendDot(lineColor, stringResource(R.string.arr_prowlarr_stats_avg_response))
        },
        modifier = modifier,
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(140.dp)
                .semantics { contentDescription = summary },
        ) {
            if (rows.isEmpty()) return@Canvas
            listOf(0.25f, 0.5f, 0.75f).forEach { fraction ->
                drawLine(
                    color = gridColor,
                    start = Offset(0f, size.height * fraction),
                    end = Offset(size.width, size.height * fraction),
                    strokeWidth = 1.dp.toPx(),
                )
            }
            val slot = size.width / rows.size
            fun pointAt(index: Int): Offset = Offset(
                x = slot * index + slot / 2f,
                y = size.height * (1f - rows[index].averageResponseTime.toFloat() / maxResponse),
            )
            val path = Path()
            val pointRadius = 4.dp.toPx()
            rows.indices.forEach { index ->
                val point = pointAt(index)
                if (index == 0) path.moveTo(point.x, point.y) else path.lineTo(point.x, point.y)
                drawCircle(color = lineColor, radius = pointRadius, center = point)
            }
            drawPath(path, color = lineColor, style = Stroke(width = 2.dp.toPx()))
        }
    }
}

/** Card wrapper shared by both charts: title, canvas slot, legend. */
@Composable
private fun ChartCard(
    title: String,
    legend: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    canvas: @Composable () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.size(8.dp))
            canvas()
            Spacer(Modifier.size(8.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                legend()
            }
        }
    }
}
