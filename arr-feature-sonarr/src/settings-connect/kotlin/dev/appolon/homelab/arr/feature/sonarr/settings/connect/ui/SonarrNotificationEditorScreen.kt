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
import dev.appolon.homelab.arr.api.sonarr.model.NotificationResource
import dev.appolon.homelab.arr.core.ServerId
import dev.appolon.homelab.arr.feature.sonarr.R
import dev.appolon.homelab.arr.feature.sonarr.settings.connect.SonarrConnectClients
import dev.appolon.homelab.arr.feature.sonarr.settings.connect.vm.NotificationEditorNotice
import dev.appolon.homelab.arr.feature.sonarr.settings.connect.vm.SonarrNotificationEditorViewModel
import dev.appolon.homelab.arr.ui.components.LabeledSwitch
import dev.appolon.homelab.arr.ui.components.SectionHeader
import dev.appolon.homelab.arr.ui.lce.ArrLceBox
import dev.appolon.homelab.arr.ui.lce.LceState
import dev.appolon.homelab.arr.ui.shell.ArrScaffold

/** Destination for the notification editor route (notificationId null creates). */
@Composable
fun SonarrNotificationEditorDestination(
    serverId: ServerId,
    notificationId: Int?,
    clients: SonarrConnectClients,
    serverName: String? = null,
    onBack: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val repository = remember(serverId, clients) { connectRepository(serverId, clients) }
    val vm: SonarrNotificationEditorViewModel = viewModel(
        key = "sonarr-notification-editor-" + serverId.value + "-" + (notificationId ?: "new"),
    ) {
        SonarrNotificationEditorViewModel(serverId, notificationId, repository)
    }
    val state by vm.state.collectAsState()
    val lce by vm.lce.collectAsState()

    SonarrNotificationEditorScreen(
        state = state,
        lce = lce,
        serverName = serverName,
        onBack = onBack,
        onPick = vm::pickSchema,
        onNameChange = { name -> vm.updateDraft { it.copy(name = name) } },
        onToggleEvent = vm::toggleEvent,
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
 * Notification add/edit. Add renders the definition picker first; after a
 * pick (or when editing) the form shows name, the event matrix restricted
 * to the events this implementation supports (grab, import, upgrade,
 * import complete, rename, series added and deleted, episode file
 * deletes, health, application update, manual interaction), tags, and the
 * per-connection dynamic fields. Test sends a probe through the
 * connection.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SonarrNotificationEditorScreen(
    state: SonarrNotificationEditorViewModel.UiState,
    lce: LceState<SonarrNotificationEditorViewModel.UiState>,
    serverName: String?,
    onBack: (() -> Unit)?,
    onPick: (NotificationResource) -> Unit,
    onNameChange: (String) -> Unit,
    onToggleEvent: (String) -> Unit,
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
            NotificationEditorNotice.SAVED ->
                stringResource(R.string.arr_sonarr_connect_notice_saved)

            NotificationEditorNotice.SAVED_QUEUED ->
                stringResource(R.string.arr_sonarr_connect_notice_saved_queued)

            NotificationEditorNotice.DELETED ->
                stringResource(R.string.arr_sonarr_connect_notice_deleted)

            NotificationEditorNotice.DELETED_QUEUED ->
                stringResource(R.string.arr_sonarr_connect_notice_deleted_queued)

            NotificationEditorNotice.TEST_PASSED ->
                stringResource(R.string.arr_sonarr_connect_notice_test_passed)

            null -> null
        },
        writeError = state.writeError,
        snackbarHostState = snackbarHostState,
        onConsumeNotice = onConsumeNotice,
        onConsumeWriteError = onConsumeWriteError,
    )

    ArrScaffold(
        title = stringResource(
            if (state.isNew) R.string.arr_sonarr_notification_editor_title_new
            else R.string.arr_sonarr_notification_editor_title,
        ),
        subtitle = serverName,
        onBack = onBack,
        actions = {
            if (state.draft != null) {
                IconButton(onClick = onTest, enabled = !state.testing) {
                    Icon(
                        imageVector = Icons.Filled.Science,
                        contentDescription = stringResource(R.string.arr_sonarr_connect_cd_test),
                    )
                }
                if (!state.isNew) {
                    val deleteCd = stringResource(R.string.arr_sonarr_connect_cd_delete)
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
                emptyTitle = stringResource(R.string.arr_sonarr_connect_picker_empty_title),
                emptyMessage = stringResource(R.string.arr_sonarr_connect_picker_empty_message),
                modifier = Modifier.fillMaxSize(),
            ) { data ->
                when {
                    data.picker -> NotificationSchemaPicker(
                        schemas = data.schemas,
                        onPick = onPick,
                    )

                    data.draft != null -> NotificationForm(
                        state = data,
                        onNameChange = onNameChange,
                        onToggleEvent = onToggleEvent,
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
            title = { Text(stringResource(R.string.arr_sonarr_connect_delete_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.arr_sonarr_connect_delete_message,
                        state.draft?.name.orEmpty(),
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
}

@Composable
private fun NotificationSchemaPicker(
    schemas: List<NotificationResource>,
    onPick: (NotificationResource) -> Unit,
) {
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.widthIn(max = ListMaxWidth + 32.dp),
    ) {
        item {
            Text(
                stringResource(R.string.arr_sonarr_connect_picker_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        items(schemas, key = { it.implementation ?: it.id.toString() }) { schema ->
            val cd = stringResource(
                R.string.arr_sonarr_connect_picker_cd_pick,
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
private fun NotificationForm(
    state: SonarrNotificationEditorViewModel.UiState,
    onNameChange: (String) -> Unit,
    onToggleEvent: (String) -> Unit,
    onValueChange: (String, kotlinx.serialization.json.JsonElement) -> Unit,
    onToggleTag: (String) -> Unit,
    onSave: () -> Unit,
) {
    val draft = state.draft ?: return
    var showAdvanced by rememberSaveable { mutableStateOf(false) }
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.widthIn(max = ListMaxWidth + 32.dp),
    ) {
        item {
            OutlinedTextField(
                value = draft.name,
                onValueChange = onNameChange,
                label = { Text(stringResource(R.string.arr_sonarr_connect_name_label)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item {
            Column {
                SectionHeader(title = stringResource(R.string.arr_sonarr_notification_section_events))
                draft.events.forEach { event ->
                    LabeledSwitch(
                        label = eventLabel(event.key),
                        checked = event.enabled,
                        onCheckedChange = { onToggleEvent(event.key) },
                    )
                }
                if (draft.onHealthIssue || draft.supportsOnHealthIssue) {
                    LabeledSwitch(
                        label = stringResource(
                            R.string.arr_sonarr_notification_event_include_warnings,
                        ),
                        checked = draft.includeHealthWarnings,
                        onCheckedChange = { onToggleEvent("includeHealthWarnings") },
                    )
                }
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
                Text(stringResource(R.string.arr_sonarr_connect_save))
            }
        }
    }
}

@Composable
private fun eventLabel(key: String): String = when (key) {
    "onGrab" -> stringResource(R.string.arr_sonarr_notification_event_grab)
    "onDownload" -> stringResource(R.string.arr_sonarr_notification_event_import)
    "onUpgrade" -> stringResource(R.string.arr_sonarr_notification_event_upgrade)
    "onImportComplete" -> stringResource(R.string.arr_sonarr_notification_event_import_complete)
    "onRename" -> stringResource(R.string.arr_sonarr_notification_event_rename)
    "onSeriesAdd" -> stringResource(R.string.arr_sonarr_notification_event_series_added)
    "onSeriesDelete" -> stringResource(R.string.arr_sonarr_notification_event_series_delete)
    "onEpisodeFileDelete" ->
        stringResource(R.string.arr_sonarr_notification_event_file_delete)

    "onEpisodeFileDeleteForUpgrade" ->
        stringResource(R.string.arr_sonarr_notification_event_file_delete_upgrade)

    "onHealthIssue" -> stringResource(R.string.arr_sonarr_notification_event_health)
    "onHealthRestored" -> stringResource(R.string.arr_sonarr_notification_event_health_restored)
    "onApplicationUpdate" ->
        stringResource(R.string.arr_sonarr_notification_event_application_update)

    "onManualInteractionRequired" ->
        stringResource(R.string.arr_sonarr_notification_event_manual_interaction)

    else -> key
}
