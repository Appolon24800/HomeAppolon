package dev.appolon.homelab.arr.feature.sonarr.settings.mm.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.appolon.homelab.arr.api.sonarr.model.EpisodeTitleRequiredType
import dev.appolon.homelab.arr.api.sonarr.model.FileDateType
import dev.appolon.homelab.arr.api.sonarr.model.ProperDownloadTypes
import dev.appolon.homelab.arr.api.sonarr.model.RescanAfterRefreshType
import dev.appolon.homelab.arr.api.sonarr.model.SeasonPackUpgradeType
import dev.appolon.homelab.arr.core.ArrError
import dev.appolon.homelab.arr.core.ServerId
import dev.appolon.homelab.arr.feature.sonarr.R
import dev.appolon.homelab.arr.feature.sonarr.settings.mm.SonarrSettingsMmClients
import dev.appolon.homelab.arr.feature.sonarr.settings.mm.data.SonarrFileSystemRepository
import dev.appolon.homelab.arr.feature.sonarr.settings.mm.data.SonarrMediaManagementRepository
import dev.appolon.homelab.arr.feature.sonarr.settings.mm.vm.MediaManagementDraft
import dev.appolon.homelab.arr.feature.sonarr.settings.mm.vm.MediaManagementNotice
import dev.appolon.homelab.arr.feature.sonarr.settings.mm.vm.SonarrMediaManagementContent
import dev.appolon.homelab.arr.feature.sonarr.settings.mm.vm.SonarrMediaManagementViewModel
import dev.appolon.homelab.arr.ui.components.LabeledSwitch
import dev.appolon.homelab.arr.ui.lce.ArrLceBox
import dev.appolon.homelab.arr.ui.lce.ArrPullToRefreshBox
import dev.appolon.homelab.arr.ui.lce.LceState
import dev.appolon.homelab.arr.ui.lce.arrErrorMessage
import dev.appolon.homelab.arr.ui.shell.ArrScaffold

/**
 * Destination for the media management route: wires the repository and
 * ViewModel for this route's server and hosts the filesystem browser used
 * by the path fields.
 */
@Composable
fun SonarrMediaManagementDestination(
    serverId: ServerId,
    clients: SonarrSettingsMmClients,
    serverName: String? = null,
    onBack: (() -> Unit)? = null,
    onOpenNaming: () -> Unit = {},
    onOpenRootFolders: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val repository = remember(serverId, clients) {
        SonarrMediaManagementRepository(
            serverId = serverId,
            store = clients.store,
            mediaManagementApi = clients.apis.mediaManagementConfigApi,
            namingApi = clients.apis.namingConfigApi,
            actionQueue = clients.actionQueue,
        )
    }
    val fileSystemRepository = remember(serverId, clients) {
        SonarrFileSystemRepository(clients.apis.fileSystemApi)
    }
    val vm: SonarrMediaManagementViewModel = viewModel(key = "sonarr-mm-" + serverId.value) {
        SonarrMediaManagementViewModel(
            serverId = serverId,
            repository = repository,
        )
    }
    val state by vm.state.collectAsState()
    val lce by vm.lce.collectAsState()

    SonarrMediaManagementScreen(
        state = state,
        lce = lce,
        serverName = serverName,
        onBack = onBack,
        onRefresh = vm::refresh,
        onEdit = vm::edit,
        onSave = vm::save,
        onOpenNaming = onOpenNaming,
        onOpenRootFolders = onOpenRootFolders,
        fileSystemRepository = fileSystemRepository,
        onPickField = vm::setPickedField,
        onApplyPickedPath = vm::applyPickedPath,
        onConsumeNotice = vm::consumeNotice,
        onConsumeWriteError = vm::consumeWriteError,
        modifier = modifier,
    )
}

