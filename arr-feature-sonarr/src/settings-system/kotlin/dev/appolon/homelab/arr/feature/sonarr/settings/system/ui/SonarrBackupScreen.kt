package dev.appolon.homelab.arr.feature.sonarr.settings.system.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.appolon.homelab.arr.api.sonarr.model.BackupResource
import dev.appolon.homelab.arr.api.sonarr.model.BackupType
import dev.appolon.homelab.arr.core.ArrError
import dev.appolon.homelab.arr.core.ArrTime
import dev.appolon.homelab.arr.core.Humanize
import dev.appolon.homelab.arr.core.ServerId
import dev.appolon.homelab.arr.feature.sonarr.R
import dev.appolon.homelab.arr.feature.sonarr.settings.system.SonarrSettingsSystemClients
import dev.appolon.homelab.arr.feature.sonarr.settings.system.vm.BackupNotice
import dev.appolon.homelab.arr.feature.sonarr.settings.system.vm.BackupScheduleDraft
import dev.appolon.homelab.arr.feature.sonarr.settings.system.vm.SonarrBackupContent
import dev.appolon.homelab.arr.feature.sonarr.settings.system.vm.SonarrBackupViewModel
import dev.appolon.homelab.arr.ui.lce.ArrLceBox
import dev.appolon.homelab.arr.ui.lce.ArrPullToRefreshBox
import dev.appolon.homelab.arr.ui.lce.LceState
import dev.appolon.homelab.arr.ui.shell.ArrScaffold
import dev.appolon.homelab.arr.ui.R as ArrUiR

