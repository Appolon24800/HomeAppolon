package dev.appolon.homelab.arr.feature.radarr.settings.connect.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SettingsEthernet
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.appolon.homelab.arr.api.radarr.model.DownloadClientConfigResource
import dev.appolon.homelab.arr.api.radarr.model.DownloadClientResource
import dev.appolon.homelab.arr.core.ServerId
import dev.appolon.homelab.arr.feature.radarr.R
import dev.appolon.homelab.arr.feature.radarr.settings.connect.RadarrConnectClients
import dev.appolon.homelab.arr.feature.radarr.settings.connect.vm.DownloadClientsNotice
import dev.appolon.homelab.arr.feature.radarr.settings.connect.vm.RadarrDownloadClientsContent
import dev.appolon.homelab.arr.feature.radarr.settings.connect.vm.RadarrDownloadClientsViewModel
import dev.appolon.homelab.arr.ui.components.LabeledSwitch
import dev.appolon.homelab.arr.ui.components.SectionHeader
import dev.appolon.homelab.arr.ui.lce.ArrLceBox
import dev.appolon.homelab.arr.ui.lce.ArrPullToRefreshBox
import dev.appolon.homelab.arr.ui.lce.LceState
import dev.appolon.homelab.arr.ui.shell.ArrScaffold
import dev.appolon.homelab.arr.ui.R as ArrUiR

/** Destination for the download clients route. */
@Composable
fun RadarrDownloadClientsDestination(
    serverId: ServerId,
    clients: RadarrConnectClients,
    serverName: String? = null,
    onBack: (() -> Unit)? = null,
    onOpenClient: (Int?) -> Unit,
    onOpenMappings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val repository = remember(serverId, clients) { connectRepository(serverId, clients) }
    val vm: RadarrDownloadClientsViewModel =
        viewModel(key = "radarr-downloadclients-" + serverId.value) {
            RadarrDownloadClientsViewModel(serverId, repository)
        }
    val state by vm.state.collectAsState()
    val lce by vm.lce.collectAsState()

    RadarrDownloadClientsScreen(
        state = state,
        lce = lce,
        serverName = serverName,
        onBack = onBack,
        onRefresh = vm::refresh,
        onOpen = onOpenClient,
        onAdd = { onOpenClient(null) },
        onOpenMappings = onOpenMappings,
        onTestAll = vm::testAll,
        onToggleEnabled = vm::setEnabled,
        onDeleteRequest = vm::requestDelete,
        onDeleteCancelled = vm::deleteCancelled,
        onDeleteConfirmed = vm::deleteConfirmed,
        onSaveOptions = vm::updateOptions,
        onConsumeNotice = vm::consumeNotice,
        onConsumeWriteError = vm::consumeWriteError,
        modifier = modifier,
    )
}

