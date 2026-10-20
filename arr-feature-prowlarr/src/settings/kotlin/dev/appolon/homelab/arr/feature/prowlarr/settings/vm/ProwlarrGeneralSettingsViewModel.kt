package dev.appolon.homelab.arr.feature.prowlarr.settings.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.appolon.homelab.arr.api.prowlarr.model.AuthenticationRequiredType
import dev.appolon.homelab.arr.api.prowlarr.model.AuthenticationType
import dev.appolon.homelab.arr.api.prowlarr.model.DevelopmentConfigResource
import dev.appolon.homelab.arr.api.prowlarr.model.HostConfigResource
import dev.appolon.homelab.arr.api.prowlarr.model.LocalizationOption
import dev.appolon.homelab.arr.api.prowlarr.model.UiConfigResource
import dev.appolon.homelab.arr.core.ArrError
import dev.appolon.homelab.arr.core.ArrResult
import dev.appolon.homelab.arr.feature.prowlarr.settings.data.ProwlarrSettingsRepository
import dev.appolon.homelab.arr.feature.prowlarr.settings.data.WriteOutcome
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
 * Editable draft of the host settings slice: bind address, port, URL base,
 * authentication state, log level, and analytics. Text and number fields
 * live as strings so partially typed values never crash the editor; parsing
 * happens at validation time. The password field starts blank and a blank
 * value means "keep the current password": the wire model omits the field,
 * which the server reads as unchanged.
 */
data class HostSettingsDraft(
    val instanceName: String = "",
    val bindAddress: String = "*",
    val port: String = "9696",
    val urlBase: String = "",
    val launchBrowser: Boolean = false,
    val authenticationMethod: AuthenticationType = AuthenticationType.NONE,
    val authenticationRequired: AuthenticationRequiredType = AuthenticationRequiredType.ENABLED,
    val username: String = "",
    val password: String = "",
    val logLevel: String = INFO_LOG_LEVEL,
    val analyticsEnabled: Boolean = false,
) {
    constructor(config: HostConfigResource) : this(
        instanceName = config.instanceName.orEmpty(),
        bindAddress = config.bindAddress ?: "*",
        port = config.port.toString(),
        urlBase = config.urlBase.orEmpty(),
        launchBrowser = config.launchBrowser,
        authenticationMethod = config.authenticationMethod,
        authenticationRequired = config.authenticationRequired,
        username = config.username.orEmpty(),
        password = "",
        logLevel = config.logLevel ?: INFO_LOG_LEVEL,
        analyticsEnabled = config.analyticsEnabled,
    )

    /** True for the methods Prowlarr authenticates with username and password. */
    fun usesCredentials(): Boolean =
        authenticationMethod == AuthenticationType.BASIC ||
            authenticationMethod == AuthenticationType.FORMS

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
        authenticationMethod = authenticationMethod,
        authenticationRequired = authenticationRequired,
        username = if (usesCredentials()) username else null,
        password = if (usesCredentials() && password.isNotBlank()) password else null,
        passwordConfirmation = null,
        logLevel = logLevel,
        analyticsEnabled = analyticsEnabled,
    )

    companion object {
        const val INFO_LOG_LEVEL = "info"
        val LOG_LEVELS = listOf("info", "debug", "trace")
    }
}

/**
 * Editable draft of the development settings slice: the verbose logging
 * switches used while debugging a server.
 */
data class DevelopmentSettingsDraft(
    val logSql: Boolean = false,
    val logIndexerResponse: Boolean = false,
    val filterSentryEvents: Boolean = false,
) {
    constructor(config: DevelopmentConfigResource) : this(
        logSql = config.logSql,
        logIndexerResponse = config.logIndexerResponse,
        filterSentryEvents = config.filterSentryEvents,
    )

    fun applyOnto(config: DevelopmentConfigResource): DevelopmentConfigResource = config.copy(
        logSql = logSql,
        logIndexerResponse = logIndexerResponse,
        filterSentryEvents = filterSentryEvents,
    )
}

/** Content payload of the general settings LCE state. */
data class ProwlarrGeneralSettingsContent(
    val hostDraft: HostSettingsDraft,
    val developmentDraft: DevelopmentSettingsDraft,
    val uiLanguage: String?,
    val localizationOptions: List<LocalizationOption>,
    val hostDirty: Boolean,
    val developmentDirty: Boolean,
    val cultureDirty: Boolean,
    val saving: Boolean,
    val portError: Boolean,
    val pendingActions: Int,
)

/** Transient save results, mapped to strings by the screen. */
enum class GeneralSettingsNotice { HOST_SAVED, HOST_SAVED_QUEUED, CULTURE_SAVED, CULTURE_SAVED_QUEUED }

/**
 * General settings screen state: host config (bind address, port, url
 * base), security and authentication state, development config (extra log
 * lines, analytics filtering), and the UI culture picker over the
 * localization options. Each slice saves through its own config endpoint;
 * offline saves apply optimistically and queue for replay.
 */
