package dev.appolon.homelab.arr.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.ImageLoader
import coil3.compose.AsyncImage
import dev.appolon.homelab.arr.ui.R

/**
 * Standard 2:3 poster tile for movie, series, and album grids. The title sits
 * on a scrim over the image and [badges] overlays the top-end corner (quality,
 * monitored, missing...). Until a poster decodes, the tile shows a monogram in
 * the dashboard's primary-container style; posters are opaque and cropped, so
 * the fallback simply disappears once the image arrives.
 *
 * [imageLoader] lets feature modules pass a loader that attaches server auth
 * (X-Api-Key) to poster URLs; the singleton loader is used when null.
 */
@Composable
fun PosterCard(
    title: String,
    posterUrl: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    imageLoader: ImageLoader? = null,
    badges: @Composable () -> Unit = {},
) {
    val posterDescription = stringResource(R.string.arr_cd_poster, title)
    val shape = RoundedCornerShape(12.dp)
    Card(
        shape = shape,
        modifier = modifier
            .aspectRatio(2f / 3f)
            .clip(shape)
            .clickable(onClick = onClick),
    ) {
        Box(Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = posterMonogram(title),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
            posterUrl?.let { url ->
                if (imageLoader != null) {
                    AsyncImage(
                        model = url,
                        contentDescription = posterDescription,
                        imageLoader = imageLoader,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    AsyncImage(
                        model = url,
                        contentDescription = posterDescription,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(72.dp)
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color.Black.copy(alpha = 0.75f)),
                        ),
                    ),
            ) {
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = PosterOverlayText,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (subtitle != null) {
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.labelSmall,
                            color = PosterOverlayText.copy(alpha = 0.8f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp),
            ) {
                badges()
            }
        }
    }
}

/** Overlay text tone, matching the dashboard's light-on-dark text token. */
private val PosterOverlayText = Color(0xFFF2F2F2)

/** First character used for the poster monogram fallback. */
internal fun posterMonogram(title: String): String =
    title.trim().take(1).uppercase().ifEmpty { "?" }