/**
 * The import and file management toggles screen. Sections mirror Sonarr's
 * media management page; the chmod and chown fields appear under the
 * permissions toggle because the server only honors them then. Save is
 * enabled while the draft is dirty and PUTs the whole config at once.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SonarrMediaManagementScreen(
    state: SonarrMediaManagementViewModel.UiState,
    lce: LceState<SonarrMediaManagementContent>,
    serverName: String?,
    onBack: (() -> Unit)?,
    onRefresh: () -> Unit,
    onEdit: (transform: (MediaManagementDraft) -> MediaManagementDraft) -> Unit,
    onSave: () -> Unit,
    onOpenNaming: () -> Unit,
    onOpenRootFolders: () -> Unit,
    fileSystemRepository: SonarrFileSystemRepository,
    onPickField: (String?) -> Unit,
    onApplyPickedPath: (String) -> Unit,
    onConsumeNotice: () -> Unit,
    onConsumeWriteError: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val content = (lce as? LceState.Content<SonarrMediaManagementContent>)?.data
    val refreshing = content != null && (lce as LceState.Content<SonarrMediaManagementContent>).refreshing

    NoticeHost(
        notice = state.notice,
        writeError = state.writeError,
        snackbarHostState = snackbarHostState,
        onConsumeNotice = onConsumeNotice,
        onConsumeWriteError = onConsumeWriteError,
    )

    ArrScaffold(
        title = stringResource(R.string.arr_sonarr_mm_title),
        subtitle = serverName,
        onBack = onBack,
        actions = {
            if (content?.dirty == true) {
                IconButton(onClick = onSave, enabled = !state.saving) {
                    Icon(
                        imageVector = Icons.Filled.Save,
                        contentDescription = stringResource(R.string.arr_sonarr_mm_cd_save),
                    )
                }
            }
            IconButton(onClick = onRefresh) {
                Icon(
                    imageVector = Icons.Filled.Refresh,
                    contentDescription = stringResource(R.string.arr_sonarr_mm_cd_refresh),
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
            BoxWithConstraints(Modifier.fillMaxSize()) {
                // Tablet: sections flow in two columns so the whole page is
                // reachable without long scrolls on wide screens.
                val twoPane = maxWidth >= ExpandedPaneWidth
                ArrLceBox(
                    state = lce,
                    onRetry = onRefresh,
                    modifier = Modifier.fillMaxSize(),
                ) { data ->
                    val draft = data.draft
                    if (twoPane) {
                        Row(Modifier.fillMaxSize()) {
                            Column(
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .widthIn(max = SectionMaxWidth)
                                    .verticalScroll(rememberScrollState())
                                    .padding(16.dp),
                            ) {
                                if (data.pendingActions > 0) PendingSyncBanner(data.pendingActions)
                                LinksSection(onOpenNaming = onOpenNaming, onOpenRootFolders = onOpenRootFolders)
                                ImportingSection(draft, onEdit)
                            }
                            Column(
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .widthIn(max = SectionMaxWidth)
                                    .verticalScroll(rememberScrollState())
                                    .padding(16.dp),
                            ) {
                                FileManagementSection(draft, onEdit, onPickField)
                                PermissionsSection(draft, onEdit)
                                ScriptImportSection(draft, onEdit, onPickField)
                                MediaInfoSection(draft, onEdit)
                            }
                        }
                    } else {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier
                                .fillMaxSize()
                                .widthIn(max = SectionMaxWidth)
                                .verticalScroll(rememberScrollState())
                                .padding(16.dp),
                        ) {
                            if (data.pendingActions > 0) PendingSyncBanner(data.pendingActions)
                            LinksSection(onOpenNaming = onOpenNaming, onOpenRootFolders = onOpenRootFolders)
                            ImportingSection(draft, onEdit)
                            FileManagementSection(draft, onEdit, onPickField)
                            PermissionsSection(draft, onEdit)
                            ScriptImportSection(draft, onEdit, onPickField)
                            MediaInfoSection(draft, onEdit)
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

    if (state.pickedField != null && content != null) {
        val initial = when (state.pickedField) {
            SonarrMediaManagementViewModel.FIELD_RECYCLE_BIN -> content.draft.recycleBin.ifBlank { "/" }
            SonarrMediaManagementViewModel.FIELD_SCRIPT_IMPORT -> content.draft.scriptImportPath.ifBlank { "/" }
            else -> "/"
        }
        FileSystemBrowserDialog(
            repository = fileSystemRepository,
            initialPath = initial,
            onPick = onApplyPickedPath,
            onDismiss = { onPickField(null) },
        )
    }
}

@Composable
private fun LinksSection(
    onOpenNaming: () -> Unit,
    onOpenRootFolders: () -> Unit,
) {
    SectionHeader(stringResource(R.string.arr_sonarr_mm_section_media_management))
    LinkRow(
        title = stringResource(R.string.arr_sonarr_mm_link_naming),
        supporting = stringResource(R.string.arr_sonarr_mm_link_naming_support),
        icon = Icons.Filled.DriveFileRenameOutline,
        onClick = onOpenNaming,
    )
    LinkRow(
        title = stringResource(R.string.arr_sonarr_mm_link_root_folders),
        supporting = stringResource(R.string.arr_sonarr_mm_link_root_folders_support),
        icon = Icons.Filled.CreateNewFolder,
        onClick = onOpenRootFolders,
    )
}

@Composable
private fun ImportingSection(
    draft: MediaManagementDraft,
    onEdit: (transform: (MediaManagementDraft) -> MediaManagementDraft) -> Unit,
) {
    SectionHeader(stringResource(R.string.arr_sonarr_mm_section_importing))
    LabeledSwitch(
        label = stringResource(R.string.arr_sonarr_mm_skip_free_space),
        supportingText = stringResource(R.string.arr_sonarr_mm_skip_free_space_support),
        checked = draft.skipFreeSpaceCheckWhenImporting,
        onCheckedChange = { value ->
            onEdit { it.copy(skipFreeSpaceCheckWhenImporting = value) }
        },
    )
    TextField(
        value = draft.minimumFreeSpaceWhenImporting,
        label = stringResource(R.string.arr_sonarr_mm_minimum_free_space),
        supporting = stringResource(R.string.arr_sonarr_mm_minimum_free_space_support),
        onValue = { text -> onEdit { it.copy(minimumFreeSpaceWhenImporting = text) } },
    )
    LabeledSwitch(
        label = stringResource(R.string.arr_sonarr_mm_hardlinks),
        supportingText = stringResource(R.string.arr_sonarr_mm_hardlinks_support),
        checked = draft.copyUsingHardlinks,
        onCheckedChange = { value ->
            onEdit { it.copy(copyUsingHardlinks = value) }
        },
    )
    LabeledSwitch(
        label = stringResource(R.string.arr_sonarr_mm_import_extra_files),
        checked = draft.importExtraFiles,
        onCheckedChange = { value -> onEdit { it.copy(importExtraFiles = value) } },
    )
    if (draft.importExtraFiles) {
        TextField(
            value = draft.extraFileExtensions,
            label = stringResource(R.string.arr_sonarr_mm_extra_extensions),
            supporting = stringResource(R.string.arr_sonarr_mm_extra_extensions_support),
            onValue = { text -> onEdit { it.copy(extraFileExtensions = text) } },
        )
    }
    TextField(
        value = draft.userRejectedExtensions,
        label = stringResource(R.string.arr_sonarr_mm_rejected_extensions),
        supporting = stringResource(R.string.arr_sonarr_mm_rejected_extensions_support),
        onValue = { text -> onEdit { it.copy(userRejectedExtensions = text) } },
    )
    EnumDropdown(
        label = stringResource(R.string.arr_sonarr_mm_propers),
        selected = draft.downloadPropersAndRepacks,
        entries = ProperDownloadTypes.entries,
        labelOf = { it.wireLabel() },
        onSelect = { choice -> onEdit { it.copy(downloadPropersAndRepacks = choice) } },
    )
}

@Composable
private fun FileManagementSection(
    draft: MediaManagementDraft,
    onEdit: (transform: (MediaManagementDraft) -> MediaManagementDraft) -> Unit,
    onPickField: (String?) -> Unit,
) {
    SectionHeader(stringResource(R.string.arr_sonarr_mm_section_file_management))
    LabeledSwitch(
        label = stringResource(R.string.arr_sonarr_mm_auto_unmonitor),
        checked = draft.autoUnmonitorPreviouslyDownloadedEpisodes,
        onCheckedChange = { value ->
            onEdit { it.copy(autoUnmonitorPreviouslyDownloadedEpisodes = value) }
        },
    )
    LabeledSwitch(
        label = stringResource(R.string.arr_sonarr_mm_create_empty_folders),
        checked = draft.createEmptySeriesFolders,
        onCheckedChange = { value -> onEdit { it.copy(createEmptySeriesFolders = value) } },
    )
    LabeledSwitch(
        label = stringResource(R.string.arr_sonarr_mm_delete_empty_folders),
        checked = draft.deleteEmptyFolders,
        onCheckedChange = { value -> onEdit { it.copy(deleteEmptyFolders = value) } },
    )
    EnumDropdown(
        label = stringResource(R.string.arr_sonarr_mm_file_date),
        selected = draft.fileDate,
        entries = FileDateType.entries,
        labelOf = { it.wireLabel() },
        onSelect = { choice -> onEdit { it.copy(fileDate = choice) } },
    )
    EnumDropdown(
        label = stringResource(R.string.arr_sonarr_mm_rescan_after_refresh),
        selected = draft.rescanAfterRefresh,
        entries = RescanAfterRefreshType.entries,
        labelOf = { it.wireLabel() },
        onSelect = { choice -> onEdit { it.copy(rescanAfterRefresh = choice) } },
    )
    EnumDropdown(
        label = stringResource(R.string.arr_sonarr_mm_episode_title_required),
        selected = draft.episodeTitleRequired,
        entries = EpisodeTitleRequiredType.entries,
        labelOf = { it.wireLabel() },
        onSelect = { choice -> onEdit { it.copy(episodeTitleRequired = choice) } },
    )
    EnumDropdown(
        label = stringResource(R.string.arr_sonarr_mm_season_pack),
        selected = draft.seasonPackUpgrade,
        entries = SeasonPackUpgradeType.entries,
        labelOf = { it.wireLabel() },
        onSelect = { choice -> onEdit { it.copy(seasonPackUpgrade = choice) } },
    )
    if (draft.seasonPackUpgrade == SeasonPackUpgradeType.THRESHOLD) {
        TextField(
            value = draft.seasonPackUpgradeThreshold,
            label = stringResource(R.string.arr_sonarr_mm_season_pack_threshold),
            supporting = stringResource(R.string.arr_sonarr_mm_season_pack_threshold_support),
            onValue = { text -> onEdit { it.copy(seasonPackUpgradeThreshold = text) } },
        )
    }
    TextField(
        value = draft.recycleBin,
        label = stringResource(R.string.arr_sonarr_mm_recycle_bin),
        supporting = stringResource(R.string.arr_sonarr_mm_recycle_bin_support),
        onValue = { text -> onEdit { it.copy(recycleBin = text) } },
        browseField = SonarrMediaManagementViewModel.FIELD_RECYCLE_BIN,
        onBrowse = onPickField,
    )
    TextField(
        value = draft.recycleBinCleanupDays,
        label = stringResource(R.string.arr_sonarr_mm_recycle_bin_days),
        supporting = stringResource(R.string.arr_sonarr_mm_recycle_bin_days_support),
        onValue = { text -> onEdit { it.copy(recycleBinCleanupDays = text) } },
    )
}

@Composable
private fun PermissionsSection(
    draft: MediaManagementDraft,
    onEdit: (transform: (MediaManagementDraft) -> MediaManagementDraft) -> Unit,
) {
    SectionHeader(stringResource(R.string.arr_sonarr_mm_section_permissions))
    LabeledSwitch(
        label = stringResource(R.string.arr_sonarr_mm_set_permissions),
        supportingText = stringResource(R.string.arr_sonarr_mm_set_permissions_support),
        checked = draft.setPermissionsLinux,
        onCheckedChange = { value -> onEdit { it.copy(setPermissionsLinux = value) } },
    )
    if (draft.setPermissionsLinux) {
        TextField(
            value = draft.chmodFolder,
            label = stringResource(R.string.arr_sonarr_mm_chmod_folder),
            supporting = stringResource(R.string.arr_sonarr_mm_chmod_folder_support),
            onValue = { text -> onEdit { it.copy(chmodFolder = text) } },
        )
        TextField(
            value = draft.chownGroup,
            label = stringResource(R.string.arr_sonarr_mm_chown_group),
            supporting = stringResource(R.string.arr_sonarr_mm_chown_group_support),
            onValue = { text -> onEdit { it.copy(chownGroup = text) } },
        )
    }
}

@Composable
private fun ScriptImportSection(
    draft: MediaManagementDraft,
    onEdit: (transform: (MediaManagementDraft) -> MediaManagementDraft) -> Unit,
    onPickField: (String?) -> Unit,
) {
    SectionHeader(stringResource(R.string.arr_sonarr_mm_section_script_import))
    LabeledSwitch(
        label = stringResource(R.string.arr_sonarr_mm_use_script_import),
        checked = draft.useScriptImport,
        onCheckedChange = { value -> onEdit { it.copy(useScriptImport = value) } },
    )
    if (draft.useScriptImport) {
        TextField(
            value = draft.scriptImportPath,
            label = stringResource(R.string.arr_sonarr_mm_script_path),
            supporting = stringResource(R.string.arr_sonarr_mm_script_path_support),
            onValue = { text -> onEdit { it.copy(scriptImportPath = text) } },
            browseField = SonarrMediaManagementViewModel.FIELD_SCRIPT_IMPORT,
            onBrowse = onPickField,
        )
    }
}

@Composable
private fun MediaInfoSection(
    draft: MediaManagementDraft,
    onEdit: (transform: (MediaManagementDraft) -> MediaManagementDraft) -> Unit,
) {
    SectionHeader(stringResource(R.string.arr_sonarr_mm_section_media_info))
    LabeledSwitch(
        label = stringResource(R.string.arr_sonarr_mm_media_info),
        supportingText = stringResource(R.string.arr_sonarr_mm_media_info_support),
        checked = draft.enableMediaInfo,
        onCheckedChange = { value -> onEdit { it.copy(enableMediaInfo = value) } },
    )
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
    )
}

/** Navigation link to a sibling settings screen, whole row clickable. */
@Composable
private fun LinkRow(
    title: String,
    supporting: String,
    icon: ImageVector,
    onClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(onClickLabel = title, onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 12.dp),
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                supporting,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Outlined field for settings values, with an optional browse button. */
@Composable
private fun TextField(
    value: String,
    label: String,
    onValue: (String) -> Unit,
    supporting: String? = null,
    browseField: String? = null,
    onBrowse: (String?) -> Unit = {},
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValue,
        singleLine = true,
        label = { Text(label) },
        supportingText = supporting?.let { { Text(it) } },
        trailingIcon = browseField?.let { field ->
            {
                IconButton(onClick = { onBrowse(field) }) {
                    Icon(
                        imageVector = Icons.Filled.FolderOpen,
                        contentDescription = stringResource(
                            R.string.arr_sonarr_mm_cd_browse,
                            label,
                        ),
                    )
                }
            }
        },
        modifier = Modifier.fillMaxWidth(),
    )
}

