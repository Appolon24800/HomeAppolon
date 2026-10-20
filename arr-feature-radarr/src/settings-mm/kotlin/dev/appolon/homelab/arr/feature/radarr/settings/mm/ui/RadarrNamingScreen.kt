package dev.appolon.homelab.arr.feature.radarr.settings.mm.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
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
import dev.appolon.homelab.arr.api.radarr.model.ColonReplacementFormat
import dev.appolon.homelab.arr.core.ArrError
import dev.appolon.homelab.arr.core.ServerId
import dev.appolon.homelab.arr.feature.radarr.R
import dev.appolon.homelab.arr.feature.radarr.settings.mm.RadarrSettingsMmClients
import dev.appolon.homelab.arr.feature.radarr.settings.mm.data.NamingToken
import dev.appolon.homelab.arr.feature.radarr.settings.mm.data.NamingTokens
import dev.appolon.homelab.arr.feature.radarr.settings.mm.data.RadarrMediaManagementRepository
import dev.appolon.homelab.arr.feature.radarr.settings.mm.vm.NamingDraft
import dev.appolon.homelab.arr.feature.radarr.settings.mm.vm.NamingNotice
import dev.appolon.homelab.arr.feature.radarr.settings.mm.vm.RadarrNamingContent
import dev.appolon.homelab.arr.feature.radarr.settings.mm.vm.RadarrNamingViewModel
import dev.appolon.homelab.arr.ui.components.LabeledSwitch
import dev.appolon.homelab.arr.ui.lce.ArrLceBox
import dev.appolon.homelab.arr.ui.lce.ArrPullToRefreshBox
import dev.appolon.homelab.arr.ui.lce.LceState
import dev.appolon.homelab.arr.ui.lce.arrErrorMessage
import dev.appolon.homelab.arr.ui.shell.ArrScaffold

/**
 * Destination for the naming route: wires the shared config repository
 * (naming half) and the naming ViewModel for this route's server.
 */
