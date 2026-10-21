package dev.appolon.homelab.arr.feature.bazarr.episodes.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.appolon.homelab.arr.feature.bazarr.R
import dev.appolon.homelab.arr.feature.bazarr.episodes.model.BazarrEpisodeSubtitles
import dev.appolon.homelab.arr.feature.bazarr.episodes.model.BazarrSubtitleTrack
import dev.appolon.homelab.arr.feature.bazarr.episodes.vm.EpisodeSubtitlesUiState
import dev.appolon.homelab.arr.feature.bazarr.episodes.vm.SubtitlesMessage
import dev.appolon.homelab.arr.ui.components.FilterChipRow
import dev.appolon.homelab.arr.ui.lce.ArrLceBox
import dev.appolon.homelab.arr.ui.lce.ArrPullToRefreshBox
import dev.appolon.homelab.arr.ui.lce.LceState
import dev.appolon.homelab.arr.ui.shell.ArrScaffold

/**
 * Per-series episode subtitle list. Each episode row shows its SxxEyy code,
 * attached subtitles with language, hearing impaired flag, and score, plus
 * every missing language with a download action, manual search, upload, and
 * delete. Language filter chips come from the languages endpoint. The list
 * centers at a readable width on tablets instead of stretching full bleed.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun EpisodeSubtitlesScreen(
    state: EpisodeSubtitlesUiState,
    lce: LceState<EpisodeSubtitlesUiState>,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onLanguageFilter: (String?) -> Unit,
    onToggleMissingOnly: () -> Unit,
    onDownload: (BazarrEpisodeSubtitles, dev.appolon.homelab.arr.feature.bazarr.episodes.model.BazarrLanguageRef) -> Unit,
    onDelete: (BazarrEpisodeSubtitles, BazarrSubtitleTrack) -> Unit,
    onOpenManualSearch: (BazarrEpisodeSubtitles) -> Unit,
    onDownloadManual: (dev.appolon.homelab.arr.feature.bazarr.episodes.model.BazarrProviderResult) -> Unit,
    onRetryManualSearch: () -> Unit,
    onCloseManualSearch: () -> Unit,
    onOpenViewer: (BazarrSubtitleTrack, String) -> Unit,
    onCloseViewer: () -> Unit,
    onOpenUpload: (BazarrEpisodeSubtitles) -> Unit,
    onUpdateUpload: (dev.appolon.homelab.arr.feature.bazarr.episodes.vm.UploadState) -> Unit,
    onSubmitUpload: (ByteArray) -> Unit,
    onCloseUpload: () -> Unit,
    onConsumeMessage: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val snackbar = remember { SnackbarHostState() }
    val messageText = state.message?.let { subtitlesMessageText(it) }
    LaunchedEffect(messageText) {
        messageText?.let {
            snackbar.showSnackbar(it)
            onConsumeMessage()
        }
    }
    val context = LocalContext.current
    var pickedBytes by remember { mutableStateOf<ByteArray?>(null) }
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            }.getOrNull()?.let { bytes ->
                pickedBytes = bytes
                onUpdateUpload(
                    (state.upload ?: dev.appolon.homelab.arr.feature.bazarr.episodes.vm.UploadState())
                        .copy(fileName = uri.lastPathSegment?.substringAfterLast('/') ?: "subtitle.srt"),
                )
            }
        }
    }

    ArrScaffold(
        title = state.seriesTitle.ifBlank {
            stringResource(R.string.arr_bazarr_episodes_title)
        },
        subtitle = stringResource(R.string.arr_bazarr_episodes_subtitle),
        onBack = onBack,
        actions = {
            val cd = stringResource(R.string.arr_bazarr_cd_refresh)
            IconButton(
                onClick = onRefresh,
                modifier = Modifier.semantics { contentDescription = cd },
            ) {
                Icon(Icons.Filled.Refresh, contentDescription = cd)
            }
        },
        filterBar = {
            LanguageFilterChips(
                languages = state.languages,
                selected = state.selectedLanguage,
                onSelect = onLanguageFilter,
            )
            FilterChip(
                selected = state.missingOnly,
                onClick = onToggleMissingOnly,
                label = { Text(stringResource(R.string.arr_bazarr_missing_only)) },
                modifier = Modifier.padding(start = 8.dp, bottom = 4.dp),
            )
        },
        modifier = modifier,
    ) { padding ->
        BoxWithConstraints(modifier = Modifier.fillMaxSize().padding(padding)) {
            val contentWidth = if (maxWidth >= 840.dp) 800.dp else maxWidth
            ArrLceBox(
                state = lce,
                onRetry = onRefresh,
                emptyTitle = stringResource(R.string.arr_bazarr_episodes_empty_title),
                emptyMessage = stringResource(R.string.arr_bazarr_episodes_empty_message),
                modifier = Modifier.fillMaxSize(),
            ) { _ ->
                ArrPullToRefreshBox(
                    isRefreshing = state.refreshing,
                    onRefresh = onRefresh,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    LazyColumn(
                        contentPadding = PaddingValues(
                            horizontal = 16.dp,
                            vertical = 8.dp,
                        ),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .fillMaxSize()
                            // Episode headers and their subtitle details form
                            // one traversal group per logical list.
                            .focusGroup(),
                    ) {
                        items(state.presented, key = { it.episodeId }) { episode ->
                            EpisodeRow(
                                episode = episode,
                                queued = episode.episodeId in state.queuedEpisodeIds,
                                busy = episode.episodeId in state.busyEpisodeIds,
                                onDownload = { language -> onDownload(episode, language) },
                                onDelete = { track -> onDelete(episode, track) },
                                onOpenManualSearch = { onOpenManualSearch(episode) },
                                onOpenViewer = { track ->
                                    onOpenViewer(track, "${episode.code} ${episode.title}".trim())
                                },
                                onOpenUpload = { onOpenUpload(episode) },
                                modifier = Modifier.fillMaxWidth().widthIn(max = contentWidth),
                            )
                        }
                    }
                }
            }

            state.manualSearch?.let { search ->
                ManualSearchSheet(
                    state = search,
                    onDownload = onDownloadManual,
                    onRetry = onRetryManualSearch,
                    onDismiss = onCloseManualSearch,
                )
            }
            state.viewer?.let { viewer ->
                SubtitleViewerDialog(state = viewer, onDismiss = onCloseViewer)
            }
            state.upload?.let { upload ->
                UploadSheet(
                    state = upload,
                    languages = state.languages,
                    onUpdate = onUpdateUpload,
                    onPickFile = { filePicker.launch(arrayOf("*/*")) },
                    onSubmit = { pickedBytes?.let(onSubmitUpload) },
                    onDismiss = onCloseUpload,
                )
            }
            SnackbarHost(
                hostState = snackbar,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }
}

