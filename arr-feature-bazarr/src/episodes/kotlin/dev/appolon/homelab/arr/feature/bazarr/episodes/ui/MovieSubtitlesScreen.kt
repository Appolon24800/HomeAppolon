package dev.appolon.homelab.arr.feature.bazarr.episodes.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
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
import dev.appolon.homelab.arr.feature.bazarr.episodes.model.BazarrSubtitleTrack
import dev.appolon.homelab.arr.feature.bazarr.episodes.vm.MovieSubtitlesUiState
import dev.appolon.homelab.arr.feature.bazarr.episodes.vm.UploadState
import dev.appolon.homelab.arr.ui.lce.ArrLceBox
import dev.appolon.homelab.arr.ui.lce.ArrPullToRefreshBox
import dev.appolon.homelab.arr.ui.lce.LceState
import dev.appolon.homelab.arr.ui.shell.ArrScaffold

/**
 * Movie subtitle screen: the same per-language breakdown as the episode
 * list, flattened onto one media card. Attached tracks carry language,
 * hearing impaired flag, and score with view and delete actions; missing
 * languages each offer a download; manual search and upload sit in the app
 * bar. Centers at a readable width on tablets.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun MovieSubtitlesScreen(
    state: MovieSubtitlesUiState,
    lce: LceState<MovieSubtitlesUiState>,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onDownload: (dev.appolon.homelab.arr.feature.bazarr.episodes.model.BazarrLanguageRef) -> Unit,
    onDelete: (BazarrSubtitleTrack) -> Unit,
    onOpenManualSearch: () -> Unit,
    onDownloadManual: (dev.appolon.homelab.arr.feature.bazarr.episodes.model.BazarrProviderResult) -> Unit,
    onRetryManualSearch: () -> Unit,
    onCloseManualSearch: () -> Unit,
    onOpenViewer: (BazarrSubtitleTrack) -> Unit,
    onCloseViewer: () -> Unit,
    onOpenUpload: () -> Unit,
    onUpdateUpload: (UploadState) -> Unit,
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
                    (state.upload ?: UploadState())
                        .copy(fileName = uri.lastPathSegment?.substringAfterLast('/') ?: "subtitle.srt"),
                )
            }
        }
    }

    ArrScaffold(
        title = state.title.ifBlank { stringResource(R.string.arr_bazarr_movie_title) },
        subtitle = state.year?.let { it.toString() },
        onBack = onBack,
        actions = {
            if (state.queued) QueuedBadge(modifier = Modifier.padding(end = 8.dp))
            if (state.busy) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                Spacer(Modifier.size(8.dp))
            }
            val uploadCd = stringResource(R.string.arr_bazarr_cd_upload)
            IconButton(
                onClick = onOpenUpload,
                modifier = Modifier.semantics { contentDescription = uploadCd },
            ) {
                Icon(Icons.Filled.UploadFile, contentDescription = uploadCd)
            }
            val searchCd = stringResource(R.string.arr_bazarr_cd_search)
            IconButton(
                onClick = onOpenManualSearch,
                modifier = Modifier.semantics { contentDescription = searchCd },
            ) {
                Icon(Icons.Filled.Search, contentDescription = searchCd)
            }
            val refreshCd = stringResource(R.string.arr_bazarr_cd_refresh)
            IconButton(
                onClick = onRefresh,
                modifier = Modifier.semantics { contentDescription = refreshCd },
            ) {
                Icon(Icons.Filled.Refresh, contentDescription = refreshCd)
            }
        },
        modifier = modifier,
    ) { padding ->
        BoxWithConstraints(modifier = Modifier.fillMaxSize().padding(padding)) {
            val contentWidth = if (maxWidth >= 840.dp) 800.dp else maxWidth
            ArrLceBox(
                state = lce,
                onRetry = onRefresh,
                modifier = Modifier.fillMaxSize(),
            ) { _ ->
                ArrPullToRefreshBox(
                    isRefreshing = state.refreshing,
                    onRefresh = onRefresh,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                    ) {
                        Card(modifier = Modifier.fillMaxWidth().widthIn(max = contentWidth)) {
                            Column(
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.padding(16.dp),
                            ) {
                                Text(
                                    text = stringResource(R.string.arr_bazarr_subtitles_section),
                                    style = MaterialTheme.typography.titleSmall,
                                )
                                if (state.subtitles.isEmpty()) {
                                    Text(
                                        text = stringResource(R.string.arr_bazarr_none_present),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                state.subtitles.forEach { track ->
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    ) {
                                        Column(Modifier.weight(1f)) {
                                            SubtitleTrackLine(track)
                                            if (track.provider.isNotBlank()) {
                                                Text(
                                                    text = stringResource(
                                                        R.string.arr_bazarr_provider_name,
                                                        track.provider,
                                                    ),
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                )
                                            }
                                        }
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
                            }
                        }
                        Card(modifier = Modifier.fillMaxWidth().widthIn(max = contentWidth)) {
                            Column(
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.padding(16.dp),
                            ) {
                                Text(
                                    text = stringResource(R.string.arr_bazarr_missing_section),
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.error,
                                )
                                if (state.missingLanguages.isEmpty()) {
                                    Text(
                                        text = stringResource(R.string.arr_bazarr_nothing_missing),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                state.missingLanguages.forEach { language ->
                                    MissingLanguageRow(
                                        language = language,
                                        busy = state.busy,
                                        onDownload = { onDownload(language) },
                                    )
                                }
                            }
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
