package dev.appolon.homelab.arr.ui.nav

import dev.appolon.homelab.arr.core.AppType
import dev.appolon.homelab.arr.core.ServerId

/**
 * Type-safe route model for every native *arr screen. The model is plain Kotlin so
 * tests and non-Compose code can encode, decode, and compare routes without pulling
 * in navigation-compose; only [dev.appolon.homelab.arr.ui.shell.ArrShell] maps it
 * onto a NavHost.
 *
 * Path scheme (each segment is percent-encoded, one segment per argument):
 * - `servers`
 * - `server/{serverId}`
 * - `server/{serverId}/{app}/library|detail/{entityId}|add|queue|calendar|settings`
 * - `f/{owner}/{...opaque feature path}` via [ArrRouteRegistry]
 */
sealed interface ArrRoute {
    /** Root of the arr shell: the list of configured servers. */
    data object ServerList : ArrRoute

    /** Dashboard of one server instance. */
    data class ServerHome(val serverId: ServerId) : ArrRoute

    /** Library grid/list of one app on one server. */
    data class Library(val serverId: ServerId, val app: AppType) : ArrRoute

    /** Detail page of one entity (movie, series, album, indexer, subtitle...). */
    data class Detail(
        val serverId: ServerId,
        val app: AppType,
        val entityId: String,
    ) : ArrRoute

    /** Interactive add/search flow (add movie by TMDB search, and so on). */
    data class Add(val serverId: ServerId, val app: AppType) : ArrRoute

    /** Download queue of one app. */
    data class Queue(val serverId: ServerId, val app: AppType) : ArrRoute

    /** Release calendar of one app. */
    data class Calendar(val serverId: ServerId, val app: AppType) : ArrRoute

    /** Settings screens of one app, mirroring the web UI admin pages. */
    data class Settings(val serverId: ServerId, val app: AppType) : ArrRoute

    /**
     * Opaque route holder for a nested destination owned by a feature module. The
     * shell never inspects [path]; it only hands it back to the feature that
     * registered the matching pattern in [ArrRouteRegistry].
     */
    data class Feature(val owner: String, val path: String) : ArrRoute
}

/** Encode/decode helpers plus the navigation patterns the shell registers. */
object ArrRoutes {

    /** Route of [ArrRoute.ServerList], also the shell's start destination. */
    const val SERVER_LIST_ROUTE = "servers"
    const val SERVER_HOME_PATTERN = "server/{serverId}"
    const val LIBRARY_PATTERN = "server/{serverId}/{app}/library"
    const val DETAIL_PATTERN = "server/{serverId}/{app}/detail/{entityId}"
    const val ADD_PATTERN = "server/{serverId}/{app}/add"
    const val QUEUE_PATTERN = "server/{serverId}/{app}/queue"
    const val CALENDAR_PATTERN = "server/{serverId}/{app}/calendar"
    const val SETTINGS_PATTERN = "server/{serverId}/{app}/settings"

    /** First segment of every feature-owned encoded route. */
    const val FEATURE_PREFIX_SEGMENT = "f"

    fun encode(route: ArrRoute): String = when (route) {
        ArrRoute.ServerList -> SERVER_LIST_ROUTE
        is ArrRoute.ServerHome -> "server/${RouteCodec.encode(route.serverId.value)}"
        is ArrRoute.Library -> appScopedPath(route.serverId, route.app, "library")
        is ArrRoute.Detail -> "server/${RouteCodec.encode(route.serverId.value)}/" +
            "${route.app.wireName}/detail/${RouteCodec.encode(route.entityId)}"
        is ArrRoute.Add -> appScopedPath(route.serverId, route.app, "add")
        is ArrRoute.Queue -> appScopedPath(route.serverId, route.app, "queue")
        is ArrRoute.Calendar -> appScopedPath(route.serverId, route.app, "calendar")
        is ArrRoute.Settings -> appScopedPath(route.serverId, route.app, "settings")
        is ArrRoute.Feature -> FEATURE_PREFIX_SEGMENT + "/" +
            RouteCodec.encode(route.owner) + "/" +
            route.path.split('/').joinToString("/") { RouteCodec.encode(it) }
    }

