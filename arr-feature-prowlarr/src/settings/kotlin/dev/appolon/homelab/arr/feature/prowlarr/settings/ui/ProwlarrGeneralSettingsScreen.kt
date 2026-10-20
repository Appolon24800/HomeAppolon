package dev.appolon.homelab.arr.feature.prowlarr.settings.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import dev.appolon.homelab.arr.api.prowlarr.model.AuthenticationRequiredType
import dev.appolon.homelab.arr.api.prowlarr.model.AuthenticationType
import dev.appolon.homelab.arr.feature.prowlarr.R
import dev.appolon.homelab.arr.feature.prowlarr.settings.vm.GeneralSettingsNotice
import dev.appolon.homelab.arr.feature.prowlarr.settings.vm.HostSettingsDraft
import dev.appolon.homelab.arr.feature.prowlarr.settings.vm.ProwlarrGeneralSettingsContent
import dev.appolon.homelab.arr.feature.prowlarr.settings.vm.ProwlarrGeneralSettingsViewModel
import dev.appolon.homelab.arr.ui.components.LabeledSwitch
import dev.appolon.homelab.arr.ui.lce.ArrLceBox
import dev.appolon.homelab.arr.ui.lce.ArrPullToRefreshBox
import dev.appolon.homelab.arr.ui.lce.LceState
import dev.appolon.homelab.arr.ui.shell.ArrScaffold

