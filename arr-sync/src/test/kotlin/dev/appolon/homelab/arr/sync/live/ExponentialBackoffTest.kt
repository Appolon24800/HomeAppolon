package dev.appolon.homelab.arr.sync.live

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ExponentialBackoffTest {
    @Test
    fun `doubles the delay per attempt and caps at the ceiling when jitter is off`() {
        val backoff = ExponentialBackoff(baseMillis = 1_000, capMillis = 30_000, jitterRatio = 0.0)
        val delays = (0..6).map(backoff::delayForAttempt)
        assertEquals(listOf(1_000L, 2_000L, 4_000L, 8_000L, 16_000L, 30_000L, 30_000L), delays)
    }

    @Test
    fun `attempts beyond the cap keep waiting the capped delay`() {
        val backoff = ExponentialBackoff(baseMillis = 1_000, capMillis = 2_000, jitterRatio = 0.0)
        assertEquals(2_000L, backoff.delayForAttempt(40))
        assertEquals(2_000L, backoff.delayForAttempt(400))
    }

    @Test
    fun `jitter stays inside the configured band`() {
        val random = Random(seed = 42)
        val backoff = ExponentialBackoff(
            baseMillis = 1_000,
            capMillis = 30_000,
            jitterRatio = 0.2,
            random = random,
        )
        repeat(1_000) { attempt ->
            val expectedRaw = minOf(30_000L, 1_000L shl (attempt % 6))
            val delay = backoff.delayForAttempt(attempt % 6)
            assertTrue(
                "delay $delay escaped the band around $expectedRaw",
                delay in (expectedRaw * 0.8 - 1).toLong()..(expectedRaw * 1.2 + 1).toLong(),
            )
            assertTrue(delay <= 36_000L)
        }
    }

    @Test
    fun `rejects nonsensical configuration`() {
        assertThrows(IllegalArgumentException::class.java) { ExponentialBackoff(baseMillis = 0) }
        assertThrows(IllegalArgumentException::class.java) {
            ExponentialBackoff(baseMillis = 10_000, capMillis = 1_000)
        }
        assertThrows(IllegalArgumentException::class.java) {
            ExponentialBackoff(jitterRatio = 1.5)
        }
    }
}
