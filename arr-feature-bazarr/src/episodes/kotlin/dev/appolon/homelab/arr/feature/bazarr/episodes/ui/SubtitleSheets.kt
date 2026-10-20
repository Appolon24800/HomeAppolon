package dev.appolon.homelab.arr.feature.bazarr.episodes.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Hearing
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import dev.appolon.homelab.arr.feature.bazarr.R
import dev.appolon.homelab.arr.feature.bazarr.episodes.model.BazarrLanguageOption
import dev.appolon.homelab.arr.feature.bazarr.episodes.model.BazarrLanguageRef
import dev.appolon.homelab.arr.feature.bazarr.episodes.model.BazarrProviderResult
import dev.appolon.homelab.arr.feature.bazarr.episodes.model.BazarrSubtitleTrack
import dev.appolon.homelab.arr.feature.bazarr.episodes.vm.ManualSearchState
import dev.appolon.homelab.arr.feature.bazarr.episodes.vm.UploadState
import dev.appolon.homelab.arr.feature.bazarr.episodes.vm.ViewerState
import dev.appolon.homelab.arr.ui.lce.ArrLceBox
import dev.appolon.homelab.arr.ui.lce.LceState

/** Display label for a language reference: name when known, else its code. */
internal fun languageLabel(code2: String, name: String): String =
    if (name.isBlank()) code2 else "$name ($code2)"

@Composable
internal fun HearingImpairedBadge(modifier: Modifier = Modifier) {
    val label = stringResource(R.string.arr_bazarr_hearing_impaired)
    AssistChip(
        onClick = {},
        enabled = false,
        label = { Text(label) },
        leadingIcon = {
            Icon(Icons.Filled.Hearing, contentDescription = null, modifier = Modifier.size(16.dp))
        },
        modifier = modifier.semantics { contentDescription = label },
    )
}

@Composable
internal fun ForcedBadge(modifier: Modifier = Modifier) {
    val label = stringResource(R.string.arr_bazarr_forced)
    AssistChip(
        onClick = {},
        enabled = false,
        label = { Text(label) },
        leadingIcon = {
            Icon(Icons.AutoMirrored.Filled.Label, contentDescription = null, modifier = Modifier.size(16.dp))
        },
        modifier = modifier.semantics { contentDescription = label },
    )
}

