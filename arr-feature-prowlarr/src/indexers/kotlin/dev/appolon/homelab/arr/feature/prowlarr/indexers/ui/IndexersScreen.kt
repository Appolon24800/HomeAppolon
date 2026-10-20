package dev.appolon.homelab.arr.feature.prowlarr.indexers.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlaylistAddCheck
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.appolon.homelab.arr.api.prowlarr.model.AppProfileResource
import dev.appolon.homelab.arr.api.prowlarr.model.ApplyTags
import dev.appolon.homelab.arr.api.prowlarr.model.IndexerResource
import dev.appolon.homelab.arr.api.prowlarr.model.TagResource
import dev.appolon.homelab.arr.feature.prowlarr.R
import dev.appolon.homelab.arr.feature.prowlarr.indexers.data.IndexerCategories
import dev.appolon.homelab.arr.feature.prowlarr.indexers.data.IndexerListQuery
import dev.appolon.homelab.arr.feature.prowlarr.indexers.data.IndexerSorts
import dev.appolon.homelab.arr.feature.prowlarr.indexers.data.PrivacyFilter
import dev.appolon.homelab.arr.feature.prowlarr.indexers.data.ProtocolFilter
import dev.appolon.homelab.arr.feature.prowlarr.indexers.data.StateFilter
import dev.appolon.homelab.arr.feature.prowlarr.indexers.vm.IndexerBulkForm
import dev.appolon.homelab.arr.feature.prowlarr.indexers.vm.IndexersMessage
import dev.appolon.homelab.arr.feature.prowlarr.indexers.vm.IndexersUiState
import dev.appolon.homelab.arr.feature.prowlarr.indexers.vm.IndexersView
import dev.appolon.homelab.arr.ui.components.SortMenu
import dev.appolon.homelab.arr.ui.lce.ArrLceBox
import dev.appolon.homelab.arr.ui.lce.ArrPullToRefreshBox
import dev.appolon.homelab.arr.ui.lce.LceState
import dev.appolon.homelab.arr.ui.lce.arrErrorMessage
import dev.appolon.homelab.arr.ui.shell.ArrScaffold

