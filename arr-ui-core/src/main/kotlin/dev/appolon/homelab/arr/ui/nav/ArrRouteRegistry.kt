package dev.appolon.homelab.arr.ui.nav

/**
 * Registration for one nested destination owned by a feature module.
 *
 * @param owner stable feature key, e.g. `"radarr"`. Must contain only unreserved
 *   path characters (letters, digits, `-._~`).
 * @param pattern path pattern relative to the owner, e.g. `"movie/{movieId}"`.
 *   `{name}` segments are string parameters; everything else must match literally.
 */
data class ArrNestedRoute(val owner: String, val pattern: String)

/**
 * Registry where feature modules register their nested routes as opaque typed
 * holders. The shell reads it when building its NavHost and when decoding an
 * [ArrRoute.Feature] back from the back stack; it never interprets the paths.
 *
 * Register everything before [dev.appolon.homelab.arr.ui.shell.ArrShell] is first
 * composed: the NavHost graph is built once per composition.
 */
class ArrRouteRegistry {

    private val lock = Any()
    private val routes = mutableListOf<ArrNestedRoute>()

    /** Returns false when [owner] already registered this exact pattern. */
    fun register(owner: String, pattern: String): Boolean = synchronized(lock) {
        val candidate = ArrNestedRoute(owner, pattern)
        if (routes.contains(candidate)) {
            false
        } else {
            routes.add(candidate)
            true
        }
    }

    /** Drops every pattern registered by [owner]. No-op when nothing is registered. */
    fun unregister(owner: String): Unit = synchronized(lock) {
        routes.removeAll { it.owner == owner }
    }

    /** Snapshot of every registration, in registration order. */
    fun nestedRoutes(): List<ArrNestedRoute> = synchronized(lock) { routes.toList() }

    /**
     * Decodes a feature-encoded route (`f/{owner}/{...path}`) against the registered
     * patterns. Returns null when the owner is unknown, no pattern matches the
     * segment shape, or any percent-escape is malformed.
     */
    fun decodeFeature(raw: String): ArrRoute.Feature? {
        val segments = raw.split('/')
        if (segments.size < 3 || segments[0] != ArrRoutes.FEATURE_PREFIX_SEGMENT) return null
        val owner = RouteCodec.decode(segments[1]) ?: return null
        val pathSegments = segments.drop(2)
        for (segment in pathSegments) {
            if (RouteCodec.decode(segment) == null) return null
        }
        val decoded = pathSegments.map { RouteCodec.decode(it)!! }
        val matches = synchronized(lock) {
            routes.filter { it.owner == owner && matchRoutePattern(it.pattern, decoded) != null }
        }
        // Exact-match patterns win over patterns with parameters; registration order
        // breaks ties so a feature can shadow its own earlier generic patterns.
        val nested = matches.minByOrNull { matchRoutePattern(it.pattern, decoded)!!.size }
            ?: return null
        return ArrRoute.Feature(owner, decoded.joinToString("/"))
    }
}

/**
 * Matches a pattern like `movie/{movieId}/cast` against decoded path segments.
 * Returns the captured parameters, or null when the shape differs, a parameter
 * name is malformed, or the same name appears twice.
 */
fun matchRoutePattern(pattern: String, segments: List<String>): Map<String, String>? {
    val patternSegments = pattern.split('/')
    if (patternSegments.size != segments.size) return null
    val params = HashMap<String, String>()
    for (index in patternSegments.indices) {
        val expected = patternSegments[index]
        val actual = segments[index]
        if (expected.startsWith("{") && expected.endsWith("}")) {
            val name = expected.removeSurrounding("{", "}")
            if (name.isEmpty() || params.containsKey(name)) return null
            params[name] = actual
        } else if (expected != actual) {
            return null
        }
    }
    return params
}

/** Parameter names in declaration order, e.g. `["movieId"]` for `movie/{movieId}`. */
fun patternParams(pattern: String): List<String> =
    pattern.split('/').filter { it.startsWith("{") && it.endsWith("}") }
        .map { it.removeSurrounding("{", "}") }

/**
 * Substitutes pattern parameters with percent-encoded values so the result can be
 * navigated to. Returns null when a declared parameter is missing or empty.
 */
fun fillPattern(pattern: String, values: Map<String, String>): String? {
    val segments = pattern.split('/').map { expected ->
        if (expected.startsWith("{") && expected.endsWith("}")) {
            val name = expected.removeSurrounding("{", "}")
            val value = values[name]
            if (value.isNullOrEmpty()) return null
            RouteCodec.encode(value)
        } else {
            expected
        }
    }
    return segments.joinToString("/")
}

/** Navigation graph route for a nested registration: `f/{owner}/{pattern}`. */
internal fun nestedNavPattern(nested: ArrNestedRoute): String =
    "${ArrRoutes.FEATURE_PREFIX_SEGMENT}/${nested.owner}/${nested.pattern}"
