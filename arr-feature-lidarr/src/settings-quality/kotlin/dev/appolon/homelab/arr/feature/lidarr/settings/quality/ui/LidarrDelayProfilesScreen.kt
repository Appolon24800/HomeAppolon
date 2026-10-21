package dev.appolon.homelab.arr.feature.lidarr.settings.quality.ui

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
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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
import dev.appolon.homelab.arr.api.lidarr.model.DelayProfileResource
import dev.appolon.homelab.arr.api.lidarr.model.DownloadProtocol
import dev.appolon.homelab.arr.core.ServerId
import dev.appolon.homelab.arr.feature.lidarr.R
import dev.appolon.homelab.arr.feature.lidarr.settings.quality.LidarrQualityClients
import dev.appolon.homelab.arr.feature.lidarr.settings.quality.vm.DelayProfilesNotice
import dev.appolon.homelab.arr.feature.lidarr.settings.quality.vm.LidarrDelayProfilesContent
import dev.appolon.homelab.arr.feature.lidarr.settings.quality.vm.LidarrDelayProfilesViewModel
import dev.appolon.homelab.arr.ui.components.LabeledSwitch
import dev.appolon.homelab.arr.ui.lce.ArrLceBox
import dev.appolon.homelab.arr.ui.lce.ArrPullToRefreshBox
import dev.appolon.homelab.arr.ui.lce.LceState
import dev.appolon.homelab.arr.ui.shell.ArrScaffold

/** Destination for the delay profiles route. */
@Composable
fun LidarrDelayProfilesDestination(
    serverId: ServerId,
    clients: LidarrQualityClients,
    serverName: String? = null,
    onBack: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val repository = remember(serverId, clients) { qualityRepository(serverId, clients) }
    val vm: LidarrDelayProfilesViewModel =
        viewModel(key = "lidarr-delay-profiles-" + serverId.value) {
            LidarrDelayProfilesViewModel(repository)
        }
    val state by vm.state.collectAsState()
    val lce by vm.lce.collectAsState()

    LidarrDelayProfilesScreen(
        state = state,
        lce = lce,
        serverName = serverName,
        onBack = onBack,
        onRefresh = vm::refresh,
        onAdd = vm::addProfile,
        onSave = vm::save,
        onEditDraft = vm::editDraft,
        onDiscardDraft = vm::discardDraft,
        onMoveUp = vm::moveUp,
        onMoveDown = vm::moveDown,
        onRequestDelete = vm::requestDelete,
        onDeleteConfirmed = vm::deleteConfirmed,
        isDefaultProfile = vm::isDefaultProfile,
        onConsumeNotice = vm::consumeNotice,
        onConsumeWriteError = vm::consumeWriteError,
        modifier = modifier,
    )
}

/**
 * Delay profiles screen: ordered cards with protocol-specific delays in
 * minutes, the preferred protocol, and the two bypass rules. The first
 * matching profile in list order wins, which is why move up / move down
 * exists; the untagged global fallback cannot be deleted.
 */
