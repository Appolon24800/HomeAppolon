package dev.appolon.homelab.data

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import java.io.IOException

/**
 * Waits between pending exchange responses using PocketID's retry interval.
 * Network errors count as still pending: the phone often drops connectivity
 * for this app while the user approves in the browser. PocketID's own answers
 * (expired, rejected) still end the wait.
 */
internal suspend fun awaitPocketIdApproval(
    intervalSeconds: Int,
    exchange: suspend () -> PocketId.PocketIdUser?,
): PocketId.PocketIdUser {
    while (true) {
        currentCoroutineContext().ensureActive()
        try {
            exchange()?.let { return it }
        } catch (_: IOException) {
            // Retry below.
        }
        delay(intervalSeconds.coerceAtLeast(1) * 1_000L)
    }
}
