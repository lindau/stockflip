package com.stockflip

import kotlin.math.floor

/** Text för utlösta notiser, i lugn ton utan utropstecken eller emojis (ren funktion, enhetstestad). */
internal object NotificationText {
    data class Text(val title: String, val message: String)

    private fun unit(currency: String?): String = when {
        currency.isNullOrBlank() || currency.equals("SEK", ignoreCase = true) -> "kr"
        else -> currency
    }

    private fun level(v: Double): String =
        if (v == floor(v)) v.toLong().toString() else CurrencyHelper.formatDecimal(v)

    /**
     * Målpris: titel "Volvo B under 245 kr", text "Kursen är 244,80 kr". [price] är senaste kurs (kan saknas).
     * [above] = bevakning över nivån.
     */
    fun priceTarget(name: String, above: Boolean, target: Double, price: Double?, currency: String?): Text {
        val u = unit(currency)
        val title = "$name ${if (above) "över" else "under"} ${level(target)} $u"
        val message = price?.let { "Kursen är ${CurrencyHelper.formatDecimal(it)} $u" }
            ?: "Målpriset ${level(target)} $u är nått"
        return Text(title, message)
    }
}
