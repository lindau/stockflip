package com.stockflip

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PairTriggerEvaluatorTest {

    @Test
    fun `evaluate triggers when ticker two is over target spread`() {
        val result = PairTriggerEvaluator.evaluate(
            priceA = 100.0,
            priceB = 105.0,
            spreadTarget = 5.0,
            notifyWhenEqual = false
        )

        assertEquals(PairTriggerSide.TICKER2_OVER, result?.side)
        assertEquals(5.0, result?.absoluteSpread ?: 0.0, 0.001)
    }

    @Test
    fun `evaluate triggers when ticker one is over target spread`() {
        val result = PairTriggerEvaluator.evaluate(
            priceA = 100.0,
            priceB = 95.0,
            spreadTarget = 5.0,
            notifyWhenEqual = false
        )

        assertEquals(PairTriggerSide.TICKER1_OVER, result?.side)
        assertEquals(5.0, result?.absoluteSpread ?: 0.0, 0.001)
    }

    @Test
    fun `evaluate does not trigger below target spread`() {
        val result = PairTriggerEvaluator.evaluate(
            priceA = 100.0,
            priceB = 96.0,
            spreadTarget = 5.0,
            notifyWhenEqual = false
        )

        assertNull(result)
    }
}
