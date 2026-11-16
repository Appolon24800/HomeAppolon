package dev.appolon.homelab.arr.sync.scheduler

import dev.appolon.homelab.arr.core.AppType
import dev.appolon.homelab.arr.core.ServerId
import dev.appolon.homelab.arr.data.ArrServer
import dev.appolon.homelab.arr.net.ProxyConfig
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProxyAwarePollAdapterTest {
    private val server = ArrServer(ServerId("one"), AppType.RADARR, "Movies", "http://example.test/", "encrypted-key")

    private fun delegate(block: suspend (ArrServer) -> PollResult) = object : PollAdapter {
        override suspend fun poll(server: ArrServer) = block(server)
    }

    @Test
    fun firstPollReadsTheSavedProxyBeforeContactingTheServer() = runTest {
        val calls = mutableListOf<String>()
        val proxy = ProxyConfig(ProxyConfig.Type.SOCKS5, "proxy.test", 9050)
        val adapter = ProxyAwarePollAdapter(delegate {
            calls += "poll"
            assertEquals(proxy, it.proxy)
            PollResult()
        }) { calls += "settings"; proxy }
        adapter.poll(server)
        assertEquals(listOf("settings", "poll"), calls)
    }

    @Test
    fun explicitDirectPolicyDoesNotReadOrInheritTheGlobalProxy() = runTest {
        val adapter = ProxyAwarePollAdapter(delegate {
            assertEquals(ProxyConfig(), it.proxy)
            PollResult()
        }) { error("A server override must win") }
        adapter.poll(server.copy(proxy = ProxyConfig()))
    }

    @Test
    fun unavailableProxySettingsDoNotAllowADirectRequest() = runTest {
        var polled = false
        val adapter = ProxyAwarePollAdapter(delegate { polled = true; PollResult() }) {
            throw IOException("Settings unavailable")
        }
        try {
            adapter.poll(server)
            throw AssertionError("The settings failure must propagate")
        } catch (_: IOException) {
            assertFalse(polled)
        }
    }

    @Test
    fun cancelledWorkerCannotPublishAResultFromAnAdapterThatSwallowedCancellation() = runTest {
        var returned = false
        val adapter = ProxyAwarePollAdapter(delegate {
            currentCoroutineContext().cancel()
            PollResult()
        }) { ProxyConfig() }
        val job = launch {
            adapter.poll(server)
            returned = true
        }
        job.join()
        assertTrue(job.isCancelled)
        assertFalse(returned)
    }

    @Test
    fun cancellationFromProxySettingsPropagates() = runTest {
        val adapter = ProxyAwarePollAdapter(delegate { error("Must not poll") }) {
            throw CancellationException("Worker stopped")
        }
        try {
            adapter.poll(server)
            throw AssertionError("Cancellation must propagate")
        } catch (_: CancellationException) {
            // Expected.
        }
    }
}
