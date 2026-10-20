package dev.appolon.homelab.arr.servers.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import dev.appolon.homelab.arr.core.ServerId
import dev.appolon.homelab.arr.net.ProxyConfig
import dev.appolon.homelab.arr.servers.R
import dev.appolon.homelab.arr.servers.logic.ProxyForm
import dev.appolon.homelab.arr.servers.vm.ProxySettingsViewModel
import dev.appolon.homelab.arr.ui.components.LabeledSwitch
import dev.appolon.homelab.arr.ui.components.SectionHeader
import dev.appolon.homelab.arr.ui.lce.ArrLceBox
import dev.appolon.homelab.arr.ui.lce.LceState
import dev.appolon.homelab.arr.ui.lce.arrErrorMessage
import dev.appolon.homelab.arr.ui.shell.ArrScaffold
import androidx.compose.foundation.layout.Box

/**
 * Global default proxy plus a per-server override card: proxy type, host,
 * port, credentials, the Orbot toggle, and this server's TLS-trust options
 * (self-signed acceptance with optional SHA-256 pinning). One Save button
 * persists everything; a snackbar confirms it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProxySettingsScreen(
    state: ProxySettingsViewModel.UiState,
    onUpdateGlobal: (ProxyForm) -> Unit,
    onSetOverride: (ServerId, Boolean) -> Unit,
    onUpdateServer: (ServerId, ProxyForm) -> Unit,
    onSave: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val savedMessage = stringResource(R.string.arr_servers_proxy_saved)
    LaunchedEffect(state.savedTick) {
        if (state.savedTick > 0) snackbarHostState.showSnackbar(savedMessage)
    }

    ArrScaffold(
        title = stringResource(R.string.arr_servers_proxy_title),
        onBack = onBack,
        modifier = modifier,
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            ArrLceBox(
                state = proxyLce(state),
                modifier = Modifier.fillMaxSize(),
            ) { loaded ->
                ProxySettingsContent(
                    state = loaded,
                    onUpdateGlobal = onUpdateGlobal,
                    onSetOverride = onSetOverride,
                    onUpdateServer = onUpdateServer,
                    onSave = onSave,
                )
            }
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }
}

private fun proxyLce(
    state: ProxySettingsViewModel.UiState,
): LceState<ProxySettingsViewModel.UiState> =
    when {
        state.loadError != null -> LceState.Error(state.loadError)
        !state.loaded -> LceState.Loading
        else -> LceState.Content(state, refreshing = state.saving)
    }

@Composable
private fun ProxySettingsContent(
    state: ProxySettingsViewModel.UiState,
    onUpdateGlobal: (ProxyForm) -> Unit,
    onSetOverride: (ServerId, Boolean) -> Unit,
    onUpdateServer: (ServerId, ProxyForm) -> Unit,
    onSave: () -> Unit,
) {
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item { SectionHeader(title = stringResource(R.string.arr_servers_proxy_global_header)) }
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        text = stringResource(R.string.arr_servers_proxy_global_support),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    ProxyEditor(
                        form = state.global,
                        showErrors = state.showErrors,
                        onForm = onUpdateGlobal,
                    )
                }
            }
        }

        if (state.servers.isNotEmpty()) {
            item {
                SectionHeader(title = stringResource(R.string.arr_servers_proxy_servers_header))
            }
            items(state.servers.size) { index ->
                val row = state.servers[index]
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        LabeledSwitch(
                            label = row.server.name,
                            checked = row.override,
                            onCheckedChange = { onSetOverride(row.server.id, it) },
                            supportingText = stringResource(
                                R.string.arr_servers_proxy_override_support,
                            ),
                        )
                        if (row.override) {
                            ProxyEditor(
                                form = row.form,
                                showErrors = state.showErrors,
                                onForm = { form -> onUpdateServer(row.server.id, form) },
                            )
                            TrustEditor(
                                form = row.form,
                                showErrors = state.showErrors,
                                onForm = { form -> onUpdateServer(row.server.id, form) },
                            )
                        }
                    }
                }
            }
        }

        item {
            Button(
                onClick = onSave,
                enabled = !state.saving,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (state.saving) {
                    CircularProgressIndicator(
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Text(stringResource(R.string.arr_servers_proxy_save))
            }
        }
        item {
            if (state.showErrors) {
                Text(
                    text = stringResource(R.string.arr_servers_proxy_error),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            state.saveError?.let { error ->
                Text(
                    text = arrErrorMessage(error),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

/**
 * The proxy fields shared by the global editor and every server override.
 * Orbot mode hides host/port/credentials because Orbot supplies its own
 * SOCKS5 endpoint.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ProxyEditor(
    form: ProxyForm,
    showErrors: Boolean,
    onForm: (ProxyForm) -> Unit,
) {
    val explicitProxy = !form.useOrbot && form.type != ProxyConfig.Type.NONE

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ProxyTypeChip(
                selected = form.type == ProxyConfig.Type.NONE && !form.useOrbot,
                label = stringResource(R.string.arr_servers_proxy_type_none),
                onClick = { onForm(form.copy(type = ProxyConfig.Type.NONE, useOrbot = false)) },
            )
            ProxyTypeChip(
                selected = form.type == ProxyConfig.Type.HTTP && !form.useOrbot,
                label = stringResource(R.string.arr_servers_proxy_type_http),
                onClick = { onForm(form.copy(type = ProxyConfig.Type.HTTP, useOrbot = false)) },
            )
            ProxyTypeChip(
                selected = form.type == ProxyConfig.Type.SOCKS5 && !form.useOrbot,
                label = stringResource(R.string.arr_servers_proxy_type_socks5),
                onClick = { onForm(form.copy(type = ProxyConfig.Type.SOCKS5, useOrbot = false)) },
            )
        }

        if (explicitProxy) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = form.host,
                    onValueChange = { text -> onForm(form.copy(host = text)) },
                    label = { Text(stringResource(R.string.arr_servers_proxy_host_label)) },
                    isError = showErrors && ProxyForm.Problem.HOST_BLANK in form.problems,
                    supportingText = {
                        if (showErrors && ProxyForm.Problem.HOST_BLANK in form.problems) {
                            Text(stringResource(R.string.arr_servers_proxy_host_blank))
                        }
                    },
                    singleLine = true,
                    modifier = Modifier.weight(2f),
                )
                OutlinedTextField(
                    value = form.port,
                    onValueChange = { text ->
                        onForm(form.copy(port = text.filter(Char::isDigit)))
                    },
                    label = { Text(stringResource(R.string.arr_servers_proxy_port_label)) },
                    isError = showErrors &&
                        (ProxyForm.Problem.PORT_BLANK in form.problems ||
                            ProxyForm.Problem.PORT_INVALID in form.problems),
                    supportingText = {
                        when {
                            showErrors && ProxyForm.Problem.PORT_BLANK in form.problems ->
                                Text(stringResource(R.string.arr_servers_proxy_port_blank))

                            showErrors && ProxyForm.Problem.PORT_INVALID in form.problems ->
                                Text(stringResource(R.string.arr_servers_proxy_port_invalid))
                        }
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = form.username,
                    onValueChange = { text -> onForm(form.copy(username = text)) },
                    label = { Text(stringResource(R.string.arr_servers_proxy_username_label)) },
                    isError = showErrors &&
                        ProxyForm.Problem.CREDENTIALS_INCOMPLETE in form.problems,
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = form.password,
                    onValueChange = { text -> onForm(form.copy(password = text)) },
                    label = { Text(stringResource(R.string.arr_servers_proxy_password_label)) },
                    isError = showErrors &&
                        ProxyForm.Problem.CREDENTIALS_INCOMPLETE in form.problems,
                    supportingText = {
                        if (showErrors &&
                            ProxyForm.Problem.CREDENTIALS_INCOMPLETE in form.problems
                        ) {
                            Text(
                                stringResource(
                                    R.string.arr_servers_proxy_credentials_incomplete,
                                ),
                            )
                        }
                    },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        LabeledSwitch(
            label = stringResource(R.string.arr_servers_proxy_orbot),
            checked = form.useOrbot,
            onCheckedChange = { checked -> onForm(form.copy(useOrbot = checked)) },
            supportingText = stringResource(R.string.arr_servers_proxy_orbot_support),
        )
    }
}

@Composable
private fun ProxyTypeChip(
    selected: Boolean,
    label: String,
    onClick: () -> Unit,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
    )
}

/** Per-server TLS trust: self-signed acceptance plus optional fingerprint pin. */
@Composable
private fun TrustEditor(
    form: ProxyForm,
    showErrors: Boolean,
    onForm: (ProxyForm) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SectionHeader(title = stringResource(R.string.arr_servers_proxy_trust_header))
        LabeledSwitch(
            label = stringResource(R.string.arr_servers_proxy_trust_accept),
            checked = form.acceptSelfSigned,
            onCheckedChange = { checked -> onForm(form.copy(acceptSelfSigned = checked)) },
            supportingText = stringResource(R.string.arr_servers_proxy_trust_support),
        )
        if (form.acceptSelfSigned) {
            OutlinedTextField(
                value = form.fingerprint,
                onValueChange = { text -> onForm(form.copy(fingerprint = text)) },
                label = { Text(stringResource(R.string.arr_servers_proxy_fingerprint_label)) },
                isError = showErrors &&
                    ProxyForm.Problem.FINGERPRINT_INVALID in form.problems,
                supportingText = {
                    if (showErrors &&
                        ProxyForm.Problem.FINGERPRINT_INVALID in form.problems
                    ) {
                        Text(stringResource(R.string.arr_servers_proxy_fingerprint_invalid))
                    }
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
