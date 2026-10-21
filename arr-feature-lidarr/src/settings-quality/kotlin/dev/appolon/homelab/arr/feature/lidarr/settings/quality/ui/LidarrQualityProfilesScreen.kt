package dev.appolon.homelab.arr.feature.lidarr.settings.quality.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
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
import dev.appolon.homelab.arr.api.lidarr.model.ProfileFormatItemResource
import dev.appolon.homelab.arr.api.lidarr.model.QualityProfileResource
import dev.appolon.homelab.arr.core.ServerId
import dev.appolon.homelab.arr.feature.lidarr.R
import dev.appolon.homelab.arr.feature.lidarr.settings.quality.LidarrQualityClients
import dev.appolon.homelab.arr.feature.lidarr.settings.quality.vm.LidarrQualityProfileEditorContent
import dev.appolon.homelab.arr.feature.lidarr.settings.quality.vm.LidarrQualityProfileEditorViewModel
import dev.appolon.homelab.arr.feature.lidarr.settings.quality.vm.LidarrQualityProfilesContent
import dev.appolon.homelab.arr.feature.lidarr.settings.quality.vm.LidarrQualityProfilesViewModel
import dev.appolon.homelab.arr.feature.lidarr.settings.quality.vm.ProfileQualityItem
import dev.appolon.homelab.arr.feature.lidarr.settings.quality.vm.QualityProfileDraft
import dev.appolon.homelab.arr.feature.lidarr.settings.quality.vm.QualityProfilesNotice
import dev.appolon.homelab.arr.ui.components.EmptyState
import dev.appolon.homelab.arr.ui.components.LabeledSwitch
import dev.appolon.homelab.arr.ui.lce.ArrLceBox
import dev.appolon.homelab.arr.ui.lce.ArrPullToRefreshBox
import dev.appolon.homelab.arr.ui.lce.LceState
import dev.appolon.homelab.arr.ui.shell.ArrScaffold

