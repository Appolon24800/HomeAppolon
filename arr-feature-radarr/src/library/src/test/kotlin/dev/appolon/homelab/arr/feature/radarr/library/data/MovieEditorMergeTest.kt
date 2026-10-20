package dev.appolon.homelab.arr.feature.radarr.library.data

import dev.appolon.homelab.arr.api.radarr.model.ApplyTags
import dev.appolon.homelab.arr.api.radarr.model.MediaCover
import dev.appolon.homelab.arr.api.radarr.model.MediaCoverTypes
import dev.appolon.homelab.arr.api.radarr.model.MovieEditorResource
import dev.appolon.homelab.arr.api.radarr.model.MovieResource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MovieEditorMergeTest {

    private val movies = listOf(
        MovieResource(id = 1, monitored = false, qualityProfileId = 1, tags = setOf(1, 2)),
        MovieResource(id = 2, monitored = true, qualityProfileId = 2, tags = setOf(3)),
        MovieResource(id = 3, monitored = true, qualityProfileId = 3, tags = setOf()),
    )

    @Test
    fun `null fields leave every movie untouched`() {
        val editor = MovieEditorResource(movieIds = listOf(1, 2))
        assertEquals(movies, applyMovieEditor(movies, editor))
    }

    @Test
    fun `set fields apply only to listed movies`() {
        val editor = MovieEditorResource(
            movieIds = listOf(1, 2),
            monitored = true,
            qualityProfileId = 7,
        )
        val merged = applyMovieEditor(movies, editor)
        assertEquals(true, merged[0].monitored)
        assertEquals(7, merged[0].qualityProfileId)
        assertEquals(true, merged[1].monitored)
        assertEquals(7, merged[1].qualityProfileId)
        assertEquals(true, merged[2].monitored)
        assertEquals(3, merged[2].qualityProfileId)
    }

    @Test
    fun `tags add merges with current tags`() {
        val editor = MovieEditorResource(
            movieIds = listOf(1),
            tags = listOf(5),
            applyTags = ApplyTags.ADD,
        )
        val merged = applyMovieEditor(movies, editor)
        assertEquals(setOf(1, 2, 5), merged[0].tags)
    }

    @Test
    fun `tags remove subtracts only the listed tags`() {
        val editor = MovieEditorResource(
            movieIds = listOf(1),
            tags = listOf(2, 9),
            applyTags = ApplyTags.REMOVE,
        )
        val merged = applyMovieEditor(movies, editor)
        assertEquals(setOf(1), merged[0].tags)
    }

    @Test
    fun `tags replace swaps the set`() {
        val editor = MovieEditorResource(
            movieIds = listOf(1, 3),
            tags = listOf(8),
            applyTags = ApplyTags.REPLACE,
        )
        val merged = applyMovieEditor(movies, editor)
        assertEquals(setOf(8), merged[0].tags)
        assertEquals(setOf(8), merged[2].tags)
    }

    @Test
    fun `empty selection changes nothing`() {
        val editor = MovieEditorResource(movieIds = emptyList(), monitored = true)
        assertEquals(movies, applyMovieEditor(movies, editor))
    }

    @Test
    fun `tag parsing accepts separators and rejects words`() {
        assertEquals(listOf(1, 2, 3), parseTagIds("1, 2 3"))
        assertEquals(emptyList<Int>(), parseTagIds(""))
        assertNull(parseTagIds("1, drama"))
        assertNull(parseTagIds("x"))
    }
}

class MoviePosterUrlsTest {

    @Test
    fun `local poster wins and builds from the mediacover path`() {
        val movie = MovieResource(
            id = 12,
            images = listOf(
                MediaCover(
                    coverType = MediaCoverTypes.POSTER,
                    url = "/api/v3/mediacover/12/poster.jpg",
                ),
            ),
        )
        assertEquals(
            "http://host:30025/radarr/api/v3/mediacover/12/poster.jpg",
            MoviePosterUrls.posterUrlOf(movie, "http://host:30025/radarr/"),
        )
    }

    @Test
    fun `remote poster serves movies without local covers`() {
        val movie = MovieResource(
            id = 13,
            images = listOf(
                MediaCover(coverType = MediaCoverTypes.POSTER, remoteUrl = "/abc.jpg"),
            ),
        )
        assertEquals(
            "https://image.tmdb.org/t/p/w500/abc.jpg",
            MoviePosterUrls.posterUrlOf(movie, "http://host:30025/radarr/"),
        )
    }

    @Test
    fun `remotePoster field is the last resort`() {
        val movie = MovieResource(id = 14, remotePoster = "https://cdn.example/x.jpg")
        assertEquals("https://cdn.example/x.jpg", MoviePosterUrls.posterUrlOf(movie, "http://h/"))
    }

    @Test
    fun `no poster anywhere returns null for the monogram fallback`() {
        assertTrue(MoviePosterUrls.posterUrlOf(MovieResource(id = 15), "http://h/") == null)
    }
}
