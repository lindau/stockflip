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

/** En daglig SMA-datapunkt: [timestamp] är dagsstängningens epoch-sekunder, [value] medelvärdet. */
data class SmaPoint(
    val timestamp: Long,
    val value: Double
)

/**
 * En SMA-serie att rita ovanpå kursgrafen, t.ex. från en aktiv bevakning. [points] är den
 * historiska SMA-utvecklingen (en punkt per dagsstängning) — inte bara det senaste värdet —
 * så linjen kan följa aktiekursens rörelse i stället för att ritas som en rak vågrät linje.
 */
data class SmaChartLevel(
    val period: Int,
    val points: List<SmaPoint>
)

/** En daglig RSI-datapunkt (0–100). */
data class RsiPoint(
    val timestamp: Long,
    val value: Double
)

/** En daglig Bollinger-datapunkt: [middle] är SMA, [upper]/[lower] banden. */
data class BollingerPoint(
    val timestamp: Long,
    val upper: Double,
    val middle: Double,
    val lower: Double
)

data class PairChartSeries(
    val timestamps: List<Long>,
    val values: List<Double>
)

data class PairChartData(
    val stockALabel: String,
    val stockBLabel: String,
    val normalizedA: PairChartSeries,
    val normalizedB: PairChartSeries,
    val spread: IntradayChartData,
    val spreadTarget: Double?,
    val showEqualLine: Boolean,
    val currentSpread: Double?,
    val distanceToTarget: Double?,
    val leaderLabel: String?,
    val lastTradeTimestamp: Long?,
    val emptyReason: String? = null
)

enum class ChartPeriod(val label: String, val range: String, val interval: String) {
    DAY("1D", "1d", "2m"),
    WEEK("1V", "5d", "15m"),
    MONTH("1M", "1mo", "1d"),
    THREE_MONTHS("3M", "3mo", "1d"),
    SIX_MONTHS("6M", "6mo", "1d"),
    YEAR("1Å", "1y", "1wk"),
    FIVE_YEARS("5Å", "5y", "1mo"),
    MAX("Max", "max", "3mo")
}

/** Uppläsningstext för skärmläsare — "1D"/"1Å" läses annars upp bokstav för bokstav. */
fun ChartPeriod.accessibilityLabel(): String = when (this) {
    ChartPeriod.DAY -> "1 dag"
    ChartPeriod.WEEK -> "1 vecka"
    ChartPeriod.MONTH -> "1 månad"
    ChartPeriod.THREE_MONTHS -> "3 månader"
    ChartPeriod.SIX_MONTHS -> "6 månader"
    ChartPeriod.YEAR -> "1 år"
    ChartPeriod.FIVE_YEARS -> "5 år"
    ChartPeriod.MAX -> "hela historiken"
}

/** RSI/Bollinger beräknas på dagsdata och är bara meningsfulla när grafen själv är daglig eller grövre. */
fun ChartPeriod.supportsIndicators(): Boolean = this != ChartPeriod.DAY && this != ChartPeriod.WEEK
