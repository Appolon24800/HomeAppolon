package dev.appolon.homelab.arr.servers.vm

import kotlinx.coroutines.Dispatchers
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.appolon.homelab.arr.core.ArrError
import dev.appolon.homelab.arr.core.ServerId
import dev.appolon.homelab.arr.data.ArrServer
import dev.appolon.homelab.arr.data.ServerRegistry
import dev.appolon.homelab.arr.data.secrets.ArrSecrets
import dev.appolon.homelab.arr.net.ServerEndpoint
import dev.appolon.homelab.arr.net.TrustSpec
import dev.appolon.homelab.arr.net.arrError
import dev.appolon.homelab.arr.servers.logic.ServerForm
import dev.appolon.homelab.arr.servers.net.ConnectionOutcome
import dev.appolon.homelab.arr.servers.net.ConnectionTester
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Add/edit sheet state. New servers pass a null [serverId]; existing ones
 * load their stored row, decrypting secrets so the fields are editable.
 * Saving re-encrypts through the registry (it encrypts non-envelope values),
 * and preserves the stored order, proxy, and trust by copying them from the
 * original row with zero-valued order/createdAt, which the registry keeps.
 */
class ServerEditViewModel(
    private val registry: ServerRegistry,
    private val secrets: ArrSecrets,
    private val tester: ConnectionTester,
    private val serverId: ServerId?,
) : ViewModel() {

    sealed interface TestState {
        data object Idle : TestState

        data class Succeeded(val version: String?) : TestState

        data class Failed(val error: ArrError) : TestState
    }

    data class UiState(
        val form: ServerForm = ServerForm(),
        val original: ArrServer? = null,
        val loading: Boolean = false,
        val loadError: ArrError? = null,
        val showErrors: Boolean = false,
        val testing: Boolean = false,
        val test: TestState = TestState.Idle,
        val saving: Boolean = false,
        val saveError: ArrError? = null,
        val saved: Boolean = false,
    )

    private val _state = MutableStateFlow(UiState(loading = serverId != null))
    val state: StateFlow<UiState> = _state.asStateFlow()

    init {
        val id = serverId
        if (id != null) {
            viewModelScope.launch {
                runCatching {
                    val server = registry.byId(id)
                        ?: error("server ${id.value} is no longer registered")
                    val form = ServerForm(
                        name = server.name,
                        type = server.type,
                        baseUrl = server.baseUrl,
                        apiKey = secrets.decrypt(server.apiKeyEncrypted),
                        basicAuthUser = server.basicAuthUser.orEmpty(),
                        basicAuthPassword = server.basicAuthPasswordEncrypted
                            ?.let { secrets.decrypt(it) }
                            .orEmpty(),
                    )
                    server to form
                }.onSuccess { (server, form) ->
                    _state.update { it.copy(loading = false, form = form, original = server) }
                }.onFailure { t ->
                    _state.update { it.copy(loading = false, loadError = t.arrError()) }
                }
            }
        }
    }

    fun update(transform: (ServerForm) -> ServerForm) {
        _state.update { it.copy(form = transform(it.form)) }
    }

    /**
     * Runs one GET against the app-type status endpoint with whatever is
     * currently typed, reusing the stored proxy/trust when editing so the
     * probe matches how real traffic would leave the device. The form is
     * snapshotted first and the probe runs once outside the state commit:
     * [MutableStateFlow.update] retries its lambda on contention, so a
     * network call inside it could fire twice.
     */
    fun testConnection() {
        val current = _state.value
        val form = current.form
        if (form.parsedUrl() == null) {
            _state.update { it.copy(showErrors = true) }
            return
        }
        val endpoint = ServerEndpoint(
            serverId = TEST_ID,
            baseUrl = ServerEndpoint.normalizeBaseUrl(form.baseUrl),
            apiKey = form.apiKey.trim(),
            basicAuthUser = form.basicAuthUser.trim().takeIf { it.isNotEmpty() },
            basicAuthPassword = form.basicAuthPassword.takeIf { it.isNotEmpty() },
            proxy = current.original?.proxy,
            trust = current.original?.trust ?: TrustSpec.System,
        )
        viewModelScope.launch {
            _state.update { it.copy(testing = true) }
            val test = try {
                when (val outcome = tester.test(endpoint, form.type)) {
                    is ConnectionOutcome.Success -> TestState.Succeeded(outcome.version)
                    is ConnectionOutcome.Failure -> TestState.Failed(outcome.error)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                TestState.Failed(t.arrError())
            }
            _state.update { s -> s.copy(testing = false, test = test) }
        }
    }

    fun save() {
        val current = _state.value
        if (!current.form.isValid) {
            _state.update { it.copy(showErrors = true) }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(saving = true) }
            runCatching {
                val form = _state.value.form
                val original = current.original
                registry.upsert(
                    ArrServer(
                        id = original?.id ?: ServerId(java.util.UUID.randomUUID().toString()),
                        type = form.type,
                        name = form.name.trim(),
                        baseUrl = form.toBaseUrl().orEmpty(),
                        apiKeyEncrypted = secrets.encrypt(form.apiKey.trim()),
                        basicAuthUser = form.basicAuthUser.trim().takeIf { it.isNotEmpty() },
                        basicAuthPasswordEncrypted = form.basicAuthPassword
                            ?.takeIf { it.isNotEmpty() }
                            ?.let { secrets.encrypt(it) },
                        proxy = original?.proxy,
                        trust = original?.trust,
                    ),
                )
            }.onSuccess {
                _state.update { it.copy(saving = false, saved = true) }
            }.onFailure { t ->
                _state.update { it.copy(saving = false, saveError = t.arrError()) }
            }
        }
    }

    private companion object {
        val TEST_ID = ServerId("__server_edit_test__")
    }
}
