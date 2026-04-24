# StockFlip

A personal Android app for monitoring stock prices and triggering push notifications when configurable alert conditions are met. Supports Swedish and international markets with market-hour-aware background updates.

## Features

- **3 main app areas** — `Översikt` for read-first prioritization, `Mina case` for setup and administration, and `Par` for pair alerts
- **In-app changelog** — tap the version row in the app menu to open the latest local changelog
- **5 primary alert flows in the current UI** — price targets, daily % moves, 52-week drawdowns, key metrics (PE/PS/yield), and spread between two tickers
- **Legacy watch type support** — price ranges and composite AND/OR/NOT conditions remain supported in storage/rendering and can still be edited when they already exist
- **Push notifications** with spam protection — at most one notification per alert per trading day
- **Market-aware background updates** — 1-minute refresh during open hours, 60-minute outside
- **Interactive stock charts** — intraday and multi-period (1M, 3M, 6M, 1Y, 5Y) price history
- **Key metrics tracking** — PE ratio, PS ratio, and dividend yield with historical trend storage
- **Stock search** — Yahoo Finance autocomplete with exact-match and Swedish-stock prioritization
- **Hybrid UI** — Fragment/View Binding structure with Jetpack Compose cards embedded via `ComposeView`

## Current User Flows

- **Översikt** — read-only overview of your cases with sections for triggered items, near-trigger items, active cases, and inactive cases
- **Mina case** — add a stock or crypto with the floating `+` button, open its detail page, and create or manage alerts there; also filter, batch-manage, edit, pause, reactivate, and delete cases
- **Par** — add a stock pair from the `Par` tab with the floating `+` button
- **Stock detail** — shows price snapshot, existing alerts for the selected stock, and buttons to add `Målpris`, `Drawdown`, `Dagsrörelse`, or `Nyckeltal`
- **Help** — open the in-app help from the top app bar menu; it renders `app/src/main/assets/manual.md`
- **Changelog** — tap the version row in the top app bar menu; it renders `app/src/main/assets/changelog.md`

## Supported Markets

| Exchange | Suffix example | Hours (local) |
|---|---|---|
| Stockholm (Nasdaq Nordic) | `VOLV-B.ST` | 09:00–17:30 |
| NYSE / NASDAQ / AMEX | `AAPL` | 09:30–16:00 ET |
| London (LSE) | `SHEL.L` | 08:00–16:30 |
| Frankfurt (Xetra) | `SAP.DE` | 09:00–17:30 |
| Tokyo (TSE) | `7203.T` | 09:00–15:00 |
| Oslo (OSE) | `EQNR.OL` | 09:00–16:25 |
| Crypto | `BTC-USD` | 24/7 |

## Alert Types

| Type | Triggers when… |
|---|---|
| **PriceTarget** | Stock price crosses a target (direction auto-inferred at save) |
| **DailyMove** | Daily change % exceeds a threshold (up, down, or either) |
| **ATHBased** | Drawdown from 52-week high exceeds a value (% or absolute) |
| **KeyMetrics** | PE ratio, PS ratio, or dividend yield crosses a target |
| **PricePair** | Spread between two tickers reaches a threshold |
| **PriceRange** | Price falls within a min–max band |
| **Combined** | Logical AND / OR / NOT of any of the above |

## Architecture

MVVM with Room, StateFlow, WorkManager, and Retrofit.

```
UI (Fragments + Compose)
   └── ViewModels (StateFlow)
        └── MarketDataService (Yahoo Finance + Finnhub)
             └── Room Database (StockPair, WatchItem, MetricHistoryEntity)
                  └── WorkManager (StockPriceUpdateWorker, MetricHistoryUpdateWorker)
```

- **`MainViewModel`** — owns `uiState` (stock pairs) and `watchItemUiState` (alerts), refreshes on a 2-minute cycle
- **`StockDetailViewModel`** — fetches price snapshot and chart data; observes alerts via Room `Flow`
- **`AlertEvaluator`** — pure, side-effect-free evaluation of alert rules; fully unit-tested
- **`WatchItemUiState`** — wrapper that separates transient live data (current price, triggered state) from the persisted `WatchItem` Room entity
- **`StockMarketScheduler`** — timezone-aware open/closed logic per exchange; drives WorkManager intervals

## Tech Stack

| Layer | Library |
|---|---|
| Language | Kotlin |
| UI | XML layouts + Jetpack Compose (Material 3) |
| Architecture | MVVM, StateFlow, LiveData |
| Database | Room 2.7 (3 entities, 8 migrations, KSP) |
| Networking | Retrofit 2 + OkHttp 4 |
| Background | WorkManager 2.9 |
| Coroutines | kotlinx-coroutines 1.7 |
| Testing | JUnit 4, MockK, Robolectric, MockWebServer, Turbine |

## Setup

### Prerequisites

- Android Studio Hedgehog or later
- Android SDK 35
- A free [Finnhub](https://finnhub.io) API key (for PE/PS/yield alerts)

### API Key

Create `local.properties` in the project root (not committed to git) and add:

```properties
FINNHUB_API_KEY=your_key_here
```

This is injected as `BuildConfig.FINNHUB_API_KEY` at build time.

### Build

```bash
# Debug APK
./gradlew assembleDebug

# Run unit tests
./gradlew testDebugUnitTest

# Single test class
./gradlew testDebugUnitTest --tests "com.stockflip.AlertEvaluatorTest"

# Instrumented tests (requires device or emulator)
./gradlew connectedDebugAndroidTest
```

Unit tests are fully offline and include MockWebServer fixtures for Yahoo Finance responses. Live/network tests in `YahooFinanceServiceTest.kt` are annotated with `@Ignore` — remove the annotation to run them manually. Recommended test symbols: `VOLV-B.ST` (Sweden), `AAPL` (US), `BTC-USD` (crypto), `EQNR.OL` (Norway).

## Versioning

- `versionCode` follows total git commit count
- `versionName` starts at `1.1.0`, increments the patch number automatically from that baseline, and appends git/build metadata as `+<commit-count>.<short-sha>[.dirty].<yyyyMMdd.HHmmss>`
- The app menu shows `BuildConfig.VERSION_NAME`

## Maintenance

- Update both `docs/CHANGELOG.md` and `app/src/main/assets/changelog.md` whenever user-facing changes are shipped so the in-app version view stays current.

## Requirements

- **Min SDK:** Android 7.0 (API 24)
- **Target SDK:** Android 15 (API 35)

## Data Sources

- **Yahoo Finance** — prices, chart history, 52-week high/low, previous close. Uses the unofficial API via Retrofit with cookie handling.
- **Finnhub** — PE ratio, PS ratio, dividend yield. Free tier: 60 req/min. Falls back to Yahoo Finance fields where available.

## License

Private project — not licensed for redistribution.
