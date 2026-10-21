package dev.appolon.homelab.arr.sync.live

import dev.appolon.homelab.arr.core.ServerId
import dev.appolon.homelab.arr.net.ServerEndpoint
import io.reactivex.rxjava3.plugins.RxJavaPlugins
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SignalRHubPortIntegrationTest {
    @Test
    fun `stop during rejected negotiation does not escape through Rx callbacks`() = runBlocking {
        val errors = CopyOnWriteArrayList<Throwable>()
        val previous = RxJavaPlugins.getErrorHandler()
        RxJavaPlugins.setErrorHandler { errors += it }
        val server = MockWebServer()
        server.enqueue(MockResponse().setResponseCode(503).setHeadersDelay(200, TimeUnit.MILLISECONDS))
        server.start()
        try {
            val hub = SignalRHubPort(
                ServerEndpoint(ServerId("smoke"), server.url("/sonarr/").toString(), "test-key"),
                OkHttpClient(),
            )
            withTimeout(10_000) {
                val start = async(Dispatchers.IO) { runCatching { hub.start() } }
                val request = withContext(Dispatchers.IO) { server.takeRequest(5, TimeUnit.SECONDS) }
                assertNotNull(request)
                assertEquals("/sonarr/signalr/messages/negotiate", request!!.path!!.substringBefore('?'))
                val stop = async(Dispatchers.IO) { hub.stop() }
                assertTrue(start.await().isFailure)
                stop.await()
                assertTrue("No error may escape to Rx's uncaught handler", errors.isEmpty())
            }
        } finally {
            server.shutdown()
            RxJavaPlugins.setErrorHandler(previous)
        }
    }
}
