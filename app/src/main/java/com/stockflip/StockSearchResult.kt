package com.stockflip

data class StockSearchResult(
    val symbol: String,
    val name: String,
    val isSwedish: Boolean = false
) {
    override fun toString(): String = "$name ($symbol)"
} 