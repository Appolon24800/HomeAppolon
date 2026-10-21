package dev.appolon.homelab.arr.api.lidarr

import dev.appolon.homelab.arr.api.lidarr.model.AlbumResource
import dev.appolon.homelab.arr.api.lidarr.model.ArtistResource
import dev.appolon.homelab.arr.api.lidarr.model.ArtistStatusType
import dev.appolon.homelab.arr.api.lidarr.model.ParsedTrackInfo
import dev.appolon.homelab.arr.api.lidarr.model.TrackFileResource
import dev.appolon.homelab.arr.net.ArrJson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LibraryModelTest {
    @Test
    fun dateAndDurationStringsAreUnmodified() {
        val date = "2026-01-01T01:02:03.1234567+02:00"
        assertEquals(date, ArrJson.decodeFromString<ArtistResource>("""{"added":"$date"}""").added)
        assertEquals(
            date,
            ArrJson.decodeFromString<AlbumResource>("""{"releaseDate":"$date"}""").releaseDate,
        )
        assertEquals(
            date,
            ArrJson.decodeFromString<TrackFileResource>("""{"dateAdded":"$date"}""").dateAdded,
        )
        assertEquals(
            "01:02:03",
            ArrJson.decodeFromString<ParsedTrackInfo>("""{"duration":"01:02:03"}""").duration,
        )
    }

    @Test
    fun unknownEnumAndUnknownFieldsAreForwardCompatible() {
        val artist = ArrJson.decodeFromString<ArtistResource>(
            """{"status":"future-status","futureField":true,"artistName":null}"""
        )
        assertEquals(ArtistStatusType.CONTINUING, artist.status)
        assertNull(artist.artistName)
        assertNull(artist.nextAlbum)
    }

    @Test
    fun unconstrainedSchemaStringsStayStrings() {
        val album = ArrJson.decodeFromString<AlbumResource>(
            """{"albumType":"future-type","releases":[{"status":"future-status"}]}"""
        )
        assertEquals("future-type", album.albumType)
        assertEquals("future-status", album.releases!!.single().status)
    }

    @Test
    fun sixtyFourBitFileSizesDoNotTruncate() {
        val file = ArrJson.decodeFromString<TrackFileResource>("""{"size":9007199254740991}""")
        assertEquals(9007199254740991L, file.size)
    }
}
