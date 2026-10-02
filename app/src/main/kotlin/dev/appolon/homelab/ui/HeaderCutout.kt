package dev.appolon.homelab.ui

import kotlin.math.ceil
import kotlin.math.sqrt

/** Height of a quarter-circle screen corner at the content's horizontal inset. */
internal fun roundedCornerTopPadding(radius: Int, horizontalPadding: Int, minimum: Int): Int {
    if (radius <= horizontalPadding) return minimum
    val x = (radius - horizontalPadding).toDouble()
    val cornerHeight = radius - sqrt(radius.toDouble() * radius - x * x)
    return maxOf(minimum, ceil(cornerHeight).toInt())
}

/** Cutout bounds in window pixels. Kept independent of Android for geometry tests. */
internal data class HeaderCutout(val left: Int, val top: Int, val right: Int, val bottom: Int)

internal fun headerCutoutTopPadding(
    width: Int,
    titleWidth: Int,
    actionsWidth: Int,
    barHeight: Int,
    cutouts: List<HeaderCutout>,
    leftInset: Int = 0,
    rightInset: Int = 0,
    titlePadding: Int = 0,
    gap: Int = 0,
    titleOnLeft: Boolean = true,
): Int {
    val left = leftInset
    val right = width - rightInset
    val titleStart = if (titleOnLeft) left + titlePadding else right - titlePadding - titleWidth
    val titleEnd = titleStart + titleWidth
    val actionsStart = if (titleOnLeft) right - actionsWidth else left
    val actionsEnd = actionsStart + actionsWidth
    val relevant = cutouts.filter {
        it.top < barHeight && it.bottom > 0 && it.left < right && it.right > left
    }
    val needsPadding = relevant.any {
        val hitsTitle = titleStart < it.right + gap && titleEnd > it.left - gap
        val hitsActions = actionsStart < it.right + gap && actionsEnd > it.left - gap
        hitsTitle || hitsActions || it.bottom + gap > barHeight
    }
    return if (needsPadding) relevant.maxOf { it.bottom + gap } else 0
}