/**
 * The general settings page: host fields (bind address, port, URL base),
 * the security section with the authentication method and credentials, the
 * log level picker with analytics, the development logging switches, and
 * the UI culture picker over the localization options. The indexer proxy
 * note points at the indexers screen, which owns Prowlarr's per-indexer
 * proxies.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProwlarrGeneralSettingsScreen(
    state: ProwlarrGeneralSettingsViewModel.UiState,
    lce: LceState<ProwlarrGeneralSettingsContent>,
    serverName: String?,
    onBack: (() -> Unit)?,
    onRefresh: () -> Unit,
    onEditHost: ((HostSettingsDraft) -> HostSettingsDraft) -> Unit,
    onEditDevelopment:
        ((dev.appolon.homelab.arr.feature.prowlarr.settings.vm.DevelopmentSettingsDraft) ->
            dev.appolon.homelab.arr.feature.prowlarr.settings.vm.DevelopmentSettingsDraft) -> Unit,
    onCultureSelect: (String?) -> Unit,
    onSaveHost: () -> Unit,
    onSaveDevelopment: () -> Unit,
    onSaveCulture: () -> Unit,
    onOpenIndexerProxies: () -> Unit,
    onConsumeNotice: () -> Unit,
    onConsumeWriteError: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val content = lce as? LceState.Content<ProwlarrGeneralSettingsContent>

    NoticeHost(
        noticeMessage = when (state.notice) {
            GeneralSettingsNotice.HOST_SAVED ->
                stringResource(R.string.arr_prowlarr_settings_notice_saved)

            GeneralSettingsNotice.HOST_SAVED_QUEUED ->
                stringResource(R.string.arr_prowlarr_settings_notice_saved_queued)

            GeneralSettingsNotice.CULTURE_SAVED ->
                stringResource(R.string.arr_prowlarr_settings_notice_saved)

            GeneralSettingsNotice.CULTURE_SAVED_QUEUED ->
                stringResource(R.string.arr_prowlarr_settings_notice_saved_queued)

            null -> null
        },
        writeError = state.writeError,
        snackbarHostState = snackbarHostState,
        onConsumeNotice = onConsumeNotice,
        onConsumeWriteError = onConsumeWriteError,
    )

    ArrScaffold(
        title = stringResource(R.string.arr_prowlarr_settings_general_title),
        subtitle = serverName,
        onBack = onBack,
        actions = {
            IconButton(onClick = onRefresh) {
                Icon(
                    imageVector = Icons.Filled.Refresh,
                    contentDescription = stringResource(R.string.arr_prowlarr_settings_cd_refresh),
                )
            }
        },
        modifier = modifier,
    ) { padding ->
        Box(Modifier.fillMaxSize()) {
            ArrPullToRefreshBox(
                isRefreshing = content?.refreshing == true,
                onRefresh = onRefresh,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            ) {
                ArrLceBox(
                    state = lce,
                    onRetry = onRefresh,
                    emptyTitle = stringResource(R.string.arr_prowlarr_settings_general_title),
                    emptyMessage = stringResource(R.string.arr_prowlarr_settings_general_empty),
                    modifier = Modifier.fillMaxSize(),
                ) { data ->
                    LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.widthIn(max = ListMaxWidth + 32.dp),
                    ) {
                        if (data.pendingActions > 0) {
                            item { PendingBanner(data.pendingActions) }
                        }
                        item { HostSection(data, onEditHost, onSaveHost, data.hostDirty) }
                        item {
                            SecuritySection(data, onEditHost)
                        }
                        item {
                            DevelopmentSection(
                                data.developmentDraft,
                                onEditDevelopment,
                                onSaveDevelopment,
                                data.developmentDirty,
                            )
                        }
                        item {
                            CultureSection(
                                data,
                                onCultureSelect,
                                onSaveCulture,
                                data.cultureDirty,
                            )
                        }
                        item { IndexerProxyNote(onOpenIndexerProxies) }
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
private fun HostSection(
    data: ProwlarrGeneralSettingsContent,
    onEditHost: ((HostSettingsDraft) -> HostSettingsDraft) -> Unit,
    onSaveHost: () -> Unit,
    dirty: Boolean,
) {
    val draft = data.hostDraft
    SettingsSection(title = stringResource(R.string.arr_prowlarr_settings_host_section)) {
        OutlinedTextField(
            value = draft.instanceName,
            onValueChange = { name -> onEditHost { it.copy(instanceName = name) } },
            label = { Text(stringResource(R.string.arr_prowlarr_settings_instance_name)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = draft.bindAddress,
            onValueChange = { address -> onEditHost { it.copy(bindAddress = address) } },
            label = { Text(stringResource(R.string.arr_prowlarr_settings_bind_address)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = draft.port,
            onValueChange = { port -> onEditHost { it.copy(port = port) } },
            label = { Text(stringResource(R.string.arr_prowlarr_settings_port)) },
            isError = data.portError,
            supportingText = if (data.portError) {
                { Text(stringResource(R.string.arr_prowlarr_settings_port_invalid)) }
            } else {
                null
            },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = draft.urlBase,
            onValueChange = { base -> onEditHost { it.copy(urlBase = base) } },
            label = { Text(stringResource(R.string.arr_prowlarr_settings_url_base)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        LabeledSwitch(
            label = stringResource(R.string.arr_prowlarr_settings_launch_browser),
            checked = draft.launchBrowser,
            onCheckedChange = { launch -> onEditHost { it.copy(launchBrowser = launch) } },
        )
        SaveFab(onClick = onSaveHost, enabled = dirty && !data.portError && !data.saving)
    }
}

@Composable
private fun SecuritySection(
    data: ProwlarrGeneralSettingsContent,
    onEditHost: ((HostSettingsDraft) -> HostSettingsDraft) -> Unit,
) {
    val draft = data.hostDraft
    SettingsSection(title = stringResource(R.string.arr_prowlarr_settings_security_section)) {
        StringDropdownField(
            label = stringResource(R.string.arr_prowlarr_settings_auth_method),
            options = AUTH_METHODS.keys.toList(),
            selected = AUTH_METHODS.entries.firstOrNull { it.value == draft.authenticationMethod }
                ?.key,
            onSelect = { label ->
                onEditHost {
                    it.copy(authenticationMethod = AUTH_METHODS.getValue(label))
                }
            },
        )
        if (draft.authenticationMethod != AuthenticationType.NONE &&
            draft.authenticationMethod != AuthenticationType.EXTERNAL
        ) {
            StringDropdownField(
                label = stringResource(R.string.arr_prowlarr_settings_auth_required),
                options = AUTH_REQUIRED.keys.toList(),
                selected = AUTH_REQUIRED.entries
                    .firstOrNull { it.value == draft.authenticationRequired }
                    ?.key,
                onSelect = { label ->
                    onEditHost {
                        it.copy(authenticationRequired = AUTH_REQUIRED.getValue(label))
                    }
                },
            )
            OutlinedTextField(
                value = draft.username,
                onValueChange = { user -> onEditHost { it.copy(username = user) } },
                label = { Text(stringResource(R.string.arr_prowlarr_settings_username)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = draft.password,
                onValueChange = { password -> onEditHost { it.copy(password = password) } },
                label = { Text(stringResource(R.string.arr_prowlarr_settings_password)) },
                supportingText = {
                    Text(stringResource(R.string.arr_prowlarr_settings_password_help))
                },
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            Text(
                stringResource(R.string.arr_prowlarr_settings_auth_off_help),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        StringDropdownField(
            label = stringResource(R.string.arr_prowlarr_settings_log_level),
            options = HostSettingsDraft.LOG_LEVELS,
            selected = draft.logLevel,
            onSelect = { level -> onEditHost { it.copy(logLevel = level) } },
        )
        LabeledSwitch(
            label = stringResource(R.string.arr_prowlarr_settings_analytics),
            checked = draft.analyticsEnabled,
            onCheckedChange = { analytics ->
                onEditHost { it.copy(analyticsEnabled = analytics) }
            },
        )
    }
}

@Composable
private fun DevelopmentSection(
    draft: dev.appolon.homelab.arr.feature.prowlarr.settings.vm.DevelopmentSettingsDraft,
    onEditDevelopment:
        ((dev.appolon.homelab.arr.feature.prowlarr.settings.vm.DevelopmentSettingsDraft) ->
            dev.appolon.homelab.arr.feature.prowlarr.settings.vm.DevelopmentSettingsDraft) -> Unit,
    onSaveDevelopment: () -> Unit,
    dirty: Boolean,
    saving: Boolean = false,
) {
    SettingsSection(title = stringResource(R.string.arr_prowlarr_settings_development_section)) {
        LabeledSwitch(
            label = stringResource(R.string.arr_prowlarr_settings_log_sql),
            checked = draft.logSql,
            onCheckedChange = { value -> onEditDevelopment { it.copy(logSql = value) } },
        )
        LabeledSwitch(
            label = stringResource(R.string.arr_prowlarr_settings_log_indexer_response),
            checked = draft.logIndexerResponse,
            onCheckedChange = { value ->
                onEditDevelopment { it.copy(logIndexerResponse = value) }
            },
        )
        LabeledSwitch(
            label = stringResource(R.string.arr_prowlarr_settings_filter_sentry),
            checked = draft.filterSentryEvents,
            onCheckedChange = { value ->
                onEditDevelopment { it.copy(filterSentryEvents = value) }
            },
        )
        SaveFab(onClick = onSaveDevelopment, enabled = dirty && !saving)
    }
}

@Composable
private fun CultureSection(
    data: ProwlarrGeneralSettingsContent,
    onCultureSelect: (String?) -> Unit,
    onSaveCulture: () -> Unit,
    dirty: Boolean,
    saving: Boolean = false,
) {
    SettingsSection(title = stringResource(R.string.arr_prowlarr_settings_culture_section)) {
        StringDropdownField(
            label = stringResource(R.string.arr_prowlarr_settings_ui_culture),
            options = data.localizationOptions.mapNotNull { it.value },
            selected = data.uiLanguage,
            onSelect = onCultureSelect,
        )
        Text(
            stringResource(R.string.arr_prowlarr_settings_ui_culture_help),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        SaveFab(onClick = onSaveCulture, enabled = dirty && !saving)
    }
}

/**
 * Prowlarr-specific note: the app-level outbound proxy lives in the host
 * config's display, but per-indexer proxies are managed on the indexers
 * screen. The button deep-links there.
 */