class ProwlarrGeneralSettingsViewModel(
    private val repository: ProwlarrSettingsRepository,
) : ViewModel() {

    data class UiState(
        val loaded: Boolean = false,
        val loadError: ArrError? = null,
        val refreshing: Boolean = false,
        val hostDraft: HostSettingsDraft = HostSettingsDraft(),
        val developmentDraft: DevelopmentSettingsDraft = DevelopmentSettingsDraft(),
        val uiLanguage: String? = null,
        val localizationOptions: List<LocalizationOption> = emptyList(),
        val hostDirty: Boolean = false,
        val developmentDirty: Boolean = false,
        val cultureDirty: Boolean = false,
        val saving: Boolean = false,
        val pendingActions: Int = 0,
        val notice: GeneralSettingsNotice? = null,
        val writeError: ArrError? = null,
    )

    private var loadedHostConfig: HostConfigResource = HostConfigResource()
    private var loadedDevelopmentConfig: DevelopmentConfigResource = DevelopmentConfigResource()

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    val lce: StateFlow<LceState<ProwlarrGeneralSettingsContent>> = state
        .map { s ->
            when {
                s.loadError != null -> LceState.Error(s.loadError)
                !s.loaded -> LceState.Loading
                else -> LceState.Content(
                    ProwlarrGeneralSettingsContent(
                        hostDraft = s.hostDraft,
                        developmentDraft = s.developmentDraft,
                        uiLanguage = s.uiLanguage,
                        localizationOptions = s.localizationOptions,
                        hostDirty = s.hostDirty,
                        developmentDirty = s.developmentDirty,
                        cultureDirty = s.cultureDirty,
                        saving = s.saving,
                        portError = s.hostDraft.port.isNotBlank() && s.hostDraft.parsedPort() == null,
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
                    loadedHostConfig = config
                    // A background refresh never camps on the user's unsaved
                    // edits; it only re-seeds the draft when nothing is dirty.
                    _state.update { s ->
                        if (s.hostDirty) {
                            s.copy(loaded = true, loadError = null)
                        } else {
                            s.copy(
                                loaded = true,
                                loadError = null,
                                hostDraft = HostSettingsDraft(config),
                            )
                        }
                    }
                }
            }
        }
        viewModelScope.launch {
            repository.observeDevelopmentConfig().collect { config ->
                if (config != null) {
                    loadedDevelopmentConfig = config
                    _state.update { s ->
                        if (s.developmentDirty) {
                            s
                        } else {
                            s.copy(
                                loaded = true,
                                developmentDraft = DevelopmentSettingsDraft(config),
                            )
                        }
                    }
                }
            }
        }
        viewModelScope.launch {
            repository.observeUiConfig().collect { config ->
                if (config != null) {
                    _state.update { s ->
                        if (s.cultureDirty) {
                            s
                        } else {
                            s.copy(loaded = true, uiLanguage = config.uiLanguage)
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
                is ArrResult.Ok -> when (val options = repository.localizationOptions()) {
                    is ArrResult.Ok -> _state.update {
                        it.copy(
                            refreshing = false,
                            loaded = true,
                            localizationOptions = options.value,
                        )
                    }

                    is ArrResult.Err -> _state.update { it.copy(refreshing = false, loaded = true) }
                }

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

    fun editHost(transform: (HostSettingsDraft) -> HostSettingsDraft) {
        _state.update { it.copy(hostDraft = transform(it.hostDraft), hostDirty = true) }
    }

    fun editDevelopment(transform: (DevelopmentSettingsDraft) -> DevelopmentSettingsDraft) {
        _state.update {
            it.copy(developmentDraft = transform(it.developmentDraft), developmentDirty = true)
        }
    }

    fun setUiLanguage(language: String?) {
        _state.update { it.copy(uiLanguage = language, cultureDirty = true) }
    }

    fun saveHost() {
        val current = _state.value
        if (!current.hostDirty || current.saving) return
        if (current.hostDraft.port.isNotBlank() && current.hostDraft.parsedPort() == null) return
        viewModelScope.launch {
            _state.update { it.copy(saving = true) }
            when (
                val result = repository.saveHostConfig(
                    current.hostDraft.applyOnto(loadedHostConfig),
                )
            ) {
                is ArrResult.Ok -> _state.update {
                    it.copy(
                        saving = false,
                        hostDirty = false,
                        notice = if (result.value is WriteOutcome.Queued) {
                            GeneralSettingsNotice.HOST_SAVED_QUEUED
                        } else {
                            GeneralSettingsNotice.HOST_SAVED
                        },
                        writeError = null,
                    )
                }

                is ArrResult.Err -> _state.update {
                    it.copy(saving = false, writeError = result.error)
                }
            }
        }
    }

    fun saveDevelopment() {
        val current = _state.value
        if (!current.developmentDirty || current.saving) return
        viewModelScope.launch {
            _state.update { it.copy(saving = true) }
            when (
                val result = repository.saveDevelopmentConfig(
                    current.developmentDraft.applyOnto(loadedDevelopmentConfig),
                )
            ) {
                is ArrResult.Ok -> _state.update {
                    it.copy(saving = false, developmentDirty = false, writeError = null)
                }

                is ArrResult.Err -> _state.update {
                    it.copy(saving = false, writeError = result.error)
                }
            }
        }
    }

    fun saveCulture() {
        val current = _state.value
        if (!current.cultureDirty || current.saving) return
        viewModelScope.launch {
            _state.update { it.copy(saving = true) }
            val config = (repository.cachedUiConfig() ?: UiConfigResource())
                .copy(uiLanguage = current.uiLanguage)
            when (val result = repository.saveUiConfig(config)) {
                is ArrResult.Ok -> _state.update {
                    it.copy(
                        saving = false,
                        cultureDirty = false,
                        notice = if (result.value is WriteOutcome.Queued) {
                            GeneralSettingsNotice.CULTURE_SAVED_QUEUED
                        } else {
                            GeneralSettingsNotice.CULTURE_SAVED
                        },
                        writeError = null,
                    )
                }

                is ArrResult.Err -> _state.update {
                    it.copy(saving = false, writeError = result.error)
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
