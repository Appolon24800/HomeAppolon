package dev.appolon.homelab.arr.core

/**
 * Load progress of one paging source (refresh, prepend, or append) for lazy
 * list screens. Pure state: screens map it to LCE visuals, it never triggers
 * anything itself.
 */
sealed interface LoadState {
    data object Idle : LoadState
    data object Loading : LoadState
    data class NotLoading(val endOfReached: Boolean = false) : LoadState
    data class Failed(val error: ArrError) : LoadState

    val isLoading: Boolean
        get() = this is Loading
}

/**
 * Half-open window `[offset, offset + size)` into a list, the unit lazy list
 * screens fetch and render in.
 */
data class PageWindow(val offset: Int, val size: Int) {
    init {
        require(offset >= 0) { "offset must be >= 0: $offset" }
        require(size > 0) { "size must be > 0: $size" }
    }

    /** The window immediately after this one, keeping the page size. */
    fun next(): PageWindow = PageWindow(offset + size, size)

    /**
     * Indexes this window covers within [total] items; empty once the window
     * starts past the end. A null total (endpoint did not report one) returns
     * the unclamped range.
     */
    fun rangeIn(total: Int?): IntRange {
        if (total != null) {
            if (offset >= total) return IntRange.EMPTY
            return offset until minOf(offset + size, total)
        }
        return offset until (offset + size)
    }
}

/** Independent slice of [this] covered by [window]; empty when past the end. */
fun <T> List<T>.slice(window: PageWindow): List<T> {
    if (window.offset >= size) return emptyList()
    return subList(window.offset, minOf(window.offset + window.size, size)).toList()
}

/**
 * Merges a fetched page into the accumulated items of a lazy list: page items
 * whose [key] already exists replace the previous value in place, new keys
 * append in page order. Use for append loads; refresh replaces the list.
 */
fun <T, K> appendPage(current: List<T>, page: ArrPage<T>, key: (T) -> K): List<T> {
    if (page.items.isEmpty()) return current
    val indexOfKey = HashMap<K, Int>(current.size)
    current.forEachIndexed { index, item -> indexOfKey[key(item)] = index }
    val merged = current.toMutableList()
    for (item in page.items) {
        val existing = indexOfKey[key(item)]
        if (existing == null) {
            indexOfKey[key(item)] = merged.size
            merged.add(item)
        } else {
            merged[existing] = item
        }
    }
    return merged
}