/** Exposed dropdown for a wire enum, labeled by its spec spelling. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> EnumDropdown(
    label: String,
    selected: T,
    entries: List<T>,
    labelOf: (T) -> String,
    onSelect: (T) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
    ) {
        OutlinedTextField(
            value = labelOf(selected),
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(MenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            entries.forEach { entry ->
                DropdownMenuItem(
                    text = { Text(labelOf(entry)) },
                    onClick = {
                        onSelect(entry)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun PendingSyncBanner(pending: Int) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp),
    ) {
        Icon(
            imageVector = Icons.Filled.CloudUpload,
            contentDescription = stringResource(R.string.arr_sonarr_mm_cd_queued_indicator),
            tint = MaterialTheme.colorScheme.tertiary,
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = stringResource(R.string.arr_sonarr_mm_pending_banner, pending),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.tertiary,
        )
    }
}

@Composable
private fun NoticeHost(
    notice: MediaManagementNotice?,
    writeError: ArrError?,
    snackbarHostState: SnackbarHostState,
    onConsumeNotice: () -> Unit,
    onConsumeWriteError: () -> Unit,
) {
    val message = when (notice) {
        MediaManagementNotice.SAVED -> stringResource(R.string.arr_sonarr_mm_notice_saved)
        MediaManagementNotice.SAVED_QUEUED -> stringResource(R.string.arr_sonarr_mm_notice_saved_queued)
        null -> null
    }
    LaunchedEffect(message) {
        if (message != null) {
            snackbarHostState.showSnackbar(message)
            onConsumeNotice()
        }
    }
    val errorText = writeError?.let { arrErrorMessage(it) }
    LaunchedEffect(errorText) {
        if (errorText != null) {
            snackbarHostState.showSnackbar(errorText)
            onConsumeWriteError()
        }
    }
}

private fun ProperDownloadTypes.wireLabel(): String = toString()
private fun FileDateType.wireLabel(): String = toString()
private fun RescanAfterRefreshType.wireLabel(): String = toString()
private fun EpisodeTitleRequiredType.wireLabel(): String = toString()
private fun SeasonPackUpgradeType.wireLabel(): String = toString()

/** Material window size class breakpoint for expanded tablet widths. */
private val ExpandedPaneWidth = 840.dp

/** Settings sections read best under this width on tablets. */
private val SectionMaxWidth = 640.dp
