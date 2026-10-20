package dev.appolon.homelab.arr.feature.radarr.settings.system.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
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
import dev.appolon.homelab.arr.api.radarr.model.AuthenticationRequiredType
import dev.appolon.homelab.arr.api.radarr.model.AuthenticationType
import dev.appolon.homelab.arr.api.radarr.model.CertificateValidationType
import dev.appolon.homelab.arr.core.ArrError
import dev.appolon.homelab.arr.core.ServerId
import dev.appolon.homelab.arr.feature.radarr.R
import dev.appolon.homelab.arr.feature.radarr.settings.system.RadarrSettingsSystemClients
import dev.appolon.homelab.arr.feature.radarr.settings.system.data.RadarrSettingsSystemRepository
import dev.appolon.homelab.arr.feature.radarr.settings.system.data.SystemText
import dev.appolon.homelab.arr.feature.radarr.settings.system.vm.GeneralSettingsDraft
import dev.appolon.homelab.arr.feature.radarr.settings.system.vm.GeneralSettingsNotice
import dev.appolon.homelab.arr.feature.radarr.settings.system.vm.RadarrGeneralSettingsContent
import dev.appolon.homelab.arr.feature.radarr.settings.system.vm.RadarrGeneralSettingsViewModel
import dev.appolon.homelab.arr.feature.radarr.settings.system.vm.usesCredentials
import dev.appolon.homelab.arr.ui.components.LabeledSwitch
import dev.appolon.homelab.arr.ui.lce.ArrLceBox
import dev.appolon.homelab.arr.ui.lce.ArrPullToRefreshBox
import dev.appolon.homelab.arr.ui.lce.LceState
import dev.appolon.homelab.arr.ui.shell.ArrScaffold

/** Shared repository factory for this group's screens. */
internal fun systemRepository(
    serverId: ServerId,
    clients: RadarrSettingsSystemClients,
): RadarrSettingsSystemRepository = RadarrSettingsSystemRepository(
    serverId = serverId,
    store = clients.store,
    hostConfigApi = clients.apis.hostConfigApi,
    backupApi = clients.apis.backupApi,
    updateApi = clients.apis.updateApi,
    updateLogFileApi = clients.apis.updateLogFileApi,
    systemApi = clients.apis.systemApi,
    taskApi = clients.apis.taskApi,
    diskSpaceApi = clients.apis.diskSpaceApi,
    healthApi = clients.apis.healthApi,
    tagApi = clients.apis.tagApi,
    tagDetailsApi = clients.apis.tagDetailsApi,
    autoTaggingApi = clients.apis.autoTaggingApi,
    commandApi = clients.apis.commandApi,
    actionQueue = clients.actionQueue,
)

/** Destination for the general settings route. */
@Composable
fun RadarrGeneralSettingsDestination(
    serverId: ServerId,
    clients: RadarrSettingsSystemClients,
    serverName: String? = null,
    onBack: (() -> Unit)? = null,
    onOpenLink: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val repository = remember(serverId, clients) { systemRepository(serverId, clients) }
    val vm: RadarrGeneralSettingsViewModel =
        viewModel(key = "radarr-system-general-" + serverId.value) {
            RadarrGeneralSettingsViewModel(repository)
        }
    val lce by vm.lce.collectAsState()
    val uiState by vm.state.collectAsState()
    val noticeMessage = uiState.notice?.let { notice ->
        stringResource(
            when (notice) {
                GeneralSettingsNotice.SAVED -> R.string.arr_radarr_system_notice_saved
                GeneralSettingsNotice.SAVED_QUEUED -> R.string.arr_radarr_system_notice_saved_queued
            },
        )
    }

    RadarrGeneralSettingsScreen(
        lce = lce,
        serverName = serverName,
        onBack = onBack,
        onRefresh = vm::refresh,
        onEdit = vm::edit,
        onSave = vm::save,
        noticeMessage = noticeMessage,
        writeError = uiState.writeError,
        onConsumeNotice = vm::consumeNotice,
        onConsumeWriteError = vm::consumeWriteError,
        onOpenLink = onOpenLink,
        modifier = modifier,
    )
}

