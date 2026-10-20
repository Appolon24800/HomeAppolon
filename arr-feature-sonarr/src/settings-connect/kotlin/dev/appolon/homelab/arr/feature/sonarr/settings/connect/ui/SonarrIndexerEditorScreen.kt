package dev.appolon.homelab.arr.feature.sonarr.settings.connect.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Science
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.appolon.homelab.arr.api.sonarr.model.IndexerResource
import dev.appolon.homelab.arr.core.ServerId
import dev.appolon.homelab.arr.feature.sonarr.R
import dev.appolon.homelab.arr.feature.sonarr.settings.connect.SonarrConnectClients
import dev.appolon.homelab.arr.feature.sonarr.settings.connect.vm.IndexerDraft
import dev.appolon.homelab.arr.feature.sonarr.settings.connect.vm.IndexerEditorNotice
import dev.appolon.homelab.arr.feature.sonarr.settings.connect.vm.SonarrIndexerEditorViewModel
import dev.appolon.homelab.arr.ui.components.LabeledSwitch
import dev.appolon.homelab.arr.ui.components.SectionHeader
import dev.appolon.homelab.arr.ui.lce.ArrLceBox
import dev.appolon.homelab.arr.ui.lce.LceState
import dev.appolon.homelab.arr.ui.shell.ArrScaffold
import dev.appolon.homelab.arr.ui.R as ArrUiR

/** Destination for the indexer editor route (indexerId null creates). */
@Composable
fun SonarrIndexerEditorDestination(
    serverId: ServerId,
    indexerId: Int?,
    clients: SonarrConnectClients,
    serverName: String? = null,
    onBack: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val repository = remember(serverId, clients) { connectRepository(serverId, clients) }
    val vm: SonarrIndexerEditorViewModel = viewModel(
        key = "sonarr-indexer-editor-" + serverId.value + "-" + (indexerId ?: "new"),
    ) {
        SonarrIndexerEditorViewModel(serverId, indexerId, repository)
    }
    val state by vm.state.collectAsState()
    val lce by vm.lce.collectAsState()

    SonarrIndexerEditorScreen(
        state = state,
        lce = lce,
        serverName = serverName,
        onBack = onBack,
        onPick = vm::pickSchema,
        onNameChange = { name -> vm.updateDraft { it.copy(name = name) } },
        onToggleRss = { vm.updateDraft { it.copy(enableRss = !it.enableRss) } },
        onToggleAutomaticSearch = {
            vm.updateDraft { it.copy(enableAutomaticSearch = !it.enableAutomaticSearch) }
        },
        onToggleInteractiveSearch = {
            vm.updateDraft { it.copy(enableInteractiveSearch = !it.enableInteractiveSearch) }
        },
        onPriorityChange = { priority -> vm.updateDraft { it.copy(priority = priority) } },
        onSeasonAgeChange = { age ->
            vm.updateDraft { it.copy(seasonSearchMaximumSingleEpisodeAge = age) }
        },
        onValueChange = vm::onValueChange,
        onToggleTag = vm::toggleTag,
        onTest = vm::test,
        onSave = { vm.save() },
        onDeleteRequest = vm::requestDelete,
        onDeleteCancelled = vm::deleteCancelled,
        onDeleteConfirmed = { vm.deleteConfirmed(onBack ?: {}) },
        onConsumeNotice = vm::consumeNotice,
        onConsumeWriteError = vm::consumeWriteError,
        modifier = modifier,
    )
}

