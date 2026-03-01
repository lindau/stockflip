# External Integrations

**Analysis Date:** 2026-03-01

## APIs & External Services

**Stock Market Data:**
- Yahoo Finance API - Primary real-time stock pricing and company information
  - SDK/Client: Retrofit 2.11.0 + custom OkHttpClient
  - Endpoints:
    - `v8/finance/chart/{symbol}` - Stock price and chart data via `YahooFinanceApi` interface
    - `v10/finance/quoteSummary/{symbol}?modules=summaryDetail` - Key metrics (P/E, dividend yield, P/S)
    - `v1/test/getcrumb` - Authentication token for API access
    - `v1/finance/search` - Stock symbol search (equity and cryptocurrency)
  - Auth: Crumb-based authentication (obtained from getcrumb endpoint)
  - Rate Limiting: Yes (automatic retry with circuit breaker pattern)
  - Files: `YahooFinanceService.kt`, `YahooMarketDataServiceImpl.kt`

- Finnhub API - Fallback financial metrics and historical data
  - SDK/Client: OkHttpClient with custom implementation
  - Endpoints:
    - `stock/metric?symbol={symbol}&metric=all` - Key metrics (P/E, P/S, dividend yield)
    - `stock/financials-reported?symbol={symbol}` - Historical financial statements for metric history
  - Auth: API key (FINNHUB_API_KEY from BuildConfig)
  - Rate Limiting: 60 calls/minute enforced with 1-second delay between requests
  - Files: `FinnhubService.kt`

## Data Storage

**Databases:**
- SQLite (via Room ORM)
  - Provider: Android built-in SQLite 3
  - Connection: In-app Room database instance
  - Client: Androidx Room 2.6.1
  - Database Name: `stock_pair_database`
  - Location: Android application cache/data directory
  - Tables:
    - `stock_pairs` - Watch pairs tracking price differences (v4+)
    - `watch_items` - Generic watch items for price ranges, P/E, dividend yield, daily move (v5+)
    - `metric_history` - Historical metric data (P/E, P/S, dividend) per symbol (v7+)
  - DAO Files:
    - `StockPairDao.kt` - Stock pair data access operations
    - `WatchItemDao.kt` - Watch item data access operations
    - `MetricHistoryDao.kt` - Metric history data access operations
  - Schema Export: Yes, exported to `app/schemas/` for version control
  - Database Version: 8 with automatic migrations

**File Storage:**
- Local filesystem only - No cloud storage integration
- Room database persisted to `/data/data/com.stockflip/databases/`

**Caching:**
- No explicit caching service (in-memory data via LiveData and Compose state)
- Network responses cached implicitly by OkHttpClient cookie jar

## Authentication & Identity

**Auth Provider:**
- Custom/None - No user account system
- Implementation: Yahoo Finance crumb-based token + Finnhub API key
  - Crumb obtained dynamically at runtime from Yahoo Finance
  - Finnhub API key stored in BuildConfig at compile-time
  - No user authentication required

## Monitoring & Observability

**Error Tracking:**
- None - No external error tracking service integrated

**Logs:**
- Local console logging only
- OkHttpClient HttpLoggingInterceptor enabled at BODY level for debugging
- Log tags used: FinnhubService, YahooFinanceService, StockPriceUpdateWorker
- File: No file-based logging

## CI/CD & Deployment

**Hosting:**
- Google Play Store (deployment target, not integrated in code)
- Direct APK installation (alternative)

**CI Pipeline:**
- None detected - No CI/CD service integration in codebase

## Environment Configuration

**Required env vars:**
- `FINNHUB_API_KEY` - Finnhub API key for financial metrics fallback
  - Set in: `local.properties` (not committed)
  - Used by: FinnhubService via BuildConfig injection
  - Priority: Required for P/E, P/S, and dividend yield metrics fallback

**Secrets location:**
- `local.properties` - Local environment file (excluded from git via .gitignore)
- Finnhub API key never exposed in logs (masked as "***" in debug logs)
- No AWS/cloud credentials integration

## Webhooks & Callbacks

**Incoming:**
- None

**Outgoing:**
- None (app does not push data to external services)
- Broadcasts sent internally only:
  - ACTION_PRICES_UPDATED - Sent after price update completion to notify UI components

## API Request Patterns

**HTTP Client Configuration:**
- Base Timeout: 15 seconds (YahooFinanceService), 10 seconds (FinnhubService)
- Cookie Management: Enabled with CookieManager/JavaNetCookieJar for session persistence
- User-Agent: Mozilla/5.0 (Chrome-like) to avoid blocking
- Interceptors: HttpLoggingInterceptor (BODY level) for debug logging
- Files: `YahooFinanceService.kt` (cookieClient, client), `FinnhubService.kt`

**Rate Limiting & Circuit Breaker:**
- Yahoo Finance: Circuit breaker after 3 consecutive failures, 1-minute cooldown before retry
- Finnhub: 1-second delay enforced between requests (60/minute limit)
- Files: `YahooFinanceService.kt` (lines 117-121), `FinnhubService.kt` (lines 24-27)

**Error Handling:**
- Crumb expiration: Yahoo Finance resets crumb on 401 responses for next attempt
- Fallback strategy: Yahoo fails → Finnhub (for key metrics only)
- Symbol variants: Finnhub tries multiple symbol formats (.ST and non-.ST)
- Network timeouts: Catches SocketTimeoutException and logs as warning

## Background Job Integration

**WorkManager Integration:**
- Periodic price updates via `StockPriceUpdateWorker`
- Background metrics history updates via `MetricHistoryUpdateWorker`
- Unique Work Configuration: ExistingPeriodicWorkPolicy used to prevent duplicates
- Application: `StockFlipApplication.kt` initializes WorkManager on app startup

---

*Integration audit: 2026-03-01*
