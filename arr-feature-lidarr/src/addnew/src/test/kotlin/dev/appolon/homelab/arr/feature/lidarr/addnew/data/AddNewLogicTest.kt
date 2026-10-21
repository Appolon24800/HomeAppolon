package dev.appolon.homelab.arr.feature.lidarr.addnew.data

import dev.appolon.homelab.arr.api.lidarr.model.AlbumResource
import dev.appolon.homelab.arr.api.lidarr.model.AlbumStatisticsResource
import dev.appolon.homelab.arr.api.lidarr.model.ArtistResource
import dev.appolon.homelab.arr.api.lidarr.model.MediaCover
import dev.appolon.homelab.arr.api.lidarr.model.MediaCoverTypes
import dev.appolon.homelab.arr.api.lidarr.model.MonitorTypes
import dev.appolon.homelab.arr.api.lidarr.model.NewItemMonitorTypes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AddNewLogicTest {

    private val baseUrl = "http://lidarr:8686/lidarr/"

    private fun artist(
        id: Int = 0,
        foreignId: String? = "mb-a",
        images: List<MediaCover>? = null,
        remotePoster: String? = null,
    ) = ArtistResource(
        id = id,
        artistName = "Aurora",
        foreignArtistId = foreignId,
        images = images,
        remotePoster = remotePoster,
    )

    @Test
    fun `artist cover prefers the local mediacover path for library artists`() {
        val libraryArtist = artist(
            id = 7,
            images = listOf(MediaCover(coverType = MediaCoverTypes.POSTER, remoteUrl = "https://cdn/x.jpg")),
        )
        assertEquals(
            "http://lidarr:8686/lidarr/api/v1/mediacover/artist/7/poster.jpg",
            AddNewLogic.artistCoverUrl(libraryArtist, baseUrl),
        )
    }

    @Test
    fun `artist cover falls back to remote art for lookup candidates`() {
        val candidate = artist(
            images = listOf(
                MediaCover(coverType = MediaCoverTypes.POSTER, remoteUrl = "https://fanart.io/a.jpg"),
            ),
        )
        assertEquals("https://fanart.io/a.jpg", AddNewLogic.artistCoverUrl(candidate, baseUrl))
    }

    @Test
    fun `artist cover returns null when only unfetchable art is known`() {
        assertNull(AddNewLogic.artistCoverUrl(artist(remotePoster = "/local/path.jpg"), baseUrl))
    }

    @Test
    fun `album cover mirrors the artist rule with the cover type`() {
        val libraryAlbum = AlbumResource(
            id = 9,
            title = "The Gods We Can Touch",
            foreignAlbumId = "mb-al",
            images = listOf(MediaCover(coverType = MediaCoverTypes.COVER, remoteUrl = "https://cdn/c.jpg")),
        )
        assertEquals(
            "http://lidarr:8686/lidarr/api/v1/mediacover/album/9/cover.jpg",
            AddNewLogic.albumCoverUrl(libraryAlbum, baseUrl),
        )
        val candidate = libraryAlbum.copy(id = 0)
        assertEquals("https://cdn/c.jpg", AddNewLogic.albumCoverUrl(candidate, baseUrl))
    }

    @Test
    fun `existing id sets skip rows without a MusicBrainz id`() {
        val existing = AddNewLogic.existingForeignArtistIds(
            listOf(artist(foreignId = "mb-1"), artist(foreignId = null)),
        )
        assertEquals(setOf("mb-1"), existing)
    }

    @Test
    fun `toAddArtistPayload encodes the monitoring strategy`() {
        val payload = AddNewLogic.toAddArtistPayload(
            candidate = artist(id = 55, foreignId = "mb-new"),
            rootFolderPath = "/media/music",
            qualityProfileId = 3,
            metadataProfileId = 2,
            monitor = MonitorTypes.FUTURE,
            tags = setOf(4, 9),
            searchForMissingAlbums = true,
        )
        assertEquals(0, payload.id)
        assertEquals("/media/music", payload.rootFolderPath)
        assertEquals(3, payload.qualityProfileId)
        assertEquals(2, payload.metadataProfileId)
        assertEquals(listOf(4, 9), payload.tags)
        assertTrue(payload.monitored)
        assertEquals(NewItemMonitorTypes.NEW, payload.monitorNewItems)
        assertEquals(MonitorTypes.FUTURE, payload.addOptions?.monitor)
        assertEquals(true, payload.addOptions?.searchForMissingAlbums)
        assertNull(payload.statistics)
    }

    @Test
    fun `toAddArtistPayload with NONE means unmonitored artist and no new item monitoring`() {
        val payload = AddNewLogic.toAddArtistPayload(
            candidate = artist(foreignId = "mb-new"),
            rootFolderPath = "/media/music",
            qualityProfileId = 1,
            metadataProfileId = 1,
            monitor = MonitorTypes.NONE,
            tags = emptySet(),
            searchForMissingAlbums = false,
        )
        assertFalse(payload.monitored)
        assertEquals(NewItemMonitorTypes.NONE, payload.monitorNewItems)
        assertEquals(MonitorTypes.NONE, payload.addOptions?.monitor)
    }

    @Test
    fun `toAddAlbumPayload targets the library artist and keeps the manual add type`() {
        val payload = AddNewLogic.toAddAlbumPayload(
            candidate = AlbumResource(id = 3, title = "Infections of a Different Kind", foreignAlbumId = "mb-al"),
            artistId = 12,
            monitored = false,
            searchForNewAlbum = true,
        )
        assertEquals(0, payload.id)
        assertEquals(12, payload.artistId)
        assertFalse(payload.monitored)
        assertEquals(true, payload.addOptions?.searchForNewAlbum)
        assertNull(payload.statistics)
    }

    private fun album(
        id: Int,
        title: String,
        releaseDate: String = "2020-01-01",
        monitored: Boolean = false,
        trackFiles: Int = 0,
    ) = AlbumResource(
        id = id,
        title = title,
        foreignAlbumId = "mb-al-$id",
        releaseDate = releaseDate,
        monitored = monitored,
        statistics = AlbumStatisticsResource(trackFileCount = trackFiles),
    )

    @Test
    fun `preselect strategies cover all, none keeps current, missing, latest, future`() {
        val albums = listOf(
            album(1, "Old", releaseDate = "2001-01-01", monitored = true, trackFiles = 2),
            album(2, "Missing", releaseDate = "2010-01-01", trackFiles = 0),
            album(3, "Newest", releaseDate = "2099-01-01", trackFiles = 1),
        )
        assertEquals(
            mapOf(1 to true, 2 to true, 3 to true),
            AddNewLogic.preselect(albums, MonitorTypes.ALL),
        )
        assertEquals(
            mapOf(1 to true, 2 to false, 3 to false),
            AddNewLogic.preselect(albums, MonitorTypes.NONE),
        )
        assertEquals(
            mapOf(1 to false, 2 to true, 3 to false),
            AddNewLogic.preselect(albums, MonitorTypes.MISSING),
        )
        assertEquals(
            mapOf(1 to false, 2 to false, 3 to true),
            AddNewLogic.preselect(albums, MonitorTypes.LATEST),
        )
        assertEquals(
            mapOf(1 to false, 2 to false, 3 to true),
            AddNewLogic.preselect(albums, MonitorTypes.FUTURE),
        )
    }

    @Test
    fun `studio payload carries explicit per-album targets`() {
        val albums = listOf(album(1, "A", monitored = false), album(2, "B", monitored = true))
        val payload = AddNewLogic.toAlbumStudioPayload(
            artistId = 6,
            albums = albums,
            targets = mapOf(1 to true, 2 to false),
            strategy = MonitorTypes.MISSING,
        )
        val entry = payload.artist.orEmpty().single()
        assertEquals(6, entry.id)
        assertEquals(listOf(true, false), entry.albums.orEmpty().map { it.monitored })
        assertEquals(MonitorTypes.MISSING, payload.monitoringOptions?.monitor)
    }

    @Test
    fun `applyStudioToAlbums rewrites only the albums the payload names`() {
        val albums = listOf(album(1, "A", monitored = false), album(2, "B", monitored = true))
        val payload = dev.appolon.homelab.arr.api.lidarr.model.AlbumStudioResource(
            artist = listOf(
                dev.appolon.homelab.arr.api.lidarr.model.AlbumStudioArtistResource(
                    id = 6,
                    albums = listOf(albums[0].copy(monitored = true)),
                ),
            ),
        )
        val applied = AddNewLogic.applyStudioToAlbums(albums, payload)
        assertEquals(listOf(true to "A", true to "B"), applied.map { it.monitored to it.title })
    }
}
