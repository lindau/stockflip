package com.stockflip.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.stockflip.ui.theme.StockFlipTheme

private val demoSeries = listOf(10.0, 11.0, 10.5, 12.0, 11.8, 13.0, 12.6, 14.0)

@Composable
private fun DesignSystemGallery() {
    var range by remember { mutableIntStateOf(2) }
    Column(Modifier.background(MaterialTheme.colorScheme.background)) {
        SectionLabel("Utlösta", count = 1)
        WatchRow(
            title = "Volvo B", subtitle = "Över 245 kr · utlöst 09:14",
            price = formatNumber(248.30), priceValue = 248.30,
            change = formatSignedPercent(1.2), changePositive = true,
            triggered = true, onClick = {}, sparkline = demoSeries, showDivider = false,
        )
        SectionLabel("Väntar", count = 2)
        WatchRow(
            title = "Investor B", subtitle = "Under 270 kr · 3,0 % kvar",
            price = formatNumber(278.40), priceValue = 278.40,
            change = formatSignedPercent(-0.4), changePositive = false,
            triggered = false, onClick = {}, sparkline = demoSeries.reversed(), showDivider = false,
        )
        WatchRow(
            title = "Apple", subtitle = "Dagsrörelse ±3 %",
            price = formatNumber(226.15), priceValue = 226.15,
            change = formatSignedPercent(0.0), changePositive = null,
            triggered = false, onClick = {},
        )
        SkeletonRow()
        SegmentedControl(listOf("1D", "1V", "1M", "1Å", "Max"), range, { range = it })
        SectionLabel("Nyckeltal")
        RangeBar("52 veckor", formatNumber(221.40), formatNumber(289.90), rangeFraction(248.30, 221.40, 289.90))
        KeyValueRow("P/E", "14,2")
        KeyValueRow("Direktavkastning", "3,9 %")
        PillStatus("Utlöst", highlighted = true)
        PillStatus("Väntar")
        EmptyState("Inga bevakningar än.", actionLabel = "Ny bevakning", onAction = {})
    }
}

@Preview(name = "Ljust", showBackground = true, widthDp = 380)
@Composable
private fun GalleryLight() = StockFlipTheme(darkTheme = false) { DesignSystemGallery() }

@Preview(name = "Mörkt", showBackground = true, widthDp = 380, backgroundColor = 0xFF131519)
@Composable
private fun GalleryDark() = StockFlipTheme(darkTheme = true) { DesignSystemGallery() }
