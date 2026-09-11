package dev.appolon.homelab.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import dev.appolon.homelab.data.HomerColors

/** Parses "#rrggbb" (or "#rgb"); returns null for anything else. */
fun parseHexColor(hex: String?): Color? {
    if (hex == null) return null
    val s = hex.trim().removePrefix("#")
    return when (s.length) {
        6 -> s.toHexOrNull()?.let { Color(it or 0xFF000000L) }
        3 -> s.chunked(1).joinToString("") { it + it }.toHexOrNull()
            ?.let { Color(it or 0xFF000000L) }
        else -> null
    }
}

private fun String.toHexOrNull(): Long? = toLongOrNull(16)

/** Black-ish or white-ish text colour depending on the background luminance. */
fun contrastOn(color: Color): Color =
    if (color.luminance() > 0.5f) Color(0xFF171717) else Color(0xFFF2F2F2)

/**
 * Maps Homer's palette onto Material 3 colour roles. Missing keys fall back to
 * sensible derived shades so a partial YAML still produces a complete scheme.
 */
fun homerColorScheme(c: HomerColors, dark: Boolean): ColorScheme? {
    if (c.isBlank) return null

    val primary = parseHexColor(c.highlightPrimary) ?: Color(0xFFB3261E)
    val primaryContainer = parseHexColor(c.highlightSecondary) ?: primary
    val hover = parseHexColor(c.highlightHover) ?: primary
    val link = parseHexColor(c.link) ?: primary
    val textTitle = parseHexColor(c.textTitle) ?: Color(0xFFE8E8E8)
    val textSubtitle = parseHexColor(c.textSubtitle) ?: textTitle.copy(alpha = 0.7f)

    val background = if (dark) {
        parseHexColor(c.background) ?: Color(0xFF050505)
    } else {
        parseHexColor(c.background) ?: Color(0xFFF5F5F5)
    }
    val card = if (dark) {
        parseHexColor(c.cardBackground) ?: Color(0xFF0D0D0D)
    } else {
        parseHexColor(c.cardBackground) ?: Color(0xFFFFFFFF)
    }
    val textHeader = parseHexColor(c.textHeader) ?: contrastOn(primary)

    // Elevated-container ramp between the page background and the card colour.
    val containerLow = lerp(background, card, 0.6f)
    val containerHigh = lerp(card, textTitle, if (dark) 0.06f else 0.10f)
    val containerHighest = lerp(card, textTitle, if (dark) 0.12f else 0.18f)

    val base = if (dark) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = primary,
        onPrimary = contrastOn(primary),
        primaryContainer = primaryContainer,
        onPrimaryContainer = contrastOn(primaryContainer),
        secondary = link,
        onSecondary = contrastOn(link),
        secondaryContainer = lerp(card, link, 0.22f),
        onSecondaryContainer = textTitle,
        tertiary = hover,
        onTertiary = contrastOn(hover),
        tertiaryContainer = lerp(card, hover, 0.22f),
        onTertiaryContainer = contrastOn(lerp(card, hover, 0.22f)),
        background = background,
        onBackground = textTitle,
        surface = background,
        onSurface = textTitle,
        surfaceVariant = containerHigh,
        onSurfaceVariant = textSubtitle,
        surfaceContainerLowest = background,
        surfaceContainerLow = containerLow,
        surfaceContainer = card,
        surfaceContainerHigh = containerHigh,
        surfaceContainerHighest = containerHighest,
        outlineVariant = lerp(background, textSubtitle, 0.25f),
    )
}

/** Header text colour for the top bar when the Homer theme is active. */
fun homerHeaderTextColor(c: HomerColors): Color =
    parseHexColor(c.textHeader) ?: contrastOn(parseHexColor(c.highlightPrimary) ?: Color(0xFFB3261E))
