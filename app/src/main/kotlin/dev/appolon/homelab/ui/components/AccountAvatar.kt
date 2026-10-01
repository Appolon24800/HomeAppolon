package dev.appolon.homelab.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import dev.appolon.homelab.HomelabApp

private val whitespace = Regex("\\s+")

private fun initialsOf(label: String?): String = label
    ?.trim()
    ?.split(whitespace)
    ?.mapNotNull { word -> word.firstOrNull()?.uppercaseChar() }
    ?.take(2)
    ?.joinToString("")
    .orEmpty()

/** Round initials avatar in the theme's primary colours. */
@Composable
fun AccountAvatar(
    label: String?,
    avatarUrl: String? = null,
    modifier: Modifier = Modifier,
    size: Dp = 36.dp,
) {
    Avatar(
        label = label,
        avatarUrl = avatarUrl,
        size = size,
        background = MaterialTheme.colorScheme.primary,
        foreground = MaterialTheme.colorScheme.onPrimary,
        modifier = modifier,
    )
}

/** Contrast-safe avatar variant for coloured (Homer) top bars. */
@Composable
fun AccountAvatarTinted(
    label: String?,
    foreground: Color,
    avatarUrl: String? = null,
    modifier: Modifier = Modifier,
    size: Dp = 36.dp,
) {
    Avatar(
        label = label,
        avatarUrl = avatarUrl,
        size = size,
        background = foreground.copy(alpha = 0.18f),
        foreground = foreground,
        modifier = modifier,
    )
}

/**
 * Profile picture when it loads, initials (or person glyph) otherwise: the
 * initials circle sits underneath, and the image — clipped to the same
 * circle — simply covers it on success. A failed/absent picture therefore
 * falls back naturally, with no state to track.
 */
@Composable
private fun Avatar(
    label: String?,
    avatarUrl: String?,
    size: Dp,
    background: Color,
    foreground: Color,
    modifier: Modifier,
) {
    val initials = initialsOf(label)
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(background),
        contentAlignment = Alignment.Center,
    ) {
        if (initials.isEmpty()) {
            Icon(
                imageVector = Icons.Filled.Person,
                contentDescription = null,
                tint = foreground,
                modifier = Modifier.size(size * 0.55f),
            )
        } else {
            Text(
                text = initials,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = foreground,
            )
        }
        if (avatarUrl != null) {
            AsyncImage(
                model = avatarUrl,
                imageLoader = HomelabApp.imageLoader,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