@Composable
private fun IndexerProxyNote(onOpenIndexerProxies: () -> Unit) {
    SettingsSection(title = stringResource(R.string.arr_prowlarr_settings_proxy_note_title)) {
        Text(
            stringResource(R.string.arr_prowlarr_settings_proxy_note_message),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TextButton(onClick = onOpenIndexerProxies) {
            Text(stringResource(R.string.arr_prowlarr_settings_proxy_note_open))
        }
    }
}

@Composable
private fun SaveFab(onClick: () -> Unit, enabled: Boolean) {
    ExtendedFloatingActionButton(
        onClick = onClick,
        icon = { Icon(Icons.Filled.Save, contentDescription = null) },
        text = { Text(stringResource(R.string.arr_prowlarr_save)) },
        modifier = if (enabled) Modifier else Modifier.alpha(0.5f),
    )
}

/** Wire labels for the authentication dropdowns. */
private val AUTH_METHODS = linkedMapOf(
    "None" to AuthenticationType.NONE,
    "Basic" to AuthenticationType.BASIC,
    "Forms" to AuthenticationType.FORMS,
    "External" to AuthenticationType.EXTERNAL,
)

private val AUTH_REQUIRED = linkedMapOf(
    "Enabled" to AuthenticationRequiredType.ENABLED,
    "Disabled for local addresses" to
        AuthenticationRequiredType.DISABLED_FOR_LOCAL_ADDRESSES,
)
