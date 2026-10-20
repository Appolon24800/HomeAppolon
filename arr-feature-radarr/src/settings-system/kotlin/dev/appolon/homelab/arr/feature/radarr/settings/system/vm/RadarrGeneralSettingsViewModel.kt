package dev.appolon.homelab.arr.feature.radarr.settings.system.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.appolon.homelab.arr.api.radarr.model.AuthenticationRequiredType
import dev.appolon.homelab.arr.api.radarr.model.AuthenticationType
import dev.appolon.homelab.arr.api.radarr.model.CertificateValidationType
import dev.appolon.homelab.arr.api.radarr.model.HostConfigResource
import dev.appolon.homelab.arr.core.ArrError
import dev.appolon.homelab.arr.core.ArrResult
import dev.appolon.homelab.arr.feature.radarr.library.data.WriteOutcome
import dev.appolon.homelab.arr.feature.radarr.settings.system.data.RadarrSettingsSystemRepository
import dev.appolon.homelab.arr.ui.lce.LceState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Editable draft of the general settings slice of the host config. Text and
 * number fields live as strings so partially typed values never crash the
 * editor; parsing happens at validation time. The password field starts
 * blank and a blank value means "keep the current password": the wire model
 * omits the field, which the server reads as unchanged.
 */
data class GeneralSettingsDraft(
    val instanceName: String = "",
    val bindAddress: String = "*",
    val port: String = "7878",
    val urlBase: String = "",
    val launchBrowser: Boolean = false,
    val enableSsl: Boolean = false,
    val sslPort: String = "9898",
    val sslCertPath: String = "",
    val sslKeyPath: String = "",
    val authenticationMethod: AuthenticationType = AuthenticationType.NONE,
    val authenticationRequired: AuthenticationRequiredType = AuthenticationRequiredType.ENABLED,
    val username: String = "",
    val password: String = "",
    val certificateValidation: CertificateValidationType = CertificateValidationType.ENABLED,
    val logLevel: String = INFO_LOG_LEVEL,
    val analyticsEnabled: Boolean = false,
    val proxyEnabled: Boolean = false,
    val proxyType: String = "",
    val proxyHost: String = "",
    val proxyPort: Int = 0,
) {
    constructor(config: HostConfigResource) : this(
        instanceName = config.instanceName.orEmpty(),
        bindAddress = config.bindAddress ?: "*",
        port = config.port.toString(),
        urlBase = config.urlBase.orEmpty(),
        launchBrowser = config.launchBrowser,
        enableSsl = config.enableSsl,
        sslPort = config.sslPort.toString(),
        sslCertPath = config.sslCertPath.orEmpty(),
        sslKeyPath = config.sslKeyPath.orEmpty(),
        authenticationMethod = config.authenticationMethod ?: AuthenticationType.NONE,
        authenticationRequired = config.authenticationRequired ?: AuthenticationRequiredType.ENABLED,
        username = config.username.orEmpty(),
        password = "",
        certificateValidation = config.certificateValidation ?: CertificateValidationType.ENABLED,
        logLevel = config.logLevel ?: INFO_LOG_LEVEL,
        analyticsEnabled = config.analyticsEnabled,
        proxyEnabled = config.proxyEnabled,
        proxyType = config.proxyType?.toString().orEmpty(),
        proxyHost = config.proxyHostname.orEmpty(),
        proxyPort = config.proxyPort,
    )

    /** True when the authentication method carries username and password. */
    fun usesCredentials(): Boolean = authenticationMethod.usesCredentials()

    /** The parsed port, or null when the field is not a valid port number. */
    fun parsedPort(): Int? = port.toIntOrNull()?.takeIf { it in 1..65535 }

    /**
     * Applies the draft onto the loaded config. Secrets never echo back: a
     * blank password keeps the server's value (omitted on the wire), and
     * switching authentication off clears the credential fields.
     */
    fun applyOnto(config: HostConfigResource): HostConfigResource = config.copy(
        instanceName = instanceName,
        bindAddress = bindAddress.ifBlank { "*" },
        port = parsedPort() ?: config.port,
        urlBase = urlBase,
        launchBrowser = launchBrowser,
        enableSsl = enableSsl,
        sslPort = sslPort.toIntOrNull() ?: config.sslPort,
        authenticationMethod = authenticationMethod,
        authenticationRequired = authenticationRequired,
        username = if (usesCredentials()) username else null,
        password = if (usesCredentials() && password.isNotBlank()) password else null,
        passwordConfirmation = null,
        certificateValidation = certificateValidation,
        logLevel = logLevel,
        analyticsEnabled = analyticsEnabled,
    )

    companion object {
        const val INFO_LOG_LEVEL = "info"
        val LOG_LEVELS = listOf("info", "debug", "trace")
    }
}

