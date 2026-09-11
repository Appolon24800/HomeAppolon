package dev.appolon.homelab.data

/**
 * Homer logos are either absolute URLs or paths relative to the Homer root
 * (e.g. "assets/icons/kavita.svg"). Coil needs an absolute URL.
 */
object IconUrlResolver {

    fun resolve(url: String?, baseUrl: String = Homer.BASE_URL): String? {
        if (url.isNullOrBlank()) return null
        return when {
            url.startsWith("http://") || url.startsWith("https://") -> url
            url.startsWith("data:") -> url
            else -> baseUrl.trimEnd('/') + "/" + url.trimStart('/', '.')
        }
    }
}
