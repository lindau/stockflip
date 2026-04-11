package com.stockflip

/**
 * Intradagsdata för att visa prisrörelse under dagen.
 * Timestamps i epoch-sekunder, prices i lokal valuta.
 */
data class IntradayChartData(
    val timestamps: List<Long>,
    val prices: List<Double>,
    val previousClose: Double?,
    val lastTradeTimestamp: Long? = null,
    val emptyReason: String? = null
)

enum class ChartPeriod(val label: String, val range: String, val interval: String) {
    DAY("1D", "1d", "2m"),
    WEEK("1V", "5d", "15m"),
    MONTH("1M", "1mo", "1d"),
    THREE_MONTHS("3M", "3mo", "1d"),
    SIX_MONTHS("6M", "6mo", "1d"),
    YEAR("1Å", "1y", "1wk"),
    FIVE_YEARS("5Å", "5y", "1mo")
}
