package dev.appolon.homelab.arr.feature.radarr.settings.quality.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.appolon.homelab.arr.api.radarr.model.ReleaseProfileResource
import dev.appolon.homelab.arr.core.ServerId
import dev.appolon.homelab.arr.feature.radarr.R
import dev.appolon.homelab.arr.feature.radarr.settings.quality.RadarrQualityClients
import dev.appolon.homelab.arr.feature.radarr.settings.quality.vm.ReleaseProfileDraft
import dev.appolon.homelab.arr.feature.radarr.settings.quality.vm.ReleaseProfilesNotice
import dev.appolon.homelab.arr.feature.radarr.settings.quality.vm.RadarrReleaseProfilesContent
import dev.appolon.homelab.arr.feature.radarr.settings.quality.vm.RadarrReleaseProfilesViewModel
import dev.appolon.homelab.arr.ui.components.LabeledSwitch
import dev.appolon.homelab.arr.ui.lce.ArrLceBox
import dev.appolon.homelab.arr.ui.lce.ArrPullToRefreshBox
import dev.appolon.homelab.arr.ui.lce.LceState
import dev.appolon.homelab.arr.ui.shell.ArrScaffold
import dev.appolon.homelab.arr.ui.R as ArrUiR

/** Destination for the release profiles route. */
@Composable
fun RadarrReleaseProfilesDestination(
    serverId: ServerId,
    clients: RadarrQualityClients,
    serverName: String? = null,
    onBack: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val repository = remember(serverId, clients) { qualityRepository(serverId, clients) }
    val vm: RadarrReleaseProfilesViewModel =
        viewModel(key = "radarr-release-profiles-" + serverId.value) {
            RadarrReleaseProfilesViewModel(repository)
        }
    val state by vm.state.collectAsState()
    val lce by vm.lce.collectAsState()

    RadarrReleaseProfilesScreen(
        state = state,
        lce = lce,
        serverName = serverName,
        onBack = onBack,
        onRefresh = vm::refresh,
        onSave = vm::save,
        onRequestDelete = vm::requestDelete,
        onDeleteConfirmed = vm::deleteConfirmed,
        onConsumeNotice = vm::consumeNotice,
        onConsumeWriteError = vm::consumeWriteError,
        modifier = modifier,
    )
}

/**
 * Release profiles screen: the profile list plus an inline editor for the
 * must-contain and must-not-contain term lists, the optional indexer
 * restriction, and the enabled toggle. Terms are edited one per line.
 */
