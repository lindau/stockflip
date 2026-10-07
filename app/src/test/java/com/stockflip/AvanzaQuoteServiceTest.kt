package com.stockflip

import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class AvanzaQuoteServiceTest {
    private lateinit var server: MockWebServer
    private lateinit var service: AvanzaQuoteService
    private var searchBody = """{"hits":[{"orderBookId":"649096","title":"Plejd (PLEJD)","flagCode":"SE"}]}"""
    private var chartCode = 200
    private val chartBody = """{"ohlc":[{"timestamp":1791356400000,"close":935.5},{"timestamp":1791360000000,"close":923.5}],"previousClosingPrice":937.0}"""

    @Before
    fun setup() {
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = when {
                request.path!!.startsWith("/_api/search/filtered-search") -> MockResponse().setBody(searchBody)
                request.path!!.startsWith("/_api/price-chart/stock/649096") -> MockResponse().setResponseCode(chartCode).setBody(chartBody)
                else -> MockResponse().setResponseCode(404)
            }
        }
        server.start()
        service = AvanzaQuoteService(OkHttpClient(), server.url("/").toString().trimEnd('/'))
    }

    @After
    fun tearDown() = server.shutdown()

    @Test
    fun `returns last minute bar and previous close`() = runBlocking {
        val quote = service.latestQuote("PLEJD.ST")!!
        assertEquals(923.5, quote.price, 0.0001)
        assertEquals(1791360000L, quote.epochSeconds)
        assertEquals(937.0, quote.previousClose!!, 0.0001)
    }

    @Test
    fun `rejects a hit for another company`() = runBlocking {
        searchBody = """{"hits":[{"orderBookId":"1","title":"Plejd Holding (PLEJDH)"}]}"""
        assertNull(service.latestQuote("PLEJD.ST"))
    }

    @Test
    fun `rejects a non Swedish listing with the same ticker`() = runBlocking {
        searchBody = """{"hits":[{"orderBookId":"1301423","title":"Evotec ADR (PLEJD)","flagCode":"US"}]}"""
        assertNull(service.latestQuote("PLEJD.ST"))
    }

    @Test
    fun `non Swedish symbol is not looked up`() = runBlocking {
        assertNull(service.latestQuote("AAPL"))
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `http error gives null`() = runBlocking {
        chartCode = 500
        assertNull(service.latestQuote("PLEJD.ST"))
    }

    @Test
    fun `share class ticker is normalised`() {
        assertEquals("VOLV B", AvanzaQuoteService.tickerOf("VOLV-B.ST"))
        assertNull(AvanzaQuoteService.tickerOf("AAPL"))
    }
}
