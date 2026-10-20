package dev.appolon.homelab.arr.feature.lidarr.library.data

import dev.appolon.homelab.arr.api.lidarr.model.ApplyTags
import dev.appolon.homelab.arr.api.lidarr.model.ArtistEditorResource
import dev.appolon.homelab.arr.api.lidarr.model.ArtistResource
import dev.appolon.homelab.arr.api.lidarr.model.NewItemMonitorTypes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ArtistEditorMergeTest {

    private fun artist(
        id: Int,
        monitored: Boolean = true,
        monitorNewItems: NewItemMonitorTypes = NewItemMonitorTypes.ALL,
        quality: Int = 1,
        metadata: Int = 1,
        rootFolderPath: String? = "/music",
        tags: List<Int>? = listOf(1),
    ) = ArtistResource(
        id = id,
        artistName = "Artist $id",
        monitored = monitored,
        monitorNewItems = monitorNewItems,
        qualityProfileId = quality,
        metadataProfileId = metadata,
        rootFolderPath = rootFolderPath,
        tags = tags,
    )

    @Test
    fun `null fields leave the artist untouched`() {
        val editor = ArtistEditorResource(artistIds = listOf(2), monitored = null)
        val merged = applyArtistEditor(listOf(artist(1), artist(2)), editor)
        assertEquals(listOf(1, 2), merged.map { it.id })
        assertEquals(true, merged[1].monitored)
        assertEquals(1, merged[1].qualityProfileId)
    }

    @Test
    fun `every editable field applies to the targets only`() {
        val editor = ArtistEditorResource(
            artistIds = listOf(2),
            monitored = false,
            monitorNewItems = NewItemMonitorTypes.NEW,
            qualityProfileId = 7,
            metadataProfileId = 8,
            rootFolderPath = "/new",
            tags = listOf(9),
            applyTags = ApplyTags.REPLACE,
        )
        val merged = applyArtistEditor(listOf(artist(1), artist(2)), editor)
        val target = merged[1]
        assertEquals(false, target.monitored)
        assertEquals(NewItemMonitorTypes.NEW, target.monitorNewItems)
        assertEquals(7, target.qualityProfileId)
        assertEquals(8, target.metadataProfileId)
        assertEquals("/new", target.rootFolderPath)
        assertEquals(listOf(9), target.tags)
        // The untouched artist keeps its values.
        assertEquals(1, merged[0].qualityProfileId)
    }

    @Test
    fun `tags merge per apply mode`() {
        val base = listOf(artist(2, tags = listOf(1, 2)))
        val add = applyArtistEditor(
            base,
            ArtistEditorResource(artistIds = listOf(2), tags = listOf(3), applyTags = ApplyTags.ADD),
        )[0].tags
        assertEquals(listOf(1, 2, 3), add)
        val remove = applyArtistEditor(
            base,
            ArtistEditorResource(artistIds = listOf(2), tags = listOf(2), applyTags = ApplyTags.REMOVE),
        )[0].tags
        assertEquals(listOf(1), remove)
    }

    @Test
    fun `empty target list returns the input unchanged`() {
        val artists = listOf(artist(1))
        assertEquals(artists, applyArtistEditor(artists, ArtistEditorResource(artistIds = emptyList())))
    }

    @Test
    fun `parseTagIds accepts commas, spaces, and semicolons`() {
        assertEquals(listOf(1, 2, 3), parseTagIds("1, 2;3"))
        assertEquals(emptyList<Int>(), parseTagIds("  "))
    }

    @Test
    fun `parseTagIds rejects non numeric tokens`() {
        assertNull(parseTagIds("1, rock"))
    }
}
