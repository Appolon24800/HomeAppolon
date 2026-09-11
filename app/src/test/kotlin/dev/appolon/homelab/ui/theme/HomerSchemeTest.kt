package dev.appolon.homelab.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import dev.appolon.homelab.data.HomerColors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class HomerSchemeTest {

    private val darkColors = HomerColors(
        highlightPrimary = "#ff1a1a",
        highlightSecondary = "#ff1a1a",
        background = "#050505",
        cardBackground = "#0d0d0d",
        textTitle = "#e8e8e8",
        textSubtitle = "#b0b0b0",
        link = "#e74c3c",
    )

    @Test
    fun `parses six digit hex`() {
        assertEquals(Color(0xFFFF1A1A), parseHexColor("#ff1a1a"))
        assertEquals(Color(0xFF050505), parseHexColor("#050505"))
    }

    @Test
    fun `parses three digit hex`() {
        assertEquals(Color(0xFFFFAA00), parseHexColor("#fa0"))
    }

    @Test
    fun `rejects junk`() {
        assertNull(parseHexColor(null))
        assertNull(parseHexColor("red"))
        assertNull(parseHexColor("#ff"))
        assertNull(parseHexColor(""))
    }

    @Test
    fun `contrast picks dark text on light backgrounds`() {
        assertEquals(Color(0xFF171717), contrastOn(Color.White))
        assertEquals(Color(0xFFF2F2F2), contrastOn(Color(0xFFFF1A1A)))
    }

    @Test
    fun `blank colors yield no scheme`() {
        assertNull(homerColorScheme(HomerColors(), dark = true))
        assertNull(homerColorScheme(HomerColors(), dark = false))
    }

    @Test
    fun `scheme maps homer roles to material roles`() {
        val scheme = homerColorScheme(darkColors, dark = true)!!
        assertEquals(Color(0xFFFF1A1A), scheme.primary)
        assertEquals(Color(0xFF050505), scheme.background)
        assertEquals(Color(0xFF050505), scheme.surface)
        assertEquals(Color(0xFF0D0D0D), scheme.surfaceContainer)
        assertEquals(Color(0xFFE8E8E8), scheme.onSurface)
        assertEquals(Color(0xFFB0B0B0), scheme.onSurfaceVariant)
        // Light icons on the red header background.
        assertFalse(scheme.primary.luminance() > 0.5f)
    }
}
