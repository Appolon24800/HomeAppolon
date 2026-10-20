package dev.appolon.homelab.arr.feature.radarr.detail

import dev.appolon.homelab.arr.api.radarr.model.CreditResource
import dev.appolon.homelab.arr.api.radarr.model.CreditType
import dev.appolon.homelab.arr.api.radarr.model.MediaCover
import dev.appolon.homelab.arr.api.radarr.model.MediaCoverTypes
import dev.appolon.homelab.arr.api.radarr.model.MovieResource
import dev.appolon.homelab.arr.core.ActionId
import dev.appolon.homelab.arr.core.AppType
import dev.appolon.homelab.arr.core.ServerId
import dev.appolon.homelab.arr.data.QueuedAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pure logic coverage: URLs, ordering, freshness, queue attribution, patches. */
class RadarrDetailLogicTest {

    @Test
    fun `server path resolves inside a path-prefixed base url`() {
        assertEquals(
            "http://host:30025/radarr/api/v3/mediacover/7/poster.jpg",
            RadarrDetailLogic.serverPath("http://host:30025/radarr/", "/api/v3/mediacover/7/poster.jpg"),
        )
        assertEquals(
            "http://host/api/v3/mediacover/7/poster.jpg",
            RadarrDetailLogic.serverPath("http://host", "/api/v3/mediacover/7/poster.jpg"),
        )
        assertNull(RadarrDetailLogic.serverPath("http://host", "  "))
        assertEquals(
            "https://cdn.example/x.jpg",
            RadarrDetailLogic.serverPath("http://host", "https://cdn.example/x.jpg"),
        )
    }

    @Test
    fun `poster prefers server cover then remote fallback`() {
        val withLocal = MovieResource(
            id = 1,
            images = listOf(MediaCover(coverType = MediaCoverTypes.POSTER, url = "/api/v3/mediacover/1/poster.jpg")),
            remotePoster = "/remote.jpg",
        )
        assertEquals(
            "http://radarr.test/api/v3/mediacover/1/poster.jpg",
            RadarrDetailLogic.posterUrl("http://radarr.test/", withLocal),
        )

        val remoteOnly = MovieResource(id = 2, remotePoster = "/remotePoster.jpg")
        assertEquals(
            "https://image.tmdb.org/t/p/w500/remotePoster.jpg",
            RadarrDetailLogic.posterUrl("http://radarr.test/", remoteOnly),
        )

        assertNull(RadarrDetailLogic.posterUrl("http://radarr.test/", MovieResource(id = 3)))
    }

    @Test
    fun `backdrop uses fanart`() {
        val movie = MovieResource(
            id = 4,
            images = listOf(
                MediaCover(coverType = MediaCoverTypes.POSTER, url = "/p.jpg"),
                MediaCover(coverType = MediaCoverTypes.FANART, remoteUrl = "/fanart.jpg"),
            ),
        )
        assertEquals(
            "https://image.tmdb.org/t/p/w500/fanart.jpg",
            RadarrDetailLogic.backdropUrl("http://radarr.test/", movie),
        )
        assertNull(RadarrDetailLogic.backdropUrl("http://radarr.test/", MovieResource(id = 5)))
    }

    @Test
    fun `headshot resolves local url before remote`() {
        val credit = CreditResource(
            id = 9,
            images = listOf(
                MediaCover(coverType = MediaCoverTypes.HEADSHOT, url = "/api/v3/mediacover/credit/9/headshot.jpg"),
            ),
        )
        assertEquals(
            "http://radarr.test/api/v3/mediacover/credit/9/headshot.jpg",
            RadarrDetailLogic.headshotUrl("http://radarr.test/", credit),
        )
        assertNull(RadarrDetailLogic.headshotUrl("http://radarr.test/", CreditResource(id = 10)))
    }

