package com.stockflip

import androidx.annotation.StringRes

/**
 * Ren presentationslogik för listvyerna (Bevakningar och Par), utbruten ur fragmenten
 * så att filtrering, rubrikräknare och tomtillstånd kan enhetstestas utan Android-vyer.
 */
internal enum class AlertsFilter(
    @StringRes val emptyTitleRes: Int,
    @StringRes val emptySubtitleRes: Int
) {
    ALL(R.string.alerts_empty_title, R.string.alerts_empty_subtitle),
    ACTIVE(R.string.alerts_empty_active_title, R.string.alerts_empty_active_subtitle),
    TRIGGERED(R.string.alerts_empty_triggered_title, R.string.alerts_empty_triggered_subtitle),
    PRICE(R.string.alerts_empty_price_title, R.string.alerts_empty_price_subtitle),
    METRICS(R.string.alerts_empty_metrics_title, R.string.alerts_empty_metrics_subtitle),
    PAIRS(R.string.alerts_empty_pairs_title, R.string.alerts_empty_pairs_subtitle);

    fun matches(uiState: WatchItemUiState): Boolean = when (this) {
        ALL -> true
        ACTIVE -> uiState.item.isActive
        TRIGGERED -> uiState.isTriggeredForDisplay()
        PRICE -> when (uiState.item.watchType) {
            is WatchType.PriceTarget,
            is WatchType.ATHBased,
            is WatchType.DailyMove,
            is WatchType.PriceRange -> true
            else -> false
        }
        METRICS -> uiState.item.watchType is WatchType.KeyMetrics
        PAIRS -> uiState.item.watchType is WatchType.PricePair
    }
}

internal data class AlertsHeaderSummary(val triggeredToday: Int, val active: Int)

internal fun alertsHeaderSummary(items: List<WatchItemUiState>, today: String): AlertsHeaderSummary =
    AlertsHeaderSummary(
        triggeredToday = items.count { it.isTriggeredTodayForDisplay(today) },
        active = items.count { it.item.isActive }
    )

/** Vad som ska visas i listans tomvy. */
internal sealed class WatchListEmptyState {
    /** Listan har synliga rader — ingen tomvy. */
    object Hidden : WatchListEmptyState()

    /** Inga rader att visa (eventuellt p.g.a. filtret). */
    object NoItems : WatchListEmptyState()

    /** Första laddningen misslyckades — visa felet med "Försök igen", aldrig "inga bevakningar". */
    data class LoadFailed(val message: String) : WatchListEmptyState()
}

/** Vad översiktens tomvy ska visa. Aktiepar visas inte i översikten utan i fliken Par. */
internal sealed class OverviewEmptyState {
    object Hidden : OverviewEmptyState()
    object NoWatches : OverviewEmptyState()

    /** Det finns bevakningar, men bara aktiepar — hänvisa till fliken Par i stället för "inga bevakningar". */
    object OnlyPairs : OverviewEmptyState()
    data class LoadFailed(val message: String) : OverviewEmptyState()
}

internal fun overviewEmptyState(items: List<WatchItemUiState>, loadError: String?): OverviewEmptyState = when {
    items.any { it.item.watchType !is WatchType.PricePair } -> OverviewEmptyState.Hidden
    loadError != null -> OverviewEmptyState.LoadFailed(loadError)
    items.isNotEmpty() -> OverviewEmptyState.OnlyPairs
    else -> OverviewEmptyState.NoWatches
}

/**
 * @param loadError felmeddelande från en misslyckad laddning när ingen data finns, annars null.
 *   Ett fel ska ligga kvar även om användaren byter filter, tills en laddning lyckas.
 */
internal fun watchListEmptyState(visibleItemCount: Int, loadError: String?): WatchListEmptyState = when {
    visibleItemCount > 0 -> WatchListEmptyState.Hidden
    loadError != null -> WatchListEmptyState.LoadFailed(loadError)
    else -> WatchListEmptyState.NoItems
}
