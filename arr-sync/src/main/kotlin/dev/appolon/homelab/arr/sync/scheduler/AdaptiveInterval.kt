package dev.appolon.homelab.arr.sync.scheduler

import java.time.Duration

/** Classification of a finished poll, driving the interval escalation ladder. */
enum class PollOutcome { BUSY, QUIET, FAILED }

/**
 * User-facing bounds for the adaptive poll interval. Inputs are clamped to
 * [HARD_MIN, HARD_MAX] (WorkManager's periodic floor and the architecture's
 * ceiling); an inverted pair (min above max) collapses to the max.
 */
data class IntervalBounds(
    val min: Duration = DEFAULT_MIN,
    val default: Duration = DEFAULT_INTERVAL,
    val max: Duration = DEFAULT_MAX,
) {
    /** The bounds as [AdaptiveInterval.nextInterval] will see them. */
    fun normalized(): IntervalBounds {
        val nMin = clamp(min, HARD_MIN, HARD_MAX)
        val nMax = clamp(max, HARD_MIN, HARD_MAX)
        val lo = if (nMin > nMax) nMax else nMin
        return IntervalBounds(lo, clamp(default, lo, nMax), nMax)
    }

    companion object {
        val DEFAULT_MIN: Duration = Duration.ofMinutes(15)
        val DEFAULT_INTERVAL: Duration = Duration.ofMinutes(30)
        val DEFAULT_MAX: Duration = Duration.ofHours(6)

        /** Hard walls no user preference can cross. */
        val HARD_MIN: Duration = Duration.ofMinutes(15)
        val HARD_MAX: Duration = Duration.ofHours(6)

        val DEFAULT = IntervalBounds()

        private fun clamp(value: Duration, lo: Duration, hi: Duration): Duration = when {
            value < lo -> lo
            value > hi -> hi
            else -> value
        }
    }
}

/**
 * Pure decision of when the next background poll should run. The ladder:
 * busy (a release lands within six hours, or something is downloading) pins
 * the minimum; a failed poll retries at the default so a flaky server is
 * neither hammered nor forgotten; every consecutive fully quiet poll doubles
 * the previous interval, from the 30 minute default up to the six hour max.
 */
object AdaptiveInterval {

    fun nextInterval(
        imminentReleases: Int,
        activeQueueItems: Int,
        lastOutcome: PollOutcome?,
        previousInterval: Duration? = null,
        bounds: IntervalBounds = IntervalBounds.DEFAULT,
    ): Duration {
        val b = bounds.normalized()
        if (imminentReleases > 0 || activeQueueItems > 0) return b.min
        return when (lastOutcome) {
            null, PollOutcome.FAILED, PollOutcome.BUSY -> b.default
            PollOutcome.QUIET -> escalate(previousInterval, b)
        }
    }

/**
 * Doubles [previousInterval], capped at the max. A missing or stale previous
 * (non-positive, or outside the current bounds after the user shrank them)
 * resets to the default instead of doubling, so recovered state starts the
 * ladder over rather than skipping rungs.
 */
private fun escalate(previous: Duration?, b: IntervalBounds): Duration {
    if (previous == null || previous <= Duration.ZERO || previous < b.min || previous > b.max) {
        return b.default
    }
    val doubled = previous.multipliedBy(2)
    return if (doubled > b.max) b.max else doubled
}
}
