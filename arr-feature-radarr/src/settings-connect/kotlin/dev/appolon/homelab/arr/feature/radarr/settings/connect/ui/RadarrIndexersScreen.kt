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
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import dev.appolon.homelab.arr.api.radarr.model.IndexerConfigResource
import dev.appolon.homelab.arr.api.radarr.model.IndexerResource
import dev.appolon.homelab.arr.core.ArrError
import dev.appolon.homelab.arr.core.ServerId
import dev.appolon.homelab.arr.feature.radarr.R
import dev.appolon.homelab.arr.feature.radarr.settings.connect.RadarrConnectClients
import dev.appolon.homelab.arr.feature.radarr.settings.connect.data.ConnectFieldForms
import dev.appolon.homelab.arr.feature.radarr.settings.connect.data.RadarrConnectRepository
import dev.appolon.homelab.arr.feature.radarr.settings.connect.vm.IndexersNotice
import dev.appolon.homelab.arr.feature.radarr.settings.connect.vm.RadarrIndexersContent
import dev.appolon.homelab.arr.feature.radarr.settings.connect.vm.RadarrIndexersViewModel
import dev.appolon.homelab.arr.ui.components.SectionHeader
import dev.appolon.homelab.arr.ui.lce.ArrLceBox
import dev.appolon.homelab.arr.ui.lce.ArrPullToRefreshBox
import dev.appolon.homelab.arr.ui.lce.LceState
import dev.appolon.homelab.arr.ui.lce.arrErrorMessage
import dev.appolon.homelab.arr.ui.shell.ArrScaffold