/**
 * The indexers screen: card and table views with filters, selection, bulk
 * edit, deletes, and probes. Stateless; every interaction routes to a
 * callback owned by the ViewModel.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun IndexersScreen(
    state: IndexersUiState,
    lce: LceState<IndexersUiState>,
    onRefresh: () -> Unit,
    onViewChange: (IndexersView) -> Unit,
    onSortSelect: (String) -> Unit,
    onToggleSortDescending: () -> Unit,
    onSearchChange: (String) -> Unit,
    onProtocolFilterChange: (ProtocolFilter) -> Unit,
    onPrivacyFilterChange: (PrivacyFilter) -> Unit,
    onStateFilterChange: (StateFilter) -> Unit,
    onTagFilterChange: (Int?) -> Unit,
    onClearFilters: () -> Unit,
    onIndexerActivate: (Int) -> Unit,
    onIndexerLongClick: (Int) -> Unit,
    onSelectAll: () -> Unit,
    onExitSelection: () -> Unit,
    onSetEnabled: (Int, Boolean) -> Unit,
    onTestIndexer: (Int) -> Unit,
    onTestAll: () -> Unit,
    onOpenBulkEditor: () -> Unit,
    onBulkEditorChange: ((IndexerBulkForm) -> IndexerBulkForm) -> Unit,
    onBulkEditorSubmit: () -> Unit,
    onBulkEditorDismiss: () -> Unit,
    onRequestDelete: () -> Unit,
    onDeleteConfirm: () -> Unit,
    onDeleteDismiss: () -> Unit,
    onAddIndexer: () -> Unit,
    onOpenCategories: () -> Unit,
    onOpenProxies: () -> Unit,
    onOpenApplications: () -> Unit,
    onConsumeMessage: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val messageText = state.message?.let { indexersMessageText(it) }
    LaunchedEffect(messageText) {
        if (messageText != null) {
            snackbarHostState.showSnackbar(messageText)
            onConsumeMessage()
        }
    }

    ArrScaffold(
        title = if (state.selectMode) {
            stringResource(R.string.arr_prowlarr_indexers_selected, state.selection.size)
        } else {
            stringResource(R.string.arr_prowlarr_indexers_title)
        },
        onBack = if (state.selectMode) onExitSelection else null,
        actions = {
            if (state.selectMode) {
                SelectionActions(
                    onSelectAll = onSelectAll,
                    onEdit = onOpenBulkEditor,
                    onDelete = onRequestDelete,
                )
            } else {
                LibraryActions(
                    state = state,
                    onViewChange = onViewChange,
                    onSortSelect = onSortSelect,
                    onToggleSortDescending = onToggleSortDescending,
                    onTestAll = onTestAll,
                    onAddIndexer = onAddIndexer,
                    onOpenCategories = onOpenCategories,
                    onOpenProxies = onOpenProxies,
                    onOpenApplications = onOpenApplications,
                )
            }
        },
        filterBar = {
            if (!state.selectMode) {
                FilterBar(
                    state = state,
                    onSearchChange = onSearchChange,
                    onProtocolFilterChange = onProtocolFilterChange,
                    onPrivacyFilterChange = onPrivacyFilterChange,
                    onStateFilterChange = onStateFilterChange,
                    onTagFilterChange = onTagFilterChange,
                    onClearFilters = onClearFilters,
                )
            }
        },
        modifier = modifier,
    ) { padding ->
        ArrPullToRefreshBox(
            isRefreshing = state.refreshing,
            onRefresh = onRefresh,
            modifier = Modifier.fillMaxSize(),
        ) {
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(padding),
            ) {
                ArrLceBox(
                    state = lce,
                    onRetry = onRefresh,
                    emptyTitle = stringResource(R.string.arr_prowlarr_indexers_empty_title),
                    emptyMessage = stringResource(R.string.arr_prowlarr_indexers_empty_message),
                    modifier = Modifier.weight(1f),
                ) { _ ->
                    if (state.view == IndexersView.CARDS) {
                        IndexerCardGrid(
                            state = state,
                            onIndexerActivate = onIndexerActivate,
                            onIndexerLongClick = onIndexerLongClick,
                            onSetEnabled = onSetEnabled,
                            onTestIndexer = onTestIndexer,
                        )
                    } else {
                        IndexerTable(
                            state = state,
                            onIndexerActivate = onIndexerActivate,
                            onIndexerLongClick = onIndexerLongClick,
                            onSetEnabled = onSetEnabled,
                        )
                    }
                }
                SnackbarHost(hostState = snackbarHostState) { data ->
                    Snackbar(snackbarData = data)
                }
            }
        }
    }

    if (state.bulkForm != null) {
        BulkEditSheet(
            form = state.bulkForm,
            appProfiles = state.appProfiles,
            tags = state.tags,
            onChange = onBulkEditorChange,
            onSubmit = onBulkEditorSubmit,
            onDismiss = onBulkEditorDismiss,
        )
    }
    if (state.confirmDelete) {
        AlertDialog(
            onDismissRequest = onDeleteDismiss,
            title = { Text(stringResource(R.string.arr_prowlarr_indexers_delete_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.arr_prowlarr_indexers_delete_message,
                        state.bulkTargets.size,
                    ),
                )
            },
            confirmButton = {
                TextButton(onClick = onDeleteConfirm) {
                    Text(stringResource(R.string.arr_prowlarr_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = onDeleteDismiss) {
                    Text(stringResource(R.string.arr_prowlarr_cancel))
                }
            },
        )
    }
}

@Composable
private fun LibraryActions(
    state: IndexersUiState,
    onViewChange: (IndexersView) -> Unit,
    onSortSelect: (String) -> Unit,
    onToggleSortDescending: () -> Unit,
    onTestAll: () -> Unit,
    onAddIndexer: () -> Unit,
    onOpenCategories: () -> Unit,
    onOpenProxies: () -> Unit,
    onOpenApplications: () -> Unit,
) {
    val viewCd = stringResource(
        if (state.view == IndexersView.CARDS) R.string.arr_prowlarr_cd_view_table
        else R.string.arr_prowlarr_cd_view_cards,
    )
    IconButton(onClick = { onViewChange(if (state.view == IndexersView.CARDS) IndexersView.TABLE else IndexersView.CARDS) }) {
        Icon(
            imageVector = if (state.view == IndexersView.CARDS) {
                Icons.Filled.ViewList
            } else {
                Icons.Filled.GridView
            },
            contentDescription = viewCd,
        )
    }
    val sortLabels = mapOf(
        IndexerSorts.NAME to stringResource(R.string.arr_prowlarr_sort_name),
        IndexerSorts.PRIORITY to stringResource(R.string.arr_prowlarr_sort_priority),
        IndexerSorts.PROTOCOL to stringResource(R.string.arr_prowlarr_sort_protocol),
        IndexerSorts.ADDED to stringResource(R.string.arr_prowlarr_sort_added),
    )
    SortMenu(
        options = IndexerSorts.ALL,
        selected = state.query.sortId,
        label = { sortLabels.getValue(it) },
        onSelect = onSortSelect,
    )
    IconButton(onClick = onToggleSortDescending) {
        Icon(
            imageVector = Icons.Filled.SwapVert,
            contentDescription = stringResource(R.string.arr_prowlarr_cd_sort_descending),
        )
    }
    var menuOpen by remember { mutableStateOf(false) }
    val queueCd = stringResource(R.string.arr_prowlarr_queued_count, state.queuedForServer)
    BadgedBox(
        badge = {
            if (state.queuedForServer > 0) Badge { Text(state.queuedForServer.toString()) }
        },
        modifier = Modifier.semantics { contentDescription = queueCd },
    ) {
        Box {
            IconButton(onClick = { menuOpen = true }) {
                Icon(
                    imageVector = Icons.Filled.MoreVert,
                    contentDescription = stringResource(R.string.arr_prowlarr_cd_more),
                )
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.arr_prowlarr_indexers_test_all)) },
                    leadingIcon = { Icon(Icons.Filled.Sync, contentDescription = null) },
                    onClick = {
                        menuOpen = false
                        onTestAll()
                    },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.arr_prowlarr_categories_title)) },
                    onClick = {
                        menuOpen = false
                        onOpenCategories()
                    },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.arr_prowlarr_proxies_title)) },
                    onClick = {
                        menuOpen = false
                        onOpenProxies()
                    },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.arr_prowlarr_applications_title)) },
                    onClick = {
                        menuOpen = false
                        onOpenApplications()
                    },
                )
            }
        }
    }
    IconButton(onClick = onAddIndexer) {
        Icon(
            imageVector = Icons.Filled.Add,
            contentDescription = stringResource(R.string.arr_prowlarr_indexers_add),
        )
    }
}

@Composable
private fun SelectionActions(
    onSelectAll: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    IconButton(onClick = onSelectAll) {
        Icon(
            imageVector = Icons.Filled.SelectAll,
            contentDescription = stringResource(R.string.arr_prowlarr_cd_select_all),
        )
    }
    IconButton(onClick = onEdit) {
        Icon(
            imageVector = Icons.Filled.PlaylistAddCheck,
            contentDescription = stringResource(R.string.arr_prowlarr_cd_edit_selected),
        )
    }
    IconButton(onClick = onDelete) {
        Icon(
            imageVector = Icons.Filled.Delete,
            contentDescription = stringResource(R.string.arr_prowlarr_cd_delete_selected),
        )
    }
}

@Composable
private fun FilterBar(
    state: IndexersUiState,
    onSearchChange: (String) -> Unit,
    onProtocolFilterChange: (ProtocolFilter) -> Unit,
    onPrivacyFilterChange: (PrivacyFilter) -> Unit,
    onStateFilterChange: (StateFilter) -> Unit,
    onTagFilterChange: (Int?) -> Unit,
    onClearFilters: () -> Unit,
) {
    val query = state.query
    Column(Modifier.padding(horizontal = 8.dp)) {
        OutlinedTextField(
            value = query.search,
            onValueChange = onSearchChange,
            label = { Text(stringResource(R.string.arr_prowlarr_indexers_search)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .padding(vertical = 8.dp),
        ) {
            ProtocolFilter.entries.forEach { filter ->
                FilterChip(
                    selected = query.protocol == filter,
                    onClick = { onProtocolFilterChange(filter) },
                    label = { Text(protocolFilterLabel(filter)) },
                )
            }
            PrivacyFilter.entries.forEach { filter ->
                FilterChip(
                    selected = query.privacy == filter,
                    onClick = { onPrivacyFilterChange(filter) },
                    label = { Text(privacyFilterLabel(filter)) },
                )
            }
            StateFilter.entries.forEach { filter ->
                FilterChip(
                    selected = query.state == filter,
                    onClick = { onStateFilterChange(filter) },
                    label = { Text(stateFilterLabel(filter)) },
                )
            }
            state.tags.forEach { tag ->
                FilterChip(
                    selected = query.tagId == tag.id,
                    onClick = { onTagFilterChange(if (query.tagId == tag.id) null else tag.id) },
                    label = { Text(tag.label.orEmpty()) },
                )
            }
            if (query != IndexerListQuery()) {
                FilterChip(
                    selected = false,
                    onClick = onClearFilters,
                    label = { Text(stringResource(R.string.arr_prowlarr_indexers_clear_filters)) },
                )
            }
        }
    }
}

@Composable
internal fun protocolFilterLabel(filter: ProtocolFilter): String = when (filter) {
    ProtocolFilter.ALL -> stringResource(R.string.arr_prowlarr_filter_protocol_all)
    ProtocolFilter.TORRENT -> stringResource(R.string.arr_prowlarr_protocol_torrent)
    ProtocolFilter.USENET -> stringResource(R.string.arr_prowlarr_protocol_usenet)
}

@Composable
internal fun privacyFilterLabel(filter: PrivacyFilter): String = when (filter) {
    PrivacyFilter.ALL -> stringResource(R.string.arr_prowlarr_filter_privacy_all)
    PrivacyFilter.PUBLIC -> stringResource(R.string.arr_prowlarr_privacy_public)
    PrivacyFilter.SEMI_PRIVATE -> stringResource(R.string.arr_prowlarr_privacy_semi_private)
    PrivacyFilter.PRIVATE -> stringResource(R.string.arr_prowlarr_privacy_private)
}

@Composable
internal fun stateFilterLabel(filter: StateFilter): String = when (filter) {
    StateFilter.ALL -> stringResource(R.string.arr_prowlarr_filter_state_all)
    StateFilter.ENABLED -> stringResource(R.string.arr_prowlarr_filter_state_enabled)
    StateFilter.DISABLED -> stringResource(R.string.arr_prowlarr_filter_state_disabled)
    StateFilter.THROTTLED -> stringResource(R.string.arr_prowlarr_filter_state_throttled)
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun IndexerCardGrid(
    state: IndexersUiState,
    onIndexerActivate: (Int) -> Unit,
    onIndexerLongClick: (Int) -> Unit,
    onSetEnabled: (Int, Boolean) -> Unit,
    onTestIndexer: (Int) -> Unit,
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 320.dp),
        contentPadding = PaddingValues(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        items(state.presentation, key = { it.id }) { indexer ->
            IndexerCard(
                indexer = indexer,
                state = state,
                onIndexerActivate = onIndexerActivate,
                onIndexerLongClick = onIndexerLongClick,
                onSetEnabled = onSetEnabled,
                onTestIndexer = onTestIndexer,
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun IndexerCard(
    indexer: IndexerResource,
    state: IndexersUiState,
    onIndexerActivate: (Int) -> Unit,
    onIndexerLongClick: (Int) -> Unit,
    onSetEnabled: (Int, Boolean) -> Unit,
    onTestIndexer: (Int) -> Unit,
) {
    val activateCd = stringResource(R.string.arr_prowlarr_cd_open_indexer, indexer.name.orEmpty())
    val selectCd = stringResource(R.string.arr_prowlarr_cd_select_indexer, indexer.name.orEmpty())
    val categoryCount =
        IndexerCategories.selectedIds(indexer).size.toString() +
            " " + stringResource(R.string.arr_prowlarr_indexer_categories_count)
    ElevatedCard(
        modifier = Modifier
            .heightIn(min = 96.dp)
            .combinedClickable(
                onClick = { onIndexerActivate(indexer.id) },
                onLongClick = { onIndexerLongClick(indexer.id) },
            )
            .semantics {
                contentDescription = if (state.selectMode) selectCd else activateCd
            },
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        indexer.name.orEmpty(),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        indexer.implementationName.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (state.selectMode) {
                    Checkbox(
                        checked = indexer.id in state.selection,
                        onCheckedChange = { onIndexerActivate(indexer.id) },
                    )
                } else {
                    Switch(
                        checked = indexer.enable,
                        onCheckedChange = { onSetEnabled(indexer.id, it) },
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                AssistChip(
                    onClick = {},
                    enabled = false,
                    label = { Text(privacyLabel(indexer.privacy)) },
                )
                AssistChip(
                    onClick = {},
                    enabled = false,
                    label = { Text(protocolLabel(indexer.protocol)) },
                )
                Text(
                    stringResource(R.string.arr_prowlarr_indexer_priority, indexer.priority),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            TagChips(
                tags = indexer.tags.orEmpty(),
                allTags = state.tags,
            )
            IndexerStatusBadges(
                indexer = indexer,
                status = state.statuses[indexer.id],
            )
            if (indexer.id in state.queuedIndexerIds) {
                QueuedBadge()
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    categoryCount,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                if (!state.selectMode) {
                    TextButton(onClick = { onTestIndexer(indexer.id) }) {
                        Text(stringResource(R.string.arr_prowlarr_test))
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun IndexerTable(
    state: IndexersUiState,
    onIndexerActivate: (Int) -> Unit,
    onIndexerLongClick: (Int) -> Unit,
    onSetEnabled: (Int, Boolean) -> Unit,
) {
    LazyColumn(
        contentPadding = PaddingValues(vertical = 4.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                Spacer(Modifier.width(72.dp))
                Text(
                    stringResource(R.string.arr_prowlarr_sort_name),
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    stringResource(R.string.arr_prowlarr_table_priority),
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.width(64.dp),
                )
                Text(
                    stringResource(R.string.arr_prowlarr_indexer_tags),
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.width(120.dp),
                )
            }
        }
        items(state.presentation, key = { it.id }) { indexer ->
            IndexerTableRow(
                indexer = indexer,
                state = state,
                onIndexerActivate = onIndexerActivate,
                onIndexerLongClick = onIndexerLongClick,
                onSetEnabled = onSetEnabled,
            )
            HorizontalDivider()
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun IndexerTableRow(
    indexer: IndexerResource,
    state: IndexersUiState,
    onIndexerActivate: (Int) -> Unit,
    onIndexerLongClick: (Int) -> Unit,
    onSetEnabled: (Int, Boolean) -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .combinedClickable(
                onClick = { onIndexerActivate(indexer.id) },
                onLongClick = { onIndexerLongClick(indexer.id) },
            )
            .padding(horizontal = 16.dp, vertical = 6.dp),
    ) {
        Switch(
            checked = indexer.enable,
            onCheckedChange = { onSetEnabled(indexer.id, it) },
        )
        Column(Modifier.weight(1f)) {
            Text(indexer.name.orEmpty(), style = MaterialTheme.typography.bodyLarge)
            Text(
                listOfNotNull(
                    indexer.implementationName,
                    privacyLabel(indexer.privacy),
                    protocolLabel(indexer.protocol),
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            indexer.priority.toString(),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.width(64.dp),
        )
        Column(Modifier.width(120.dp)) {
            TagChips(
                tags = indexer.tags.orEmpty().take(2),
                allTags = state.tags,
            )
            if (indexer.id in state.queuedIndexerIds) {
                QueuedBadge()
            } else {
                IndexerStatusBadges(
                    indexer = indexer,
                    status = state.statuses[indexer.id],
                )
            }
        }
    }
}

/** One-line snackbar text for a screen message. */
@Composable
internal fun indexersMessageText(message: IndexersMessage): String = when (message) {
    IndexersMessage.ToggleSynced -> stringResource(R.string.arr_prowlarr_message_toggle_synced)
    IndexersMessage.ToggleQueued -> stringResource(R.string.arr_prowlarr_message_toggle_queued)
    IndexersMessage.BulkSynced -> stringResource(R.string.arr_prowlarr_message_bulk_synced)
    IndexersMessage.BulkQueued -> stringResource(R.string.arr_prowlarr_message_bulk_queued)
    IndexersMessage.DeleteSynced -> stringResource(R.string.arr_prowlarr_message_delete_synced)
    IndexersMessage.DeleteQueued -> stringResource(R.string.arr_prowlarr_message_delete_queued)
    IndexersMessage.TestPassed -> stringResource(R.string.arr_prowlarr_message_test_passed)
    is IndexersMessage.TestFailed ->
        stringResource(R.string.arr_prowlarr_message_test_failed, arrErrorMessage(message.error))

    IndexersMessage.TestAllStarted ->
        stringResource(R.string.arr_prowlarr_message_test_all_started)

    is IndexersMessage.TestAllFailed ->
        stringResource(R.string.arr_prowlarr_message_test_failed, arrErrorMessage(message.error))

    is IndexersMessage.WriteFailed ->
        stringResource(R.string.arr_prowlarr_message_write_failed, arrErrorMessage(message.error))
}

