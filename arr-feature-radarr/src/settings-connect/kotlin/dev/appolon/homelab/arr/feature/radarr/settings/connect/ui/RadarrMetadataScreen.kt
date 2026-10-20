package dev.appolon.homelab.arr.feature.radarr.settings.connect.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.ui.draw.alpha
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Science
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.appolon.homelab.arr.api.radarr.model.MetadataResource
import dev.appolon.homelab.arr.core.ServerId
import dev.appolon.homelab.arr.feature.radarr.R
import dev.appolon.homelab.arr.feature.radarr.settings.connect.RadarrConnectClients
import dev.appolon.homelab.arr.feature.radarr.settings.connect.vm.MetadataNotice
import dev.appolon.homelab.arr.feature.radarr.settings.connect.vm.RadarrMetadataContent
import dev.appolon.homelab.arr.feature.radarr.settings.connect.vm.RadarrMetadataViewModel
import dev.appolon.homelab.arr.ui.components.LabeledSwitch
import dev.appolon.homelab.arr.ui.components.SectionHeader
import dev.appolon.homelab.arr.ui.lce.ArrLceBox
import dev.appolon.homelab.arr.ui.lce.ArrPullToRefreshBox
import dev.appolon.homelab.arr.ui.lce.LceState
import dev.appolon.homelab.arr.ui.shell.ArrScaffold
import dev.appolon.homelab.arr.ui.R as ArrUiR

/** Destination for the metadata consumers route. */
@Composable
fun RadarrMetadataDestination(
    serverId: ServerId,
    clients: RadarrConnectClients,
    serverName: String? = null,
    onBack: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val repository = remember(serverId, clients) { connectRepository(serverId, clients) }
    val vm: RadarrMetadataViewModel = viewModel(key = "radarr-metadata-" + serverId.value) {
        RadarrMetadataViewModel(serverId, repository)
    }
    val state by vm.state.collectAsState()
    val lce by vm.lce.collectAsState()

    RadarrMetadataScreen(
        state = state,
        lce = lce,
        serverName = serverName,
        onBack = onBack,
        onRefresh = vm::refresh,
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
 * Metadata consumers: every consumer Radarr ships, each row carrying its
 * enable toggle; tapping a row expands the inline dynamic field editor
 * with Save and Test actions.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RadarrMetadataScreen(
    state: RadarrMetadataViewModel.UiState,
    lce: LceState<RadarrMetadataContent>,
    serverName: String?,
    onBack: (() -> Unit)?,
    onRefresh: () -> Unit,
    onToggleEnabled: (Int, Boolean) -> Unit,
    onValueChange: (Int, String, kotlinx.serialization.json.JsonElement) -> Unit,
    onTest: (Int) -> Unit,
    onSave: (Int) -> Unit,
    onConsumeNotice: () -> Unit,
    onConsumeWriteError: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val content = (lce as? LceState.Content<RadarrMetadataContent>)?.data
    val refreshing = content != null && (lce as LceState.Content<RadarrMetadataContent>).refreshing

    NoticeHost(
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
                    MetadataList(
                        data = data,
                        testingId = state.testingId,
                        onToggleEnabled = onToggleEnabled,
                        onValueChange = onValueChange,
                        onTest = onTest,
                        onSave = onSave,
                    )
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
private fun MetadataList(
    data: RadarrMetadataContent,
    testingId: Int?,
    onToggleEnabled: (Int, Boolean) -> Unit,
    onValueChange: (Int, String, kotlinx.serialization.json.JsonElement) -> Unit,
    onTest: (Int) -> Unit,
    onSave: (Int) -> Unit,
) {
    var expandedId by rememberSaveable { mutableStateOf<Int?>(null) }
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.widthIn(max = ListMaxWidth + 32.dp),
    ) {
        if (data.pendingActions > 0) {
            item { PendingBanner(data.pendingActions) }
        }
        item {
            SectionHeader(title = stringResource(ArrUiR.string.arr_common_metadata_section_consumers))
        }
        items(data.consumers, key = { it.id }, contentType = { "consumer" }) { consumer ->
            val draft = data.drafts[consumer.id]
            MetadataRow(
                consumer = consumer,
                draft = draft,
                expanded = expandedId == consumer.id,
                testing = testingId == consumer.id,
                onToggleExpanded = {
                    expandedId = if (expandedId == consumer.id) null else consumer.id
                },
                onToggleEnabled = { enabled -> onToggleEnabled(consumer.id, enabled) },
                onValueChange = { name, value ->
                    onValueChange(consumer.id, name, value)
                },
                onTest = { onTest(consumer.id) },
                onSave = { onSave(consumer.id) },
            )
        }
    }
}

@Composable
private fun MetadataRow(
    consumer: MetadataResource,
    draft: dev.appolon.homelab.arr.feature.radarr.settings.connect.vm.MetadataDraft?,
    expanded: Boolean,
    testing: Boolean,
    onToggleExpanded: () -> Unit,
    onToggleEnabled: (Boolean) -> Unit,
    onValueChange: (String, kotlinx.serialization.json.JsonElement) -> Unit,
    onTest: () -> Unit,
    onSave: () -> Unit,
) {
    val expandCd = stringResource(
        ArrUiR.string.arr_common_metadata_cd_expand,
        consumer.name.orEmpty(),
    )
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 64.dp)
                    .clickable(onClickLabel = expandCd, onClick = onToggleExpanded)
                    .padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 8.dp),
            ) {
                Column(Modifier.weight(1f)) {
                    Text(consumer.name.orEmpty(), style = MaterialTheme.typography.bodyLarge)
                    Text(
                        consumer.implementationName.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                LabeledSwitch(
                    label = consumer.name.orEmpty(),
                    checked = consumer.enable,
                    onCheckedChange = onToggleEnabled,
                )
                IconButton(
                    onClick = onToggleExpanded,
                    modifier = Modifier.semantics { contentDescription = expandCd },
                ) {
                    Icon(
                        imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                        contentDescription = null,
                    )
                }
            }
            AnimatedVisibility(visible = expanded && draft != null) {
                if (draft != null) {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        ConnectFieldEditors(
                            fields = draft.fields,
                            values = draft.values,
                            onValueChange = onValueChange,
                            tagOptions = emptyList(),
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            ExtendedFloatingActionButton(onClick = onSave) {
                                Text(stringResource(ArrUiR.string.arr_common_save))
                            }
                            ExtendedFloatingActionButton(
                                onClick = onTest,
                                modifier = if (testing) {
                                    Modifier.alpha(0.5f)
                                } else {
                                    Modifier
                                },
                            ) {
                                Icon(
                                    Icons.Filled.Science,
                                    contentDescription = stringResource(
                                        ArrUiR.string.arr_common_connect_cd_test,
                                    ),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
