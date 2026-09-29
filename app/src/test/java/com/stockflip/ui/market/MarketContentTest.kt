package com.stockflip.ui.market

import com.stockflip.StockSearchResult
import com.stockflip.repository.SearchState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MarketContentTest {
    private val volvo = StockSearchResult("VOLV-B.ST", "Volvo B", isSwedish = true)
    private val apple = StockSearchResult("AAPL", "Apple")
    private val idle = SearchState.Success(emptyList())

    @Test
    fun `kort fråga utan historik visar tips`() {
        assertEquals(MarketContent.Hint, marketContentFor("v", idle, emptyList()))
    }

    @Test
    fun `kort fråga med historik visar senast sökta`() {
        assertEquals(MarketContent.Recent(listOf(volvo)), marketContentFor("", idle, listOf(volvo)))
    }

    @Test
    fun `laddning, fel, tomt och träffar`() {
        assertEquals(MarketContent.Loading, marketContentFor("vo", SearchState.Loading, emptyList()))
        assertEquals(MarketContent.Failed("x"), marketContentFor("vo", SearchState.Error("x", "vo"), emptyList()))
        assertEquals(MarketContent.NoResults, marketContentFor("vo", idle, listOf(volvo)))
        assertEquals(MarketContent.Results(listOf(apple)), marketContentFor("aa", SearchState.Success(listOf(apple)), emptyList()))
    }

    @Test
    fun `mellanslag räknas inte som söktext`() {
        assertTrue(marketContentFor("  a ", idle, emptyList()) is MarketContent.Hint)
    }

    @Test
    fun `recent lägger nyast först utan dubbletter och kapar`() {
        val many = (1..RecentSearches.MAX).map { StockSearchResult("S$it", "Namn $it") }
        val out = RecentSearches.add(many, StockSearchResult("S5", "Namn 5"))
        assertEquals("S5", out.first().symbol)
        assertEquals(RecentSearches.MAX, out.size)
        assertEquals(1, out.count { it.symbol == "S5" })
        assertEquals(RecentSearches.MAX, RecentSearches.add(many, apple).size)
    }

    @Test
    fun `recent kodas och avkodas`() {
        val decoded = RecentSearches.decode(RecentSearches.encode(listOf(volvo, apple)))
        assertEquals(listOf("VOLV-B.ST", "AAPL"), decoded.map { it.symbol })
        assertEquals("Volvo B", decoded[0].name)
        assertTrue(decoded[0].isSwedish)
        assertTrue(RecentSearches.decode(null).isEmpty())
    }
}
