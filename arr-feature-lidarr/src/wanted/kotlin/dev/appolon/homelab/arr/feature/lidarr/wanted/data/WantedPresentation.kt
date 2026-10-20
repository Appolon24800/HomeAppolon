package dev.appolon.homelab.arr.feature.lidarr.wanted.data

import dev.appolon.homelab.arr.api.lidarr.model.AlbumResource
import dev.appolon.homelab.arr.core.FilterDefinition
import dev.appolon.homelab.arr.core.FilterSpec
import dev.appolon.homelab.arr.core.FilterState
import dev.appolon.homelab.arr.core.FilterValue
import dev.appolon.homelab.arr.core.SortSpec
import dev.appolon.homelab.arr.core.applyFilters
import dev.appolon.homelab.arr.core.sortItems
import java.util.Locale
import kotlinx.serialization.Serializable

/**
 * The two Lidarr wanted lists; each backs one tab and one entity type in the
 * store. Wire names double as route parameters and meta row ids.
 */
enum class WantedTab(val wireName: String) {
    MISSING("missing"),
    CUTOFF("cutoff"),
}

/**
 * Server-reported album totals for both lists, the numbers the shell badges
 * the Wanted entry with. Null until the matching tab finished its first
 * refresh.
 */
@Serializable
data class WantedCounts(
    val missing: Int? = null,
    val cutoff: Int? = null,
)

/**
 * Filter and presentation logic for the wanted album lists, pure so tests
 * cover it directly. Filter ids: `monitored` (Toggle), `releaseType` (Choice
 * over lowercased primary album types). The rendered order is artist, release
 * date, album title, so it stays stable no matter which server page an
 * album arrived on.
 */
object WantedFilters {

    const val MONITORED = "monitored"
    const val RELEASE_TYPE = "releaseType"

    /** The three release types the filter bar names explicitly. */
    val CANONICAL_RELEASE_TYPES = listOf("album", "single", "ep")

    /** Choice option universe: release types present in the active list. */
    data class Options(val releaseTypes: List<String> = emptyList()) {
        fun with(seenTypes: List<String>): Options = Options(
            releaseTypes = (releaseTypes + seenTypes).distinct().sortedWith(
                compareBy(
                    { type -> CANONICAL_RELEASE_TYPES.indexOf(type).let { if (it < 0) CANONICAL_RELEASE_TYPES.size else it } },
                    { it },
                ),
            ),
        )
    }

    fun optionsFrom(albums: List<AlbumResource>): Options =
        Options().with(albums.mapNotNull(::releaseTypeOf))

    /** Specs for the filter controls, in bar order. */
    fun specs(options: Options): List<FilterSpec> = listOf(
        FilterSpec.Toggle(MONITORED),
        FilterSpec.Choice(RELEASE_TYPE, options.releaseTypes),
    )

    /** Matching logic for every spec in [specs]. */
    fun definitions(options: Options): List<FilterDefinition<AlbumResource>> = listOf(
        FilterDefinition(FilterSpec.Toggle(MONITORED)) { album, value ->
            (value as FilterValue.Toggle).on?.let { album.monitored == it } ?: true
        },
        FilterDefinition(
            FilterSpec.Choice(RELEASE_TYPE, options.releaseTypes),
        ) { album, value ->
            val selected = (value as FilterValue.Choice).selected
            releaseTypeOf(album)?.let { it in selected } ?: false
        },
    )
}

/** One rendered wanted list after filters; ordering is artist, date, title, id. */
data class WantedPresentation(
    val all: List<AlbumResource>,
    val endReached: Boolean,
) {
    companion object {
        val EMPTY = WantedPresentation(emptyList(), endReached = true)
    }
}

/** Applies filters and the stable artist sort over one cached wanted list. */
fun presentWanted(
    albums: List<AlbumResource>,
    options: WantedFilters.Options,
    filterState: FilterState,
): WantedPresentation {
    val filtered = applyFilters(albums, WantedFilters.definitions(options), filterState)
    val sorted = sortItems(filtered, WANTED_SORT)
    return WantedPresentation(all = sorted, endReached = true)
}

/** Artist-first ordering shared by both tabs; ids keep it total and stable. */
private val WANTED_SORT: SortSpec<AlbumResource> =
    SortSpec.byNullable<AlbumResource, String>(
        "artist",
        { album ->
            album.artist?.sortName?.takeIf { it.isNotBlank() }
                ?: album.artist?.artistName?.takeIf { it.isNotBlank() }
        },
    )
        .then(SortSpec.byNullable<AlbumResource, String>("releaseDate", { it.releaseDate }))
        .then(SortSpec.byNullable<AlbumResource, String>("title", { it.title }))
        .then(SortSpec.by<AlbumResource, Int>("id") { it.id })

/** Lowercased primary album type, the release type filter's option id. */
fun releaseTypeOf(album: AlbumResource): String? =
    album.albumType?.trim()?.lowercase(Locale.ROOT)?.takeIf { it.isNotEmpty() }

/** Artist display name, falling back to the id when the payload carries none. */
fun artistNameOf(album: AlbumResource): String =
    album.artist?.artistName?.takeIf { it.isNotBlank() } ?: "#${album.artistId}"

/** Album display title, falling back to the id when the payload carries none. */
fun albumTitleOf(album: AlbumResource): String =
    album.title?.takeIf { it.isNotBlank() } ?: "#${album.id}"