@Composable
internal fun QueuedBadge(modifier: Modifier = Modifier) {
    val label = stringResource(R.string.arr_bazarr_queued)
    Surface(
        color = MaterialTheme.colorScheme.tertiaryContainer,
        shape = MaterialTheme.shapes.small,
        modifier = modifier.semantics { contentDescription = label },
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onTertiaryContainer,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}

/** "Language · HI · forced · score" summary line for one attached subtitle. */
@Composable
internal fun SubtitleTrackLine(track: BazarrSubtitleTrack, modifier: Modifier = Modifier) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier,
    ) {
        Text(
            text = languageLabel(track.languageCode2, track.languageName),
            style = MaterialTheme.typography.bodyMedium,
        )
        if (track.hearingImpaired) HearingImpairedBadge()
        if (track.forced) ForcedBadge()
        track.score?.let { score ->
            Text(
                text = stringResource(R.string.arr_bazarr_score, score.toInt()),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Missing-language chip row with one download action per language variant. */
@Composable
internal fun MissingLanguageRow(
    language: BazarrLanguageRef,
    busy: Boolean,
    onDownload: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier,
    ) {
        Text(
            text = stringResource(
                R.string.arr_bazarr_missing_language,
                languageLabel(language.code2, language.name),
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
        )
        if (language.hearingImpaired) HearingImpairedBadge()
        if (language.forced) ForcedBadge()
        Spacer(Modifier.weight(1f))
        if (busy) {
            val busyCd = stringResource(R.string.arr_bazarr_cd_loading)
            CircularProgressIndicator(
                modifier = Modifier
                    .size(24.dp)
                    .semantics { contentDescription = busyCd },
                strokeWidth = 2.dp,
            )
        } else {
            val cd = stringResource(R.string.arr_bazarr_cd_download)
            IconButton(onClick = onDownload, modifier = Modifier.semantics { contentDescription = cd }) {
                Icon(Icons.Filled.Download, contentDescription = cd)
            }
        }
    }
}

/**
 * Manual search bottom sheet: provider hits with per-provider score and a
 * download action per row. Shared by the episode and movie screens.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ManualSearchSheet(
    state: ManualSearchState,
    onDownload: (BazarrProviderResult) -> Unit,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        modifier = modifier,
    ) {
        Text(
            text = stringResource(R.string.arr_bazarr_manual_search_title, state.mediaLabel),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .semantics { heading() },
        )
        val lce = when {
            state.loading -> LceState.Loading
            state.error != null -> LceState.Error(state.error)
            state.results.isEmpty() -> LceState.Empty
            else -> LceState.Content(state.results)
        }
        ArrLceBox(
            state = lce,
            onRetry = onRetry,
            emptyTitle = stringResource(R.string.arr_bazarr_manual_search_empty_title),
            emptyMessage = stringResource(R.string.arr_bazarr_manual_search_empty_message),
            modifier = Modifier
                .fillMaxWidth()
                .height(420.dp),
        ) { results ->
            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.verticalScroll(rememberScrollState()),
            ) {
                results.forEach { result ->
                    val key = state.resultKey(result)
                    ListItem(
                        headlineContent = {
                            Text(
                                text = result.releaseInfo.ifBlank {
                                    stringResource(R.string.arr_bazarr_provider_result, result.provider)
                                },
                                maxLines = 2,
                            )
                        },
                        supportingContent = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.horizontalScroll(rememberScrollState()),
                            ) {
                                Text(
                                    text = languageLabel(result.languageCode2, result.languageName),
                                    style = MaterialTheme.typography.bodySmall,
                                )
                                Text(
                                    text = stringResource(R.string.arr_bazarr_provider_name, result.provider),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                result.score?.let {
                                    Text(
                                        text = stringResource(R.string.arr_bazarr_score, it.toInt()),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                if (result.hearingImpaired) HearingImpairedBadge()
                                if (result.forced) ForcedBadge()
                            }
                        },
                        trailingContent = {
                            if (state.downloadingKey == key) {
                                val busyCd = stringResource(R.string.arr_bazarr_cd_loading)
                                CircularProgressIndicator(
                                    modifier = Modifier
                                        .size(24.dp)
                                        .semantics { contentDescription = busyCd },
                                    strokeWidth = 2.dp,
                                )
                            } else {
                                val cd = stringResource(R.string.arr_bazarr_cd_download)
                                IconButton(
                                    onClick = { onDownload(result) },
                                    modifier = Modifier.semantics { contentDescription = cd },
                                ) {
                                    Icon(Icons.Filled.Download, contentDescription = cd)
                                }
                            }
                        },
                    )
                }
            }
        }
    }
}

/**
 * Upload sheet: language picker, forced and hearing impaired flags, and the
 * picked file name. File bytes arrive from the screen's picker callback.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun UploadSheet(
    state: UploadState,
    languages: List<BazarrLanguageOption>,
    onUpdate: (UploadState) -> Unit,
    onPickFile: () -> Unit,
    onSubmit: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        modifier = modifier,
    ) {
        var menuOpen by remember { mutableStateOf(false) }
        Text(
            text = stringResource(R.string.arr_bazarr_upload_title),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .semantics { heading() },
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
        ) {
            OutlinedButton(onClick = { menuOpen = true }) {
                Text(
                    if (state.languageCode2.isBlank()) {
                        stringResource(R.string.arr_bazarr_upload_pick_language)
                    } else {
                        languages.firstOrNull { it.code2 == state.languageCode2 }?.label
                            ?: state.languageCode2
                    },
                )
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                languages.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option.label) },
                        onClick = {
                            menuOpen = false
                            onUpdate(state.copy(languageCode2 = option.code2))
                        },
                    )
                }
            }
        }
        // Toggleable rows pair each label with its checkbox and keep the
        // full row as the 48dp touch target.
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .toggleable(
                    value = state.forced,
                    onValueChange = { onUpdate(state.copy(forced = it)) },
                    role = Role.Checkbox,
                )
                .padding(horizontal = 16.dp),
        ) {
            Checkbox(checked = state.forced, onCheckedChange = null)
            Text(stringResource(R.string.arr_bazarr_forced))
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .toggleable(
                    value = state.hearingImpaired,
                    onValueChange = { onUpdate(state.copy(hearingImpaired = it)) },
                    role = Role.Checkbox,
                )
                .padding(horizontal = 16.dp),
        ) {
            Checkbox(checked = state.hearingImpaired, onCheckedChange = null)
            Text(stringResource(R.string.arr_bazarr_hearing_impaired))
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            OutlinedButton(onClick = onPickFile, enabled = !state.submitting) {
                Text(
                    if (state.fileName.isBlank()) {
                        stringResource(R.string.arr_bazarr_upload_choose_file)
                    } else {
                        state.fileName
                    },
                )
            }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onSubmit, enabled = state.ready) {
                Text(stringResource(R.string.arr_bazarr_upload_submit))
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

/** Full-width dialog rendering the text of one subtitle file. */
@Composable
internal fun SubtitleViewerDialog(
    state: ViewerState,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.arr_bazarr_close)) }
        },
        title = {
            Text(state.path.substringAfterLast('/').ifBlank {
                stringResource(R.string.arr_bazarr_viewer_title)
            })
        },
        text = {
            when {
                state.loading -> {
                    val busyCd = stringResource(R.string.arr_bazarr_cd_loading)
                    CircularProgressIndicator(
                        modifier = Modifier
                            .size(24.dp)
                            .semantics { contentDescription = busyCd },
                    )
                }
                state.error != null -> Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(Icons.Filled.CloudOff, contentDescription = null)
                    Text(
                        text = stringResource(R.string.arr_bazarr_viewer_failed),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }

                state.content.isBlank() -> Text(stringResource(R.string.arr_bazarr_viewer_empty))
                else -> Surface(
                    tonalElevation = 1.dp,
                    shape = MaterialTheme.shapes.small,
                ) {
                    Text(
                        text = state.content,
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier
                            .height(320.dp)
                            .verticalScroll(rememberScrollState())
                            .padding(8.dp),
                    )
                }
            }
        },
        modifier = modifier,
    )
}
