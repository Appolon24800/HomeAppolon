package dev.appolon.homelab.arr.feature.sonarr.settings.connect.ui

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
import dev.appolon.homelab.arr.api.sonarr.model.IndexerConfigResource
import dev.appolon.homelab.arr.api.sonarr.model.IndexerResource
import dev.appolon.homelab.arr.core.ServerId
import dev.appolon.homelab.arr.feature.sonarr.R
import dev.appolon.homelab.arr.feature.sonarr.settings.connect.SonarrConnectClients
import dev.appolon.homelab.arr.feature.sonarr.settings.connect.data.SonarrConnectFieldForms
import dev.appolon.homelab.arr.feature.sonarr.settings.connect.data.SonarrConnectRepository
import dev.appolon.homelab.arr.feature.sonarr.settings.connect.vm.IndexersNotice
import dev.appolon.homelab.arr.feature.sonarr.settings.connect.vm.SonarrIndexersContent
import dev.appolon.homelab.arr.feature.sonarr.settings.connect.vm.SonarrIndexersViewModel
import dev.appolon.homelab.arr.ui.components.LabeledSwitch
import dev.appolon.homelab.arr.ui.components.SectionHeader
import dev.appolon.homelab.arr.ui.lce.ArrLceBox
import dev.appolon.homelab.arr.ui.lce.ArrPullToRefreshBox
import dev.appolon.homelab.arr.ui.lce.LceState
import dev.appolon.homelab.arr.ui.shell.ArrScaffold

/** Builds this group's repository once per server; shared by every destination. */
internal fun connectRepository(serverId: ServerId, clients: SonarrConnectClients) =
    SonarrConnectRepository(
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
        metadataApi = clients.apis.metadataApi,
        commandApi = clients.apis.commandApi,
        tagApi = clients.apis.tagApi,
        actionQueue = clients.actionQueue,
    )

