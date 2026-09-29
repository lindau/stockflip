package com.stockflip.ui

/** Rena hjälpfunktioner för sammanfattningsraderna på kollapsade sektioner i aktiedetaljsidan. */
object DetailSectionSummaries {

    fun alerts(active: Int, triggered: Int): String? {
        if (active == 0 && triggered == 0) return null
        val parts = mutableListOf<String>()
        if (active > 0) parts += "$active ${if (active == 1) "aktiv" else "aktiva"}"
        if (triggered > 0) parts += "$triggered ${if (triggered == 1) "triggad" else "triggade"}"
        return parts.joinToString(" · ")
    }

    fun insider(buys: Int, sells: Int): String? {
        val parts = mutableListOf<String>()
        if (buys > 0) parts += "$buys köp"
        if (sells > 0) parts += "$sells sälj"
        return parts.joinToString(" · ").ifBlank { null }
    }

    fun podcast(enabled: Boolean, count: Int): String? = when {
        !enabled -> "Av"
        count == 0 -> null
        count == 1 -> "1 omnämnande"
        else -> "$count omnämnanden"
    }

    fun note(note: String?): String? =
        note?.lineSequence()?.firstOrNull { it.isNotBlank() }?.trim()
}
