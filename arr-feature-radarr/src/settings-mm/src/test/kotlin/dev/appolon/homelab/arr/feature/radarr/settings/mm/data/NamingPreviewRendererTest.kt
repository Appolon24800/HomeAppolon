package dev.appolon.homelab.arr.feature.radarr.settings.mm.data

import org.junit.Assert.assertEquals
import org.junit.Test

class NamingPreviewRendererTest {

    @Test
    fun `substitutes every known token with its sample`() {
        assertEquals(
            "Movie Title (2010)",
            NamingPreviewRenderer.render("{Movie Title} ({Year})"),
        )
    }

    @Test
    fun `token lookup ignores case and inner whitespace`() {
        assertEquals(
            "Movie Title",
            NamingPreviewRenderer.render("{ movie  tITLE }"),
        )
    }

    @Test
    fun `unknown tokens stay visible so typos are caught`() {
        assertEquals(
            "{Movie Tile}",
            NamingPreviewRenderer.render("{Movie Tile}"),
        )
    }

    @Test
    fun `separator runs collapse to a single character`() {
        assertEquals(
            "Movie Title - 2010",
            NamingPreviewRenderer.render("{Movie Title} -- {Year}"),
        )
        // A deliberate single separator between tokens survives untouched.
        assertEquals(
            "Movie Title - 2010",
            NamingPreviewRenderer.render("{Movie Title} - {Year}"),
        )
    }

    @Test
    fun `separators around the edges trim away`() {
        assertEquals(
            "Movie Title",
            NamingPreviewRenderer.render(" {Movie Title} . "),
        )
    }

    @Test
    fun `blank format renders blank`() {
        assertEquals("", NamingPreviewRenderer.render("  "))
    }

    @Test
    fun `file and folder token sets cover the documented basics`() {
        val fileFormat = "{Movie Title} {Quality Title} {MediaInfo VideoCodec}-{Release Group}"
        assertEquals(
            "Movie Title Bluray-1080p x264-RlsGrp",
            NamingPreviewRenderer.render(fileFormat),
        )
        val folderFormat = "{Movie Title} ({Year}) {Collection}"
        assertEquals(
            "Movie Title (2010) Movie Collection",
            NamingPreviewRenderer.render(folderFormat),
        )
    }
}
