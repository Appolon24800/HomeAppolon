package dev.appolon.homelab.arr.feature.lidarr.settings.system.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SystemUpdateAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.appolon.homelab.arr.api.lidarr.model.LogFileResource
import dev.appolon.homelab.arr.api.lidarr.model.UpdateMechanism
import dev.appolon.homelab.arr.core.ArrError
import dev.appolon.homelab.arr.core.ArrTime
import dev.appolon.homelab.arr.core.ServerId
import dev.appolon.homelab.arr.feature.lidarr.R
import dev.appolon.homelab.arr.feature.lidarr.settings.system.LidarrSettingsSystemClients
import dev.appolon.homelab.arr.feature.lidarr.settings.system.vm.LidarrUpdateRow
import dev.appolon.homelab.arr.feature.lidarr.settings.system.vm.LidarrUpdatesContent
import dev.appolon.homelab.arr.feature.lidarr.settings.system.vm.LidarrUpdatesViewModel
import dev.appolon.homelab.arr.feature.lidarr.settings.system.vm.UpdatesNotice
import dev.appolon.homelab.arr.ui.lce.ArrLceBox
import dev.appolon.homelab.arr.ui.lce.ArrPullToRefreshBox
import dev.appolon.homelab.arr.ui.lce.LceState
import dev.appolon.homelab.arr.ui.lce.arrErrorMessage
import dev.appolon.homelab.arr.ui.shell.ArrScaffold
import dev.appolon.homelab.arr.ui.R as ArrUiR

/** Destination for the updates route. */
@Composable
fun LidarrUpdatesDestination(
    serverId: ServerId,
    clients: LidarrSettingsSystemClients,
    serverName: String? = null,
    onBack: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val repository = remember(serverId, clients) { systemRepository(serverId, clients) }
    val vm: LidarrUpdatesViewModel =
        viewModel(key = "lidarr-system-updates-" + serverId.value) {
            LidarrUpdatesViewModel(repository)
        }
    val lce by vm.lce.collectAsState()
    val uiState by vm.state.collectAsState()
    val noticeMessage = uiState.notice?.let { notice ->
        when (notice) {
            UpdatesNotice.CHECKED -> stringResource(ArrUiR.string.arr_common_updates_notice_checking)
            UpdatesNotice.INSTALL_STARTED ->
                stringResource(R.string.arr_lidarr_updates_notice_install_started)

            UpdatesNotice.INSTALL_QUEUED ->
                stringResource(ArrUiR.string.arr_common_updates_notice_install_queued)
        }
    }

    UpdateLogDialog(
        state = uiState,
        onDismiss = vm::closeLogFile,
    )

    LidarrUpdatesScreen(
        lce = lce,
        serverName = serverName,
        onBack = onBack,
        onRefresh = vm::refresh,
        onCheck = vm::checkForUpdates,
        onInstall = vm::install,
        onOpenLogFile = vm::openLogFile,
        noticeMessage = noticeMessage,
        writeError = uiState.writeError,
        onConsumeNotice = vm::consumeNotice,
        onConsumeWriteError = vm::consumeWriteError,
        modifier = modifier,
    )
}

