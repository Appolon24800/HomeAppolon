package dev.appolon.homelab.data

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PocketIdPollingTest {
    @Test
    fun `pending responses respect the server interval`() = runTest {
        var calls = 0
        val pending = async {
            awaitPocketIdApproval(3) {
                if (++calls == 2) PocketId.PocketIdUser("u1", "alice") else null
            }
        }
        runCurrent()
        advanceTimeBy(2_999)
        assertEquals(1, calls)
        advanceTimeBy(1)
        runCurrent()
        assertEquals("u1", pending.await().id)
        assertEquals(2, calls)
    }

    @Test
    fun `a network error keeps waiting for the approval`() = runTest {
        var calls = 0
        val pending = async {
            awaitPocketIdApproval(1) {
                when (++calls) {
                    1 -> throw java.net.UnknownHostException("offline")
                    else -> PocketId.PocketIdUser("u1", "alice")
                }
            }
        }
        advanceTimeBy(1_001)
        assertEquals("u1", pending.await().id)
        assertEquals(2, calls)
    }

    @Test
    fun `a PocketID rejection ends the wait`() = runTest {
        val pending = async {
            runCatching {
                awaitPocketIdApproval(1) { throw PocketId.DeviceLoginException("expired") }
            }
        }
        runCurrent()
        assertEquals("expired", pending.await().exceptionOrNull()?.message)
    }

    @Test
    fun `cancelling approval stops future requests even with an invalid interval`() = runTest {
        var calls = 0
        val pending = async { awaitPocketIdApproval(0) { calls++; null } }
        runCurrent()
        advanceTimeBy(999)
        assertEquals(1, calls)
        pending.cancelAndJoin()
        advanceTimeBy(10_000)
        assertEquals(1, calls)
    }
}
