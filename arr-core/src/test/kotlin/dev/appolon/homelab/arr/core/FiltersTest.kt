package dev.appolon.homelab.arr.core

import org.junit.Assert.assertEquals
import org.junit.Test

class FiltersTest {

    private data class Movie(
        val title: String,
        val year: Int,
        val runtimeMinutes: Int,
        val genre: String,
        val monitored: Boolean,
    )

    private val library = listOf(
        Movie("Alpha", 2020, 90, "action", true),
        Movie("Beta", 2022, 120, "drama", false),
        Movie("Gamma", 1999, 100, "action", false),
        Movie("Delta", 2022, 150, "comedy", true),
    )

    private val genreSpec = FilterSpec.Choice("genre", listOf("action", "drama", "comedy"))
    private val runtimeSpec = FilterSpec.Range("runtime", 0.0, 300.0)
    private val monitoredSpec = FilterSpec.Toggle("monitored")

    private val definitions: List<FilterDefinition<Movie>> = listOf(
        FilterDefinition(genreSpec) { movie, value ->
            val selected = (value as FilterValue.Choice).selected
            selected.isEmpty() || movie.genre in selected
        },
        FilterDefinition(runtimeSpec) { movie, value ->
            val range = value as FilterValue.Range
            (range.min == null || movie.runtimeMinutes >= range.min) &&
                (range.max == null || movie.runtimeMinutes <= range.max)
        },
        FilterDefinition(monitoredSpec) { movie, value ->
            val toggle = value as FilterValue.Toggle
            toggle.on == null || movie.monitored == toggle.on
        },
    )

    @Test
    fun emptyStateKeepsEverything() {
        assertEquals(library, applyFilters(library, definitions, FilterState()))
        val emptyChoice = FilterState().with("genre", FilterValue.Choice(emptySet()))
        assertEquals(library, applyFilters(library, definitions, emptyChoice))
    }

    @Test
    fun choiceFiltersOrWithinSelection() {
        val state = FilterState().with("genre", FilterValue.Choice(setOf("action", "comedy")))
        val result = applyFilters(library, definitions, state)
        assertEquals(listOf("Alpha", "Gamma", "Delta"), result.map { it.title })
    }

    @Test
    fun rangeIsInclusiveOnBothBounds() {
        val state = FilterState().with(
            "runtime",
            FilterValue.Range(min = 100.0, max = 120.0),
        )
        val result = applyFilters(library, definitions, state)
        assertEquals(listOf("Beta", "Gamma"), result.map { it.title })
    }

    @Test
    fun rangeOpenBounds() {
        val minOnly = applyFilters(
            library,
            definitions,
            FilterState().with("runtime", FilterValue.Range(min = 101.0, max = null)),
        )
        assertEquals(listOf("Beta", "Delta"), minOnly.map { it.title })

        val maxOnly = applyFilters(
            library,
            definitions,
            FilterState().with("runtime", FilterValue.Range(min = null, max = 99.0)),
        )
        assertEquals(listOf("Alpha"), maxOnly.map { it.title })
    }

    @Test
    fun toggleTriState() {
        val on = applyFilters(
            library,
            definitions,
            FilterState().with("monitored", FilterValue.Toggle(true)),
        )
        assertEquals(listOf("Alpha", "Delta"), on.map { it.title })

        val off = applyFilters(
            library,
            definitions,
            FilterState().with("monitored", FilterValue.Toggle(false)),
        )
        assertEquals(listOf("Beta", "Gamma"), off.map { it.title })

        val unset = applyFilters(
            library,
            definitions,
            FilterState().with("monitored", FilterValue.Toggle(null)),
        )
        assertEquals(library, unset)
    }

    @Test
    fun activeFiltersAndTogether() {
        val state = FilterState()
            .with("genre", FilterValue.Choice(setOf("action", "drama")))
            .with("runtime", FilterValue.Range(min = 101.0, max = null))
            .with("monitored", FilterValue.Toggle(false))
        val result = applyFilters(library, definitions, state)
        assertEquals(listOf("Beta"), result.map { it.title })
    }

