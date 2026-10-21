package dev.appolon.homelab.arr.servers

import dev.appolon.homelab.arr.core.AppType
import java.util.concurrent.atomic.AtomicReference

/**
 * One-shot prefill for the server setup sheet, set when a deep link
 * carrying an API key is opened. The add sheet consumes it on first
 * composition; a stale prefill never overwrites a later manual entry.
 */
object PendingServerSetup {

    data class Prefill(
        val appType: AppType,
        val baseUrl: String,
        val apiKey: String,
    )

    private val pending = AtomicReference<Prefill?>(null)

    fun set(prefill: Prefill) {
        pending.set(prefill)
    }

    /** Returns and clears the pending prefill; null when none is waiting. */
    fun consume(): Prefill? = pending.getAndSet(null)
}
