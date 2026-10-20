package dev.appolon.homelab.arr.servers.logic

import dev.appolon.homelab.arr.net.ProxyConfig
import dev.appolon.homelab.arr.net.TrustSpec

/**
 * Editable proxy and TLS-trust state for the proxy settings screen. Like
 * [ServerForm] it holds raw input; [problems] gates saving and the
 * `to*` converters produce the committed configuration types. Orbot mode is
 * deliberately exclusive: when [useOrbot] is set, host, port, and credentials
 * are ignored because Orbot supplies its own SOCKS5 endpoint.
 */
data class ProxyForm(
    val type: ProxyConfig.Type = ProxyConfig.Type.NONE,
    val host: String = "",
    val port: String = "",
    val username: String = "",
    val password: String = "",
    val useOrbot: Boolean = false,
    val acceptSelfSigned: Boolean = false,
    val fingerprint: String = "",
) {
    /** Everything currently wrong; empty means the form can be saved. */
    val problems: Set<Problem> get() = validate()

    /**
     * The committed proxy: Orbot mode collapses to a bare `useOrbot` config
     * (the net layer resolves it after its install probe), everything else
     * maps straight through.
     */
    fun toProxyConfig(): ProxyConfig = if (useOrbot) {
        ProxyConfig(useOrbot = true)
    } else {
        ProxyConfig(
            type = type,
            host = host.trim(),
            port = port.toIntOrNull() ?: 0,
            username = username.trim().takeIf { it.isNotEmpty() },
            password = password.takeIf { it.isNotEmpty() },
        )
    }

    /**
     * The committed TLS policy: null is the default system trust, otherwise
     * self-signed acceptance, pinned to the normalized fingerprint when one
     * is entered.
     */
    fun toTrustSpec(): TrustSpec? = when {
        !acceptSelfSigned -> null
        else -> normalizedFingerprint()?.let(TrustSpec::Pinned) ?: TrustSpec.AcceptAny
    }

    /**
     * Lowercase hex with colons and spaces stripped, or null when blank or
     * not exactly 64 hex characters.
     */
    fun normalizedFingerprint(): String? {
        if (fingerprint.none { it.isLetterOrDigit() }) return null
        val compact = fingerprint.filter { it.isLetterOrDigit() }.lowercase()
        return compact.takeIf {
            it.length == SHA256_HEX_LENGTH && it.all { c -> c in HEX_DIGITS }
        }
    }

    private val explicitProxy: Boolean
        get() = !useOrbot && type != ProxyConfig.Type.NONE

    private fun validate(): Set<Problem> {
        val found = mutableSetOf<Problem>()
        if (explicitProxy) {
            if (host.isBlank()) found += Problem.HOST_BLANK
            when {
                port.isBlank() -> found += Problem.PORT_BLANK
                else -> {
                    val parsed = port.toIntOrNull()
                    if (parsed == null || parsed !in 1..65535) {
                        found += Problem.PORT_INVALID
                    }
                }
            }
            if (username.isBlank() != password.isBlank()) {
                found += Problem.CREDENTIALS_INCOMPLETE
            }
        }
        if (acceptSelfSigned && fingerprint.any { it.isLetterOrDigit() } &&
            normalizedFingerprint() == null
        ) {
            found += Problem.FINGERPRINT_INVALID
        }
        return found
    }

    /** Input problems, one per field, the screen renders inline. */
    enum class Problem {
        HOST_BLANK,
        PORT_BLANK,
        PORT_INVALID,
        CREDENTIALS_INCOMPLETE,
        FINGERPRINT_INVALID,
    }

    companion object {
        /** SHA-256 digest length in lowercase hex characters. */
        const val SHA256_HEX_LENGTH = 64

        private const val HEX_DIGITS = "0123456789abcdef"

        /** Round-trip helper for loading a stored [ProxyConfig] into the form. */
        fun fromConfig(config: ProxyConfig): ProxyForm = ProxyForm(
            type = config.type,
            host = config.host,
            port = if (config.port > 0) config.port.toString() else "",
            username = config.username.orEmpty(),
            password = config.password.orEmpty(),
            useOrbot = config.useOrbot,
        )
    }
}
