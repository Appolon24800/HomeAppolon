package dev.appolon.homelab.arr.feature.bazarr.settings.ui

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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.automirrored.filled.RotateRight
import androidx.compose.material.icons.filled.SettingsBackupRestore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.appolon.homelab.arr.feature.bazarr.R
import dev.appolon.homelab.arr.feature.bazarr.episodes.ui.QueuedBadge
import dev.appolon.homelab.arr.feature.bazarr.settings.model.BazarrBackupFile
import dev.appolon.homelab.arr.feature.bazarr.settings.model.BazarrJobRow
import dev.appolon.homelab.arr.feature.bazarr.settings.model.BazarrStatusRow
import dev.appolon.homelab.arr.feature.bazarr.settings.vm.SystemMessage
import dev.appolon.homelab.arr.feature.bazarr.settings.vm.SystemTab
import dev.appolon.homelab.arr.feature.bazarr.settings.vm.SystemUiState
import dev.appolon.homelab.arr.ui.components.EmptyState
import dev.appolon.homelab.arr.ui.components.FilterChipRow
import dev.appolon.homelab.arr.ui.lce.ArrLceBox
import dev.appolon.homelab.arr.ui.lce.ArrPullToRefreshBox
import dev.appolon.homelab.arr.ui.lce.LceState
import dev.appolon.homelab.arr.ui.lce.arrErrorMessage
import dev.appolon.homelab.arr.ui.shell.ArrScaffold

/** Job queue actions the spec's force_start, move_top, and move_bottom allow. */
private const val JOB_ACTION_FORCE_START = "force_start"
private const val JOB_ACTION_MOVE_TOP = "move_top"
private const val JOB_ACTION_MOVE_BOTTOM = "move_bottom"