/** Destination for the indexers route. */
@Composable
fun SonarrIndexersDestination(
    serverId: ServerId,
    clients: SonarrConnectClients,
    serverName: String? = null,
    onBack: (() -> Unit)? = null,
    onOpenIndexer: (Int?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val repository = remember(serverId, clients) { connectRepository(serverId, clients) }
    val vm: SonarrIndexersViewModel =
        viewModel(key = "sonarr-indexers-" + serverId.value) {
            SonarrIndexersViewModel(serverId, repository)
        }
    val state by vm.state.collectAsState()
    val lce by vm.lce.collectAsState()

    SonarrIndexersScreen(
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
 * Indexers list. Rows show the search modes each indexer participates in,
 * categories, seed settings read out of the field values, and tag chips;
 * the app bar offers test-all, the indexer options dialog, and refresh.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SonarrIndexersScreen(
    state: SonarrIndexersViewModel.UiState,
    lce: LceState<SonarrIndexersContent>,
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
    val content = (lce as? LceState.Content<SonarrIndexersContent>)?.data
    val refreshing = content != null && (lce as LceState.Content<SonarrIndexersContent>).refreshing

    ConnectNoticeHost(
        noticeMessage = when (state.notice) {
            IndexersNotice.DELETED -> stringResource(R.string.arr_sonarr_connect_notice_deleted)
            IndexersNotice.DELETED_QUEUED ->
                stringResource(R.string.arr_sonarr_connect_notice_deleted_queued)

            IndexersNotice.TEST_ALL_DONE ->
                stringResource(R.string.arr_sonarr_connect_notice_test_all_done)

            IndexersNotice.OPTIONS_SAVED ->
                stringResource(R.string.arr_sonarr_connect_notice_saved)

            IndexersNotice.OPTIONS_SAVED_QUEUED ->
                stringResource(R.string.arr_sonarr_connect_notice_saved_queued)

            null -> null
        },
        writeError = state.writeError,
        snackbarHostState = snackbarHostState,
        onConsumeNotice = onConsumeNotice,
        onConsumeWriteError = onConsumeWriteError,
    )

    ArrScaffold(
        title = stringResource(R.string.arr_sonarr_indexers_title),
        subtitle = serverName,
        onBack = onBack,
        actions = {
            IconButton(onClick = onTestAll, enabled = !state.testingAll && content != null) {
                Icon(
                    imageVector = Icons.Filled.PlayArrow,
                    contentDescription = stringResource(R.string.arr_sonarr_indexers_cd_test_all),
                )
            }
            IconButton(onClick = { showOptions = true }) {
                Icon(
                    imageVector = Icons.Filled.Tune,
                    contentDescription = stringResource(R.string.arr_sonarr_indexers_cd_options),
                )
            }
            IconButton(onClick = onRefresh) {
                Icon(
                    imageVector = Icons.Filled.Refresh,
                    contentDescription = stringResource(R.string.arr_sonarr_indexers_cd_refresh),
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
                    emptyTitle = stringResource(R.string.arr_sonarr_indexers_empty_title),
                    emptyMessage = stringResource(R.string.arr_sonarr_indexers_empty_message),
                    modifier = Modifier.fillMaxSize(),
                ) { data ->
                    LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.widthIn(max = ListMaxWidth + 32.dp),
                    ) {
                        if (data.pendingActions > 0) {
                            item { ConnectPendingBanner(data.pendingActions) }
                        }
                        item {
                            SectionHeader(title = stringResource(R.string.arr_sonarr_indexers_section_indexers))
                        }
                        items(data.indexers, key = { it.id }) { indexer ->
                            IndexerRow(
                                indexer = indexer,
                                tags = data.tags,
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
                text = { Text(stringResource(R.string.arr_sonarr_indexers_add)) },
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
            title = { Text(stringResource(R.string.arr_sonarr_connect_delete_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.arr_sonarr_connect_delete_message,
                        target.name ?: target.id.toString(),
                    ),
                )
            },
            confirmButton = {
                TextButton(onClick = onDeleteConfirmed) {
                    Text(stringResource(R.string.arr_sonarr_connect_delete_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = onDeleteCancelled) {
                    Text(stringResource(R.string.arr_sonarr_connect_delete_cancel))
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
    tags: List<dev.appolon.homelab.arr.api.sonarr.model.TagResource>,
    queued: Boolean,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
) {
    val openCd = stringResource(R.string.arr_sonarr_indexers_cd_open, indexer.name.orEmpty())
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
                Text(indexer.name.orEmpty(), style = MaterialTheme.typography.bodyLarge)
                Text(
                    text = listOfNotNull(
                        indexer.implementationName,
                        stringResource(R.string.arr_sonarr_connect_priority, indexer.priority),
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = buildString {
                        if (indexer.enableRss) {
                            append(stringResource(R.string.arr_sonarr_indexers_mode_rss))
                        }
                        if (indexer.enableAutomaticSearch) {
                            if (isNotEmpty()) append(" · ")
                            append(stringResource(R.string.arr_sonarr_indexers_mode_automatic))
                        }
                        if (indexer.enableInteractiveSearch) {
                            if (isNotEmpty()) append(" · ")
                            append(stringResource(R.string.arr_sonarr_indexers_mode_interactive))
                        }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                val seedRatio = SonarrConnectFieldForms.seedRatio(indexer.fields)
                if (seedRatio != null) {
                    Text(
                        text = stringResource(R.string.arr_sonarr_indexers_seed_ratio, seedRatio),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                val categories = SonarrConnectFieldForms.categoryIds(indexer.fields)
                if (categories.isNotEmpty()) {
                    Text(
                        text = stringResource(R.string.arr_sonarr_indexers_categories, categories.size),
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
            }
            val deleteCd = stringResource(
                R.string.arr_sonarr_indexers_cd_delete,
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

/** Indexer options edited in place: ages, retention, size cap, RSS interval. */
@Composable
private fun IndexerOptionsDialog(
    initial: IndexerConfigResource,
    busy: Boolean,
    onSave: (IndexerConfigResource) -> Unit,
    onDismiss: () -> Unit,
) {
    var minimumAge by remember { mutableStateOf(initial.minimumAge.toString()) }
    var retention by remember { mutableStateOf(initial.retention.toString()) }
    var maximumSize by remember { mutableStateOf(initial.maximumSize.toString()) }
    var rssSyncInterval by remember { mutableStateOf(initial.rssSyncInterval.toString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.arr_sonarr_indexers_options_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = minimumAge,
                    onValueChange = { minimumAge = it },
                    label = { Text(stringResource(R.string.arr_sonarr_indexers_option_minimum_age)) },
                    supportingText = {
                        Text(stringResource(R.string.arr_sonarr_indexers_option_minutes_support))
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = retention,
                    onValueChange = { retention = it },
                    label = { Text(stringResource(R.string.arr_sonarr_indexers_option_retention)) },
                    supportingText = {
                        Text(stringResource(R.string.arr_sonarr_indexers_option_days_support))
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = maximumSize,
                    onValueChange = { maximumSize = it },
                    label = { Text(stringResource(R.string.arr_sonarr_indexers_option_maximum_size)) },
                    supportingText = {
                        Text(stringResource(R.string.arr_sonarr_indexers_option_megabytes_support))
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = rssSyncInterval,
                    onValueChange = { rssSyncInterval = it },
                    label = { Text(stringResource(R.string.arr_sonarr_indexers_option_rss_interval)) },
                    supportingText = {
                        Text(stringResource(R.string.arr_sonarr_indexers_option_minimum_minutes_support))
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(
                        initial.copy(
                            minimumAge = minimumAge.trim().toIntOrNull() ?: initial.minimumAge,
                            retention = retention.trim().toIntOrNull() ?: initial.retention,
                            maximumSize = maximumSize.trim().toIntOrNull() ?: initial.maximumSize,
                            rssSyncInterval = rssSyncInterval.trim().toIntOrNull()
                                ?: initial.rssSyncInterval,
                        ),
                    )
                },
                enabled = !busy,
            ) {
                Text(stringResource(R.string.arr_sonarr_connect_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.arr_sonarr_connect_cancel))
            }
        },
    )
}
