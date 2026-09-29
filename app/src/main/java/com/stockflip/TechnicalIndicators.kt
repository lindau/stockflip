package com.stockflip

import kotlin.math.sqrt

/**
 * Rena beräkningar av tekniska indikatorer på en kronologisk serie stängningskurser.
 * Ingen I/O — allt returneras index-för-index mot indata (null där värde saknas).
 */
object TechnicalIndicators {
    const val DEFAULT_RSI_PERIOD = 14
    const val DEFAULT_BOLLINGER_PERIOD = 20
    const val DEFAULT_BOLLINGER_STD_DEVS = 2.0

    /** Antal extra bars före det synliga fönstret så Wilders utjämning hinner konvergera. */
    fun rsiWarmupBars(period: Int): Int = maxOf(period * 4, 60)

    /**
     * RSI med Wilders utjämning. Första värdet hamnar på index [period] (det behövs [period]
     * kursförändringar), tidigare index är null. Om snittförlusten är 0 blir RSI 100.
     */
    fun rsi(closes: List<Double>, period: Int = DEFAULT_RSI_PERIOD): List<Double?> {
        require(period >= 1) { "period måste vara minst 1" }
        val result = MutableList<Double?>(closes.size) { null }
        if (closes.size <= period) return result

        var avgGain = 0.0
        var avgLoss = 0.0
        for (i in 1..period) {
            val change = closes[i] - closes[i - 1]
            if (change > 0) avgGain += change else avgLoss -= change
        }
        avgGain /= period
        avgLoss /= period
        result[period] = rsiValue(avgGain, avgLoss)

        for (i in period + 1 until closes.size) {
            val change = closes[i] - closes[i - 1]
            val gain = if (change > 0) change else 0.0
            val loss = if (change < 0) -change else 0.0
            avgGain = (avgGain * (period - 1) + gain) / period
            avgLoss = (avgLoss * (period - 1) + loss) / period
            result[i] = rsiValue(avgGain, avgLoss)
        }
        return result
    }

    private fun rsiValue(avgGain: Double, avgLoss: Double): Double = when {
        avgLoss == 0.0 && avgGain == 0.0 -> 50.0
        avgLoss == 0.0 -> 100.0
        else -> 100.0 - 100.0 / (1.0 + avgGain / avgLoss)
    }

    /**
     * Bollinger Bands: SMA(period) ± [stdDevs] populationsstandardavvikelser.
     * Första värdet hamnar på index period − 1, tidigare index är null.
     */
    fun bollinger(
        closes: List<Double>,
        period: Int = DEFAULT_BOLLINGER_PERIOD,
        stdDevs: Double = DEFAULT_BOLLINGER_STD_DEVS
    ): List<BollingerValues?> {
        require(period >= 1) { "period måste vara minst 1" }
        val result = MutableList<BollingerValues?>(closes.size) { null }
        for (i in period - 1 until closes.size) {
            val window = closes.subList(i - period + 1, i + 1)
            val mean = window.average()
            val variance = window.sumOf { (it - mean) * (it - mean) } / period
            val deviation = sqrt(variance) * stdDevs
            result[i] = BollingerValues(upper = mean + deviation, middle = mean, lower = mean - deviation)
        }
        return result
    }
}

data class BollingerValues(val upper: Double, val middle: Double, val lower: Double)
