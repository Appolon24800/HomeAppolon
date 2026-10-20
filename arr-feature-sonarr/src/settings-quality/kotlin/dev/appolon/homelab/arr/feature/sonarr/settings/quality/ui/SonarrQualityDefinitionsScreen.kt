package dev.appolon.homelab.arr.feature.sonarr.settings.quality.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.appolon.homelab.arr.core.ServerId
import dev.appolon.homelab.arr.feature.sonarr.R
import dev.appolon.homelab.arr.feature.sonarr.settings.quality.SonarrQualityClients
import dev.appolon.homelab.arr.feature.sonarr.settings.quality.data.SonarrQualityRepository
import dev.appolon.homelab.arr.feature.sonarr.settings.quality.vm.QualityDefinitionDraft
import dev.appolon.homelab.arr.feature.sonarr.settings.quality.vm.QualityDefinitionFieldError
import dev.appolon.homelab.arr.feature.sonarr.settings.quality.vm.QualityDefinitionsNotice
import dev.appolon.homelab.arr.feature.sonarr.settings.quality.vm.SonarrQualityDefinitionsContent
import dev.appolon.homelab.arr.feature.sonarr.settings.quality.vm.SonarrQualityDefinitionsViewModel
import dev.appolon.homelab.arr.ui.lce.ArrLceBox
import dev.appolon.homelab.arr.ui.lce.ArrPullToRefreshBox
import dev.appolon.homelab.arr.ui.lce.LceState
import dev.appolon.homelab.arr.ui.shell.ArrScaffold
import dev.appolon.homelab.arr.ui.R as ArrUiR

/**
 * Destination for the quality definitions route: wires the repository and
 * ViewModel for this route's server and renders the editor.
 */
@Composable
fun SonarrQualityDefinitionsDestination(
    serverId: ServerId,
    clients: SonarrQualityClients,
    serverName: String? = null,
    onBack: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val repository = remember(serverId, clients) {
        qualityRepository(serverId, clients)
    }
    val vm: SonarrQualityDefinitionsViewModel =
        viewModel(key = "sonarr-quality-defs-" + serverId.value) {
            SonarrQualityDefinitionsViewModel(repository)
        }
    val state by vm.state.collectAsState()
    val lce by vm.lce.collectAsState()

    SonarrQualityDefinitionsScreen(
        state = state,
        lce = lce,
        serverName = serverName,
        onBack = onBack,
        onRefresh = vm::refresh,
        onEditRow = vm::editRow,
        onSave = vm::save,
        onConsumeNotice = vm::consumeNotice,
        onConsumeWriteError = vm::consumeWriteError,
        modifier = modifier,
    )
}

/**
 * The quality definitions editor. One card per quality: editable title,
 * weight slider bounded by the server limits, and the min / max / preferred
 * megabits-per-minute constraints with inline validation. Save writes the
 * whole table at once through the bulk endpoint.
 */
