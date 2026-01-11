package com.stockflip

/**
 * Helper för att hantera valuta-formatering och symboler.
 */
object CurrencyHelper {
    /**
     * Formaterar ett pris med valuta-symbol.
     * 
     * @param price Priset att formatera
     * @param currency Valuta-kod (t.ex. "SEK", "USD", "EUR")
     * @return Formaterat pris med valuta-symbol
     */
    fun formatPrice(price: Double, currency: String?): String {
        val currencySymbol = getCurrencySymbol(currency ?: "SEK")
        val priceFormat = java.text.DecimalFormat("#,##0.00", java.text.DecimalFormatSymbols(java.util.Locale("sv", "SE")))
        return "${priceFormat.format(price)} $currencySymbol"
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
            "NOK" -> "kr"
            "DKK" -> "kr"
            "BTC", "ETH", "BNB", "ADA", "SOL", "XRP", "DOT", "DOGE", "USDT", "USDC", "DAI" -> currency.uppercase() // Krypto-symboler
            else -> currency.uppercase() // Default: använd valuta-koden som symbol (t.ex. SEK)
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
            else -> "SEK" // Default
        }
    }
}