/** Destination for the quality profiles list route. */
@Composable
fun LidarrQualityProfilesDestination(
    serverId: ServerId,
    clients: LidarrQualityClients,
    serverName: String? = null,
    onBack: (() -> Unit)? = null,
    onOpenProfile: (Int?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val repository = remember(serverId, clients) { qualityRepository(serverId, clients) }
    val vm: LidarrQualityProfilesViewModel =
        viewModel(key = "lidarr-quality-profiles-" + serverId.value) {
            LidarrQualityProfilesViewModel(repository)
        }
    val state by vm.state.collectAsState()
    val lce by vm.lce.collectAsState()

    LidarrQualityProfilesScreen(
        state = state,
        lce = lce,
        serverName = serverName,
        onBack = onBack,
        onRefresh = vm::refresh,
        onAdd = { onOpenProfile(null) },
        onOpenProfile = onOpenProfile,
        onRequestDelete = vm::requestDelete,
        onDeleteConfirmed = vm::deleteConfirmed,
        onConsumeNotice = vm::consumeNotice,
        onConsumeWriteError = vm::consumeWriteError,
        modifier = modifier,
    )
}

/** Destination for the schema-driven profile builder route. */
@Composable
fun LidarrQualityProfileEditorDestination(
    serverId: ServerId,
    profileId: Int?,
    clients: LidarrQualityClients,
    serverName: String? = null,
    onBack: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val repository = remember(serverId, clients) { qualityRepository(serverId, clients) }
    val vm: LidarrQualityProfileEditorViewModel = viewModel(
        key = "lidarr-quality-profile-editor-" + serverId.value + "-" + (profileId ?: "new"),
    ) {
        LidarrQualityProfileEditorViewModel(profileId, repository)
    }
    val state by vm.state.collectAsState()
    val lce by vm.lce.collectAsState()

    LidarrQualityProfileEditorScreen(
        state = state,
        lce = lce,
        serverName = serverName,
        onBack = onBack,
        onRefresh = vm::refresh,
        onEdit = vm::edit,
        onToggleAllowed = vm::toggleAllowed,
        onSetCutoff = vm::setCutoff,
        onSetFormatScore = vm::setFormatScore,
        onSave = vm::save,
        onConsumeSavedNotice = vm::consumeSavedNotice,
        onConsumeWriteError = vm::consumeWriteError,
        modifier = modifier,
    )
}

/**
 * Quality profiles list. Rows open the builder; the app bar adds a new
 * profile from the server schema. Delete asks for confirmation because it
 * affects every artist using the profile.
 */
@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun LidarrQualityProfilesScreen(
    state: LidarrQualityProfilesViewModel.UiState,
    lce: LceState<LidarrQualityProfilesContent>,
    serverName: String?,
    onBack: (() -> Unit)?,
    onRefresh: () -> Unit,
    onAdd: () -> Unit,
    onOpenProfile: (Int) -> Unit,
    onRequestDelete: (Int?) -> Unit,
    onDeleteConfirmed: () -> Unit,
    onConsumeNotice: () -> Unit,
    onConsumeWriteError: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val content = (lce as? LceState.Content<LidarrQualityProfilesContent>)?.data
    val refreshing = content != null && (lce as LceState.Content<LidarrQualityProfilesContent>).refreshing

    val noticeMessage = when (state.notice) {
        QualityProfilesNotice.DELETED -> stringResource(R.string.arr_lidarr_quality_notice_deleted)
        QualityProfilesNotice.QUEUED ->
            stringResource(R.string.arr_lidarr_quality_notice_saved_queued)
        else -> null
    }
    QualityNoticeHost(
        noticeMessage = noticeMessage,
        writeError = state.writeError,
        snackbarHostState = snackbarHostState,
        onConsumeNotice = onConsumeNotice,
        onConsumeWriteError = onConsumeWriteError,
    )

    ArrScaffold(
        title = stringResource(R.string.arr_lidarr_quality_profiles_title),
        subtitle = serverName,
        onBack = onBack,
        actions = {
            IconButton(onClick = onAdd) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = stringResource(R.string.arr_lidarr_quality_cd_add_profile),
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
                if (content != null && content.profiles.isEmpty()) {
                    EmptyState(
                        title = stringResource(R.string.arr_lidarr_quality_profiles_empty_title),
                        message = stringResource(R.string.arr_lidarr_quality_profiles_empty_message),
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    ArrLceBox(state = lce, onRetry = onRefresh, modifier = Modifier.fillMaxSize()) { data ->
                        LazyColumn(
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                                horizontal = 16.dp,
                                vertical = 8.dp,
                            ),
                            modifier = Modifier.widthIn(max = SectionMaxWidth + 32.dp),
                        ) {
                            if (data.pendingActions > 0) {
                                item { QualityPendingSyncBanner(data.pendingActions) }
                            }
                            items(data.profiles, key = { it.id }) { profile ->
                                ProfileRow(
                                    profile = profile,
                                    onOpen = { onOpenProfile(profile.id) },
                                    onDelete = { onRequestDelete(profile.id) },
                                )
                            }
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
            title = { Text(stringResource(R.string.arr_lidarr_quality_delete_profile_title)) },
            text = { Text(stringResource(R.string.arr_lidarr_quality_delete_profile_message)) },
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
private fun ProfileRow(
    profile: QualityProfileResource,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(onClickLabel = profile.name.orEmpty(), onClick = onOpen)
            .padding(vertical = 12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(profile.name.orEmpty(), style = MaterialTheme.typography.bodyLarge)
            Text(
                text = stringResource(
                    R.string.arr_lidarr_quality_profiles_row_support,
                    profile.formatItems.orEmpty().size,
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onDelete) {
            Icon(
                imageVector = Icons.Filled.Delete,
                contentDescription = stringResource(
                    R.string.arr_lidarr_quality_cd_delete_profile,
                    profile.name.orEmpty(),
                ),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Schema-driven profile builder: quality items with allowed toggles, the
 * upgrade-until picker, custom format score rows, and the minimum score
 * thresholds. Album type selection lives in the metadata profile builder,
 * which is the Lidarr-specific table Sonarr does not have.
 */
@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun LidarrQualityProfileEditorScreen(
    state: LidarrQualityProfileEditorViewModel.UiState,
    lce: LceState<LidarrQualityProfileEditorContent>,
    serverName: String?,
    onBack: (() -> Unit)?,
    onRefresh: () -> Unit,
    onEdit: (transform: (QualityProfileDraft) -> QualityProfileDraft) -> Unit,
    onToggleAllowed: (ProfileQualityItem) -> Unit,
    onSetCutoff: (Int) -> Unit,
    onSetFormatScore: (Int, String) -> Unit,
    onSave: () -> Unit,
    onConsumeSavedNotice: () -> Unit,
    onConsumeWriteError: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val content = (lce as? LceState.Content<LidarrQualityProfileEditorContent>)?.data

    val savedMessage = when {
        state.savedNotice -> stringResource(R.string.arr_lidarr_quality_notice_saved)
        state.savedQueued -> stringResource(R.string.arr_lidarr_quality_notice_saved_queued)
        else -> null
    }
    QualityNoticeHost(
        noticeMessage = savedMessage,
        writeError = state.writeError,
        snackbarHostState = snackbarHostState,
        onConsumeNotice = onConsumeSavedNotice,
        onConsumeWriteError = onConsumeWriteError,
    )

    ArrScaffold(
        title = stringResource(
            if (state.draft.isExisting) {
                R.string.arr_lidarr_quality_profile_editor_title_edit
            } else {
                R.string.arr_lidarr_quality_profile_editor_title_new
            },
        ),
        subtitle = serverName,
        onBack = onBack,
        actions = {
            if (content?.dirty == true) {
                IconButton(onClick = onSave, enabled = !state.saving) {
                    Icon(
                        imageVector = Icons.Filled.Save,
                        contentDescription = stringResource(R.string.arr_lidarr_quality_cd_save),
                    )
                }
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
            isRefreshing = false,
            onRefresh = onRefresh,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            Box(Modifier.fillMaxSize()) {
                ArrLceBox(state = lce, onRetry = onRefresh, modifier = Modifier.fillMaxSize()) { data ->
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .fillMaxSize()
                            .widthIn(max = SectionMaxWidth)
                            .verticalScroll(rememberScrollState())
                            .padding(16.dp),
                    ) {
                        if (data.pendingActions > 0) QualityPendingSyncBanner(data.pendingActions)
                        QualityTextField(
                            value = data.draft.name,
                            label = stringResource(R.string.arr_lidarr_quality_profile_name),
                            onValue = { text -> onEdit { it.copy(name = text) } },
                        )
                        LabeledSwitch(
                            label = stringResource(R.string.arr_lidarr_quality_profile_upgrade_allowed),
                            supportingText = stringResource(
                                R.string.arr_lidarr_quality_profile_upgrade_allowed_support,
                            ),
                            checked = data.draft.upgradeAllowed,
                            onCheckedChange = { value -> onEdit { it.copy(upgradeAllowed = value) } },
                        )
                        QualitySectionHeader(stringResource(R.string.arr_lidarr_quality_profile_section_qualities))
                        data.draft.items.forEach { item ->
                            QualityItemRow(item, data.draft.cutoff, 0, onToggleAllowed, onSetCutoff)
                        }
                        QualitySectionHeader(stringResource(R.string.arr_lidarr_quality_profile_section_upgrade))
                        val allowedLeaves = data.draft.allowedLeaves()
                        QualityDropdown(
                            label = stringResource(R.string.arr_lidarr_quality_profile_upgrade_until),
                            selected = allowedLeaves.firstOrNull { it.qualityId == data.draft.cutoff }
                                ?: allowedLeaves.firstOrNull()
                                ?: ProfileQualityItem(0, "", 0, false),
                            entries = allowedLeaves.ifEmpty {
                                listOf(ProfileQualityItem(0, "", 0, false))
                            },
                            labelOf = { it.name },
                            onSelect = { choice -> choice.qualityId?.let(onSetCutoff) },
                        )
                        QualitySectionHeader(stringResource(R.string.arr_lidarr_quality_profile_section_scores))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Column(Modifier.weight(1f)) {
                                QualityTextField(
                                    value = data.draft.minFormatScore,
                                    label = stringResource(R.string.arr_lidarr_quality_profile_min_score),
                                    onValue = { text ->
                                        onEdit { it.copy(minFormatScore = text) }
                                    },
                                )
                            }
                            Column(Modifier.weight(1f)) {
                                QualityTextField(
                                    value = data.draft.cutoffFormatScore,
                                    label = stringResource(R.string.arr_lidarr_quality_profile_cutoff_score),
                                    onValue = { text ->
                                        onEdit { it.copy(cutoffFormatScore = text) }
                                    },
                                )
                            }
                        }
                        if (data.draft.formatItems.isNotEmpty()) {
                            QualitySectionHeader(
                                stringResource(R.string.arr_lidarr_quality_profile_section_format_scores),
                            )
                            data.draft.formatItems.forEach { item ->
                                FormatScoreField(item, onSetFormatScore)
                            }
                        }
                        Spacer(Modifier.heightIn(min = 24.dp))
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
private fun QualityItemRow(
    item: ProfileQualityItem,
    cutoff: Int,
    indent: Int,
    onToggleAllowed: (ProfileQualityItem) -> Unit,
    onSetCutoff: (Int) -> Unit,
) {
    Column(Modifier.padding(start = (indent * 12).dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(item.name, style = MaterialTheme.typography.bodyLarge)
            }
            Checkbox(
                checked = item.allowed,
                onCheckedChange = { onToggleAllowed(item) },
                modifier = Modifier.heightIn(min = 48.dp),
            )
        }
        item.children.forEach { child ->
            QualityItemRow(child, cutoff, indent + 1, onToggleAllowed, onSetCutoff)
        }
        // Leaf rows double as the cutoff picker through a radio row; the
        // group's own header row only toggles all children.
        if (item.isLeaf && item.allowed) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .clickable(
                        onClickLabel = stringResource(
                            R.string.arr_lidarr_quality_profile_cd_upgrade_until,
                            item.name,
                        ),
                    ) { item.qualityId?.let(onSetCutoff) }
                    .padding(horizontal = 4.dp),
            ) {
                RadioButton(
                    selected = item.qualityId == cutoff,
                    onClick = { item.qualityId?.let(onSetCutoff) },
                    modifier = Modifier.heightIn(min = 48.dp),
                )
                Text(
                    text = stringResource(R.string.arr_lidarr_quality_profile_upgrade_until_row),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun FormatScoreField(
    item: ProfileFormatItemResource,
    onSetFormatScore: (Int, String) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = item.name.orEmpty(),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(16.dp))
        QualityTextField(
            value = item.score.toString(),
            label = stringResource(R.string.arr_lidarr_quality_profile_score_label),
            onValue = { text -> onSetFormatScore(item.format, text) },
            modifier = Modifier.width(120.dp),
        )
    }
}
