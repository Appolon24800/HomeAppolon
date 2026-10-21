package dev.appolon.homelab.arr.api.lidarr

import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import retrofit2.HttpException
import retrofit2.Retrofit

@RunWith(Parameterized::class)
class LidarrCalendarErrorTest(
    private val tag: String,
    private val call: suspend (Retrofit) -> Any?,
) {
    private val server = MockWebServer()

    @Before
    fun setUp() = server.start()

    @After
    fun tearDown() = server.shutdown()

    @Test
    fun httpErrorPreservesStatus() = runTest {
        server.enqueue(MockResponse().setResponseCode(503).setBody("{}"))
        try {
            call(calendarRetrofit(server))
            fail("Expected HttpException for $tag")
        } catch (error: HttpException) {
            assertEquals(503, error.code())
        }
    }

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun tags(): List<Array<Any>> {
            val calls: Map<String, suspend (Retrofit) -> Any?> = linkedMapOf(
                "Calendar" to { LidarrCalendarApis.calendar(it).getCalendar() },
                "CalendarFeed" to { LidarrCalendarApis.calendarFeed(it).getCalendarFeed() },
            )
            return calls.map { (tag, call) -> arrayOf(tag, call) }
        }
    }
}