/**
 * System: status fields, the job queue with reorder and removal actions,
 * backup files with create, restore, and delete, and the log viewer with
 * level filtering and rotation.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun BazarrSystemScreen(
    state: SystemUiState,
    lce: LceState<SystemUiState>,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onTab: (SystemTab) -> Unit,
    onJobAction: (Int, String) -> Unit,
    onDeleteJob: (BazarrJobRow) -> Unit,
    onEmptyQueue: (String) -> Unit,
    onCreateBackup: () -> Unit,
    onRestoreBackup: (BazarrBackupFile) -> Unit,
    onDeleteBackup: (BazarrBackupFile) -> Unit,
    onRotateLogs: () -> Unit,
    onLogTypeFilter: (String?) -> Unit,
    onConsumeMessage: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val snackbar = remember { SnackbarHostState() }
    val messageText = state.message?.let { systemMessageText(it) }
    LaunchedEffect(messageText) {
        messageText?.let {
            snackbar.showSnackbar(it)
            onConsumeMessage()
        }
    }
    var confirmDeleteJob by remember { mutableStateOf<BazarrJobRow?>(null) }
    var confirmEmptyQueue by remember { mutableStateOf<String?>(null) }
    var confirmRestore by remember { mutableStateOf<BazarrBackupFile?>(null) }
    var confirmDeleteBackup by remember { mutableStateOf<BazarrBackupFile?>(null) }
    var confirmRotate by remember { mutableStateOf(false) }

    confirmDeleteJob?.let { job ->
        AlertDialog(
            onDismissRequest = { confirmDeleteJob = null },
            title = {
                Text(stringResource(R.string.arr_bazarr_settings_system_job_confirm_delete_title))
            },
            text = {
                Text(
                    stringResource(
                        R.string.arr_bazarr_settings_system_job_confirm_delete_message,
                    ),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDeleteJob = null
                        onDeleteJob(job)
                    },
                ) {
                    Text(stringResource(R.string.arr_bazarr_settings_system_job_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDeleteJob = null }) {
                    Text(stringResource(R.string.arr_bazarr_action_cancel))
                }
            },
        )
    }
    confirmEmptyQueue?.let { queueName ->
        AlertDialog(
            onDismissRequest = { confirmEmptyQueue = null },
            title = {
                Text(
                    stringResource(R.string.arr_bazarr_settings_system_empty_queue_confirm_title),
                )
            },
            text = {
                Text(
                    stringResource(
                        R.string.arr_bazarr_settings_system_empty_queue_confirm_message,
                    ),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmEmptyQueue = null
                        onEmptyQueue(queueName)
                    },
                ) {
                    Text(stringResource(R.string.arr_bazarr_settings_system_job_empty_queue))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmEmptyQueue = null }) {
                    Text(stringResource(R.string.arr_bazarr_action_cancel))
                }
            },
        )
    }
    confirmRestore?.let { backup ->
        AlertDialog(
            onDismissRequest = { confirmRestore = null },
            title = {
                Text(
                    stringResource(
                        R.string.arr_bazarr_settings_system_backup_confirm_restore_title,
                    ),
                )
            },
            text = {
                Text(
                    stringResource(
                        R.string.arr_bazarr_settings_system_backup_confirm_restore_message,
                    ),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmRestore = null
                        onRestoreBackup(backup)
                    },
                ) {
                    Text(stringResource(R.string.arr_bazarr_settings_system_backup_restore))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmRestore = null }) {
                    Text(stringResource(R.string.arr_bazarr_action_cancel))
                }
            },
        )
    }
    confirmDeleteBackup?.let { backup ->
        AlertDialog(
            onDismissRequest = { confirmDeleteBackup = null },
            title = {
                Text(
                    stringResource(R.string.arr_bazarr_settings_system_backup_confirm_delete_title),
                )
            },
            text = {
                Text(
                    stringResource(
                        R.string.arr_bazarr_settings_system_backup_confirm_delete_message,
                    ),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDeleteBackup = null
                        onDeleteBackup(backup)
                    },
                ) {
                    Text(stringResource(R.string.arr_bazarr_settings_system_backup_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDeleteBackup = null }) {
                    Text(stringResource(R.string.arr_bazarr_action_cancel))
                }
            },
        )
    }
    if (confirmRotate) {
        AlertDialog(
            onDismissRequest = { confirmRotate = false },
            title = {
                Text(stringResource(R.string.arr_bazarr_settings_system_logs_rotate_confirm_title))
            },
            text = {
                Text(
                    stringResource(R.string.arr_bazarr_settings_system_logs_rotate_confirm_message),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmRotate = false
                        onRotateLogs()
                    },
                ) {
                    Text(stringResource(R.string.arr_bazarr_settings_system_logs_rotate))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmRotate = false }) {
                    Text(stringResource(R.string.arr_bazarr_action_cancel))
                }
            },
        )
    }

    ArrScaffold(
        title = stringResource(R.string.arr_bazarr_settings_home_system_title),
        onBack = onBack,
        actions = {
            if (state.tab == SystemTab.BACKUPS) {
                val cd = stringResource(R.string.arr_bazarr_settings_system_backup_create)
                IconButton(
                    onClick = onCreateBackup,
                    modifier = Modifier.semantics { contentDescription = cd },
                ) {
                    Icon(Icons.Filled.Add, contentDescription = cd)
                }
            }
            if (state.tab == SystemTab.LOGS) {
                val cd = stringResource(R.string.arr_bazarr_settings_system_logs_rotate)
                IconButton(
                    onClick = { confirmRotate = true },
                    modifier = Modifier.semantics { contentDescription = cd },
                ) {
                    Icon(Icons.AutoMirrored.Filled.RotateRight, contentDescription = cd)
                }
            }
            val refreshCd = stringResource(R.string.arr_bazarr_cd_refresh)
            IconButton(
                onClick = onRefresh,
                modifier = Modifier.semantics { contentDescription = refreshCd },
            ) {
                Icon(Icons.Filled.Refresh, contentDescription = refreshCd)
            }
        },
        filterBar = {
            PrimaryTabRow(selectedTabIndex = state.tab.ordinal) {
                SystemTab.entries.forEach { tab ->
                    Tab(
                        selected = state.tab == tab,
                        onClick = { onTab(tab) },
                        text = { Text(systemTabLabel(tab)) },
                    )
                }
            }
        },
        modifier = modifier,
    ) { padding ->
        BoxWithConstraints(modifier = Modifier.fillMaxSize().padding(padding)) {
            val contentWidth = if (maxWidth >= 840.dp) 720.dp else maxWidth
            ArrLceBox(
                state = lce,
                onRetry = onRefresh,
                emptyTitle = stringResource(R.string.arr_bazarr_settings_system_empty_title),
                emptyMessage = stringResource(
                    R.string.arr_bazarr_settings_system_status_empty_message,
                ),
                modifier = Modifier.fillMaxSize(),
            ) { _ ->
                ArrPullToRefreshBox(
                    isRefreshing = state.refreshing,
                    onRefresh = onRefresh,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    when (state.tab) {
                        SystemTab.STATUS -> SystemStatusList(
                            rows = state.statusRows,
                            contentWidth = contentWidth,
                        )

                        SystemTab.JOBS -> SystemJobsList(
                            state = state,
                            onJobAction = onJobAction,
                            onDeleteJob = { confirmDeleteJob = it },
                            onEmptyQueue = { confirmEmptyQueue = it },
                            contentWidth = contentWidth,
                        )

                        SystemTab.BACKUPS -> SystemBackupsList(
                            state = state,
                            onRestore = { confirmRestore = it },
                            onDelete = { confirmDeleteBackup = it },
                            contentWidth = contentWidth,
                        )

                        SystemTab.LOGS -> SystemLogsList(
                            state = state,
                            onLogTypeFilter = onLogTypeFilter,
                            contentWidth = contentWidth,
                        )
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
private fun systemTabLabel(tab: SystemTab): String = when (tab) {
    SystemTab.STATUS -> stringResource(R.string.arr_bazarr_settings_system_tab_status)
    SystemTab.JOBS -> stringResource(R.string.arr_bazarr_settings_system_tab_jobs)
    SystemTab.BACKUPS -> stringResource(R.string.arr_bazarr_settings_system_tab_backups)
    SystemTab.LOGS -> stringResource(R.string.arr_bazarr_settings_system_tab_logs)
}

@Composable
private fun SystemStatusList(
    rows: List<BazarrStatusRow>,
    contentWidth: Dp,
) {
    if (rows.isEmpty()) {
        TabEmptyState(R.string.arr_bazarr_settings_system_status_empty_message)
        return
    }
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxSize(),
    ) {
        items(rows, key = { it.key }) { row ->
            ListItem(
                headlineContent = {
                    Text(text = row.key, style = MaterialTheme.typography.bodyMedium)
                },
                supportingContent = {
                    Text(
                        text = row.value,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
                modifier = Modifier.fillMaxWidth().widthIn(max = contentWidth),
            )
        }
    }
}

@Composable
private fun SystemJobsList(
    state: SystemUiState,
    onJobAction: (Int, String) -> Unit,
    onDeleteJob: (BazarrJobRow) -> Unit,
    onEmptyQueue: (String) -> Unit,
    contentWidth: Dp,
) {
    if (state.jobs.isEmpty()) {
        TabEmptyState(R.string.arr_bazarr_settings_system_jobs_empty_message)
        return
    }
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxSize(),
    ) {
        items(state.jobs, key = { it.id }) { job ->
            JobRow(
                job = job,
                busy = job.id in state.busyJobIds,
                queued = job.id in state.queuedJobIds,
                onJobAction = onJobAction,
                onDeleteJob = onDeleteJob,
                onEmptyQueue = onEmptyQueue,
                modifier = Modifier.fillMaxWidth().widthIn(max = contentWidth),
            )
        }
    }
}

@Composable
private fun JobRow(
    job: BazarrJobRow,
    busy: Boolean,
    queued: Boolean,
    onJobAction: (Int, String) -> Unit,
    onDeleteJob: (BazarrJobRow) -> Unit,
    onEmptyQueue: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Card(modifier = modifier) {
        ListItem(
            headlineContent = {
                Text(
                    text = job.title.ifBlank { job.jobName.ifBlank { "#${job.id}" } },
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                )
            },
            supportingContent = {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    if (job.queueName.isNotBlank()) {
                        Text(
                            text = job.queueName,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (job.status.isNotBlank()) {
                            Text(
                                text = job.status,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (job.progressRemaining.isNotBlank()) {
                            Text(
                                text = job.progressRemaining,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            },
            trailingContent = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (queued) QueuedBadge()
                    if (busy) {
                        val busyCd = stringResource(R.string.arr_bazarr_cd_loading)
                        CircularProgressIndicator(
                            modifier = Modifier
                                .padding(4.dp)
                                .size(24.dp)
                                .semantics { contentDescription = busyCd },
                            strokeWidth = 2.dp,
                        )
                    } else {
                        val cd = stringResource(R.string.arr_bazarr_settings_system_job_cd_actions)
                        Box {
                            IconButton(
                                onClick = { menuOpen = true },
                                modifier = Modifier.semantics { contentDescription = cd },
                            ) {
                                Icon(Icons.Filled.MoreVert, contentDescription = cd)
                            }
                            DropdownMenu(
                                expanded = menuOpen,
                                onDismissRequest = { menuOpen = false },
                            ) {
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            stringResource(
                                                R.string.arr_bazarr_settings_system_job_force_start,
                                            ),
                                        )
                                    },
                                    onClick = {
                                        menuOpen = false
                                        onJobAction(job.id, JOB_ACTION_FORCE_START)
                                    },
                                )
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            stringResource(
                                                R.string.arr_bazarr_settings_system_job_move_top,
                                            ),
                                        )
                                    },
                                    onClick = {
                                        menuOpen = false
                                        onJobAction(job.id, JOB_ACTION_MOVE_TOP)
                                    },
                                )
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            stringResource(
                                                R.string.arr_bazarr_settings_system_job_move_bottom,
                                            ),
                                        )
                                    },
                                    onClick = {
                                        menuOpen = false
                                        onJobAction(job.id, JOB_ACTION_MOVE_BOTTOM)
                                    },
                                )
                                if (job.queueName.isNotBlank()) {
                                    val emptyQueueLabel = stringResource(
                                        R.string.arr_bazarr_settings_system_job_empty_queue,
                                    )
                                    DropdownMenuItem(
                                        text = { Text(emptyQueueLabel) },
                                        onClick = {
                                            menuOpen = false
                                            onEmptyQueue(job.queueName)
                                        },
                                    )
                                }
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            stringResource(
                                                R.string.arr_bazarr_settings_system_job_delete,
                                            ),
                                        )
                                    },
                                    onClick = {
                                        menuOpen = false
                                        onDeleteJob(job)
                                    },
                                )
                            }
                        }
                    }
                }
            },
        )
    }
}

@Composable
private fun SystemBackupsList(
    state: SystemUiState,
    onRestore: (BazarrBackupFile) -> Unit,
    onDelete: (BazarrBackupFile) -> Unit,
    contentWidth: Dp,
) {
    if (state.backups.isEmpty()) {
        TabEmptyState(R.string.arr_bazarr_settings_system_backups_empty_message)
        return
    }
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxSize(),
    ) {
        items(state.backups, key = { it.filename }) { backup ->
            BackupRow(
                backup = backup,
                queued = backup.filename in state.queuedBackupFiles,
                onRestore = { onRestore(backup) },
                onDelete = { onDelete(backup) },
                modifier = Modifier.fillMaxWidth().widthIn(max = contentWidth),
            )
        }
    }
}

@Composable
private fun BackupRow(
    backup: BazarrBackupFile,
    queued: Boolean,
    onRestore: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier) {
        ListItem(
            headlineContent = {
                Text(
                    text = backup.filename,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                )
            },
            supportingContent = {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (backup.type.isNotBlank()) {
                            Text(
                                text = backup.type,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                        if (backup.size.isNotBlank()) {
                            Text(
                                text = backup.size,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    if (backup.date.isNotBlank()) {
                        Text(
                            text = backup.date,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            },
            trailingContent = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (queued) QueuedBadge()
                    val restoreCd = stringResource(
                        R.string.arr_bazarr_settings_system_backup_cd_restore,
                        backup.filename,
                    )
                    IconButton(
                        onClick = onRestore,
                        modifier = Modifier.semantics { contentDescription = restoreCd },
                    ) {
                        Icon(Icons.Filled.SettingsBackupRestore, contentDescription = restoreCd)
                    }
                    val deleteCd = stringResource(
                        R.string.arr_bazarr_settings_system_backup_cd_delete,
                        backup.filename,
                    )
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.semantics { contentDescription = deleteCd },
                    ) {
                        Icon(Icons.Filled.Delete, contentDescription = deleteCd)
                    }
                }
            },
        )
    }
}

@Composable
private fun SystemLogsList(
    state: SystemUiState,
    onLogTypeFilter: (String?) -> Unit,
    contentWidth: Dp,
) {
    if (state.logs.isEmpty()) {
        TabEmptyState(R.string.arr_bazarr_settings_system_logs_empty_message)
        return
    }
    val allLabel = stringResource(R.string.arr_bazarr_settings_system_logs_filter_all)
    LazyColumn(
        contentPadding = PaddingValues(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxSize(),
    ) {
        item(key = "log_filter") {
            FilterChipRow(
                filters = listOf<String?>(null) + state.logTypes,
                selected = { it == state.logTypeFilter },
                label = { type -> type ?: allLabel },
                onSelect = onLogTypeFilter,
                modifier = Modifier.fillMaxWidth().widthIn(max = contentWidth),
            )
        }
        if (state.presentedLogs.isEmpty()) {
            item(key = "log_filtered_empty") {
                TabEmptyState(R.string.arr_bazarr_settings_system_logs_empty_message)
            }
        } else {
            items(state.presentedLogs) { entry ->
                ListItem(
                    headlineContent = {
                        Text(
                            text = entry.message,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 4,
                        )
                    },
                    supportingContent = {
                        val meta = listOf(entry.type, entry.name)
                            .filter { it.isNotBlank() }
                            .joinToString(" · ")
                        if (meta.isNotBlank()) {
                            Text(
                                text = meta,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth().widthIn(max = contentWidth),
                )
            }
        }
    }
}

/** Per-tab empty pane for tabs whose feed is empty while others carry data. */
@Composable
private fun TabEmptyState(messageRes: Int) {
    EmptyState(
        title = stringResource(R.string.arr_bazarr_settings_system_empty_title),
        message = stringResource(messageRes),
        modifier = Modifier.fillMaxSize(),
    )
}