@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun RadarrReleaseProfilesScreen(
    state: RadarrReleaseProfilesViewModel.UiState,
    lce: LceState<RadarrReleaseProfilesContent>,
    serverName: String?,
    onBack: (() -> Unit)?,
    onRefresh: () -> Unit,
    onSave: (ReleaseProfileDraft) -> Unit,
    onRequestDelete: (Int?) -> Unit,
    onDeleteConfirmed: () -> Unit,
    onConsumeNotice: () -> Unit,
    onConsumeWriteError: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val content = (lce as? LceState.Content<RadarrReleaseProfilesContent>)?.data
    val refreshing = content != null && (lce as LceState.Content<RadarrReleaseProfilesContent>).refreshing
    /** Non-null opens the editor dialog: a draft of an existing row or a blank one. */
    var editorDraft by remember { mutableStateOf<ReleaseProfileDraft?>(null) }
    val onAdd: () -> Unit = { editorDraft = ReleaseProfileDraft() }

    val noticeMessage = when (state.notice) {
        ReleaseProfilesNotice.CREATED -> stringResource(ArrUiR.string.arr_common_quality_notice_created)
        ReleaseProfilesNotice.UPDATED -> stringResource(ArrUiR.string.arr_common_system_notice_saved)
        ReleaseProfilesNotice.DELETED -> stringResource(ArrUiR.string.arr_common_connect_notice_deleted)
        ReleaseProfilesNotice.QUEUED ->
            stringResource(ArrUiR.string.arr_common_system_notice_saved_queued)
        null -> null
    }
    QualityNoticeHost(
        noticeMessage = noticeMessage,
        writeError = state.writeError,
        snackbarHostState = snackbarHostState,
        onConsumeNotice = onConsumeNotice,
        onConsumeWriteError = onConsumeWriteError,
    )

    ArrScaffold(
        title = stringResource(ArrUiR.string.arr_common_quality_release_title),
        subtitle = serverName,
        onBack = onBack,
        actions = {
            IconButton(onClick = onAdd) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = stringResource(ArrUiR.string.arr_common_quality_cd_add_release),
                )
            }
            IconButton(onClick = onRefresh) {
                Icon(
                    imageVector = Icons.Filled.Refresh,
                    contentDescription = stringResource(ArrUiR.string.arr_common_quality_cd_refresh),
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
                    emptyTitle = stringResource(ArrUiR.string.arr_common_quality_release_empty_title),
                    emptyMessage = stringResource(ArrUiR.string.arr_common_quality_release_empty_message),
                    modifier = Modifier.fillMaxSize(),
                ) { data ->
                    LazyColumn(
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                        modifier = Modifier.widthIn(max = SectionMaxWidth + 32.dp),
                    ) {
                        if (data.pendingActions > 0) {
                            item { QualityPendingSyncBanner(data.pendingActions) }
                        }
                        itemsIndexed(
                            data.profiles,
                            key = { _, profile -> if (profile.id > 0) profile.id else profile.hashCode() },
                            contentType = { _, _ -> "profile" },
                        ) { _, profile ->
                            ReleaseProfileRow(
                                profile = profile,
                                onEdit = { editorDraft = ReleaseProfileDraft.from(profile) },
                                onDelete = { onRequestDelete(profile.id) },
                            )
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

    editorDraft?.let { draft ->
        ReleaseProfileEditorDialog(
            draft = draft,
            onDismiss = { editorDraft = null },
            onSave = { saved ->
                onSave(saved)
                editorDraft = null
            },
        )
    }

    if (state.confirmDeleteId != null) {
        AlertDialog(
            onDismissRequest = { onRequestDelete(null) },
            title = { Text(stringResource(ArrUiR.string.arr_common_quality_delete_release_title)) },
            text = { Text(stringResource(ArrUiR.string.arr_common_quality_delete_release_message)) },
            confirmButton = {
                TextButton(onClick = onDeleteConfirmed) {
                    Text(stringResource(ArrUiR.string.arr_common_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { onRequestDelete(null) }) {
                    Text(stringResource(ArrUiR.string.arr_common_cancel))
                }
            },
        )
    }
}

@Composable
private fun ReleaseProfileRow(
    profile: ReleaseProfileResource,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .padding(vertical = 12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(profile.name.orEmpty(), style = MaterialTheme.typography.bodyLarge)
            Text(
                text = if (profile.enabled) {
                    stringResource(ArrUiR.string.arr_common_general_cert_validation_enabled)
                } else {
                    stringResource(ArrUiR.string.arr_common_general_cert_validation_disabled)
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onEdit) {
            Icon(
                imageVector = Icons.Filled.Edit,
                contentDescription = stringResource(
                    ArrUiR.string.arr_common_quality_cd_edit_release,
                    profile.name.orEmpty(),
                ),
            )
        }
        IconButton(onClick = onDelete) {
            Icon(
                imageVector = Icons.Filled.Delete,
                contentDescription = stringResource(
                    ArrUiR.string.arr_common_quality_cd_delete_release,
                    profile.name.orEmpty(),
                ),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ReleaseProfileEditorDialog(
    draft: ReleaseProfileDraft,
    onDismiss: () -> Unit,
    onSave: (ReleaseProfileDraft) -> Unit,
) {
    var name by remember { mutableStateOf(draft.name) }
    var enabled by remember { mutableStateOf(draft.enabled) }
    var mustContain by remember { mutableStateOf(draft.mustContain.joinToString("\n")) }
    var mustNotContain by remember { mutableStateOf(draft.mustNotContain.joinToString("\n")) }
    var indexerId by remember { mutableStateOf(draft.indexerId) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(ArrUiR.string.arr_common_quality_release_editor_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                QualityTextField(
                    value = name,
                    label = stringResource(ArrUiR.string.arr_common_autotag_name),
                    onValue = { name = it },
                )
                QualityTextField(
                    value = mustContain,
                    label = stringResource(ArrUiR.string.arr_common_quality_release_must_contain),
                    supporting = stringResource(ArrUiR.string.arr_common_quality_release_terms_support),
                    onValue = { mustContain = it },
                    minLines = 2,
                )
                QualityTextField(
                    value = mustNotContain,
                    label = stringResource(ArrUiR.string.arr_common_quality_release_must_not_contain),
                    supporting = stringResource(ArrUiR.string.arr_common_quality_release_terms_support),
                    onValue = { mustNotContain = it },
                    minLines = 2,
                )
                QualityTextField(
                    value = indexerId,
                    label = stringResource(ArrUiR.string.arr_common_quality_release_indexer),
                    supporting = stringResource(ArrUiR.string.arr_common_quality_release_indexer_support),
                    onValue = { indexerId = it },
                )
                LabeledSwitch(
                    label = stringResource(ArrUiR.string.arr_common_general_cert_validation_enabled),
                    checked = enabled,
                    onCheckedChange = { enabled = it },
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(
                        draft.copy(
                            name = name,
                            enabled = enabled,
                            mustContain = ReleaseProfileDraft.splitTerms(mustContain),
                            mustNotContain = ReleaseProfileDraft.splitTerms(mustNotContain),
                            indexerId = indexerId,
                        ),
                    )
                },
                enabled = name.isNotBlank(),
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
