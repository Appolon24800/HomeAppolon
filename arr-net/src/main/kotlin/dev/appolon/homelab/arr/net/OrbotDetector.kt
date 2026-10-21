package dev.appolon.homelab.arr.net

/**
 * Detects Orbot and turns the `useOrbot` toggle into a concrete SOCKS5 proxy.
 * Installation is probed through the injected lambda so this class stays free of
 * `android.content` and runs in plain JVM unit tests; the app side supplies a
 * PackageManager-backed check. Fails closed: when the toggle is set but Orbot
 * is not installed, [resolveProxy] throws [OrbotUnavailableException] so the
 * request fails instead of silently leaving Tor and connecting directly.
 */
class OrbotDetector(
    private val isPackageInstalled: (packageName: String) -> Boolean = { false },
) {
    /** True when the Orbot app is present on this device. */
    fun isOrbotInstalled(): Boolean = isPackageInstalled(ORBOT_PACKAGE)

    /**
     * Resolves the effective proxy for a server: `useOrbot` becomes Orbot's
     * SOCKS5 endpoint (which forwards DNS through Tor) when installed, throws
     * [OrbotUnavailableException] when not, and any explicit proxy config
     * passes through unchanged.
     */
    fun resolveProxy(config: ProxyConfig): ProxyConfig = when {
        config.useOrbot && isOrbotInstalled() ->
            ProxyConfig(type = ProxyConfig.Type.SOCKS5, host = ORBOT_HOST, port = ORBOT_PORT)
        config.useOrbot -> throw OrbotUnavailableException()
        else -> config
    }

    private companion object {
        const val ORBOT_PACKAGE = "org.torproject.android"
        const val ORBOT_HOST = "127.0.0.1"
        const val ORBOT_PORT = 9050
    }
}

/**
 * Raised when a server demands Orbot but the install probe says it is absent.
 * Subclasses [IllegalStateException] so callers that already treat client
 * construction failures as "server unreachable" handle it like any other
 * connection error instead of crashing.
 */
class OrbotUnavailableException : IllegalStateException(
    "useOrbot is set but Orbot (org.torproject.android) is not installed; " +
        "failing closed instead of connecting directly",
)
