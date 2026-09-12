package dev.appolon.homelab.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.appolon.homelab.data.ConfigRepository
import dev.appolon.homelab.data.HomeUiState
import dev.appolon.homelab.data.WebTarget
import dev.appolon.homelab.data.MessageInfo
import dev.appolon.homelab.data.MessagePoller
import dev.appolon.homelab.data.PocketId
import dev.appolon.homelab.ui.theme.ThemeSource
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class HomeViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = ConfigRepository(app)
    private val poller = MessagePoller()

    private val _state = MutableStateFlow(HomeUiState())
    val state: StateFlow<HomeUiState> = _state

    private var pollJob: Job? = null
    private var inForeground = false

    init {
        viewModelScope.launch {
            val saved = repo.loadThemeSource()
            _state.update {
                it.copy(themeSource = if (saved == "system") ThemeSource.SYSTEM else ThemeSource.HOMER)
            }
        }
        load()
    }

    fun toggleTheme() {
        val next = if (_state.value.themeSource == ThemeSource.HOMER) ThemeSource.SYSTEM else ThemeSource.HOMER
        _state.update { it.copy(themeSource = next) }
        viewModelScope.launch { repo.saveThemeSource(if (next == ThemeSource.SYSTEM) "system" else "homer") }
    }

    /** Initial load and pull-to-refresh. `showSpinner` drives the refresh indicator. */
    fun load(showSpinner: Boolean = false) {
        viewModelScope.launch {
            val cached = repo.loadCached()
            if (cached != null) {
                _state.update { it.copy(config = cached, loading = false, refreshing = showSpinner) }
            } else if (!showSpinner) {
                _state.update { it.copy(loading = true) }
            }

            repo.fetch()
                .onSuccess { fresh ->
                    _state.update {
                        it.copy(
                            config = fresh,
                            loading = false,
                            refreshing = false,
                            offline = false,
                        )
                    }
                    if (inForeground) startPolling()
                }
                .onFailure {
                    _state.update { it.copy(loading = false, refreshing = false, offline = cached != null) }
                }
        }
    }

    fun onQueryChange(query: String) {
        _state.update { it.copy(query = query) }
    }

    /** Opens a page in the in-app WebView overlay. */
    fun openWeb(url: String, title: String?) {
        _state.update { it.copy(webTarget = WebTarget(url, title)) }
    }

    fun closeWeb() {
        _state.update { it.copy(webTarget = null) }
    }

    // ---- PocketID account management -------------------------------------

    fun openAccount() {
        viewModelScope.launch {
            val stored = repo.loadPocketIdBaseUrl()
            val url = (stored ?: PocketId.WELL_KNOWN_BASE_URL).trimEnd('/')
            val label = repo.loadPocketIdAccount() ?: ""
            _state.update { it.copy(accountUrl = url, pocketIdAccount = label) }
            refreshPocketIdStatus()
        }
    }

    fun closeAccount() {
        _state.update { it.copy(accountUrl = null) }
    }

    /** Cookie check + live identity fetch; safe to call repeatedly. */
    fun refreshPocketIdStatus() {
        val s = _state.value
        val url = s.accountUrl ?: return
        viewModelScope.launch {
            val user = PocketId.fetchUser(url)
            _state.update {
                it.copy(
                    pocketIdSignedIn = user != null,
                    pocketIdUser = user?.displayName ?: user?.username,
                )
            }
        }
    }

    /** One-time access code login: exchanges the code and bridges the session. */
    fun signInWithOneTimeCode(code: String) {
        val s = _state.value
        val url = s.accountUrl ?: return
        viewModelScope.launch {
            _state.update { it.copy(pocketIdBusy = true, pocketIdError = null) }
            runCatching { PocketId.signInWithOneTimeCode(url, code) }
                .onSuccess { user ->
                    _state.update {
                        it.copy(
                            pocketIdSignedIn = true,
                            pocketIdUser = user.displayName ?: user.username,
                            pocketIdAccount = user.displayName ?: user.username,
                            pocketIdBusy = false,
                        )
                    }
                    repo.savePocketIdAccount(user.displayName ?: user.username)
                }
                .onFailure { e ->
                    _state.update {
                        it.copy(
                            pocketIdBusy = false,
                            pocketIdError = e.message ?: "Sign-in failed",
                        )
                    }
                }
        }
    }

    /** User edited the PocketID base URL; persist and re-check the session. */
    fun setPocketIdBaseUrl(url: String) {
        val cleaned = url.trim().trimEnd('/')
        if (cleaned.isBlank()) return
        viewModelScope.launch { repo.savePocketIdBaseUrl(cleaned) }
        _state.update { it.copy(accountUrl = cleaned) }
        refreshPocketIdStatus()
    }

    fun openPocketIdSignIn() {
        val s = _state.value
        val url = s.accountUrl ?: PocketId.WELL_KNOWN_BASE_URL
        _state.update { it.copy(webTarget = WebTarget(url, "PocketID", pocketIdSignIn = true)) }
    }

    fun signOutPocketId() {
        val s = _state.value
        val url = s.accountUrl ?: PocketId.WELL_KNOWN_BASE_URL
        PocketId.clearSession(url) {
            viewModelScope.launch { repo.savePocketIdAccount(null) }
            _state.update { it.copy(pocketIdSignedIn = false, pocketIdUser = null, pocketIdAccount = "") }
        }
    }

    fun setSearching(searching: Boolean) {
        _state.update { if (searching) it.copy(searching = true) else it.copy(searching = false, query = "") }
    }

    fun dismissMessage() {
        _state.update { it.copy(messageDismissed = true) }
    }

    fun onForeground() {
        inForeground = true
        startPolling()
    }

    fun onBackground() {
        inForeground = false
        pollJob?.cancel()
        pollJob = null
    }

    private fun startPolling() {
        pollJob?.cancel()
        val messageConfig = _state.value.config?.message ?: return
        pollJob = viewModelScope.launch {
            while (isActive) {
                val info = poller.fetch(messageConfig)
                if (info != null) {
                    _state.update { s ->
                        if (s.message == info) s else s.copy(message = info, messageDismissed = false)
                    }
                }
                delay(messageConfig.refreshInterval.toLong())
            }
        }
    }
}