/**
 * The updates page: the current version card, the available update list
 * with an install trigger, and the update log files viewer.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LidarrUpdatesScreen(
    lce: LceState<LidarrUpdatesContent>,
    serverName: String?,
    onBack: (() -> Unit)?,
    onRefresh: () -> Unit,
    onCheck: () -> Unit,
    onInstall: () -> Unit,
    onOpenLogFile: (LogFileResource) -> Unit,
    noticeMessage: String?,
    writeError: ArrError?,
    onConsumeNotice: () -> Unit,
    onConsumeWriteError: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val content = (lce as? LceState.Content<LidarrUpdatesContent>)?.data
    val refreshing = content != null && (lce as LceState.Content<LidarrUpdatesContent>).refreshing

    SystemNoticeHost(
        noticeMessage = noticeMessage,
        writeError = writeError,
        snackbarHostState = snackbarHostState,
        onConsumeNotice = onConsumeNotice,
        onConsumeWriteError = onConsumeWriteError,
    )

    ArrScaffold(
        title = stringResource(ArrUiR.string.arr_common_updates_title),
        subtitle = serverName,
        onBack = onBack,
        actions = {
            IconButton(onClick = onCheck) {
                Icon(
                    imageVector = Icons.Filled.Refresh,
                    contentDescription = stringResource(ArrUiR.string.arr_common_updates_cd_check),
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
                    emptyTitle = stringResource(ArrUiR.string.arr_common_updates_empty_title),
                    emptyMessage = stringResource(ArrUiR.string.arr_common_updates_empty_message),
                    modifier = Modifier.fillMaxSize(),
                ) { data ->
                    val installableUpdates = remember(data.updates) {
                        data.updates.filter { !it.isCurrent }
                    }
                    LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.widthIn(max = SectionMaxWidth + 32.dp),
                    ) {
                        if (data.pendingActions > 0) {
                            item { SystemPendingSyncBanner(data.pendingActions) }
                        }
                        item { CurrentVersionCard(data, onInstall) }
                        if (data.updates.none { it.installable }) {
                            item { UpToDateCard() }
                        }
                        items(
                            installableUpdates,
                            key = { it.update.id },
                            contentType = { "update" },
                        ) { row ->
                            UpdateRowCard(row, onInstall)
                        }
                        item { UpdateLogsSection(data.logFiles, onOpenLogFile) }
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
private fun CurrentVersionCard(
    data: LidarrUpdatesContent,
    onInstall: () -> Unit,
) {
    SystemCard {
        Column {
            SystemSectionHeader(stringResource(ArrUiR.string.arr_common_updates_current_title))
            data.status?.let { status ->
                SystemValueRow(
                    label = stringResource(ArrUiR.string.arr_common_status_version),
                    value = status.version ?: stringResource(ArrUiR.string.arr_common_status_unknown),
                )
                SystemValueRow(
                    label = stringResource(ArrUiR.string.arr_common_status_branch),
                    value = status.branch ?: stringResource(ArrUiR.string.arr_common_status_unknown),
                )
                val mechanism = status.packageUpdateMechanism
                if (mechanism == UpdateMechanism.BUILT_IN) {
                    Text(
                        text = stringResource(R.string.arr_lidarr_updates_mechanism_builtin),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Text(
                        text = stringResource(R.string.arr_lidarr_updates_mechanism_external, mechanism.toString()),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (data.updates.any { it.installable } && !data.installing) {
                OutlinedButton(onClick = onInstall) {
                    Icon(
                        imageVector = Icons.Filled.SystemUpdateAlt,
                        contentDescription = null,
                        modifier = Modifier.heightIn(max = 20.dp),
                    )
                    Text(
                        text = stringResource(
                            ArrUiR.string.arr_common_updates_cd_install,
                            data.updates.first { it.installable }.update.version ?: "",
                        ),
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }
            if (data.installing) {
                CircularProgressIndicator(Modifier.heightIn(max = 24.dp))
            }
        }
    }
}

@Composable
private fun UpToDateCard() {
    SystemCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Filled.DownloadDone,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = stringResource(R.string.arr_lidarr_updates_up_to_date),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(start = 12.dp),
            )
        }
    }
}

@Composable
private fun UpdateRowCard(
    row: LidarrUpdateRow,
    onInstall: () -> Unit,
) {
    SystemCard {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = row.update.version.orEmpty(),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    val installed = row.update.installedOn?.let { ArrTime.parse(it) }
                    if (installed != null) {
                        Text(
                            text = stringResource(
                                ArrUiR.string.arr_common_updates_installed_on,
                                ArrTime.dateTimeShort(installed),
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (row.installable) {
                    IconButton(onClick = onInstall) {
                        Icon(
                            imageVector = Icons.Filled.SystemUpdateAlt,
                            contentDescription = stringResource(
                                ArrUiR.string.arr_common_updates_cd_install,
                                row.update.version ?: "",
                            ),
                        )
                    }
                }
            }
            UpdateChangesList(row)
        }
    }
}

@Composable
private fun UpdateChangesList(row: LidarrUpdateRow) {
    val new = row.update.changes.new.orEmpty()
    val fixed = row.update.changes.fixed.orEmpty()
    if (new.isEmpty() && fixed.isEmpty()) return
    Column {
        if (new.isNotEmpty()) {
            Text(
                text = stringResource(ArrUiR.string.arr_common_updates_changes_new),
                style = MaterialTheme.typography.labelLarge,
            )
            new.forEach { change ->
                Text(change, style = MaterialTheme.typography.bodySmall)
            }
        }
        if (fixed.isNotEmpty()) {
            Text(
                text = stringResource(ArrUiR.string.arr_common_updates_changes_fixed),
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(top = 8.dp),
            )
            fixed.forEach { change ->
                Text(change, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun UpdateLogsSection(
    logFiles: List<LogFileResource>,
    onOpenLogFile: (LogFileResource) -> Unit,
) {
    SystemCard {
        Column {
            SystemSectionHeader(stringResource(ArrUiR.string.arr_common_updates_section_logs))
            if (logFiles.isEmpty()) {
                Text(
                    text = stringResource(ArrUiR.string.arr_common_updates_logs_empty),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                logFiles.forEach { file ->
                    SystemLinkRow(
                        title = file.filename.orEmpty(),
                        supporting = file.lastWriteTime
                            .let { ArrTime.parse(it) }
                            ?.let { ArrTime.dateTimeShort(it) },
                        onClick = { onOpenLogFile(file) },
                    )
                }
            }
        }
    }
}

/**
 * Full-screen log viewer over the update log contents endpoint. The spec
 * leaves the body untyped, so whatever the server returns renders as
 * monospace text.
 */
@Composable
private fun UpdateLogDialog(
    state: LidarrUpdatesViewModel.UiState,
    onDismiss: () -> Unit,
) {
    val file = state.openLogFile ?: return
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(
                    ArrUiR.string.arr_common_update_log_file,
                    file.filename.orEmpty(),
                ),
            )
        },
        text = {
            Column {
                when {
                    state.logLoading -> CircularProgressIndicator()

                    state.logError != null -> Text(
                        text = state.logError.displayText(),
                        color = MaterialTheme.colorScheme.error,
                    )

                    state.logContents.isNullOrEmpty() -> Text(
                        text = stringResource(ArrUiR.string.arr_common_updates_log_empty),
                    )

                    else -> Text(
                        text = state.logContents,
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(ArrUiR.string.arr_common_cancel))
            }
        },
    )
}

@Composable
private fun ArrError.displayText(): String = arrErrorMessage(this)
