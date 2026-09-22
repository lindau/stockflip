package com.stockflip

import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class GithubReleaseServiceTest {
    private lateinit var mockWebServer: MockWebServer
    private lateinit var api: GithubReleaseApi
    private lateinit var mapper: GithubReleaseMapper

    @Before
    fun setup() {
        mockWebServer = MockWebServer()
        mockWebServer.start()
        val okHttpClient = OkHttpClient.Builder().build()
        val retrofit = Retrofit.Builder()
            .baseUrl(mockWebServer.url("/"))
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        api = retrofit.create(GithubReleaseApi::class.java)
        mapper = GithubReleaseMapper(api)
    }

    @After
    fun tearDown() {
        mockWebServer.shutdown()
    }

    @Test
    fun `getLatestReleaseInfo parses tag name and strips v prefix`() = runBlocking {
        mockWebServer.enqueue(okResponse(readResource("github/release_latest.json")))
        val release = mapper.getLatestReleaseInfo()
        assertEquals("1.2.94", release?.versionName)
        val request = mockWebServer.takeRequest()
        assertEquals("/repos/lindau/StockFlip/releases/latest", request.path)
    }

    @Test
    fun `getLatestReleaseInfo finds the stockflip apk asset among others`() = runBlocking {
        mockWebServer.enqueue(okResponse(readResource("github/release_latest.json")))
        val release = mapper.getLatestReleaseInfo()
        assertEquals("https://github.com/lindau/StockFlip/releases/download/v1.2.94/stockflip.apk", release?.downloadUrl)
        assertEquals(15728640L, release?.apkSizeBytes)
    }

    @Test
    fun `getLatestReleaseInfo returns null when no stockflip apk asset is present`() = runBlocking {
        mockWebServer.enqueue(
            okResponse(
                """
                {
                  "tag_name": "v1.2.94",
                  "body": "notes",
                  "assets": [
                    { "name": "source.zip", "browser_download_url": "https://example.com/source.zip", "size": 100 }
                  ]
                }
                """.trimIndent()
            )
        )
        val release = mapper.getLatestReleaseInfo()
        assertNull(release)
    }

    @Test
    fun `getLatestReleaseInfo propagates a server error to the caller`() = runBlocking {
        mockWebServer.enqueue(MockResponse().setResponseCode(500))
        var threw = false
        try {
            mapper.getLatestReleaseInfo()
        } catch (e: Exception) {
            threw = true
        }
        assertEquals(true, threw)
    }

    private fun okResponse(body: String): MockResponse {
        return MockResponse()
            .setResponseCode(200)
            .setHeader("Content-Type", "application/json")
            .setBody(body)
    }

    private fun readResource(path: String): String {
        val inputStream = javaClass.classLoader?.getResourceAsStream(path)
            ?: throw IllegalStateException("Missing test resource: $path")
        return inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
    }
}
