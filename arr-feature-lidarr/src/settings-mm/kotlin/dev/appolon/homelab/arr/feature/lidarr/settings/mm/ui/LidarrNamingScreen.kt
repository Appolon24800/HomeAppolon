package dev.appolon.homelab.arr.feature.lidarr.settings.mm.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.appolon.homelab.arr.core.ArrError
import dev.appolon.homelab.arr.core.ServerId
import dev.appolon.homelab.arr.feature.lidarr.R
import dev.appolon.homelab.arr.feature.lidarr.settings.mm.LidarrSettingsMmClients
import dev.appolon.homelab.arr.feature.lidarr.settings.mm.data.LidarrMediaManagementRepository
import dev.appolon.homelab.arr.feature.lidarr.settings.mm.data.NamingToken
import dev.appolon.homelab.arr.feature.lidarr.settings.mm.data.NamingTokens
import dev.appolon.homelab.arr.feature.lidarr.settings.mm.vm.LidarrNamingContent
import dev.appolon.homelab.arr.feature.lidarr.settings.mm.vm.LidarrNamingViewModel
import dev.appolon.homelab.arr.feature.lidarr.settings.mm.vm.NamingDraft
import dev.appolon.homelab.arr.feature.lidarr.settings.mm.vm.NamingNotice
import dev.appolon.homelab.arr.ui.components.LabeledSwitch
import dev.appolon.homelab.arr.ui.lce.ArrLceBox
import dev.appolon.homelab.arr.ui.lce.ArrPullToRefreshBox
import dev.appolon.homelab.arr.ui.lce.LceState
import dev.appolon.homelab.arr.ui.lce.arrErrorMessage
import dev.appolon.homelab.arr.ui.shell.ArrScaffold

/**
 * The colon replacement options Lidarr accepts on the wire. The spec
 * types the field as a raw int (there is no enum resource), so the labels
 * live here with their wire values.
 */
private val ColonReplacements = listOf(
    0 to "Smart",
    1 to "Dash",
    2 to "Delete",
    3 to "Space Dash",
    4 to "Space Dash Space",
    5 to "Custom",
)

/**
 * Destination for the naming route: wires the shared config repository
 * (naming half) and the naming ViewModel for this route's server.
 */
@Composable
fun LidarrNamingDestination(
    serverId: ServerId,
    clients: LidarrSettingsMmClients,
    serverName: String? = null,
    onBack: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val repository = remember(serverId, clients) {
        LidarrMediaManagementRepository(
            serverId = serverId,
            store = clients.store,
            mediaManagementApi = clients.apis.mediaManagementConfigApi,
            namingApi = clients.apis.namingConfigApi,
            actionQueue = clients.actionQueue,
        )
    }
    val vm: LidarrNamingViewModel = viewModel(key = "lidarr-naming-" + serverId.value) {
        LidarrNamingViewModel(
            serverId = serverId,
            repository = repository,
        )
    }
    val state by vm.state.collectAsState()
    val lce by vm.lce.collectAsState()

    LidarrNamingScreen(
        state = state,
        lce = lce,
        serverName = serverName,
        onBack = onBack,
        onRefresh = vm::refresh,
        onEdit = vm::edit,
        onInsertToken = vm::insertToken,
        onSave = vm::save,
        onConsumeNotice = vm::consumeNotice,
        onConsumeWriteError = vm::consumeWriteError,
        modifier = modifier,
    )
}