@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun SonarrQualityDefinitionsScreen(
    state: SonarrQualityDefinitionsViewModel.UiState,
    lce: LceState<SonarrQualityDefinitionsContent>,
    serverName: String?,
    onBack: (() -> Unit)?,
    onRefresh: () -> Unit,
    onEditRow: (Int, transform: (QualityDefinitionDraft) -> QualityDefinitionDraft) -> Unit,
    onSave: () -> Unit,
    onConsumeNotice: () -> Unit,
    onConsumeWriteError: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val content = (lce as? LceState.Content<SonarrQualityDefinitionsContent>)?.data
    val refreshing = content != null && (lce as LceState.Content<SonarrQualityDefinitionsContent>).refreshing

    val savedMessage = when (state.notice) {
        QualityDefinitionsNotice.SAVED -> stringResource(ArrUiR.string.arr_common_system_notice_saved)
        QualityDefinitionsNotice.SAVED_QUEUED ->
            stringResource(ArrUiR.string.arr_common_system_notice_saved_queued)
        null -> null
    }
    QualityNoticeHost(
        noticeMessage = savedMessage,
        writeError = state.writeError,
        snackbarHostState = snackbarHostState,
        onConsumeNotice = onConsumeNotice,
        onConsumeWriteError = onConsumeWriteError,
    )

    ArrScaffold(
        title = stringResource(ArrUiR.string.arr_common_quality_definitions_title),
        subtitle = serverName,
        onBack = onBack,
        actions = {
            if (content?.dirty == true) {
                IconButton(onClick = onSave, enabled = !state.saving) {
                    Icon(
                        imageVector = Icons.Filled.Save,
                        contentDescription = stringResource(ArrUiR.string.arr_common_save_changes),
                    )
                }
            }
            IconButton(onClick = onRefresh) {
                Icon(
                    imageVector = Icons.Filled.Refresh,
                    contentDescription = stringResource(ArrUiR.string.arr_common_quality_cd_refresh),
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
                ArrLceBox(
                    state = lce,
                    onRetry = onRefresh,
                    modifier = Modifier.fillMaxSize(),
                ) { data ->
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .fillMaxSize()
                            .widthIn(max = SectionMaxWidth)
                            .verticalScroll(rememberScrollState())
                            .padding(16.dp),
                    ) {
                        if (data.pendingActions > 0) QualityPendingSyncBanner(data.pendingActions)
                        Text(
                            text = stringResource(ArrUiR.string.arr_common_quality_definitions_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        data.drafts.forEach { draft ->
                            QualityDefinitionCard(
                                draft = draft,
                                errors = data.errors[draft.id].orEmpty(),
                                weightMin = data.limits?.min ?: 0,
                                weightMax = data.limits?.max ?: 100,
                                onEditRow = onEditRow,
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
private fun QualityDefinitionCard(
    draft: QualityDefinitionDraft,
    errors: Set<QualityDefinitionFieldError>,
    weightMin: Int,
    weightMax: Int,
    onEditRow: (Int, transform: (QualityDefinitionDraft) -> QualityDefinitionDraft) -> Unit,
) {
    QualityCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = draft.qualityName.ifBlank { draft.title },
                    style = MaterialTheme.typography.titleSmall,
                )
            }
        }
        QualityTextField(
            value = draft.title,
            label = stringResource(ArrUiR.string.arr_common_quality_definitions_title_field),
            onValue = { text -> onEditRow(draft.id) { it.copy(title = text) } },
        )
        QualityWeightSlider(
            label = stringResource(ArrUiR.string.arr_common_quality_definitions_weight),
            value = draft.weight,
            min = weightMin,
            max = weightMax,
            onValue = { value -> onEditRow(draft.id) { it.copy(weight = value) } },
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(Modifier.weight(1f)) {
                QualityTextField(
                    value = draft.minSize,
                    label = stringResource(ArrUiR.string.arr_common_quality_definitions_min),
                    onValue = { text -> onEditRow(draft.id) { it.copy(minSize = text) } },
                    isError = errors.contains(QualityDefinitionFieldError.MIN_NOT_A_NUMBER) ||
                        errors.contains(QualityDefinitionFieldError.MIN_NEGATIVE) ||
                        errors.contains(QualityDefinitionFieldError.MIN_ABOVE_MAX),
                    errorMessage = when {
                        errors.contains(QualityDefinitionFieldError.MIN_NOT_A_NUMBER) ->
                            stringResource(ArrUiR.string.arr_common_quality_definitions_error_number)

                        errors.contains(QualityDefinitionFieldError.MIN_NEGATIVE) ->
                            stringResource(ArrUiR.string.arr_common_quality_definitions_error_negative)

                        else -> stringResource(ArrUiR.string.arr_common_quality_definitions_error_min_above_max)
                    },
                )
            }
            Column(Modifier.weight(1f)) {
                QualityTextField(
                    value = draft.maxSize,
                    label = stringResource(ArrUiR.string.arr_common_quality_definitions_max),
                    onValue = { text -> onEditRow(draft.id) { it.copy(maxSize = text) } },
                    isError = errors.contains(QualityDefinitionFieldError.MAX_NOT_A_NUMBER) ||
                        errors.contains(QualityDefinitionFieldError.MIN_ABOVE_MAX) ||
                        errors.contains(QualityDefinitionFieldError.PREFERRED_ABOVE_MAX),
                    errorMessage = when {
                        errors.contains(QualityDefinitionFieldError.MAX_NOT_A_NUMBER) ->
                            stringResource(ArrUiR.string.arr_common_quality_definitions_error_number)

                        else -> stringResource(ArrUiR.string.arr_common_quality_definitions_error_min_above_max)
                    },
                )
            }
        }
        QualityTextField(
            value = draft.preferredSize,
            label = stringResource(ArrUiR.string.arr_common_quality_definitions_preferred),
            supporting = stringResource(ArrUiR.string.arr_common_quality_definitions_preferred_support),
            onValue = { text -> onEditRow(draft.id) { it.copy(preferredSize = text) } },
            isError = errors.contains(QualityDefinitionFieldError.PREFERRED_NOT_A_NUMBER) ||
                errors.contains(QualityDefinitionFieldError.PREFERRED_ABOVE_MAX),
            errorMessage = when {
                errors.contains(QualityDefinitionFieldError.PREFERRED_NOT_A_NUMBER) ->
                    stringResource(ArrUiR.string.arr_common_quality_definitions_error_number)

                else -> stringResource(ArrUiR.string.arr_common_quality_definitions_error_preferred_above_max)
            },
        )
        Text(
            text = stringResource(ArrUiR.string.arr_common_quality_definitions_size_unit),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Settings sections read best under this width on tablets. */
internal val SectionMaxWidth = 640.dp

/** Builds this group's repository against the per-server client bundle. */
internal fun qualityRepository(serverId: ServerId, clients: SonarrQualityClients): SonarrQualityRepository =
    SonarrQualityRepository(
        serverId = serverId,
        store = clients.store,
        qualityDefinitionApi = clients.apis.qualityDefinitionApi,
        qualityProfileApi = clients.apis.qualityProfileApi,
        qualityProfileSchemaApi = clients.apis.qualityProfileSchemaApi,
        languageProfileApi = clients.apis.languageProfileApi,
        languageProfileSchemaApi = clients.apis.languageProfileSchemaApi,
        customFormatApi = clients.apis.customFormatApi,
        delayProfileApi = clients.apis.delayProfileApi,
        releaseProfileApi = clients.apis.releaseProfileApi,
        languageApi = clients.apis.languageApi,
        actionQueue = clients.actionQueue,
    )
