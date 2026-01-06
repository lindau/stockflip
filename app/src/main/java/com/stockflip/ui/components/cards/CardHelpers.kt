package com.stockflip.ui.components.cards

import com.stockflip.WatchItem

/**
 * Hjälpfunktioner för kortkomponenter.
 */
internal fun formatAlertStatus(watchItem: WatchItem): String {
    return when {
        !watchItem.isActive -> "Status: Inaktiverad"
        watchItem.isTriggered -> "Status: Triggad (${watchItem.lastTriggeredDate ?: "idag"})"
        else -> "Status: Aktiv"
    }
}

