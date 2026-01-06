package com.stockflip

/**
 * Sammanfattning av historisk data för ett nyckeltal.
 * 
 * Enligt PRD Fas 2 ska detta innehålla min, max, snitt (och ev. median)
 * för 1, 3 och 5 års perioder.
 */
data class MetricHistorySummary(
    val metricType: WatchType.MetricType,
    val symbol: String,
    val oneYear: PeriodSummary,
    val threeYear: PeriodSummary,
    val fiveYear: PeriodSummary,
    val lastUpdated: Long = System.currentTimeMillis()
)

/**
 * Sammanfattning för en specifik tidsperiod.
 */
data class PeriodSummary(
    val min: Double,
    val max: Double,
    val average: Double,
    val median: Double? = null // Optional för framtida utökning
) {
    /**
     * Kontrollerar om sammanfattningen är tom (ingen data).
     */
    fun isEmpty(): Boolean {
        return min == 0.0 && max == 0.0 && average == 0.0
    }
}

