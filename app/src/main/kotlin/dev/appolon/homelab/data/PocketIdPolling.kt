package dev.appolon.homelab.data

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive

/** Waits between pending exchange responses using PocketID's retry interval. */
internal suspend fun awaitPocketIdApproval(
    intervalSeconds: Int,
    exchange: suspend () -> PocketId.PocketIdUser?,
): PocketId.PocketIdUser {
    while (true) {
        currentCoroutineContext().ensureActive()
        exchange()?.let { return it }
        delay(intervalSeconds.coerceAtLeast(1) * 1_000L)
    }
}