/** One episode card: header actions, subtitle tracks, missing languages. */
@Composable
private fun EpisodeRow(
    episode: BazarrEpisodeSubtitles,
    queued: Boolean,
    busy: Boolean,
    onDownload: (dev.appolon.homelab.arr.feature.bazarr.episodes.model.BazarrLanguageRef) -> Unit,
    onDelete: (BazarrSubtitleTrack) -> Unit,
    onOpenManualSearch: () -> Unit,
    onOpenViewer: (BazarrSubtitleTrack) -> Unit,
    onOpenUpload: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier) {
        Column(modifier = Modifier.padding(vertical = 4.dp)) {
            ListItem(
                headlineContent = {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(episode.code, style = MaterialTheme.typography.titleSmall)
                        Text(
                            text = episode.title,
                            style = MaterialTheme.typography.titleSmall,
                            maxLines = 1,
                        )
                    }
                },
                supportingContent = if (episode.subtitles.isEmpty()) {
                    {
                        Text(
                            text = stringResource(R.string.arr_bazarr_none_present),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    null
                },
                trailingContent = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (queued) QueuedBadge()
                        if (busy) {
                            val busyCd = stringResource(R.string.arr_bazarr_cd_loading)
                            CircularProgressIndicator(
                                modifier = Modifier
                                    .size(24.dp)
                                    .semantics { contentDescription = busyCd },
                                strokeWidth = 2.dp,
                            )
                        }
                        val uploadCd = stringResource(R.string.arr_bazarr_cd_upload)
                        IconButton(
                            onClick = onOpenUpload,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .semantics { contentDescription = uploadCd },
                        ) {
                            Icon(Icons.Filled.UploadFile, contentDescription = uploadCd)
                        }
                        val searchCd = stringResource(R.string.arr_bazarr_cd_search)
                        IconButton(
                            onClick = onOpenManualSearch,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .semantics { contentDescription = searchCd },
                        ) {
                            Icon(Icons.Filled.Search, contentDescription = searchCd)
                        }
                    }
                },
            )
            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            ) {
                episode.subtitles.forEach { track ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Box(Modifier.weight(1f)) { SubtitleTrackLine(track) }
                        val viewCd = stringResource(R.string.arr_bazarr_cd_view)
                        IconButton(
                            onClick = { onOpenViewer(track) },
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .semantics { contentDescription = viewCd },
                        ) {
                            Icon(Icons.Filled.Description, contentDescription = viewCd)
                        }
                        val deleteCd = stringResource(R.string.arr_bazarr_cd_delete)
                        IconButton(
                            onClick = { onDelete(track) },
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .semantics { contentDescription = deleteCd },
                        ) {
                            Icon(Icons.Filled.Delete, contentDescription = deleteCd)
                        }
                    }
                }
                episode.missingLanguages.forEach { language ->
                    MissingLanguageRow(
                        language = language,
                        busy = false,
                        onDownload = { onDownload(language) },
                    )
                }
            }
            Spacer(Modifier.size(4.dp))
        }
    }
}

/** Language chips driven by the Bazarr languages endpoint. */
@Composable
internal fun LanguageFilterChips(
    languages: List<dev.appolon.homelab.arr.feature.bazarr.episodes.model.BazarrLanguageOption>,
    selected: String?,
    onSelect: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    FilterChipRow(
        filters = languages,
        selected = { it.code2 == selected },
        label = { it.label },
        onSelect = { onSelect(it.code2) },
        modifier = modifier,
    )
}

/** Snackbar text for every write outcome the screens report. */
@Composable
internal fun subtitlesMessageText(message: SubtitlesMessage): String = when (message) {
    SubtitlesMessage.DownloadSynced -> stringResource(R.string.arr_bazarr_download_synced)
    SubtitlesMessage.DownloadQueued -> stringResource(R.string.arr_bazarr_download_queued)
    SubtitlesMessage.DeleteSynced -> stringResource(R.string.arr_bazarr_delete_synced)
    SubtitlesMessage.DeleteQueued -> stringResource(R.string.arr_bazarr_delete_queued)
    SubtitlesMessage.UploadSynced -> stringResource(R.string.arr_bazarr_upload_synced)
    is SubtitlesMessage.WriteFailed -> dev.appolon.homelab.arr.ui.lce.arrErrorMessage(message.error)
}
