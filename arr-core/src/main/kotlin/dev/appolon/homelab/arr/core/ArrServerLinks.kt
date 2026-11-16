package dev.appolon.homelab.arr.core

import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder

/** Credential-free links shared by notification creation and app routing. */
object ArrServerLinks {
    const val PREFIX = "homelab://arr/server/"

    fun encode(id: ServerId): String {
        require(id.value.isNotBlank()) { "A server link needs a server id" }
        return PREFIX + URLEncoder.encode(id.value, "UTF-8").replace("+", "%20")
    }

    fun decode(raw: String): ServerId? {
        val uri = runCatching { URI(raw.trim()) }.getOrNull() ?: return null
        if (!uri.scheme.equals("homelab", ignoreCase = true) ||
            !uri.host.equals("arr", ignoreCase = true) ||
            uri.rawQuery != null || uri.rawFragment != null || uri.userInfo != null ||
            uri.port != -1
        ) return null
        val segments = uri.rawPath?.split('/') ?: return null
        if (segments.size != 3 || segments[1] != "server") return null
        val id = runCatching {
            // A plus in a path is literal, unlike a plus in a form query.
            URLDecoder.decode(segments[2].replace("+", "%2B"), "UTF-8")
        }.getOrNull()?.takeIf { it.isNotBlank() } ?: return null
        return ServerId(id)
    }
}
