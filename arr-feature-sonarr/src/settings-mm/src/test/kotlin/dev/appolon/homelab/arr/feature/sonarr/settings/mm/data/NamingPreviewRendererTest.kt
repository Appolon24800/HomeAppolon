package dev.appolon.homelab.arr.feature.sonarr.settings.mm.data

import org.junit.Assert.assertEquals
import org.junit.Test

class NamingPreviewRendererTest {

    @Test
    fun `substitutes every known token with its sample`() {
        assertEquals(
            "The Series Title's! - S01E01 - Episode Title (1)",
            NamingPreviewRenderer.render("{Series Title} - S{season:00}E{episode:00} - {Episode Title}"),
        )
    }

    @Test
    fun `daily formats render the air date`() {
        assertEquals(
            "The Series Title's! - 2013-10-30 - Episode Title (1)",
            NamingPreviewRenderer.render("{Series Title} - {yyyy-MM-dd} - {Episode Title}"),
        )
        assertEquals(
            "The Series Title's! - 2013 10 30",
            NamingPreviewRenderer.render("{Series Title} - {Air Date}"),
        )
    }

    @Test
    fun `anime formats render absolute numbering`() {
        assertEquals(
            "The Series Title's! - 001 - Episode Title (1)",
            NamingPreviewRenderer.render("{Series Title} - {absolute:000} - {Episode Title}"),
        )
    }

    @Test
    fun `season and series folder formats render`() {
        assertEquals("Season 01", NamingPreviewRenderer.render("Season {season:00}"))
        assertEquals(
            "The Series Title's! (2010)",
            NamingPreviewRenderer.render("{Series Title} ({Series Year})"),
        )
    }

    @Test
    fun `token lookup ignores case and inner whitespace`() {
        assertEquals(
            "The Series Title's! 01",
            NamingPreviewRenderer.render("{ series  TITLE } {Season:00}"),
        )
    }

    @Test
    fun `unknown tokens stay visible so typos are caught`() {
        assertEquals(
            "{Series Titl}",
            NamingPreviewRenderer.render("{Series Titl}"),
        )
    }

    @Test
    fun `separator runs collapse to a single character`() {
        assertEquals(
            "The Series Title's! - 01",
            NamingPreviewRenderer.render("{Series Title} -- {season:00}"),
        )
    }

    @Test
    fun `separators around the edges trim away`() {
        assertEquals(
            "The Series Title's!",
            NamingPreviewRenderer.render(" {Series Title} . "),
        )
    }

    @Test
    fun `quality tokens render the sample release`() {
        assertEquals(
            "The Series Title's! WEBDL-1080p Proper x264-RlsGrp",
            NamingPreviewRenderer.render("{Series Title} {Quality Full} {MediaInfo VideoCodec}-{Release Group}"),
        )
    }

    @Test
    fun `blank format renders blank`() {
        assertEquals("", NamingPreviewRenderer.render("  "))
    }
}
