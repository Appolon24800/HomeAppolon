package dev.appolon.homelab.arr.feature.radarr.library.data

import dev.appolon.homelab.arr.core.FilterState
import dev.appolon.homelab.arr.core.FilterValue
import dev.appolon.homelab.arr.core.ServerId
import dev.appolon.homelab.arr.feature.radarr.library.FakeEntityStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SavedMovieFiltersStoreTest {

    private val store = FakeEntityStore()
    private val saved = SavedMovieFiltersStore(store, ServerId("s1"))

    @Test
    fun `saved filters round trip through the entity store`() = runTest {
        val filter = SavedMovieFilter(
            id = "f1",
            label = "Missing 4K",
            missing = true,
            quality = setOf("2"),
            genre = setOf("Drama"),
            yearMin = 2000.0,
        )
        saved.save(filter)

        val observed = saved.observe()
        // Drain to the current value.
        val rows = observed.first()
        assertEquals(listOf(filter), rows)
    }

    @Test
    fun `saved filters map to and from framework state`() {
        val filter = SavedMovieFilter(
            id = "f2",
            label = "Unwatched comedy",
            monitored = true,
            tags = setOf("3", "4"),
            yearMax = 2010.0,
        )
        val state: FilterState = filter.toFilterState()
        assertEquals(FilterValue.Toggle(true), state.value(MovieLibraryFilters.MONITORED))
        assertEquals(FilterValue.Choice(setOf("3", "4")), state.value(MovieLibraryFilters.TAGS))
        assertEquals(FilterValue.Range(null, 2010.0), state.value(MovieLibraryFilters.YEAR))

        val rebuilt = newSavedFilter("copy", state)
        assertEquals(true, rebuilt.monitored)
        assertEquals(setOf("3", "4"), rebuilt.tags)
        assertEquals(null, rebuilt.yearMin)
        assertEquals(2010.0, rebuilt.yearMax!!, 0.0)
    }

    @Test
    fun `deleting removes only that filter`() = runTest {
        saved.save(SavedMovieFilter(id = "a", label = "One"))
        saved.save(SavedMovieFilter(id = "b", label = "Two"))
        saved.delete("a")
        val remaining = saved.observe().first()
        assertEquals(listOf("b"), remaining.map { it.id })
    }

    @Test
    fun `empty state saves as an inactive filter`() {
        val filter = newSavedFilter("Everything", FilterState())
        assertEquals(FilterState(), filter.toFilterState())
        assertTrue(filter.toFilterState().values.isEmpty())
    }
}
