package com.stockflip

/**
 * Intradagsdata för att visa prisrörelse under dagen.
 * Timestamps i epoch-sekunder, prices i lokal valuta.
 */
data class IntradayChartData(
    val timestamps: List<Long>,
    val prices: List<Double>,
    val previousClose: Double?
)
