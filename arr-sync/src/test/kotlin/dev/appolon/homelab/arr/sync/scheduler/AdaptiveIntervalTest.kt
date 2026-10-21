package dev.appolon.homelab.arr.sync.scheduler

import java.time.Duration
import org.junit.Assert.assertEquals
import org.junit.Test

class AdaptiveIntervalTest {

    private val min = Duration.ofMinutes(15)
    private val default = Duration.ofMinutes(30)
    private val oneHour = Duration.ofHours(1)
    private val twoHours = Duration.ofHours(2)
    private val fourHours = Duration.ofHours(4)
    private val max = Duration.ofHours(6)

    private fun next(
        imminent: Int,
        active: Int,
        outcome: PollOutcome?,
        previous: Duration? = null,
        bounds: IntervalBounds = IntervalBounds.DEFAULT,
    ): Duration = AdaptiveInterval.nextInterval(imminent, active, outcome, previous, bounds)

    // Busy signals pin the minimum regardless of everything else.

    @Test
    fun imminentReleaseForcesMinimum() {
        assertEquals(
            min,
            next(imminent = 1, active = 0, outcome = PollOutcome.QUIET, previous = max),
        )
    }

    @Test
    fun activeQueueItemForcesMinimum() {
        assertEquals(
            min,
            next(imminent = 0, active = 1, outcome = PollOutcome.QUIET, previous = max),
        )
    }

    @Test
    fun manyBusySignalsForceMinimum() {
        assertEquals(min, next(imminent = 42, active = 7, outcome = null, previous = max))
    }

    @Test
    fun busyOverridesFailureAndMissingHistory() {
        assertEquals(
            min,
            next(imminent = 2, active = 0, outcome = PollOutcome.FAILED, previous = null),
        )
        assertEquals(min, next(imminent = 0, active = 3, outcome = null, previous = null))
    }

    @Test
    fun negativeCountsAreNotBusy() {
        assertEquals(default, next(imminent = -1, active = -5, outcome = null))
    }

    // First run and failures settle on the default.

    @Test
    fun firstPollUsesDefault() {
        assertEquals(default, next(imminent = 0, active = 0, outcome = null))
    }

    @Test
    fun failedPollRetriesAtDefaultEvenAfterLongQuietStretch() {
        assertEquals(default, next(0, 0, PollOutcome.FAILED, previous = max))
        assertEquals(default, next(0, 0, PollOutcome.FAILED, previous = null))
    }

    @Test
    fun busyThatEndedRelaxesToDefault() {
        assertEquals(default, next(0, 0, PollOutcome.BUSY, previous = min))
    }

    // Quiet polls double the previous interval up to the max.

    @Test
    fun quietFirstPollUsesDefault() {
        assertEquals(default, next(0, 0, PollOutcome.QUIET, previous = null))
    }

    @Test
    fun quietDoublesFromDefaultUpTheLadder() {
        assertEquals(oneHour, next(0, 0, PollOutcome.QUIET, previous = default))
        assertEquals(twoHours, next(0, 0, PollOutcome.QUIET, previous = oneHour))
        assertEquals(fourHours, next(0, 0, PollOutcome.QUIET, previous = twoHours))
        assertEquals(max, next(0, 0, PollOutcome.QUIET, previous = fourHours))
    }

    @Test
    fun quietCapsAtMax() {
        assertEquals(max, next(0, 0, PollOutcome.QUIET, previous = max))
    }

    @Test
    fun quietDoublesOffLadderValuesToo() {
        val previous = Duration.ofMinutes(45)
        assertEquals(Duration.ofMinutes(90), next(0, 0, PollOutcome.QUIET, previous = previous))
    }

    @Test
    fun quietDoublesFromTheMinimum() {
        assertEquals(default, next(0, 0, PollOutcome.QUIET, previous = min))
    }

    // Stale or nonsense previous intervals reset to the default, not a rung.

