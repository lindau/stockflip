package com.stockflip.ui

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasStateDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeRight
import com.stockflip.ui.stockdetail.AlertAction
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.stockflip.StockSearchResult
import com.stockflip.ui.components.SegmentedControl
import com.stockflip.ui.components.WatchRow
import com.stockflip.ui.market.MarketContent
import com.stockflip.ui.market.MarketScreen
import com.stockflip.ui.settings.SettingsScreen
import com.stockflip.ui.settings.ThemeMode
import com.stockflip.ui.theme.StockFlipTheme
import com.stockflip.ui.watchlist.WatchListSections
import com.stockflip.ui.watchlist.WatchRowModel
import com.stockflip.ui.watchlist.WatchlistScreen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Compose UI-tester (kräver enhet/emulator: `./gradlew connectedDebugAndroidTest`). */
class ComposeUiTests {
    @get:Rule
    val rule = createComposeRule()

    private fun row(id: Int, title: String, triggered: Boolean = false) = WatchRowModel(
        id = id, title = title, subtitle = "Under 245 kr", price = "244,80", priceValue = 244.8,
        change = "+1,2 %", changePositive = true, triggered = triggered, paused = false, isPair = false, symbol = "VOLV-B.ST",
    )

    @Test
    fun watchRow_showsTextAndHandlesClick() {
        var clicks = 0
        rule.setContent {
            StockFlipTheme {
                WatchRow("Volvo B", "Under 245 kr", "244,80", 244.8, "+1,2 %", true, triggered = false, onClick = { clicks++ })
            }
        }
        rule.onNodeWithText("Volvo B").assertIsDisplayed()
        rule.onNodeWithText("+1,2 %").assertIsDisplayed()
        rule.onNodeWithText("Volvo B").performClick()
        assertEquals(1, clicks)
    }

    @Test
    fun watchRow_triggeredExposesStateDescription() {
        rule.setContent {
            StockFlipTheme { WatchRow("Volvo B", "x", "1", 1.0, null, null, triggered = true, onClick = {}) }
        }
        rule.onNode(hasStateDescription("Utlöst")).assertIsDisplayed()
    }

    @Test
    fun segmentedControl_selectsTappedOption() {
        var selected by mutableStateOf(0)
        rule.setContent {
            StockFlipTheme { SegmentedControl(listOf("1D", "1V", "1M"), selected, { selected = it }) }
        }
        rule.onNodeWithText("1V").performClick()
        rule.onNodeWithText("1V").assertIsSelected()
        assertEquals(1, selected)
    }

    @Test
    fun watchlist_deleteViaAccessibilityAction() {
        var deleted: WatchRowModel? = null
        val volvo = row(1, "Volvo B")
        rule.setContent {
            StockFlipTheme {
                WatchlistScreen(
                    sections = WatchListSections(triggered = emptyList(), waiting = listOf(volvo)),
                    isLoading = false, isRefreshing = false, loadError = null, query = "",
                    onQueryChange = {}, onRefresh = {}, onRowClick = {}, onDelete = { deleted = it },
                    onAddWatch = {}, onAddPair = {}, onAddCombined = {},
                )
            }
        }
        rule.onNodeWithText("Väntar", substring = true, ignoreCase = true).assertIsDisplayed()
        rule.onNodeWithText("Volvo B").assertIsDisplayed()
        // Svepgesten har en motsvarande tillgänglighetsåtgärd.
        val node = rule.onNode(SemanticsMatcher.keyIsDefined(SemanticsActions.CustomActions)).fetchSemanticsNode()
        val action = node.config[SemanticsActions.CustomActions].first { it.label == "Ta bort" }
        rule.runOnUiThread { action.action() }
        rule.waitForIdle()
        assertEquals(1, deleted?.id)
    }

    private fun swipeRightAction(row: WatchRowModel): AlertAction? {
        var got: AlertAction? = null
        rule.setContent {
            StockFlipTheme {
                WatchlistScreen(
                    sections = WatchListSections(triggered = if (row.triggered) listOf(row) else emptyList(), waiting = if (row.triggered) emptyList() else listOf(row)),
                    isLoading = false, isRefreshing = false, loadError = null, query = "",
                    onQueryChange = {}, onRefresh = {}, onRowClick = {}, onDelete = {},
                    onAddWatch = {}, onAddPair = {}, onAddCombined = {},
                    onRowAction = { _, action -> got = action },
                )
            }
        }
        rule.onNodeWithText(row.title).performTouchInput { swipeRight() }
        rule.waitForIdle()
        return got
    }

    @Test
    fun watchlist_swipeRight_pausesActiveRow() = assertEquals(AlertAction.Pause, swipeRightAction(row(1, "Volvo B")))

    @Test
    fun watchlist_swipeRight_resumesPausedRow() =
        assertEquals(AlertAction.Resume, swipeRightAction(row(1, "Volvo B").copy(paused = true)))

    @Test
    fun watchlist_swipeRight_reactivatesTriggeredRow() =
        assertEquals(AlertAction.Reactivate, swipeRightAction(row(1, "Volvo B", triggered = true).copy(paused = true)))

    @Test
    fun watchlist_emptyStateOffersAdd() {
        var added = false
        rule.setContent {
            StockFlipTheme {
                WatchlistScreen(
                    sections = WatchListSections(emptyList(), emptyList()),
                    isLoading = false, isRefreshing = false, loadError = null, query = "",
                    onQueryChange = {}, onRefresh = {}, onRowClick = {}, onDelete = {},
                    onAddWatch = { added = true }, onAddPair = {}, onAddCombined = {},
                )
            }
        }
        rule.onNodeWithText("Ny bevakning").performClick()
        assertTrue(added)
    }

    @Test
    fun market_showsResultsAndReportsClick() {
        var clicked: StockSearchResult? = null
        val volvo = StockSearchResult("VOLV-B.ST", "Volvo B", isSwedish = true)
        rule.setContent {
            StockFlipTheme {
                MarketScreen("vo", MarketContent.Results(listOf(volvo)), {}, { clicked = it }, {}, {})
            }
        }
        rule.onNodeWithText("Volvo B").performClick()
        assertEquals("VOLV-B.ST", clicked?.symbol)
    }

    @Test
    fun settings_themeChangeAndNavigationCallbacks() {
        var theme = ThemeMode.System
        var help = false
        rule.setContent {
            StockFlipTheme {
                SettingsScreen(
                    themeMode = theme, versionName = "1.2.0", busy = false,
                    onThemeChange = { theme = it }, onExport = {}, onImport = {}, onCheckUpdate = {},
                    onOpenHelp = { help = true }, onOpenChangelog = {},
                )
            }
        }
        rule.onNodeWithText("Mörkt").performClick()
        assertEquals(ThemeMode.Dark, theme)
        rule.onNodeWithText("Hjälp").performClick()
        assertTrue(help)
        rule.onNodeWithText("Version 1.2.0").assertIsDisplayed()
    }
}