/**
 * The general settings page: host fields, read-only SSL display with the
 * server docs link, the security section with auth method and credential
 * management where the API allows, the log level picker, the analytics
 * toggle, and the server-side proxy display with a pointer to the app's
 * own proxy settings.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RadarrGeneralSettingsScreen(
    lce: LceState<RadarrGeneralSettingsContent>,
    serverName: String?,
    onBack: (() -> Unit)?,
    onRefresh: () -> Unit,
    onEdit: (transform: (GeneralSettingsDraft) -> GeneralSettingsDraft) -> Unit,
    onSave: () -> Unit,
    noticeMessage: String?,
    writeError: ArrError?,
    onConsumeNotice: () -> Unit,
    onConsumeWriteError: () -> Unit,
    onOpenLink: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val content = (lce as? LceState.Content<RadarrGeneralSettingsContent>)?.data
    val refreshing = content != null && (lce as LceState.Content<RadarrGeneralSettingsContent>).refreshing

    SystemNoticeHost(
        noticeMessage = noticeMessage,
        writeError = writeError,
        snackbarHostState = snackbarHostState,
        onConsumeNotice = onConsumeNotice,
        onConsumeWriteError = onConsumeWriteError,
    )

    ArrScaffold(
        title = stringResource(R.string.arr_radarr_general_title),
        subtitle = serverName,
        onBack = onBack,
        actions = {
            IconButton(onClick = onRefresh) {
                Icon(
                    imageVector = Icons.Filled.Refresh,
                    contentDescription = stringResource(R.string.arr_radarr_system_cd_refresh),
                )
            }
            IconButton(onClick = onSave, enabled = content?.dirty == true && !content.portError) {
                Icon(
                    imageVector = Icons.Filled.Save,
                    contentDescription = stringResource(R.string.arr_radarr_system_cd_save),
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
                    val draft = data.draft
                    LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.widthIn(max = SectionMaxWidth + 32.dp),
                    ) {
                        if (data.pendingActions > 0) {
                            item { SystemPendingSyncBanner(data.pendingActions) }
                        }
                        item { GeneralHostSection(draft, onEdit, data.portError) }
                        item { GeneralSslSection(draft, onEdit, onOpenLink) }
                        item { GeneralSecuritySection(draft, onEdit) }
                        item { GeneralLoggingSection(draft, onEdit) }
                        item { GeneralAnalyticsSection(draft, onEdit) }
                        item { GeneralProxySection(draft) }
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
private fun GeneralHostSection(
    draft: GeneralSettingsDraft,
    onEdit: (transform: (GeneralSettingsDraft) -> GeneralSettingsDraft) -> Unit,
    portError: Boolean,
) {
    SystemCard {
        Column {
            SystemSectionHeader(stringResource(R.string.arr_radarr_general_section_host))
            SystemTextField(
                value = draft.instanceName,
                label = stringResource(R.string.arr_radarr_general_instance_name),
                onValue = { value -> onEdit { it.copy(instanceName = value) } },
            )
            SystemTextField(
                value = draft.bindAddress,
                label = stringResource(R.string.arr_radarr_general_bind_address),
                onValue = { value -> onEdit { it.copy(bindAddress = value) } },
            )
            SystemTextField(
                value = draft.port,
                label = stringResource(R.string.arr_radarr_general_port),
                onValue = { value -> onEdit { it.copy(port = value.filter(Char::isDigit)) } },
                isError = portError,
                errorMessage = stringResource(R.string.arr_radarr_general_port_error),
            )
            SystemTextField(
                value = draft.urlBase,
                label = stringResource(R.string.arr_radarr_general_url_base),
                onValue = { value -> onEdit { it.copy(urlBase = value) } },
                supporting = stringResource(R.string.arr_radarr_general_url_base_support),
            )
            LabeledSwitch(
                label = stringResource(R.string.arr_radarr_general_launch_browser),
                supportingText = stringResource(R.string.arr_radarr_general_launch_browser_support),
                checked = draft.launchBrowser,
                onCheckedChange = { checked -> onEdit { it.copy(launchBrowser = checked) } },
            )
        }
    }
}

@Composable
private fun GeneralSslSection(
    draft: GeneralSettingsDraft,
    onEdit: (transform: (GeneralSettingsDraft) -> GeneralSettingsDraft) -> Unit,
    onOpenLink: ((String) -> Unit)?,
) {
    SystemCard {
        Column {
            SystemSectionHeader(stringResource(R.string.arr_radarr_general_section_ssl))
            LabeledSwitch(
                label = stringResource(R.string.arr_radarr_general_ssl_enable),
                checked = draft.enableSsl,
                onCheckedChange = { checked -> onEdit { it.copy(enableSsl = checked) } },
            )
            if (draft.enableSsl) {
                SystemTextField(
                    value = draft.sslPort,
                    label = stringResource(R.string.arr_radarr_general_ssl_port),
                    onValue = { value -> onEdit { it.copy(sslPort = value.filter(Char::isDigit)) } },
                )
                SystemValueRow(
                    label = stringResource(R.string.arr_radarr_general_ssl_cert_path),
                    value = draft.sslCertPath.ifBlank {
                        stringResource(R.string.arr_radarr_general_ssl_unset)
                    },
                )
                SystemValueRow(
                    label = stringResource(R.string.arr_radarr_general_ssl_key_path),
                    value = draft.sslKeyPath.ifBlank {
                        stringResource(R.string.arr_radarr_general_ssl_unset)
                    },
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                HintText(
                    text = stringResource(R.string.arr_radarr_general_ssl_read_only),
                    modifier = Modifier.weight(1f),
                )
                SystemExternalLinkButton(
                    contentDescription = stringResource(R.string.arr_radarr_general_ssl_docs),
                    onOpenLink = onOpenLink?.let { open -> { open(SystemText.RADARR_SSL_DOC_URL) } },
                )
            }
        }
    }
}

@Composable
private fun GeneralSecuritySection(
    draft: GeneralSettingsDraft,
    onEdit: (transform: (GeneralSettingsDraft) -> GeneralSettingsDraft) -> Unit,
) {
    SystemCard {
        Column {
            SystemSectionHeader(stringResource(R.string.arr_radarr_general_section_security))
            SystemDropdown(
                label = stringResource(R.string.arr_radarr_general_auth_method),
                selected = draft.authenticationMethod,
                entries = AuthenticationType.entries,
                labelOf = { it.authMethodLabel() },
                onSelect = { method ->
                    onEdit {
                        it.copy(
                            authenticationMethod = method,
                            username = if (method.usesCredentials()) it.username else "",
                            password = if (method.usesCredentials()) it.password else "",
                        )
                    }
                },
            )
            when {
                draft.authenticationMethod == AuthenticationType.NONE -> HintText(
                    stringResource(R.string.arr_radarr_general_auth_none_warning),
                )

                draft.usesCredentials() -> {
                    SystemTextField(
                        value = draft.username,
                        label = stringResource(R.string.arr_radarr_general_username),
                        onValue = { value -> onEdit { it.copy(username = value) } },
                    )
                    SystemTextField(
                        value = draft.password,
                        label = stringResource(R.string.arr_radarr_general_password),
                        onValue = { value -> onEdit { it.copy(password = value) } },
                        supporting = stringResource(R.string.arr_radarr_general_password_support),
                    )
                }

                else -> HintText(stringResource(R.string.arr_radarr_general_auth_external_hint))
            }
            SystemDropdown(
                label = stringResource(R.string.arr_radarr_general_auth_required),
                selected = draft.authenticationRequired,
                entries = AuthenticationRequiredType.entries,
                labelOf = { it.authRequiredLabel() },
                onSelect = { value -> onEdit { it.copy(authenticationRequired = value) } },
            )
            SystemDropdown(
                label = stringResource(R.string.arr_radarr_general_certificate_validation),
                selected = draft.certificateValidation,
                entries = CertificateValidationType.entries,
                labelOf = { it.certValidationLabel() },
                onSelect = { value -> onEdit { it.copy(certificateValidation = value) } },
            )
        }
    }
}

@Composable
private fun GeneralLoggingSection(
    draft: GeneralSettingsDraft,
    onEdit: (transform: (GeneralSettingsDraft) -> GeneralSettingsDraft) -> Unit,
) {
    SystemCard {
        Column {
            SystemSectionHeader(stringResource(R.string.arr_radarr_general_section_logging))
            SystemDropdown(
                label = stringResource(R.string.arr_radarr_general_log_level),
                selected = draft.logLevel,
                entries = GeneralSettingsDraft.LOG_LEVELS,
                labelOf = { it.logLevelLabel() },
                onSelect = { level -> onEdit { it.copy(logLevel = level) } },
            )
        }
    }
}

@Composable
private fun GeneralAnalyticsSection(
    draft: GeneralSettingsDraft,
    onEdit: (transform: (GeneralSettingsDraft) -> GeneralSettingsDraft) -> Unit,
) {
    SystemCard {
        Column {
            SystemSectionHeader(stringResource(R.string.arr_radarr_general_section_analytics))
            LabeledSwitch(
                label = stringResource(R.string.arr_radarr_general_analytics),
                supportingText = stringResource(R.string.arr_radarr_general_analytics_support),
                checked = draft.analyticsEnabled,
                onCheckedChange = { checked -> onEdit { it.copy(analyticsEnabled = checked) } },
            )
        }
    }
}

@Composable
private fun GeneralProxySection(draft: GeneralSettingsDraft) {
    SystemCard {
        Column {
            SystemSectionHeader(stringResource(R.string.arr_radarr_general_section_proxy))
            if (draft.proxyEnabled) {
                SystemValueRow(
                    label = stringResource(R.string.arr_radarr_general_proxy_enabled),
                    value = stringResource(
                        R.string.arr_radarr_general_proxy_row,
                        draft.proxyType,
                        draft.proxyHost,
                        draft.proxyPort,
                    ),
                )
            } else {
                SystemValueRow(
                    label = stringResource(R.string.arr_radarr_general_proxy_enabled),
                    value = stringResource(R.string.arr_radarr_general_proxy_disabled),
                )
            }
            HintText(stringResource(R.string.arr_radarr_general_proxy_app_hint))
        }
    }
}

@Composable
private fun HintText(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier,
    )
}

@Composable
private fun AuthenticationType.authMethodLabel(): String = when (this) {
    AuthenticationType.NONE -> stringResource(R.string.arr_radarr_general_auth_method_none)
    AuthenticationType.BASIC -> stringResource(R.string.arr_radarr_general_auth_method_basic)
    AuthenticationType.FORMS -> stringResource(R.string.arr_radarr_general_auth_method_forms)
    AuthenticationType.EXTERNAL -> stringResource(R.string.arr_radarr_general_auth_method_external)
    AuthenticationType.OIDC -> stringResource(R.string.arr_radarr_general_auth_method_oidc)
}

@Composable
private fun AuthenticationRequiredType.authRequiredLabel(): String = when (this) {
    AuthenticationRequiredType.ENABLED ->
        stringResource(R.string.arr_radarr_general_auth_required_enabled)

    AuthenticationRequiredType.DISABLED_FOR_LOCAL_ADDRESSES ->
        stringResource(R.string.arr_radarr_general_auth_required_local_addresses)

    AuthenticationRequiredType.DISABLED_FOR_LOCALHOST ->
        stringResource(R.string.arr_radarr_general_auth_required_localhost)
}

@Composable
private fun CertificateValidationType.certValidationLabel(): String = when (this) {
    CertificateValidationType.ENABLED ->
        stringResource(R.string.arr_radarr_general_cert_validation_enabled)

    CertificateValidationType.DISABLED_FOR_LOCAL_ADDRESSES ->
        stringResource(R.string.arr_radarr_general_cert_validation_local)

    CertificateValidationType.DISABLED ->
        stringResource(R.string.arr_radarr_general_cert_validation_disabled)
}

@Composable
private fun String.logLevelLabel(): String = when (this) {
    "debug" -> stringResource(R.string.arr_radarr_general_log_level_debug)
    "trace" -> stringResource(R.string.arr_radarr_general_log_level_trace)
    else -> stringResource(R.string.arr_radarr_general_log_level_info)
}

internal val SectionMaxWidth = 640.dp
