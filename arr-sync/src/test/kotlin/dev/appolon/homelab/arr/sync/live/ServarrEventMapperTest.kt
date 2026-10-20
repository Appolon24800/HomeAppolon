package dev.appolon.homelab.arr.sync.live

import dev.appolon.homelab.arr.core.AppType
import dev.appolon.homelab.arr.core.ServerId
import dev.appolon.homelab.arr.sync.ArrEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ServarrEventMapperTest {
    private val serverId = ServerId("server-1")

    private fun map(appType: AppType, name: String?, action: String? = null) =
        ServarrEventMapper.map(serverId, appType, name, action)

    @Test
    fun `radarr movie and queue and calendar and health and diskspace map to typed events`() {
        assertEquals(ArrEvent.EntitiesChanged(serverId, "movie", "updated"), map(AppType.RADARR, "movie", "updated"))
        assertEquals(ArrEvent.QueueChanged(serverId), map(AppType.RADARR, "queue"))
        assertEquals(ArrEvent.CalendarChanged(serverId), map(AppType.RADARR, "calendar"))
        assertEquals(ArrEvent.HealthChanged(serverId), map(AppType.RADARR, "health"))
        assertEquals(ArrEvent.DiskSpaceChanged(serverId), map(AppType.RADARR, "diskspace"))
    }

    @Test
    fun `sonarr maps series and episode entities with their action`() {
        assertEquals(ArrEvent.EntitiesChanged(serverId, "series", "deleted"), map(AppType.SONARR, "series", "deleted"))
        assertEquals(ArrEvent.EntitiesChanged(serverId, "episode", null), map(AppType.SONARR, "episode"))
        assertEquals(ArrEvent.EntitiesChanged(serverId, "episodefile", "renamed"), map(AppType.SONARR, "episodefile", "renamed"))
    }

    @Test
    fun `lidarr maps artist and album entities`() {
        assertEquals(ArrEvent.EntitiesChanged(serverId, "artist", null), map(AppType.LIDARR, "artist"))
        assertEquals(ArrEvent.EntitiesChanged(serverId, "album", "updated"), map(AppType.LIDARR, "album", "updated"))
    }

    @Test
    fun `prowlarr maps indexer entities`() {
        assertEquals(ArrEvent.EntitiesChanged(serverId, "indexer", null), map(AppType.PROWLARR, "indexer"))
    }

    @Test
    fun `bazarr maps its own entity names`() {
        assertEquals(ArrEvent.EntitiesChanged(serverId, "series", null), map(AppType.BAZARR, "series"))
        assertEquals(ArrEvent.EntitiesChanged(serverId, "movieids_changed", null), map(AppType.BAZARR, "movieids_changed"))
    }

    @Test
    fun `entity names are scoped per app family`() {
        assertNull(map(AppType.RADARR, "series"))
        assertNull(map(AppType.SONARR, "artist"))
        assertNull(map(AppType.PROWLARR, "movie"))
    }

    @Test
    fun `chatter and unknown names drop to null`() {
        assertNull(map(AppType.RADARR, "command"))
        assertNull(map(AppType.SONARR, "system"))
        assertNull(map(AppType.RADARR, null))
        assertNull(map(AppType.BAZARR, "brand-new-event"))
    }
}
