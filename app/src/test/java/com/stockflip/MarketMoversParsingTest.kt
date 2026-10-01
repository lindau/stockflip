package com.stockflip

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MarketMoversParsingTest {

    private val body = """
        {"finance":{"result":[{"title":"Day Gainers","total":82,"quotes":[
          {"symbol":"BETCO.ST","shortName":"Better Collective A/S","regularMarketPrice":74.5,"regularMarketChangePercent":6.125361,"regularMarketVolume":221665,"exchange":"STO","currency":"SEK","quoteType":"EQUITY"},
          {"symbol":"UTHR","shortName":"United Therapeutics Corporation","regularMarketPrice":541.89,"regularMarketChangePercent":12.5491,"regularMarketVolume":3299585,"exchange":"NMS","currency":"USD","quoteType":"EQUITY"},
          {"symbol":"NOPRICE.ST","shortName":"Utan pris","quoteType":"EQUITY"},
          {"symbol":"ZEROPRICE.ST","shortName":"Nollpris","regularMarketPrice":0.0,"quoteType":"EQUITY"},
          {"symbol":"SPY","shortName":"SPDR S&P 500","regularMarketPrice":500.0,"quoteType":"ETF"},
          {"symbol":"LMGAB.ST","regularMarketPrice":48.3,"regularMarketChangePercent":3.98,"quoteType":"EQUITY"}
        ]}],"error":null}}
    """.trimIndent()

    @Test
    fun `parseMovers läser fält och hoppar över rader utan pris eller andra typer`() {
        val movers = YahooFinanceService.parseMovers(body)

        assertEquals(listOf("BETCO.ST", "UTHR", "LMGAB.ST"), movers.map { it.symbol })
        val first = movers[0]
        assertEquals("Better Collective A/S", first.name)
        assertEquals(74.5, first.price, 0.0)
        assertEquals(6.125361, first.changePercent!!, 1e-6)
        assertEquals(221665L, first.volume)
        assertEquals("SEK", first.currency)
        assertEquals("USD", movers[1].currency)
    }

    @Test
    fun `parseMovers faller tillbaka på symbol som namn och valuta efter börs`() {
        val last = YahooFinanceService.parseMovers(body).last()

        assertEquals("LMGAB.ST", last.name)
        assertEquals("SEK", last.currency)
        assertNull(last.volume)
    }

    @Test
    fun `parseMovers ger tom lista för tomt eller oväntat svar`() {
        assertTrue(YahooFinanceService.parseMovers("""{"finance":{"result":[],"error":null}}""").isEmpty())
        assertTrue(YahooFinanceService.parseMovers("""{"finance":{"result":null,"error":{"code":"Unauthorized"}}}""").isEmpty())
    }

    @Test
    fun `svensk skannerkropp filtrerar på Stockholmsbörsen och marknadsvärde`() {
        val json = JSONObject(YahooFinanceService.swedishScreenerBody("percentchange", "DESC", 15))

        assertEquals(15, json.getInt("size"))
        assertEquals("percentchange", json.getString("sortField"))
        assertEquals("DESC", json.getString("sortType"))
        val operands = json.getJSONObject("query").getJSONArray("operands")
        assertEquals("STO", operands.getJSONObject(0).getJSONArray("operands").getString(1))
        assertEquals("intradaymarketcap", operands.getJSONObject(1).getJSONArray("operands").getString(0))
    }

    @Test
    fun `parseTrendingSymbols läser symbolerna i ordning`() {
        val json = """{"finance":{"result":[{"count":3,"quotes":[{"symbol":"MU"},{"symbol":"GOOG"},{"symbol":""},{"symbol":"SNDK"}]}],"error":null}}"""

        assertEquals(listOf("MU", "GOOG", "SNDK"), YahooFinanceService.parseTrendingSymbols(json))
        assertTrue(YahooFinanceService.parseTrendingSymbols("""{"finance":{"result":[],"error":null}}""").isEmpty())
    }

    @Test
    fun `parseQuoteResponse läser v7-quote med samma fält`() {
        val json = """{"quoteResponse":{"result":[
            {"symbol":"MU","shortName":"Micron Technology, Inc.","regularMarketPrice":1065.11,"regularMarketChangePercent":0.0028,"regularMarketVolume":28568102,"currency":"USD","quoteType":"EQUITY"},
            {"symbol":"ETFX","regularMarketPrice":10.0,"quoteType":"ETF"}
        ],"error":null}}"""

        val movers = YahooFinanceService.parseQuoteResponse(json)

        assertEquals(listOf("MU"), movers.map { it.symbol })
        assertEquals(1065.11, movers[0].price, 0.0)
    }

    @Test
    fun `Trendar finns bara för USA`() {
        assertTrue(MoverList.TRENDING.isAvailableFor(MoverMarket.US))
        assertTrue(!MoverList.TRENDING.isAvailableFor(MoverMarket.SWEDEN))
        assertTrue(MoverList.GAINERS.isAvailableFor(MoverMarket.SWEDEN))
    }
}
