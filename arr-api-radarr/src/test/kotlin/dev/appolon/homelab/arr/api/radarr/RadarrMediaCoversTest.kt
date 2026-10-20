package dev.appolon.homelab.arr.api.radarr

import dev.appolon.homelab.arr.api.radarr.api.RadarrMediaCovers
import dev.appolon.homelab.arr.api.radarr.model.MediaCoverTypes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class RadarrMediaCoversTest {

    @Test
    fun buildsPathForExactFilename() {
        assertEquals("api/v3/mediacover/7/banner.png", RadarrMediaCovers.path(7, "banner.png"))
    }

    @Test
    fun buildsTypedPathWithOptionalResizedVariant() {
        assertEquals(
            "api/v3/mediacover/7/poster.jpg",
            RadarrMediaCovers.path(7, MediaCoverTypes.POSTER),
        )
        assertEquals(
            "api/v3/mediacover/7/fanart-500.jpg",
            RadarrMediaCovers.path(7, MediaCoverTypes.FANART, sizePx = 500),
        )
    }

    @Test
    fun rejectsFilenamesOutsideTheSpecPattern() {
        assertThrows(IllegalArgumentException::class.java) {
            RadarrMediaCovers.path(7, "poster.webp")
        }
        assertThrows(IllegalArgumentException::class.java) {
            RadarrMediaCovers.path(7, ".jpg")
        }
    }

    @Test
    fun rejectsNonPositiveSizes() {
        assertThrows(IllegalArgumentException::class.java) {
            RadarrMediaCovers.path(7, MediaCoverTypes.POSTER, sizePx = 0)
        }
    }
}
