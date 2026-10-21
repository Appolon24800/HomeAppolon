package dev.appolon.homelab.arr.api.radarr

import dev.appolon.homelab.arr.net.ArrJson
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.mockwebserver.MockWebServer
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.io.FileNotFoundException

/** Shared plumbing for MockWebServer tests: fixture loading and Retrofit wiring. */
object TestRadarr {
    /**
     * Mirrors how `:arr-data` builds Retrofit: prefix-preserving base URL
     * plus the shared converter.
     */
    fun retrofit(server: MockWebServer): Retrofit = Retrofit.Builder()
        .baseUrl(server.url("/"))
        .addConverterFactory(ArrJson.asConverterFactory("application/json".toMediaType()))
        .build()

    /** Loads a fixture relative to `fixtures/radarr/`. */
    fun fixture(vararg parts: String): String {
        val path = listOf("fixtures", "radarr", *parts).joinToString("/")
        val stream = TestRadarr::class.java.classLoader?.getResourceAsStream(path)
            ?: throw FileNotFoundException("missing test fixture: $path")
        return stream.readBytes().decodeToString()
    }
}
