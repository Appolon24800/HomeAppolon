package dev.appolon.homelab.arr.feature.lidarr.settings.quality.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.appolon.homelab.arr.api.lidarr.model.WriteAudioTagsType
import dev.appolon.homelab.arr.core.ServerId
import dev.appolon.homelab.arr.feature.lidarr.R
import dev.appolon.homelab.arr.feature.lidarr.settings.quality.LidarrQualityClients
import dev.appolon.homelab.arr.feature.lidarr.settings.quality.vm.LidarrMetadataProviderContent
import dev.appolon.homelab.arr.feature.lidarr.settings.quality.vm.LidarrMetadataProviderViewModel
import dev.appolon.homelab.arr.ui.components.LabeledSwitch
import dev.appolon.homelab.arr.ui.lce.ArrLceBox
import dev.appolon.homelab.arr.ui.lce.ArrPullToRefreshBox
import dev.appolon.homelab.arr.ui.lce.LceState
import dev.appolon.homelab.arr.ui.shell.ArrScaffold

/** Destination for the metadata provider config route. */
@Composable
fun LidarrMetadataProviderDestination(
    serverId: ServerId,
    clients: LidarrQualityClients,
    serverName: String? = null,
    onBack: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val repository = remember(serverId, clients) { qualityRepository(serverId, clients) }
    val vm: LidarrMetadataProviderViewModel =
        viewModel(key = "lidarr-metadata-provider-" + serverId.value) {
            LidarrMetadataProviderViewModel(repository)
        }
    val state by vm.state.collectAsState()
    val lce by vm.lce.collectAsState()

    LidarrMetadataProviderScreen(
        state = state,
        lce = lce,
        serverName = serverName,
        onBack = onBack,
        onRefresh = vm::refresh,
        onSetMetadataSource = vm::setMetadataSource,
        onSetWriteAudioTags = vm::setWriteAudioTags,
        onSetScrubAudioTags = vm::setScrubAudioTags,
        onSetEmbedCoverArt = vm::setEmbedCoverArt,
        onSave = vm::save,
        onConsumeSavedNotice = vm::consumeSavedNotice,
        onConsumeWriteError = vm::consumeWriteError,
        modifier = modifier,
    )
}

/**
 * The metadata provider config, the single-row settings screen Lidarr has
 * and Sonarr does not: where metadata comes from, how far tag writing goes,
 * and the scrub and cover art switches that only apply while tags are
 * written.
 */
@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun LidarrMetadataProviderScreen(
    state: LidarrMetadataProviderViewModel.UiState,
    lce: LceState<LidarrMetadataProviderContent>,
    serverName: String?,
    onBack: (() -> Unit)?,
    onRefresh: () -> Unit,
    onSetMetadataSource: (String) -> Unit,
    onSetWriteAudioTags: (WriteAudioTagsType) -> Unit,
    onSetScrubAudioTags: (Boolean) -> Unit,
    onSetEmbedCoverArt: (Boolean) -> Unit,
    onSave: () -> Unit,
    onConsumeSavedNotice: () -> Unit,
    onConsumeWriteError: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val content = (lce as? LceState.Content<LidarrMetadataProviderContent>)?.data

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
        title = stringResource(R.string.arr_lidarr_quality_metadata_provider_title),
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
                            value = data.draft.metadataSource,
                            label = stringResource(R.string.arr_lidarr_quality_metadata_provider_source),
                            supporting = stringResource(
                                R.string.arr_lidarr_quality_metadata_provider_source_support,
                            ),
                            onValue = onSetMetadataSource,
                        )
                        // labelOf runs in a non-composable lambda, so resolve
                        // the labels here where composition is available.
                        val tagLabels = WriteAudioTagsType.entries.associateWith { mode ->
                            writeTagsLabel(mode)
                        }
                        QualityDropdown(
                            label = stringResource(
                                R.string.arr_lidarr_quality_metadata_provider_write_tags,
                            ),
                            selected = data.draft.writeAudioTags,
                            entries = WriteAudioTagsType.entries.toList(),
                            labelOf = { mode -> tagLabels.getValue(mode) },
                            onSelect = onSetWriteAudioTags,
                        )
                        if (data.draft.writeAudioTags != WriteAudioTagsType.NO) {
                            LabeledSwitch(
                                label = stringResource(
                                    R.string.arr_lidarr_quality_metadata_provider_scrub,
                                ),
                                supportingText = stringResource(
                                    R.string.arr_lidarr_quality_metadata_provider_scrub_support,
                                ),
                                checked = data.draft.scrubAudioTags,
                                onCheckedChange = onSetScrubAudioTags,
                            )
                            LabeledSwitch(
                                label = stringResource(
                                    R.string.arr_lidarr_quality_metadata_provider_cover_art,
                                ),
                                supportingText = stringResource(
                                    R.string.arr_lidarr_quality_metadata_provider_cover_art_support,
                                ),
                                checked = data.draft.embedCoverArt,
                                onCheckedChange = onSetEmbedCoverArt,
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
private fun writeTagsLabel(mode: WriteAudioTagsType): String = when (mode) {
    WriteAudioTagsType.NO -> stringResource(R.string.arr_lidarr_quality_metadata_provider_write_tags_no)
    WriteAudioTagsType.NEW_FILES ->
        stringResource(R.string.arr_lidarr_quality_metadata_provider_write_tags_new)
    WriteAudioTagsType.ALL_FILES ->
        stringResource(R.string.arr_lidarr_quality_metadata_provider_write_tags_all)
    WriteAudioTagsType.SYNC ->
        stringResource(R.string.arr_lidarr_quality_metadata_provider_write_tags_sync)
}