/**
 * The naming config editor. Each format field keeps its own
 * [TextFieldValue] so token chips can insert at the cursor; the preview
 * card below re-renders on every keystroke from the local sample
 * renderer, and swaps in the server's authoritative example when the
 * debounced fetch answers. On tablet widths the previews sit beside the
 * fields instead of under them.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LidarrNamingScreen(
    state: LidarrNamingViewModel.UiState,
    lce: LceState<LidarrNamingContent>,
    serverName: String?,
    onBack: (() -> Unit)?,
    onRefresh: () -> Unit,
    onEdit: (transform: (NamingDraft) -> NamingDraft) -> Unit,
    onInsertToken: (format: String, cursor: Int, token: String) -> Pair<String, Int>,
    onSave: () -> Unit,
    onConsumeNotice: () -> Unit,
    onConsumeWriteError: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val content = (lce as? LceState.Content<LidarrNamingContent>)?.data
    val refreshing = content != null && (lce as LceState.Content<LidarrNamingContent>).refreshing

    NoticeHost(
        notice = state.notice,
        writeError = state.writeError,
        snackbarHostState = snackbarHostState,
        onConsumeNotice = onConsumeNotice,
        onConsumeWriteError = onConsumeWriteError,
    )

    ArrScaffold(
        title = stringResource(R.string.arr_lidarr_naming_title),
        subtitle = serverName,
        onBack = onBack,
        actions = {
            if (content?.dirty == true) {
                IconButton(onClick = onSave, enabled = !state.saving) {
                    Icon(
                        imageVector = Icons.Filled.Save,
                        contentDescription = stringResource(R.string.arr_lidarr_naming_cd_save),
                    )
                }
            }
            IconButton(onClick = onRefresh) {
                Icon(
                    imageVector = Icons.Filled.Refresh,
                    contentDescription = stringResource(R.string.arr_lidarr_naming_cd_refresh),
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
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .verticalScroll(rememberScrollState())
                                    .padding(16.dp),
                            ) {
                                NamingToggles(draft, onEdit)
                                FormatEditor(
                                    title = stringResource(R.string.arr_lidarr_naming_track_format),
                                    format = draft.standardTrackFormat,
                                    tokens = NamingTokens.standardTrackTokens,
                                    onNewFormat = { text ->
                                        onEdit { it.copy(standardTrackFormat = text) }
                                    },
                                    onInsertToken = onInsertToken,
                                )
                                FormatEditor(
                                    title = stringResource(R.string.arr_lidarr_naming_multidisc_format),
                                    format = draft.multiDiscTrackFormat,
                                    tokens = NamingTokens.multiDiscTrackTokens,
                                    onNewFormat = { text ->
                                        onEdit { it.copy(multiDiscTrackFormat = text) }
                                    },
                                    onInsertToken = onInsertToken,
                                )
                                FormatEditor(
                                    title = stringResource(R.string.arr_lidarr_naming_artist_folder_format),
                                    format = draft.artistFolderFormat,
                                    tokens = NamingTokens.artistFolderTokens,
                                    onNewFormat = { text ->
                                        onEdit { it.copy(artistFolderFormat = text) }
                                    },
                                    onInsertToken = onInsertToken,
                                )
                            }
                            Column(
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .verticalScroll(rememberScrollState())
                                    .padding(16.dp),
                            ) {
                                if (data.pendingActions > 0) PendingSyncBanner(data.pendingActions)
                                PreviewCard(
                                    title = stringResource(R.string.arr_lidarr_naming_track_preview),
                                    localPreview = data.localTrackPreview,
                                    serverExample = data.serverExample?.standardTrackExample,
                                )
                                PreviewCard(
                                    title = stringResource(R.string.arr_lidarr_naming_multidisc_preview),
                                    localPreview = data.localMultiDiscPreview,
                                    serverExample = data.serverExample?.multiDiscTrackExample,
                                )
                                PreviewCard(
                                    title = stringResource(R.string.arr_lidarr_naming_artist_folder_preview),
                                    localPreview = data.localArtistFolderPreview,
                                    serverExample = data.serverExample?.artistFolderExample,
                                )
                            }
                        }
                    } else {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                                .padding(16.dp),
                        ) {
                            if (data.pendingActions > 0) PendingSyncBanner(data.pendingActions)
                            NamingToggles(draft, onEdit)
                            FormatEditor(
                                title = stringResource(R.string.arr_lidarr_naming_track_format),
                                format = draft.standardTrackFormat,
                                tokens = NamingTokens.standardTrackTokens,
                                onNewFormat = { text ->
                                    onEdit { it.copy(standardTrackFormat = text) }
                                },
                                onInsertToken = onInsertToken,
                            )
                            PreviewCard(
                                title = stringResource(R.string.arr_lidarr_naming_track_preview),
                                localPreview = data.localTrackPreview,
                                serverExample = data.serverExample?.standardTrackExample,
                            )
                            FormatEditor(
                                title = stringResource(R.string.arr_lidarr_naming_multidisc_format),
                                format = draft.multiDiscTrackFormat,
                                tokens = NamingTokens.multiDiscTrackTokens,
                                onNewFormat = { text ->
                                    onEdit { it.copy(multiDiscTrackFormat = text) }
                                },
                                onInsertToken = onInsertToken,
                            )
                            PreviewCard(
                                title = stringResource(R.string.arr_lidarr_naming_multidisc_preview),
                                localPreview = data.localMultiDiscPreview,
                                serverExample = data.serverExample?.multiDiscTrackExample,
                            )
                            FormatEditor(
                                title = stringResource(R.string.arr_lidarr_naming_artist_folder_format),
                                format = draft.artistFolderFormat,
                                tokens = NamingTokens.artistFolderTokens,
                                onNewFormat = { text ->
                                    onEdit { it.copy(artistFolderFormat = text) }
                                },
                                onInsertToken = onInsertToken,
                            )
                            PreviewCard(
                                title = stringResource(R.string.arr_lidarr_naming_artist_folder_preview),
                                localPreview = data.localArtistFolderPreview,
                                serverExample = data.serverExample?.artistFolderExample,
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
private fun NamingToggles(
    draft: NamingDraft,
    onEdit: (transform: (NamingDraft) -> NamingDraft) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        LabeledSwitch(
            label = stringResource(R.string.arr_lidarr_naming_rename_tracks),
            supportingText = stringResource(R.string.arr_lidarr_naming_rename_tracks_support),
            checked = draft.renameTracks,
            onCheckedChange = { value -> onEdit { it.copy(renameTracks = value) } },
        )
        LabeledSwitch(
            label = stringResource(R.string.arr_lidarr_naming_replace_illegal),
            supportingText = stringResource(R.string.arr_lidarr_naming_replace_illegal_support),
            checked = draft.replaceIllegalCharacters,
            onCheckedChange = { value -> onEdit { it.copy(replaceIllegalCharacters = value) } },
        )
        ColonReplacementDropdown(
            selected = draft.colonReplacementFormat,
            onSelect = { choice -> onEdit { it.copy(colonReplacementFormat = choice) } },
        )
    }
}

/**
 * One token-builder field. The [TextFieldValue] is remembered locally and
 * re-seeded when the incoming format changes externally (load, refresh),
 * while chip taps splice their token in at the cursor and move the cursor
 * past it.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FormatEditor(
    title: String,
    format: String,
    tokens: List<NamingToken>,
    onNewFormat: (String) -> Unit,
    onInsertToken: (format: String, cursor: Int, token: String) -> Pair<String, Int>,
) {
    var field by remember {
        mutableStateOf(TextFieldValue(format, TextRange(format.length)))
    }
    var lastSyncedFormat by remember { mutableStateOf(format) }
    // Re-seed only on external changes (load, refresh, chip insert already
    // updated `field`): keying remember() on `format` would reset the
    // cursor to the end on every keystroke.
    if (format != lastSyncedFormat && format != field.text) {
        lastSyncedFormat = format
        field = TextFieldValue(format, TextRange(format.length))
    }
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 4.dp),
        )
        OutlinedTextField(
            value = field,
            onValueChange = { next ->
                field = next
                lastSyncedFormat = next.text
                onNewFormat(next.text)
            },
            label = { Text(stringResource(R.string.arr_lidarr_naming_format_hint)) },
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            text = stringResource(R.string.arr_lidarr_naming_token_hint),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            tokens.forEach { token ->
                AssistChip(
                    onClick = {
                        val (nextText, nextCursor) = onInsertToken(field.text, field.selection.start, token.token)
                        field = TextFieldValue(nextText, TextRange(nextCursor))
                        onNewFormat(nextText)
                    },
                    label = { Text(token.token) },
                )
            }
        }
    }
}

/**
 * The live preview card. The local sample render is always shown; when
 * the server's examples endpoint has answered, its sample replaces the
 * local one and the label notes the source.
 */