@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun LidarrDelayProfilesScreen(
    state: LidarrDelayProfilesViewModel.UiState,
    lce: LceState<LidarrDelayProfilesContent>,
    serverName: String?,
    onBack: (() -> Unit)?,
    onRefresh: () -> Unit,
    onAdd: () -> Unit,
    onSave: (DelayProfileResource) -> Unit,
    onEditDraft: (DelayProfileResource, transform: (DelayProfileResource) -> DelayProfileResource) -> Unit,
    onDiscardDraft: (DelayProfileResource) -> Unit,
    onMoveUp: (DelayProfileResource) -> Unit,
    onMoveDown: (DelayProfileResource) -> Unit,
    onRequestDelete: (Int?) -> Unit,
    onDeleteConfirmed: () -> Unit,
    isDefaultProfile: (DelayProfileResource) -> Boolean,
    onConsumeNotice: () -> Unit,
    onConsumeWriteError: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val content = (lce as? LceState.Content<LidarrDelayProfilesContent>)?.data
    val refreshing = content != null && (lce as LceState.Content<LidarrDelayProfilesContent>).refreshing

    val noticeMessage = when (state.notice) {
        DelayProfilesNotice.CREATED -> stringResource(R.string.arr_lidarr_quality_notice_created)
        DelayProfilesNotice.UPDATED -> stringResource(R.string.arr_lidarr_quality_notice_saved)
        DelayProfilesNotice.DELETED -> stringResource(R.string.arr_lidarr_quality_notice_deleted)
        DelayProfilesNotice.MOVED -> stringResource(R.string.arr_lidarr_quality_notice_moved)
        DelayProfilesNotice.QUEUED ->
            stringResource(R.string.arr_lidarr_quality_notice_saved_queued)
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
        title = stringResource(R.string.arr_lidarr_quality_delay_title),
        subtitle = serverName,
        onBack = onBack,
        actions = {
            IconButton(onClick = onAdd) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = stringResource(R.string.arr_lidarr_quality_cd_add_delay),
                )
            }
            IconButton(onClick = onRefresh) {
                Icon(
                    imageVector = Icons.Filled.Refresh,
                    contentDescription = stringResource(R.string.arr_lidarr_quality_cd_refresh),
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
                    emptyTitle = stringResource(R.string.arr_lidarr_quality_delay_empty_title),
                    emptyMessage = stringResource(R.string.arr_lidarr_quality_delay_empty_message),
                    modifier = Modifier.fillMaxSize(),
                ) { data ->
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                        modifier = Modifier.widthIn(max = SectionMaxWidth + 32.dp),
                    ) {
                        if (data.pendingActions > 0) {
                            item { QualityPendingSyncBanner(data.pendingActions) }
                        }
                        itemsIndexed(
                            data.profiles,
                            key = { index, profile ->
                                if (profile.id > 0) profile.id else -1000 - index
                            },
                        ) { index, profile ->
                            DelayProfileCard(
                                profile = profile,
                                isFirst = index == 0,
                                isLast = index == data.profiles.size - 1,
                                isBusy = state.busyId == profile.id,
                                isDirty = profile.id <= 0 || profile.id in data.dirtyIds,
                                showDelete = profile.id <= 0 || !isDefaultProfile(profile),
                                onSave = onSave,
                                onEditDraft = onEditDraft,
                                onDiscardDraft = onDiscardDraft,
                                onMoveUp = onMoveUp,
                                onMoveDown = onMoveDown,
                                onRequestDelete = onRequestDelete,
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

    if (state.confirmDeleteId != null) {
        AlertDialog(
            onDismissRequest = { onRequestDelete(null) },
            title = { Text(stringResource(R.string.arr_lidarr_quality_delete_delay_title)) },
            text = { Text(stringResource(R.string.arr_lidarr_quality_delete_delay_message)) },
            confirmButton = {
                TextButton(onClick = onDeleteConfirmed) {
                    Text(stringResource(R.string.arr_lidarr_quality_action_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { onRequestDelete(null) }) {
                    Text(stringResource(R.string.arr_lidarr_quality_action_cancel))
                }
            },
        )
    }
}

@Composable
private fun DelayProfileCard(
    profile: DelayProfileResource,
    isFirst: Boolean,
    isLast: Boolean,
    isBusy: Boolean,
    isDirty: Boolean,
    showDelete: Boolean,
    onSave: (DelayProfileResource) -> Unit,
    onEditDraft: (DelayProfileResource, transform: (DelayProfileResource) -> DelayProfileResource) -> Unit,
    onDiscardDraft: (DelayProfileResource) -> Unit,
    onMoveUp: (DelayProfileResource) -> Unit,
    onMoveDown: (DelayProfileResource) -> Unit,
    onRequestDelete: (Int?) -> Unit,
) {
    QualityCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.arr_lidarr_quality_delay_profile_title, profile.order + 1),
                    style = MaterialTheme.typography.titleSmall,
                )
            }
            IconButton(onClick = { onMoveUp(profile) }, enabled = !isFirst && !isBusy) {
                Icon(
                    imageVector = Icons.Filled.ArrowUpward,
                    contentDescription = stringResource(
                        R.string.arr_lidarr_quality_cd_delay_move_up,
                        profile.order + 1,
                    ),
                )
            }
            IconButton(onClick = { onMoveDown(profile) }, enabled = !isLast && !isBusy) {
                Icon(
                    imageVector = Icons.Filled.ArrowDownward,
                    contentDescription = stringResource(
                        R.string.arr_lidarr_quality_cd_delay_move_down,
                        profile.order + 1,
                    ),
                )
            }
            if (showDelete) {
                if (profile.id > 0) {
                    IconButton(
                        onClick = { onRequestDelete(profile.id) },
                        enabled = !isBusy,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Delete,
                            contentDescription = stringResource(
                                R.string.arr_lidarr_quality_cd_delete_delay,
                                profile.order + 1,
                            ),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    // Unsaved drafts only need a local discard, no dialog.
                    IconButton(onClick = { onDiscardDraft(profile) }) {
                        Icon(
                            imageVector = Icons.Filled.Delete,
                            contentDescription = stringResource(
                                R.string.arr_lidarr_quality_cd_discard_draft,
                            ),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
        LabeledSwitch(
            label = stringResource(R.string.arr_lidarr_quality_delay_usenet),
            checked = profile.enableUsenet,
            onCheckedChange = { value -> onEditDraft(profile) { it.copy(enableUsenet = value) } },
        )
        LabeledSwitch(
            label = stringResource(R.string.arr_lidarr_quality_delay_torrent),
            checked = profile.enableTorrent,
            onCheckedChange = { value -> onEditDraft(profile) { it.copy(enableTorrent = value) } },
        )
        if (profile.enableUsenet) {
            QualityTextField(
                value = profile.usenetDelay.toString(),
                label = stringResource(R.string.arr_lidarr_quality_delay_usenet_minutes),
                onValue = { text ->
                    onEditDraft(profile) { it.copy(usenetDelay = text.toIntOrNull() ?: it.usenetDelay) }
                },
            )
        }
        if (profile.enableTorrent) {
            QualityTextField(
                value = profile.torrentDelay.toString(),
                label = stringResource(R.string.arr_lidarr_quality_delay_torrent_minutes),
                onValue = { text ->
                    onEditDraft(profile) { it.copy(torrentDelay = text.toIntOrNull() ?: it.torrentDelay) }
                },
            )
        }
        if (profile.enableUsenet && profile.enableTorrent) {
            // Lidarr's wire carries a nullable protocol; unknown drafts on
            // old rows default to usenet the way the web UI does.
            val selected = when (profile.preferredProtocol) {
                DownloadProtocol.TORRENT -> DownloadProtocol.TORRENT
                else -> DownloadProtocol.USENET
            }
            QualityDropdown(
                label = stringResource(R.string.arr_lidarr_quality_delay_preferred_protocol),
                selected = selected,
                entries = listOf(DownloadProtocol.USENET, DownloadProtocol.TORRENT),
                labelOf = { it.toString() },
                onSelect = { choice ->
                    onEditDraft(profile) { it.copy(preferredProtocol = choice) }
                },
            )
        }
        LabeledSwitch(
            label = stringResource(R.string.arr_lidarr_quality_delay_bypass_highest),
            supportingText = stringResource(R.string.arr_lidarr_quality_delay_bypass_highest_support),
            checked = profile.bypassIfHighestQuality,
            onCheckedChange = { value ->
                onEditDraft(profile) { it.copy(bypassIfHighestQuality = value) }
            },
        )
        LabeledSwitch(
            label = stringResource(R.string.arr_lidarr_quality_delay_bypass_score),
            checked = profile.bypassIfAboveCustomFormatScore,
            onCheckedChange = { value ->
                onEditDraft(profile) { it.copy(bypassIfAboveCustomFormatScore = value) }
            },
        )
        if (profile.bypassIfAboveCustomFormatScore) {
            QualityTextField(
                value = profile.minimumCustomFormatScore.toString(),
                label = stringResource(R.string.arr_lidarr_quality_delay_minimum_score),
                onValue = { text ->
                    onEditDraft(profile) {
                        it.copy(minimumCustomFormatScore = text.toIntOrNull() ?: it.minimumCustomFormatScore)
                    }
                },
            )
        }
        Button(
            onClick = { onSave(profile) },
            enabled = !isBusy && isDirty,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp),
        ) {
            Icon(imageVector = Icons.Filled.Save, contentDescription = null)
            Text(stringResource(R.string.arr_lidarr_quality_action_save_row))
        }
    }
}
