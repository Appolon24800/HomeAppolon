package dev.appolon.homelab.arr.sync.scheduler

import dev.appolon.homelab.arr.core.ArrError
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PollResultTest {

    private val error = ArrError.Network(cause = null)

    @Test
    fun defaultsAreInertAndQuiet() {
        val result = PollResult()
        assertEquals(PollOutcome.QUIET, result.outcome())
        assertFalse(result.busy)
    }

    @Test
    fun busyBeatsError() {
        val result = PollResult(imminentReleases = 1, error = error)
        assertEquals(PollOutcome.BUSY, result.outcome())
        assertTrue(result.busy)
    }

    @Test
    fun activeQueueAloneIsBusy() {
        val result = PollResult(activeQueueItems = 2)
        assertEquals(PollOutcome.BUSY, result.outcome())
    }

    @Test
    fun errorWithoutActivityIsFailed() {
        val result = PollResult(error = error)
        assertEquals(PollOutcome.FAILED, result.outcome())
        assertFalse(result.busy)
    }
}
