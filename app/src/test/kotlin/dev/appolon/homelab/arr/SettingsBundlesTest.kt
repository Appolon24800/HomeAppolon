package dev.appolon.homelab.arr

import dev.appolon.homelab.arr.core.ServerId
import dev.appolon.homelab.arr.ui.lce.LceState
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsBundlesTest {
    private val id = ServerId("one")

    @Test
    fun setupFailureCanRetryWithoutRetainingAnError() = runTest {
        var fail = true
        val bundles = SettingsBundles {
            if (fail) throw IOException("Connection setup failed")
            "connected"
        }
        bundles.retainVersions(mapOf(id to 1))
        bundles.connectNow(id)
        assertTrue(bundles.stateOf(id) is LceState.Error)
        assertFalse(bundles.isReady(id))
        fail = false
        bundles.connectNow(id)
        assertEquals("connected", bundles.get(id))
    }

    @Test
    fun missingCredentialsBecomeAnErrorInsteadOfLoadingForever() = runTest {
        val bundles = SettingsBundles<String> { null }
        bundles.retainVersions(mapOf(id to 1))
        bundles.connectNow(id)
        assertTrue(bundles.stateOf(id) is LceState.Error)
    }

    @Test
    fun editingOneServerRetiresItsClientsAndPreservesOtherServers() = runTest {
        val other = ServerId("two")
        val bundles = SettingsBundles { it.value }
        bundles.retainVersions(mapOf(id to 1, other to 2))
        bundles.connectNow(id)
        bundles.connectNow(other)
        bundles.retainVersions(mapOf(id to 3, other to 2))
        assertNull(bundles.get(id))
        assertEquals("two", bundles.get(other))
        bundles.connectNow(id)
        assertEquals("one", bundles.get(id))
    }

    @Test
    fun lateConnectionResultCannotRestoreEditedOrDeletedClients() = runTest {
        for (current in listOf(mapOf(id to 2L), emptyMap())) {
            val started = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            val bundles = SettingsBundles {
                started.complete(Unit)
                release.await()
                "old client"
            }
            bundles.retainVersions(mapOf(id to 1))
            val connection = async { bundles.connectNow(id) }
            started.await()
            bundles.retainVersions(current)
            release.complete(Unit)
            connection.await()
            assertFalse(bundles.isReady(id))
        }
    }

    @Test
    fun cancellationPropagatesAndDoesNotBecomeAnError() = runTest {
        val bundles = SettingsBundles<String> { throw CancellationException("Closed screen") }
        bundles.retainVersions(mapOf(id to 1))
        try {
            bundles.connectNow(id)
            throw AssertionError("Cancellation must propagate")
        } catch (_: CancellationException) {
            assertFalse(bundles.stateOf(id) is LceState.Error)
            assertFalse(bundles.isReady(id))
        }
    }
}
