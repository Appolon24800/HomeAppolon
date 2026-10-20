package dev.appolon.homelab.arr.servers.logic

import dev.appolon.homelab.arr.core.AppType
import dev.appolon.homelab.arr.net.ServerEndpoint
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * Editable state of the add/edit server sheet, kept raw (exactly what the
 * user typed) so validation and the URL preview stay honest. A persisted
 * [dev.appolon.homelab.arr.data.ArrServer] is only produced through
 * [toBaseUrl] after [problems] is empty.
 */
data class ServerForm(
    val name: String = "",
    val type: AppType = AppType.RADARR,
    val baseUrl: String = "",
    val apiKey: String = "",
    val basicAuthUser: String = "",
    val basicAuthPassword: String = "",
) {
    /** Everything currently wrong with the input; empty means the form can be saved. */
    val problems: Set<Problem> get() = validate()

    val isValid: Boolean get() = problems.isEmpty()

    /**
     * Live preview under the URL field: the address requests will actually
     * use. Null hides the preview while the field is blank.
     */
    fun previewUrl(): String? =
        baseUrl.trim().takeIf { it.isNotBlank() }?.let(ServerEndpoint::normalizeBaseUrl)

    /** The normalized URL when it parses, trailing slash included, or null. */
    fun toBaseUrl(): String? = parsedUrl()?.toString()

    /** Parsed normalized URL; null when the host is missing or the scheme is not http(s). */
    fun parsedUrl(): HttpUrl? = normalizedCandidate().toHttpUrlOrNull()

    private fun normalizedCandidate(): String =
        baseUrl.trim().let(ServerEndpoint::normalizeBaseUrl)

    private fun validate(): Set<Problem> {
        val found = mutableSetOf<Problem>()
        if (name.isBlank()) found += Problem.NAME_BLANK
        when {
            baseUrl.isBlank() -> found += Problem.URL_BLANK
            parsedUrl()?.host.isNullOrBlank() -> found += Problem.URL_INVALID
        }
        if (apiKey.isBlank()) found += Problem.API_KEY_BLANK
        if (basicAuthUser.isBlank() != basicAuthPassword.isBlank()) {
            found += Problem.BASIC_AUTH_INCOMPLETE
        }
        return found
    }

    /** Input problems, one per field, that the sheet renders inline. */
    enum class Problem {
        NAME_BLANK,
        URL_BLANK,
        URL_INVALID,
        API_KEY_BLANK,
        BASIC_AUTH_INCOMPLETE,
    }
}
