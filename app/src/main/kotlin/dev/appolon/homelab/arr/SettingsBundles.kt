package dev.appolon.homelab.arr

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.appolon.homelab.arr.core.ArrError
import dev.appolon.homelab.arr.core.ServerId
import dev.appolon.homelab.arr.net.arrError
import dev.appolon.homelab.arr.ui.lce.LceState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Cached clients belong to one version of a server connection. */
internal class SettingsBundles<T>(private val connect: suspend (ServerId) -> T?) {
    private var versions = emptyMap<ServerId, Long>()
    private var states by mutableStateOf<Map<ServerId, LceState<T>>>(emptyMap())

    fun stateOf(id: ServerId): LceState<T> = states[id] ?: LceState.Loading

    fun get(id: ServerId): T? = (states[id] as? LceState.Content)?.data

    fun isReady(id: ServerId): Boolean = states[id] is LceState.Content

    suspend fun connectNow(id: ServerId) {
        val version = versions[id] ?: return
        states = states + (id to LceState.Loading)
        val result = try {
            withContext(Dispatchers.IO) { connect(id) }?.let { LceState.Content(it) }
                ?: LceState.Error(ArrError.Unknown(null))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            LceState.Error(e.arrError())
        }
        // An edit or deletion while connecting retires this result.
        if (versions[id] == version) states = states + (id to result)
    }

    fun retainVersions(current: Map<ServerId, Long>) {
        states = states.filterKeys { it in current && current[it] == versions[it] }
        versions = current
    }
}
