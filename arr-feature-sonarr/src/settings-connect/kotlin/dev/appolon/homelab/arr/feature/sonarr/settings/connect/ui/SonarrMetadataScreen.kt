package dev.appolon.homelab.arr.feature.sonarr.settings.connect.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.appolon.homelab.arr.api.sonarr.model.MetadataResource
import dev.appolon.homelab.arr.core.ServerId
import dev.appolon.homelab.arr.feature.sonarr.settings.connect.SonarrConnectClients
import dev.appolon.homelab.arr.feature.sonarr.settings.connect.vm.MetadataNotice
import dev.appolon.homelab.arr.feature.sonarr.settings.connect.vm.SonarrMetadataViewModel
import dev.appolon.homelab.arr.ui.components.LabeledSwitch
import dev.appolon.homelab.arr.ui.components.SectionHeader
import dev.appolon.homelab.arr.ui.lce.ArrLceBox
import dev.appolon.homelab.arr.ui.lce.ArrPullToRefreshBox
import dev.appolon.homelab.arr.ui.lce.LceState
import dev.appolon.homelab.arr.ui.shell.ArrScaffold
import dev.appolon.homelab.arr.ui.R as ArrUiR

/** Destination for the metadata consumers route. */
@Composable
fun SonarrMetadataDestination(
    serverId: ServerId,
    clients: SonarrConnectClients,
    serverName: String? = null,
    onBack: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val repository = remember(serverId, clients) { connectRepository(serverId, clients) }
    val vm: SonarrMetadataViewModel =
        viewModel(key = "sonarr-metadata-" + serverId.value) {
            SonarrMetadataViewModel(serverId, repository)
        }
    val state by vm.state.collectAsState()
    val lce by vm.lce.collectAsState()

    SonarrMetadataScreen(
        state = state,
        lce = lce,
        serverName = serverName,
        onBack = onBack,
        onRefresh = vm::refresh,
        onExpand = vm::openForm,
        onCollapse = vm::closeForm,
        onToggleEnabled = vm::setEnabled,
        onValueChange = vm::onValueChange,
        onTest = vm::test,
        onSave = vm::save,
        onConsumeNotice = vm::consumeNotice,
        onConsumeWriteError = vm::consumeWriteError,
        modifier = modifier,
    )
}

