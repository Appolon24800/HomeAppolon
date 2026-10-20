package dev.appolon.homelab.arr.api.lidarr

import dev.appolon.homelab.arr.api.lidarr.model.AlbumResource
import dev.appolon.homelab.arr.api.lidarr.model.AlbumStudioArtistResource
import dev.appolon.homelab.arr.api.lidarr.model.AlbumStudioResource
import dev.appolon.homelab.arr.api.lidarr.model.AlbumsMonitoredResource
import dev.appolon.homelab.arr.api.lidarr.model.ApplyTags
import dev.appolon.homelab.arr.api.lidarr.model.ArtistEditorResource
import dev.appolon.homelab.arr.api.lidarr.model.ArtistResource
import dev.appolon.homelab.arr.api.lidarr.model.MonitorTypes
import dev.appolon.homelab.arr.api.lidarr.model.MonitoringOptions
import dev.appolon.homelab.arr.api.lidarr.model.RenameTrackResource
import dev.appolon.homelab.arr.api.lidarr.model.RetagTrackResource
import dev.appolon.homelab.arr.api.lidarr.model.TrackFileListResource
import dev.appolon.homelab.arr.api.lidarr.model.TrackFileResource
import dev.appolon.homelab.arr.api.lidarr.model.TrackResource
import java.io.IOException
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import retrofit2.HttpException

class FakeLidarrLibraryTest {
    @Test
    fun artistCrudAndEditorMutateStoredResources() = runTest {
        val fake = FakeLidarrLibrary()
        val first = fake.addArtist(ArtistResource(artistName = "string", mbId = "id"))
        val second = fake.addArtist(ArtistResource(artistName = "other"))
        assertTrue(first.id != second.id)
        assertEquals(listOf(first), fake.getArtists(mbId = "id"))
        assertEquals(listOf(first), fake.lookupArtists("STRING"))
        assertEquals(first, fake.getArtist(first.id))
        fake.updateArtist(id = first.id.toString(), body = first.copy(monitored = true))
        assertTrue(fake.getArtist(first.id).monitored)
        fake.updateArtists(
            ArtistEditorResource(
                artistIds = listOf(first.id), monitored = false, tags = listOf(1),
            )
        )
        assertFalse(fake.getArtist(first.id).monitored)
        assertEquals(listOf(1), fake.getArtist(first.id).tags)
        fake.updateArtists(
            ArtistEditorResource(
                artistIds = listOf(first.id), tags = listOf(1), applyTags = ApplyTags.REMOVE,
            )
        )
        assertEquals(emptyList<Int>(), fake.getArtist(first.id).tags)
        fake.deleteArtists(
            ArtistEditorResource(artistIds = listOf(first.id), addImportListExclusion = true)
        )
        assertTrue(first.id in fake.excludedArtistIds)
        fake.deleteArtist(second.id)
        assertTrue(fake.getArtists().isEmpty())
    }

    @Test
    fun albumCrudMonitoringAndStudioMutateStoredResources() = runTest {
        val fake = FakeLidarrLibrary()
        val artist = fake.addArtist(ArtistResource())
        val album = fake.addAlbum(
            AlbumResource(title = "string", artistId = artist.id, foreignAlbumId = "foreign")
        )
        assertEquals(
            listOf(album),
            fake.getAlbums(artist.id, listOf(album.id), "foreign", true),
        )
        assertEquals(listOf(album), fake.lookupAlbums("STRING"))
        fake.updateAlbum(album.id.toString(), album.copy(title = "updated"))
        assertEquals("updated", fake.getAlbum(album.id).title)
        fake.monitorAlbums(AlbumsMonitoredResource(listOf(album.id), true))
        assertTrue(fake.getAlbum(album.id).monitored)
        fake.updateAlbumStudio(
            AlbumStudioResource(
                artist = listOf(AlbumStudioArtistResource(id = artist.id, monitored = true)),
                monitoringOptions = MonitoringOptions(monitor = MonitorTypes.NONE),
            )
        )
        assertTrue(fake.getArtist(artist.id).monitored)
        assertFalse(fake.getAlbum(album.id).monitored)
        fake.deleteAlbum(album.id, addImportListExclusion = true)
        assertTrue(fake.getAlbums().isEmpty())
        assertTrue(album.id in fake.excludedAlbumIds)
    }

