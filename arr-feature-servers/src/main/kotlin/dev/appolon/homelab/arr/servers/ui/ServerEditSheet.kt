package dev.appolon.homelab.arr.servers.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.QrCode2
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import dev.appolon.homelab.arr.core.AppType
import dev.appolon.homelab.arr.servers.R
import dev.appolon.homelab.arr.servers.logic.ServerForm
import dev.appolon.homelab.arr.servers.vm.ServerEditViewModel
import dev.appolon.homelab.arr.ui.lce.ArrLceBox
import dev.appolon.homelab.arr.ui.lce.LceState
import dev.appolon.homelab.arr.ui.lce.arrErrorMessage
import dev.appolon.homelab.arr.ui.shell.ArrScaffold

/**
 * Full-screen add/edit editor: name, app type, base URL with a live
 * normalization preview, API key with paste and scan entry, optional basic
 * auth, a real test-connection round trip, and save. The test result and any
 * mapped [dev.appolon.homelab.arr.core.ArrError] render inline.
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ServerEditSheet(
    state: ServerEditViewModel.UiState,
    onUpdate: ((ServerForm) -> ServerForm) -> Unit,
    onTest: () -> Unit,
    onSave: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isEdit = state.original != null

    ArrScaffold(
        title = stringResource(
            if (isEdit) {
                R.string.arr_servers_edit_title_edit
            } else {
                R.string.arr_servers_edit_title_add
            },
        ),
        onBack = onBack,
        modifier = modifier,
        actions = {
            TextButton(onClick = onSave, enabled = !state.saving) {
                if (state.saving) {
                    CircularProgressIndicator(
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(16.dp),
                    )
                } else {
                    Text(stringResource(R.string.arr_servers_edit_save))
                }
            }
        },
    ) { padding ->
        ArrLceBox(
            state = editLce(state),
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) { loaded ->
            ServerEditFields(
                state = loaded,
                onUpdate = onUpdate,
                onTest = onTest,
            )
        }
    }
}

/** Loading while an existing server is read, error pane when decryption fails. */
private fun editLce(state: ServerEditViewModel.UiState): LceState<ServerEditViewModel.UiState> =
    when {
        state.loading -> LceState.Loading
        state.loadError != null -> LceState.Error(state.loadError)
        else -> LceState.Content(state)
    }

@Composable
private fun ServerEditFields(
    state: ServerEditViewModel.UiState,
    onUpdate: ((ServerForm) -> ServerForm) -> Unit,
    onTest: () -> Unit,
) {
    val form = state.form
    val showErrors = state.showErrors
    val clipboard = LocalClipboardManager.current
    var scanHintVisible by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        OutlinedTextField(
            value = form.name,
            onValueChange = { text -> onUpdate { it.copy(name = text) } },
            label = { Text(stringResource(R.string.arr_servers_edit_name_label)) },
            isError = showErrors && ServerForm.Problem.NAME_BLANK in form.problems,
            supportingText = {
                if (showErrors && ServerForm.Problem.NAME_BLANK in form.problems) {
                    Text(stringResource(R.string.arr_servers_edit_name_blank))
                }
            },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        Column {
            Text(
                text = stringResource(R.string.arr_servers_edit_type_label),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.size(8.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                AppType.entries.forEach { type ->
                    FilterChip(
                        selected = form.type == type,
                        onClick = { onUpdate { it.copy(type = type) } },
                        label = { Text(type.displayName) },
                    )
                }
            }
        }

        OutlinedTextField(
            value = form.baseUrl,
            onValueChange = { text -> onUpdate { it.copy(baseUrl = text) } },
            label = { Text(stringResource(R.string.arr_servers_edit_url_label)) },
            isError = showErrors &&
                (ServerForm.Problem.URL_BLANK in form.problems ||
                    ServerForm.Problem.URL_INVALID in form.problems),
            supportingText = {
                val preview = form.previewUrl()
                when {
                    showErrors && ServerForm.Problem.URL_BLANK in form.problems ->
                        Text(stringResource(R.string.arr_servers_edit_url_blank))

                    showErrors && ServerForm.Problem.URL_INVALID in form.problems ->
                        Text(stringResource(R.string.arr_servers_edit_url_invalid))

                    preview != null ->
                        Text(stringResource(R.string.arr_servers_edit_url_preview, preview))
                }
            },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        OutlinedTextField(
            value = form.apiKey,
            onValueChange = { text -> onUpdate { it.copy(apiKey = text) } },
            label = { Text(stringResource(R.string.arr_servers_edit_key_label)) },
            visualTransformation = PasswordVisualTransformation(),
            isError = showErrors && ServerForm.Problem.API_KEY_BLANK in form.problems,
            supportingText = {
                if (showErrors && ServerForm.Problem.API_KEY_BLANK in form.problems) {
                    Text(stringResource(R.string.arr_servers_edit_key_blank))
                }
            },
            singleLine = true,
            trailingIcon = {
                IconButton(
                    onClick = {
                        clipboard.getText()?.text?.trim()?.takeIf { it.isNotEmpty() }
                            ?.let { pasted -> onUpdate { it.copy(apiKey = pasted) } }
                    },
                ) {
                    Icon(
                        imageVector = Icons.Outlined.ContentPaste,
                        contentDescription = stringResource(R.string.arr_servers_edit_key_paste),
                    )
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )

        Column {
            OutlinedButton(onClick = { scanHintVisible = true }) {
                Icon(
                    imageVector = Icons.Outlined.QrCode2,
                    contentDescription = null,
                )
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.arr_servers_edit_key_scan))
            }
            if (scanHintVisible) {
                Text(
                    text = stringResource(R.string.arr_servers_edit_scan_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        OutlinedTextField(
            value = form.basicAuthUser,
            onValueChange = { text -> onUpdate { it.copy(basicAuthUser = text) } },
            label = { Text(stringResource(R.string.arr_servers_edit_basic_user_label)) },
            isError = showErrors &&
                ServerForm.Problem.BASIC_AUTH_INCOMPLETE in form.problems,
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
            modifier = Modifier.fillMaxWidth(),
        )

        OutlinedTextField(
            value = form.basicAuthPassword,
            onValueChange = { text -> onUpdate { it.copy(basicAuthPassword = text) } },
            label = { Text(stringResource(R.string.arr_servers_edit_basic_password_label)) },
            isError = showErrors &&
                ServerForm.Problem.BASIC_AUTH_INCOMPLETE in form.problems,
            supportingText = {
                if (showErrors && ServerForm.Problem.BASIC_AUTH_INCOMPLETE in form.problems) {
                    Text(stringResource(R.string.arr_servers_edit_basic_incomplete))
                }
            },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
        )

        Column {
            OutlinedButton(onClick = onTest, enabled = !state.testing) {
                if (state.testing) {
                    CircularProgressIndicator(
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.arr_servers_edit_testing))
                } else {
                    Text(stringResource(R.string.arr_servers_edit_test))
                }
            }
            when (val test = state.test) {
                is ServerEditViewModel.TestState.Succeeded -> Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 8.dp),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = test.version?.let { version ->
                            stringResource(R.string.arr_servers_edit_test_ok, version)
                        } ?: stringResource(R.string.arr_servers_edit_test_ok_noversion),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }

                is ServerEditViewModel.TestState.Failed -> Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 8.dp),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.ErrorOutline,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = arrErrorMessage(test.error),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                ServerEditViewModel.TestState.Idle -> Unit
            }
        }

        state.saveError?.let { error ->
            Text(
                text = arrErrorMessage(error),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}
