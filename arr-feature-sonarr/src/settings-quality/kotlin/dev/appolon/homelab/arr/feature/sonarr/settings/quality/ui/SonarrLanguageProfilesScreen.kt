package dev.appolon.homelab.arr.feature.sonarr.settings.quality.ui

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
import dev.appolon.homelab.arr.api.sonarr.model.Language
import dev.appolon.homelab.arr.api.sonarr.model.LanguageProfileItemResource
import dev.appolon.homelab.arr.api.sonarr.model.LanguageProfileResource
import dev.appolon.homelab.arr.core.ServerId
import dev.appolon.homelab.arr.feature.sonarr.R
import dev.appolon.homelab.arr.feature.sonarr.settings.quality.SonarrQualityClients
import dev.appolon.homelab.arr.feature.sonarr.settings.quality.vm.LanguageProfileDraft
import dev.appolon.homelab.arr.feature.sonarr.settings.quality.vm.LanguageProfilesNotice
import dev.appolon.homelab.arr.feature.sonarr.settings.quality.vm.SonarrLanguageProfileEditorContent
import dev.appolon.homelab.arr.feature.sonarr.settings.quality.vm.SonarrLanguageProfileEditorViewModel
import dev.appolon.homelab.arr.feature.sonarr.settings.quality.vm.SonarrLanguageProfilesContent
import dev.appolon.homelab.arr.feature.sonarr.settings.quality.vm.SonarrLanguageProfilesViewModel
import dev.appolon.homelab.arr.ui.components.EmptyState
import dev.appolon.homelab.arr.ui.components.LabeledSwitch
import dev.appolon.homelab.arr.ui.lce.ArrLceBox
import dev.appolon.homelab.arr.ui.lce.ArrPullToRefreshBox
import dev.appolon.homelab.arr.ui.lce.LceState
import dev.appolon.homelab.arr.ui.shell.ArrScaffold