/** Destination for the backup route. */
@Composable
fun SonarrBackupDestination(
    serverId: ServerId,
    clients: SonarrSettingsSystemClients,
    serverName: String? = null,
    onBack: (() -> Unit)? = null,
    onDownloadBackup: ((BackupResource) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val repository = remember(serverId, clients) { systemRepository(serverId, clients) }
    val vm: SonarrBackupViewModel =
        viewModel(key = "sonarr-system-backup-" + serverId.value) {
            SonarrBackupViewModel(repository)
        }
    val lce by vm.lce.collectAsState()
    val uiState by vm.state.collectAsState()
    val noticeMessage = uiState.notice?.let { notice -> backupNoticeText(notice) }

    BackupConfirmDialogs(
        state = uiState,
        onRestoreCancelled = vm::restoreCancelled,
        onRestoreConfirmed = vm::restoreConfirmed,
        onDeleteCancelled = vm::deleteCancelled,
        onDeleteConfirmed = vm::deleteConfirmed,
    )

    SonarrBackupScreen(
        lce = lce,
        serverName = serverName,
        onBack = onBack,
        onRefresh = vm::refresh,
        onCreate = vm::createBackup,
        onRestore = vm::requestRestore,
        onDelete = vm::requestDelete,
        onEditSchedule = vm::editSchedule,
        onSaveSchedule = vm::saveSchedule,
        noticeMessage = noticeMessage,
        writeError = uiState.writeError,
        onConsumeNotice = vm::consumeNotice,
        onConsumeWriteError = vm::consumeWriteError,
        onDownloadBackup = onDownloadBackup,
        modifier = modifier,
    )
}

@Composable
private fun backupNoticeText(notice: BackupNotice): String = when (notice) {
    BackupNotice.CREATED -> stringResource(ArrUiR.string.arr_common_backup_notice_created)
    BackupNotice.CREATED_QUEUED -> stringResource(ArrUiR.string.arr_common_backup_notice_created_queued)
    BackupNotice.RESTORED -> stringResource(R.string.arr_sonarr_backup_notice_restored)
    BackupNotice.DELETED -> stringResource(ArrUiR.string.arr_common_backup_notice_deleted)
    BackupNotice.DELETED_QUEUED -> stringResource(ArrUiR.string.arr_common_backup_notice_deleted_queued)
    BackupNotice.SCHEDULE_SAVED -> stringResource(ArrUiR.string.arr_common_system_notice_saved)
    BackupNotice.SCHEDULE_SAVED_QUEUED ->
        stringResource(ArrUiR.string.arr_common_system_notice_saved_queued)
}

/**
 * The backup page: the backup list with dates, sizes, download handling,
 * restore with confirmation, the create action, and the schedule card the
 * server's automatic backups follow.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SonarrBackupScreen(
    lce: LceState<SonarrBackupContent>,
    serverName: String?,
    onBack: (() -> Unit)?,
    onRefresh: () -> Unit,
    onCreate: () -> Unit,
    onRestore: (BackupResource) -> Unit,
    onDelete: (BackupResource) -> Unit,
    onEditSchedule: (transform: (BackupScheduleDraft) -> BackupScheduleDraft) -> Unit,
    onSaveSchedule: () -> Unit,
    noticeMessage: String?,
    writeError: ArrError?,
    onConsumeNotice: () -> Unit,
    onConsumeWriteError: () -> Unit,
    onDownloadBackup: ((BackupResource) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val content = (lce as? LceState.Content<SonarrBackupContent>)?.data
    val refreshing = content != null && (lce as LceState.Content<SonarrBackupContent>).refreshing

    SystemNoticeHost(
        noticeMessage = noticeMessage,
        writeError = writeError,
        snackbarHostState = snackbarHostState,
        onConsumeNotice = onConsumeNotice,
        onConsumeWriteError = onConsumeWriteError,
    )

    ArrScaffold(
        title = stringResource(ArrUiR.string.arr_common_backup_title),
        subtitle = serverName,
        onBack = onBack,
        actions = {
            IconButton(onClick = onRefresh) {
                Icon(
                    imageVector = Icons.Filled.Refresh,
                    contentDescription = stringResource(ArrUiR.string.arr_common_system_cd_refresh),
                )
            }
            IconButton(onClick = onCreate) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = stringResource(ArrUiR.string.arr_common_backup_cd_create),
                )
            }
        },
        modifier = modifier,
    ) { padding ->
        ArrPullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = onRefresh,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            Box(Modifier.fillMaxSize()) {
                ArrLceBox(
                    state = lce,
                    onRetry = onRefresh,
                    emptyTitle = stringResource(ArrUiR.string.arr_common_backup_empty_title),
                    emptyMessage = stringResource(R.string.arr_sonarr_backup_empty_message),
                    modifier = Modifier.fillMaxSize(),
                ) { data ->
                    LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.widthIn(max = SectionMaxWidth + 32.dp),
                    ) {
                        if (data.pendingActions > 0) {
                            item { SystemPendingSyncBanner(data.pendingActions) }
                        }
                        item { BackupScheduleCard(data, onEditSchedule, onSaveSchedule) }
                        items(data.backups, key = { it.id }, contentType = { "backup" }) { backup ->
                            BackupRow(backup, onRestore, onDelete, onDownloadBackup)
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
}

@Composable
private fun BackupScheduleCard(
    data: SonarrBackupContent,
    onEditSchedule: (transform: (BackupScheduleDraft) -> BackupScheduleDraft) -> Unit,
    onSaveSchedule: () -> Unit,
) {
    SystemCard {
        Column {
            SystemSectionHeader(stringResource(ArrUiR.string.arr_common_backup_section_schedule))
            SystemTextField(
                value = data.schedule.folder,
                label = stringResource(ArrUiR.string.arr_common_backup_folder),
                onValue = { value -> onEditSchedule { it.copy(folder = value) } },
                supporting = stringResource(ArrUiR.string.arr_common_backup_folder_support),
            )
            SystemTextField(
                value = data.schedule.intervalDays,
                label = stringResource(ArrUiR.string.arr_common_backup_interval),
                onValue = { value ->
                    onEditSchedule { it.copy(intervalDays = value.filter(Char::isDigit)) }
                },
                isError = data.scheduleError,
                errorMessage = stringResource(ArrUiR.string.arr_common_backup_interval_error),
            )
            SystemTextField(
                value = data.schedule.retentionDays,
                label = stringResource(ArrUiR.string.arr_common_backup_retention),
                onValue = { value ->
                    onEditSchedule { it.copy(retentionDays = value.filter(Char::isDigit)) }
                },
                isError = data.scheduleError,
                errorMessage = stringResource(ArrUiR.string.arr_common_backup_interval_error),
            )
            TextButton(
                onClick = onSaveSchedule,
                enabled = data.scheduleDirty && !data.scheduleError && !data.scheduleSaving,
            ) {
                Text(stringResource(ArrUiR.string.arr_common_save))
            }
        }
    }
}

@Composable
private fun BackupRow(
    backup: BackupResource,
    onRestore: (BackupResource) -> Unit,
    onDelete: (BackupResource) -> Unit,
    onDownloadBackup: ((BackupResource) -> Unit)?,
) {
    SystemCard {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = backup.name.orEmpty(),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Text(
                        text = backup.type.typeLabel() + " · " + backup.timeLabel() + " · " +
                            Humanize.bytes(backup.size),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (onDownloadBackup != null) {
                    IconButton(onClick = { onDownloadBackup(backup) }) {
                        Icon(
                            imageVector = Icons.Filled.CloudDownload,
                            contentDescription = stringResource(
                                ArrUiR.string.arr_common_backup_cd_download,
                                backup.name.orEmpty(),
                            ),
                        )
                    }
                }
                IconButton(onClick = { onRestore(backup) }) {
                    Icon(
                        imageVector = Icons.Filled.Restore,
                        contentDescription = stringResource(
                            ArrUiR.string.arr_common_backup_cd_restore,
                            backup.name.orEmpty(),
                        ),
                    )
                }
                IconButton(onClick = { onDelete(backup) }) {
                    Icon(
                        imageVector = Icons.Filled.Delete,
                        contentDescription = stringResource(
                            ArrUiR.string.arr_common_backup_cd_delete,
                            backup.name.orEmpty(),
                        ),
                    )
                }
            }
        }
    }
}

@Composable
private fun BackupType?.typeLabel(): String = when (this) {
    BackupType.SCHEDULED -> stringResource(ArrUiR.string.arr_common_backup_type_scheduled)
    BackupType.MANUAL -> stringResource(ArrUiR.string.arr_common_backup_type_manual)
    BackupType.UPDATE -> stringResource(ArrUiR.string.arr_common_backup_type_update)
    null -> ""
}

@Composable
private fun BackupResource.timeLabel(): String {
    val instant = ArrTime.parse(time) ?: return ""
    return ArrTime.dateTimeFull(instant)
}

/** Restore and delete confirmations hosted by the backup destination. */
@Composable
internal fun BackupConfirmDialogs(
    state: SonarrBackupViewModel.UiState,
    onRestoreCancelled: () -> Unit,
    onRestoreConfirmed: () -> Unit,
    onDeleteCancelled: () -> Unit,
    onDeleteConfirmed: () -> Unit,
) {
    state.confirmRestoreFor?.let { backup ->
        AlertDialog(
            onDismissRequest = onRestoreCancelled,
            title = { Text(stringResource(ArrUiR.string.arr_common_backup_restore_title)) },
            text = { Text(stringResource(R.string.arr_sonarr_backup_restore_message)) },
            confirmButton = {
                TextButton(onClick = onRestoreConfirmed) {
                    Text(stringResource(ArrUiR.string.arr_common_backup_cd_restore, backup.name.orEmpty()))
                }
            },
            dismissButton = {
                TextButton(onClick = onRestoreCancelled) {
                    Text(stringResource(ArrUiR.string.arr_common_cancel))
                }
            },
        )
    }
    state.confirmDeleteFor?.let { backup ->
        AlertDialog(
            onDismissRequest = onDeleteCancelled,
            title = { Text(stringResource(ArrUiR.string.arr_common_backup_delete_title)) },
            text = {
                Text(
                    stringResource(
                        ArrUiR.string.arr_common_backup_delete_message,
                        backup.name.orEmpty(),
                    ),
                )
            },
            confirmButton = {
                TextButton(onClick = onDeleteConfirmed) {
                    Text(stringResource(ArrUiR.string.arr_common_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = onDeleteCancelled) {
                    Text(stringResource(ArrUiR.string.arr_common_cancel))
                }
            },
        )
    }
}