@Composable
fun RadarrNamingDestination(
    serverId: ServerId,
    clients: RadarrSettingsMmClients,
    serverName: String? = null,
    onBack: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val repository = remember(serverId, clients) {
        RadarrMediaManagementRepository(
            serverId = serverId,
            store = clients.store,
            mediaManagementApi = clients.apis.mediaManagementConfigApi,
            namingApi = clients.apis.namingConfigApi,
            actionQueue = clients.actionQueue,
        )
    }
    val vm: RadarrNamingViewModel = viewModel(key = "radarr-naming-" + serverId.value) {
        RadarrNamingViewModel(
            serverId = serverId,
            repository = repository,
        )
    }
    val state by vm.state.collectAsState()
    val lce by vm.lce.collectAsState()

    RadarrNamingScreen(
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
 * debounced fetch answers. On tablet widths the preview sits beside the
 * fields instead of under them.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RadarrNamingScreen(
    state: RadarrNamingViewModel.UiState,
    lce: LceState<RadarrNamingContent>,
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
    val content = (lce as? LceState.Content<RadarrNamingContent>)?.data
    val refreshing = content != null && (lce as LceState.Content<RadarrNamingContent>).refreshing

    NoticeHost(
        notice = state.notice,
        writeError = state.writeError,
        snackbarHostState = snackbarHostState,
        onConsumeNotice = onConsumeNotice,
        onConsumeWriteError = onConsumeWriteError,
    )

    ArrScaffold(
        title = stringResource(R.string.arr_radarr_naming_title),
        subtitle = serverName,
        onBack = onBack,
        actions = {
            if (content?.dirty == true) {
                IconButton(onClick = onSave, enabled = !state.saving) {
                    Icon(
                        imageVector = Icons.Filled.Save,
                        contentDescription = stringResource(R.string.arr_radarr_naming_cd_save),
                    )
                }
            }
            IconButton(onClick = onRefresh) {
                Icon(
                    imageVector = Icons.Filled.Refresh,
                    contentDescription = stringResource(R.string.arr_radarr_naming_cd_refresh),
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
                                    title = stringResource(R.string.arr_radarr_naming_movie_format),
                                    format = draft.standardMovieFormat,
                                    tokens = NamingTokens.movieFileTokens,
                                    onNewFormat = { text ->
                                        onEdit { it.copy(standardMovieFormat = text) }
                                    },
                                    onInsertToken = onInsertToken,
                                )
                                FormatEditor(
                                    title = stringResource(R.string.arr_radarr_naming_folder_format),
                                    format = draft.movieFolderFormat,
                                    tokens = NamingTokens.movieFolderTokens,
                                    onNewFormat = { text ->
                                        onEdit { it.copy(movieFolderFormat = text) }
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
                                PreviewCard(
                                    title = stringResource(R.string.arr_radarr_naming_movie_preview),
                                    localPreview = data.localMoviePreview,
                                    serverExample = data.serverExample?.movieExample,
                                )
                                PreviewCard(
                                    title = stringResource(R.string.arr_radarr_naming_folder_preview),
                                    localPreview = data.localFolderPreview,
                                    serverExample = data.serverExample?.movieFolderExample,
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
                            NamingToggles(draft, onEdit)
                            FormatEditor(
                                title = stringResource(R.string.arr_radarr_naming_movie_format),
                                format = draft.standardMovieFormat,
                                tokens = NamingTokens.movieFileTokens,
                                onNewFormat = { text ->
                                    onEdit { it.copy(standardMovieFormat = text) }
                                },
                                onInsertToken = onInsertToken,
                            )
                            PreviewCard(
                                title = stringResource(R.string.arr_radarr_naming_movie_preview),
                                localPreview = data.localMoviePreview,
                                serverExample = data.serverExample?.movieExample,
                            )
                            FormatEditor(
                                title = stringResource(R.string.arr_radarr_naming_folder_format),
                                format = draft.movieFolderFormat,
                                tokens = NamingTokens.movieFolderTokens,
                                onNewFormat = { text ->
                                    onEdit { it.copy(movieFolderFormat = text) }
                                },
                                onInsertToken = onInsertToken,
                            )
                            PreviewCard(
                                title = stringResource(R.string.arr_radarr_naming_folder_preview),
                                localPreview = data.localFolderPreview,
                                serverExample = data.serverExample?.movieFolderExample,
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
            label = stringResource(R.string.arr_radarr_naming_rename_movies),
            supportingText = stringResource(R.string.arr_radarr_naming_rename_movies_support),
            checked = draft.renameMovies,
            onCheckedChange = { value -> onEdit { it.copy(renameMovies = value) } },
        )
        LabeledSwitch(
            label = stringResource(R.string.arr_radarr_naming_replace_illegal),
            supportingText = stringResource(R.string.arr_radarr_naming_replace_illegal_support),
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
            label = { Text(stringResource(R.string.arr_radarr_naming_format_hint)) },
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            text = stringResource(R.string.arr_radarr_naming_token_hint),
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
                    stringResource(R.string.arr_radarr_naming_preview_empty)
                },
                style = MaterialTheme.typography.titleMedium,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 4.dp),
            )
            if (serverExample != null) {
                Text(
                    text = stringResource(R.string.arr_radarr_naming_preview_server_source),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            } else {
                Text(
                    text = stringResource(R.string.arr_radarr_naming_preview_local_source),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}

/** Exposed dropdown for the colon replacement wire enum. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ColonReplacementDropdown(
    selected: ColonReplacementFormat,
    onSelect: (ColonReplacementFormat) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
    ) {
        OutlinedTextField(
            value = selected.toString(),
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            label = { Text(stringResource(R.string.arr_radarr_naming_colon_replacement)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(MenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            ColonReplacementFormat.entries.forEach { entry ->
                DropdownMenuItem(
                    text = { Text(entry.toString()) },
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
private fun NoticeHost(
    notice: NamingNotice?,
    writeError: ArrError?,
    snackbarHostState: SnackbarHostState,
    onConsumeNotice: () -> Unit,
    onConsumeWriteError: () -> Unit,
) {
    val message = when (notice) {
        NamingNotice.SAVED -> stringResource(R.string.arr_radarr_naming_notice_saved)
        NamingNotice.SAVED_QUEUED -> stringResource(R.string.arr_radarr_naming_notice_saved_queued)
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
