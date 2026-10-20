package dev.appolon.homelab.arr.feature.prowlarr.indexers.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.RemoveRedEye
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.unit.dp
import dev.appolon.homelab.arr.api.prowlarr.model.AppProfileResource
import dev.appolon.homelab.arr.api.prowlarr.model.ApplicationResource
import dev.appolon.homelab.arr.api.prowlarr.model.ApplicationSyncLevel
import dev.appolon.homelab.arr.feature.prowlarr.R
import dev.appolon.homelab.arr.feature.prowlarr.indexers.vm.ApplicationsMessage
import dev.appolon.homelab.arr.feature.prowlarr.indexers.vm.ApplicationsUiState
import dev.appolon.homelab.arr.feature.prowlarr.indexers.vm.SyncPreviewRow
import dev.appolon.homelab.arr.ui.lce.ArrLceBox
import dev.appolon.homelab.arr.ui.lce.ArrPullToRefreshBox
import dev.appolon.homelab.arr.ui.lce.LceState
import dev.appolon.homelab.arr.ui.lce.arrErrorMessage
import dev.appolon.homelab.arr.ui.components.LabeledSwitch
import dev.appolon.homelab.arr.ui.shell.ArrScaffold
import kotlinx.serialization.json.JsonElement

/**
 * Applications screen: sync targets for Radarr, Sonarr, and Lidarr with sync
 * level badges, a client-computed sync preview, and the app profile editors.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun ApplicationsScreen(
    state: ApplicationsUiState,
    lce: LceState<ApplicationsUiState>,
    onRefresh: () -> Unit,
    onBack: () -> Unit,
    onSyncAll: () -> Unit,
    onAdd: () -> Unit,
    onShowProfiles: (Boolean) -> Unit,
    onOpenSyncPreview: (ApplicationResource) -> Unit,
    onCloseSyncPreview: () -> Unit,
    onEdit: (ApplicationResource) -> Unit,
    onEditorClose: () -> Unit,
    onFieldValueChange: (String, JsonElement) -> Unit,
    onNameChange: (String) -> Unit,
    onSyncLevelChange: (ApplicationSyncLevel) -> Unit,
    onToggleTag: (String) -> Unit,
    onSave: () -> Unit,
    onTest: () -> Unit,
    onRequestDelete: (Int) -> Unit,
    onDeleteConfirm: () -> Unit,
    onDeleteDismiss: () -> Unit,
    onOpenProfileEditor: (AppProfileResource?) -> Unit,
    onCloseProfileEditor: () -> Unit,
    onProfileNameChange: (String) -> Unit,
    onProfileMinimumSeedersChange: (Int) -> Unit,
    onProfileEnableRssChange: (Boolean) -> Unit,
    onProfileEnableAutomaticSearchChange: (Boolean) -> Unit,
    onProfileEnableInteractiveSearchChange: (Boolean) -> Unit,
    onProfileSave: () -> Unit,
    onProfileRequestDelete: (Int) -> Unit,
    onProfileDeleteConfirm: () -> Unit,
    onProfileDeleteDismiss: () -> Unit,
    onConsumeMessage: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val messageText = state.message?.let { applicationsMessageText(it) }
    LaunchedEffect(messageText) {
        if (messageText != null) {
            snackbarHostState.showSnackbar(messageText)
            onConsumeMessage()
        }
    }

    ArrScaffold(
        title = stringResource(R.string.arr_prowlarr_applications_title),
        onBack = onBack,
        actions = {
            IconButton(onClick = onSyncAll) {
                Icon(
                    imageVector = Icons.Filled.Sync,
                    contentDescription = stringResource(R.string.arr_prowlarr_applications_sync_all),
                )
            }
            IconButton(onClick = { onShowProfiles(true) }) {
                Icon(
                    imageVector = Icons.Filled.Tune,
                    contentDescription = stringResource(R.string.arr_prowlarr_applications_profiles),
                )
            }
        },
        modifier = modifier,
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            ArrPullToRefreshBox(
                isRefreshing = state.refreshing,
                onRefresh = onRefresh,
                modifier = Modifier.fillMaxSize(),
            ) {
                ArrLceBox(
                    state = lce,
                    onRetry = onRefresh,
                    emptyTitle = stringResource(R.string.arr_prowlarr_applications_empty),
                    modifier = Modifier.fillMaxSize(),
                ) { content ->
                    LazyColumn(
                        contentPadding = PaddingValues(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        items(content.applications, key = { it.id }) { application ->
                            ApplicationCard(
                                application = application,
                                state = content,
                                onOpenSyncPreview = onOpenSyncPreview,
                                onEdit = onEdit,
                                onRequestDelete = onRequestDelete,
                            )
                        }
                    }
                }
            }
            ExtendedFloatingActionButton(
                onClick = onAdd,
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.arr_prowlarr_applications_add)) },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp),
            )
        }
        SnackbarHost(hostState = snackbarHostState) { data ->
            Snackbar(snackbarData = data)
        }
    }

    state.syncPreviewFor?.let { application ->
        SyncPreviewDialog(
            application = application,
            rows = state.syncPreview,
            onDismiss = onCloseSyncPreview,
        )
    }
    if (state.editor != null) {
        ApplicationEditorSheet(
            state = state,
            onFieldValueChange = onFieldValueChange,
            onNameChange = onNameChange,
            onSyncLevelChange = onSyncLevelChange,
            onToggleTag = onToggleTag,
            onSave = onSave,
            onTest = onTest,
            onDismiss = onEditorClose,
        )
    }
    if (state.profilesShowing && state.profileEditor == null) {
        ProfilesSheet(
            state = state,
            onShow = onShowProfiles,
            onEdit = onOpenProfileEditor,
            onRequestDelete = onProfileRequestDelete,
        )
    }
    if (state.profileEditor != null) {
        AppProfileEditorSheet(
            state = state,
            onNameChange = onProfileNameChange,
            onMinimumSeedersChange = onProfileMinimumSeedersChange,
            onEnableRssChange = onProfileEnableRssChange,
            onEnableAutomaticSearchChange = onProfileEnableAutomaticSearchChange,
            onEnableInteractiveSearchChange = onProfileEnableInteractiveSearchChange,
            onSave = onProfileSave,
            onDismiss = onCloseProfileEditor,
        )
    }
    state.confirmDeleteId?.let {
        ConfirmDialog(
            title = stringResource(R.string.arr_prowlarr_applications_delete_title),
            message = stringResource(R.string.arr_prowlarr_applications_delete_message),
            onConfirm = onDeleteConfirm,
            onDismiss = onDeleteDismiss,
        )
    }
    state.confirmProfileDeleteId?.let {
        ConfirmDialog(
            title = stringResource(R.string.arr_prowlarr_profiles_delete_title),
            message = stringResource(R.string.arr_prowlarr_profiles_delete_message),
            onConfirm = onProfileDeleteConfirm,
            onDismiss = onProfileDeleteDismiss,
        )
    }
}

@Composable
private fun ApplicationCard(
    application: ApplicationResource,
    state: ApplicationsUiState,
    onOpenSyncPreview: (ApplicationResource) -> Unit,
    onEdit: (ApplicationResource) -> Unit,
    onRequestDelete: (Int) -> Unit,
) {
    val editCd = stringResource(R.string.arr_prowlarr_cd_edit_application, application.name.orEmpty())
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        application.name.orEmpty(),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        application.implementationName.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = { onOpenSyncPreview(application) }) {
                    Icon(
                        imageVector = Icons.Filled.RemoveRedEye,
                        contentDescription = stringResource(
                            R.string.arr_prowlarr_cd_sync_preview,
                            application.name.orEmpty(),
                        ),
                    )
                }
                IconButton(onClick = { onEdit(application) }) {
                    Icon(
                        imageVector = Icons.Filled.Edit,
                        contentDescription = editCd,
                    )
                }
                IconButton(onClick = { onRequestDelete(application.id) }) {
                    Icon(
                        imageVector = Icons.Filled.Delete,
                        contentDescription = stringResource(
                            R.string.arr_prowlarr_cd_delete_application,
                            application.name.orEmpty(),
                        ),
                    )
                }
            }
            AssistChip(
                onClick = {},
                enabled = false,
                label = { Text(syncLevelLabel(application.syncLevel)) },
            )
            TagChips(
                tags = application.tags.orEmpty(),
                allTags = state.tags,
            )
        }
    }
}

@Composable
private fun SyncPreviewDialog(
    application: ApplicationResource,
    rows: List<SyncPreviewRow>,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.arr_prowlarr_sync_preview_title, application.name.orEmpty())) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    stringResource(R.string.arr_prowlarr_sync_preview_message, syncLevelLabel(application.syncLevel)),
                    style = MaterialTheme.typography.bodySmall,
                )
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 320.dp),
                ) {
                    items(rows, key = { it.indexerName }) { row ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp),
                        ) {
                            Text(
                                row.indexerName,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                row.appProfileLabel,
                                style = MaterialTheme.typography.bodySmall,
                                color = if (row.enabled) {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                } else {
                                    MaterialTheme.colorScheme.error
                                },
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.arr_prowlarr_ok))
            }
        },
    )
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun ApplicationEditorSheet(
    state: ApplicationsUiState,
    onFieldValueChange: (String, JsonElement) -> Unit,
    onNameChange: (String) -> Unit,
    onSyncLevelChange: (ApplicationSyncLevel) -> Unit,
    onToggleTag: (String) -> Unit,
    onSave: () -> Unit,
    onTest: () -> Unit,
    onDismiss: () -> Unit,
) {
    val form = state.editor ?: return
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                stringResource(
                    if (form.editing != null) R.string.arr_prowlarr_applications_edit
                    else R.string.arr_prowlarr_applications_add,
                ),
                style = MaterialTheme.typography.titleMedium,
            )
            OutlinedTextField(
                value = form.name,
                onValueChange = onNameChange,
                label = { Text(stringResource(R.string.arr_prowlarr_editor_name)) },
                isError = form.nameMissing,
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                stringResource(R.string.arr_prowlarr_sync_level),
                style = MaterialTheme.typography.bodyMedium,
            )
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                val levels = listOf(
                    ApplicationSyncLevel.DISABLED,
                    ApplicationSyncLevel.ADD_ONLY,
                    ApplicationSyncLevel.FULL_SYNC,
                )
                levels.forEachIndexed { index, level ->
                    SegmentedButton(
                        selected = form.syncLevel == level,
                        onClick = { onSyncLevelChange(level) },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = levels.size),
                        label = { Text(syncLevelLabel(level)) },
                    )
                }
            }
            FieldEditors(
                fields = form.fields,
                values = form.values,
                onValueChange = onFieldValueChange,
                tagOptions = state.tags,
                showAdvanced = true,
            )
            TagLabelPicker(
                allTags = state.tags,
                selected = form.tagLabels,
                onToggle = onToggleTag,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onDismiss, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.arr_prowlarr_cancel))
                }
                TextButton(onClick = onTest, enabled = !state.testing, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.arr_prowlarr_test))
                }
                Button(onClick = onSave, enabled = !state.saving, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.arr_prowlarr_save))
                }
            }
        }
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun ProfilesSheet(
    state: ApplicationsUiState,
    onShow: (Boolean) -> Unit,
    onEdit: (AppProfileResource?) -> Unit,
    onRequestDelete: (Int) -> Unit,
) {
    ModalBottomSheet(onDismissRequest = { onShow(false) }) {
        Column(
            Modifier
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.arr_prowlarr_profiles_title),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { onEdit(null) }) {
                    Icon(
                        imageVector = Icons.Filled.Add,
                        contentDescription = stringResource(R.string.arr_prowlarr_profiles_add),
                    )
                }
            }
            state.appProfiles.forEach { profile ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp),
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(profile.name.orEmpty(), style = MaterialTheme.typography.bodyLarge)
                        Text(
                            stringResource(
                                R.string.arr_prowlarr_profiles_summary,
                                profile.minimumSeeders,
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = { onEdit(profile) }) {
                        Icon(
                            imageVector = Icons.Filled.Edit,
                            contentDescription = stringResource(
                                R.string.arr_prowlarr_cd_edit_profile,
                                profile.name.orEmpty(),
                            ),
                        )
                    }
                    IconButton(onClick = { onRequestDelete(profile.id) }) {
                        Icon(
                            imageVector = Icons.Filled.Delete,
                            contentDescription = stringResource(
                                R.string.arr_prowlarr_cd_delete_profile,
                                profile.name.orEmpty(),
                            ),
                        )
                    }
                }
            }
        }
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun AppProfileEditorSheet(
    state: ApplicationsUiState,
    onNameChange: (String) -> Unit,
    onMinimumSeedersChange: (Int) -> Unit,
    onEnableRssChange: (Boolean) -> Unit,
    onEnableAutomaticSearchChange: (Boolean) -> Unit,
    onEnableInteractiveSearchChange: (Boolean) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
) {
    val form = state.profileEditor ?: return
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                stringResource(
                    if (form.editing != null) R.string.arr_prowlarr_profiles_edit
                    else R.string.arr_prowlarr_profiles_add,
                ),
                style = MaterialTheme.typography.titleMedium,
            )
            OutlinedTextField(
                value = form.name,
                onValueChange = onNameChange,
                label = { Text(stringResource(R.string.arr_prowlarr_editor_name)) },
                isError = form.nameMissing,
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            var seedersText by remember(form.editing?.id) {
                mutableStateOf(form.minimumSeeders.toString())
            }
            OutlinedTextField(
                value = seedersText,
                onValueChange = { text ->
                    seedersText = text
                    text.trim().toIntOrNull()?.let(onMinimumSeedersChange)
                },
                label = { Text(stringResource(R.string.arr_prowlarr_profiles_minimum_seeders)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            LabeledSwitch(
                label = stringResource(R.string.arr_prowlarr_profiles_enable_rss),
                checked = form.enableRss,
                onCheckedChange = onEnableRssChange,
            )
            LabeledSwitch(
                label = stringResource(R.string.arr_prowlarr_profiles_enable_automatic_search),
                checked = form.enableAutomaticSearch,
                onCheckedChange = onEnableAutomaticSearchChange,
            )
            LabeledSwitch(
                label = stringResource(R.string.arr_prowlarr_profiles_enable_interactive_search),
                checked = form.enableInteractiveSearch,
                onCheckedChange = onEnableInteractiveSearchChange,
            )
            Button(onClick = onSave, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.arr_prowlarr_save))
            }
        }
    }
}

@Composable
private fun ConfirmDialog(
    title: String,
    message: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.arr_prowlarr_delete))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.arr_prowlarr_cancel))
            }
        },
    )
}

/** One-line snackbar text for an applications message. */
@Composable
internal fun applicationsMessageText(message: ApplicationsMessage): String = when (message) {
    ApplicationsMessage.Saved -> stringResource(R.string.arr_prowlarr_message_saved)
    ApplicationsMessage.SavedQueued -> stringResource(R.string.arr_prowlarr_message_saved_queued)
    ApplicationsMessage.Deleted -> stringResource(R.string.arr_prowlarr_message_delete_synced)
    ApplicationsMessage.DeleteQueued -> stringResource(R.string.arr_prowlarr_message_delete_queued)
    ApplicationsMessage.TestPassed -> stringResource(R.string.arr_prowlarr_message_test_passed)
    is ApplicationsMessage.TestFailed ->
        stringResource(R.string.arr_prowlarr_message_test_failed, arrErrorMessage(message.error))

    ApplicationsMessage.SyncStarted ->
        stringResource(R.string.arr_prowlarr_message_sync_started)

    ApplicationsMessage.SyncQueued ->
        stringResource(R.string.arr_prowlarr_message_sync_queued)

    is ApplicationsMessage.SyncFailed ->
        stringResource(R.string.arr_prowlarr_message_write_failed, arrErrorMessage(message.error))

    ApplicationsMessage.ProfileSaved ->
        stringResource(R.string.arr_prowlarr_message_profile_saved)

    ApplicationsMessage.ProfileDeleted ->
        stringResource(R.string.arr_prowlarr_message_profile_deleted)

    is ApplicationsMessage.WriteFailed ->
        stringResource(R.string.arr_prowlarr_message_write_failed, arrErrorMessage(message.error))

    ApplicationsMessage.NameMissing -> stringResource(R.string.arr_prowlarr_editor_name_missing)
}
