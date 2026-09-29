package com.stockflip.ui.createwatch

import com.stockflip.AlertExpression
import com.stockflip.AlertRule
import com.stockflip.StockSearchResult
import com.stockflip.WatchType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CombinedPairDraftTest {
    private val price = ConditionDraft(ConditionKind.PRICE, above = false, value = "245")
    private val pe = ConditionDraft(ConditionKind.PE, above = false, value = "12,5", or = true)

    @Test
    fun `bygger OCH och ELLER vänsterassociativt`() {
        val r = buildCombined("VOLV-B.ST", listOf(price, ConditionDraft(ConditionKind.DAILY_MOVE, value = "3"), pe)) as CombinedResult.Ok
        val or = r.expression as AlertExpression.Or
        assertTrue(or.left is AlertExpression.And)
        assertTrue(or.right is AlertExpression.Single)
    }

    @Test
    fun `validerar symbol, tom lista och värden`() {
        assertTrue(buildCombined("", listOf(price)) is CombinedResult.Invalid)
        assertTrue(buildCombined("X", emptyList()) is CombinedResult.Invalid)
        assertTrue(buildCombined("X", listOf(price.copy(value = "abc"))) is CombinedResult.Invalid)
        assertTrue(buildCombined("X", listOf(ConditionDraft(ConditionKind.DRAWDOWN, value = "120"))) is CombinedResult.Invalid)
    }

    @Test
    fun `decompose är invers till build`() {
        val list = listOf(price, ConditionDraft(ConditionKind.DRAWDOWN, value = "20", or = false), pe)
        val expr = (buildCombined("VOLV-B.ST", list) as CombinedResult.Ok).expression
        val (symbol, back) = decomposeCombined(expr)!!
        assertEquals("VOLV-B.ST", symbol)
        assertEquals(list.map { it.kind to it.or }, back.map { it.kind to it.or })
        assertEquals(false, back[0].above)
        assertEquals("12.5", back[2].value)
    }

    @Test
    fun `decompose avvisar NOT och flera aktier`() {
        val a = AlertExpression.Single(AlertRule.SinglePrice("A", AlertRule.PriceComparisonType.ABOVE, 1.0))
        val b = AlertExpression.Single(AlertRule.SinglePrice("B", AlertRule.PriceComparisonType.ABOVE, 1.0))
        assertNull(decomposeCombined(AlertExpression.Not(a)))
        assertNull(decomposeCombined(AlertExpression.And(a, b)))
    }

    private val volvo = StockSearchResult("VOLV-A.ST", "Volvo A")
    private val volvoB = StockSearchResult("VOLV-B.ST", "Volvo B")

    @Test
    fun `par valideras och tom skillnad blir noll`() {
        assertTrue(buildPair(null, volvoB, "", false) is PairResult.Invalid)
        assertTrue(buildPair(volvo, volvo, "", false) is PairResult.Invalid)
        assertTrue(buildPair(volvo, volvoB, "x", false) is PairResult.Invalid)
        val ok = buildPair(volvo, volvoB, "", true) as PairResult.Ok
        assertEquals(WatchType.PricePair(0.0, true), ok.item.watchType)
        assertEquals("VOLV-B.ST", ok.item.ticker2)
    }
}
