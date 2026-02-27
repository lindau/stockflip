package com.stockflip

/**
 * Helper för att hantera valuta-formatering och symboler.
 */
object CurrencyHelper {
    /**
     * Formaterar ett pris med valuta-symbol.
     * € och $ ska stå före beloppet, SEK efter.
     * 
     * @param price Priset att formatera
     * @param currency Valuta-kod (t.ex. "SEK", "USD", "EUR")
     * @return Formaterat pris med valuta-symbol
     */
    fun formatPrice(price: Double, currency: String?): String {
        val currencyCode = currency ?: "SEK"
        val currencySymbol = getCurrencySymbol(currencyCode)
        val priceFormat = java.text.DecimalFormat("#,##0.00", java.text.DecimalFormatSymbols(java.util.Locale("sv", "SE")))
        val formattedPrice = priceFormat.format(price)
        
        // För USD, EUR, GBP, JPY, CNY, CHF, CAD, AUD: placera symbol före
        // För SEK, NOK, DKK och krypto: placera symbol efter
        return when (currencyCode.uppercase()) {
            "USD", "EUR", "GBP", "JPY", "CNY", "CHF", "CAD", "AUD" -> "$currencySymbol$formattedPrice"
            else -> "$formattedPrice $currencySymbol"
        }
    }

    /**
     * Hämtar valuta-symbol för en valuta-kod.
     * 
     * @param currency Valuta-kod (t.ex. "SEK", "USD", "EUR")
     * @return Valuta-symbol (t.ex. "SEK", "$", "€")
     */
    fun getCurrencySymbol(currency: String): String {
        return when (currency.uppercase()) {
            "USD" -> "$"
            "EUR" -> "€"
            "GBP" -> "£"
            "JPY" -> "¥"
            "CNY" -> "¥"
            "CHF" -> "CHF"
            "CAD" -> "C$"
            "AUD" -> "A$"
            "SEK" -> "kr"
            "NOK" -> "kr"
            "DKK" -> "kr"
            "BTC", "ETH", "BNB", "ADA", "SOL", "XRP", "DOT", "DOGE", "USDT", "USDC", "DAI" -> currency.uppercase() // Krypto-symboler
            else -> currency.uppercase() // Default: använd valuta-koden som symbol
        }
    }

    fun isCryptoCurrency(currency: String): Boolean {
        val cryptoList = listOf("BTC", "ETH", "BNB", "ADA", "SOL", "XRP", "DOT", "DOGE", "USDT", "USDC", "DAI")
        return cryptoList.contains(currency.uppercase())
    }

    /**
     * Hämtar valuta från börs-kod.
     * 
     * @param exchange Börs-kod (t.ex. "STO", "NASDAQ", "NYSE")
     * @return Valuta-kod, eller "SEK" som default
     */
    fun getCurrencyFromExchange(exchange: String?): String {
        return when (exchange?.uppercase()) {
            "STO" -> "SEK"
            "NASDAQ", "NYSE", "AMEX" -> "USD"
            "LSE" -> "GBP"
            "XETR", "XFRA" -> "EUR"
            "TSE" -> "JPY"
            "OSE" -> "NOK" // Oslobörsen
            else -> "SEK" // Default
        }
    }

    /**
     * Hämtar valuta från symbol.
     * För krypto-symboler (t.ex. "BTC-USD") extraheras valutan från suffixet.
     * 
     * @param symbol Aktie- eller krypto-symbol
     * @return Valuta-kod, eller "SEK" som default
     */
    fun getCurrencyFromSymbol(symbol: String?): String {
        if (symbol == null) return "SEK"
        
        val upperSymbol = symbol.uppercase()
        
        // För krypto-symboler: BTC-USD -> USD, ETH-EUR -> EUR
        if (upperSymbol.contains("-")) {
            val parts = upperSymbol.split("-")
            if (parts.size >= 2) {
                val currency = parts.last()
                // Kontrollera om det är en känd valuta
                if (currency in listOf("USD", "EUR", "GBP", "JPY", "SEK", "NOK", "DKK")) {
                    return currency
                }
            }
        }
        
        // För svenska aktier på Stockholmsbörsen
        if (upperSymbol.endsWith(".ST") || upperSymbol.endsWith(".STO")) {
            return "SEK"
        }
        
        // För norska aktier på Oslobörsen
        if (upperSymbol.endsWith(".OL") || upperSymbol.endsWith(".OSE")) {
            return "NOK"
        }
        
        // För brittiska aktier
        if (upperSymbol.endsWith(".L")) {
            return "GBP"
        }
        
        // För tyska aktier
        if (upperSymbol.endsWith(".DE") || upperSymbol.endsWith(".XETR")) {
            return "EUR"
        }
        
        // För japanska aktier
        if (upperSymbol.endsWith(".T")) {
            return "JPY"
        }
        
        // För amerikanska aktier: om symbolen är en ren ticker (inga punkter, inga bindestreck)
        // och inte är en känd svensk ticker, anta USD
        // Kända svenska tickers är vanligtvis 3-4 bokstäver och kan innehålla siffror
        // Amerikanska tickers är vanligtvis 1-5 bokstäver, ibland med siffror
        if (!upperSymbol.contains(".") && !upperSymbol.contains("-")) {
            // Om det är en ren ticker utan suffix, anta USD (för amerikanska aktier)
            // Detta är en heuristik - bättre vore att använda exchange-information
            return "USD"
        }
        
        // Default: SEK
        return "SEK"
    }
}
