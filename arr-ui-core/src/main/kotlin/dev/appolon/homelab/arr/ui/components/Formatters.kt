package dev.appolon.homelab.arr.ui.components

import java.util.Locale

/**
 * Formats a transfer rate the way the *arr web UIs do: binary steps, one
 * decimal below 100 and none above, so queue text stays steady in width.
 */
fun formatDataRate(bytesPerSecond: Long): String {
    if (bytesPerSecond <= 0) return "0 B/s"
    val units = arrayOf("B/s", "KB/s", "MB/s", "GB/s", "TB/s")
    var value = bytesPerSecond.toDouble()
    var unit = 0
    while (value >= 1024.0 && unit < units.lastIndex) {
        value /= 1024.0
        unit++
    }
    return if (value >= 100.0 || unit == 0) {
        String.format(Locale.ROOT, "%.0f %s", value, units[unit])
    } else {
        String.format(Locale.ROOT, "%.1f %s", value, units[unit])
    }
}

/** Compact duration for queue ETAs: "45 s", "1 min 30 s", "1 h 12 min". */
fun formatEta(seconds: Long): String {
    if (seconds <= 0) return "0 s"
    if (seconds < 60) return "$seconds s"
    val totalMinutes = seconds / 60
    if (totalMinutes < 60) {
        val rest = seconds % 60
        return if (rest == 0L) "$totalMinutes min" else "$totalMinutes min $rest s"
    }
    val hours = totalMinutes / 60
    val restMinutes = totalMinutes % 60
    return if (restMinutes == 0L) "$hours h" else "$hours h $restMinutes min"
}
