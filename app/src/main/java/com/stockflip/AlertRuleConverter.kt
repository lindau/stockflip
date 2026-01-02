package com.stockflip

/**
 * Helper för att konvertera WatchItem (WatchType) till AlertRule.
 * Detta gör det möjligt att använda AlertEvaluator med WatchItems.
 */
object AlertRuleConverter {
    /**
     * Konverterar WatchItem till AlertRule.
     * 
     * @param watchItem WatchItem att konvertera
     * @return AlertRule eller null om WatchItem inte kan konverteras
     */
    fun toAlertRule(watchItem: WatchItem): AlertRule? {
        val watchType = watchItem.watchType
        
        return when (watchType) {
            is WatchType.PricePair -> {
                val ticker1 = watchItem.ticker1 ?: return null
                val ticker2 = watchItem.ticker2 ?: return null
                AlertRule.PairSpread(
                    symbolA = ticker1,
                    symbolB = ticker2,
                    spreadTarget = watchType.priceDifference,
                    notifyWhenEqual = watchType.notifyWhenEqual
                )
            }
            is WatchType.PriceTarget -> {
                val ticker = watchItem.ticker ?: return null
                val comparisonType = when (watchType.direction) {
                    WatchType.PriceDirection.BELOW -> AlertRule.PriceComparisonType.BELOW
                    WatchType.PriceDirection.ABOVE -> AlertRule.PriceComparisonType.ABOVE
                }
                AlertRule.SinglePrice(
                    symbol = ticker,
                    comparisonType = comparisonType,
                    priceLimit = watchType.targetPrice,
                    maxPrice = null
                )
            }
            is WatchType.PriceRange -> {
                val ticker = watchItem.ticker ?: return null
                AlertRule.SinglePrice(
                    symbol = ticker,
                    comparisonType = AlertRule.PriceComparisonType.WITHIN_RANGE,
                    priceLimit = watchType.minPrice,
                    maxPrice = watchType.maxPrice
                )
            }
            is WatchType.ATHBased -> {
                val ticker = watchItem.ticker ?: return null
                // ATHBased använder 52w high enligt PRD
                val dropType = when (watchType.dropType) {
                    WatchType.DropType.PERCENTAGE -> AlertRule.DrawdownDropType.PERCENTAGE
                    WatchType.DropType.ABSOLUTE -> AlertRule.DrawdownDropType.ABSOLUTE
                }
                AlertRule.SingleDrawdownFromHigh(
                    symbol = ticker,
                    dropType = dropType,
                    dropValue = watchType.dropValue
                )
            }
            is WatchType.DailyMove -> {
                val ticker = watchItem.ticker ?: return null
                val direction = when (watchType.direction) {
                    WatchType.DailyMoveDirection.UP -> AlertRule.DailyMoveDirection.UP
                    WatchType.DailyMoveDirection.DOWN -> AlertRule.DailyMoveDirection.DOWN
                    WatchType.DailyMoveDirection.BOTH -> AlertRule.DailyMoveDirection.BOTH
                }
                AlertRule.SingleDailyMove(
                    symbol = ticker,
                    percentThreshold = watchType.percentThreshold,
                    direction = direction
                )
            }
            is WatchType.KeyMetrics -> {
                val ticker = watchItem.ticker ?: return null
                val metricType = when (watchType.metricType) {
                    WatchType.MetricType.PE_RATIO -> AlertRule.KeyMetricType.PE_RATIO
                    WatchType.MetricType.PS_RATIO -> AlertRule.KeyMetricType.PS_RATIO
                    WatchType.MetricType.DIVIDEND_YIELD -> AlertRule.KeyMetricType.DIVIDEND_YIELD
                }
                val direction = when (watchType.direction) {
                    WatchType.PriceDirection.ABOVE -> AlertRule.PriceComparisonType.ABOVE
                    WatchType.PriceDirection.BELOW -> AlertRule.PriceComparisonType.BELOW
                }
                AlertRule.SingleKeyMetric(
                    symbol = ticker,
                    metricType = metricType,
                    targetValue = watchType.targetValue,
                    direction = direction
                )
            }
        }
    }
    
    /**
     * Konverterar StockPair till AlertRule.
     */
    fun toAlertRule(pair: StockPair): AlertRule {
        return AlertRule.PairSpread(
            symbolA = pair.ticker1,
            symbolB = pair.ticker2,
            spreadTarget = pair.priceDifference,
            notifyWhenEqual = pair.notifyWhenEqual
        )
    }
}