/** Destination for the indexers route: repository plus ViewModel wiring. */
@Composable
fun RadarrIndexersDestination(
    serverId: ServerId,
    clients: RadarrConnectClients,
    serverName: String? = null,
    onBack: (() -> Unit)? = null,
    onOpenIndexer: (Int?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val repository = remember(serverId, clients) { connectRepository(serverId, clients) }
    val vm: RadarrIndexersViewModel = viewModel(key = "radarr-indexers-" + serverId.value) {
        RadarrIndexersViewModel(serverId, repository)
    }
    val state by vm.state.collectAsState()
    val lce by vm.lce.collectAsState()

    RadarrIndexersScreen(
        state = state,
        lce = lce,
        serverName = serverName,
        onBack = onBack,
        onRefresh = vm::refresh,
        onOpen = onOpenIndexer,
        onAdd = { onOpenIndexer(null) },
        onTestAll = vm::testAll,
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
 * The indexers list. Each row shows the provider name, implementation,
 * protocol, priority, the enabled search modes, seed ratio/time when the
 * provider declares them, and the per-indexer verdict of the last test-all
 * run. The options card edits the indexer-wide config. On tablet widths
 * the list stays a single centered column: settings rows are wide, not
 * tileable.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RadarrIndexersScreen(
    state: RadarrIndexersViewModel.UiState,
    lce: LceState<RadarrIndexersContent>,
    serverName: String?,
    onBack: (() -> Unit)?,
    onRefresh: () -> Unit,
    onOpen: (Int) -> Unit,
    onAdd: () -> Unit,
    onTestAll: () -> Unit,
    onDeleteRequest: (IndexerResource) -> Unit,
    onDeleteCancelled: () -> Unit,
    onDeleteConfirmed: () -> Unit,
    onSaveOptions: (IndexerConfigResource) -> Unit,
    onConsumeNotice: () -> Unit,
    onConsumeWriteError: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    var showOptions by remember { mutableStateOf(false) }
    val content = (lce as? LceState.Content<RadarrIndexersContent>)?.data
    val refreshing = content != null && (lce as LceState.Content<RadarrIndexersContent>).refreshing

    NoticeHost(
        noticeMessage = when (state.notice) {
            IndexersNotice.DELETED -> stringResource(R.string.arr_radarr_connect_notice_deleted)
            IndexersNotice.DELETED_QUEUED ->
                stringResource(R.string.arr_radarr_connect_notice_deleted_queued)

            IndexersNotice.TEST_ALL_DONE ->
                stringResource(R.string.arr_radarr_connect_notice_test_all_done)

            IndexersNotice.OPTIONS_SAVED ->
                stringResource(R.string.arr_radarr_connect_notice_saved)

            IndexersNotice.OPTIONS_SAVED_QUEUED ->
                stringResource(R.string.arr_radarr_connect_notice_saved_queued)

            null -> null
        },
        writeError = state.writeError,
        snackbarHostState = snackbarHostState,
        onConsumeNotice = onConsumeNotice,
        onConsumeWriteError = onConsumeWriteError,
    )

    ArrScaffold(
        title = stringResource(R.string.arr_radarr_indexers_title),
        subtitle = serverName,
        onBack = onBack,
        actions = {
            IconButton(onClick = onTestAll, enabled = !state.testingAll && content != null) {
                Icon(
                    imageVector = Icons.Filled.PlayArrow,
                    contentDescription = stringResource(R.string.arr_radarr_indexers_cd_test_all),
                )
            }
            IconButton(onClick = { showOptions = true }) {
                Icon(
                    imageVector = Icons.Filled.Tune,
                    contentDescription = stringResource(R.string.arr_radarr_indexers_cd_options),
                )
            }
            IconButton(onClick = onRefresh) {
                Icon(
                    imageVector = Icons.Filled.Refresh,
                    contentDescription = stringResource(R.string.arr_radarr_indexers_cd_refresh),
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
                    emptyTitle = stringResource(R.string.arr_radarr_indexers_empty_title),
                    emptyMessage = stringResource(R.string.arr_radarr_indexers_empty_message),
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
                                title = stringResource(R.string.arr_radarr_indexers_section_indexers),
                            )
                        }
                        items(data.indexers, key = { it.id }) { indexer ->
                            IndexerRow(
                                indexer = indexer,
                                tags = data.tags,
                                testResult = data.testResults[indexer.id],
                                queued = indexer.id in data.queuedRemovedIds,
                                onOpen = { onOpen(indexer.id) },
                                onDelete = { onDeleteRequest(indexer) },
                            )
                        }
                    }
                }
            }
            ExtendedFloatingActionButton(
                onClick = onAdd,
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.arr_radarr_indexers_add)) },
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
            title = { Text(stringResource(R.string.arr_radarr_connect_delete_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.arr_radarr_connect_delete_message,
                        target.name ?: target.id.toString(),
                    ),
                )
            },
            confirmButton = {
                TextButton(onClick = onDeleteConfirmed) {
                    Text(stringResource(R.string.arr_radarr_connect_delete_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = onDeleteCancelled) {
                    Text(stringResource(R.string.arr_radarr_connect_delete_cancel))
                }
            },
        )
    }

    if (showOptions) {
        IndexerOptionsDialog(
            initial = state.options ?: IndexerConfigResource(),
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
private fun IndexerRow(
    indexer: IndexerResource,
    tags: List<dev.appolon.homelab.arr.api.radarr.model.TagResource>,
    testResult: dev.appolon.homelab.arr.api.radarr.model.ProviderTestAllResult?,
    queued: Boolean,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
) {
    val openCd = stringResource(R.string.arr_radarr_indexers_cd_open, indexer.name.orEmpty())
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
                Text(
                    indexer.name.orEmpty(),
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    text = listOfNotNull(
                        indexer.implementationName,
                        stringResource(R.string.arr_radarr_indexers_priority, indexer.priority),
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                val modes = buildList {
                    if (indexer.enableRss) add(stringResource(R.string.arr_radarr_indexers_mode_rss))
                    if (indexer.enableAutomaticSearch) {
                        add(stringResource(R.string.arr_radarr_indexers_mode_automatic))
                    }
                    if (indexer.enableInteractiveSearch) {
                        add(stringResource(R.string.arr_radarr_indexers_mode_interactive))
                    }
                }
                if (modes.isNotEmpty()) {
                    Text(
                        modes.joinToString(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                val seedRatio = ConnectFieldForms.seedRatio(indexer.fields)
                val seedTime = ConnectFieldForms.seedTime(indexer.fields)
                if (seedRatio != null || seedTime != null) {
                    Text(
                        text = listOfNotNull(
                            seedRatio?.let {
                                stringResource(R.string.arr_radarr_indexers_seed_ratio, it)
                            },
                            seedTime?.let {
                                stringResource(R.string.arr_radarr_indexers_seed_time, it)
                            },
                        ).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (queued) ConnectQueuedBadge()
                    ConnectTagChips(indexer.tags.orEmpty().toList(), tags)
                }
                testResult?.let { TestResultBadge(it) }
            }
            val deleteCd = stringResource(
                R.string.arr_radarr_indexers_cd_delete,
                indexer.name.orEmpty(),
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

/** Indexer-wide options: minimum age, maximum size, retention, RSS interval. */
@Composable
private fun IndexerOptionsDialog(
    initial: IndexerConfigResource,
    busy: Boolean,
    onSave: (IndexerConfigResource) -> Unit,
    onDismiss: () -> Unit,
) {
    var minimumAge by remember { mutableStateOf(initial.minimumAge.toString()) }
    var maximumSize by remember { mutableStateOf(initial.maximumSize.toString()) }
    var retention by remember { mutableStateOf(initial.retention.toString()) }
    var rssSyncInterval by remember { mutableStateOf(initial.rssSyncInterval.toString()) }
    var preferFlags by remember { mutableStateOf(initial.preferIndexerFlags) }
    var availabilityDelay by remember { mutableStateOf(initial.availabilityDelay.toString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.arr_radarr_indexers_options_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedNumberField(
                    label = stringResource(R.string.arr_radarr_indexers_option_minimum_age),
                    value = minimumAge,
                    onChange = { minimumAge = it },
                )
                OutlinedNumberField(
                    label = stringResource(R.string.arr_radarr_indexers_option_maximum_size),
                    value = maximumSize,
                    onChange = { maximumSize = it },
                )
                OutlinedNumberField(
                    label = stringResource(R.string.arr_radarr_indexers_option_retention),
                    value = retention,
                    onChange = { retention = it },
                )
                OutlinedNumberField(
                    label = stringResource(R.string.arr_radarr_indexers_option_rss_interval),
                    value = rssSyncInterval,
                    onChange = { rssSyncInterval = it },
                )
                OutlinedNumberField(
                    label = stringResource(R.string.arr_radarr_indexers_option_availability_delay),
                    value = availabilityDelay,
                    onChange = { availabilityDelay = it },
                )
                dev.appolon.homelab.arr.ui.components.LabeledSwitch(
                    label = stringResource(R.string.arr_radarr_indexers_option_prefer_flags),
                    checked = preferFlags,
                    onCheckedChange = { preferFlags = it },
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(
                        initial.copy(
                            minimumAge = minimumAge.toIntOrNull() ?: initial.minimumAge,
                            maximumSize = maximumSize.toIntOrNull() ?: initial.maximumSize,
                            retention = retention.toIntOrNull() ?: initial.retention,
                            rssSyncInterval = rssSyncInterval.toIntOrNull() ?: initial.rssSyncInterval,
                            preferIndexerFlags = preferFlags,
                            availabilityDelay = availabilityDelay.toIntOrNull()
                                ?: initial.availabilityDelay,
                        ),
                    )
                },
                enabled = !busy,
            ) {
                Text(stringResource(R.string.arr_radarr_connect_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.arr_radarr_connect_cancel))
            }
        },
    )
}

/** Numeric text field tolerating intermediate empty states. */
@Composable
internal fun OutlinedNumberField(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    suffix: String? = null,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        suffix = suffix?.let { { Text(it) } },
        isError = value.isNotBlank() && value.toIntOrNull() == null,
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
}

/** Banner over the list while writes wait to sync. */
@Composable
internal fun PendingBanner(pending: Int) {
    AssistChip(
        onClick = {},
        enabled = false,
        label = { Text(stringResource(R.string.arr_radarr_connect_pending_banner, pending)) },
    )
}

/** Snackbar plumbing shared by every screen in this group. */
@Composable
internal fun NoticeHost(
    noticeMessage: String?,
    writeError: ArrError?,
    snackbarHostState: SnackbarHostState,
    onConsumeNotice: () -> Unit,
    onConsumeWriteError: () -> Unit,
) {
    val errorText = writeError?.let { arrErrorMessage(it) }
    LaunchedEffect(noticeMessage) {
        if (noticeMessage != null) {
            snackbarHostState.showSnackbar(noticeMessage)
            onConsumeNotice()
        }
    }
    LaunchedEffect(errorText) {
        if (errorText != null) {
            snackbarHostState.showSnackbar(errorText)
            onConsumeWriteError()
        }
    }
}

/** Shared repository builder for this group's destinations. */
internal fun connectRepository(
    serverId: ServerId,
    clients: RadarrConnectClients,
): RadarrConnectRepository = RadarrConnectRepository(
    serverId = serverId,
    store = clients.store,
    indexerApi = clients.apis.indexerApi,
    indexerConfigApi = clients.apis.indexerConfigApi,
    indexerFlagApi = clients.apis.indexerFlagApi,
    downloadClientApi = clients.apis.downloadClientApi,
    downloadClientConfigApi = clients.apis.downloadClientConfigApi,
    notificationApi = clients.apis.notificationApi,
    remotePathMappingApi = clients.apis.remotePathMappingApi,
    importListApi = clients.apis.importListApi,
    importListConfigApi = clients.apis.importListConfigApi,
    importListExclusionApi = clients.apis.importListExclusionApi,
    importListMoviesApi = clients.apis.importListMoviesApi,
    metadataApi = clients.apis.metadataApi,
    commandApi = clients.apis.commandApi,
    tagApi = clients.apis.tagApi,
    actionQueue = clients.actionQueue,
)

internal val ListMaxWidth = 640.dp
