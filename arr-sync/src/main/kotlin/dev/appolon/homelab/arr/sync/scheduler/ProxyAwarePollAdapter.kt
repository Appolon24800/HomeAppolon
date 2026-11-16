package dev.appolon.homelab.arr.sync.scheduler

import dev.appolon.homelab.arr.data.ArrServer
import dev.appolon.homelab.arr.net.ProxyConfig
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** Reads persisted proxy policy before a cold-started worker makes its first request. */
class ProxyAwarePollAdapter(
    private val delegate: PollAdapter,
    private val defaultProxy: suspend () -> ProxyConfig,
) : PollAdapter {
    override suspend fun poll(server: ArrServer): PollResult {
        currentCoroutineContext().ensureActive()
        val configured = server.copy(proxy = server.proxy ?: defaultProxy())
        return delegate.poll(configured).also { currentCoroutineContext().ensureActive() }
    }
}
