package dev.appolon.homelab.arr.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PagingTest {

    private val items = (0 until 10).toList()

    @Test
    fun sliceMidList() {
        assertEquals(listOf(3, 4, 5), items.slice(PageWindow(3, 3)))
    }

    @Test
    fun sliceClampsAtEnd() {
        assertEquals(listOf(8, 9), items.slice(PageWindow(8, 5)))
    }

    @Test
    fun sliceBeyondEndIsEmpty() {
        assertEquals(emptyList<Int>(), items.slice(PageWindow(10, 5)))
        assertEquals(emptyList<Int>(), emptyList<Int>().slice(PageWindow(0, 5)))
    }

    @Test
    fun sliceReturnsIndependentList() {
        val backing = mutableListOf(1, 2, 3)
        val sliced = backing.slice(PageWindow(0, 2))
        backing.add(4)
        assertEquals(listOf(1, 2), sliced)
    }

    @Test
    fun windowValidation() {
        org.junit.Assert.assertThrows(IllegalArgumentException::class.java) {
            PageWindow(-1, 5)
        }
        org.junit.Assert.assertThrows(IllegalArgumentException::class.java) {
            PageWindow(0, 0)
        }
    }

    @Test
    fun nextWindowAdvancesBySize() {
        assertEquals(PageWindow(20, 10), PageWindow(10, 10).next())
    }

    @Test
    fun rangeInKnownTotalClamps() {
        assertEquals(3..5, PageWindow(3, 3).rangeIn(6))
        assertEquals(8..9, PageWindow(8, 5).rangeIn(10))
        assertEquals(IntRange.EMPTY, PageWindow(10, 5).rangeIn(10))
    }

    @Test
    fun rangeInUnknownTotalIsUnclamped() {
        assertEquals(8..12, PageWindow(8, 5).rangeIn(null))
    }

    @Test
    fun loadStateHelpers() {
        assertTrue(LoadState.Loading.isLoading)
        assertFalse(LoadState.Idle.isLoading)
        assertFalse(LoadState.NotLoading(endOfReached = true).isLoading)
        assertFalse(LoadState.Failed(ArrError.Http(500, null)).isLoading)
    }

    @Test
    fun appendPageAppendsNewKeys() {
        val current = listOf("a" to 1, "b" to 2)
        val page = ArrPage(items = listOf("c" to 3, "d" to 4), total = 4, hasMore = false)
        val merged = appendPage(current, page, { it.first })
        assertEquals(listOf("a" to 1, "b" to 2, "c" to 3, "d" to 4), merged)
    }

    @Test
    fun appendPageReplacesExistingKeysInPlace() {
        val current = listOf("a" to 1, "b" to 2, "c" to 3)
        val page = ArrPage(items = listOf("b" to 20, "d" to 4), total = null, hasMore = true)
        val merged = appendPage(current, page, { it.first })
        assertEquals(listOf("a" to 1, "b" to 20, "c" to 3, "d" to 4), merged)
    }

    @Test
    fun appendPageWithEmptyPageReturnsCurrent() {
        val current = listOf(1, 2)
        val merged = appendPage(current, ArrPage(emptyList()), { it })
        assertEquals(current, merged)
        // Must be a stable no-op, not the same mutable instance issues.
        assertEquals(listOf(1, 2), merged)
    }
}
