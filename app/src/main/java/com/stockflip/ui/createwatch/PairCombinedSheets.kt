package com.stockflip.ui.createwatch

import com.stockflip.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.stockflip.AlertRule
import com.stockflip.StockSearchResult
import com.stockflip.WatchItem
import com.stockflip.WatchType
import com.stockflip.repository.SearchState
import com.stockflip.repository.StockRepository
import com.stockflip.ui.theme.Space
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Sökfält med förslagslista. [selected] visas som text; ändras texten nollställs valet. */
@Composable
internal fun StockPickerField(
    label: String,
    selected: StockSearchResult?,
    repository: StockRepository,
    onSelect: (StockSearchResult?) -> Unit,
    includeCrypto: Boolean = false,
) {
    var text by remember { mutableStateOf(selected?.name.orEmpty()) }
    var results by remember { mutableStateOf<List<StockSearchResult>>(emptyList()) }

    LaunchedEffect(text, selected) {
        results = emptyList()
        if (selected != null || text.trim().length < 2) return@LaunchedEffect
        delay(250)
        repository.searchStocks(text.trim(), includeCrypto).collect { state ->
            if (state is SearchState.Success) results = state.results.take(5)
        }
    }

    Column {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it; if (selected != null) onSelect(null) },
            label = { Text(label) },
            supportingText = selected?.let { { Text(it.symbol) } },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        results.forEach { r ->
            Column(
                Modifier.fillMaxWidth().heightIn(min = Space.touch)
                    .clickable { text = r.name; onSelect(r) }
                    .padding(vertical = Space.sm),
            ) {
                Text(r.name, style = MaterialTheme.typography.titleMedium)
                Text(r.symbol, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** Skapa/redigera en parbevakning (två aktier + prisskillnad). [onSave] returnerar felmeddelande eller `null`. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PairWatchSheet(
    repository: StockRepository,
    existing: WatchItem?,
    onDismiss: () -> Unit,
    onSave: suspend (WatchItem) -> String?,
) {
    val pair = existing?.watchType as? WatchType.PricePair
    var a by remember { mutableStateOf(existing?.ticker1?.let { StockSearchResult(it, existing.companyName1 ?: it) }) }
    var b by remember { mutableStateOf(existing?.ticker2?.let { StockSearchResult(it, existing.companyName2 ?: it) }) }
    var spread by remember { mutableStateOf(pair?.priceDifference?.takeIf { it != 0.0 }?.let { com.stockflip.ui.components.formatNumber(it) }.orEmpty()) }
    var notifyEqual by remember { mutableStateOf(pair?.notifyWhenEqual ?: false) }
    var error by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val preview = buildPair(a, b, spread, notifyEqual, existing)

    ModalBottomSheet(onDismissRequest = onDismiss, contentColor = MaterialTheme.colorScheme.onSurface, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.fillMaxWidth().navigationBarsPadding().verticalScroll(rememberScrollState())
                .padding(horizontal = Space.screenH).padding(bottom = Space.lg),
            verticalArrangement = Arrangement.spacedBy(Space.md),
        ) {
            Text(if (existing == null) "Ny parbevakning" else "Redigera parbevakning", style = MaterialTheme.typography.headlineSmall)
            StockPickerField("Första aktien", a, repository, { a = it; error = null })
            StockPickerField("Andra aktien", b, repository, { b = it; error = null })
            OutlinedTextField(
                value = spread,
                onValueChange = { spread = it; error = null },
                label = { Text(stringResource(R.string.createwatch_prisskillnad_tomt_nar_kurserna_mots)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )
            Row(Modifier.fillMaxWidth().clickable { notifyEqual = !notifyEqual }, verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = notifyEqual, onCheckedChange = { notifyEqual = it })
                Text(stringResource(R.string.createwatch_notis_aven_nar_kurserna_ar_lika), style = MaterialTheme.typography.bodyMedium)
            }
            val message = error ?: (preview as? PairResult.Ok)?.sentence
            if (message != null) Text(message, style = MaterialTheme.typography.bodyMedium,
                color = if (error != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
            Button(
                enabled = !saving,
                onClick = {
                    when (val r = buildPair(a, b, spread, notifyEqual, existing)) {
                        is PairResult.Invalid -> error = r.message
                        is PairResult.Ok -> scope.launch { saving = true; error = onSave(r.item); saving = false }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(if (saving) "Sparar…" else "Spara") }
        }
    }
}

/** Skapa/redigera en kombinerad bevakning: flera villkor på samma aktie kopplade med OCH/ELLER. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun CombinedWatchSheet(
    repository: StockRepository,
    existing: WatchItem?,
    onDismiss: () -> Unit,
    onSave: suspend (WatchItem) -> String?,
) {
    val decomposed = (existing?.watchType as? WatchType.Combined)?.let { decomposeCombined(it.expression) }
    var stock by remember {
        mutableStateOf(existing?.ticker?.let { StockSearchResult(it, existing.companyName ?: it) })
    }
    var conditions by remember { mutableStateOf(decomposed?.second ?: listOf(ConditionDraft())) }
    var error by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val preview = buildCombined(stock?.symbol.orEmpty(), conditions)

    fun update(i: Int, c: ConditionDraft) { conditions = conditions.toMutableList().also { it[i] = c }; error = null }

    ModalBottomSheet(onDismissRequest = onDismiss, contentColor = MaterialTheme.colorScheme.onSurface, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.fillMaxWidth().navigationBarsPadding().verticalScroll(rememberScrollState())
                .padding(horizontal = Space.screenH).padding(bottom = Space.lg),
            verticalArrangement = Arrangement.spacedBy(Space.md),
        ) {
            Text(if (existing == null) "Ny kombinerad bevakning" else "Redigera kombinerad bevakning", style = MaterialTheme.typography.headlineSmall)
            StockPickerField("Aktie", stock, repository, { stock = it; error = null }, includeCrypto = true)

            conditions.forEachIndexed { i, c ->
                Column(verticalArrangement = Arrangement.spacedBy(Space.sm)) {
                    if (i > 0) Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                        FilterChip(!c.or, { update(i, c.copy(or = false)) }, { Text(stringResource(R.string.createwatch_och)) })
                        FilterChip(c.or, { update(i, c.copy(or = true)) }, { Text(stringResource(R.string.createwatch_eller)) })
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                        ConditionKind.entries.forEach { k ->
                            FilterChip(c.kind == k, { update(i, c.copy(kind = k)) }, { Text(k.label) })
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(Space.sm), verticalAlignment = Alignment.CenterVertically) {
                        if (c.kind.hasDirection) {
                            FilterChip(c.above, { update(i, c.copy(above = true)) }, { Text(stringResource(R.string.createwatch_over)) })
                            FilterChip(!c.above, { update(i, c.copy(above = false)) }, { Text(stringResource(R.string.createwatch_under)) })
                        }
                        if (c.kind == ConditionKind.DRAWDOWN) {
                            FilterChip(c.reference == AlertRule.HighReference.FIFTY_TWO_WEEK_HIGH,
                                { update(i, c.copy(reference = AlertRule.HighReference.FIFTY_TWO_WEEK_HIGH)) }, { Text(stringResource(R.string.createwatch_52v)) })
                            FilterChip(c.reference == AlertRule.HighReference.ALL_TIME_HIGH,
                                { update(i, c.copy(reference = AlertRule.HighReference.ALL_TIME_HIGH)) }, { Text(stringResource(R.string.createwatch_all_time)) })
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = c.value,
                            onValueChange = { update(i, c.copy(value = it)) },
                            label = { Text(stringResource(R.string.createwatch_varde)) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.weight(1f),
                        )
                        if (conditions.size > 1) IconButton(onClick = { conditions = conditions.filterIndexed { j, _ -> j != i }; error = null }) {
                            Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.createwatch_ta_bort_villkor))
                        }
                    }
                }
            }
            OutlinedButton(onClick = { conditions = conditions + ConditionDraft(); error = null }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.createwatch_lagg_till_villkor))
            }

            val message = error ?: (preview as? CombinedResult.Ok)?.expression?.getDescription()
            if (message != null) Text(message, style = MaterialTheme.typography.bodyMedium,
                color = if (error != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
            Button(
                enabled = !saving,
                onClick = {
                    val s = stock
                    when (val r = buildCombined(s?.symbol.orEmpty(), conditions)) {
                        is CombinedResult.Invalid -> error = r.message
                        is CombinedResult.Ok -> scope.launch {
                            saving = true
                            val base = existing ?: WatchItem(watchType = WatchType.Combined(r.expression))
                            error = onSave(base.copy(watchType = WatchType.Combined(r.expression), ticker = s!!.symbol, companyName = s.name))
                            saving = false
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(if (saving) "Sparar…" else "Spara") }
        }
    }
}
