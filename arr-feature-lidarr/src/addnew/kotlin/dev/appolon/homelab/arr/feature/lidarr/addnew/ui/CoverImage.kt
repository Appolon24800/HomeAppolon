package dev.appolon.homelab.arr.feature.lidarr.addnew.ui

import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.ImageLoader
import coil3.compose.AsyncImage

/**
 * Cover thumbnail tolerating a null image loader (previews, tests): with a
 * loader it rides the authenticated OkHttp client; without it falls back to
 * the singleton loader, which remote CDN art works with.
 */
@Composable
internal fun CoverImage(
    url: String?,
    contentDescription: String,
    imageLoader: ImageLoader?,
    width: Dp,
    height: Dp,
    cornerRadius: Dp = 8.dp,
) {
    val modifier = Modifier
        .size(width = width, height = height)
        .clip(RoundedCornerShape(cornerRadius))
    if (imageLoader != null) {
        AsyncImage(
            model = url,
            imageLoader = imageLoader,
            contentDescription = contentDescription,
            contentScale = ContentScale.Crop,
            modifier = modifier,
        )
    } else {
        AsyncImage(
            model = url,
            contentDescription = contentDescription,
            contentScale = ContentScale.Crop,
            modifier = modifier,
        )
    }
}
