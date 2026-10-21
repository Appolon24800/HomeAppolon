package dev.appolon.homelab.arr.sync.live

import kotlin.math.roundToLong
import kotlin.random.Random

/**
 * Exponential reconnect delay with jitter: retry attempt n waits
 * `min(cap, base * 2^n)` scaled by a random factor within
 * `[1 - jitterRatio, 1 + jitterRatio]`, so the result never exceeds
 * `cap * (1 + jitterRatio)`. Jitter de-synchronizes clients that all dropped
 * at once, which here means several arr screens open against one server.
 */
class ExponentialBackoff(
    private val baseMillis: Long = DEFAULT_BASE_MILLIS,
    private val capMillis: Long = DEFAULT_CAP_MILLIS,
    private val jitterRatio: Double = DEFAULT_JITTER_RATIO,
    private val random: Random = Random.Default,
) {
    init {
        require(baseMillis > 0) { "baseMillis must be positive" }
        require(capMillis >= baseMillis) { "capMillis must not be below baseMillis" }
        require(jitterRatio in 0.0..1.0) { "jitterRatio must be within [0.0, 1.0]" }
    }

    /** Delay before the given retry attempt, 0-based and counted consecutively. */
    fun delayForAttempt(attempt: Int): Long {
        var exponential = baseMillis
        repeat(attempt.coerceIn(0, MAX_SHIFT)) {
            if (exponential < capMillis) exponential = exponential shl 1
        }
        val bounded = exponential.coerceAtMost(capMillis)
        if (jitterRatio == 0.0) return bounded
        val factor = 1.0 + jitterRatio * (2.0 * random.nextDouble() - 1.0)
        val maxDelay = capMillis + (capMillis * jitterRatio).roundToLong()
        return (bounded * factor).roundToLong().coerceIn(1L, maxDelay)
    }

    private companion object {
        const val DEFAULT_BASE_MILLIS = 1_000L
        const val DEFAULT_CAP_MILLIS = 60_000L
        const val DEFAULT_JITTER_RATIO = 0.2

        /** Shifts beyond this all land on the cap anyway; keeps the shift overflow-free. */
        const val MAX_SHIFT = 63
    }
}
