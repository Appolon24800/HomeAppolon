package dev.appolon.homelab.data

import java.net.URI

/** Authentication cookie names and attributes used when restoring saved accounts. */
internal object PocketIdCookies {
    private val names = setOf("access_token", "session").flatMap { name ->
        listOf(name, "__Host-$name", "__Secure-$name")
    }.toSet()

    fun isAccessCookie(pair: String): Boolean = pair.substringBefore('=') in
        setOf("access_token", "__Host-access_token", "__Secure-access_token") &&
        pair.substringAfter('=', "").isNotBlank()

    fun sessionPairs(header: String?): List<String> = header.orEmpty().split(';')
        .map(String::trim)
        .filter { it.substringBefore('=') in names && it.substringAfter('=', "").isNotBlank() }

    fun restoreHeaders(baseUrl: String, cookies: List<String>): List<String> {
        val secure = URI(baseUrl).scheme.equals("https", ignoreCase = true)
        return sessionPairs(cookies.joinToString(";")).map { pair ->
            require(secure || !pair.startsWith("__Host-") && !pair.startsWith("__Secure-")) {
                "This saved PocketID session requires HTTPS"
            }
            pair + "; Path=/; HttpOnly; SameSite=Lax" + if (secure) "; Secure" else ""
        }
    }
}