    /**
     * Decodes a built-in route. Returns null for anything malformed and for
     * feature-owned routes, which need pattern knowledge from [ArrRouteRegistry.decodeFeature].
     */
    fun decode(raw: String): ArrRoute? {
        val segments = raw.split('/')
        return when {
            segments == listOf(SERVER_LIST_ROUTE) -> ArrRoute.ServerList
            segments.size == 2 && segments[0] == "server" ->
                segmentValue(segments[1])?.let { ArrRoute.ServerHome(ServerId(it)) }
            segments.size == 4 && segments[0] == "server" && segments[3] == "library" ->
                appRouteFrom(segments[1], segments[2], ArrRoute::Library)
            segments.size == 5 && segments[0] == "server" && segments[3] == "detail" -> {
                val entityId = segmentValue(segments[4])
                val serverId = segmentValue(segments[1])
                val app = segmentValue(segments[2])?.let(::appFromWireName)
                if (entityId != null && serverId != null && app != null) {
                    ArrRoute.Detail(ServerId(serverId), app, entityId)
                } else {
                    null
                }
            }
            segments.size == 4 && segments[0] == "server" && segments[3] == "add" ->
                appRouteFrom(segments[1], segments[2], ArrRoute::Add)
            segments.size == 4 && segments[0] == "server" && segments[3] == "queue" ->
                appRouteFrom(segments[1], segments[2], ArrRoute::Queue)
            segments.size == 4 && segments[0] == "server" && segments[3] == "calendar" ->
                appRouteFrom(segments[1], segments[2], ArrRoute::Calendar)
            segments.size == 4 && segments[0] == "server" && segments[3] == "settings" ->
                appRouteFrom(segments[1], segments[2], ArrRoute::Settings)
            else -> null
        }
    }

    /** Server the route is scoped to, or null for [ArrRoute.ServerList] and [ArrRoute.Feature]. */
    fun serverIdOf(route: ArrRoute): ServerId? = when (route) {
        ArrRoute.ServerList -> null
        is ArrRoute.Feature -> null
        is ArrRoute.ServerHome -> route.serverId
        is ArrRoute.Library -> route.serverId
        is ArrRoute.Detail -> route.serverId
        is ArrRoute.Add -> route.serverId
        is ArrRoute.Queue -> route.serverId
        is ArrRoute.Calendar -> route.serverId
        is ArrRoute.Settings -> route.serverId
    }

    internal fun appFromWireName(name: String): AppType? =
        AppType.entries.firstOrNull { it.wireName == name }

    /** Decodes one argument segment; empty strings are treated as absent. */
    private fun segmentValue(raw: String): String? =
        RouteCodec.decode(raw)?.takeIf { it.isNotEmpty() }

    private fun appScopedPath(serverId: ServerId, app: AppType, literal: String): String =
        "server/${RouteCodec.encode(serverId.value)}/${RouteCodec.encode(app.wireName)}/$literal"

    private fun appRouteFrom(
        rawServerId: String,
        rawApp: String,
        build: (ServerId, AppType) -> ArrRoute,
    ): ArrRoute? {
        val serverId = segmentValue(rawServerId) ?: return null
        val app = segmentValue(rawApp)?.let(::appFromWireName) ?: return null
        return build(ServerId(serverId), app)
    }
}

/**
 * Minimal RFC 3986 percent codec for single path segments. Slashes, spaces, and
 * non-ASCII bytes are escaped so one argument can never split into two segments.
 */
internal object RouteCodec {

    fun encode(raw: String): String = StringBuilder(raw.length).apply {
        for (byte in raw.encodeToByteArray()) {
            val c = byte.toInt() and 0xFF
            if (isUnreserved(c)) append(c.toChar()) else {
                append('%')
                append(HEX[c ushr 4])
                append(HEX[c and 0xF])
            }
        }
    }.toString()

    /** Returns null for malformed percent sequences (strict, never throws). */
    fun decode(raw: String): String? {
        if ('%' !in raw) return raw
        val bytes = ArrayList<Byte>(raw.length)
        var i = 0
        while (i < raw.length) {
            val c = raw[i]
            if (c == '%') {
                if (i + 2 >= raw.length) return null
                val hi = hexValue(raw[i + 1]) ?: return null
                val lo = hexValue(raw[i + 2]) ?: return null
                bytes.add(((hi shl 4) or lo).toByte())
                i += 3
            } else {
                bytes.addAll(c.toString().encodeToByteArray().toList())
                i += 1
            }
        }
        return bytes.toByteArray().decodeToString()
    }

    private fun hexValue(c: Char): Int? = when (c) {
        in '0'..'9' -> c - '0'
        in 'a'..'f' -> c - 'a' + 10
        in 'A'..'F' -> c - 'A' + 10
        else -> null
    }

    private fun isUnreserved(c: Int): Boolean =
        c in '0'.code..'9'.code || c in 'A'.code..'Z'.code ||
            c in 'a'.code..'z'.code || c == '-'.code || c == '.'.code ||
            c == '_'.code || c == '~'.code

    private const val HEX = "0123456789ABCDEF"
}
