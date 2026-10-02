package dev.appolon.homelab.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.appolon.homelab.HomelabApp
import dev.appolon.homelab.data.AccountState
import dev.appolon.homelab.data.AccountStore
import dev.appolon.homelab.data.ConfigRepository
import dev.appolon.homelab.data.Favorites
import dev.appolon.homelab.data.ServiceItem
import dev.appolon.homelab.data.HomeUiState
import dev.appolon.homelab.data.PocketIdAccount
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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class HomeViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = ConfigRepository(app)
    private val accountStore = AccountStore(app)
    private val poller = MessagePoller()

    private val _state = MutableStateFlow(HomeUiState())
    val state: StateFlow<HomeUiState> = _state

    private var pollJob: Job? = null
    private var inForeground = false
    private val favoritesMutex = Mutex()

    init {
        viewModelScope.launch {
            favoritesMutex.withLock {
                val favorites = repo.loadFavorites()
                _state.update { it.copy(favorites = favorites) }
            }
        }
        viewModelScope.launch {
            val saved = repo.loadThemeSource()
            _state.update {
                it.copy(themeSource = if (saved == "system") ThemeSource.SYSTEM else ThemeSource.HOMER)
            }
        }
        load()
        initAccounts()
    }

    /** The PocketID base URL to talk to right now (user override or default). */
    private fun effectiveAccountUrl(): String =
        _state.value.accountUrl.ifBlank { PocketId.WELL_KNOWN_BASE_URL }

    /** Resolve the PocketID base URL and restore the saved account state. */
    private fun initAccounts() {
        viewModelScope.launch {
            val stored = repo.loadPocketIdBaseUrl()
            val url = (stored ?: PocketId.WELL_KNOWN_BASE_URL).trimEnd('/')
            var accountState = accountStore.load()
            _state.update {
                it.copy(
                    accountUrl = url,
                    accounts = accountState.accounts,
                    activeAccountId = accountState.activeAccountId,
                )
            }

            // The WebView jar can lose cookies behind our back (provider
            // updates, storage eviction) — re-seed it from the store first.
            val active = accountState.active
            if (active != null &&
                PocketId.sessionCookies(url).isEmpty() &&
                active.sessionCookies.isNotEmpty()
            ) {
                PocketId.installSessionCookies(url, active.sessionCookies)
            }

            // Validate the session; a live one names the user.
            val user = PocketId.fetchUser(url)
            if (user != null) {
                // Heal the stored account with the jar's real cookies: covers
                // legacy entries saved before __Host- matching (empty lists)
                // and sessions refreshed server-side since the last save.
                val jarCookies = PocketId.sessionCookies(url)
                val healed = accountState.accounts.map {
                    if (it.id == user.id && it.sessionCookies != jarCookies) {
                        it.copy(sessionCookies = jarCookies)
                    } else {
                        it
                    }
                }
                if (healed != accountState.accounts) {
                    accountState = AccountState(healed, user.id)
                    accountStore.save(accountState)
                    _state.update { it.copy(accounts = healed) }
                }
            }
            _state.update { it.copy(accountUser = user, activeAccountId = user?.id ?: it.activeAccountId) }
        }
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
                    warmIconCache(fresh)
                    if (inForeground) startPolling()
                }
                .onFailure {
                    _state.update { it.copy(loading = false, refreshing = false, offline = cached != null) }
                }
        }
    }

    /**
     * Decodes all service logos into Coil's memory cache while the user is
     * still on the fresh config, at the exact pixel size rows request. This
     * moves every SVG decode off the first scroll — the cold-pass jank tail.
     */
    private fun warmIconCache(config: dev.appolon.homelab.data.HomerConfig) {
        val context = getApplication<Application>()
        val px = (40f * context.resources.displayMetrics.density).toInt()
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.Default) {
            config.services.asSequence()
                .flatMap { it.items.asSequence() }
                .mapNotNull { it.logo }
                .mapNotNull { logo ->
                    runCatching {
                        coil3.request.ImageRequest.Builder(context)
                            .data(dev.appolon.homelab.data.IconUrlResolver.resolve(logo) ?: return@mapNotNull null)
                            .size(px)
                            .build()
                    }.getOrNull()
                }
                .forEach { request ->
                    runCatching { HomelabApp.imageLoader.execute(request) }
                }
        }
    }

    fun onQueryChange(query: String) {
        _state.update { it.copy(query = query) }
    }

    fun toggleFavorite(item: ServiceItem) {
        viewModelScope.launch {
            favoritesMutex.withLock {
                val favorites = repo.toggleFavorite(Favorites.id(item))
                _state.update { it.copy(favorites = favorites) }
            }
        }
    }

    /** Opens a page in the in-app WebView overlay. */
    fun openWeb(url: String, title: String?) {
        _state.update { it.copy(webTarget = WebTarget(url)) }
    }

    fun closeWeb() {
        _state.update { it.copy(webTarget = null) }
    }

    // ---- PocketID accounts ------------------------------------------------

    fun openAccountSheet() {
        viewModelScope.launch {
            // Refresh the live session identity so the sheet shows the truth.
            val user = PocketId.fetchUser(_state.value.accountUrl)
            _state.update {
                it.copy(accountUser = user, accountSheetOpen = true, accountError = null)
            }
        }
    }

    fun closeAccountSheet() {
        _state.update { it.copy(accountSheetOpen = false) }
    }

    fun openLogin() {
        _state.update { it.copy(accountSheetOpen = false, loginOpen = true, accountError = null) }
    }

    fun closeLogin() {
        cancelDeviceLogin()
        _state.update {
            it.copy(loginOpen = false, accountBusy = false, codeSentTo = null, accountError = null)
        }
    }

    /** User picked a Credential Manager assertion; finish it server-side. */
    fun finishPasskeyLogin(challenge: PocketId.PasskeyChallenge, assertionJson: String) {
        viewModelScope.launch {
            _state.update { it.copy(accountBusy = true, accountError = null) }
            runCatching { PocketId.finishPasskeyLogin(_state.value.accountUrl, challenge, assertionJson) }
                .onSuccess { user -> completeLogin(user) }
                .onFailure { e ->
                    _state.update { it.copy(accountBusy = false, accountError = e.message ?: "Sign-in failed") }
                }
        }
    }

    /** Device cookie from the last emailed-code request, for the exchange. */
    private var oneTimeDeviceCookie: String? = null

    /** Asks PocketID to email a one-time access code to [email]. */
    fun requestOneTimeCodeEmail(email: String) {
        viewModelScope.launch {
            _state.update { it.copy(accountBusy = true, accountError = null) }
            runCatching { PocketId.requestOneTimeCodeEmail(_state.value.accountUrl, email) }
                .onSuccess { cookie ->
                    oneTimeDeviceCookie = cookie
                    _state.update {
                        it.copy(accountBusy = false, codeSentTo = email.trim(), accountError = null)
                    }
                }
                .onFailure { e ->
                    _state.update { it.copy(accountBusy = false, accountError = e.message ?: "Request failed") }
                }
        }
    }

    /** Device login ("other device") polling job, if one is running. */
    private var deviceLoginJob: Job? = null

    /** Starts a device login: shows the code, waits for the other device. */
    fun startDeviceLogin() {
        if (deviceLoginJob?.isActive == true) return
        deviceLoginJob = viewModelScope.launch {
            _state.update { it.copy(accountError = null) }
            runCatching { PocketId.beginDeviceLogin(_state.value.accountUrl) }
                .onFailure { e ->
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    _state.update { it.copy(accountError = e.message ?: "Device login failed") }
                }
                .onSuccess { session ->
                    _state.update { it.copy(deviceLogin = session.request) }
                    while (kotlin.coroutines.coroutineContext.isActive) {
                        // The exchange call blocks server-side until a decision
                        // (or its window closes); poll again right after.
                        try {
                            val user = PocketId.awaitDeviceLogin(_state.value.accountUrl, session)
                            if (user != null) {
                                completeLogin(user)
                                return@launch
                            }
                        } catch (e: kotlinx.coroutines.CancellationException) {
                            // Cancel/close is a normal exit, not an error.
                            throw e
                        } catch (e: Exception) {
                            _state.update {
                                it.copy(
                                    deviceLogin = null,
                                    accountError = e.message ?: "Device login failed",
                                )
                            }
                            return@launch
                        }
                    }
                }
        }
    }

    fun cancelDeviceLogin() {
        deviceLoginJob?.cancel()
        deviceLoginJob = null
        _state.update { it.copy(deviceLogin = null) }
    }

    /** One-time access code login: exchanges the code and bridges the session. */
    fun signInWithOneTimeCode(code: String) {
        viewModelScope.launch {
            _state.update { it.copy(accountBusy = true, accountError = null) }
            runCatching {
                PocketId.signInWithOneTimeCode(_state.value.accountUrl, code, oneTimeDeviceCookie)
            }
                .onSuccess { user -> completeLogin(user) }
                .onFailure { e ->
                    _state.update { it.copy(accountBusy = false, accountError = e.message ?: "Sign-in failed") }
                }
        }
    }

    /**
     * A fresh session for [user] now lives in the cookie jar: record the
     * account (with its cookies) and make it active.
     */
    private suspend fun completeLogin(user: PocketId.PocketIdUser) {
        // Never cancel ourselves: when the device-login loop signs in, it is
        // the job we would be cancelling; self-cancellation would abort this
        // very function at its next suspension point.
        val currentJob = kotlin.coroutines.coroutineContext[Job]
        if (deviceLoginJob != null && deviceLoginJob != currentJob) {
            deviceLoginJob?.cancel()
        }
        deviceLoginJob = null
        oneTimeDeviceCookie = null
        val url = _state.value.accountUrl
        val cookies = PocketId.sessionCookies(url)
        val newState = _state.value.let { s ->
            val account = PocketIdAccount(
                id = user.id,
                username = user.username,
                email = user.email,
                displayName = user.displayName,
                isAdmin = user.isAdmin,
                sessionCookies = cookies,
            )
            val merged = s.accounts.filterNot { it.id == user.id } + account
            AccountState(accounts = merged.sortedBy { it.label.lowercase() }, activeAccountId = user.id)
        }
        accountStore.save(newState)
        _state.update {
            it.copy(
                accounts = newState.accounts,
                activeAccountId = newState.activeAccountId,
                accountUser = user,
                accountBusy = false,
                accountError = null,
                codeSentTo = null,
                deviceLogin = null,
                loginOpen = false,
            )
        }
    }

    /**
     * Swaps the active identity: the current session is saved under its
     * account, the cookie jar is wiped (service sessions belong to the old
     * identity), and the target account's cookies are installed.
     */
    fun switchAccount(id: String) {
        val s = _state.value
        if (id == s.activeAccountId || s.accountBusy) {
            closeAccountSheet()
            return
        }
        val target = s.accounts.firstOrNull { it.id == id } ?: return
        // Accounts saved before per-account cookie capture have no stored
        // session; switching would wipe the jar and strand every service on
        // the PocketID login page. Refuse and ask for a fresh sign-in.
        if (target.sessionCookies.isEmpty()) {
            _state.update {
                it.copy(accountError = "That account needs a fresh sign-in — its session was never saved")
            }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(accountBusy = true, accountError = null) }
            val fresh = _state.value
            val url = fresh.accountUrl.ifBlank { PocketId.WELL_KNOWN_BASE_URL }
            val outgoing = fresh.activeAccount
            val current = PocketId.sessionCookies(url)
            val accountsNow = fresh.accounts
            // Preserve whatever session the WebView currently holds for the
            // outgoing account (it may have been refreshed since sign-in).
            var storeState = AccountState(accountsNow, id)
            if (outgoing != null && current.isNotEmpty()) {
                storeState = storeState.copy(
                    accounts = storeState.accounts.map {
                        if (it.id == outgoing.id) it.copy(sessionCookies = current) else it
                    }
                )
            }
            PocketId.wipeCookieJar()
            PocketId.installSessionCookies(url, target.sessionCookies)
            val user = PocketId.fetchUser(url)
            if (user == null) {
                // Stale stored session: keep the account, sign the app out.
                accountStore.save(storeState.copy(activeAccountId = null))
                _state.update {
                    it.copy(
                        accounts = storeState.accounts,
                        activeAccountId = null,
                        accountUser = null,
                        accountBusy = false,
                        accountError = "That session expired — sign in again",
                    )
                }
            } else {
                accountStore.save(storeState)
                _state.update {
                    it.copy(
                        accounts = storeState.accounts,
                        activeAccountId = id,
                        accountUser = user,
                        accountBusy = false,
                        accountSheetOpen = false,
                    )
                }
            }
        }
    }

    /** Clears the active session; saved accounts remain for quick switching. */
    fun signOut() {
        val s = _state.value
        viewModelScope.launch {
            PocketId.wipeCookieJar()
            accountStore.save(AccountState(s.accounts, activeAccountId = null))
            _state.update { it.copy(activeAccountId = null, accountUser = null, accountSheetOpen = false) }
        }
    }

    /** Deletes a saved account (and its stored session) entirely. */
    fun removeAccount(id: String) {
        val s = _state.value
        viewModelScope.launch {
            val remaining = s.accounts.filterNot { it.id == id }
            val wasActive = s.activeAccountId == id
            if (wasActive) PocketId.wipeCookieJar()
            accountStore.save(AccountState(remaining, if (wasActive) null else s.activeAccountId))
            _state.update {
                it.copy(
                    accounts = remaining,
                    activeAccountId = if (wasActive) null else s.activeAccountId,
                    accountUser = if (wasActive) null else s.accountUser,
                )
            }
        }
    }

    /** Web login fallback: opens the PocketID page in the in-app WebView. */
    fun openWebLogin() {
        _state.update {
            it.copy(loginOpen = false, webTarget = WebTarget(effectiveAccountUrl(), webLogin = true))
        }
    }

    /** The WebView fallback login produced a session; adopt it. */
    fun adoptWebLoginSession() {
        viewModelScope.launch {
            val user = PocketId.fetchUser(_state.value.accountUrl) ?: return@launch
            completeLogin(user)
        }
    }

    fun setSearching(searching: Boolean) {
        _state.update { it.copy(searching = searching, query = if (searching) it.query else "") }
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
                        if (s.message == info) s else s.copy(message = info)
                    }
                }
                delay(messageConfig.refreshInterval.toLong())
            }
        }
    }
}
