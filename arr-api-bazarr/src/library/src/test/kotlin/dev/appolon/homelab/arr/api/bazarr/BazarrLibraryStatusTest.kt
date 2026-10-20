package dev.appolon.homelab.arr.api.bazarr

import dev.appolon.homelab.arr.net.ArrJson
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

class BazarrLibraryStatusTest {
    @Test
    fun omittedOptionalQueriesLetTheServerChooseDefaults() = runTest {
        val server = MockWebServer()
        server.start()
        try {
            server.enqueue(MockResponse().setBody("{}"))
            val retrofit = Retrofit.Builder().baseUrl(server.url("/"))
                .addConverterFactory(ArrJson.asConverterFactory("application/json".toMediaType()))
                .build()
            BazarrLibraryApis.series(retrofit).getSeries()
            assertNull(server.takeRequest().requestUrl!!.query)
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun subtitleConflictRetainsItsStatusAndErrorBody() = runTest {
        val server = MockWebServer()
        server.start()
        try {
            server.enqueue(MockResponse().setResponseCode(409).setBody("permission issue"))
            val retrofit = Retrofit.Builder().baseUrl(server.url("/"))
                .addConverterFactory(ArrJson.asConverterFactory("application/json".toMediaType()))
                .build()
            val response = BazarrLibraryApis.moviesSubtitles(retrofit)
                .patchMoviesSubtitles(1, "en", "false", "true")
            assertEquals(409, response.code())
            assertEquals("permission issue", response.errorBody()!!.string())
        } finally {
            server.shutdown()
        }
    }
}
