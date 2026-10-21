package dev.appolon.homelab.arr.feature.lidarr.settings.mm.data

import org.junit.Assert.assertEquals
import org.junit.Test

class NamingPreviewRendererTest {

    @Test
    fun `substitutes every known token with its sample`() {
        assertEquals(
            "The Artist Name - The Album Title - Track Title (1)",
            NamingPreviewRenderer.render("{Artist Name} - {Album Title} - {Track Title}"),
        )
    }

    @Test
    fun `padded track and medium numbers render zero-filled`() {
        assertEquals(
            "The Artist Name - The Album Title - 01 - 01 - Track Title (1)",
            NamingPreviewRenderer.render(
                "{Artist Name} - {Album Title} - {Medium:00} - {Track:00} - {Track Title}",
            ),
        )
    }

    @Test
    fun `artist folder formats render`() {
        assertEquals(
            "Artist Name, The",
            NamingPreviewRenderer.render("{Artist NameThe}"),
        )
    }

    @Test
    fun `quality and media info tokens render the sample release`() {
        assertEquals(
            "The Artist Name - The Album Title MP3-320 Proper MP3-RlsGrp",
            NamingPreviewRenderer.render(
                "{Artist Name} - {Album Title} {Quality Full} {MediaInfo AudioCodec}-{Release Group}",
            ),
        )
    }

    @Test
    fun `token lookup ignores case and inner whitespace`() {
        assertEquals(
            "The Album Title 2010",
            NamingPreviewRenderer.render("{ album  TITLE } {Album ReleaseYear}"),
        )
    }

    @Test
    fun `unknown tokens stay visible so typos are caught`() {
        assertEquals(
            "{Album Titl}",
            NamingPreviewRenderer.render("{Album Titl}"),
        )
    }

    @Test
    fun `standard separator runs stay untouched`() {
        // The Sonarr sibling collapsed runs like "--" to "-", previewing
        // filenames the server never produces; Lidarr keeps them verbatim.
        assertEquals(
            "The Artist Name -- 01",
            NamingPreviewRenderer.render("{Artist Name} -- {Track:00}"),
        )
        assertEquals(
            "The Artist Name..The Album Title",
            NamingPreviewRenderer.render("{Artist Name}..{Album Title}"),
        )
        assertEquals(
            "The Artist Name  The Album Title",
            NamingPreviewRenderer.render("{Artist Name}  {Album Title}"),
        )
    }

    @Test
    fun `separators around the edges trim away`() {
        assertEquals(
            "The Artist Name",
            NamingPreviewRenderer.render(" {Artist Name} . "),
        )
    }

    @Test
    fun `blank format renders blank`() {
        assertEquals("", NamingPreviewRenderer.render("  "))
    }
}