    @Test
    fun trackFilesFilterUpdateAndDeleteAndClearTrackState() = runTest {
        val fake = FakeLidarrLibrary()
        val file = TrackFileResource(id = 1, artistId = 2, albumId = 3)
        val track = TrackResource(
            id = 4, artistId = 2, albumId = 3, trackFileId = 1, hasFile = true, trackFile = file,
        )
        fake.trackFiles[1] = file
        fake.trackFiles[5] = TrackFileResource(id = 5)
        fake.tracks[4] = track
        fake.trackReleaseIds[4] = 6
        assertEquals(listOf(track), fake.getTracks(2, 3, 6, listOf(4)))
        assertEquals(track, fake.getTrack(4))
        assertTrue(fake.getTracks(albumReleaseId = 7).isEmpty())
        assertEquals(listOf(file), fake.getTrackFiles(2, listOf(1), listOf(3), false))
        assertEquals(listOf(fake.trackFiles.getValue(5)), fake.getTrackFiles(unmapped = true))
        fake.updateTrackFile("1", file.copy(path = "string"))
        assertEquals("string", fake.getTrackFile(1).path)
        fake.updateTrackFiles(TrackFileListResource(trackFileIds = listOf(1), sceneName = "new"))
        assertEquals("new", fake.getTrackFile(1).sceneName)
        fake.deleteTrackFile(1)
        assertFalse(fake.getTrack(4).hasFile)
        assertNull(fake.getTrack(4).trackFile)
        fake.deleteTrackFiles(TrackFileListResource(trackFileIds = listOf(5)))
        assertTrue(fake.getTrackFiles().isEmpty())
        assertEquals(setOf(1, 5), fake.deletedTrackFileIds)
    }

    @Test
    fun renameRetagAndCoverQueriesUseSeededData() = runTest {
        val fake = FakeLidarrLibrary()
        val rename = RenameTrackResource(id = 1, artistId = 2, albumId = 3)
        val retag = RetagTrackResource(id = 1, artistId = 2, albumId = 3)
        val cover = JsonObject(emptyMap())
        fake.renames.add(rename)
        fake.retags.add(retag)
        fake.artistCovers[2 to "cover.jpg"] = cover
        fake.albumCovers[3 to "cover.jpg"] = cover
        assertEquals(listOf(rename), fake.getTrackRenames(2, 3))
        assertEquals(listOf(retag), fake.getTrackRetags(2, 3))
        assertTrue(fake.getTrackRenames(artistId = 9).isEmpty())
        assertTrue(fake.getTrackRetags(albumId = 9).isEmpty())
        assertEquals(cover, fake.getArtistCover(2, "cover.jpg"))
        assertEquals(cover, fake.getAlbumCover(3, "cover.jpg"))
    }

    @Test
    fun injectedLatencyUsesVirtualTimeAndErrorDoesNotMutate() = runTest {
        val seen = mutableListOf<String>()
        val failure = IOException("Synthetic failure")
        val fake = FakeLidarrLibrary(
            latency = { operation ->
                seen.add(operation)
                delay(250)
            },
            error = { operation -> if (operation == "addArtist") failure else null },
        )
        val start = testScheduler.currentTime
        try {
            fake.addArtist(ArtistResource())
            fail("Expected injected failure")
        } catch (error: IOException) {
            assertTrue(error === failure)
        }
        assertEquals(250L, testScheduler.currentTime - start)
        assertEquals(listOf("addArtist"), seen)
        assertTrue(fake.artists.isEmpty())
        fake.error = { null }
        assertEquals(1, fake.addArtist(ArtistResource()).id)
    }

    @Test
    fun missingResourcesAndInvalidBatchReturn404WithoutPartialMutation() = runTest {
        val fake = FakeLidarrLibrary()
        val artist = fake.addArtist(ArtistResource())
        try {
            fake.deleteArtists(ArtistEditorResource(artistIds = listOf(artist.id, 99)))
            fail("Expected missing resource")
        } catch (error: HttpException) {
            assertEquals(404, error.code())
        }
        assertEquals(artist, fake.getArtist(artist.id))
        try {
            fake.getAlbumCover(99, "cover.jpg")
            fail("Expected missing cover")
        } catch (error: HttpException) {
            assertEquals(404, error.code())
        }
    }

    @Test
    fun deletingArtistWithFilesRemovesDependentResources() = runTest {
        val fake = FakeLidarrLibrary()
        fake.artists[1] = ArtistResource(id = 1)
        fake.albums[2] = AlbumResource(id = 2, artistId = 1)
        fake.trackFiles[3] = TrackFileResource(id = 3, artistId = 1, albumId = 2)
        fake.tracks[4] = TrackResource(id = 4, artistId = 1, albumId = 2, trackFileId = 3)
        fake.deleteArtist(1, deleteFiles = true)
        assertTrue(fake.albums.isEmpty())
        assertTrue(fake.tracks.isEmpty())
        assertTrue(fake.trackFiles.isEmpty())
        assertEquals(setOf(3), fake.deletedTrackFileIds)
    }
}
