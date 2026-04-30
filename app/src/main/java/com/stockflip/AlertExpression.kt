package com.stockflip

/**
 * Expression-lager för att kombinera flera AlertRule med logiska operatorer.
 * 
 * Enligt PRD Fas 3 ska detta möjliggöra kombinerade larm som:
 * - "pris 20% under 52w high OCH P/E < 20"
 * - "pris under X ELLER drawdown > Y%"
 * 
 * AlertExpression är rekursiv och kan innehålla andra AlertExpression.
 */
sealed class AlertExpression {
    /**
     * Enskild alert-regel.
     */
    data class Single(val rule: AlertRule) : AlertExpression()

    /**
     * Logisk AND: Båda uttrycken måste vara sanna.
     */
    data class And(val left: AlertExpression, val right: AlertExpression) : AlertExpression()

    /**
     * Logisk OR: Minst ett av uttrycken måste vara sant.
     */
    data class Or(val left: AlertExpression, val right: AlertExpression) : AlertExpression()

    /**
     * Logisk NOT: Negation av uttrycket.
     */
    data class Not(val inner: AlertExpression) : AlertExpression()

    /**
     * Hämtar alla symboler som används i detta uttryck.
     * Används för att veta vilka MarketSnapshot som behövs för evaluering.
     */
    fun getSymbols(): Set<String> {
        return when (this) {
            is Single -> {
                when (val rule = this.rule) {
                    is AlertRule.PairSpread -> setOf(rule.symbolA, rule.symbolB)
                    is AlertRule.SinglePrice -> setOf(rule.symbol)
                    is AlertRule.SingleDrawdownFromHigh -> setOf(rule.symbol)
                    is AlertRule.SingleDailyMove -> setOf(rule.symbol)
                    is AlertRule.SingleKeyMetric -> setOf(rule.symbol)
                }
            }
            is And -> left.getSymbols() + right.getSymbols()
            is Or -> left.getSymbols() + right.getSymbols()
            is Not -> inner.getSymbols()
        }
    }

    /**
     * Hämtar en beskrivning av uttrycket för visning i UI.
     */
    fun getDescription(): String {
        return when (this) {
            is Single -> {
                when (val rule = this.rule) {
                    is AlertRule.PairSpread -> "Spread ${rule.symbolA}-${rule.symbolB} ≥ ${rule.spreadTarget}"
                    is AlertRule.SinglePrice -> {
                        when (rule.comparisonType) {
                            AlertRule.PriceComparisonType.BELOW -> "${rule.symbol} ≤ ${rule.priceLimit}"
                            AlertRule.PriceComparisonType.ABOVE -> "${rule.symbol} ≥ ${rule.priceLimit}"
                            AlertRule.PriceComparisonType.WITHIN_RANGE -> {
                                "${rule.symbol} i ${rule.priceLimit}-${rule.maxPrice}"
                            }
                        }
                    }
                    is AlertRule.SingleDrawdownFromHigh -> {
                        val reference = when (rule.reference) {
                            AlertRule.HighReference.FIFTY_TWO_WEEK_HIGH -> "52v"
                            AlertRule.HighReference.ALL_TIME_HIGH -> "högsta pris"
                        }
                        "${rule.symbol} drawdown från $reference ${rule.dropValue}${if (rule.dropType == AlertRule.DrawdownDropType.PERCENTAGE) "%" else " SEK"}"
                    }
                    is AlertRule.SingleDailyMove -> {
                        "${rule.symbol} dagsrörelse ${rule.percentThreshold}%"
                    }
                    is AlertRule.SingleKeyMetric -> {
                        val metricName = when (rule.metricType) {
	                            AlertRule.KeyMetricType.PE_RATIO -> "P/E"
	                            AlertRule.KeyMetricType.PS_RATIO -> "P/S"
	                            AlertRule.KeyMetricType.DIVIDEND_YIELD -> "Yield"
	                            AlertRule.KeyMetricType.EARNINGS_PER_SHARE -> "Vinst/aktie"
	                        }
                        "${rule.symbol} $metricName ${if (rule.direction == AlertRule.PriceComparisonType.ABOVE) "≥" else "≤"} ${rule.targetValue}"
                    }
                }
            }
            is And -> "(${left.getDescription()}) OCH (${right.getDescription()})"
            is Or -> "(${left.getDescription()}) ELLER (${right.getDescription()})"
            is Not -> "INTE (${inner.getDescription()})"
        }
    }
}
