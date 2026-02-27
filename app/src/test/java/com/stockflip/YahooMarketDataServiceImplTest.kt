package com.stockflip

import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class YahooMarketDataServiceImplTest {
    private lateinit var mockWebServer: MockWebServer
    private lateinit var api: YahooFinanceApi
    private lateinit var service: YahooMarketDataServiceImpl

    @Before
    fun setup() {
        mockWebServer = MockWebServer()
        mockWebServer.start()
        val okHttpClient: OkHttpClient = OkHttpClient.Builder().build()
        val retrofit: Retrofit = Retrofit.Builder()
            .baseUrl(mockWebServer.url("/"))
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        api = retrofit.create(YahooFinanceApi::class.java)
        service = YahooMarketDataServiceImpl(api)
    }

    @After
    fun tearDown() {
        mockWebServer.shutdown()
    }

    @Test
    fun `getStockPrice returns price for Swedish stock`() = kotlinx.coroutines.runBlocking {
        mockWebServer.enqueue(okResponse(readResource("yahoo/chart_VOLV-B.ST.json")))
        val actualPrice: Double? = service.getStockPrice("VOLV-B.ST")
        assertNotNull(actualPrice)
        assertEquals(300.12, actualPrice!!, 0.0001)
        val request = mockWebServer.takeRequest()
        assertEquals("/v8/finance/chart/VOLV-B.ST", request.path)
    }

    @Test
    fun `getCurrency returns SEK for Swedish stock`() = kotlinx.coroutines.runBlocking {
        mockWebServer.enqueue(okResponse(readResource("yahoo/chart_VOLV-B.ST.json")))
        val actualCurrency: String? = service.getCurrency("VOLV-B.ST")
        assertEquals("SEK", actualCurrency)
    }

    @Test
    fun `getExchange returns STO for Swedish stock`() = kotlinx.coroutines.runBlocking {
        mockWebServer.enqueue(okResponse(readResource("yahoo/chart_VOLV-B.ST.json")))
        val actualExchange: String? = service.getExchange("VOLV-B.ST")
        assertEquals("STO", actualExchange)
    }

    @Test
    fun `getPreviousClose returns value for Swedish stock`() = kotlinx.coroutines.runBlocking {
        mockWebServer.enqueue(okResponse(readResource("yahoo/chart_VOLV-B.ST.json")))
        val actualPreviousClose: Double? = service.getPreviousClose("VOLV-B.ST")
        assertEquals(295.0, actualPreviousClose!!, 0.0001)
    }

    @Test
    fun `getDailyChangePercent calculates change percent from single chart response`() = kotlinx.coroutines.runBlocking {
        mockWebServer.enqueue(okResponse(readResource("yahoo/chart_VOLV-B.ST.json")))
        val actualChangePercent: Double? = service.getDailyChangePercent("VOLV-B.ST")
        val expectedChangePercent: Double = ((300.12 - 295.0) / 295.0) * 100.0
        assertEquals(expectedChangePercent, actualChangePercent!!, 0.0001)
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

