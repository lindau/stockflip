package com.stockflip

data class StockSearchResult(
    val symbol: String,
    val name: String,
    val isSwedish: Boolean = false,
    val isCrypto: Boolean = false
) {
    companion object {
        /**
         * Identifierar om en symbol är kryptovaluta.
         * Krypto-symboler i Yahoo Finance: BTC-USD, ETH-USD, etc.
         */
        fun isCryptoSymbol(symbol: String): Boolean {
            val upperSymbol = symbol.uppercase()
            return upperSymbol.matches(Regex("[A-Z]{2,10}-USD")) ||
                   upperSymbol.matches(Regex("[A-Z]{2,10}-EUR")) ||
                   upperSymbol.contains("-USD") || 
                   upperSymbol.contains("-EUR") ||
                   upperSymbol.contains("-GBP")
        }
    }
    
    override fun toString(): String = "$name ($symbol)"
} 