/** Destination for the language profiles list route. */
@Composable
fun SonarrLanguageProfilesDestination(
    serverId: ServerId,
    clients: SonarrQualityClients,
    serverName: String? = null,
    onBack: (() -> Unit)? = null,
    onOpenProfile: (Int?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val repository = remember(serverId, clients) { qualityRepository(serverId, clients) }
    val vm: SonarrLanguageProfilesViewModel =
        viewModel(key = "sonarr-language-profiles-" + serverId.value) {
            SonarrLanguageProfilesViewModel(repository)
        }
    val state by vm.state.collectAsState()
    val lce by vm.lce.collectAsState()

    SonarrLanguageProfilesScreen(
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

/** Destination for the schema-driven language profile builder route. */
@Composable
fun SonarrLanguageProfileEditorDestination(
    serverId: ServerId,
    profileId: Int?,
    clients: SonarrQualityClients,
    serverName: String? = null,
    onBack: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val repository = remember(serverId, clients) { qualityRepository(serverId, clients) }
    val vm: SonarrLanguageProfileEditorViewModel = viewModel(
        key = "sonarr-language-profile-editor-" + serverId.value + "-" + (profileId ?: "new"),
    ) {
        SonarrLanguageProfileEditorViewModel(profileId, repository)
    }
    val state by vm.state.collectAsState()
    val lce by vm.lce.collectAsState()

    SonarrLanguageProfileEditorScreen(
        state = state,
        lce = lce,
        serverName = serverName,
        onBack = onBack,
        onRefresh = vm::refresh,
        onSetName = vm::setName,
        onSetUpgradeAllowed = vm::setUpgradeAllowed,
        onToggleLanguage = vm::toggleLanguage,
        onSetCutoff = vm::setCutoff,
        onSave = vm::save,
        onConsumeSavedNotice = vm::consumeSavedNotice,
        onConsumeWriteError = vm::consumeWriteError,
        modifier = modifier,
    )
}

/**
 * Language profiles list, the table Sonarr has and Radarr does not. Rows
 * open the builder; the app bar adds a new profile from the server schema.
 * Delete asks for confirmation because it affects every series using the
 * profile.
 */
@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun SonarrLanguageProfilesScreen(
    state: SonarrLanguageProfilesViewModel.UiState,
    lce: LceState<SonarrLanguageProfilesContent>,
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
    val content = (lce as? LceState.Content<SonarrLanguageProfilesContent>)?.data
    val refreshing = content != null && (lce as LceState.Content<SonarrLanguageProfilesContent>).refreshing

    val noticeMessage = when (state.notice) {
        LanguageProfilesNotice.DELETED -> stringResource(R.string.arr_sonarr_quality_notice_deleted)
        LanguageProfilesNotice.QUEUED ->
            stringResource(R.string.arr_sonarr_quality_notice_saved_queued)
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
        title = stringResource(R.string.arr_sonarr_quality_language_profiles_title),
        subtitle = serverName,
        onBack = onBack,
        actions = {
            IconButton(onClick = onAdd) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = stringResource(R.string.arr_sonarr_quality_cd_add_language_profile),
                )
            }
            IconButton(onClick = onRefresh) {
                Icon(
                    imageVector = Icons.Filled.Refresh,
                    contentDescription = stringResource(R.string.arr_sonarr_quality_cd_refresh),
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
                        title = stringResource(R.string.arr_sonarr_quality_language_profiles_empty_title),
                        message = stringResource(R.string.arr_sonarr_quality_language_profiles_empty_message),
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
                                LanguageProfileRow(
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
            title = { Text(stringResource(R.string.arr_sonarr_quality_delete_language_profile_title)) },
            text = { Text(stringResource(R.string.arr_sonarr_quality_delete_language_profile_message)) },
            confirmButton = {
                TextButton(onClick = onDeleteConfirmed) {
                    Text(stringResource(R.string.arr_sonarr_quality_action_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { onRequestDelete(null) }) {
                    Text(stringResource(R.string.arr_sonarr_quality_action_cancel))
                }
            },
        )
    }
}

@Composable
private fun LanguageProfileRow(
    profile: LanguageProfileResource,
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
                    R.string.arr_sonarr_quality_language_profiles_row_support,
                    profile.languages.orEmpty().count { it.allowed },
                    profile.cutoff.name.orEmpty(),
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onDelete) {
            Icon(
                imageVector = Icons.Filled.Delete,
                contentDescription = stringResource(
                    R.string.arr_sonarr_quality_cd_delete_language_profile,
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
 * Schema-driven language profile builder: one row per language with an
 * allowed checkbox, the cutoff picker over the allowed languages, and the
 * upgrade grouping switch.
 */
@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun SonarrLanguageProfileEditorScreen(
    state: SonarrLanguageProfileEditorViewModel.UiState,
    lce: LceState<SonarrLanguageProfileEditorContent>,
    serverName: String?,
    onBack: (() -> Unit)?,
    onRefresh: () -> Unit,
    onSetName: (String) -> Unit,
    onSetUpgradeAllowed: (Boolean) -> Unit,
    onToggleLanguage: (LanguageProfileItemResource) -> Unit,
    onSetCutoff: (Language) -> Unit,
    onSave: () -> Unit,
    onConsumeSavedNotice: () -> Unit,
    onConsumeWriteError: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val content = (lce as? LceState.Content<SonarrLanguageProfileEditorContent>)?.data

    val savedMessage = when {
        state.savedNotice -> stringResource(R.string.arr_sonarr_quality_notice_saved)
        state.savedQueued -> stringResource(R.string.arr_sonarr_quality_notice_saved_queued)
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
                R.string.arr_sonarr_quality_language_profile_editor_title_edit
            } else {
                R.string.arr_sonarr_quality_language_profile_editor_title_new
            },
        ),
        subtitle = serverName,
        onBack = onBack,
        actions = {
            if (content?.dirty == true) {
                IconButton(onClick = onSave, enabled = !state.saving) {
                    Icon(
                        imageVector = Icons.Filled.Save,
                        contentDescription = stringResource(R.string.arr_sonarr_quality_cd_save),
                    )
                }
            }
            IconButton(onClick = onRefresh) {
                Icon(
                    imageVector = Icons.Filled.Refresh,
                    contentDescription = stringResource(R.string.arr_sonarr_quality_cd_refresh),
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
                            label = stringResource(R.string.arr_sonarr_quality_profile_name),
                            onValue = onSetName,
                        )
                        LabeledSwitch(
                            label = stringResource(R.string.arr_sonarr_quality_language_profile_upgrade_allowed),
                            supportingText = stringResource(
                                R.string.arr_sonarr_quality_language_profile_upgrade_allowed_support,
                            ),
                            checked = data.draft.upgradeAllowed,
                            onCheckedChange = onSetUpgradeAllowed,
                        )
                        QualitySectionHeader(
                            stringResource(R.string.arr_sonarr_quality_language_profile_section_languages),
                        )
                        data.draft.items.forEach { item ->
                            LanguageItemRow(
                                item = item,
                                isCutoff = item.allowed && item.language.id == data.draft.cutoff.id,
                                onToggle = onToggleLanguage,
                                onSetCutoff = onSetCutoff,
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
private fun LanguageItemRow(
    item: LanguageProfileItemResource,
    isCutoff: Boolean,
    onToggle: (LanguageProfileItemResource) -> Unit,
    onSetCutoff: (Language) -> Unit,
) {
    Column {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp),
        ) {
            Text(
                text = item.language.name.orEmpty(),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f),
            )
            Checkbox(
                checked = item.allowed,
                onCheckedChange = { onToggle(item) },
                modifier = Modifier.heightIn(min = 48.dp),
            )
        }
        // Allowed languages double as the cutoff picker through a radio
        // row, mirroring the quality builder's upgrade-until rows.
        if (item.allowed) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .clickable(
                        onClickLabel = stringResource(
                            R.string.arr_sonarr_quality_language_profile_cd_cutoff,
                            item.language.name.orEmpty(),
                        ),
                    ) { onSetCutoff(item.language) }
                    .padding(horizontal = 4.dp),
            ) {
                RadioButton(
                    selected = isCutoff,
                    onClick = { onSetCutoff(item.language) },
                    modifier = Modifier.heightIn(min = 48.dp),
                )
                Text(
                    text = stringResource(R.string.arr_sonarr_quality_language_profile_cutoff_row),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
