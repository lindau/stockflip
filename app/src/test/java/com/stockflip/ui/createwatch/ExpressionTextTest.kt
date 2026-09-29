package com.stockflip.ui.createwatch

import com.stockflip.AlertExpression
import com.stockflip.AlertRule
import org.junit.Assert.assertEquals
import org.junit.Test

class ExpressionTextTest {
    private fun price(v: Double, above: Boolean = true) = AlertExpression.Single(
        AlertRule.SinglePrice("A", if (above) AlertRule.PriceComparisonType.ABOVE else AlertRule.PriceComparisonType.BELOW, v)
    )
    private fun pe(v: Double) = AlertExpression.Single(
        AlertRule.SingleKeyMetric("A", AlertRule.KeyMetricType.PE_RATIO, v, AlertRule.PriceComparisonType.BELOW)
    )

    @Test
    fun `enkel och sammansatt text`() {
        assertEquals("Pris över 430 kr", describeExpression(price(430.0)))
        assertEquals("Pris över 430 kr OCH P/E under 8", describeExpression(AlertExpression.And(price(430.0), pe(8.0))))
    }

    @Test
    fun `grupper med annan operator parentesomsluts`() {
        val expr = AlertExpression.Or(AlertExpression.And(price(430.0), pe(8.0)), price(300.0, above = false))
        assertEquals("(Pris över 430 kr OCH P/E under 8) ELLER Pris under 300 kr", describeExpression(expr))
    }

    @Test
    fun `samma operator kedjas utan parentes`() {
        val expr = AlertExpression.And(AlertExpression.And(price(1.0), pe(2.0)), price(3.0))
        assertEquals("Pris över 1 kr OCH P/E under 2 OCH Pris över 3 kr", describeExpression(expr))
    }

    @Test
    fun `dagsrörelse, fall och not`() {
        val daily = AlertExpression.Single(AlertRule.SingleDailyMove("A", 3.0, AlertRule.DailyMoveDirection.BOTH))
        val draw = AlertExpression.Single(AlertRule.SingleDrawdownFromHigh("A", AlertRule.DrawdownDropType.PERCENTAGE, 20.0))
        assertEquals("Dagsrörelse ±3 %", describeExpression(daily))
        assertEquals("Från 52v-högsta −20 %", describeExpression(draw))
        assertEquals("inte (Dagsrörelse ±3 %)", describeExpression(AlertExpression.Not(daily)))
    }
}
