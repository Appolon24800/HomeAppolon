package dev.appolon.homelab.arr.api.radarr

import dev.appolon.homelab.arr.api.radarr.fake.FakeRadarrLibrary
import dev.appolon.homelab.arr.api.radarr.model.ApplyTags
import dev.appolon.homelab.arr.api.radarr.model.MovieEditorResource
import dev.appolon.homelab.arr.api.radarr.model.MovieFileListResource
import dev.appolon.homelab.arr.api.radarr.model.AlternativeTitleResource
import dev.appolon.homelab.arr.api.radarr.model.CreditResource
import dev.appolon.homelab.arr.api.radarr.model.ExtraFileResource
import dev.appolon.homelab.arr.api.radarr.model.MovieFileResource
import dev.appolon.homelab.arr.api.radarr.model.MovieResource
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import retrofit2.HttpException

class FakeRadarrLibraryTest {

    private fun movie(id: Int, tmdbId: Int = id * 10, title: String = "Movie $id") =
        MovieResource(id = id, title = title, tmdbId = tmdbId, monitored = false, tags = emptySet())

    @Test
    fun errorFailsEveryCall() = runTest {
        val fake = FakeRadarrLibrary(error = IOException("offline"))

        val error = runCatching { fake.movie.list() }.exceptionOrNull()

        assertTrue(error is IOException)
    }

    @Test
    fun errorOnceFailsExactlyOneCall() = runTest {
        val fake = FakeRadarrLibrary().apply {
            movies += movie(1)
            errorOnce = IOException("flake")
        }

        assertTrue(runCatching { fake.movie.list() }.isFailure)
        assertEquals(listOf(1), fake.movie.list().map { it.id })
    }

    @Test
    fun latencyDelaysEachCall() = runTest {
        val fake = FakeRadarrLibrary(latencyMs = 50, sleep = { delay(it) })

        fake.movie.list()

        assertEquals(50, testScheduler.currentTime)
    }

    @Test
    fun movieCrudFollowsServerSemantics() = runTest {
        val fake = FakeRadarrLibrary().apply { movies += movie(1, tmdbId = 19995) }

        assertEquals(1, fake.movie.get(1).body()!!.id)
        assertEquals(404, fake.movie.get(999).code())

        val created = fake.movie.create(movie(0, tmdbId = 42, title = "Fresh"))
        assertTrue(created.isSuccessful)
        assertEquals(201, created.code())
        assertTrue(created.body()!!.id != 0)
        assertEquals(2, fake.movies.size)

        val updated = fake.movie.update("1", movie = movie(1, tmdbId = 19995, title = "Renamed"))
        assertEquals(202, updated.code())
        assertEquals("Renamed", updated.body()!!.title)

        fake.movie.delete(1, deleteFiles = true, addImportExclusion = true)
        assertEquals(1, fake.movies.size)
        assertEquals(setOf(19995), fake.importExclusions)
    }

    @Test
    fun createWithoutTmdbIdIsNotFound() = runTest {
        val fake = FakeRadarrLibrary()

        assertEquals(404, fake.movie.create(MovieResource(title = "Void")).code())
    }

    @Test
    fun editorUpdateAppliesTagModes() = runTest {
        val fake = FakeRadarrLibrary().apply {
            movies += movie(1).copy(tags = setOf(1))
            movies += movie(2).copy(tags = setOf(1))
        }

        val added = fake.movieEditor.update(
            MovieEditorResource(movieIds = listOf(1), applyTags = ApplyTags.ADD, tags = listOf(2)),
        )
        assertEquals(setOf(1, 2), added.body()!!.single().tags)

        val replaced = fake.movieEditor.update(
            MovieEditorResource(
                movieIds = listOf(1),
                applyTags = ApplyTags.REPLACE,
                tags = listOf(7),
                monitored = true,
            ),
        )
        assertEquals(setOf(7), replaced.body()!!.single().tags)
        assertTrue(replaced.body()!!.single().monitored)
        // Untouched movies keep their tags.
        assertEquals(setOf(1), fake.movies.first { it.id == 2 }.tags)
    }

    @Test
    fun editorUpdateRequiresTagsWhenApplying() = runTest {
        val fake = FakeRadarrLibrary()

        val response = fake.movieEditor.update(
            MovieEditorResource(movieIds = listOf(1), applyTags = ApplyTags.ADD),
        )

        assertEquals(400, response.code())
    }

