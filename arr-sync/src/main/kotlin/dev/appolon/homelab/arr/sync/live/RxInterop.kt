package dev.appolon.homelab.arr.sync.live

import io.reactivex.rxjava3.core.Completable
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Bridges the SignalR client's RxJava3 completions into coroutine suspension
 * without kotlinx-coroutines-rx3 (not in the version catalog). Cancellation
 * disposes the Rx subscription so an abandoned connect stops negotiating.
 */
internal suspend fun Completable.awaitCompletion() {
    suspendCancellableCoroutine { continuation ->
        val disposable = subscribe(
            { if (continuation.isActive) continuation.resume(Unit) },
            { if (continuation.isActive) continuation.resumeWithException(it) },
        )
        continuation.invokeOnCancellation { disposable.dispose() }
    }
}
