package dev.appolon.homelab.arr.core

import java.io.File
import java.util.Properties

/** One live *arr server entry from `local.arr-test.properties`. */
data class ArrTestServer(val app: AppType, val baseUrl: String, val apiKey: String)

/**
 * Reader for the gitignored `local.arr-test.properties` at the repo root, the
 * only sanctioned source of live-server credentials for integration and smoke
 * tests. Plain JVM IO, no Android APIs, no network. When the file is absent
 * (CI, any checkout without credentials) everything reports unavailable and
 * tests self-skip; values are never logged.
 *
 * Keys per app are `{app}.url` and `{app}.apikey` with the lowercase app ids
 * matching [AppType.wireName]. An app counts as configured only when both
 * values are non-blank. The optional `arr.test.properties` system property
 * points at the file directly, for CI layouts.
 */
object ArrTestServers {

    const val FILE_NAME = "local.arr-test.properties"

    private const val PATH_PROPERTY = "arr.test.properties"
    private const val URL_KEY = ".url"
    private const val API_KEY_KEY = ".apikey"
    private const val MAX_PARENT_WALK = 10

    private data class Loaded(val file: File?, val props: Properties)

    private var cache: Loaded? = null

    /** True when the properties file was found and read. */
    val available: Boolean
        get() = loaded().file != null

    /**
     * The configured server for [app], or null when the file is absent or the
     * app's entry is incomplete.
     */
    fun server(app: AppType): ArrTestServer? {
        val state = loaded()
        if (state.file == null) return null
        val baseUrl = state.props.getProperty(app.wireName + URL_KEY)?.trim().orEmpty()
        val apiKey = state.props.getProperty(app.wireName + API_KEY_KEY)?.trim().orEmpty()
        if (baseUrl.isEmpty() || apiKey.isEmpty()) return null
        return ArrTestServer(app, baseUrl, apiKey)
    }

    val radarr: ArrTestServer?
        get() = server(AppType.RADARR)

    val sonarr: ArrTestServer?
        get() = server(AppType.SONARR)

    val lidarr: ArrTestServer?
        get() = server(AppType.LIDARR)

    val prowlarr: ArrTestServer?
        get() = server(AppType.PROWLARR)

    val bazarr: ArrTestServer?
        get() = server(AppType.BAZARR)

    private fun loaded(): Loaded {
        cache?.let { return it }
        val file = locate()
        val props = Properties()
        if (file != null) {
            // An unreadable file behaves like a missing one; every getter then
            // returns null and callers skip their tests.
            runCatching { file.inputStream().use(props::load) }
        }
        return Loaded(file, props).also { cache = it }
    }

    private fun locate(): File? {
        val override = System.getProperty(PATH_PROPERTY)
        if (!override.isNullOrBlank()) {
            return File(override).takeIf(File::isFile)
        }
        var dir: File? = File(System.getProperty("user.dir") ?: ".").absoluteFile
        repeat(MAX_PARENT_WALK) {
            val candidate = dir?.resolve(FILE_NAME)
            if (candidate?.isFile == true) return candidate
            dir = dir?.parentFile
        }
        return null
    }

    /** Drops cached state so tests can point the reader elsewhere. Test-only. */
    internal fun resetForTest() {
        cache = null
    }
}
