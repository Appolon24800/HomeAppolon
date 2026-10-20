package dev.appolon.homelab.arr.feature.sonarr.settings.mm.ui

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
import dev.appolon.homelab.arr.core.ArrError
import dev.appolon.homelab.arr.core.ServerId
import dev.appolon.homelab.arr.feature.sonarr.R
import dev.appolon.homelab.arr.feature.sonarr.settings.mm.SonarrSettingsMmClients
import dev.appolon.homelab.arr.feature.sonarr.settings.mm.data.NamingToken
import dev.appolon.homelab.arr.feature.sonarr.settings.mm.data.NamingTokens
import dev.appolon.homelab.arr.feature.sonarr.settings.mm.data.SonarrMediaManagementRepository
import dev.appolon.homelab.arr.feature.sonarr.settings.mm.vm.NamingDraft
import dev.appolon.homelab.arr.feature.sonarr.settings.mm.vm.NamingNotice
import dev.appolon.homelab.arr.feature.sonarr.settings.mm.vm.SonarrNamingContent
import dev.appolon.homelab.arr.feature.sonarr.settings.mm.vm.SonarrNamingViewModel
import dev.appolon.homelab.arr.ui.components.LabeledSwitch
import dev.appolon.homelab.arr.ui.lce.ArrLceBox
import dev.appolon.homelab.arr.ui.lce.ArrPullToRefreshBox
import dev.appolon.homelab.arr.ui.lce.LceState
import dev.appolon.homelab.arr.ui.lce.arrErrorMessage
import dev.appolon.homelab.arr.ui.shell.ArrScaffold

/** One editable format: chip set plus where its preview values come from. */
private class FormatSpec(
    val title: String,
    val format: String,
    val tokens: List<NamingToken>,
    val previewTitle: String,
    val localPreview: String,
    val serverExample: String?,
    val onNewFormat: (String) -> Unit,
)

/**
 * Destination for the naming route: wires the shared config repository
 * (naming half) and the naming ViewModel for this route's server.
 */
