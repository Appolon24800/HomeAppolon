package dev.appolon.homelab.arr.api.radarr

import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class CreditApiTest {
    private lateinit var server: MockWebServer
    private lateinit var api: dev.appolon.homelab.arr.api.radarr.api.CreditApi

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        api = RadarrApis.credit(TestRadarr.retrofit(server))
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun listSendsFiltersAndParsesCredits() = runTest {
        server.enqueue(MockResponse().setBody(TestRadarr.fixture("credit", "credit-list.json")))

        val credits = api.list(movieId = 100)

        assertEquals("/api/v3/credit?movieId=100", server.takeRequest().path)
        assertEquals(1, credits.size)
        assertEquals("Example Person", credits[0].personName)
        assertEquals(dev.appolon.homelab.arr.api.radarr.model.CreditType.CREW, credits[0].type)
    }

    @Test
    fun getReturnsCredit() = runTest {
        server.enqueue(MockResponse().setBody(TestRadarr.fixture("credit", "credit.json")))

        val response = api.get(77)

        assertTrue(response.isSuccessful)
        assertEquals(77, response.body()!!.id)
    }

    @Test
    fun getSurfacesNotFound() = runTest {
        server.enqueue(MockResponse().setResponseCode(404))

        assertEquals(404, api.get(999).code())
    }
}
