package com.stockflip

/**
 * Gemensam modell för bevakningsregler (alerts) som stödjer både par- och single-stock bevakningar.
 * 
 * Enligt PRD Fas 1 ska alla AlertRule kunna evalueras via en gemensam funktion.
 * Detta är den centrala modellen som används av AlertEvaluator.
 */
sealed class AlertRule {
    /**
     * Par-bevakning: Larm när prisspread mellan två aktier når ett visst värde.
     * Detta är befintlig funktionalitet som måste fortsätta fungera.
     */
    data class PairSpread(
        val symbolA: String,
        val symbolB: String,
        val spreadTarget: Double,
        val notifyWhenEqual: Boolean = false
    ) : AlertRule()

    /**
     * Single-stock: Målpris-bevakning.
     * 
     * @param symbol Aktiens symbol
     * @param comparisonType Typ av jämförelse: BELOW (≤), ABOVE (≥), eller WITHIN_RANGE
     * @param priceLimit För BELOW/ABOVE: tröskelvärde. För WITHIN_RANGE: minPrice
     * @param maxPrice För WITHIN_RANGE: maxPrice. Ignoreras för BELOW/ABOVE
     */
    data class SinglePrice(
        val symbol: String,
        val comparisonType: PriceComparisonType,
        val priceLimit: Double,
        val maxPrice: Double? = null // Används endast för WITHIN_RANGE
    ) : AlertRule() {
        init {
            require(comparisonType != PriceComparisonType.WITHIN_RANGE || maxPrice != null) {
                "maxPrice måste anges när comparisonType är WITHIN_RANGE"
            }
            require(comparisonType != PriceComparisonType.WITHIN_RANGE || (maxPrice != null && maxPrice > priceLimit)) {
                "maxPrice måste vara större än priceLimit för WITHIN_RANGE"
            }
        }
    }

    /**
     * Single-stock: Drawdown från vald toppnivå.
     * 
     * @param symbol Aktiens symbol
     * @param dropType Typ av nedgång: PERCENTAGE eller ABSOLUTE
     * @param dropValue För PERCENTAGE: procentuell nedgång (t.ex. 15.0 för 15%)
     *                  För ABSOLUTE: absolut nedgång i SEK (t.ex. 50.0 för 50 SEK)
     * @param reference Toppnivå att jämföra mot: 52-veckorshögsta eller historiskt högsta pris
     */
    data class SingleDrawdownFromHigh(
        val symbol: String,
        val dropType: DrawdownDropType,
        val dropValue: Double,
        val reference: HighReference = HighReference.FIFTY_TWO_WEEK_HIGH
    ) : AlertRule() {
        init {
            when (dropType) {
                DrawdownDropType.PERCENTAGE -> {
                    require(dropValue > 0 && dropValue <= 100) {
                        "dropValue måste vara mellan 0 och 100 för PERCENTAGE"
                    }
                }
                DrawdownDropType.ABSOLUTE -> {
                    require(dropValue > 0) {
                        "dropValue måste vara större än 0 för ABSOLUTE"
                    }
                }
            }
        }
    }

    /**
     * Single-stock: Dagsrörelse i procent.
     * 
     * @param symbol Aktiens symbol
     * @param percentThreshold Tröskelvärde i procent (t.ex. 5.0 för 5%)
     * @param direction Riktning: UP (≥ +threshold), DOWN (≤ -threshold), eller BOTH (|change| ≥ threshold)
     */
    data class SingleDailyMove(
        val symbol: String,
        val percentThreshold: Double,
        val direction: DailyMoveDirection
    ) : AlertRule() {
        init {
            require(percentThreshold > 0) {
                "percentThreshold måste vara större än 0"
            }
        }
    }

    /**
     * Single-stock: Nyckeltal-bevakning (Fas 2).
     * 
     * @param symbol Aktiens symbol
     * @param metricType Typ av nyckeltal: PE_RATIO, PS_RATIO, eller DIVIDEND_YIELD
     * @param targetValue Tröskelvärde för nyckeltalet
     * @param direction Riktning: ABOVE (≥ targetValue) eller BELOW (≤ targetValue)
     */
    data class SingleKeyMetric(
        val symbol: String,
        val metricType: KeyMetricType,
        val targetValue: Double,
        val direction: PriceComparisonType
    ) : AlertRule() {
        init {
            require(targetValue > 0) {
                "targetValue måste vara större än 0"
            }
            require(direction == PriceComparisonType.ABOVE || direction == PriceComparisonType.BELOW) {
                "direction måste vara ABOVE eller BELOW för KeyMetric"
            }
        }
    }

    /**
     * Typ av prisjämförelse för SinglePrice.
     */
    enum class PriceComparisonType {
        BELOW,          // Pris ≤ priceLimit
        ABOVE,          // Pris ≥ priceLimit
        WITHIN_RANGE    // priceLimit ≤ Pris ≤ maxPrice
    }

    /**
     * Riktning för dagsrörelse-bevakning.
     */
    enum class DailyMoveDirection {
        UP,     // Alert när dailyChange ≥ +percentThreshold
        DOWN,   // Alert när dailyChange ≤ -percentThreshold
        BOTH    // Alert när |dailyChange| ≥ percentThreshold
    }

    /**
     * Typ av nedgång för drawdown-bevakning.
     */
    enum class DrawdownDropType {
        PERCENTAGE,  // Procentuell nedgång från vald toppnivå
        ABSOLUTE     // Absolut nedgång från vald toppnivå
    }

    /**
     * Referenspunkt för drawdown-bevakning.
     */
    enum class HighReference {
        FIFTY_TWO_WEEK_HIGH,
        ALL_TIME_HIGH
    }

    /**
     * Typ av nyckeltal för KeyMetric-bevakning.
     */
    enum class KeyMetricType {
        PE_RATIO,        // Price-to-Earnings ratio
        PS_RATIO,        // Price-to-Sales ratio
        DIVIDEND_YIELD   // Dividend yield percentage
    }
}
