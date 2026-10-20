package dev.appolon.homelab.arr.feature.prowlarr.indexers.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Science
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.appolon.homelab.arr.api.prowlarr.model.IndexerProxyResource
import dev.appolon.homelab.arr.feature.prowlarr.R
import dev.appolon.homelab.arr.feature.prowlarr.indexers.vm.ProxiesMessage
import dev.appolon.homelab.arr.feature.prowlarr.indexers.vm.ProxiesUiState
import dev.appolon.homelab.arr.feature.prowlarr.indexers.vm.ProxyEditorForm
import dev.appolon.homelab.arr.ui.lce.ArrLceBox
import dev.appolon.homelab.arr.ui.lce.ArrPullToRefreshBox
import dev.appolon.homelab.arr.ui.lce.LceState
import dev.appolon.homelab.arr.ui.lce.arrErrorMessage
import dev.appolon.homelab.arr.ui.shell.ArrScaffold
import kotlinx.serialization.json.JsonElement

/**
 * Indexer proxies screen: list with CRUD over the generic field model.
 * Proxies ride between Prowlarr and the indexers, so the editor renders
 * whatever fields the proxy implementation declares.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun ProxiesScreen(
    state: ProxiesUiState,
    lce: LceState<ProxiesUiState>,
    onRefresh: () -> Unit,
    onBack: () -> Unit,
    onAdd: () -> Unit,
    onEdit: (IndexerProxyResource) -> Unit,
    onEditorClose: () -> Unit,
    onFieldValueChange: (String, JsonElement) -> Unit,
    onNameChange: (String) -> Unit,
    onToggleTag: (String) -> Unit,
    onSave: () -> Unit,
    onTest: () -> Unit,
    onRequestDelete: (Int) -> Unit,
    onDeleteConfirm: () -> Unit,
    onDeleteDismiss: () -> Unit,
    onConsumeMessage: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val messageText = state.message?.let { proxiesMessageText(it) }
    LaunchedEffect(messageText) {
        if (messageText != null) {
            snackbarHostState.showSnackbar(messageText)
            onConsumeMessage()
        }
    }

    ArrScaffold(
        title = stringResource(R.string.arr_prowlarr_proxies_title),
        onBack = onBack,
        modifier = modifier,
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            ArrPullToRefreshBox(
                isRefreshing = state.refreshing,
                onRefresh = onRefresh,
                modifier = Modifier.fillMaxSize(),
            ) {
                ArrLceBox(
                    state = lce,
                    onRetry = onRefresh,
                    emptyTitle = stringResource(R.string.arr_prowlarr_proxies_empty),
                    modifier = Modifier.fillMaxSize(),
                ) { content ->
                    LazyColumn(
                        contentPadding = PaddingValues(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        items(content.proxies, key = { it.id }) { proxy ->
                            ProxyCard(
                                proxy = proxy,
                                state = content,
                                onEdit = onEdit,
                                onTest = onTest,
                                onRequestDelete = onRequestDelete,
                            )
                        }
                    }
                }
            }
            ExtendedFloatingActionButton(
                onClick = onAdd,
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.arr_prowlarr_proxies_add)) },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp),
            )
        }
        SnackbarHost(hostState = snackbarHostState) { data ->
            Snackbar(snackbarData = data)
        }
    }

    if (state.editor != null) {
        ProxyEditorSheet(
            form = state.editor,
            state = state,
            onFieldValueChange = onFieldValueChange,
            onNameChange = onNameChange,
            onToggleTag = onToggleTag,
            onSave = onSave,
            onTest = onTest,
            onDismiss = onEditorClose,
        )
    }
    state.confirmDeleteId?.let { id ->
        AlertDialog(
            onDismissRequest = onDeleteDismiss,
            title = { Text(stringResource(R.string.arr_prowlarr_proxies_delete_title)) },
            text = { Text(stringResource(R.string.arr_prowlarr_proxies_delete_message)) },
            confirmButton = {
                TextButton(onClick = onDeleteConfirm) {
                    Text(stringResource(R.string.arr_prowlarr_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = onDeleteDismiss) {
                    Text(stringResource(R.string.arr_prowlarr_cancel))
                }
            },
        )
    }
}

@Composable
private fun ProxyCard(
    proxy: IndexerProxyResource,
    state: ProxiesUiState,
    onEdit: (IndexerProxyResource) -> Unit,
    onTest: () -> Unit,
    onRequestDelete: (Int) -> Unit,
) {
    val editCd = stringResource(R.string.arr_prowlarr_cd_edit_proxy, proxy.name.orEmpty())
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        proxy.name.orEmpty(),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        proxy.implementationName.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = { onTest() }) {
                    Icon(
                        imageVector = Icons.Filled.Science,
                        contentDescription = stringResource(R.string.arr_prowlarr_test),
                    )
                }
                IconButton(onClick = { onEdit(proxy) }) {
                    Icon(
                        imageVector = Icons.Filled.Edit,
                        contentDescription = editCd,
                    )
                }
                IconButton(onClick = { onRequestDelete(proxy.id) }) {
                    Icon(
                        imageVector = Icons.Filled.Delete,
                        contentDescription = stringResource(
                            R.string.arr_prowlarr_cd_delete_proxy,
                            proxy.name.orEmpty(),
                        ),
                    )
                }
            }
            TagChips(
                tags = proxy.tags.orEmpty(),
                allTags = state.tags,
            )
        }
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun ProxyEditorSheet(
    form: ProxyEditorForm,
    state: ProxiesUiState,
    onFieldValueChange: (String, JsonElement) -> Unit,
    onNameChange: (String) -> Unit,
    onToggleTag: (String) -> Unit,
    onSave: () -> Unit,
    onTest: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                stringResource(
                    if (form.editing != null) R.string.arr_prowlarr_proxies_edit
                    else R.string.arr_prowlarr_proxies_add,
                ),
                style = MaterialTheme.typography.titleMedium,
            )
            OutlinedTextField(
                value = form.name,
                onValueChange = onNameChange,
                label = { Text(stringResource(R.string.arr_prowlarr_editor_name)) },
                isError = form.nameMissing,
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            FieldEditors(
                fields = form.fields,
                values = form.values,
                onValueChange = onFieldValueChange,
                tagOptions = state.tags,
                showAdvanced = true,
            )
            TagLabelPicker(
                allTags = state.tags,
                selected = form.tagLabels,
                onToggle = onToggleTag,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onDismiss, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.arr_prowlarr_cancel))
                }
                Button(onClick = onSave, enabled = !state.saving, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.arr_prowlarr_save))
                }
            }
        }
    }
}

/** One-line snackbar text for a proxies message. */
@Composable
internal fun proxiesMessageText(message: ProxiesMessage): String = when (message) {
    ProxiesMessage.Saved -> stringResource(R.string.arr_prowlarr_message_saved)
    ProxiesMessage.SavedQueued -> stringResource(R.string.arr_prowlarr_message_saved_queued)
    ProxiesMessage.Deleted -> stringResource(R.string.arr_prowlarr_message_delete_synced)
    ProxiesMessage.DeleteQueued -> stringResource(R.string.arr_prowlarr_message_delete_queued)
    ProxiesMessage.TestPassed -> stringResource(R.string.arr_prowlarr_message_test_passed)
    is ProxiesMessage.TestFailed ->
        stringResource(R.string.arr_prowlarr_message_test_failed, arrErrorMessage(message.error))

    is ProxiesMessage.WriteFailed ->
        stringResource(R.string.arr_prowlarr_message_write_failed, arrErrorMessage(message.error))

    ProxiesMessage.NameMissing -> stringResource(R.string.arr_prowlarr_editor_name_missing)
}