    @Test
    fun editorDeleteRemovesMoviesAndFiles() = runTest {
        val fake = FakeRadarrLibrary().apply {
            movies += movie(1)
            movies += movie(2)
            movieFiles += MovieFileResource(id = 9, movieId = 1)
        }

        fake.movieEditor.delete(MovieEditorResource(movieIds = listOf(1, 2), deleteFiles = true))

        assertTrue(fake.movies.isEmpty())
        assertTrue(fake.movieFiles.isEmpty())
    }

    @Test
    fun movieFileOperationsEditState() = runTest {
        val fake = FakeRadarrLibrary().apply {
            movieFiles += MovieFileResource(id = 25, movieId = 1)
            movieFiles += MovieFileResource(id = 26, movieId = 2)
        }

        assertEquals(listOf(25), fake.movieFile.list(movieId = listOf(1)).map { it.id })
        assertEquals(listOf(26), fake.movieFile.list(movieFileIds = listOf(26)).map { it.id })
        assertEquals(404, fake.movieFile.get(999).code())
        val updated = fake.movieFile.update(
            "25",
            MovieFileResource(id = 25, edition = "Director's Cut"),
        )
        assertEquals(202, updated.code())
        assertEquals("Director's Cut", fake.movieFiles.first { it.id == 25 }.edition)
        assertEquals(200, fake.movieFile.delete(26).code())
        assertEquals(1, fake.movieFiles.size)
        fake.movieFile.deleteBulk(MovieFileListResource(movieFileIds = listOf(25)))
        assertTrue(fake.movieFiles.isEmpty())
    }

    @Suppress("DEPRECATION")
    @Test
    fun movieFileEditorPatchesListedFiles() = runTest {
        val fake = FakeRadarrLibrary().apply {
            movieFiles += MovieFileResource(id = 25, movieId = 1)
            movieFiles += MovieFileResource(id = 26, movieId = 1)
        }

        val patched = fake.movieFile.updateEditor(
            MovieFileListResource(
                movieFileIds = listOf(25),
                edition = "IMAX",
            ),
        )

        assertEquals("IMAX", patched.first { it.id == 25 }.edition)
        assertEquals(null, patched.first { it.id == 26 }.edition)
    }

    @Test
    fun folderFallsBackThroughFolderFields() = runTest {
        val fake = FakeRadarrLibrary().apply {
            movies += movie(1).copy(folder = "Named Folder")
            movies += movie(2).copy(path = "/movies/From Path")
        }

        assertEquals("Named Folder", fake.movieFolder.get(1).folder)
        assertEquals("From Path", fake.movieFolder.get(2).folder)
        assertTrue(runCatching { fake.movieFolder.get(999) }.exceptionOrNull() is HttpException)
    }

    @Test
    fun importAssignsFreshIds() = runTest {
        val fake = FakeRadarrLibrary()

        val imported = fake.movieImport.importMovies(listOf(movie(0), movie(0)))

        assertEquals(2, imported.size)
        assertTrue(imported.all { it.id != 0 })
        assertEquals(2, fake.movies.size)
    }

    @Test
    fun lookupSearchesCatalogCaseInsensitively() = runTest {
        val fake = FakeRadarrLibrary().apply {
            lookupCatalog += movie(0, tmdbId = 11, title = "Example Movie")
            lookupCatalog += movie(0, tmdbId = 12, title = "Another Film")
        }

        assertEquals(listOf("Example Movie"), fake.movieLookup.lookup("example").map { it.title })
        assertTrue(fake.movieLookup.lookup("nope").isEmpty())
        assertEquals(11, fake.movieLookup.lookupByTmdbId(11).tmdbId)
        assertTrue(
            runCatching { fake.movieLookup.lookupByTmdbId(999) }.exceptionOrNull() is HttpException,
        )
    }

    @Test
    fun altTitleCreditAndExtraFileListsFilterByMovie() = runTest {
        val fake = FakeRadarrLibrary().apply {
            alternativeTitles += AlternativeTitleResource(
                id = 10,
                movieMetadataId = 100,
                title = "Alt",
            )
            credits += CreditResource(
                id = 77,
                movieMetadataId = 100,
                personName = "Person",
            )
            extraFiles += ExtraFileResource(
                id = 901,
                movieId = 1,
                title = "Sub",
            )
        }

        assertEquals(1, fake.alternativeTitle.list(movieId = 100).size)
        assertEquals(404, fake.alternativeTitle.get(11).code())
        assertEquals(1, fake.credit.list(movieMetadataId = 100).size)
        assertEquals(77, fake.credit.get(77).body()!!.id)
        assertEquals(1, fake.extraFile.list(movieId = 1).size)
        assertEquals(0, fake.extraFile.list(movieId = 2).size)
    }
}
