package dev.appolon.homelab.arr.feature.radarr.settings.system.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.appolon.homelab.arr.api.radarr.model.DatabaseType
import dev.appolon.homelab.arr.api.radarr.model.HealthCheckResult
import dev.appolon.homelab.arr.api.radarr.model.RuntimeMode
import dev.appolon.homelab.arr.core.ArrTime
import dev.appolon.homelab.arr.core.Humanize
import dev.appolon.homelab.arr.core.ServerId
import dev.appolon.homelab.arr.feature.radarr.R
import dev.appolon.homelab.arr.feature.radarr.settings.system.RadarrSettingsSystemClients
import dev.appolon.homelab.arr.feature.radarr.settings.system.data.SystemText
import dev.appolon.homelab.arr.feature.radarr.settings.system.vm.RadarrSystemStatusContent
import dev.appolon.homelab.arr.feature.radarr.settings.system.vm.RadarrSystemStatusViewModel
import dev.appolon.homelab.arr.ui.components.EmptyState
import dev.appolon.homelab.arr.ui.lce.ArrLceBox
import dev.appolon.homelab.arr.ui.lce.ArrPullToRefreshBox
import dev.appolon.homelab.arr.ui.lce.LceState
import dev.appolon.homelab.arr.ui.shell.ArrScaffold

/** Destination for the system status route. */
@Composable
fun RadarrSystemStatusDestination(
    serverId: ServerId,
    clients: RadarrSettingsSystemClients,
    serverName: String? = null,
    onBack: (() -> Unit)? = null,
    onOpenLink: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val repository = remember(serverId, clients) { systemRepository(serverId, clients) }
    val vm: RadarrSystemStatusViewModel =
        viewModel(key = "radarr-system-status-" + serverId.value) {
            RadarrSystemStatusViewModel(repository)
        }
    val lce by vm.lce.collectAsState()

    RadarrSystemStatusScreen(
        lce = lce,
        serverName = serverName,
        onBack = onBack,
        onRefresh = vm::refresh,
        onOpenLink = onOpenLink,
        modifier = modifier,
    )
}

