package dev.appolon.homelab.arr.feature.radarr.settings.system.ui

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
import androidx.compose.material.icons.filled.Description
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
import dev.appolon.homelab.arr.api.radarr.model.LogFileResource
import dev.appolon.homelab.arr.api.radarr.model.UpdateMechanism
import dev.appolon.homelab.arr.core.ArrError
import dev.appolon.homelab.arr.core.ArrTime
import dev.appolon.homelab.arr.core.ServerId
import dev.appolon.homelab.arr.feature.radarr.R
import dev.appolon.homelab.arr.feature.radarr.settings.system.RadarrSettingsSystemClients
import dev.appolon.homelab.arr.feature.radarr.settings.system.vm.RadarrUpdateRow
import dev.appolon.homelab.arr.feature.radarr.settings.system.vm.RadarrUpdatesContent
import dev.appolon.homelab.arr.feature.radarr.settings.system.vm.RadarrUpdatesViewModel
import dev.appolon.homelab.arr.feature.radarr.settings.system.vm.UpdatesNotice
import dev.appolon.homelab.arr.ui.lce.ArrLceBox
import dev.appolon.homelab.arr.ui.lce.ArrPullToRefreshBox
import dev.appolon.homelab.arr.ui.lce.LceState
import dev.appolon.homelab.arr.ui.lce.arrErrorMessage
import dev.appolon.homelab.arr.ui.shell.ArrScaffold

/** Destination for the updates route. */
@Composable
fun RadarrUpdatesDestination(
    serverId: ServerId,
    clients: RadarrSettingsSystemClients,
    serverName: String? = null,
    onBack: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val repository = remember(serverId, clients) { systemRepository(serverId, clients) }
    val vm: RadarrUpdatesViewModel =
        viewModel(key = "radarr-system-updates-" + serverId.value) {
            RadarrUpdatesViewModel(repository)
        }
    val lce by vm.lce.collectAsState()
    val uiState by vm.state.collectAsState()
    val noticeMessage = uiState.notice?.let { notice ->
        when (notice) {
            UpdatesNotice.CHECKED -> stringResource(R.string.arr_radarr_updates_notice_checking)
            UpdatesNotice.INSTALL_STARTED ->
                stringResource(R.string.arr_radarr_updates_notice_install_started)

            UpdatesNotice.INSTALL_QUEUED ->
                stringResource(R.string.arr_radarr_updates_notice_install_queued)
        }
    }

    UpdateLogDialog(
        state = uiState,
        onDismiss = vm::closeLogFile,
    )

    RadarrUpdatesScreen(
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
fun RadarrUpdatesScreen(
    lce: LceState<RadarrUpdatesContent>,
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
    val content = (lce as? LceState.Content<RadarrUpdatesContent>)?.data
    val refreshing = content != null && (lce as LceState.Content<RadarrUpdatesContent>).refreshing

    SystemNoticeHost(
        noticeMessage = noticeMessage,
        writeError = writeError,
        snackbarHostState = snackbarHostState,
        onConsumeNotice = onConsumeNotice,
        onConsumeWriteError = onConsumeWriteError,
    )

    ArrScaffold(
        title = stringResource(R.string.arr_radarr_updates_title),
        subtitle = serverName,
        onBack = onBack,
        actions = {
            IconButton(onClick = onCheck) {
                Icon(
                    imageVector = Icons.Filled.Refresh,
                    contentDescription = stringResource(R.string.arr_radarr_updates_cd_check),
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
                    emptyTitle = stringResource(R.string.arr_radarr_updates_empty_title),
                    emptyMessage = stringResource(R.string.arr_radarr_updates_empty_message),
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
                        item { CurrentVersionCard(data, onInstall) }
                        if (data.updates.none { it.installable }) {
                            item { UpToDateCard() }
                        }
                        items(
                            data.updates.filter { !it.isCurrent },
                            key = { it.update.id },
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
    data: RadarrUpdatesContent,
    onInstall: () -> Unit,
) {
    SystemCard {
        Column {
            SystemSectionHeader(stringResource(R.string.arr_radarr_updates_current_title))
            data.status?.let { status ->
                SystemValueRow(
                    label = stringResource(R.string.arr_radarr_status_version),
                    value = status.version ?: stringResource(R.string.arr_radarr_status_unknown),
                )
                SystemValueRow(
                    label = stringResource(R.string.arr_radarr_status_branch),
                    value = status.branch ?: stringResource(R.string.arr_radarr_status_unknown),
                )
                val mechanism = status.packageUpdateMechanism ?: UpdateMechanism.BUILT_IN
                if (mechanism == UpdateMechanism.BUILT_IN) {
                    Text(
                        text = stringResource(R.string.arr_radarr_updates_mechanism_builtin),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Text(
                        text = stringResource(R.string.arr_radarr_updates_mechanism_external, mechanism.toString()),
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
                            R.string.arr_radarr_updates_cd_install,
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
                text = stringResource(R.string.arr_radarr_updates_up_to_date),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(start = 12.dp),
            )
        }
    }
}

@Composable
private fun UpdateRowCard(
    row: RadarrUpdateRow,
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
                                R.string.arr_radarr_updates_installed_on,
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
                                R.string.arr_radarr_updates_cd_install,
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
private fun UpdateChangesList(row: RadarrUpdateRow) {
    val new = row.update.changes?.newFeatures.orEmpty()
    val fixed = row.update.changes?.fixed.orEmpty()
    if (new.isEmpty() && fixed.isEmpty()) return
    Column {
        if (new.isNotEmpty()) {
            Text(
                text = stringResource(R.string.arr_radarr_updates_changes_new),
                style = MaterialTheme.typography.labelLarge,
            )
            new.forEach { change ->
                Text(change, style = MaterialTheme.typography.bodySmall)
            }
        }
        if (fixed.isNotEmpty()) {
            Text(
                text = stringResource(R.string.arr_radarr_updates_changes_fixed),
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
            SystemSectionHeader(stringResource(R.string.arr_radarr_updates_section_logs))
            if (logFiles.isEmpty()) {
                Text(
                    text = stringResource(R.string.arr_radarr_updates_logs_empty),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                logFiles.forEach { file ->
                    SystemLinkRow(
                        title = file.filename.orEmpty(),
                        supporting = file.lastWriteTime
                            ?.let { ArrTime.parse(it) }
                            ?.let { ArrTime.dateTimeShort(it) },
                        onClick = { onOpenLogFile(file) },
                    )
                }
            }
        }
    }
}

/** Full-screen log viewer over the plain-text contents endpoint. */
@Composable
private fun UpdateLogDialog(
    state: RadarrUpdatesViewModel.UiState,
    onDismiss: () -> Unit,
) {
    val file = state.openLogFile ?: return
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(R.string.arr_radarr_updates_log_dialog_title) +
                    " " + (file.filename.orEmpty()),
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
                        text = stringResource(R.string.arr_radarr_updates_log_empty),
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
                Text(stringResource(R.string.arr_radarr_system_action_cancel))
            }
        },
    )
}

@Composable
private fun ArrError.displayText(): String = arrErrorMessage(this)
