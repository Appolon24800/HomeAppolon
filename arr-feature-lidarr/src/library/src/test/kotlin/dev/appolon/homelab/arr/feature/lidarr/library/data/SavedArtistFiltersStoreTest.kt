package dev.appolon.homelab.arr.feature.lidarr.library.data

import dev.appolon.homelab.arr.core.FilterState
import dev.appolon.homelab.arr.core.FilterValue
import dev.appolon.homelab.arr.core.ServerId
import dev.appolon.homelab.arr.feature.lidarr.library.FakeEntityStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SavedArtistFiltersStoreTest {

    private val serverId = ServerId("s1")
    private val store = FakeEntityStore()

    @Test
    fun `save then observe round-trips the filter`() = runTest {
        val savedStore = SavedArtistFiltersStore(store, serverId)
        val filter = SavedArtistFilter(
            id = "f1",
            label = "Missing rock",
            monitored = true,
            missing = true,
            genre = setOf("Rock"),
            albumsMin = 2.0,
            albumsMax = 10.0,
        )
        savedStore.save(filter)
        assertEquals(listOf(filter), savedStore.observe().first())
    }

    @Test
    fun `saved filters sort by label and delete removes them`() = runTest {
        val savedStore = SavedArtistFiltersStore(store, serverId)
        savedStore.save(SavedArtistFilter(id = "b", label = "zeta"))
        savedStore.save(SavedArtistFilter(id = "a", label = "Alpha"))
        assertEquals(listOf("Alpha", "zeta"), savedStore.observe().first().map { it.label })
        savedStore.delete("a")
        assertEquals(listOf("zeta"), savedStore.observe().first().map { it.label })
    }

    @Test
    fun `filters scope to their own server`() = runTest {
        val mine = SavedArtistFiltersStore(store, serverId)
        val theirs = SavedArtistFiltersStore(store, ServerId("s2"))
        mine.save(SavedArtistFilter(id = "f1", label = "Mine"))
        assertTrue(theirs.observe().first().isEmpty())
    }

    @Test
    fun `toFilterState and toSavedValues are inverse`() {
        val state = FilterState()
            .with(ArtistLibraryFilters.MONITORED, FilterValue.Toggle(true))
            .with(ArtistLibraryFilters.MISSING, FilterValue.Toggle(false))
            .with(ArtistLibraryFilters.QUALITY, FilterValue.Choice(setOf("1", "2")))
            .with(ArtistLibraryFilters.METADATA, FilterValue.Choice(setOf("3")))
            .with(ArtistLibraryFilters.GENRE, FilterValue.Choice(setOf("Rock")))
            .with(ArtistLibraryFilters.FOLDER, FilterValue.Choice(setOf("/music")))
            .with(ArtistLibraryFilters.TAGS, FilterValue.Choice(setOf("7")))
            .with(ArtistLibraryFilters.ALBUMS, FilterValue.Range(1.0, 5.0))
        val filter = newSavedFilter("Everything", state)
        val restored = filter.toFilterState()
        assertEquals(state.values.keys, restored.values.keys)
        state.values.forEach { (id, value) -> assertEquals(value, restored.values[id]) }
    }

    @Test
    fun `inactive filters drop out of the saved form`() {
        val state = FilterState()
            .with(ArtistLibraryFilters.MONITORED, FilterValue.Toggle(null))
            .with(ArtistLibraryFilters.GENRE, FilterValue.Choice(emptySet()))
        val filter = newSavedFilter("Empty", state)
        assertEquals(null, filter.monitored)
        assertTrue(filter.genre.isEmpty())
        assertTrue(filter.toFilterState().values.isEmpty())
    }
}