/**
 * Indexer add/edit. Add renders the definition picker first; after a pick
 * (or when editing) the form shows name, the search-mode toggles, the
 * season search age, tags, and the dynamic provider fields, whose sections
 * include categories and seed criteria.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SonarrIndexerEditorScreen(
    state: SonarrIndexerEditorViewModel.UiState,
    lce: LceState<SonarrIndexerEditorViewModel.UiState>,
    serverName: String?,
    onBack: (() -> Unit)?,
    onPick: (IndexerResource) -> Unit,
    onNameChange: (String) -> Unit,
    onToggleRss: () -> Unit,
    onToggleAutomaticSearch: () -> Unit,
    onToggleInteractiveSearch: () -> Unit,
    onPriorityChange: (Int) -> Unit,
    onSeasonAgeChange: (Int) -> Unit,
    onValueChange: (String, kotlinx.serialization.json.JsonElement) -> Unit,
    onToggleTag: (String) -> Unit,
    onTest: () -> Unit,
    onSave: () -> Unit,
    onDeleteRequest: () -> Unit,
    onDeleteCancelled: () -> Unit,
    onDeleteConfirmed: () -> Unit,
    onConsumeNotice: () -> Unit,
    onConsumeWriteError: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val snackbarHostState = remember { SnackbarHostState() }

    ConnectNoticeHost(
        noticeMessage = when (state.notice) {
            IndexerEditorNotice.SAVED -> stringResource(ArrUiR.string.arr_common_connect_notice_saved)
            IndexerEditorNotice.SAVED_QUEUED ->
                stringResource(ArrUiR.string.arr_common_system_notice_saved_queued)

            IndexerEditorNotice.DELETED ->
                stringResource(ArrUiR.string.arr_common_connect_notice_deleted)

            IndexerEditorNotice.DELETED_QUEUED ->
                stringResource(ArrUiR.string.arr_common_backup_notice_deleted_queued)

            IndexerEditorNotice.TEST_PASSED ->
                stringResource(ArrUiR.string.arr_common_connect_notice_test_passed)

            null -> null
        },
        writeError = state.writeError,
        snackbarHostState = snackbarHostState,
        onConsumeNotice = onConsumeNotice,
        onConsumeWriteError = onConsumeWriteError,
    )

    ArrScaffold(
        title = stringResource(
            if (state.isNew) ArrUiR.string.arr_common_indexers_add
            else ArrUiR.string.arr_common_indexer_editor_title,
        ),
        subtitle = serverName,
        onBack = onBack,
        actions = {
            if (state.draft != null) {
                IconButton(onClick = onTest, enabled = !state.testing) {
                    Icon(
                        imageVector = Icons.Filled.Science,
                        contentDescription = stringResource(ArrUiR.string.arr_common_connect_cd_test),
                    )
                }
                if (!state.isNew) {
                    val deleteCd = stringResource(ArrUiR.string.arr_common_connect_cd_delete)
                    IconButton(
                        onClick = onDeleteRequest,
                        modifier = Modifier.semantics { contentDescription = deleteCd },
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Delete,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        },
        modifier = modifier,
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            ArrLceBox(
                state = lce,
                onRetry = onBack,
                emptyTitle = stringResource(ArrUiR.string.arr_common_connect_picker_empty_title),
                emptyMessage = stringResource(ArrUiR.string.arr_common_connect_picker_empty_message),
                modifier = Modifier.fillMaxSize(),
            ) { data ->
                when {
                    data.picker -> SchemaPicker(
                        schemas = data.schemas,
                        onPick = onPick,
                    )

                    data.draft != null -> IndexerForm(
                        state = data,
                        draft = data.draft,
                        onNameChange = onNameChange,
                        onToggleRss = onToggleRss,
                        onToggleAutomaticSearch = onToggleAutomaticSearch,
                        onToggleInteractiveSearch = onToggleInteractiveSearch,
                        onPriorityChange = onPriorityChange,
                        onSeasonAgeChange = onSeasonAgeChange,
                        onValueChange = onValueChange,
                        onToggleTag = onToggleTag,
                        onSave = onSave,
                    )
                }
            }
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }

    if (state.confirmDelete) {
        AlertDialog(
            onDismissRequest = onDeleteCancelled,
            title = { Text(stringResource(ArrUiR.string.arr_common_delete)) },
            text = {
                Text(
                    stringResource(
                        ArrUiR.string.arr_common_connect_delete_message,
                        state.draft?.name.orEmpty(),
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

@Composable
private fun SchemaPicker(
    schemas: List<IndexerResource>,
    onPick: (IndexerResource) -> Unit,
) {
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.widthIn(max = ListMaxWidth + 32.dp),
    ) {
        item {
            Text(
                stringResource(ArrUiR.string.arr_common_connect_picker_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        items(
            schemas,
            key = { it.implementation ?: it.id.toString() },
            contentType = { "schema" },
        ) { schema ->
            val cd = stringResource(
                ArrUiR.string.arr_common_connect_picker_cd_pick,
                schema.implementationName.orEmpty(),
            )
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClickLabel = cd) { onPick(schema) },
            ) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp)
                        .padding(16.dp),
                ) {
                    Text(
                        schema.implementationName.orEmpty(),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }
        }
    }
}

@Composable
private fun IndexerForm(
    state: SonarrIndexerEditorViewModel.UiState,
    draft: IndexerDraft,
    onNameChange: (String) -> Unit,
    onToggleRss: () -> Unit,
    onToggleAutomaticSearch: () -> Unit,
    onToggleInteractiveSearch: () -> Unit,
    onPriorityChange: (Int) -> Unit,
    onSeasonAgeChange: (Int) -> Unit,
    onValueChange: (String, kotlinx.serialization.json.JsonElement) -> Unit,
    onToggleTag: (String) -> Unit,
    onSave: () -> Unit,
) {
    var showAdvanced by rememberSaveable { mutableStateOf(false) }
    var priorityText by rememberSaveable { mutableStateOf(draft.priority.toString()) }
    var seasonAgeText by rememberSaveable { mutableStateOf(draft.seasonSearchMaximumSingleEpisodeAge.toString()) }
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.widthIn(max = ListMaxWidth + 32.dp),
    ) {
        item {
            OutlinedTextField(
                value = draft.name,
                onValueChange = onNameChange,
                label = { Text(stringResource(ArrUiR.string.arr_common_autotag_name)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item {
            Column {
                SectionHeader(title = stringResource(ArrUiR.string.arr_common_indexer_section_search_modes))
                if (draft.supportsRss) {
                    LabeledSwitch(
                        label = stringResource(ArrUiR.string.arr_common_indexers_mode_rss),
                        checked = draft.enableRss,
                        onCheckedChange = { onToggleRss() },
                    )
                }
                LabeledSwitch(
                    label = stringResource(ArrUiR.string.arr_common_indexers_mode_automatic),
                    checked = draft.enableAutomaticSearch,
                    onCheckedChange = { onToggleAutomaticSearch() },
                )
                if (draft.supportsSearch) {
                    LabeledSwitch(
                        label = stringResource(ArrUiR.string.arr_common_indexers_mode_interactive),
                        checked = draft.enableInteractiveSearch,
                        onCheckedChange = { onToggleInteractiveSearch() },
                    )
                }
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = priorityText,
                    onValueChange = { next ->
                        priorityText = next
                        next.trim().toIntOrNull()?.let(onPriorityChange)
                    },
                    label = { Text(stringResource(ArrUiR.string.arr_common_connect_priority_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = seasonAgeText,
                    onValueChange = { next ->
                        seasonAgeText = next
                        next.trim().toIntOrNull()?.let(onSeasonAgeChange)
                    },
                    label = {
                        Text(stringResource(R.string.arr_sonarr_indexer_season_search_age))
                    },
                    supportingText = {
                        Text(stringResource(R.string.arr_sonarr_indexer_season_search_age_support))
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        item {
            ConnectTagPicker(
                allTags = state.tags,
                selected = draft.tagLabels,
                onToggle = onToggleTag,
            )
        }
        item {
            ConnectFieldEditors(
                fields = draft.fields,
                values = draft.values,
                onValueChange = onValueChange,
                tagOptions = state.tags,
                showAdvanced = showAdvanced,
                onToggleAdvanced = { showAdvanced = !showAdvanced },
            )
        }
        item {
            ExtendedFloatingActionButton(
                onClick = onSave,
                modifier = if (state.busy || draft.name.isBlank()) {
                    Modifier.alpha(0.5f)
                } else {
                    Modifier
                },
            ) {
                Text(stringResource(ArrUiR.string.arr_common_save))
            }
        }
    }
}
