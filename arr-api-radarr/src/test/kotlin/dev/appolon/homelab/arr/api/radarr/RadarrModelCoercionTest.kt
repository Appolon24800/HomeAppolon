package dev.appolon.homelab.arr.api.radarr

import dev.appolon.homelab.arr.api.radarr.model.MediaCoverTypes
import dev.appolon.homelab.arr.api.radarr.model.MovieResource
import dev.appolon.homelab.arr.api.radarr.model.MovieStatusType
import dev.appolon.homelab.arr.net.ArrJson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Pins the lenient-decoding contract every DTO relies on: unknown keys are
 * absorbed, absent fields fall back to defaults, and enum values a newer
 * server adds coerce to the property default (null) instead of crashing the
 * screen. Every enum field in this module is nullable with a null default
 * precisely so unknown wire values surface as null; if kotlinx.serialization
 * ever changes that behavior, this test fails before any screen does.
 */
class RadarrModelCoercionTest {

    @Test
    fun unknownKeysAndMissingFieldsDecode() {
        val movie = ArrJson.decodeFromString<MovieResource>(
            """{"id":5,"title":"X","brandNewField":{"nested":true}}""",
        )

        assertEquals(5, movie.id)
        assertEquals("X", movie.title)
        assertNull(movie.status)
        assertEquals(0, movie.year)
    }

    @Test
    fun unknownEnumValuesCoerceToNull() {
        val movie = ArrJson.decodeFromString<MovieResource>(
            """{"id":1,"status":"holographicRelease","minimumAvailability":"quantum"}""",
        )

        assertNull(movie.status)
        assertNull(movie.minimumAvailability)
    }

    @Test
    fun unknownCoverTypeCoercesToNull() {
        val movie = ArrJson.decodeFromString<MovieResource>(
            """{"id":1,"images":[{"coverType":"hologram"}]}""",
        )

        assertNull(movie.images!!.single().coverType)
    }

    @Test
    fun knownEnumsStillDecodeAndRoundTrip() {
        val movie = ArrJson.decodeFromString<MovieResource>(
            """{"id":1,"status":"released","images":[{"coverType":"poster"}]}""",
        )

        assertEquals(MovieStatusType.RELEASED, movie.status)
        assertEquals(MediaCoverTypes.POSTER, movie.images!!.single().coverType)

        val encoded = ArrJson.encodeToString(MovieResource(id = 3, title = "Y"))
        val decoded = ArrJson.decodeFromString<MovieResource>(encoded)
        assertEquals(decoded, MovieResource(id = 3, title = "Y"))
    }
}