    @Test
    fun invalidPreviousIntervalsFallBackToDefault() {
        assertEquals(default, next(0, 0, PollOutcome.QUIET, previous = Duration.ZERO))
        assertEquals(default, next(0, 0, PollOutcome.QUIET, previous = Duration.ofMinutes(-10)))
        assertEquals(default, next(0, 0, PollOutcome.QUIET, previous = Duration.ofMinutes(5)))
        assertEquals(default, next(0, 0, PollOutcome.QUIET, previous = Duration.ofHours(7)))
    }

    // Bounds normalization.

    @Test
    fun boundsClampToHardWalls() {
        val bounds = IntervalBounds(min = Duration.ofMinutes(1), max = Duration.ofHours(12))
        val n = bounds.normalized()
        assertEquals(IntervalBounds.HARD_MIN, n.min)
        assertEquals(IntervalBounds.HARD_MAX, n.max)
    }

    @Test
    fun invertedBoundsCollapseToTheMax() {
        val n = IntervalBounds(min = Duration.ofHours(3), max = Duration.ofHours(1)).normalized()
        assertEquals(oneHour, n.min)
        assertEquals(oneHour, n.max)
        assertEquals(oneHour, n.default)
    }

    @Test
    fun defaultOutsideBoundsIsPulledInside() {
        val n = IntervalBounds(min = Duration.ofHours(2), default = default, max = max).normalized()
        assertEquals(Duration.ofHours(2), n.default)
        val n2 = IntervalBounds(min = min, default = Duration.ofHours(8), max = max).normalized()
        assertEquals(max, n2.default)
    }

    @Test
    fun normalizationIsIdempotent() {
        val once = IntervalBounds(
            min = Duration.ofMinutes(1),
            default = Duration.ofHours(8),
            max = Duration.ofMinutes(20),
        ).normalized()
        assertEquals(once, once.normalized())
    }

    // User bounds interact with the ladder.

    @Test
    fun busyReturnsUserMinimum() {
        val bounds = IntervalBounds(min = oneHour, default = twoHours, max = max)
        assertEquals(oneHour, next(1, 0, PollOutcome.QUIET, previous = max, bounds = bounds))
    }

    @Test
    fun quietEscalatesWithinUserBounds() {
        val bounds = IntervalBounds(min = oneHour, default = twoHours, max = fourHours)
        assertEquals(fourHours, next(0, 0, PollOutcome.QUIET, previous = twoHours, bounds = bounds))
        assertEquals(
            fourHours,
            next(0, 0, PollOutcome.QUIET, previous = fourHours, bounds = bounds),
        )
    }

    @Test
    fun collapsedBoundsReturnTheSingleValueEverywhere() {
        val bounds = IntervalBounds(min = twoHours, default = twoHours, max = twoHours)
        assertEquals(twoHours, next(5, 5, null, previous = null, bounds = bounds))
        assertEquals(twoHours, next(0, 0, PollOutcome.QUIET, previous = twoHours, bounds = bounds))
        assertEquals(twoHours, next(0, 0, PollOutcome.FAILED, previous = twoHours, bounds = bounds))
    }

    @Test
    fun rawOutOfBoundsBoundsBehaveLikeNormalizedOnes() {
        val raw = IntervalBounds(
            min = Duration.ofMinutes(1),
            default = Duration.ofMinutes(1),
            max = Duration.ofMinutes(20),
        )
        val normalized = raw.normalized()
        assertEquals(
            AdaptiveInterval.nextInterval(1, 0, null, null, normalized),
            AdaptiveInterval.nextInterval(1, 0, null, null, raw),
        )
        assertEquals(
            AdaptiveInterval.nextInterval(0, 0, PollOutcome.QUIET, oneHour, normalized),
            AdaptiveInterval.nextInterval(0, 0, PollOutcome.QUIET, oneHour, raw),
        )
    }
}
