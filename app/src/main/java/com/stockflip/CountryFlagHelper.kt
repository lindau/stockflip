package com.stockflip

/**
 * Helper för att mappa börser till länder och flaggor.
 */
object CountryFlagHelper {
    /**
     * Hämtar landskod för en börs.
     * 
     * @param exchange Börs-kod (t.ex. "STO", "NASDAQ", "NYSE", "NMS", "NYQ")
     * @return ISO 3166-1 alpha-2 landskod (t.ex. "SE", "US", "GB")
     */
    fun getCountryCodeFromExchange(exchange: String?): String? {
        val exchangeUpper = exchange?.uppercase() ?: return null
        
        // Om exchange är krypto-relaterad, returnera null
        if (exchangeUpper.contains("CRYPTO", ignoreCase = true)) {
            return null
        }
        
        return when {
            // Sverige
            exchangeUpper == "STO" || exchangeUpper.contains("STOCKHOLM", ignoreCase = true) -> "SE"
            // USA - många variationer
            exchangeUpper.contains("NASDAQ", ignoreCase = true) || 
            exchangeUpper == "NMS" || 
            exchangeUpper == "NCM" || 
            exchangeUpper == "NGM" -> "US"
            exchangeUpper.contains("NYSE", ignoreCase = true) || 
            exchangeUpper == "NYQ" || 
            exchangeUpper == "NYM" -> "US"
            exchangeUpper == "AMEX" || 
            exchangeUpper.contains("AMERICAN", ignoreCase = true) -> "US"
            exchangeUpper.contains("OTC", ignoreCase = true) -> "US"
            // Storbritannien
            exchangeUpper == "LSE" || exchangeUpper.contains("LONDON", ignoreCase = true) -> "GB"
            // Tyskland
            exchangeUpper == "XETR" || exchangeUpper == "XFRA" || exchangeUpper.contains("XETRA", ignoreCase = true) -> "DE"
            // Japan
            exchangeUpper == "TSE" || exchangeUpper.contains("TOKYO", ignoreCase = true) -> "JP"
            // Kina
            exchangeUpper == "SSE" || exchangeUpper == "SZSE" || exchangeUpper.contains("SHANGHAI", ignoreCase = true) -> "CN"
            // Australien
            exchangeUpper == "ASX" || exchangeUpper.contains("AUSTRALIA", ignoreCase = true) -> "AU"
            // Kanada
            exchangeUpper == "TSX" || exchangeUpper.contains("TORONTO", ignoreCase = true) -> "CA"
            // Norge
            exchangeUpper == "OSE" || exchangeUpper.contains("OSLO", ignoreCase = true) -> "NO"
            // Danmark
            exchangeUpper == "CSE" || exchangeUpper.contains("COPENHAGEN", ignoreCase = true) -> "DK"
            // Finland
            exchangeUpper == "HEL" || exchangeUpper.contains("HELSINKI", ignoreCase = true) -> "FI"
            // Schweiz
            exchangeUpper == "SWX" || exchangeUpper.contains("SWISS", ignoreCase = true) -> "CH"
            // Spanien
            exchangeUpper == "BME" || exchangeUpper.contains("MADRID", ignoreCase = true) -> "ES"
            // Italien
            exchangeUpper == "BIT" || exchangeUpper.contains("MILAN", ignoreCase = true) -> "IT"
            // Frankrike
            exchangeUpper == "EPA" || exchangeUpper.contains("PARIS", ignoreCase = true) -> "FR"
            // Nederländerna
            exchangeUpper == "AMS" || exchangeUpper.contains("AMSTERDAM", ignoreCase = true) -> "NL"
            else -> null // Returnera null istället för default
        }
    }

    /**
     * Hämtar landskod från valuta.
     * 
     * @param currency Valuta-kod (t.ex. "USD", "EUR", "SEK")
     * @return ISO 3166-1 alpha-2 landskod, eller null om det inte kan bestämmas
     */
    fun getCountryCodeFromCurrency(currency: String?): String? {
        return when (currency?.uppercase()) {
            "USD" -> "US"
            "EUR" -> null // Kan vara flera länder
            "GBP" -> "GB"
            "JPY" -> "JP"
            "CNY" -> "CN"
            "AUD" -> "AU"
            "CAD" -> "CA"
            "NOK" -> "NO"
            "DKK" -> "DK"
            "SEK" -> "SE"
            "CHF" -> "CH"
            else -> null
        }
    }

    /**
     * Hämtar flagga-emoji för en landskod.
     * 
     * @param countryCode ISO 3166-1 alpha-2 landskod (t.ex. "SE", "US")
     * @return Flagga-emoji som sträng
     */
    fun getFlagEmoji(countryCode: String): String {
        val codePoints = countryCode.uppercase().map { char ->
            0x1F1E6 + (char - 'A')
        }
        return String(codePoints.toIntArray(), 0, codePoints.size)
    }

    /**
     * Hämtar flagga-emoji för en börs.
     * 
     * @param exchange Börs-kod (t.ex. "STO", "NASDAQ", "NMS", "NYQ")
     * @param currency Valuta-kod (t.ex. "USD", "SEK") - används som fallback om börs inte kan mappas
     * @return Flagga-emoji som sträng
     */
    fun getFlagForExchange(exchange: String?, currency: String? = null): String? {
        // Om exchange är null eller krypto, returnera null
        if (exchange == null || exchange.contains("CRYPTO", ignoreCase = true)) {
            return null
        }
        
        // Försök först mappa från börs
        val exchangeCountryCode = getCountryCodeFromExchange(exchange)
        
        // Om börs mappade till null, försök använda valuta som fallback
        val finalCountryCode = exchangeCountryCode ?: getCountryCodeFromCurrency(currency)
        
        return finalCountryCode?.let { getFlagEmoji(it) }
    }
}
