package dev.appolon.homelab.arr.feature.sonarr.settings.system.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.NavigateNext
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
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
import dev.appolon.homelab.arr.api.sonarr.model.AutoTaggingResource
import dev.appolon.homelab.arr.core.ArrError
import dev.appolon.homelab.arr.core.ServerId
import dev.appolon.homelab.arr.feature.sonarr.R
import dev.appolon.homelab.arr.feature.sonarr.settings.system.SonarrSettingsSystemClients
import dev.appolon.homelab.arr.feature.sonarr.settings.system.vm.AutoTaggingNotice
import dev.appolon.homelab.arr.feature.sonarr.settings.system.vm.SonarrAutoTaggingContent
import dev.appolon.homelab.arr.feature.sonarr.settings.system.vm.SonarrAutoTaggingViewModel
import dev.appolon.homelab.arr.ui.lce.ArrLceBox
import dev.appolon.homelab.arr.ui.lce.ArrPullToRefreshBox
import dev.appolon.homelab.arr.ui.lce.LceState
import dev.appolon.homelab.arr.ui.shell.ArrScaffold
import dev.appolon.homelab.arr.ui.R as ArrUiR

/** Destination for the auto tagging list route. */
@Composable
fun SonarrAutoTaggingDestination(
    serverId: ServerId,
    clients: SonarrSettingsSystemClients,
    serverName: String? = null,
    onBack: (() -> Unit)? = null,
    onOpenEditor: (ruleId: Int?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val repository = remember(serverId, clients) { systemRepository(serverId, clients) }
    val vm: SonarrAutoTaggingViewModel =
        viewModel(key = "sonarr-system-autotagging-" + serverId.value) {
            SonarrAutoTaggingViewModel(repository)
        }
    val lce by vm.lce.collectAsState()
    val uiState by vm.state.collectAsState()

    AutoTaggingConfirmDialog(
        state = uiState,
        onDeleteCancelled = vm::deleteCancelled,
        onDeleteConfirmed = vm::deleteConfirmed,
    )

    SonarrAutoTaggingScreen(
        lce = lce,
        serverName = serverName,
        onBack = onBack,
        onRefresh = vm::refresh,
        onAdd = { onOpenEditor(null) },
        onOpen = { rule -> onOpenEditor(rule.id) },
        onDelete = vm::requestDelete,
        noticeMessage = uiState.notice?.let { notice -> autoTaggingNoticeText(notice) },
        writeError = uiState.writeError,
        onConsumeNotice = vm::consumeNotice,
        onConsumeWriteError = vm::consumeWriteError,
        modifier = modifier,
    )
}

/**
 * The auto tagging list: each rule row shows its name, applied tags, and
 * condition count, linking to the editor. Delete confirms first.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SonarrAutoTaggingScreen(
    lce: LceState<SonarrAutoTaggingContent>,
    serverName: String?,
    onBack: (() -> Unit)?,
    onRefresh: () -> Unit,
    onAdd: () -> Unit,
    onOpen: (AutoTaggingResource) -> Unit,
    onDelete: (AutoTaggingResource) -> Unit,
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
        title = stringResource(ArrUiR.string.arr_common_autotag_title),
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
                    contentDescription = stringResource(ArrUiR.string.arr_common_autotag_cd_add),
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
                    emptyTitle = stringResource(ArrUiR.string.arr_common_autotag_empty_title),
                    emptyMessage = stringResource(ArrUiR.string.arr_common_autotag_empty_message),
                    modifier = Modifier.fillMaxSize(),
                ) { data ->
                    LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        modifier = Modifier.widthIn(max = SectionMaxWidth + 32.dp),
                    ) {
                        if (data.pendingActions > 0) {
                            item { SystemPendingSyncBanner(data.pendingActions) }
                        }
                        items(
                            data.rows,
                            key = { it.rule.id },
                            contentType = { "auto-tagging" },
                        ) { row ->
                            AutoTaggingRow(
                                name = row.name,
                                tagLabels = row.tagLabels,
                                conditionCount = row.conditionCount,
                                queuedForRemoval = row.queuedForRemoval,
                                onOpen = { onOpen(row.rule) },
                                onDelete = { onDelete(row.rule) },
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
}

@Composable
private fun AutoTaggingRow(
    name: String,
    tagLabels: List<String>,
    conditionCount: Int,
    queuedForRemoval: Boolean,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
) {
    SystemLinkRow(
        title = name,
        supporting = tagSupportText(tagLabels, conditionCount, queuedForRemoval),
        onClick = onOpen,
        trailing = {
            Row {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.NavigateNext,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                IconButton(onClick = onDelete) {
                    Icon(
                        imageVector = Icons.Filled.Delete,
                        contentDescription = stringResource(
                            ArrUiR.string.arr_common_autotag_cd_delete_rule,
                            name,
                        ),
                    )
                }
            }
        },
    )
}

@Composable
private fun tagSupportText(
    tagLabels: List<String>,
    conditionCount: Int,
    queuedForRemoval: Boolean,
): String {
    val conditionText = stringResource(ArrUiR.string.arr_common_autotag_row_support, conditionCount)
    val tags = tagLabels.joinToString(", ")
    val base = if (tags.isEmpty()) conditionText else "$tags · $conditionText"
    return if (queuedForRemoval) {
        stringResource(
                    ArrUiR.string.arr_common_metadata_pair,
                    stringResource(ArrUiR.string.arr_common_system_cd_queued_indicator),
                    base,
                )
    } else {
        base
    }
}

@Composable
internal fun autoTaggingNoticeText(notice: AutoTaggingNotice): String = when (notice) {
    AutoTaggingNotice.SAVED -> stringResource(ArrUiR.string.arr_common_system_notice_saved)
    AutoTaggingNotice.SAVED_QUEUED ->
        stringResource(ArrUiR.string.arr_common_system_notice_saved_queued)

    AutoTaggingNotice.DELETED -> stringResource(ArrUiR.string.arr_common_tags_notice_deleted)
    AutoTaggingNotice.DELETED_QUEUED ->
        stringResource(ArrUiR.string.arr_common_system_notice_saved_queued)
}

@Composable
private fun AutoTaggingConfirmDialog(
    state: SonarrAutoTaggingViewModel.UiState,
    onDeleteCancelled: () -> Unit,
    onDeleteConfirmed: () -> Unit,
) {
    state.confirmDeleteFor?.let { rule ->
        AlertDialog(
            onDismissRequest = onDeleteCancelled,
            title = { Text(stringResource(ArrUiR.string.arr_common_autotag_delete_title)) },
            text = { Text(stringResource(R.string.arr_sonarr_autotag_delete_message)) },
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
