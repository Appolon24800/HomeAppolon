package dev.appolon.homelab.arr.net

import dev.appolon.homelab.arr.core.ArrError
import java.io.IOException
import okhttp3.Response

/** Bytes of an error body kept for diagnostics; arr error payloads are small JSON. */
private const val SNIPPET_BYTE_LIMIT = 2048L

/**
 * Maps a non-2xx [Response] onto the shared error taxonomy: API key or basic auth
 * problems (401, 403) become [ArrError.Auth], everything else becomes
 * [ArrError.Http] carrying a trimmed body snippet for the sync status screen.
 * The body is peeked, so it stays readable by callers afterwards.
 */
fun Response.arrError(): ArrError {
    if (code == 401 || code == 403) return ArrError.Auth(code)
    val snippet = runCatching { peekBody(SNIPPET_BYTE_LIMIT).string() }
        .getOrNull()
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
    return ArrError.Http(code, snippet)
}

/**
 * Maps a thrown failure: transport problems (timeouts, resets, DNS, TLS) are
 * [IOException] and become [ArrError.Network]; anything else is
 * [ArrError.Unknown]. Never crash a screen on IO, per the architecture doc.
 */
fun Throwable.arrError(): ArrError = when (this) {
    is IOException -> ArrError.Network(this)
    else -> ArrError.Unknown(this)
}
