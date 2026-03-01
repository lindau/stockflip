# StockFlip

## What This Is

Android app for monitoring Swedish and international stock prices with configurable price alerts. Users create watch items (price targets, key metrics, price ranges, daily moves, stock pairs, and combined conditions) and receive push notifications when conditions are met. Primarily focused on Swedish stocks (Nasdaq Stockholm / .ST tickers) but supports US, Norwegian, German, UK, and crypto markets.

## Core Value

Users get notified immediately when a stock or metric they care about reaches a target — without needing to constantly check prices manually.

## Requirements

### Validated

<!-- Inferred from existing codebase — shipped and working. -->

- ✓ 7 watch types: PricePair, PriceTarget, PriceRange, DailyMove, ATHBased, KeyMetrics, Combined
- ✓ Push notifications when alert conditions are met (spam protection: one per trading day per alert)
- ✓ Background price updates via WorkManager (1-minute during market hours, 60-minute outside)
- ✓ Swedish stock exchange support (.ST suffix, SEK currency, Swedish locale formatting)
- ✓ Yahoo Finance (unofficial) + Finnhub API integration
- ✓ Per-stock detail view with 52-week high/low, drawdown percentage, and existing watches
- ✓ Stock search with 5-minute in-memory cache, Swedish stocks prioritized
- ✓ Swipe-to-delete for watch items and stock symbols
- ✓ Direct inline editing for PriceRange and DailyMove items
- ✓ Room database with migrations v1→v8
- ✓ AlertEvaluator pure logic layer (testable, separate from UI)
- ✓ Market-hours-aware scheduler (Stockholm, US, LSE, Xetra, TSE, OSE)

### Active

See REQUIREMENTS.md for v1.0 milestone (Watch Creation Simplification).

### Out of Scope

- Server-side backend — local device app only
- iOS version — Android only
- Social/sharing features — individual personal alerts only
- Premium/paid features — free app

## Context

Brownfield Android project. Architecture: MVVM (ViewModel + StateFlow) with Room, WorkManager, Retrofit, OkHttp. UI is a mix of XML View Binding (fragments, dialogs) and Jetpack Compose (watch item cards, stock summary header).

Known structural concerns: monolithic MainActivity (~2,500 lines) and StockDetailFragment (~1,500 lines). WatchItem uses @Ignore fields for transient data (currentPrice, currentMetricValue etc.) that must be re-populated from the network after each DB read — causes "Loading..." flash on first load.

Database is at version 8 with a complex migration chain. Yahoo Finance integration uses unofficial API with cookie/crumb management that can be fragile.

## Constraints

- **Tech stack**: Kotlin + Android (minSdk 24, targetSdk 35) — no framework changes
- **Database**: Room v8 — all changes must preserve existing user data; avoid unnecessary migrations
- **API**: Yahoo Finance (unofficial, rate-sensitive) + Finnhub (free tier, 60 req/min)
- **Architecture**: MVVM with StateFlow; follow existing patterns in new code

## Key Decisions

| Decision | Rationale | Outcome |
|----------|-----------|---------|
| Yahoo Finance (unofficial API) | Free, comprehensive data | ⚠️ Revisit — fragile cookie/crumb management |
| WatchType stored as pipe-delimited string | Flexible polymorphism in Room without separate tables | — Pending |
| @Ignore fields for transient price data | Avoids DB migration on every new display field | ⚠️ Revisit — creates Loading... flash UX issue |
| Keep direction field in WatchType data model | Avoid DB migration risk; infer direction in code instead | — Pending |

---
*Last updated: 2026-03-01 after v1.0 milestone definition*
