package com.stockflip.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.stockflip.CurrencyHelper
import com.stockflip.LiveWatchData
import com.stockflip.TriggerSeenTracker
import com.stockflip.WatchItem

/**
 * Compose-komponent som visar ett WatchItem-kort med kontroller (toggle, delete, reactivate).
 * Används i StockDetailFragment för att visa bevakningar med full information.
 */
@Composable
fun ComposeWatchItemCardWithControls(
    item: WatchItem,
    live: LiveWatchData = LiveWatchData(),
    priceFormat: (Double) -> String = { value -> CurrencyHelper.formatDecimal(value) },
    onToggleActive: (WatchItem) -> Unit,
    onEdit: (WatchItem) -> Unit,
    containerColor: Color = MaterialTheme.colorScheme.surface,
    triggerHistory: List<Long> = emptyList(),
    modifier: Modifier = Modifier
) {
    // Visa kortet med full information (inklusive status och kontroller)
    ComposeWatchItemCard(
        item = item,
        live = live,
        priceFormat = priceFormat,
        onItemClick = { onEdit(item) }, // Klicka på kortet för att redigera
        showStatus = true, // Visa status i kortet
        showControls = true, // Visa kontroller i kortet
        onToggleActive = { onToggleActive(item) },
        containerColor = containerColor,
        triggerHistory = triggerHistory,
        isNew = TriggerSeenTracker.isNew(item),
        modifier = modifier.fillMaxWidth()
    )
}
