package dev.appolon.homelab.arr.feature.sonarr.library.data

import dev.appolon.homelab.arr.core.FilterState
import dev.appolon.homelab.arr.core.FilterValue
import dev.appolon.homelab.arr.core.ServerId
import dev.appolon.homelab.arr.feature.sonarr.library.FakeEntityStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Covers saved custom filters round-tripping through the EntityStore. */
class SavedSeriesFiltersStoreTest {

    private val serverId = ServerId("server-1")
    private val store = FakeEntityStore()
    private val saved = SavedSeriesFiltersStore(store, serverId)

    @Test
    fun `save then observe returns the filter sorted by label`() = runTest {
        saved.save(newSavedFilter("Zeta", stateOf()))
        saved.save(newSavedFilter("Alpha", stateOf()))
        val labels = saved.observe().first().map { it.label }
        assertEquals(listOf("Alpha", "Zeta"), labels)
    }

    @Test
    fun `saved filter state round trips through serialization`() = runTest {
        val original = stateOf()
        saved.save(newSavedFilter("Mine", original))
        val loaded = saved.observe().first().single()

        assertEquals(original, loaded.toFilterState())
        assertEquals("Mine", loaded.label)
    }

    @Test
    fun `delete removes only the targeted filter`() = runTest {
        val keep = newSavedFilter("Keep", stateOf())
        val drop = newSavedFilter("Drop", FilterState())
        saved.save(keep)
        saved.save(drop)
        saved.delete(drop.id)
        val remaining = saved.observe().first()
        assertEquals(listOf("Keep"), remaining.map { it.label })
        assertTrue(remaining.single().toFilterState().isActive(SeriesLibraryFilters.MONITORED))
    }

    @Test
    fun `stores are scoped per server`() = runTest {
        val other = SavedSeriesFiltersStore(FakeEntityStore(), ServerId("server-2"))
        saved.save(newSavedFilter("Mine", FilterState()))
        assertTrue(other.observe().first().isEmpty())
    }

    private fun stateOf(): FilterState = FilterState()
        .with(SeriesLibraryFilters.MONITORED, FilterValue.Toggle(true))
        .with(SeriesLibraryFilters.MISSING, FilterValue.Toggle(true))
        .with(SeriesLibraryFilters.QUALITY, FilterValue.Choice(setOf("1")))
        .with(SeriesLibraryFilters.GENRE, FilterValue.Choice(setOf("Drama")))
        .with(SeriesLibraryFilters.NETWORK, FilterValue.Choice(setOf("BBC")))
        .with(SeriesLibraryFilters.FOLDER, FilterValue.Choice(setOf("Alpha")))
        .with(SeriesLibraryFilters.TAGS, FilterValue.Choice(setOf("10")))
        .with(SeriesLibraryFilters.SEASONS, FilterValue.Range(1.0, 5.0))
        .with(SeriesLibraryFilters.YEAR, FilterValue.Range(2010.0, 2020.0))
}