/** Snackbar text for every system write outcome. */
@Composable
internal fun systemMessageText(message: SystemMessage): String = when (message) {
    SystemMessage.JobUpdatedSynced ->
        stringResource(R.string.arr_bazarr_settings_system_job_updated_synced)

    SystemMessage.JobUpdatedQueued ->
        stringResource(R.string.arr_bazarr_settings_system_job_updated_queued)

    SystemMessage.JobDeletedSynced ->
        stringResource(R.string.arr_bazarr_settings_system_job_deleted_synced)

    SystemMessage.JobDeletedQueued ->
        stringResource(R.string.arr_bazarr_settings_system_job_deleted_queued)

    SystemMessage.QueueEmptiedSynced ->
        stringResource(R.string.arr_bazarr_settings_system_queue_emptied_synced)

    SystemMessage.QueueEmptiedQueued ->
        stringResource(R.string.arr_bazarr_settings_system_queue_emptied_queued)

    SystemMessage.BackupCreatedSynced ->
        stringResource(R.string.arr_bazarr_settings_system_backup_created_synced)

    SystemMessage.BackupCreatedQueued ->
        stringResource(R.string.arr_bazarr_settings_system_backup_created_queued)

    SystemMessage.BackupRestoredSynced ->
        stringResource(R.string.arr_bazarr_settings_system_backup_restored_synced)

    SystemMessage.BackupRestoredQueued ->
        stringResource(R.string.arr_bazarr_settings_system_backup_restored_queued)

    SystemMessage.BackupDeletedSynced ->
        stringResource(R.string.arr_bazarr_settings_system_backup_deleted_synced)

    SystemMessage.BackupDeletedQueued ->
        stringResource(R.string.arr_bazarr_settings_system_backup_deleted_queued)

    SystemMessage.LogsRotatedSynced ->
        stringResource(R.string.arr_bazarr_settings_system_logs_rotated_synced)

    SystemMessage.LogsRotatedQueued ->
        stringResource(R.string.arr_bazarr_settings_system_logs_rotated_queued)

    is SystemMessage.WriteFailed -> arrErrorMessage(message.error)
}