    @Test
    fun withoutClearsOneFilter() {
        val state = FilterState()
            .with("genre", FilterValue.Choice(setOf("action")))
            .with("monitored", FilterValue.Toggle(true))
            .without("genre")
        val result = applyFilters(library, definitions, state)
        assertEquals(listOf("Alpha", "Delta"), result.map { it.title })
    }

    @Test
    fun inputListIsNotMutated() {
        val original = library.toList()
        val active = FilterState().with("genre", FilterValue.Choice(setOf("action")))
        applyFilters(library, definitions, active)
        assertEquals(original, library)
    }

    @Test(expected = IllegalArgumentException::class)
    fun duplicateDefinitionIdsRejected() {
        val doubled = definitions + FilterDefinition(genreSpec) { _, _ -> true }
        applyFilters(library, doubled, FilterState())
    }

    @Test(expected = IllegalArgumentException::class)
    fun mismatchedValueSpecRejected() {
        applyFilters(
            library,
            definitions,
            FilterState().with("genre", FilterValue.Toggle(true)),
        )
    }

    @Test
    fun stateWithoutDefinitionIsIgnored() {
        val state = FilterState().with("unknown-filter", FilterValue.Toggle(true))
        assertEquals(library, applyFilters(library, definitions, state))
    }

    @Test
    fun specValidation() {
        org.junit.Assert.assertThrows(IllegalArgumentException::class.java) {
            FilterSpec.Choice("genre", listOf("a", "a"))
        }
        org.junit.Assert.assertThrows(IllegalArgumentException::class.java) {
            FilterSpec.Range("size", 10.0, 5.0)
        }
    }

    @Test
    fun sortBySelector() {
        val byYear = SortSpec.by<Movie, Int>("year") { it.year }
        assertEquals(listOf(1999, 2020, 2022, 2022), sortItems(library, byYear).map { it.year })
        val newestFirst = sortItems(library, byYear, descending = true).map { it.year }
        assertEquals(listOf(2022, 2022, 2020, 1999), newestFirst)
    }

    @Test
    fun sortIsStableForEqualKeys() {
        val byYear = SortSpec.by<Movie, Int>("year") { it.year }
        // Beta precedes Delta in the input and both are 2022; order survives.
        assertEquals(
            listOf("Gamma", "Alpha", "Beta", "Delta"),
            sortItems(library, byYear).map { it.title },
        )
    }

    @Test
    fun sortNullablePlacesNullsConfigurably() {
        val movies = library.map { it.copy(year = if (it.title == "Beta") -1 else it.year) }
        val selector: (Movie) -> Int? = { m -> if (m.year == -1) null else m.year }

        val nullsLast = SortSpec.byNullable<Movie, Int>("year", selector)
        assertEquals(
            listOf("Gamma", "Alpha", "Delta", "Beta"),
            sortItems(movies, nullsLast).map { it.title },
        )

        val nullsFirst = SortSpec.byNullable<Movie, Int>("year", selector, nullsLast = false)
        assertEquals(
            listOf("Beta", "Gamma", "Alpha", "Delta"),
            sortItems(movies, nullsFirst).map { it.title },
        )
    }

    @Test
    fun compositeSortChainsThen() {
        val byYearThenTitle = SortSpec.by<Movie, Int>("year") { it.year }
            .then(SortSpec.by<Movie, String>("title") { it.title })
        assertEquals(
            listOf("Gamma", "Alpha", "Beta", "Delta"),
            sortItems(library, byYearThenTitle).map { it.title },
        )
        // Within 2022 the tiebreak must survive descending too.
        assertEquals(
            listOf("Delta", "Beta", "Alpha", "Gamma"),
            sortItems(library, byYearThenTitle, descending = true).map { it.title },
        )
    }
}
