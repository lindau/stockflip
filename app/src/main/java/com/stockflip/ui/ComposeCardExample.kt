package com.stockflip.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.stockflip.WatchItem
import com.stockflip.ui.components.cards.High52wCard
import com.stockflip.ui.components.cards.MetricAlertCard
import com.stockflip.ui.components.cards.PairCard
import com.stockflip.ui.theme.StockFlipTheme
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

/**
 * Exempel på hur man använder de nya Compose-kortkomponenterna.
 * 
 * För att integrera i befintliga XML-baserade vyer, använd ComposeView:
 * 
 * ```xml
 * <androidx.compose.ui.platform.ComposeView
 *     android:id="@+id/composeCard"
 *     android:layout_width="match_parent"
 *     android:layout_height="wrap_content" />
 * ```
 * 
 * I Kotlin-koden:
 * ```kotlin
 * binding.composeCard.setContent {
 *     StockFlipTheme {
 *         WatchItemCard(item = watchItem, priceFormat = priceFormat)
 *     }
 * }
 * ```
 */
@Composable
fun WatchItemCard(
    item: WatchItem,
    priceFormat: (Double) -> String = { value ->
        DecimalFormat("#,##0.00", DecimalFormatSymbols(Locale("sv", "SE"))).format(value)
    },
    modifier: Modifier = Modifier
) {
    when (item.watchType) {
        is com.stockflip.WatchType.PricePair -> {
            PairCard(
                item = item,
                priceFormat = priceFormat,
                modifier = modifier
            )
        }
        is com.stockflip.WatchType.KeyMetrics -> {
            MetricAlertCard(
                item = item,
                priceFormat = priceFormat,
                modifier = modifier
            )
        }
        is com.stockflip.WatchType.ATHBased -> {
            High52wCard(
                item = item,
                priceFormat = priceFormat,
                modifier = modifier
            )
        }
        else -> {
            // För andra typer, använd befintlig XML-layout eller skapa nya kort
        }
    }
}

/**
 * Exempel på en Compose-baserad lista med kort.
 * Detta kan användas i en ComposeView eller i en helt Compose-baserad skärm.
 */
@Composable
fun WatchItemList(
    items: List<WatchItem>,
    priceFormat: (Double) -> String = { value ->
        DecimalFormat("#,##0.00", DecimalFormatSymbols(Locale("sv", "SE"))).format(value)
    },
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        items.forEach { item ->
            WatchItemCard(
                item = item,
                priceFormat = priceFormat,
                modifier = Modifier.padding(vertical = 8.dp)
            )
        }
    }
}

