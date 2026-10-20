package dev.appolon.homelab.arr.feature.bazarr.settings.ui

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
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
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
import dev.appolon.homelab.arr.feature.bazarr.episodes.ui.QueuedBadge
import dev.appolon.homelab.arr.feature.bazarr.settings.model.BazarrScheduledTask
import dev.appolon.homelab.arr.feature.bazarr.settings.vm.SchedulerMessage
import dev.appolon.homelab.arr.feature.bazarr.settings.vm.SchedulerUiState
import dev.appolon.homelab.arr.ui.lce.ArrLceBox
import dev.appolon.homelab.arr.ui.lce.ArrPullToRefreshBox
import dev.appolon.homelab.arr.ui.lce.LceState
import dev.appolon.homelab.arr.ui.lce.arrErrorMessage
import dev.appolon.homelab.arr.ui.shell.ArrScaffold
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Scheduler: one row per task with its interval and next run time, and a
 * run-now action per row. Interval editing is not part of the bundled spec,
 * so intervals display as the server reports them.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun BazarrSchedulerScreen(
    state: SchedulerUiState,
    lce: LceState<SchedulerUiState>,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onRunNow: (BazarrScheduledTask) -> Unit,
    onConsumeMessage: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val snackbar = remember { SnackbarHostState() }
    val messageText = state.message?.let { schedulerMessageText(it) }
    LaunchedEffect(messageText) {
        messageText?.let {
            snackbar.showSnackbar(it)
            onConsumeMessage()
        }
    }

    ArrScaffold(
        title = stringResource(R.string.arr_bazarr_settings_home_scheduler_title),
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
        modifier = modifier,
    ) { padding ->
        BoxWithConstraints(modifier = Modifier.fillMaxSize().padding(padding)) {
            val contentWidth = if (maxWidth >= 840.dp) 720.dp else maxWidth
            ArrLceBox(
                state = lce,
                onRetry = onRefresh,
                emptyTitle = stringResource(R.string.arr_bazarr_settings_scheduler_empty_title),
                emptyMessage =
                    stringResource(R.string.arr_bazarr_settings_scheduler_empty_message),
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
                        items(state.tasks, key = { it.jobId }) { task ->
                            TaskRow(
                                task = task,
                                busy = task.jobId in state.busyTaskIds,
                                queued = task.jobId in state.queuedTaskIds,
                                onRunNow = { onRunNow(task) },
                                modifier = Modifier.fillMaxWidth().widthIn(max = contentWidth),
                            )
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
private fun TaskRow(
    task: BazarrScheduledTask,
    busy: Boolean,
    queued: Boolean,
    onRunNow: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier) {
        ListItem(
            headlineContent = {
                Text(text = task.label, style = MaterialTheme.typography.titleSmall)
            },
            supportingContent = {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    if (task.intervalLabel.isNotBlank()) {
                        Text(
                            text = stringResource(
                                R.string.arr_bazarr_settings_scheduler_interval,
                                task.intervalLabel,
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        text = stringResource(
                            R.string.arr_bazarr_settings_scheduler_next_run,
                            formatNextRun(task.nextRunMillis),
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (task.running) {
                        Text(
                            text = stringResource(R.string.arr_bazarr_settings_scheduler_running),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            },
            trailingContent = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (queued) QueuedBadge()
                    if (busy) {
                        CircularProgressIndicator(
                            modifier = Modifier.padding(4.dp).size(24.dp),
                            strokeWidth = 2.dp,
                        )
                    } else {
                        val cd = stringResource(
                            R.string.arr_bazarr_settings_scheduler_cd_run,
                            task.label,
                        )
                        IconButton(
                            onClick = onRunNow,
                            modifier = Modifier.semantics { contentDescription = cd },
                        ) {
                            Icon(Icons.Filled.PlayArrow, contentDescription = cd)
                        }
                    }
                }
            },
        )
    }
}

/** Snackbar text for every run-now outcome. */
@Composable
internal fun schedulerMessageText(message: SchedulerMessage): String = when (message) {
    SchedulerMessage.RunSynced ->
        stringResource(R.string.arr_bazarr_settings_scheduler_run_synced)

    SchedulerMessage.RunQueued ->
        stringResource(R.string.arr_bazarr_settings_scheduler_run_queued)

    is SchedulerMessage.WriteFailed -> arrErrorMessage(message.error)
}

/** Local-time short timestamp; a zero next run renders as never. */
@Composable
private fun formatNextRun(millis: Long): String =
    if (millis <= 0) {
        stringResource(R.string.arr_bazarr_settings_scheduler_never)
    } else {
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
            .withZone(ZoneId.systemDefault())
            .format(Instant.ofEpochMilli(millis))
    }
