package com.stockflip

data class StockSearchResult(
    val symbol: String,
    val name: String,
    val isSwedish: Boolean = false,
    val isCrypto: Boolean = false,
    val isIndex: Boolean = false
) {
    companion object {
        /**
         * Identifierar om en symbol är ett index.
         * Index-symboler i Yahoo Finance har ^-prefix: ^OMX, ^OMXS30, ^GSPC, etc.
         */
        fun isIndexSymbol(symbol: String): Boolean = symbol.startsWith("^")

        /**
         * Börs-kod vars öppettider ett index följer, eller null om indexet är okänt.
         */
        fun indexExchange(symbol: String): String? {
            if (!isIndexSymbol(symbol)) return null
            val upperSymbol = symbol.uppercase()
            return when {
                upperSymbol.startsWith("^OMX") -> "STO"
                upperSymbol in setOf("^GSPC", "^IXIC", "^NDX", "^DJI", "^RUT", "^VIX") -> "NASDAQ"
                upperSymbol == "^FTSE" -> "LSE"
                upperSymbol == "^GDAXI" -> "XETR"
                upperSymbol == "^N225" -> "TSE"
                upperSymbol == "^OSEAX" || upperSymbol == "^OBX" -> "OSE"
                else -> null
            }
        }

        /**
         * Sant för instrument som saknar bolagsdata (nyckeltal, rapporter, insider, logga).
         */
        fun isNonEquitySymbol(symbol: String): Boolean = isCryptoSymbol(symbol) || isIndexSymbol(symbol)

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