/**
 * Metadata consumers. Rows carry the enable toggle and expand in place
 * into the consumer's dynamic field form with test and save actions.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SonarrMetadataScreen(
    state: SonarrMetadataViewModel.UiState,
    lce: LceState<SonarrMetadataViewModel.UiState>,
    serverName: String?,
    onBack: (() -> Unit)?,
    onRefresh: () -> Unit,
    onExpand: (MetadataResource) -> Unit,
    onCollapse: (Int) -> Unit,
    onToggleEnabled: (Int, Boolean) -> Unit,
    onValueChange: (Int, String, kotlinx.serialization.json.JsonElement) -> Unit,
    onTest: (Int) -> Unit,
    onSave: (Int) -> Unit,
    onConsumeNotice: () -> Unit,
    onConsumeWriteError: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val content = (lce as? LceState.Content<SonarrMetadataViewModel.UiState>)?.data
    val refreshing = content != null && (lce as LceState.Content<SonarrMetadataViewModel.UiState>).refreshing

    ConnectNoticeHost(
        noticeMessage = when (state.notice) {
            MetadataNotice.SAVED -> stringResource(ArrUiR.string.arr_common_connect_notice_saved)
            MetadataNotice.SAVED_QUEUED ->
                stringResource(ArrUiR.string.arr_common_system_notice_saved_queued)

            MetadataNotice.TEST_PASSED ->
                stringResource(ArrUiR.string.arr_common_connect_notice_test_passed)

            null -> null
        },
        writeError = state.writeError,
        snackbarHostState = snackbarHostState,
        onConsumeNotice = onConsumeNotice,
        onConsumeWriteError = onConsumeWriteError,
    )

    ArrScaffold(
        title = stringResource(ArrUiR.string.arr_common_metadata_title),
        subtitle = serverName,
        onBack = onBack,
        actions = {
            IconButton(onClick = onRefresh) {
                Icon(
                    imageVector = Icons.Filled.Refresh,
                    contentDescription = stringResource(ArrUiR.string.arr_common_metadata_cd_refresh),
                )
            }
        },
        modifier = modifier,
    ) { padding ->
        Box(Modifier.fillMaxSize()) {
            ArrPullToRefreshBox(
                isRefreshing = refreshing,
                onRefresh = onRefresh,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            ) {
                ArrLceBox(
                    state = lce,
                    onRetry = onRefresh,
                    emptyTitle = stringResource(ArrUiR.string.arr_common_metadata_empty_title),
                    emptyMessage = stringResource(ArrUiR.string.arr_common_metadata_empty_message),
                    modifier = Modifier.fillMaxSize(),
                ) { data ->
                    LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.widthIn(max = ListMaxWidth + 32.dp),
                    ) {
                        item {
                            SectionHeader(
                                title = stringResource(ArrUiR.string.arr_common_metadata_section_consumers),
                            )
                        }
                        items(
                            data.consumers,
                            key = { it.id },
                            contentType = { "consumer" },
                        ) { consumer ->
                            MetadataConsumerCard(
                                consumer = consumer,
                                draft = data.drafts[consumer.id],
                                testing = consumer.id in data.testingIds,
                                onExpand = { onExpand(consumer) },
                                onCollapse = { onCollapse(consumer.id) },
                                onToggleEnabled = { enabled ->
                                    onToggleEnabled(consumer.id, enabled)
                                },
                                onValueChange = { name, value ->
                                    onValueChange(consumer.id, name, value)
                                },
                                onTest = { onTest(consumer.id) },
                                onSave = { onSave(consumer.id) },
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

@Composable
private fun MetadataConsumerCard(
    consumer: MetadataResource,
    draft: dev.appolon.homelab.arr.feature.sonarr.settings.connect.vm.MetadataConsumerDraft?,
    testing: Boolean,
    onExpand: () -> Unit,
    onCollapse: () -> Unit,
    onToggleEnabled: (Boolean) -> Unit,
    onValueChange: (String, kotlinx.serialization.json.JsonElement) -> Unit,
    onTest: () -> Unit,
    onSave: () -> Unit,
) {
    val expanded = draft != null
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 8.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp),
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        consumer.name.orEmpty(),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Text(
                        text = consumer.implementationName.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                val toggleCd = stringResource(
                    ArrUiR.string.arr_common_clients_cd_toggle,
                    consumer.name.orEmpty(),
                )
                LabeledSwitch(
                    label = consumer.name.orEmpty(),
                    checked = consumer.enable,
                    onCheckedChange = onToggleEnabled,
                    modifier = Modifier.semantics { contentDescription = toggleCd },
                )
                val expandCd = stringResource(
                    ArrUiR.string.arr_common_metadata_cd_expand,
                    consumer.name.orEmpty(),
                )
                IconButton(
                    onClick = { if (expanded) onCollapse() else onExpand() },
                    modifier = Modifier.semantics { contentDescription = expandCd },
                ) {
                    Icon(
                        imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                        contentDescription = null,
                    )
                }
            }
            AnimatedVisibility(visible = expanded && draft != null) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.padding(top = 8.dp),
                ) {
                    if (draft != null) {
                        ConnectFieldEditors(
                            fields = draft.fields,
                            values = draft.values,
                            onValueChange = onValueChange,
                            tagOptions = emptyList(),
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = onTest, enabled = !testing) {
                                Text(stringResource(ArrUiR.string.arr_common_connect_test))
                            }
                            TextButton(onClick = onSave, enabled = !testing) {
                                Text(stringResource(ArrUiR.string.arr_common_save))
                            }
                        }
                    }
                }
            }
        }
    }
}
