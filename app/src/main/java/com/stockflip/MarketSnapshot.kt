package com.stockflip

/**
 * Snapshot av marknadsdata för en eller flera aktier.
 * Används av AlertEvaluator för att utvärdera alert-regler.
 * 
 * Enligt PRD Fas 1 behöver vi:
 * - lastPrice
 * - previousClose (för dagsförändring)
 * - week52High (för drawdown)
 * - allTimeHigh (för historisk drawdown)
 * - dailyChangePercent (beräknat från lastPrice och previousClose)
 * 
 * Enligt PRD Fas 2 behöver vi även:
 * - keyMetrics (P/E, P/S, Dividend Yield)
 */
data class MarketSnapshot(
    /**
     * För single-stock alerts: senaste pris
     * För pair alerts: pris för symbolA
     */
    val lastPrice: Double?,
    
    /**
     * För single-stock alerts: föregående stängning
     * För pair alerts: pris för symbolB
     */
    val previousCloseOrPriceB: Double?,
    
    /**
     * 52-veckors högsta (används för drawdown-bevakning)
     */
    val week52High: Double? = null,

    /**
     * Historiskt högsta pris (används för bevakning från högsta pris).
     */
    val allTimeHigh: Double? = null,
    
    /**
     * Nyckeltal (används för KeyMetrics-bevakning, Fas 2).
     * Map där nyckeln är metricType och värdet är det aktuella värdet.
     */
    val keyMetrics: Map<AlertRule.KeyMetricType, Double> = emptyMap()
) {
    /**
     * Beräknar dagsförändring i procent.
     */
    fun getDailyChangePercent(): Double? {
        val current = lastPrice ?: return null
        val previous = previousCloseOrPriceB ?: return null
        
        if (previous <= 0) {
            return null
        }
        
        return ((current - previous) / previous) * 100
    }
    
    /**
     * Skapar snapshot för single-stock alert.
     */
    companion object {
        fun forSingleStock(
            lastPrice: Double?,
            previousClose: Double?,
            week52High: Double? = null,
            keyMetrics: Map<AlertRule.KeyMetricType, Double> = emptyMap(),
            allTimeHigh: Double? = null
        ): MarketSnapshot {
            return MarketSnapshot(
                lastPrice = lastPrice,
                previousCloseOrPriceB = previousClose,
                week52High = week52High,
                allTimeHigh = allTimeHigh,
                keyMetrics = keyMetrics
            )
        }
        
        /**
         * Skapar snapshot för pair alert.
         */
        fun forPair(
            priceA: Double?,
            priceB: Double?
        ): MarketSnapshot {
            return MarketSnapshot(
                lastPrice = priceA,
                previousCloseOrPriceB = priceB,
                week52High = null,
                allTimeHigh = null
            )
        }
    }
}
