package dev.appolon.homelab.arr.ui.nav

import dev.appolon.homelab.arr.core.AppType
import dev.appolon.homelab.arr.core.ServerId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ArrRoutesTest {

    @Test
    fun roundTripsEveryBuiltInRoute() {
        val routes = listOf(
            ArrRoute.ServerList,
            ArrRoute.ServerHome(ServerId("srv-1")),
            ArrRoute.Library(ServerId("srv-1"), AppType.RADARR),
            ArrRoute.Library(ServerId("srv-1"), AppType.PROWLARR),
            ArrRoute.Detail(ServerId("srv 2"), AppType.SONARR, "tvdb:1234"),
            ArrRoute.Add(ServerId("srv-1"), AppType.LIDARR),
            ArrRoute.Queue(ServerId("srv-1"), AppType.BAZARR),
            ArrRoute.Calendar(ServerId("srv-1"), AppType.SONARR),
            ArrRoute.Settings(ServerId("srv-1"), AppType.RADARR),
        )
        routes.forEach { route ->
            assertEquals(route, ArrRoutes.decode(ArrRoutes.encode(route)))
        }
    }

    @Test
    fun roundTripsEveryAppTypeOnEveryPerAppRoute() {
        AppType.entries.forEach { app ->
            val routes = listOf(
                ArrRoute.Library(ServerId("s"), app),
                ArrRoute.Detail(ServerId("s"), app, "id/with slashes"),
                ArrRoute.Add(ServerId("s"), app),
                ArrRoute.Queue(ServerId("s"), app),
                ArrRoute.Calendar(ServerId("s"), app),
                ArrRoute.Settings(ServerId("s"), app),
            )
            routes.forEach { route ->
                assertEquals(route, ArrRoutes.decode(ArrRoutes.encode(route)))
            }
        }
    }

    @Test
    fun encodeUsesTheDocumentedPathScheme() {
        assertEquals("servers", ArrRoutes.encode(ArrRoute.ServerList))
        assertEquals("server/srv-1", ArrRoutes.encode(ArrRoute.ServerHome(ServerId("srv-1"))))
        assertEquals(
            "server/srv-1/radarr/library",
            ArrRoutes.encode(ArrRoute.Library(ServerId("srv-1"), AppType.RADARR)),
        )
        assertEquals(
            "server/srv-1/sonarr/detail/tvdb%3A1234",
            ArrRoutes.encode(ArrRoute.Detail(ServerId("srv-1"), AppType.SONARR, "tvdb:1234")),
        )
    }

    @Test
    fun encodesArgumentsSoTheyNeverSplitSegments() {
        val tricky = ServerId("a/b c?d#e&f=ü")
        val encoded = ArrRoutes.encode(ArrRoute.ServerHome(tricky))
        assertEquals(tricky, ArrRoutes.decode(encoded)?.let(ArrRoutes::serverIdOf))
        // Slashes and spaces must be escaped: the encoded route has exactly 2 segments.
        assertEquals(2, encoded.split('/').size)
    }

    @Test
    fun decodeRejectsMalformedInput() {
        listOf(
            "",
            "/servers",
            "server",
            "server/",
            "servers/extra",
            "server/srv-1/radarr",
            "server/srv-1/radarr/library/extra",
            "server/srv-1/notanapp/library",
            "server/srv-1/radarr/unknown",
            "server/srv-1/radarr/detail",
            "server/srv-1/radarr/detail/",
            "server/%ZZ",
            "f/radarr/movie/42",
        ).forEach { raw ->
            assertNull("expected decode($raw) to be null", ArrRoutes.decode(raw))
        }
    }

    @Test
    fun decodeAcceptsPlainServerHome() {
        assertEquals(
            ArrRoute.ServerHome(ServerId("srv-1")),
            ArrRoutes.decode("server/srv-1"),
        )
    }

    @Test
    fun appFromWireNameMapsAllWireNamesAndRejectsUnknowns() {
        AppType.entries.forEach { app ->
            assertEquals(app, ArrRoutes.appFromWireName(app.wireName))
        }
        assertNull(ArrRoutes.appFromWireName("NotAnApp"))
    }

    @Test
    fun serverIdOfCoversEveryRouteKind() {
        assertNull(ArrRoutes.serverIdOf(ArrRoute.ServerList))
        assertNull(ArrRoutes.serverIdOf(ArrRoute.Feature("radarr", "movie/1")))
        val serverId = ServerId("srv")
        assertEquals(serverId, ArrRoutes.serverIdOf(ArrRoute.ServerHome(serverId)))
        assertEquals(serverId, ArrRoutes.serverIdOf(ArrRoute.Library(serverId, AppType.RADARR)))
        assertEquals(serverId, ArrRoutes.serverIdOf(ArrRoute.Detail(serverId, AppType.RADARR, "x")))
        assertEquals(serverId, ArrRoutes.serverIdOf(ArrRoute.Add(serverId, AppType.RADARR)))
        assertEquals(serverId, ArrRoutes.serverIdOf(ArrRoute.Queue(serverId, AppType.RADARR)))
        assertEquals(serverId, ArrRoutes.serverIdOf(ArrRoute.Calendar(serverId, AppType.RADARR)))
        assertEquals(serverId, ArrRoutes.serverIdOf(ArrRoute.Settings(serverId, AppType.RADARR)))
    }
}