/**
 * Bulk editor sheet: enable state, app profile, priority, and tags with
 * ApplyTags semantics. Null selections leave the target indexers unchanged.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BulkEditSheet(
    form: IndexerBulkForm,
    appProfiles: List<AppProfileResource>,
    tags: List<TagResource>,
    onChange: ((IndexerBulkForm) -> IndexerBulkForm) -> Unit,
    onSubmit: () -> Unit,
    onDismiss: () -> Unit,
) {
    var profileExpanded by remember { mutableStateOf(false) }
    var priorityText by rememberSaveable { mutableStateOf(form.priority?.toString().orEmpty()) }
    val applyOptions = listOf(ApplyTags.ADD, ApplyTags.REMOVE, ApplyTags.REPLACE)
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                stringResource(R.string.arr_prowlarr_bulk_title),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                stringResource(R.string.arr_prowlarr_bulk_enable),
                style = MaterialTheme.typography.bodyMedium,
            )
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                val options = listOf(null, true, false)
                options.forEachIndexed { index, option ->
                    SegmentedButton(
                        selected = form.enable == option,
                        onClick = { onChange { it.copy(enable = option) } },
                        shape = SegmentedButtonDefaults.itemShape(
                            index = index,
                            count = options.size,
                        ),
                        label = {
                            Text(
                                when (option) {
                                    null -> stringResource(R.string.arr_prowlarr_bulk_unchanged)
                                    true -> stringResource(R.string.arr_prowlarr_bulk_enable)
                                    false -> stringResource(R.string.arr_prowlarr_bulk_disable)
                                },
                            )
                        },
                    )
                }
            }
            ExposedDropdownMenuBox(
                expanded = profileExpanded,
                onExpandedChange = { profileExpanded = it },
                modifier = Modifier.fillMaxWidth(),
            ) {
                val selectedLabel = form.appProfileId
                    ?.let { id -> appProfiles.firstOrNull { it.id == id }?.name ?: "#$id" }
                OutlinedTextField(
                    value = selectedLabel.orEmpty(),
                    onValueChange = {},
                    readOnly = true,
                    label = { Text(stringResource(R.string.arr_prowlarr_bulk_app_profile)) },
                    trailingIcon = {
                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = profileExpanded)
                    },
                    modifier = Modifier
                        .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                        .fillMaxWidth(),
                )
                DropdownMenu(
                    expanded = profileExpanded,
                    onDismissRequest = { profileExpanded = false },
                ) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.arr_prowlarr_bulk_unchanged)) },
                        onClick = {
                            profileExpanded = false
                            onChange { it.copy(appProfileId = null) }
                        },
                    )
                    appProfiles.forEach { profile ->
                        DropdownMenuItem(
                            text = { Text(profile.name.orEmpty()) },
                            onClick = {
                                profileExpanded = false
                                onChange { it.copy(appProfileId = profile.id) }
                            },
                        )
                    }
                }
            }
            OutlinedTextField(
                value = priorityText,
                onValueChange = { text ->
                    priorityText = text
                    onChange { it.copy(priority = text.trim().toIntOrNull()) }
                },
                label = { Text(stringResource(R.string.arr_prowlarr_bulk_priority)) },
                isError = priorityText.isNotBlank() && priorityText.trim().toIntOrNull() == null,
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                stringResource(R.string.arr_prowlarr_bulk_tags),
                style = MaterialTheme.typography.bodyMedium,
            )
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                applyOptions.forEachIndexed { index, option ->
                    SegmentedButton(
                        selected = form.applyTags == option,
                        onClick = { onChange { it.copy(applyTags = option) } },
                        shape = SegmentedButtonDefaults.itemShape(
                            index = index,
                            count = applyOptions.size,
                        ),
                        label = {
                            Text(
                                when (option) {
                                    ApplyTags.ADD ->
                                        stringResource(R.string.arr_prowlarr_tags_add)

                                    ApplyTags.REMOVE ->
                                        stringResource(R.string.arr_prowlarr_tags_remove)

                                    ApplyTags.REPLACE ->
                                        stringResource(R.string.arr_prowlarr_tags_replace)
                                },
                            )
                        },
                    )
                }
            }
            TagLabelPicker(
                allTags = tags,
                selected = form.tagLabels,
                onToggle = { label ->
                    onChange { form ->
                        val next = if (label in form.tagLabels) {
                            form.tagLabels - label
                        } else {
                            form.tagLabels + label
                        }
                        form.copy(tagLabels = next)
                    }
                },
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onDismiss, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.arr_prowlarr_cancel))
                }
                Button(onClick = onSubmit, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.arr_prowlarr_bulk_apply))
                }
            }
        }
    }
}

