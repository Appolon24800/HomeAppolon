package dev.appolon.homelab.arr.core

/** Shared cache freshness limits, in milliseconds. */
object CacheTtl {
    /** Library data refreshes after five minutes to pick up server-side edits. */
    const val LIBRARY: Long = 5 * 60 * 1000

    /** Active download queues refresh after fifteen seconds. */
    const val QUEUE: Long = 15 * 1000

    /** Calendar windows refresh after ten minutes as release dates change. */
    const val CALENDAR: Long = 10 * 60 * 1000

    /** Settings and picker options refresh after one minute. */
    const val SETTINGS: Long = 60 * 1000
}