/**
 * The system page: status card (version, runtime, database type and
 * migration state), health checks with wiki-link remedies, disk space bars
 * per root path, and the scheduled task list. Wide screens split into two
 * columns; phones stack the four sections.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RadarrSystemStatusScreen(
    lce: LceState<RadarrSystemStatusContent>,
    serverName: String?,
    onBack: (() -> Unit)?,
    onRefresh: () -> Unit,
    onOpenLink: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    ArrScaffold(
        title = stringResource(R.string.arr_radarr_status_title),
        subtitle = serverName,
        onBack = onBack,
        actions = {
            IconButton(onClick = onRefresh) {
                Icon(
                    imageVector = Icons.Filled.Refresh,
                    contentDescription = stringResource(R.string.arr_radarr_system_cd_refresh),
                )
            }
        },
        modifier = modifier,
    ) { padding ->
        ArrPullToRefreshBox(
            isRefreshing = lce is LceState.Content && (lce as LceState.Content<*>).refreshing,
            onRefresh = onRefresh,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            Box(Modifier.fillMaxSize()) {
                ArrLceBox(
                    state = lce,
                    onRetry = onRefresh,
                    modifier = Modifier.fillMaxSize(),
                ) { data ->
                    BoxWithConstraints(Modifier.fillMaxSize()) {
                        val twoPane = maxWidth >= 720.dp
                        if (twoPane) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 16.dp),
                            ) {
                                Column(
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .widthIn(max = 520.dp),
                                ) {
                                    StatusHostCard(data)
                                    HealthCard(data.health, onOpenLink)
                                }
                                Column(
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.weight(1f),
                                ) {
                                    DiskSpaceCard(data.diskSpace)
                                    TasksCard(data.tasks)
                                }
                            }
                        } else {
                            LazyColumn(
                                contentPadding = PaddingValues(16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.widthIn(max = SectionMaxWidth + 32.dp),
                            ) {
                                item { StatusHostCard(data) }
                                item { HealthCard(data.health, onOpenLink) }
                                item { DiskSpaceCard(data.diskSpace) }
                                item { TasksCard(data.tasks) }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusHostCard(data: RadarrSystemStatusContent) {
    val status = data.status
    SystemCard {
        Column {
            SystemSectionHeader(stringResource(R.string.arr_radarr_status_section_host))
            SystemValueRow(
                label = stringResource(R.string.arr_radarr_status_version),
                value = status.version ?: stringResource(R.string.arr_radarr_status_unknown),
            )
            SystemValueRow(
                label = stringResource(R.string.arr_radarr_status_branch),
                value = status.branch ?: stringResource(R.string.arr_radarr_status_not_set),
            )
            SystemValueRow(
                label = stringResource(R.string.arr_radarr_status_runtime),
                formatPair(
                    stringResource(R.string.arr_radarr_status_unknown),
                    status.runtimeName,
                ) + " " + (status.runtimeVersion ?: "").trim(),
            )
            SystemValueRow(
                label = stringResource(R.string.arr_radarr_status_os),
                value = listOfNotNull(
                    status.osName,
                    status.osVersion?.takeIf { it.isNotBlank() },
                ).joinToString(" ").ifBlank { stringResource(R.string.arr_radarr_status_unknown) },
            )
            SystemValueRow(
                label = stringResource(R.string.arr_radarr_status_mode),
                value = status.mode.display(),
            )
            if (status.isDocker || status.isContainerized) {
                Text(
                    text = stringResource(R.string.arr_radarr_status_docker),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            ArrTime.parse(status.startTime)?.let { started ->
                SystemValueRow(
                    label = stringResource(R.string.arr_radarr_status_start_time),
                    value = ArrTime.dateTimeFull(started),
                )
            }
            ArrTime.parse(status.buildTime)?.let { built ->
                SystemValueRow(
                    label = stringResource(R.string.arr_radarr_status_build_time),
                    value = ArrTime.dateTimeFull(built),
                )
            }
            status.appData?.let { appData ->
                SystemValueRow(
                    label = stringResource(R.string.arr_radarr_status_app_data),
                    value = appData,
                )
            }
            SystemSectionHeader(stringResource(R.string.arr_radarr_status_section_database))
            SystemValueRow(
                label = stringResource(R.string.arr_radarr_status_database_type),
                value = status.databaseType.display(),
            )
            SystemValueRow(
                label = stringResource(R.string.arr_radarr_status_database_version),
                value = status.databaseVersion ?: stringResource(R.string.arr_radarr_status_unknown),
            )
            SystemValueRow(
                label = stringResource(R.string.arr_radarr_status_database_migration),
                value = status.migrationVersion.toString(),
            )
        }
    }
}

@Composable
private fun HealthCard(
    health: List<dev.appolon.homelab.arr.api.radarr.model.HealthResource>,
    onOpenLink: ((String) -> Unit)?,
) {
    SystemCard {
        Column {
            SystemSectionHeader(stringResource(R.string.arr_radarr_status_section_health))
            if (health.isEmpty()) {
                Text(
                    text = stringResource(R.string.arr_radarr_status_health_ok),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = stringResource(R.string.arr_radarr_status_health_ok_message),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            health.forEach { check ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = check.type.display(),
                            style = MaterialTheme.typography.labelLarge,
                            color = check.type.color(),
                        )
                        Text(
                            text = check.message.orEmpty(),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    val wikiUrl = check.wikiUrl
                    if (wikiUrl != null) {
                        SystemExternalLinkButton(
                            contentDescription = stringResource(
                                R.string.arr_radarr_status_cd_health_more_info,
                            ),
                            onOpenLink = onOpenLink?.let { open -> { open(wikiUrl) } },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DiskSpaceCard(
    diskSpace: List<dev.appolon.homelab.arr.api.radarr.model.DiskSpaceResource>,
) {
    SystemCard {
        Column {
            SystemSectionHeader(stringResource(R.string.arr_radarr_status_section_disk))
            if (diskSpace.isEmpty()) {
                Text(
                    text = stringResource(R.string.arr_radarr_status_not_set),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            diskSpace.forEach { disk ->
                val free = disk.freeSpace.coerceAtLeast(0)
                val total = disk.totalSpace.coerceAtLeast(1)
                Column(Modifier.padding(vertical = 8.dp)) {
                    Row {
                        Text(
                            text = disk.label ?: disk.path.orEmpty(),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            text = stringResource(
                                R.string.arr_radarr_status_disk_free_of,
                                Humanize.bytes(free),
                                Humanize.bytes(total),
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    LinearProgressIndicator(
                        progress = { 1f - (free.toFloat() / total) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun TasksCard(
    tasks: List<dev.appolon.homelab.arr.feature.radarr.settings.system.vm.RadarrTaskRow>,
) {
    SystemCard {
        Column {
            SystemSectionHeader(stringResource(R.string.arr_radarr_status_section_tasks))
            if (tasks.isEmpty()) {
                Text(
                    text = stringResource(R.string.arr_radarr_status_not_set),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            tasks.forEach { row ->
                Column(Modifier.padding(vertical = 8.dp)) {
                    Text(
                        text = row.task.name.orEmpty(),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        text = stringResource(
                            R.string.arr_radarr_status_task_interval,
                            SystemText.intervalMinutes(row.intervalMinutes),
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    val lastRun = row.lastExecutionEpochMs?.let { ArrTime.relative(it) }
                    val duration = row.lastDurationSeconds
                    val line = when {
                        lastRun != null && duration != null -> stringResource(
                            R.string.arr_radarr_status_task_last_run,
                            lastRun,
                            Humanize.duration(duration),
                        )

                        lastRun != null -> stringResource(
                            R.string.arr_radarr_status_task_last_run,
                            lastRun,
                            "",
                        )

                        else -> stringResource(R.string.arr_radarr_status_task_never_run)
                    }
                    Text(
                        text = line,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    row.nextExecutionEpochMs?.let { next ->
                        Text(
                            text = stringResource(
                                R.string.arr_radarr_status_task_next_run,
                                ArrTime.relative(next),
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RuntimeMode?.display(): String = when (this) {
    RuntimeMode.CONSOLE, RuntimeMode.SERVICE, RuntimeMode.TRAY -> this.toString()
    null -> stringResource(R.string.arr_radarr_status_unknown)
}

@Composable
private fun DatabaseType?.display(): String = when (this) {
    DatabaseType.SQLITE -> "SQLite"
    DatabaseType.POSTGRESQL -> "PostgreSQL"
    null -> stringResource(R.string.arr_radarr_status_unknown)
}

@Composable
private fun HealthCheckResult?.display(): String = when (this) {
    HealthCheckResult.OK -> "OK"
    HealthCheckResult.NOTICE -> this.toString()
    HealthCheckResult.WARNING -> this.toString()
    HealthCheckResult.ERROR -> this.toString()
    null -> stringResource(R.string.arr_radarr_status_unknown)
}

@Composable
private fun HealthCheckResult?.color(): androidx.compose.ui.graphics.Color =
    when (this) {
        HealthCheckResult.ERROR -> MaterialTheme.colorScheme.error
        HealthCheckResult.WARNING -> androidx.compose.ui.graphics.Color(0xFFB00020)
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

private fun formatPair(fallback: String, value: String?): String =
    value?.takeIf { it.isNotBlank() } ?: fallback
