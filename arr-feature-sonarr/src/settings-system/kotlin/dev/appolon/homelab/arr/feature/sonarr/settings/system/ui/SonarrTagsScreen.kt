package dev.appolon.homelab.arr.feature.sonarr.settings.system.ui

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
import androidx.compose.material.icons.automirrored.filled.Label
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.appolon.homelab.arr.api.sonarr.model.TagResource
import dev.appolon.homelab.arr.core.ArrError
import dev.appolon.homelab.arr.core.ServerId
import dev.appolon.homelab.arr.feature.sonarr.R
import dev.appolon.homelab.arr.feature.sonarr.settings.system.SonarrSettingsSystemClients
import dev.appolon.homelab.arr.feature.sonarr.settings.system.vm.SonarrTagRow
import dev.appolon.homelab.arr.feature.sonarr.settings.system.vm.SonarrTagsContent
import dev.appolon.homelab.arr.feature.sonarr.settings.system.vm.SonarrTagsViewModel
import dev.appolon.homelab.arr.feature.sonarr.settings.system.vm.TagsNotice
import dev.appolon.homelab.arr.ui.lce.ArrLceBox
import dev.appolon.homelab.arr.ui.lce.ArrPullToRefreshBox
import dev.appolon.homelab.arr.ui.lce.LceState
import dev.appolon.homelab.arr.ui.shell.ArrScaffold
import dev.appolon.homelab.arr.ui.R as ArrUiR

/** Destination for the tags route. */
@Composable
fun SonarrTagsDestination(
    serverId: ServerId,
    clients: SonarrSettingsSystemClients,
    serverName: String? = null,
    onBack: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val repository = remember(serverId, clients) { systemRepository(serverId, clients) }
    val vm: SonarrTagsViewModel =
        viewModel(key = "sonarr-system-tags-" + serverId.value) {
            SonarrTagsViewModel(repository)
        }
    val lce by vm.lce.collectAsState()
    val uiState by vm.state.collectAsState()

    TagsConfirmDialogs(
        state = uiState,
        onDeleteCancelled = vm::deleteCancelled,
        onDeleteConfirmed = vm::deleteConfirmed,
    )

    SonarrTagsScreen(
        lce = lce,
        serverName = serverName,
        onBack = onBack,
        onRefresh = vm::refresh,
        onAdd = vm::startCreate,
        onRename = vm::startRename,
        onDelete = vm::requestDelete,
        noticeMessage = uiState.notice?.let { notice -> tagsNoticeText(notice) },
        writeError = uiState.writeError,
        onConsumeNotice = vm::consumeNotice,
        onConsumeWriteError = vm::consumeWriteError,
        modifier = modifier,
    )

    TagEditorDialog(
        state = uiState,
        labelValid = vm.editorLabelValid(),
        onLabel = vm::editLabel,
        onConfirm = vm::editorConfirmed,
        onDismiss = vm::editorCancelled,
    )
}

/**
 * The tags page: each row shows the label and its usage from TagDetails.
 * Create and rename share one dialog; delete confirms first. Rows with a
 * queued offline removal keep their place with a queued badge.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SonarrTagsScreen(
    lce: LceState<SonarrTagsContent>,
    serverName: String?,
    onBack: (() -> Unit)?,
    onRefresh: () -> Unit,
    onAdd: () -> Unit,
    onRename: (TagResource) -> Unit,
    onDelete: (TagResource) -> Unit,
    noticeMessage: String?,
    writeError: ArrError?,
    onConsumeNotice: () -> Unit,
    onConsumeWriteError: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val snackbarHostState = remember { SnackbarHostState() }

    SystemNoticeHost(
        noticeMessage = noticeMessage,
        writeError = writeError,
        snackbarHostState = snackbarHostState,
        onConsumeNotice = onConsumeNotice,
        onConsumeWriteError = onConsumeWriteError,
    )

    ArrScaffold(
        title = stringResource(ArrUiR.string.arr_common_tags_title),
        subtitle = serverName,
        onBack = onBack,
        actions = {
            IconButton(onClick = onRefresh) {
                Icon(
                    imageVector = Icons.Filled.Refresh,
                    contentDescription = stringResource(ArrUiR.string.arr_common_system_cd_refresh),
                )
            }
            IconButton(onClick = onAdd) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = stringResource(ArrUiR.string.arr_common_tags_cd_add),
                )
            }
        },
        modifier = modifier,
    ) { padding ->
        ArrPullToRefreshBox(
            isRefreshing = lce is LceState.Content && (lce as LceState.Content<*>).refreshing,
            onRefresh = onRefresh,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            Box(Modifier.fillMaxSize()) {
                ArrLceBox(
                    state = lce,
                    onRetry = onRefresh,
                    emptyTitle = stringResource(ArrUiR.string.arr_common_tags_empty_title),
                    emptyMessage = stringResource(R.string.arr_sonarr_tags_empty_message),
                    modifier = Modifier.fillMaxSize(),
                ) { data ->
                    LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        modifier = Modifier.widthIn(max = SectionMaxWidth + 32.dp),
                    ) {
                        if (data.pendingActions > 0) {
                            item { SystemPendingSyncBanner(data.pendingActions) }
                        }
                        items(data.rows, key = { it.tag.id }, contentType = { "tag" }) { row ->
                            TagRow(row, onRename, onDelete)
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
}

@Composable
private fun TagRow(
    row: SonarrTagRow,
    onRename: (TagResource) -> Unit,
    onDelete: (TagResource) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.Label,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp, vertical = 12.dp),
            ) {
                Text(row.label, style = MaterialTheme.typography.bodyLarge)
                val support = if (row.queuedForRemoval) {
                    stringResource(ArrUiR.string.arr_common_system_cd_queued_indicator)
                } else {
                    row.usageSummary()
                }
                Text(
                    support,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = { onRename(row.tag) }) {
                Icon(
                    imageVector = Icons.Filled.Edit,
                    contentDescription = stringResource(
                        ArrUiR.string.arr_common_tags_cd_rename,
                        row.label,
                    ),
                )
            }
            IconButton(onClick = { onDelete(row.tag) }) {
                Icon(
                    imageVector = Icons.Filled.Delete,
                    contentDescription = stringResource(
                        ArrUiR.string.arr_common_tags_cd_delete,
                        row.label,
                    ),
                )
            }
        }
    }
}

/**
 * Builds the per-row usage summary from TagDetails counts through string
 * resources; unused tags render their dedicated label.
 */
