package dev.appolon.homelab.arr.feature.prowlarr.settings.ui

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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.appolon.homelab.arr.api.prowlarr.model.NotificationResource
import dev.appolon.homelab.arr.feature.prowlarr.R
import dev.appolon.homelab.arr.feature.prowlarr.indexers.ui.TagChips
import dev.appolon.homelab.arr.feature.prowlarr.settings.data.NotificationEvents
import dev.appolon.homelab.arr.feature.prowlarr.settings.vm.NotificationsNotice
import dev.appolon.homelab.arr.feature.prowlarr.settings.vm.ProwlarrNotificationsContent
import dev.appolon.homelab.arr.feature.prowlarr.settings.vm.ProwlarrNotificationsViewModel
import dev.appolon.homelab.arr.ui.components.SectionHeader
import dev.appolon.homelab.arr.ui.lce.ArrLceBox
import dev.appolon.homelab.arr.ui.lce.ArrPullToRefreshBox
import dev.appolon.homelab.arr.ui.lce.LceState
import dev.appolon.homelab.arr.ui.shell.ArrScaffold
import dev.appolon.homelab.arr.ui.R as ArrUiR

/**
 * Connect notifications list. Each row summarizes the implementation and
 * the events it fires; test-all probes every connection and reports one
 * verdict, because Prowlarr answers acceptance only.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProwlarrNotificationsScreen(
    state: ProwlarrNotificationsViewModel.UiState,
    lce: LceState<ProwlarrNotificationsContent>,
    serverName: String?,
    onBack: (() -> Unit)?,
    onRefresh: () -> Unit,
    onOpen: (Int) -> Unit,
    onAdd: () -> Unit,
    onTestAll: () -> Unit,
    onDeleteRequest: (NotificationResource) -> Unit,
    onDeleteCancelled: () -> Unit,
    onDeleteConfirmed: () -> Unit,
    onConsumeNotice: () -> Unit,
    onConsumeWriteError: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val content = (lce as? LceState.Content<ProwlarrNotificationsContent>)?.data
    val refreshing = content != null &&
        (lce as LceState.Content<ProwlarrNotificationsContent>).refreshing

    NoticeHost(
        noticeMessage = when (state.notice) {
            NotificationsNotice.DELETED ->
                stringResource(ArrUiR.string.arr_common_connect_notice_deleted)

            NotificationsNotice.DELETED_QUEUED ->
                stringResource(R.string.arr_prowlarr_settings_notice_deleted_queued)

            NotificationsNotice.TOGGLE_QUEUED ->
                stringResource(R.string.arr_prowlarr_settings_notice_saved_queued)

            NotificationsNotice.TEST_ALL_PASSED ->
                stringResource(R.string.arr_prowlarr_settings_notice_test_all_passed)

            NotificationsNotice.TEST_ALL_FAILED ->
                stringResource(R.string.arr_prowlarr_settings_notice_test_all_failed)

            null -> null
        },
        writeError = state.writeError,
        snackbarHostState = snackbarHostState,
        onConsumeNotice = onConsumeNotice,
        onConsumeWriteError = onConsumeWriteError,
    )

    ArrScaffold(
        title = stringResource(R.string.arr_prowlarr_settings_notifications_title),
        subtitle = serverName,
        onBack = onBack,
        actions = {
            IconButton(onClick = onTestAll, enabled = !state.testingAll && content != null) {
                Icon(
                    imageVector = Icons.Filled.PlayArrow,
                    contentDescription = stringResource(R.string.arr_prowlarr_settings_cd_test_all),
                )
            }
            IconButton(onClick = onRefresh) {
                Icon(
                    imageVector = Icons.Filled.Refresh,
                    contentDescription = stringResource(ArrUiR.string.arr_common_system_cd_refresh),
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
                    emptyTitle = stringResource(
                        ArrUiR.string.arr_common_notifications_empty_title,
                    ),
                    emptyMessage = stringResource(
                        R.string.arr_prowlarr_settings_notifications_empty_message,
                    ),
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
                                title = stringResource(
                                    R.string.arr_prowlarr_settings_notifications_section,
                                ),
                                modifier = Modifier.semantics { heading() },
                            )
                        }
                        items(data.notifications, key = { it.id }) { notification ->
                            NotificationRow(
                                notification = notification,
                                tags = data.tags,
                                queued = notification.id in data.queuedRemovedIds,
                                onOpen = { onOpen(notification.id) },
                                onDelete = { onDeleteRequest(notification) },
                            )
                        }
                    }
                }
            }
            ExtendedFloatingActionButton(
                onClick = onAdd,
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text(stringResource(ArrUiR.string.arr_common_notifications_add)) },
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
            title = { Text(stringResource(R.string.arr_prowlarr_settings_delete_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.arr_prowlarr_settings_delete_message,
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
}

@Composable
private fun NotificationRow(
    notification: NotificationResource,
    tags: List<dev.appolon.homelab.arr.api.prowlarr.model.TagResource>,
    queued: Boolean,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
) {
    val openCd = stringResource(
        ArrUiR.string.arr_common_mm_browser_cd_open,
        notification.name.orEmpty(),
    )
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
                Text(notification.name.orEmpty(), style = MaterialTheme.typography.bodyLarge)
                Text(
                    text = notification.implementationName.orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (queued) QueuedChip()
                    NotificationEvents.summary(notification).forEach { key ->
                        AssistChip(
                            onClick = {},
                            enabled = false,
                            label = { Text(eventSummaryLabel(key)) },
                        )
                    }
                }
                TagChips(notification.tags.orEmpty().toList(), tags)
            }
            val deleteCd = stringResource(
                R.string.arr_prowlarr_settings_clients_cd_delete,
                notification.name.orEmpty(),
            )
            IconButton(
                onClick = onDelete,
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
}

/** Short chips on the list rows reuse the editor's event labels. */
@Composable
private fun eventSummaryLabel(key: String): String = when (key) {
    "onGrab" -> stringResource(ArrUiR.string.arr_common_notification_event_grab)
    "onHealthIssue" -> stringResource(ArrUiR.string.arr_common_notification_event_health)
    "onHealthRestored" -> stringResource(ArrUiR.string.arr_common_notification_event_health_restored)
    "onApplicationUpdate" ->
        stringResource(ArrUiR.string.arr_common_notification_event_application_update)

    else -> key
}
