package dev.appolon.homelab.arr.api.bazarr

import dev.appolon.homelab.arr.net.ArrJson
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import retrofit2.HttpException
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

/** Each documented operation exercises both its request contract and an HTTP failure. */
@RunWith(Parameterized::class)
class BazarrLibraryApiTest(private val case: LibraryCase) {
    @Test
    fun happyPath() = runTest {
        val server = MockWebServer()
        server.start()
        try {
            val fixture = requireNotNull(javaClass.getResource(
                "/fixtures/bazarr/${case.tag}/response.json"
            )).readText()
            server.enqueue(MockResponse().setResponseCode(case.status).apply {
                if (case.status != 204) setBody(fixture)
            })
            val result = case.call(retrofit(server))
            if (result is Response<*>) {
                assertEquals(case.status, result.code())
                if (case.status == 200) {
                    assertEquals(ArrJson.parseToJsonElement(fixture), result.body())
                }
            } else {
                assertEquals(ArrJson.parseToJsonElement(fixture), result)
            }
            val request = server.takeRequest()
            assertEquals(case.method, request.method)
            val url = requireNotNull(request.requestUrl)
            assertEquals("/prefix/api/${case.path}", url.encodedPath)
            assertEquals(case.query.keys, url.queryParameterNames)
            case.query.forEach { (key, values) ->
                assertEquals(values, url.queryParameterValues(key))
            }
            if (case.upload) {
                assertTrue(request.getHeader("Content-Type")!!.startsWith("multipart/form-data"))
                val body = request.body.readUtf8()
                assertTrue(body.contains("name=\"file\"; filename=\"subtitle.srt\""))
                assertTrue(body.contains("spec fixture subtitle"))
            }
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun errorPath() = runTest {
        val server = MockWebServer()
        server.start()
        try {
            server.enqueue(MockResponse().setResponseCode(401).setBody("{}"))
            try {
                val result = case.call(retrofit(server))
                assertTrue("Expected status-bearing failure", result is Response<*>)
                assertEquals(401, (result as Response<*>).code())
            } catch (error: HttpException) {
                assertEquals(401, error.code())
            }
        } finally {
            server.shutdown()
        }
    }

    private fun retrofit(server: MockWebServer): Retrofit = Retrofit.Builder()
        .baseUrl(server.url("/prefix/"))
        .addConverterFactory(ArrJson.asConverterFactory("application/json".toMediaType()))
        .build()

    companion object {
        private fun upload() = MultipartBody.Part.createFormData(
            "file", "subtitle.srt",
            "spec fixture subtitle".toRequestBody("text/plain".toMediaType())
        )

        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun cases(): List<Array<Any>> {
            val cases = mutableListOf<LibraryCase>()
            fun add(
                tag: String, method: String, path: String, status: Int = 200,
                query: Map<String, List<String>> = emptyMap(), upload: Boolean = false,
                call: suspend (Retrofit) -> Any,
            ) {
                cases += LibraryCase(tag, method, path, status, query, upload, call)
            }
            val fullFilters = mapOf(
                "start" to listOf("0"), "length" to listOf("2"),
                "sort_by" to listOf("title"), "sort_order" to listOf("desc"),
                "monitored" to listOf("true"), "profileid" to listOf("none"),
                "missing" to listOf("false"), "audio_language" to listOf("English"),
                "tags[]" to listOf("a", "b"),
            )
            add("series", "GET", "series", query = fullFilters +
                ("seriesid[]" to listOf("1", "2"))) {
                BazarrLibraryApis.series(it).getSeries(
                    0, 2, listOf(1, 2), "title", "desc", "true", "none", "false",
                    "English", listOf("a", "b"),
                )
            }
            add("movies", "GET", "movies", query = fullFilters +
                ("radarrid[]" to listOf("1", "2"))) {
                BazarrLibraryApis.movies(it).getMovies(
                    0, 2, listOf(1, 2), "title", "desc", "true", "none", "false",
                    "English", listOf("a", "b"),
                )
            }
            add("series", "PATCH", "series", 204,
                mapOf("seriesid" to listOf("1"), "action" to listOf("scan-disk"))) {
                BazarrLibraryApis.series(it).patchSeries(1, "scan-disk")
            }
            add("movies", "PATCH", "movies", 204,
                mapOf("radarrid" to listOf("1"), "action" to listOf("sync"))) {
                BazarrLibraryApis.movies(it).patchMovies(1, "sync")
            }
            add("series", "POST", "series", 204, mapOf(
                "seriesid" to listOf("1", "2"), "profileid" to listOf("none", "3"),
            )) { BazarrLibraryApis.series(it).postSeries(listOf(1, 2), listOf("none", "3")) }
            add("movies", "POST", "movies", 204, mapOf(
                "radarrid" to listOf("1", "2"), "profileid" to listOf("none", "3"),
            )) { BazarrLibraryApis.movies(it).postMovies(listOf(1, 2), listOf("none", "3")) }
            add("episodes", "GET", "episodes", query = mapOf(
                "seriesid[]" to listOf("1", "2"), "episodeid[]" to listOf("3", "4"),
            )) { BazarrLibraryApis.episodes(it).getEpisodes(listOf(1, 2), listOf(3, 4)) }
            add("series-tags", "GET", "series/tags") {
                BazarrLibraryApis.seriesTags(it).getSeriesTags()
            }
            add("movies-tags", "GET", "movies/tags") {
                BazarrLibraryApis.moviesTags(it).getMoviesTags()
            }
            add("badges", "GET", "badges") { BazarrLibraryApis.badges(it).getBadges() }
            val paging = mapOf("start" to listOf("1"), "length" to listOf("2"))
            add("episodes-history", "GET", "episodes/history", query = paging +
                ("episodeid" to listOf("3"))) {
                BazarrLibraryApis.episodesHistory(it).getEpisodesHistory(1, 2, 3)
            }
            add("movies-history", "GET", "movies/history", query = paging +
                ("radarrid" to listOf("3"))) {
                BazarrLibraryApis.moviesHistory(it).getMoviesHistory(1, 2, 3)
            }
            add("episodes-wanted", "GET", "episodes/wanted", query = paging +
                ("episodeid[]" to listOf("3", "4"))) {
                BazarrLibraryApis.episodesWanted(it).getEpisodesWanted(1, 2, listOf(3, 4))
            }
            add("movies-wanted", "GET", "movies/wanted", query = paging +
                ("radarrid[]" to listOf("3", "4"))) {
                BazarrLibraryApis.moviesWanted(it).getMoviesWanted(1, 2, listOf(3, 4))
            }
            add("episodes-blacklist", "GET", "episodes/blacklist", query = paging) {
                BazarrLibraryApis.episodesBlacklist(it).getEpisodesBlacklist(1, 2)
            }
            add("movies-blacklist", "GET", "movies/blacklist", query = paging) {
                BazarrLibraryApis.moviesBlacklist(it).getMoviesBlacklist(1, 2)
            }
            val blacklist = mapOf(
                "provider" to listOf("example"), "subs_id" to listOf("abc"),
                "language" to listOf("en"), "subtitles_path" to listOf("/media/file.en.srt"),
            )
            val episode = mapOf("seriesid" to listOf("1"), "episodeid" to listOf("2"))
            val movie = mapOf("radarrid" to listOf("1"))
            add("episodes-blacklist", "POST", "episodes/blacklist",
                query = blacklist + episode) {
                BazarrLibraryApis.episodesBlacklist(it).postEpisodesBlacklist(
                    1, 2, "example", "abc", "en", "/media/file.en.srt"
                )
            }
            add("movies-blacklist", "POST", "movies/blacklist", query = blacklist + movie) {
                BazarrLibraryApis.moviesBlacklist(it).postMoviesBlacklist(
                    1, "example", "abc", "en", "/media/file.en.srt"
                )
            }
            val removal = mapOf(
                "all" to listOf("false"), "provider" to listOf("example"),
                "subs_id" to listOf("abc"),
            )
            add("episodes-blacklist", "DELETE", "episodes/blacklist", 204, removal) {
                BazarrLibraryApis.episodesBlacklist(it).deleteEpisodesBlacklist(
                    "false", "example", "abc"
                )
            }
            add("movies-blacklist", "DELETE", "movies/blacklist", 204, removal) {
                BazarrLibraryApis.moviesBlacklist(it).deleteMoviesBlacklist(
                    "false", "example", "abc"
                )
            }
            val flags = mapOf(
                "language" to listOf("en"), "forced" to listOf("false"),
                "hi" to listOf("true"),
            )
            val path = mapOf("path" to listOf("/media/file.en.srt"))
            add("episodes-subtitles", "PATCH", "episodes/subtitles", 204, episode + flags) {
                BazarrLibraryApis.episodesSubtitles(it).patchEpisodesSubtitles(
                    1, 2, "en", "false", "true"
                )
            }
            add("episodes-subtitles", "POST", "episodes/subtitles", 204,
                episode + flags, upload = true) {
                BazarrLibraryApis.episodesSubtitles(it).postEpisodesSubtitles(
                    1, 2, "en", "false", "true", upload()
                )
            }
            add("episodes-subtitles", "DELETE", "episodes/subtitles", 204,
                episode + flags + path) {
                BazarrLibraryApis.episodesSubtitles(it).deleteEpisodesSubtitles(
                    1, 2, "en", "false", "true", "/media/file.en.srt"
                )
            }
            add("movies-subtitles", "PATCH", "movies/subtitles", 204, movie + flags) {
                BazarrLibraryApis.moviesSubtitles(it).patchMoviesSubtitles(
                    1, "en", "false", "true"
                )
            }
            add("movies-subtitles", "POST", "movies/subtitles", 204,
                movie + flags, upload = true) {
                BazarrLibraryApis.moviesSubtitles(it).postMoviesSubtitles(
                    1, "en", "false", "true", upload()
                )
            }
            add("movies-subtitles", "DELETE", "movies/subtitles", 204, movie + flags + path) {
                BazarrLibraryApis.moviesSubtitles(it).deleteMoviesSubtitles(
                    1, "en", "false", "true", "/media/file.en.srt"
                )
            }
            return cases.map { arrayOf(it) }
        }
    }
}

/** An operation's wire expectations are independent of Retrofit's implementation. */
data class LibraryCase(
    val tag: String,
    val method: String,
    val path: String,
    val status: Int,
    val query: Map<String, List<String>>,
    val upload: Boolean,
    val call: suspend (Retrofit) -> Any,
) {
    override fun toString() = "$method $path"
}