    @Test
    fun `cast sorts by credited order and crew by job`() {
        val credits = listOf(
            CreditResource(id = 1, personName = "Zed", type = CreditType.CAST, order = 2),
            CreditResource(id = 2, personName = "Amy", type = CreditType.CAST, order = 1),
            CreditResource(id = 3, personName = "Director", type = CreditType.CREW, job = "Director"),
            CreditResource(id = 4, personName = "Writer", type = CreditType.CREW, job = "Screenplay"),
            CreditResource(id = 5, personName = "Late", type = CreditType.CAST, order = 0),
        )
        assertEquals(listOf("Amy", "Zed", "Late"), RadarrDetailLogic.castOf(credits).map { it.personName })
        assertEquals(
            listOf("Director", "Writer"),
            RadarrDetailLogic.crewOf(credits).map { it.personName },
        )
    }

    @Test
    fun `freshness respects null, window, and skew`() {
        val ttl = 60_000L
        assertFalse(RadarrDetailLogic.isFresh(null, nowMillis = 1_000, ttlMillis = ttl))
        assertTrue(RadarrDetailLogic.isFresh(1_000, nowMillis = 60_999, ttlMillis = ttl))
        assertFalse(RadarrDetailLogic.isFresh(1_000, nowMillis = 61_000, ttlMillis = ttl))
        // A device clock behind the stamp is not fresh either.
        assertFalse(RadarrDetailLogic.isFresh(2_000, nowMillis = 1_000, ttlMillis = ttl))
    }

    @Test
    fun `queued actions are attributed by path and command body`() {
        val movieUpdate = action("PUT", "api/v3/movie/42")
        val otherMovie = action("PUT", "api/v3/movie/43")
        val search = action("POST", "api/v3/command", """{"name":"MoviesSearch","movieIds":[42]}""")
        val rename = action("POST", "api/v3/command", """{"name":"RenameFiles","movieId":42}""")
        val foreignCommand = action("POST", "api/v3/command", """{"name":"MoviesSearch","movieIds":[7]}""")
        val attributed = RadarrDetailLogic.actionsForMovie(
            listOf(movieUpdate, otherMovie, search, rename, foreignCommand),
            movieId = 42,
        )
        assertEquals(listOf(movieUpdate, search, rename), attributed)
    }

    @Test
    fun `edits patch only the editable fields`() {
        val movie = MovieResource(
            id = 6,
            title = "Title",
            monitored = false,
            qualityProfileId = 1,
            path = "/old",
            tags = setOf(1),
            tmdbId = 99,
        )
        val patched = RadarrDetailLogic.applyEdits(
            movie,
            EditableMovieUi(monitored = true, qualityProfileId = 7, path = "/new", tags = setOf(2, 3)),
        )
        assertTrue(patched.monitored)
        assertEquals(7, patched.qualityProfileId)
        assertEquals("/new", patched.path)
        assertEquals(setOf(2, 3), patched.tags)
        // Untouched fields ride along for the full-resource PUT.
        assertEquals("Title", patched.title)
        assertEquals(99, patched.tmdbId)
    }

    @Test
    fun `command bodies carry movie scoping`() {
        val search = RadarrDetailLogic.searchCommandBody(5)
        assertEquals("MoviesSearch", search["name"]?.toString()?.trim('"'))
        assertEquals("[5]", search["movieIds"]?.toString())

        val rename = RadarrDetailLogic.renameCommandBody(5)
        assertEquals("RenameFiles", rename["name"]?.toString()?.trim('"'))
        assertEquals("5", rename["movieId"]?.toString())

        val scan = RadarrDetailLogic.importScanCommandBody("/movies/Foo")
        assertEquals("DownloadedMoviesScan", scan["name"]?.toString()?.trim('"'))
        assertEquals("\"/movies/Foo\"", scan["path"]?.toString())
    }

    private fun action(method: String, path: String, body: String? = null) = QueuedAction(
        actionId = ActionId("$method:$path"),
        serverId = ServerId("s1"),
        appType = AppType.RADARR,
        method = method,
        path = path,
        bodyJson = body,
        description = "test",
        enqueuedAtMillis = 1L,
    )
}
