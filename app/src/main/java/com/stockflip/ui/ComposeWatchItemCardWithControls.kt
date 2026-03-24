package com.stockflip.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.stockflip.CurrencyHelper
import com.stockflip.LiveWatchData
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
    onReactivate: (WatchItem) -> Unit,
    onDelete: (WatchItem) -> Unit,
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
        modifier = modifier.fillMaxWidth()
    )
}


private fun formatAlertStatus(watchItem: WatchItem): String {
    return when {
        !watchItem.isActive -> "Status: Inaktiverad"
        watchItem.isTriggered -> "Status: Triggad (${watchItem.lastTriggeredDate ?: "idag"})"
        else -> "Status: Aktiv"
    }
}