/** True for the methods Radarr authenticates with username and password. */
fun AuthenticationType.usesCredentials(): Boolean =
    this == AuthenticationType.BASIC || this == AuthenticationType.FORMS

/** Content payload of the general settings LCE state. */
data class RadarrGeneralSettingsContent(
    val draft: GeneralSettingsDraft,
    val dirty: Boolean,
    val saving: Boolean,
    val portError: Boolean,
    val pendingActions: Int,
)

/** Transient save results, mapped to strings by the screen. */
enum class GeneralSettingsNotice { SAVED, SAVED_QUEUED }

/**
 * General settings screen state: host, security, logging, analytics, and
 * the server-side proxy display. Loads the host config cache-first through
 * the repository; every edit updates the draft and save PUTs the whole
 * config. Offline saves apply optimistically and queue for replay.
 */
class RadarrGeneralSettingsViewModel(
    private val repository: RadarrSettingsSystemRepository,
) : ViewModel() {

    data class UiState(
        val loaded: Boolean = false,
        val loadError: ArrError? = null,
        val refreshing: Boolean = false,
        val draft: GeneralSettingsDraft = GeneralSettingsDraft(),
        val dirty: Boolean = false,
        val saving: Boolean = false,
        val pendingActions: Int = 0,
        val notice: GeneralSettingsNotice? = null,
        val writeError: ArrError? = null,
    )

    private var loadedConfig: HostConfigResource = HostConfigResource()

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    val lce: StateFlow<LceState<RadarrGeneralSettingsContent>> = state
        .map { s ->
            when {
                s.loadError != null -> LceState.Error(s.loadError)
                !s.loaded -> LceState.Loading
                else -> LceState.Content(
                    RadarrGeneralSettingsContent(
                        draft = s.draft,
                        dirty = s.dirty,
                        saving = s.saving,
                        portError = s.draft.port.isNotBlank() && s.draft.parsedPort() == null,
                        pendingActions = s.pendingActions,
                    ),
                    refreshing = s.refreshing,
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LceState.Loading)

    init {
        viewModelScope.launch {
            repository.observeHostConfig().collect { config ->
                if (config != null) {
                    loadedConfig = config
                    // A background refresh never camps on the user's unsaved
                    // edits; it only re-seeds the draft when nothing is dirty.
                    _state.update { s ->
                        if (s.dirty) {
                            s.copy(loaded = true, loadError = null)
                        } else {
                            s.copy(loaded = true, loadError = null, draft = GeneralSettingsDraft(config))
                        }
                    }
                }
            }
        }
        viewModelScope.launch {
            repository.queuedCount().collect { pending ->
                _state.update { it.copy(pendingActions = pending) }
            }
        }
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(refreshing = true) }
            when (val result = repository.refreshGeneralIfStale()) {
                is ArrResult.Ok -> _state.update { it.copy(refreshing = false) }

                is ArrResult.Err -> _state.update {
                    it.copy(
                        loaded = true,
                        loadError = if (!it.loaded) result.error else null,
                        writeError = if (it.loaded) result.error else null,
                        refreshing = false,
                    )
                }
            }
        }
    }

    fun edit(transform: (GeneralSettingsDraft) -> GeneralSettingsDraft) {
        _state.update { it.copy(draft = transform(it.draft), dirty = true) }
    }

    fun save() {
        val current = _state.value
        if (!current.dirty || current.saving) return
        if (current.draft.port.isNotBlank() && current.draft.parsedPort() == null) return
        viewModelScope.launch {
            _state.update { it.copy(saving = true) }
            when (val result = repository.saveHostConfig(current.draft.applyOnto(loadedConfig))) {
                is ArrResult.Ok -> _state.update {
                    it.copy(
                        saving = false,
                        dirty = false,
                        notice = if (result.value is WriteOutcome.Queued) {
                            GeneralSettingsNotice.SAVED_QUEUED
                        } else {
                            GeneralSettingsNotice.SAVED
                        },
                        writeError = null,
                    )
                }

                is ArrResult.Err -> _state.update {
                    it.copy(saving = false, notice = null, writeError = result.error)
                }
            }
        }
    }

    fun consumeNotice() {
        _state.update { it.copy(notice = null) }
    }

    fun consumeWriteError() {
        _state.update { it.copy(writeError = null) }
    }
}
