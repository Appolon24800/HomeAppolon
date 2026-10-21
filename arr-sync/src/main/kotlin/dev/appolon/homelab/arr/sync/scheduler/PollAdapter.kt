package dev.appolon.homelab.arr.sync.scheduler

import dev.appolon.homelab.arr.core.ArrError
import dev.appolon.homelab.arr.data.ArrServer

/**
 * Notification categories the scheduler distinguishes. Each kind maps to one
 * system notification channel and to one per-server toggle in GlobalSettings.
 */
enum class NotificationKind(val channelId: String) {
    GRAB("arr_channel_grab"),
    IMPORT("arr_channel_import"),
    FAIL("arr_channel_fail"),
    HEALTH("arr_channel_health"),
    DISK("arr_channel_disk"),
}

/**
 * One notifiable outcome of a poll. Adapters supply finished text because
 * wording is app-specific; [dedupeKey] must be stable for the same underlying
 * occurrence so the scheduler can suppress repeat notifications for it.
 */
data class PollEvent(
    val kind: NotificationKind,
    val title: String,
    val body: String,
    val dedupeKey: String,
)

/**
 * What one background check found. Counts drive summary notifications and the
 * adaptive interval; [events] carries occurrences the adapter wants notified
 * verbatim. A poll that degraded partway through still returns its partial
 * counts with [error] set.
 */
data class PollResult(
    val newGrabs: Int = 0,
    val newImports: Int = 0,
    val failures: Int = 0,
    val healthIssues: Int = 0,
    val diskWarnings: Int = 0,
    /** Calendar releases inside the six hour imminent window. */
    val imminentReleases: Int = 0,
    /** Queue items actively downloading right now. */
    val activeQueueItems: Int = 0,
    val events: List<PollEvent> = emptyList(),
    val error: ArrError? = null,
) {
    val busy: Boolean get() = imminentReleases > 0 || activeQueueItems > 0

    fun outcome(): PollOutcome = when {
        busy -> PollOutcome.BUSY
        error != null -> PollOutcome.FAILED
        else -> PollOutcome.QUIET
    }
}

/**
 * Background check contract, implemented once per app type by the feature
 * modules. Implementations must not throw; report degradation through
 * [PollResult.error]. The scheduler never depends on the api modules, so
 * adapters translate their DTOs into these scheduler-owned shapes.
 */
interface PollAdapter {
    suspend fun poll(server: ArrServer): PollResult
}
