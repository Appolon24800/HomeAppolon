package dev.appolon.homelab.arr.api.radarr

import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AlternativeTitleApiTest {
    private lateinit var server: MockWebServer
    private lateinit var api: dev.appolon.homelab.arr.api.radarr.api.AlternativeTitleApi

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        api = RadarrApis.alternativeTitle(TestRadarr.retrofit(server))
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun listSendsFiltersAndParsesTitles() = runTest {
        server.enqueue(MockResponse().setBody(TestRadarr.fixture("alttitle", "alttitle-list.json")))

        val titles = api.list(movieId = 100)

        assertEquals("/api/v3/alttitle?movieId=100", server.takeRequest().path)
        assertEquals(2, titles.size)
        assertEquals("Alt Title", titles[0].title)
    }

    @Test
    fun getReturnsTitle() = runTest {
        server.enqueue(MockResponse().setBody(TestRadarr.fixture("alttitle", "alttitle.json")))

        val response = api.get(10)

        assertTrue(response.isSuccessful)
        assertEquals(10, response.body()!!.id)
    }

    @Test
    fun getSurfacesNotFound() = runTest {
        server.enqueue(MockResponse().setResponseCode(404))

        assertEquals(404, api.get(999).code())
    }
}
