package com.stockflip.ui

import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.stockflip.StockDetailData
import com.stockflip.WatchType
import com.stockflip.ui.components.showUndo
import com.stockflip.ui.createwatch.CreateWatchSheet
import com.stockflip.ui.theme.StockFlipTheme
import kotlinx.coroutines.launch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Skapa-bevakning-flödet och ångra-snackbaren (kräver enhet/emulator). */
class CreateFlowTests {
    @get:Rule
    val rule = createComposeRule()

    private val data = StockDetailData(
        symbol = "VOLV-B.ST", companyName = "Volvo B", lastPrice = 250.0, previousClose = 249.0,
        week52High = 300.0, week52Low = 200.0, dailyChangePercent = 0.4, drawdownPercent = null, peRatio = 10.0,
    )

    @Test
    fun priceTarget_showsSentenceAndSavesInferredDirection() {
        var saved: WatchType? = null
        rule.setContent {
            StockFlipTheme {
                CreateWatchSheet(data, initial = null, onDismiss = {}, fetchSma = { null }, onSave = { saved = it; null })
            }
        }
        rule.onNode(hasSetTextAction()).performTextInput("245")
        rule.onNodeWithText("Notis när Volvo B går under 245,00 kr.").assertIsDisplayed()
        rule.onNodeWithText("Spara").performClick()
        rule.waitForIdle()
        assertEquals(WatchType.PriceTarget(245.0, WatchType.PriceDirection.BELOW), saved)
    }

    @Test
    fun emptyValue_showsValidationErrorAndDoesNotSave() {
        var saved: WatchType? = null
        rule.setContent {
            StockFlipTheme {
                CreateWatchSheet(data, initial = null, onDismiss = {}, fetchSma = { null }, onSave = { saved = it; null })
            }
        }
        rule.onNodeWithText("Spara").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("Ange ett målpris").assertIsDisplayed()
        assertNull(saved)
    }

    @Test
    fun saveError_fromCallback_isShown() {
        rule.setContent {
            StockFlipTheme {
                CreateWatchSheet(data, initial = null, onDismiss = {}, fetchSma = { null },
                    onSave = { "En bevakning med dessa inställningar finns redan" })
            }
        }
        rule.onNode(hasSetTextAction()).performTextInput("245")
        rule.onNodeWithText("Spara").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("En bevakning med dessa inställningar finns redan").assertIsDisplayed()
    }

    @Test
    fun undoSnackbar_returnsTrueWhenÅngraTapped() {
        val host = SnackbarHostState()
        var undone: Boolean? = null
        rule.setContent {
            StockFlipTheme {
                Scaffold(snackbarHost = { SnackbarHost(host) }) { padding ->
                    val scope = rememberCoroutineScope()
                    Button(onClick = { scope.launch { undone = host.showUndo("Tog bort bevakning för Volvo B") } }) { Text("Radera") }
                }
            }
        }
        rule.onNodeWithText("Radera").performClick()
        rule.onNodeWithText("Tog bort bevakning för Volvo B").assertIsDisplayed()
        rule.onNodeWithText("Ångra").performClick()
        rule.waitForIdle()
        assertTrue(undone == true)
    }
}