@Composable
private fun PreviewCard(
    title: String,
    localPreview: String,
    serverExample: String?,
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val example = serverExample ?: localPreview
            Text(
                text = example.ifBlank {
                    stringResource(R.string.arr_lidarr_naming_preview_empty)
                },
                style = MaterialTheme.typography.titleMedium,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 4.dp),
            )
            if (serverExample != null) {
                Text(
                    text = stringResource(R.string.arr_lidarr_naming_preview_server_source),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            } else {
                Text(
                    text = stringResource(R.string.arr_lidarr_naming_preview_local_source),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}

/** Exposed dropdown for the colon replacement wire int. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ColonReplacementDropdown(
    selected: Int,
    onSelect: (Int) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedLabel = ColonReplacements.firstOrNull { it.first == selected }?.second
        ?: selected.toString()
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
    ) {
        OutlinedTextField(
            value = selectedLabel,
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            label = { Text(stringResource(R.string.arr_lidarr_naming_colon_replacement)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(MenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            ColonReplacements.forEach { (value, label) ->
                DropdownMenuItem(
                    text = { Text(label) },
                    onClick = {
                        onSelect(value)
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
            contentDescription = stringResource(R.string.arr_lidarr_mm_cd_queued_indicator),
            tint = MaterialTheme.colorScheme.tertiary,
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = stringResource(R.string.arr_lidarr_mm_pending_banner, pending),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.tertiary,
        )
    }
}

@Composable
private fun NoticeHost(
    notice: NamingNotice?,
    writeError: ArrError?,
    snackbarHostState: SnackbarHostState,
    onConsumeNotice: () -> Unit,
    onConsumeWriteError: () -> Unit,
) {
    val message = when (notice) {
        NamingNotice.SAVED -> stringResource(R.string.arr_lidarr_naming_notice_saved)
        NamingNotice.SAVED_QUEUED -> stringResource(R.string.arr_lidarr_naming_notice_saved_queued)
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

/** Material window size class breakpoint for expanded tablet widths. */
private val ExpandedPaneWidth = 840.dp
