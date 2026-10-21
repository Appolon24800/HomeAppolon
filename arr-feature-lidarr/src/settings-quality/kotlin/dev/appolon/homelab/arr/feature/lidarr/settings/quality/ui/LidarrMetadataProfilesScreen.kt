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
import dev.appolon.homelab.arr.api.lidarr.model.MetadataProfileResource
import dev.appolon.homelab.arr.api.lidarr.model.ProfilePrimaryAlbumTypeItemResource
import dev.appolon.homelab.arr.api.lidarr.model.ProfileReleaseStatusItemResource
import dev.appolon.homelab.arr.api.lidarr.model.ProfileSecondaryAlbumTypeItemResource
import dev.appolon.homelab.arr.core.ServerId
import dev.appolon.homelab.arr.feature.lidarr.R
import dev.appolon.homelab.arr.feature.lidarr.settings.quality.LidarrQualityClients
import dev.appolon.homelab.arr.feature.lidarr.settings.quality.vm.LidarrMetadataProfileEditorContent
import dev.appolon.homelab.arr.feature.lidarr.settings.quality.vm.LidarrMetadataProfileEditorViewModel
import dev.appolon.homelab.arr.feature.lidarr.settings.quality.vm.LidarrMetadataProfilesContent
import dev.appolon.homelab.arr.feature.lidarr.settings.quality.vm.LidarrMetadataProfilesViewModel
import dev.appolon.homelab.arr.feature.lidarr.settings.quality.vm.MetadataProfileDraft
import dev.appolon.homelab.arr.feature.lidarr.settings.quality.vm.MetadataProfilesNotice
import dev.appolon.homelab.arr.ui.components.EmptyState
import dev.appolon.homelab.arr.ui.lce.ArrLceBox
import dev.appolon.homelab.arr.ui.lce.ArrPullToRefreshBox
import dev.appolon.homelab.arr.ui.lce.LceState
import dev.appolon.homelab.arr.ui.shell.ArrScaffold

