package dev.appolon.homelab.arr.ui.nav

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ArrRouteRegistryTest {

    @Test
    fun decodeFeatureRoundTripsEncodedFeatureRoutes() {
        val registry = ArrRouteRegistry()
        assertTrue(registry.register("radarr", "movie/{movieId}"))

        val route = ArrRoute.Feature("radarr", "movie/42")
        assertEquals(route, registry.decodeFeature(ArrRoutes.encode(route)))
    }

    @Test
    fun registerRejectsExactDuplicates() {
        val registry = ArrRouteRegistry()
        assertTrue(registry.register("radarr", "movie/{movieId}"))
        assertFalse(registry.register("radarr", "movie/{movieId}"))
        assertEquals(1, registry.nestedRoutes().size)
    }

    @Test
    fun samePatternForDifferentOwnersIsAllowed() {
        val registry = ArrRouteRegistry()
        assertTrue(registry.register("radarr", "movie/{movieId}"))
        assertTrue(registry.register("sonarr", "movie/{movieId}"))
        assertEquals(2, registry.nestedRoutes().size)
    }

    @Test
    fun decodeFeatureReturnsNullForUnknownOwnerOrShape() {
        val registry = ArrRouteRegistry()
        registry.register("radarr", "movie/{movieId}")

        assertNull(registry.decodeFeature("f/lidarr/movie/42"))
        assertNull(registry.decodeFeature("f/radarr/movie"))
        assertNull(registry.decodeFeature("f/radarr/movie/1/credits"))
        assertNull(registry.decodeFeature("f/radarr"))
        assertNull(registry.decodeFeature("servers"))
        assertNull(registry.decodeFeature(""))
    }

    @Test
    fun decodeFeaturePicksTheMatchingPatternAmongMany() {
        val registry = ArrRouteRegistry()
        registry.register("radarr", "movie/{movieId}")
        registry.register("radarr", "collection/{collectionId}")

        assertEquals(
            ArrRoute.Feature("radarr", "collection/9"),
            registry.decodeFeature("f/radarr/collection/9"),
        )
        assertEquals(
            ArrRoute.Feature("radarr", "movie/42"),
            registry.decodeFeature("f/radarr/movie/42"),
        )
    }

    @Test
    fun decodeFeatureDecodesPercentEncodedParameters() {
        val registry = ArrRouteRegistry()
        registry.register("sonarr", "series/{titleSlug}")

        assertEquals(
            ArrRoute.Feature("sonarr", "series/Blue%20Beetle"),
            registry.decodeFeature("f/sonarr/series/Blue%2520Beetle"),
        )
        assertNull(registry.decodeFeature("f/sonarr/series/Blue%2G"))
    }

    @Test
    fun unregisterDropsOnlyTheGivenOwner() {
        val registry = ArrRouteRegistry()
        registry.register("radarr", "movie/{movieId}")
        registry.register("sonarr", "series/{seriesId}")

        registry.unregister("radarr")

        assertEquals(listOf(ArrNestedRoute("sonarr", "series/{seriesId}")), registry.nestedRoutes())
        assertNull(registry.decodeFeature("f/radarr/movie/42"))
    }

    @Test
    fun matchRoutePatternCapturesParametersAndRejectsShapeMismatches() {
        assertEquals(
            mapOf("id" to "42"),
            matchRoutePattern("movie/{id}", listOf("movie", "42")),
        )
        assertEquals(emptyMap<String, String>(), matchRoutePattern("settings", listOf("settings")))
        assertNull(matchRoutePattern("movie/{id}", listOf("movie")))
        assertNull(matchRoutePattern("movie/{id}", listOf("movie", "1", "cast")))
        assertNull(matchRoutePattern("movie/{id}", listOf("series", "1")))
    }

    @Test
    fun matchRoutePatternRejectsMalformedOrDuplicateParameterNames() {
        assertNull(matchRoutePattern("movie/{}", listOf("movie", "1")))
        assertNull(matchRoutePattern("{id}/{id}", listOf("1", "2")))
    }

    @Test
    fun patternParamsListsNamesInOrder() {
        assertEquals(
            listOf("movieId", "tab"),
            patternParams("movie/{movieId}/{tab}"),
        )
        assertEquals(emptyList<String>(), patternParams("settings/general"))
    }

    @Test
    fun fillPatternEncodesValuesAndRejectsMissingOnes() {
        assertEquals(
            "movie/Blue%20Beetle",
            fillPattern("movie/{title}", mapOf("title" to "Blue Beetle")),
        )
        assertNull(fillPattern("movie/{title}", emptyMap()))
        assertNull(fillPattern("movie/{title}", mapOf("title" to "")))
    }

    @Test
    fun nestedNavPatternPrefixesTheOwnerWithTheFeatureSegment() {
        assertEquals(
            "f/radarr/movie/{movieId}",
            nestedNavPattern(ArrNestedRoute("radarr", "movie/{movieId}")),
        )
    }
}
