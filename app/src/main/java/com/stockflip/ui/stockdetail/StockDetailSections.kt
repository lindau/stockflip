package com.stockflip.ui.stockdetail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import com.stockflip.InsiderTransactionEntity
import com.stockflip.PodcastObservationEntity
import com.stockflip.StockDetailData
import com.stockflip.ui.components.KeyValueRow
import com.stockflip.ui.components.SectionLabel
import com.stockflip.ui.components.cards.analystUpsidePercent
import com.stockflip.ui.components.cards.recommendationLabel
import com.stockflip.ui.components.formatNumber
import com.stockflip.ui.components.formatSignedPercent
import com.stockflip.ui.theme.NumericSecondaryStyle
import com.stockflip.ui.theme.Space
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Söksträng för mäklarsidor: ticker utan börssuffix/valutasuffix, bindestreck som mellanslag ("VOLV-B.ST" → "VOLV B"). */
internal fun brokerSearchQuery(symbol: String): String {
    var base = symbol.uppercase()
    for (suffix in listOf(".ST", ".STO", ".OL", ".OSE", ".L", ".DE", ".XETR", ".T")) {
        if (base.endsWith(suffix)) { base = base.removeSuffix(suffix); break }
    }
    for (suffix in listOf("-USD", "-EUR", "-GBP", "-JPY", "-SEK", "-NOK", "-DKK")) {
        if (base.endsWith(suffix)) { base = base.removeSuffix(suffix); break }
    }
    return base.replace("-", " ")
}

internal data class InsiderRowModel(val id: String, val title: String, val subtitle: String, val value: String)

/** Ren mappning av en insideraffär till radtexter, t.ex. "Köp · 12 000 aktier" och "5,2 mkr". */
internal fun InsiderTransactionEntity.toRowModel(): InsiderRowModel {
    val kind = if (transactionType.equals("SELL", ignoreCase = true)) "Sälj" else "Köp"
    val amount = shares?.let { "$kind · ${formatNumber(it, 0)} aktier" } ?: kind
    val who = listOfNotNull(relationship?.takeIf { it.isNotBlank() }, transactionDate).joinToString(" · ")
    val value = estimatedValue?.let {
        if (it >= 1_000_000) "${formatNumber(it / 1_000_000, 1)} mkr" else "${formatNumber(it / 1_000, 0)} tkr"
    } ?: "–"
    return InsiderRowModel(id, reportingOwner, "$amount\n$who", value)
}

@Composable
internal fun AnalystSection(data: StockDetailData) {
    val mean = data.targetMeanPrice ?: return
    SectionLabel("Analytikernas kursmål")
    val currency = data.financialCurrency ?: data.currency
    KeyValueRow("Snittkursmål", "${formatNumber(mean)} $currency", showDivider = false)
    val low = data.targetLowPrice
    val high = data.targetHighPrice
    if (low != null && high != null) KeyValueRow("Spann", "${formatNumber(low)} – ${formatNumber(high)} $currency")
    analystUpsidePercent(mean, data.lastPrice, data.financialCurrency, data.currency)?.let {
        KeyValueRow("Uppsida", formatSignedPercent(it))
    }
    recommendationLabel(data.recommendationKey)?.let { KeyValueRow("Rekommendation", it) }
    data.analystCount?.let { KeyValueRow("Antal analytiker", it.toString()) }
}

@Composable
internal fun InsiderSection(transactions: List<InsiderTransactionEntity>, highlightId: String?) {
    if (transactions.isEmpty()) return
    SectionLabel("Insideraffärer", count = transactions.size)
    transactions.take(5).forEach { tx ->
        val row = tx.toRowModel()
        Row(
            Modifier.fillMaxWidth()
                .then(if (row.id == highlightId) Modifier.background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)) else Modifier)
                .padding(horizontal = Space.screenH, vertical = Space.md),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(Modifier.weight(1f)) {
                Text(row.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(row.subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(row.value, style = NumericSecondaryStyle)
        }
    }
}

@Composable
internal fun PodcastSection(observations: List<PodcastObservationEntity>) {
    if (observations.isEmpty()) return
    SectionLabel("Poddomnämnanden", count = observations.size)
    val fmt = SimpleDateFormat("d MMM yyyy", Locale("sv", "SE"))
    observations.take(5).forEach { o ->
        Column(Modifier.fillMaxWidth().padding(horizontal = Space.screenH, vertical = Space.md)) {
            val head = listOfNotNull(o.podcast, o.publishedAtMillis?.let { fmt.format(Date(it)) }).joinToString(" · ")
            Text(head, style = MaterialTheme.typography.titleMedium)
            listOfNotNull(o.episodeTitle, o.recommendation ?: o.stance).joinToString(" · ").takeIf { it.isNotBlank() }?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            o.exactQuote?.takeIf { it.isNotBlank() }?.let {
                Text("”$it”".replaceFirst('”', '“'), style = MaterialTheme.typography.bodyMedium, maxLines = 3, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = Space.xs))
            }
        }
    }
}

@Composable
internal fun BrokerLinks(onOpenAvanza: () -> Unit, onOpenNordnet: () -> Unit) {
    SectionLabel("Handla")
    Row(Modifier.padding(horizontal = Space.md)) {
        TextButton(onClick = onOpenAvanza) { Text("Öppna i Avanza") }
        TextButton(onClick = onOpenNordnet) { Text("Öppna i Nordnet") }
    }
}