/** Destination for the metadata profiles list route. */
@Composable
fun LidarrMetadataProfilesDestination(
    serverId: ServerId,
    clients: LidarrQualityClients,
    serverName: String? = null,
    onBack: (() -> Unit)? = null,
    onOpenProfile: (Int?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val repository = remember(serverId, clients) { qualityRepository(serverId, clients) }
    val vm: LidarrMetadataProfilesViewModel =
        viewModel(key = "lidarr-metadata-profiles-" + serverId.value) {
            LidarrMetadataProfilesViewModel(repository)
        }
    val state by vm.state.collectAsState()
    val lce by vm.lce.collectAsState()

    LidarrMetadataProfilesScreen(
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

/** Destination for the schema-driven metadata profile builder route. */
@Composable
fun LidarrMetadataProfileEditorDestination(
    serverId: ServerId,
    profileId: Int?,
    clients: LidarrQualityClients,
    serverName: String? = null,
    onBack: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val repository = remember(serverId, clients) { qualityRepository(serverId, clients) }
    val vm: LidarrMetadataProfileEditorViewModel = viewModel(
        key = "lidarr-metadata-profile-editor-" + serverId.value + "-" + (profileId ?: "new"),
    ) {
        LidarrMetadataProfileEditorViewModel(profileId, repository)
    }
    val state by vm.state.collectAsState()
    val lce by vm.lce.collectAsState()

    LidarrMetadataProfileEditorScreen(
        state = state,
        lce = lce,
        serverName = serverName,
        onBack = onBack,
        onRefresh = vm::refresh,
        onSetName = vm::setName,
        onTogglePrimaryType = vm::togglePrimaryType,
        onToggleSecondaryType = vm::toggleSecondaryType,
        onToggleReleaseStatus = vm::toggleReleaseStatus,
        onSave = vm::save,
        onConsumeSavedNotice = vm::consumeSavedNotice,
        onConsumeWriteError = vm::consumeWriteError,
        modifier = modifier,
    )
}

/**
 * Metadata profiles list, the table Lidarr has and Sonarr does not. Rows
 * open the builder; the app bar adds a new profile from the server schema.
 * Delete asks for confirmation because it affects every artist using the
 * profile.
 */
@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun LidarrMetadataProfilesScreen(
    state: LidarrMetadataProfilesViewModel.UiState,
    lce: LceState<LidarrMetadataProfilesContent>,
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
    val content = (lce as? LceState.Content<LidarrMetadataProfilesContent>)?.data
    val refreshing =
        content != null && (lce as LceState.Content<LidarrMetadataProfilesContent>).refreshing

    val noticeMessage = when (state.notice) {
        MetadataProfilesNotice.DELETED -> stringResource(R.string.arr_lidarr_quality_notice_deleted)
        MetadataProfilesNotice.QUEUED ->
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
        title = stringResource(R.string.arr_lidarr_quality_metadata_profiles_title),
        subtitle = serverName,
        onBack = onBack,
        actions = {
            IconButton(onClick = onAdd) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription =
                        stringResource(R.string.arr_lidarr_quality_cd_add_metadata_profile),
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
                        title = stringResource(R.string.arr_lidarr_quality_metadata_profiles_empty_title),
                        message = stringResource(R.string.arr_lidarr_quality_metadata_profiles_empty_message),
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
                                MetadataProfileRow(
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
            title = { Text(stringResource(R.string.arr_lidarr_quality_delete_metadata_profile_title)) },
            text = { Text(stringResource(R.string.arr_lidarr_quality_delete_metadata_profile_message)) },
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
private fun MetadataProfileRow(
    profile: MetadataProfileResource,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
) {
    val draft = MetadataProfileDraft.from(profile, isExisting = true)
    val (primary, secondary, statuses) = draft.allowedCounts()
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
                    R.string.arr_lidarr_quality_metadata_profiles_row_support,
                    primary,
                    secondary,
                    statuses,
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onDelete) {
            Icon(
                imageVector = Icons.Filled.Delete,
                contentDescription = stringResource(
                    R.string.arr_lidarr_quality_cd_delete_metadata_profile,
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
 * Schema-driven metadata profile builder: one row per primary album type,
 * secondary album type, and release status, each with an allowed checkbox.
 * Allowed rows are the album kinds Lidarr monitors for artists on this
 * profile.
 */
@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun LidarrMetadataProfileEditorScreen(
    state: LidarrMetadataProfileEditorViewModel.UiState,
    lce: LceState<LidarrMetadataProfileEditorContent>,
    serverName: String?,
    onBack: (() -> Unit)?,
    onRefresh: () -> Unit,
    onSetName: (String) -> Unit,
    onTogglePrimaryType: (ProfilePrimaryAlbumTypeItemResource) -> Unit,
    onToggleSecondaryType: (ProfileSecondaryAlbumTypeItemResource) -> Unit,
    onToggleReleaseStatus: (ProfileReleaseStatusItemResource) -> Unit,
    onSave: () -> Unit,
    onConsumeSavedNotice: () -> Unit,
    onConsumeWriteError: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val content = (lce as? LceState.Content<LidarrMetadataProfileEditorContent>)?.data

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
                R.string.arr_lidarr_quality_metadata_profile_editor_title_edit
            } else {
                R.string.arr_lidarr_quality_metadata_profile_editor_title_new
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
                            onValue = onSetName,
                        )
                        QualitySectionHeader(
                            stringResource(R.string.arr_lidarr_quality_metadata_profile_section_primary),
                        )
                        data.draft.primaryAlbumTypes.forEach { item ->
                            MetadataItemRow(
                                label = item.albumType?.name.orEmpty(),
                                allowed = item.allowed,
                                onToggle = { onTogglePrimaryType(item) },
                            )
                        }
                        QualitySectionHeader(
                            stringResource(R.string.arr_lidarr_quality_metadata_profile_section_secondary),
                        )
                        data.draft.secondaryAlbumTypes.forEach { item ->
                            MetadataItemRow(
                                label = item.albumType?.name.orEmpty(),
                                allowed = item.allowed,
                                onToggle = { onToggleSecondaryType(item) },
                            )
                        }
                        QualitySectionHeader(
                            stringResource(R.string.arr_lidarr_quality_metadata_profile_section_statuses),
                        )
                        data.draft.releaseStatuses.forEach { item ->
                            MetadataItemRow(
                                label = item.releaseStatus?.name.orEmpty(),
                                allowed = item.allowed,
                                onToggle = { onToggleReleaseStatus(item) },
                            )
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
private fun MetadataItemRow(
    label: String,
    allowed: Boolean,
    onToggle: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(onClickLabel = label, onClick = onToggle),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )
        Checkbox(
            checked = allowed,
            onCheckedChange = { onToggle() },
            modifier = Modifier.heightIn(min = 48.dp),
        )
    }
}
