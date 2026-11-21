package dev.appolon.homelab.ui

import java.net.URI

/** True when [url] is [serviceUrl] or a page below it on the same origin. */
internal fun isServicePage(url: String, serviceUrl: String): Boolean = runCatching {
    val page = URI(url)
    val service = URI(serviceUrl)
    val samePlace = page.scheme.equals(service.scheme, ignoreCase = true) &&
        page.host.equals(service.host, ignoreCase = true) &&
        page.port == service.port
    val root = service.path.orEmpty().trimEnd('/')
    val path = page.path.orEmpty()
    samePlace && (root.isEmpty() || path == root || path.startsWith("$root/"))
}.getOrDefault(false)
