package dev.appolon.homelab.arr.feature.prowlarr.settings.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.appolon.homelab.arr.core.ArrTime
import dev.appolon.homelab.arr.feature.prowlarr.R
import dev.appolon.homelab.arr.feature.prowlarr.settings.vm.ProwlarrSystemStatusContent
import dev.appolon.homelab.arr.feature.prowlarr.settings.vm.ProwlarrSystemStatusViewModel
import dev.appolon.homelab.arr.ui.components.SectionHeader
import dev.appolon.homelab.arr.ui.lce.ArrLceBox
import dev.appolon.homelab.arr.ui.lce.ArrPullToRefreshBox
import dev.appolon.homelab.arr.ui.lce.LceState
import dev.appolon.homelab.arr.ui.shell.ArrScaffold
import dev.appolon.homelab.arr.ui.R as ArrUiR

/**
 * The system page: status card (version, runtime, database,
 * authentication mode, start time), health findings with wiki-link
 * remedies, and the scheduled task table with interval and next run. Wide
 * screens split status and tasks into two columns; phones stack the
 * sections.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProwlarrSystemStatusScreen(
    state: ProwlarrSystemStatusViewModel.UiState,
    lce: LceState<ProwlarrSystemStatusContent>,
    serverName: String?,
    onBack: (() -> Unit)?,
    onRefresh: () -> Unit,
    onConsumeWriteError: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val content = lce as? LceState.Content<ProwlarrSystemStatusContent>

    NoticeHost(
        noticeMessage = null,
        writeError = state.writeError,
        snackbarHostState = snackbarHostState,
        onConsumeNotice = {},
        onConsumeWriteError = onConsumeWriteError,
    )

    ArrScaffold(
        title = stringResource(ArrUiR.string.arr_common_status_title),
        subtitle = serverName,
        onBack = onBack,
        actions = {
            IconButton(onClick = onRefresh) {
                Icon(
                    imageVector = Icons.Filled.Refresh,
                    contentDescription = stringResource(ArrUiR.string.arr_common_system_cd_refresh),
                )
            }
        },
        modifier = modifier,
    ) { padding ->
        Box(Modifier.fillMaxSize()) {
            ArrPullToRefreshBox(
                isRefreshing = content?.refreshing == true,
                onRefresh = onRefresh,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            ) {
                ArrLceBox(
                    state = lce,
                    onRetry = onRefresh,
                    emptyTitle = stringResource(ArrUiR.string.arr_common_status_title),
                    emptyMessage = stringResource(R.string.arr_prowlarr_settings_system_empty),
                    modifier = Modifier.fillMaxSize(),
                ) { data ->
                    BoxWithConstraints(Modifier.fillMaxSize()) {
                        val twoColumns = maxWidth >= TwoColumnBreakpoint
                        if (twoColumns) {
                            Row(
                                Modifier
                                    .fillMaxSize()
                                    .padding(16.dp),
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                            ) {
                                Column(
                                    Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(16.dp),
                                ) {
                                    StatusCard(data)
                                    HealthCard(data)
                                }
                                Column(Modifier.weight(1f)) {
                                    TasksCard(data)
                                }
                            }
                        } else {
                            LazyColumn(
                                contentPadding = PaddingValues(16.dp),
                                verticalArrangement = Arrangement.spacedBy(16.dp),
                                modifier = Modifier.widthIn(max = ListMaxWidth + 32.dp),
                            ) {
                                item { StatusCard(data) }
                                item { HealthCard(data) }
                                item { TasksCard(data) }
                            }
                        }
                    }
                }
            }
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }
}

@Composable
private fun StatusCard(data: ProwlarrSystemStatusContent) {
    val status = data.status
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            SectionHeader(
                title = stringResource(R.string.arr_prowlarr_settings_status_section),
                modifier = Modifier.semantics { heading() },
            )
            LabelRow(
                label = stringResource(R.string.arr_prowlarr_settings_status_app),
                value = listOfNotNull(
                    status.appName,
                    status.version,
                ).joinToString(" "),
            )
            LabelRow(
                label = stringResource(R.string.arr_prowlarr_settings_status_instance),
                value = status.instanceName.orEmpty(),
            )
            LabelRow(
                label = stringResource(ArrUiR.string.arr_common_status_branch),
                value = status.branch.orEmpty(),
            )
            LabelRow(
                label = stringResource(R.string.arr_prowlarr_settings_status_runtime),
                value = listOfNotNull(
                    status.runtimeName,
                    status.runtimeVersion,
                ).joinToString(" "),
            )
            LabelRow(
                label = stringResource(ArrUiR.string.arr_common_status_os),
                value = listOfNotNull(
                    status.osName,
                    status.osVersion,
                ).joinToString(" "),
            )
            LabelRow(
                label = stringResource(ArrUiR.string.arr_common_status_section_database),
                value = listOfNotNull(
                    status.databaseType?.name?.lowercase(),
                    status.databaseVersion,
                ).joinToString(" "),
            )
            LabelRow(
                label = stringResource(R.string.arr_prowlarr_settings_status_auth),
                value = status.authentication?.name?.lowercase().orEmpty(),
            )
            LabelRow(
                label = stringResource(R.string.arr_prowlarr_settings_status_is_docker),
                value = if (status.isDocker) {
                    stringResource(ArrUiR.string.arr_common_yes)
                } else {
                    stringResource(ArrUiR.string.arr_common_no)
                },
            )
            ArrTime.parse(status.startTime)?.let { started ->
                LabelRow(
                    label = stringResource(ArrUiR.string.arr_common_status_start_time),
                    value = ArrTime.dateTimeShort(started),
                )
            }
        }
    }
}

@Composable
private fun HealthCard(data: ProwlarrSystemStatusContent) {
    if (data.health.isEmpty()) return
    val uriHandler = LocalUriHandler.current
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionHeader(
                title = stringResource(ArrUiR.string.arr_common_status_section_health),
                modifier = Modifier.semantics { heading() },
            )
            data.health.forEach { finding ->
                Column {
                    Text(
                        finding.message.orEmpty(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                    Text(
                        finding.source.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    finding.wikiUrl?.let { url ->
                        TextButton(onClick = { runCatching { uriHandler.openUri(url) } }) {
                            Text(stringResource(R.string.arr_prowlarr_settings_health_wiki))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TasksCard(data: ProwlarrSystemStatusContent) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionHeader(
                title = stringResource(ArrUiR.string.arr_common_status_section_tasks),
                modifier = Modifier.semantics { heading() },
            )
            if (data.tasks.isEmpty()) {
                Text(
                    stringResource(R.string.arr_prowlarr_settings_tasks_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            data.tasks.forEach { task ->
                Column {
                    Text(task.name.orEmpty(), style = MaterialTheme.typography.bodyLarge)
                    ArrTime.parse(task.lastExecution)?.let { last ->
                        LabelRow(
                            label = stringResource(R.string.arr_prowlarr_settings_task_last),
                            value = ArrTime.dateTimeShort(last),
                        )
                    }
                    ArrTime.parse(task.nextExecution)?.let { next ->
                        LabelRow(
                            label = stringResource(R.string.arr_prowlarr_settings_task_next),
                            value = ArrTime.dateTimeShort(next),
                        )
                    }
                }
            }
        }
    }
}

private val TwoColumnBreakpoint = 600.dp