@Composable
private fun SonarrTagRow.usageSummary(): String {
    val texts = mutableListOf<String>()
    if (seriesCount > 0) {
        texts += stringResource(R.string.arr_sonarr_tags_usage_series, seriesCount)
    }
    if (importListCount > 0) {
        texts += stringResource(ArrUiR.string.arr_common_tags_usage_import_lists, importListCount)
    }
    if (notificationCount > 0) {
        texts += stringResource(ArrUiR.string.arr_common_tags_usage_notifications, notificationCount)
    }
    if (releaseProfileCount > 0) {
        texts += stringResource(ArrUiR.string.arr_common_tags_usage_release_profiles, releaseProfileCount)
    }
    if (excludedReleaseProfileCount > 0) {
        texts += stringResource(
            R.string.arr_sonarr_tags_usage_excluded_release_profiles,
            excludedReleaseProfileCount,
        )
    }
    if (indexerCount > 0) {
        texts += stringResource(ArrUiR.string.arr_common_tags_usage_indexers, indexerCount)
    }
    if (downloadClientCount > 0) {
        texts += stringResource(ArrUiR.string.arr_common_tags_usage_download_clients, downloadClientCount)
    }
    if (delayProfileCount > 0) {
        texts += stringResource(ArrUiR.string.arr_common_tags_usage_delay_profiles, delayProfileCount)
    }
    if (autoTagCount > 0) {
        texts += stringResource(ArrUiR.string.arr_common_tags_usage_auto_tags, autoTagCount)
    }
    return if (texts.isEmpty()) {
        stringResource(ArrUiR.string.arr_common_tags_usage_unused)
    } else {
        texts.joinToString(", ")
    }
}

@Composable
private fun tagsNoticeText(notice: TagsNotice): String = when (notice) {
    TagsNotice.CREATED -> stringResource(ArrUiR.string.arr_common_tags_notice_created)
    TagsNotice.CREATED_QUEUED -> stringResource(ArrUiR.string.arr_common_system_notice_saved_queued)
    TagsNotice.RENAMED -> stringResource(ArrUiR.string.arr_common_tags_notice_renamed)
    TagsNotice.RENAMED_QUEUED -> stringResource(ArrUiR.string.arr_common_system_notice_saved_queued)
    TagsNotice.DELETED -> stringResource(ArrUiR.string.arr_common_tags_notice_deleted)
    TagsNotice.DELETED_QUEUED -> stringResource(ArrUiR.string.arr_common_system_notice_saved_queued)
}

@Composable
private fun TagEditorDialog(
    state: SonarrTagsViewModel.UiState,
    labelValid: Boolean,
    onLabel: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    if (!state.editorOpen) return
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (state.editingTagId == null) {
                    stringResource(ArrUiR.string.arr_common_tags_add_title)
                } else {
                    stringResource(ArrUiR.string.arr_common_tags_rename_title)
                },
            )
        },
        text = {
            Column {
                SystemTextField(
                    value = state.editingLabel,
                    label = stringResource(ArrUiR.string.arr_common_tags_label),
                    onValue = onLabel,
                    isError = state.editingLabel.isNotBlank() && !labelValid,
                    errorMessage = stringResource(ArrUiR.string.arr_common_tags_label_error),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = labelValid) {
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

@Composable
private fun TagsConfirmDialogs(
    state: SonarrTagsViewModel.UiState,
    onDeleteCancelled: () -> Unit,
    onDeleteConfirmed: () -> Unit,
) {
    state.confirmDeleteFor?.let { tag ->
        AlertDialog(
            onDismissRequest = onDeleteCancelled,
            title = { Text(stringResource(ArrUiR.string.arr_common_tags_delete_title)) },
            text = { Text(stringResource(R.string.arr_sonarr_tags_delete_message)) },
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
