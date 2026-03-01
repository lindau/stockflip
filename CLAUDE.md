# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & Test Commands

```bash
# Build debug APK
./gradlew assembleDebug

# Run all unit tests
./gradlew testDebugUnitTest

# Run a single test class
./gradlew testDebugUnitTest --tests "com.stockflip.AlertEvaluatorTest"

# Run instrumented tests (requires device/emulator)
./gradlew connectedDebugAndroidTest

# Clean build
./gradlew clean
```

Live/network tests in `YahooFinanceServiceTest.kt` are annotated with `@Ignore` — remove the annotation to run them manually. Recommended test symbols: `VOLV-B.ST` (Swedish), `AAPL` (US), `BTC-USD` (crypto).

## API Key Setup

Add to `local.properties` (not committed to git):
```
FINNHUB_API_KEY=your_key_here
```

This is injected as `BuildConfig.FINNHUB_API_KEY` at build time.

## Architecture

### MVVM with Room + WorkManager

- **`MainViewModel`** — central ViewModel managing two `StateFlow` states: `uiState` (list of `StockPair`) and `watchItemUiState` (list of `WatchItem`). Calls `MarketDataService` directly to refresh prices; updates Room then emits updated objects directly (not re-queried from DB) because `@Ignore` fields like `currentMetricValue` aren't persisted.
- **`StockDetailViewModel`** — ViewModel for the per-stock detail screen. Fetches all data via a single `getStockDetailSnapshot()` call to keep price and previousClose from the same response. Observes alerts via Room `Flow`.
- **`StockSearchViewModel`** — search ViewModel backed by `StockRepository` with a 5-minute in-memory cache.

### Domain Model

**`WatchType`** (sealed class) — defines the type of an alert:
- `PricePair` — spread between two tickers
- `PriceTarget` — single stock above/below a price
- `PriceRange` — price within [min, max]
- `DailyMove` — daily % change threshold (UP/DOWN/BOTH)
- `ATHBased` — drawdown from 52-week high (percentage or absolute SEK)
- `KeyMetrics` — fundamental metric (PE, PS, Dividend Yield)
- `Combined` — recursive AND/OR/NOT of `AlertExpression` nodes

**`AlertRule`** (sealed class) — the evaluatable form of a condition, used by `AlertEvaluator`. Parallel to `WatchType` but separate so evaluation logic is pure and testable.

**`AlertExpression`** (sealed class) — tree of `Single(AlertRule)`, `And`, `Or`, `Not` for combined alerts.

**`AlertEvaluator`** (object) — pure evaluation logic; takes an `AlertRule` + one or two `MarketSnapshot` objects and returns a boolean. Contains all trigger conditions.

### Data Layer

**`StockPairDatabase`** (Room, version 8) — three entities: `StockPair`, `WatchItem`, `MetricHistoryEntity`. `WatchType` is stored as a pipe-delimited string via `WatchTypeConverter`. Explicit migrations MIGRATION_4_5 through MIGRATION_7_8 are defined; schema files are exported to `app/schemas/`.

**`MarketDataService`** (interface) — abstraction over Yahoo Finance. Implemented by `YahooFinanceService` (singleton object using Retrofit + OkHttp with cookie jar). `YahooMarketDataServiceImpl` delegates to this. Inject the interface in ViewModels and use cases to enable fake implementations in tests.

**`StockRepository`** — handles stock symbol search (Yahoo Finance autocomplete), with a TTL cache. Prioritizes exact ticker matches, then Swedish stocks, then alphabetical.

**`MetricHistoryRepository`** / **`MetricHistoryService`** — stores historical key metric values (PE, PS, Dividend Yield) in `metric_history` table for trend tracking.

### Background Updates

**`StockPriceUpdateWorker`** (WorkManager `CoroutineWorker`) — scheduled periodic work that fetches prices for all `StockPair` entries, fires push notifications when alert conditions are met, then re-schedules itself via `StockMarketScheduler`.

**`StockMarketScheduler`** — determines update interval: 1 minute during market hours, 60 minutes outside. Handles Stockholm, US, LSE, Xetra, TSE, OSE exchange hours. Market hours are relative to each exchange's time zone.

### UI

The app uses a **hybrid View/Compose** approach:
- Fragments (`MainActivity`, `AlertsFragment`, `StockDetailFragment`) use XML layouts with View Binding.
- Alert and watch item cards in `ui/components/cards/` and the stock summary header (`StockSummaryRow`) are Jetpack Compose components embedded via `ComposeView`.
- Theme is in `ui/theme/` (Material 3). `PriceUp`/`PriceDown` colours are defined in `Color.kt`.
- `SwipeToDeleteCallback` (`ui/SwipeToDeleteCallback.kt`) is a reusable `ItemTouchHelper` for RecyclerViews.

### Key Conventions

- UI state is represented by the `UiState<T>` sealed class (`Loading`, `Success`, `Error`).
- `WatchItem` uses the **copy-and-mutate** pattern for `@Ignore` fields (e.g. `withCurrentPrices`, `withCurrentMetricValue`) because Room doesn't persist them.
- Spam protection on alerts: each `WatchItem` has `isTriggered` + `lastTriggeredDate`; use `canTrigger(today)` before firing a notification, `markAsTriggered(today)` after.
- Stock tickers for the Swedish market use the `.ST` suffix (e.g. `VOLV-B.ST`).
- Price formatting uses Swedish locale (`sv_SE`) with `#,##0.00` pattern.
- Code comments are mixed Swedish/English (Swedish is common in business logic).
