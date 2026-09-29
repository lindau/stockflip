package com.stockflip.ui.createwatch

import com.stockflip.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.stockflip.StockDetailData
import com.stockflip.WatchType
import com.stockflip.ui.components.formatNumber
import com.stockflip.ui.theme.Space
import kotlinx.coroutines.launch

/**
 * Ett enda flöde för att skapa eller redigera en bevakning på en aktie: typchips, ett stort
 * inmatningsfält, en klartextmening och Spara. [onSave] returnerar ett felmeddelande eller `null`
 * när sparandet lyckades (då stängs sheeten av anroparen).
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun CreateWatchSheet(
    data: StockDetailData,
    initial: WatchDraft?,
    onDismiss: () -> Unit,
    fetchSma: suspend (Int) -> Double?,
    onSave: suspend (WatchType) -> String?,
) {
    val editing = initial != null
    var draft by remember { mutableStateOf(initial ?: WatchDraft()) }
    var showAdvanced by remember { mutableStateOf(initial?.kind?.advanced == true) }
    var error by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val preview = buildWatch(draft, data, sma = { null })
    // SMA-typer kan inte förhandsvisas utan hämtad SMA; visa en neutral mening tills dess.
    val sentence = when {
        preview is BuildResult.Ok -> preview.sentence
        draft.kind.advanced && draft.kind != WatchKind.INSIDER_BUY -> null
        else -> null
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = Space.screenH).padding(bottom = Space.lg),
            verticalArrangement = Arrangement.spacedBy(Space.md),
        ) {
            Text(if (editing) "Redigera bevakning" else "Ny bevakning", style = MaterialTheme.typography.headlineSmall)
            Text(data.companyName, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

            FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                WatchKind.entries.filter { !it.advanced }.forEach { k ->
                    FilterChip(selected = draft.kind == k, enabled = !editing, onClick = { draft = WatchDraft(kind = k); error = null }, label = { Text(k.label) })
                }
                if (!editing) FilterChip(selected = showAdvanced, onClick = { showAdvanced = !showAdvanced }, label = { Text(stringResource(R.string.createwatch_avancerat)) })
            }
            if (showAdvanced) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                    WatchKind.entries.filter { it.advanced }.forEach { k ->
                        FilterChip(selected = draft.kind == k, enabled = !editing, onClick = { draft = WatchDraft(kind = k); error = null }, label = { Text(k.label) })
                    }
                }
            }

            KindOptions(draft, data) { draft = it; error = null }

            if (draft.kind != WatchKind.INSIDER_BUY) {
                OutlinedTextField(
                    value = draft.value,
                    onValueChange = { draft = draft.copy(value = it); error = null },
                    label = { Text(valueLabel(draft)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = if (draft.kind.isPeriod()) KeyboardType.Number else KeyboardType.Decimal),
                    textStyle = MaterialTheme.typography.headlineSmall,
                    isError = error != null,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (draft.kind == WatchKind.SMA_CROSSOVER) {
                OutlinedTextField(
                    value = draft.value2,
                    onValueChange = { draft = draft.copy(value2 = it); error = null },
                    label = { Text(stringResource(R.string.createwatch_langt_sma_dagar)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    isError = error != null,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            val message = error ?: sentence
            if (message != null) {
                Text(
                    message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (error != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Button(
                enabled = !saving,
                onClick = {
                    scope.launch {
                        saving = true
                        val sma = smaPeriodsNeeded(draft).associateWith { fetchSma(it) }
                        when (val r = buildWatch(draft, data) { sma[it] }) {
                            is BuildResult.Invalid -> error = r.message
                            is BuildResult.Ok -> error = onSave(r.type)
                        }
                        saving = false
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(if (saving) "Sparar…" else "Spara") }
        }
    }
}

private fun WatchKind.isPeriod() = this == WatchKind.PRICE_VS_SMA || this == WatchKind.SMA_CROSSOVER

private fun valueLabel(d: WatchDraft): String = when (d.kind) {
    WatchKind.PRICE -> "Målpris"
    WatchKind.DAILY_MOVE -> "Gräns (%)"
    WatchKind.DRAWDOWN -> if (d.dropType == WatchType.DropType.PERCENTAGE) "Fall (%)" else "Fall (belopp)"
    WatchKind.METRIC -> "Målvärde"
    WatchKind.PRICE_VS_SMA -> "SMA (dagar)"
    WatchKind.SMA_CROSSOVER -> "Kort SMA (dagar)"
    WatchKind.INSIDER_BUY -> ""
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun KindOptions(draft: WatchDraft, data: StockDetailData, onChange: (WatchDraft) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
        when (draft.kind) {
            WatchKind.PRICE -> data.lastPrice?.takeIf { it > 0 }?.let { p ->
                listOf(0.90, 0.95, 1.05, 1.10).forEach { m ->
                    val target = p * m
                    val pct = Math.round((m - 1) * 100).toInt()
                    FilterChip(
                        selected = false,
                        onClick = { onChange(draft.copy(value = formatNumber(target))) },
                        label = { Text((if (pct > 0) "+$pct" else "−${-pct}") + " %") },
                    )
                }
            }
            WatchKind.DAILY_MOVE -> listOf(
                "Upp" to WatchType.DailyMoveDirection.UP,
                "Ner" to WatchType.DailyMoveDirection.DOWN,
                "Båda" to WatchType.DailyMoveDirection.BOTH,
            ).forEach { (l, d) -> FilterChip(selected = draft.dailyDirection == d, onClick = { onChange(draft.copy(dailyDirection = d)) }, label = { Text(l) }) }
            WatchKind.DRAWDOWN -> {
                FilterChip(draft.dropType == WatchType.DropType.PERCENTAGE, { onChange(draft.copy(dropType = WatchType.DropType.PERCENTAGE)) }, { Text(stringResource(R.string.createwatch_procent)) })
                FilterChip(draft.dropType == WatchType.DropType.ABSOLUTE, { onChange(draft.copy(dropType = WatchType.DropType.ABSOLUTE)) }, { Text(stringResource(R.string.createwatch_belopp)) })
                FilterChip(draft.reference == WatchType.HighReference.FIFTY_TWO_WEEK_HIGH, { onChange(draft.copy(reference = WatchType.HighReference.FIFTY_TWO_WEEK_HIGH)) }, { Text(stringResource(R.string.createwatch_52v_hogsta)) })
                FilterChip(draft.reference == WatchType.HighReference.ALL_TIME_HIGH, { onChange(draft.copy(reference = WatchType.HighReference.ALL_TIME_HIGH)) }, { Text(stringResource(R.string.createwatch_all_time_high)) })
            }
            WatchKind.METRIC -> listOf(
                "P/E" to WatchType.MetricType.PE_RATIO,
                "P/S" to WatchType.MetricType.PS_RATIO,
                "Direktavkastning" to WatchType.MetricType.DIVIDEND_YIELD,
                "Vinst/aktie" to WatchType.MetricType.EARNINGS_PER_SHARE,
            ).forEach { (l, m) -> FilterChip(selected = draft.metric == m, onClick = { onChange(draft.copy(metric = m)) }, label = { Text(l) }) }
            else -> Unit
        }
    }
}