/**
 * Download clients list. Each row carries the enable toggle, protocol,
 * priority, and the per-client verdict of the last test-all run; the
 * options card edits completed-download handling; remote path mappings
 * open from the app bar.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RadarrDownloadClientsScreen(
    state: RadarrDownloadClientsViewModel.UiState,
    lce: LceState<RadarrDownloadClientsContent>,
    serverName: String?,
    onBack: (() -> Unit)?,
    onRefresh: () -> Unit,
    onOpen: (Int) -> Unit,
    onAdd: () -> Unit,
    onOpenMappings: () -> Unit,
    onTestAll: () -> Unit,
    onToggleEnabled: (DownloadClientResource, Boolean) -> Unit,
    onDeleteRequest: (DownloadClientResource) -> Unit,
    onDeleteCancelled: () -> Unit,
    onDeleteConfirmed: () -> Unit,
    onSaveOptions: (DownloadClientConfigResource) -> Unit,
    onConsumeNotice: () -> Unit,
    onConsumeWriteError: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    var showOptions by remember { mutableStateOf(false) }
    val content = (lce as? LceState.Content<RadarrDownloadClientsContent>)?.data
    val refreshing = content != null &&
        (lce as LceState.Content<RadarrDownloadClientsContent>).refreshing

    NoticeHost(
        noticeMessage = when (state.notice) {
            DownloadClientsNotice.DELETED ->
                stringResource(ArrUiR.string.arr_common_connect_notice_deleted)

            DownloadClientsNotice.DELETED_QUEUED ->
                stringResource(ArrUiR.string.arr_common_backup_notice_deleted_queued)

            DownloadClientsNotice.TOGGLE_QUEUED ->
                stringResource(ArrUiR.string.arr_common_system_notice_saved_queued)

            DownloadClientsNotice.TEST_ALL_DONE ->
                stringResource(ArrUiR.string.arr_common_connect_notice_test_all_done)

            DownloadClientsNotice.OPTIONS_SAVED ->
                stringResource(ArrUiR.string.arr_common_connect_notice_saved)

            DownloadClientsNotice.OPTIONS_SAVED_QUEUED ->
                stringResource(ArrUiR.string.arr_common_system_notice_saved_queued)

            null -> null
        },
        writeError = state.writeError,
        snackbarHostState = snackbarHostState,
        onConsumeNotice = onConsumeNotice,
        onConsumeWriteError = onConsumeWriteError,
    )

    ArrScaffold(
        title = stringResource(ArrUiR.string.arr_common_clients_title),
        subtitle = serverName,
        onBack = onBack,
        actions = {
            IconButton(onClick = onOpenMappings) {
                Icon(
                    imageVector = Icons.Filled.SettingsEthernet,
                    contentDescription = stringResource(ArrUiR.string.arr_common_clients_cd_mappings),
                )
            }
            IconButton(onClick = onTestAll, enabled = !state.testingAll && content != null) {
                Icon(
                    imageVector = Icons.Filled.PlayArrow,
                    contentDescription = stringResource(ArrUiR.string.arr_common_clients_cd_test_all),
                )
            }
            IconButton(onClick = { showOptions = true }) {
                Icon(
                    imageVector = Icons.Filled.Tune,
                    contentDescription = stringResource(ArrUiR.string.arr_common_clients_cd_options),
                )
            }
            IconButton(onClick = onRefresh) {
                Icon(
                    imageVector = Icons.Filled.Refresh,
                    contentDescription = stringResource(ArrUiR.string.arr_common_clients_cd_refresh),
                )
            }
        },
        modifier = modifier,
    ) { padding ->
        Box(Modifier.fillMaxSize()) {
            ArrPullToRefreshBox(
                isRefreshing = refreshing,
                onRefresh = onRefresh,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            ) {
                ArrLceBox(
                    state = lce,
                    onRetry = onRefresh,
                    emptyTitle = stringResource(ArrUiR.string.arr_common_clients_empty_title),
                    emptyMessage = stringResource(ArrUiR.string.arr_common_clients_empty_message),
                    modifier = Modifier.fillMaxSize(),
                ) { data ->
                    LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.widthIn(max = ListMaxWidth + 32.dp),
                    ) {
                        if (data.pendingActions > 0) {
                            item { PendingBanner(data.pendingActions) }
                        }
                        item {
                            SectionHeader(
                                title = stringResource(ArrUiR.string.arr_common_clients_title),
                            )
                        }
                        items(data.clients, key = { it.id }, contentType = { "client" }) { client ->
                            DownloadClientRow(
                                client = client,
                                tags = data.tags,
                                testResult = data.testResults[client.id],
                                queued = client.id in data.queuedRemovedIds,
                                onOpen = { onOpen(client.id) },
                                onToggleEnabled = { enabled -> onToggleEnabled(client, enabled) },
                                onDelete = { onDeleteRequest(client) },
                            )
                        }
                    }
                }
            }
            ExtendedFloatingActionButton(
                onClick = onAdd,
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text(stringResource(ArrUiR.string.arr_common_clients_add)) },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp),
            )
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }

    state.confirmDeleteFor?.let { target ->
        AlertDialog(
            onDismissRequest = onDeleteCancelled,
            title = { Text(stringResource(ArrUiR.string.arr_common_delete)) },
            text = {
                Text(
                    stringResource(
                        ArrUiR.string.arr_common_connect_delete_message,
                        target.name ?: target.id.toString(),
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

    if (showOptions) {
        DownloadClientOptionsDialog(
            initial = state.options ?: DownloadClientConfigResource(),
            busy = state.busy,
            onSave = {
                onSaveOptions(it)
                showOptions = false
            },
            onDismiss = { showOptions = false },
        )
    }
}

@Composable
private fun DownloadClientRow(
    client: DownloadClientResource,
    tags: List<dev.appolon.homelab.arr.api.radarr.model.TagResource>,
    testResult: dev.appolon.homelab.arr.api.radarr.model.ProviderTestAllResult?,
    queued: Boolean,
    onOpen: () -> Unit,
    onToggleEnabled: (Boolean) -> Unit,
    onDelete: () -> Unit,
) {
    val openCd = stringResource(ArrUiR.string.arr_common_clients_cd_open, client.name.orEmpty())
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClickLabel = openCd, onClick = onOpen),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 72.dp)
                .padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 8.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(client.name.orEmpty(), style = MaterialTheme.typography.bodyLarge)
                Text(
                    text = listOfNotNull(
                        client.implementationName,
                        stringResource(ArrUiR.string.arr_common_connect_priority, client.priority),
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (queued) ConnectQueuedBadge()
                    ConnectTagChips(client.tags.orEmpty().toList(), tags)
                }
                testResult?.let { TestResultBadge(it) }
            }
            val toggleCd = stringResource(
                ArrUiR.string.arr_common_clients_cd_toggle,
                client.name.orEmpty(),
            )
            LabeledSwitch(
                label = client.name.orEmpty(),
                checked = client.enable,
                onCheckedChange = onToggleEnabled,
                modifier = Modifier.semantics { contentDescription = toggleCd },
            )
            val deleteCd = stringResource(
                ArrUiR.string.arr_common_clients_cd_delete,
                client.name.orEmpty(),
            )
            IconButton(onClick = onDelete, modifier = Modifier.semantics { contentDescription = deleteCd }) {
                Icon(
                    imageVector = Icons.Filled.Delete,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

/** Completed download handling options edited in place. */
@Composable
private fun DownloadClientOptionsDialog(
    initial: DownloadClientConfigResource,
    busy: Boolean,
    onSave: (DownloadClientConfigResource) -> Unit,
    onDismiss: () -> Unit,
) {
    var workingFolders by remember { mutableStateOf(initial.downloadClientWorkingFolders.orEmpty()) }
    var enableCompleted by remember { mutableStateOf(initial.enableCompletedDownloadHandling) }
    var autoRedownloadFailed by remember { mutableStateOf(initial.autoRedownloadFailed) }
    var autoRedownloadInteractive by remember {
        mutableStateOf(initial.autoRedownloadFailedFromInteractiveSearch)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(ArrUiR.string.arr_common_clients_cd_options)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                androidx.compose.material3.OutlinedTextField(
                    value = workingFolders,
                    onValueChange = { workingFolders = it },
                    label = {
                        Text(stringResource(ArrUiR.string.arr_common_clients_option_working_folders))
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                LabeledSwitch(
                    label = stringResource(ArrUiR.string.arr_common_clients_option_completed),
                    checked = enableCompleted,
                    onCheckedChange = { enableCompleted = it },
                )
                LabeledSwitch(
                    label = stringResource(ArrUiR.string.arr_common_clients_option_redownload_failed),
                    checked = autoRedownloadFailed,
                    onCheckedChange = { autoRedownloadFailed = it },
                )
                LabeledSwitch(
                    label = stringResource(
                        ArrUiR.string.arr_common_clients_option_redownload_interactive,
                    ),
                    checked = autoRedownloadInteractive,
                    onCheckedChange = { autoRedownloadInteractive = it },
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(
                        initial.copy(
                            downloadClientWorkingFolders = workingFolders,
                            enableCompletedDownloadHandling = enableCompleted,
                            autoRedownloadFailed = autoRedownloadFailed,
                            autoRedownloadFailedFromInteractiveSearch = autoRedownloadInteractive,
                        ),
                    )
                },
                enabled = !busy,
            ) {
                Text(stringResource(ArrUiR.string.arr_common_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(ArrUiR.string.arr_common_cancel))
            }
        },
    )
}
