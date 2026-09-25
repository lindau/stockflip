package com.stockflip

import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
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

    @Test
    fun `getDailyChangePercent returns direct market change percent when present`() = kotlinx.coroutines.runBlocking {
        mockWebServer.enqueue(okResponse("""
            {
              "chart": {
                "result": [
                  {
                    "meta": {
                      "regularMarketPrice": 300.12,
                      "regularMarketPreviousClose": 295.0,
                      "regularMarketChangePercent": -1.25
                    }
                  }
                ],
                "error": null
              }
            }
        """.trimIndent()))

        val actualChangePercent: Double? = service.getDailyChangePercent("VOLV-B.ST")

        assertEquals(-1.25, actualChangePercent!!, 0.0001)
    }

    @Test
    fun `getDailyChangePercent falls back to chart previous close`() = kotlinx.coroutines.runBlocking {
        mockWebServer.enqueue(okResponse("""
            {
              "chart": {
                "result": [
                  {
                    "meta": {
                      "regularMarketPrice": 300.12,
                      "chartPreviousClose": 295.0
                    }
                  }
                ],
                "error": null
              }
            }
        """.trimIndent()))

        val actualChangePercent: Double? = service.getDailyChangePercent("VOLV-B.ST")
        val expectedChangePercent: Double = ((300.12 - 295.0) / 295.0) * 100.0

        assertEquals(expectedChangePercent, actualChangePercent!!, 0.0001)
    }

    @Test
    fun `getPreviousClose falls back to chart previous close`() = kotlinx.coroutines.runBlocking {
        mockWebServer.enqueue(okResponse("""
            {
              "chart": {
                "result": [
                  {
                    "meta": {
                      "chartPreviousClose": 295.0
                    }
                  }
                ],
                "error": null
              }
            }
        """.trimIndent()))

        val actualPreviousClose: Double? = service.getPreviousClose("VOLV-B.ST")

        assertEquals(295.0, actualPreviousClose!!, 0.0001)
    }

    @Test
    fun `getAllTimeHigh returns highest historical high`() = kotlinx.coroutines.runBlocking {
        mockWebServer.enqueue(okResponse("""
            {
              "chart": {
                "result": [
                  {
                    "meta": {
                      "regularMarketPrice": 300.12,
                      "fiftyTwoWeekHigh": 320.0,
                      "regularMarketDayHigh": 301.0
                    },
                    "timestamp": [1, 2, 3],
                    "indicators": {
                      "quote": [
                        {
                          "high": [310.0, 450.5, 400.0],
                          "close": [300.0, 430.0, 390.0]
                        }
                      ]
                    }
                  }
                ],
                "error": null
              }
            }
        """.trimIndent()))

        val actualHigh = service.getAllTimeHigh("VOLV-B.ST")

        assertEquals(450.5, actualHigh!!, 0.0001)
        val request = mockWebServer.takeRequest()
        assertEquals("/v8/finance/chart/VOLV-B.ST?range=max&interval=1mo", request.path)
    }

    @Test
    fun `concurrent price requests for the same symbol share one network call`() = kotlinx.coroutines.runBlocking {
        // Svaret fördröjs så att alla fem anropen hinner starta innan det första är klart.
        mockWebServer.enqueue(
            okResponse(readResource("yahoo/chart_VOLV-B.ST.json")).setBodyDelay(300, TimeUnit.MILLISECONDS)
        )

        val prices = coroutineScope {
            (1..5).map { async(Dispatchers.IO) { service.getStockPrice("VOLV-B.ST") } }.awaitAll()
        }

        assertEquals(List(5) { 300.12 }, prices)
        assertEquals(1, mockWebServer.requestCount)
    }

    @Test
    fun `failed quote fetch is not cached so the next call retries`() = kotlinx.coroutines.runBlocking {
        mockWebServer.enqueue(MockResponse().setResponseCode(500))
        mockWebServer.enqueue(okResponse(readResource("yahoo/chart_VOLV-B.ST.json")))

        assertNull(service.getStockPrice("VOLV-B.ST"))
        assertEquals(300.12, service.getStockPrice("VOLV-B.ST")!!, 0.0001)
        assertEquals(2, mockWebServer.requestCount)
    }

    @Test
    fun `chart is cached per symbol and period and expires after its ttl`() = kotlinx.coroutines.runBlocking {
        var now = 1_000_000L
        val cachedService = YahooMarketDataServiceImpl(api, clock = { now })
        repeat(3) { mockWebServer.enqueue(okResponse(CHART_JSON)) }

        assertNotNull(cachedService.getIntradayChart("VOLV-B.ST", ChartPeriod.YEAR))
        assertNotNull(cachedService.getIntradayChart("VOLV-B.ST", ChartPeriod.YEAR))
        assertEquals("Samma period ska komma från cachen", 1, mockWebServer.requestCount)

        assertNotNull(cachedService.getIntradayChart("VOLV-B.ST", ChartPeriod.MONTH))
        assertEquals("Annan period hämtas separat", 2, mockWebServer.requestCount)

        now += 16 * 60_000L // efter 15 min livslängd för YEAR
        assertNotNull(cachedService.getIntradayChart("VOLV-B.ST", ChartPeriod.YEAR))
        assertEquals(3, mockWebServer.requestCount)
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

    private companion object {
        val CHART_JSON: String = """
            {"chart":{"result":[{"meta":{"regularMarketPrice":3.0,"chartPreviousClose":1.0},
            "timestamp":[1,2,3],"indicators":{"quote":[{"close":[1.0,2.0,3.0]}]}}],"error":null}}
        """.trimIndent()
    }
}
