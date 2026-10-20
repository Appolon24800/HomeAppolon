package dev.appolon.homelab.arr.feature.lidarr.settings.connect.ui

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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.appolon.homelab.arr.api.lidarr.model.NotificationResource
import dev.appolon.homelab.arr.api.lidarr.model.TagResource
import dev.appolon.homelab.arr.core.ServerId
import dev.appolon.homelab.arr.feature.lidarr.R
import dev.appolon.homelab.arr.feature.lidarr.settings.connect.LidarrConnectClients
import dev.appolon.homelab.arr.feature.lidarr.settings.connect.vm.LidarrNotificationsContent
import dev.appolon.homelab.arr.feature.lidarr.settings.connect.vm.LidarrNotificationsViewModel
import dev.appolon.homelab.arr.feature.lidarr.settings.connect.vm.NotificationsNotice
import dev.appolon.homelab.arr.ui.components.SectionHeader
import dev.appolon.homelab.arr.ui.lce.ArrLceBox
import dev.appolon.homelab.arr.ui.lce.ArrPullToRefreshBox
import dev.appolon.homelab.arr.ui.lce.LceState
import dev.appolon.homelab.arr.ui.shell.ArrScaffold
import dev.appolon.homelab.arr.ui.R as ArrUiR

/** Destination for the connect notifications route. */
@Composable
fun LidarrNotificationsDestination(
    serverId: ServerId,
    clients: LidarrConnectClients,
    serverName: String? = null,
    onBack: (() -> Unit)? = null,
    onOpenNotification: (Int?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val repository = remember(serverId, clients) { connectRepository(serverId, clients) }
    val vm: LidarrNotificationsViewModel =
        viewModel(key = "lidarr-notifications-" + serverId.value) {
            LidarrNotificationsViewModel(serverId, repository)
        }
    val state by vm.state.collectAsState()
    val lce by vm.lce.collectAsState()

    LidarrNotificationsScreen(
        state = state,
        lce = lce,
        serverName = serverName,
        onBack = onBack,
        onRefresh = vm::refresh,
        onOpen = onOpenNotification,
        onAdd = { onOpenNotification(null) },
        onTestAll = vm::testAll,
        onDeleteRequest = vm::requestDelete,
        onDeleteCancelled = vm::deleteCancelled,
        onDeleteConfirmed = vm::deleteConfirmed,
        onConsumeNotice = vm::consumeNotice,
        onConsumeWriteError = vm::consumeWriteError,
        modifier = modifier,
    )
}

/**
 * Connect notifications list. Rows summarize the Lidarr events each
 * connection listens on so the matrix is visible without opening the
 * editor.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LidarrNotificationsScreen(
    state: LidarrNotificationsViewModel.UiState,
    lce: LceState<LidarrNotificationsContent>,
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
    val content = (lce as? LceState.Content<LidarrNotificationsContent>)?.data
    val refreshing = content != null && (lce as LceState.Content<LidarrNotificationsContent>).refreshing

    ConnectNoticeHost(
        noticeMessage = when (state.notice) {
            NotificationsNotice.DELETED ->
                stringResource(ArrUiR.string.arr_common_connect_notice_deleted)

            NotificationsNotice.DELETED_QUEUED ->
                stringResource(ArrUiR.string.arr_common_backup_notice_deleted_queued)

            NotificationsNotice.TEST_ALL_DONE ->
                stringResource(ArrUiR.string.arr_common_connect_notice_test_all_done)

            null -> null
        },
        writeError = state.writeError,
        snackbarHostState = snackbarHostState,
        onConsumeNotice = onConsumeNotice,
        onConsumeWriteError = onConsumeWriteError,
    )

    ArrScaffold(
        title = stringResource(ArrUiR.string.arr_common_notifications_title),
        subtitle = serverName,
        onBack = onBack,
        actions = {
            IconButton(onClick = onTestAll, enabled = !state.testingAll && content != null) {
                Icon(
                    imageVector = Icons.Filled.PlayArrow,
                    contentDescription = stringResource(ArrUiR.string.arr_common_notifications_cd_test_all),
                )
            }
            IconButton(onClick = onRefresh) {
                Icon(
                    imageVector = Icons.Filled.Refresh,
                    contentDescription = stringResource(ArrUiR.string.arr_common_notifications_cd_refresh),
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
                    emptyTitle = stringResource(ArrUiR.string.arr_common_notifications_empty_title),
                    emptyMessage = stringResource(ArrUiR.string.arr_common_notifications_empty_message),
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
                            SectionHeader(
                                title = stringResource(ArrUiR.string.arr_common_notifications_section_connections),
                            )
                        }
                        items(
                            data.notifications,
                            key = { it.id },
                            contentType = { "notification" },
                        ) { notification ->
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
            title = { Text(stringResource(ArrUiR.string.arr_common_delete)) },
            text = {
                Text(
                    stringResource(
                        ArrUiR.string.arr_common_connect_delete_message,
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
    tags: List<TagResource>,
    queued: Boolean,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
) {
    val openCd = stringResource(
        ArrUiR.string.arr_common_notifications_cd_open,
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
                Text(
                    text = enabledEventLabels(notification).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (queued) ConnectQueuedBadge()
                    ConnectTagChips(notification.tags.orEmpty().toList(), tags)
                }
            }
            val deleteCd = stringResource(
                ArrUiR.string.arr_common_notifications_cd_delete,
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

/** Short labels for the row summary; full sentences live in the editor. */
@Composable
private fun enabledEventLabels(notification: NotificationResource): List<String> =
    listOfNotNull(
        "onGrab".takeIf { notification.onGrab }?.let {
            stringResource(ArrUiR.string.arr_common_notification_event_grab)
        },
        "onReleaseImport".takeIf { notification.onReleaseImport }?.let {
            stringResource(R.string.arr_lidarr_notification_event_release_import)
        },
        "onUpgrade".takeIf { notification.onUpgrade }?.let {
            stringResource(ArrUiR.string.arr_common_notification_event_upgrade)
        },
        "onRename".takeIf { notification.onRename }?.let {
            stringResource(ArrUiR.string.arr_common_notification_event_rename)
        },
        "onArtistAdd".takeIf { notification.onArtistAdd }?.let {
            stringResource(R.string.arr_lidarr_notification_event_artist_added)
        },
        "onArtistDelete".takeIf { notification.onArtistDelete }?.let {
            stringResource(R.string.arr_lidarr_notification_event_artist_delete)
        },
        "onAlbumDelete".takeIf { notification.onAlbumDelete }?.let {
            stringResource(R.string.arr_lidarr_notification_event_album_delete)
        },
        "onHealthIssue".takeIf { notification.onHealthIssue }?.let {
            stringResource(ArrUiR.string.arr_common_notification_event_health)
        },
        "onHealthRestored".takeIf { notification.onHealthRestored }?.let {
            stringResource(ArrUiR.string.arr_common_notification_event_health_restored)
        },
        "onDownloadFailure".takeIf { notification.onDownloadFailure }?.let {
            stringResource(R.string.arr_lidarr_notification_event_download_failure)
        },
        "onImportFailure".takeIf { notification.onImportFailure }?.let {
            stringResource(R.string.arr_lidarr_notification_event_import_failure)
        },
        "onTrackRetag".takeIf { notification.onTrackRetag }?.let {
            stringResource(R.string.arr_lidarr_notification_event_track_retag)
        },
        "onApplicationUpdate".takeIf { notification.onApplicationUpdate }?.let {
            stringResource(ArrUiR.string.arr_common_notification_event_application_update)
        },
    )