@Composable
fun SonarrNamingDestination(
    serverId: ServerId,
    clients: SonarrSettingsMmClients,
    serverName: String? = null,
    onBack: (() -> Unit)? = null,
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
    val vm: SonarrNamingViewModel = viewModel(key = "sonarr-naming-" + serverId.value) {
        SonarrNamingViewModel(
            serverId = serverId,
            repository = repository,
        )
    }
    val state by vm.state.collectAsState()
    val lce by vm.lce.collectAsState()

    SonarrNamingScreen(
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
 * The episode naming editor. Each format field keeps its own
 * [TextFieldValue] so token chips can insert at the cursor; the preview
 * card below re-renders on every keystroke from the local sample
 * renderer, and swaps in the server's authoritative example when the
 * debounced fetch answers. On tablet widths the previews sit beside the
 * fields instead of under them.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SonarrNamingScreen(
    state: SonarrNamingViewModel.UiState,
    lce: LceState<SonarrNamingContent>,
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
    val content = (lce as? LceState.Content<SonarrNamingContent>)?.data
    val refreshing = content != null && (lce as LceState.Content<SonarrNamingContent>).refreshing

    NoticeHost(
        notice = state.notice,
        writeError = state.writeError,
        snackbarHostState = snackbarHostState,
        onConsumeNotice = onConsumeNotice,
        onConsumeWriteError = onConsumeWriteError,
    )

    ArrScaffold(
        title = stringResource(R.string.arr_sonarr_naming_title),
        subtitle = serverName,
        onBack = onBack,
        actions = {
            if (content?.dirty == true) {
                IconButton(onClick = onSave, enabled = !state.saving) {
                    Icon(
                        imageVector = Icons.Filled.Save,
                        contentDescription = stringResource(R.string.arr_sonarr_naming_cd_save),
                    )
                }
            }
            IconButton(onClick = onRefresh) {
                Icon(
                    imageVector = Icons.Filled.Refresh,
                    contentDescription = stringResource(R.string.arr_sonarr_naming_cd_refresh),
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
                    val specs = formatSpecs(data, onEdit)
                    if (twoPane) {
                        Row(Modifier.fillMaxSize()) {
                            Column(
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .verticalScroll(rememberScrollState())
                                    .padding(16.dp),
                            ) {
                                NamingToggles(draft = data.draft, onEdit = onEdit)
                                specs.forEach { spec ->
                                    FormatEditor(
                                        title = spec.title,
                                        format = spec.format,
                                        tokens = spec.tokens,
                                        onNewFormat = spec.onNewFormat,
                                        onInsertToken = onInsertToken,
                                    )
                                }
                            }
                            Column(
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .verticalScroll(rememberScrollState())
                                    .padding(16.dp),
                            ) {
                                specs.forEach { spec ->
                                    PreviewCard(
                                        title = spec.previewTitle,
                                        localPreview = spec.localPreview,
                                        serverExample = spec.serverExample,
                                    )
                                }
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
                            NamingToggles(draft = data.draft, onEdit = onEdit)
                            specs.forEach { spec ->
                                FormatEditor(
                                    title = spec.title,
                                    format = spec.format,
                                    tokens = spec.tokens,
                                    onNewFormat = spec.onNewFormat,
                                    onInsertToken = onInsertToken,
                                )
                                PreviewCard(
                                    title = spec.previewTitle,
                                    localPreview = spec.localPreview,
                                    serverExample = spec.serverExample,
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
}

/** The six editable formats with their chip sets and preview sources. */
@Composable
private fun formatSpecs(
    data: SonarrNamingContent,
    onEdit: (transform: (NamingDraft) -> NamingDraft) -> Unit,
): List<FormatSpec> = listOf(
    FormatSpec(
        title = stringResource(R.string.arr_sonarr_naming_standard_format),
        format = data.draft.standardEpisodeFormat,
        tokens = NamingTokens.standardEpisodeTokens,
        previewTitle = stringResource(R.string.arr_sonarr_naming_standard_preview),
        localPreview = data.localStandardPreview,
        serverExample = data.serverExample?.episodeExample,
        onNewFormat = { text -> onEdit { it.copy(standardEpisodeFormat = text) } },
    ),
    FormatSpec(
        title = stringResource(R.string.arr_sonarr_naming_daily_format),
        format = data.draft.dailyEpisodeFormat,
        tokens = NamingTokens.dailyEpisodeTokens,
        previewTitle = stringResource(R.string.arr_sonarr_naming_daily_preview),
        localPreview = data.localDailyPreview,
        serverExample = data.serverExample?.dailyEpisodeExample,
        onNewFormat = { text -> onEdit { it.copy(dailyEpisodeFormat = text) } },
    ),
    FormatSpec(
        title = stringResource(R.string.arr_sonarr_naming_anime_format),
        format = data.draft.animeEpisodeFormat,
        tokens = NamingTokens.animeEpisodeTokens,
        previewTitle = stringResource(R.string.arr_sonarr_naming_anime_preview),
        localPreview = data.localAnimePreview,
        serverExample = data.serverExample?.animeEpisodeExample,
        onNewFormat = { text -> onEdit { it.copy(animeEpisodeFormat = text) } },
    ),
    FormatSpec(
        title = stringResource(R.string.arr_sonarr_naming_series_format),
        format = data.draft.seriesFolderFormat,
        tokens = NamingTokens.seriesFolderTokens,
        previewTitle = stringResource(R.string.arr_sonarr_naming_series_preview),
        localPreview = data.localSeriesFolderPreview,
        serverExample = data.serverExample?.seriesFolderExample,
        onNewFormat = { text -> onEdit { it.copy(seriesFolderFormat = text) } },
    ),
    FormatSpec(
        title = stringResource(R.string.arr_sonarr_naming_season_format),
        format = data.draft.seasonFolderFormat,
        tokens = NamingTokens.seasonFolderTokens,
        previewTitle = stringResource(R.string.arr_sonarr_naming_season_preview),
        localPreview = data.localSeasonFolderPreview,
        serverExample = data.serverExample?.seasonFolderExample,
        onNewFormat = { text -> onEdit { it.copy(seasonFolderFormat = text) } },
    ),
    FormatSpec(
        title = stringResource(R.string.arr_sonarr_naming_specials_format),
        format = data.draft.specialsFolderFormat,
        tokens = NamingTokens.seasonFolderTokens,
        previewTitle = stringResource(R.string.arr_sonarr_naming_specials_preview),
        localPreview = data.localSpecialsFolderPreview,
        serverExample = data.serverExample?.specialsFolderExample,
        onNewFormat = { text -> onEdit { it.copy(specialsFolderFormat = text) } },
    ),
)

@Composable
private fun NamingToggles(
    draft: NamingDraft,
    onEdit: (transform: (NamingDraft) -> NamingDraft) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        LabeledSwitch(
            label = stringResource(R.string.arr_sonarr_naming_rename_episodes),
            supportingText = stringResource(R.string.arr_sonarr_naming_rename_episodes_support),
            checked = draft.renameEpisodes,
            onCheckedChange = { value -> onEdit { it.copy(renameEpisodes = value) } },
        )
        LabeledSwitch(
            label = stringResource(R.string.arr_sonarr_naming_replace_illegal),
            supportingText = stringResource(R.string.arr_sonarr_naming_replace_illegal_support),
            checked = draft.replaceIllegalCharacters,
            onCheckedChange = { value -> onEdit { it.copy(replaceIllegalCharacters = value) } },
        )
        IntDropdown(
            label = stringResource(R.string.arr_sonarr_naming_colon_replacement),
            selected = draft.colonReplacementFormat,
            entries = ColonReplacementChoices,
            onSelect = { choice -> onEdit { it.copy(colonReplacementFormat = choice) } },
        )
        if (draft.colonReplacementFormat == NamingDraft.COLON_CUSTOM) {
            OutlinedTextField(
                value = draft.customColonReplacementFormat,
                onValueChange = { text -> onEdit { it.copy(customColonReplacementFormat = text) } },
                singleLine = true,
                label = { Text(stringResource(R.string.arr_sonarr_naming_custom_colon)) },
                supportingText = { Text(stringResource(R.string.arr_sonarr_naming_custom_colon_support)) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        IntDropdown(
            label = stringResource(R.string.arr_sonarr_naming_multi_episode),
            selected = draft.multiEpisodeStyle,
            entries = MultiEpisodeChoices,
            onSelect = { choice -> onEdit { it.copy(multiEpisodeStyle = choice) } },
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
            label = { Text(stringResource(R.string.arr_sonarr_naming_format_hint)) },
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            text = stringResource(R.string.arr_sonarr_naming_token_hint),
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
                    stringResource(R.string.arr_sonarr_naming_preview_empty)
                },
                style = MaterialTheme.typography.titleMedium,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 4.dp),
            )
            if (serverExample != null) {
                Text(
                    text = stringResource(R.string.arr_sonarr_naming_preview_server_source),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            } else {
                Text(
                    text = stringResource(R.string.arr_sonarr_naming_preview_local_source),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}

/** Exposed dropdown for an integer-coded wire enum. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun IntDropdown(
    label: String,
    selected: Int,
    entries: List<Pair<Int, Int>>,
    onSelect: (Int) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedLabel = stringResource(entries.first { it.first == selected }.second)
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
    ) {
        OutlinedTextField(
            value = selectedLabel,
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
            entries.forEach { (wire, labelRes) ->
                DropdownMenuItem(
                    text = { Text(stringResource(labelRes)) },
                    onClick = {
                        onSelect(wire)
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
        NamingNotice.SAVED -> stringResource(R.string.arr_sonarr_naming_notice_saved)
        NamingNotice.SAVED_QUEUED -> stringResource(R.string.arr_sonarr_naming_notice_saved_queued)
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

/** Colon replacement choices: wire value to label resource. */
private val ColonReplacementChoices = listOf(
    NamingDraft.COLON_DELETE to R.string.arr_sonarr_naming_colon_delete,
    NamingDraft.COLON_DASH to R.string.arr_sonarr_naming_colon_dash,
    NamingDraft.COLON_SPACE_DASH to R.string.arr_sonarr_naming_colon_space_dash,
    NamingDraft.COLON_SPACE_DASH_SPACE to R.string.arr_sonarr_naming_colon_space_dash_space,
    NamingDraft.COLON_SMART to R.string.arr_sonarr_naming_colon_smart,
    NamingDraft.COLON_CUSTOM to R.string.arr_sonarr_naming_colon_custom,
)

/** Multi-episode style choices: wire value to label resource. */
private val MultiEpisodeChoices = listOf(
    NamingDraft.MULTI_EXTEND to R.string.arr_sonarr_naming_multi_extend,
    NamingDraft.MULTI_DUPLICATE to R.string.arr_sonarr_naming_multi_duplicate,
    NamingDraft.MULTI_REPEAT to R.string.arr_sonarr_naming_multi_repeat,
    NamingDraft.MULTI_SCENE to R.string.arr_sonarr_naming_multi_scene,
    NamingDraft.MULTI_RANGE to R.string.arr_sonarr_naming_multi_range,
    NamingDraft.MULTI_PREFIXED_RANGE to R.string.arr_sonarr_naming_multi_prefixed_range,
)

/** Material window size class breakpoint for expanded tablet widths. */
private val ExpandedPaneWidth = 840.dp
