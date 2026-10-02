package dev.appolon.homelab.ui.components

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import dev.appolon.homelab.ui.HeaderCutout
import dev.appolon.homelab.ui.headerCutoutTopPadding

/** Reserve top space only when the cutout would cover header content. */
@Composable
internal fun homeTopBarInsets(width: Dp, title: String): WindowInsets {
    val density = LocalDensity.current
    val direction = LocalLayoutDirection.current
    val view = LocalView.current
    // Read Compose's observable insets and configuration before the platform bounds.
    val cutoutTop = WindowInsets.displayCutout.getTop(density)
    val configuration = LocalConfiguration.current
    val horizontal = WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)
    val offset = IntArray(2).also(view::getLocationInWindow)
    val cutouts = if (cutoutTop > 0 || configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE) {
        view.rootWindowInsets?.displayCutout?.boundingRects.orEmpty().map {
            HeaderCutout(it.left - offset[0], it.top - offset[1], it.right - offset[0], it.bottom - offset[1])
        }
    } else emptyList()
    val titleWidth = rememberTextMeasurer().measure(
        text = AnnotatedString(title),
        style = MaterialTheme.typography.titleLarge,
        maxLines = 1,
        softWrap = false,
    ).size.width
    val top = with(density) {
        headerCutoutTopPadding(
            width = width.roundToPx(),
            titleWidth = titleWidth,
            actionsWidth = 148.dp.roundToPx(),
            barHeight = 64.dp.roundToPx(),
            cutouts = cutouts,
            leftInset = horizontal.getLeft(this, direction),
            rightInset = horizontal.getRight(this, direction),
            titlePadding = 16.dp.roundToPx(),
            gap = 8.dp.roundToPx(),
            titleOnLeft = direction == LayoutDirection.Ltr,
        )
    }
    // If platform bounds are not available yet, keep the cutout inset until they arrive.
    val safeTop = if (cutoutTop > 0 && cutouts.isEmpty()) cutoutTop else top
    return horizontal
        .union(WindowInsets.statusBars.only(WindowInsetsSides.Top))
        .union(WindowInsets(top = safeTop))
}
