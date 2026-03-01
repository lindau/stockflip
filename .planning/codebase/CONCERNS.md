# Codebase Concerns

**Analysis Date:** 2026-03-01

## Tech Debt

**Monolithic MainActivity (2,486 lines):**
- Issue: `MainActivity.kt` is vastly oversized and contains ~88 functions with 60+ private methods, handling UI composition, dialog management, data fetching, and state coordination all in one class
- Files: `app/src/main/java/com/stockflip/MainActivity.kt`
- Impact: Difficult to test, maintain, and extend; high cognitive load; difficult to reuse logic; violates Single Responsibility Principle
- Fix approach: Extract dialog management into separate fragment controllers; move watch item operations into dedicated fragments; create view model for dialog state; use composition pattern for dialog builders (combine AlertDialog creation logic into separate builder classes)

**Monolithic StockDetailFragment (1,466 lines):**
- Issue: Fragment contains logic for displaying metrics, handling multiple alert types, managing edit dialogs, and complex UI state transitions
- Files: `app/src/main/java/com/stockflip/StockDetailFragment.kt`
- Impact: Hard to test component behavior; difficult to isolate and debug metric display or alert editing logic
- Fix approach: Extract alert editing into separate EditAlertDialog component; move metric display into separate composable or dedicated view component; use ViewModel to separate UI logic from rendering

**@Ignore Fields Create Runtime Initialization Burden:**
- Issue: `WatchItem.kt` uses 8 @Ignore fields (`currentPrice1`, `currentPrice2`, `currentPrice`, `currentMetricValue`, `metricValueAtCreation`, `currentATH`, `currentDropPercentage`, `currentDropAbsolute`, `currentDailyChangePercent`) that are not persisted and must be manually populated after database reads
- Files: `app/src/main/java/com/stockflip/WatchItem.kt` (lines 35-69)
- Impact: Every WatchItem loaded from database is incomplete; requires immediate network refresh to populate values; risk of displaying "Loading..." states; complex state tracking across layers
- Fix approach: Refactor to use a separate `WatchItemDisplay` data class that combines persistent `WatchItem` with transient computed values; populate this on UI layer only; OR persist the most-accessed values (currentPrice, currentMetricValue) to database

**Multiple Symbol Variants Handling in FinnhubService:**
- Issue: `FinnhubService.kt` (lines 49-62) handles symbol variants (.ST vs non-.ST for Swedish stocks) by trying multiple formats, increasing request count and latency
- Files: `app/src/main/java/com/stockflip/FinnhubService.kt`
- Impact: Rate limiting is more likely to trigger; slower metric fetches; complex retry logic; potential for incomplete data refresh
- Fix approach: Cache symbol variant mappings after first successful fetch; pre-validate symbols during stock selection to determine correct format

**API Key Built into BuildConfig:**
- Issue: Finnhub API key is embedded via BuildConfig from local.properties, logged in debug mode (lines 32, 77 in FinnhubService.kt)
- Files: `app/src/main/java/com/stockflip/FinnhubService.kt`, `app/build.gradle` (line 27)
- Impact: Risk of key exposure through debug logs; not suitable for production distribution; cannot rotate keys without rebuild
- Fix approach: Move to remote configuration service or request key from secure backend endpoint during app startup; remove debug logging of API key; use secure storage (Android Keystore) for sensitive credentials

**Database Migrations Getting Complex:**
- Issue: 8 database schema versions with increasingly complex migrations; migration 5->6 converts old PricePair table to generic WatchItem with serialized watchType field
- Files: `app/src/main/java/com/stockflip/StockPairDatabase.kt` (migrations from line 23-142)
- Impact: Risky to test; potential data loss if migration fails; difficult to debug production data issues; forward compatibility breaks if schema needs revision
- Fix approach: Document migration testing procedure; add migration verification tests; consider using Room's auto-migration feature for future versions

## Known Bugs

**KeyMetrics Values Not Available on First Load:**
- Symptoms: KeyMetrics watch items show "Loading..." on initial app launch; UI state remains Loading until refreshWatchItems() completes
- Files: `app/src/main/java/com/stockflip/MainViewModel.kt` (lines 124-137)
- Trigger: Launch app with existing KeyMetrics watch items; observe loading state before first network refresh
- Workaround: Manually refresh or wait for auto-refresh to complete; app does prevent showing stale data by explicitly not setting Success state until metrics are loaded

**YahooFinanceService Cookie/Crumb Management Fragile:**
- Symptoms: Stock price fetches may intermittently fail with authentication errors; crumb fetch has 2-second timeout that may fail on slow connections
- Files: `app/src/main/java/com/stockflip/YahooFinanceService.kt` (lines 123-152)
- Trigger: Network latency >2 seconds during crumb fetch; cookie validity expires; multiple concurrent price requests
- Workaround: Manual refresh or retry; app has circuit breaker (3 consecutive failures triggers cooldown) but doesn't inform user of cooldown state

**Notification ID Collision:**
- Symptoms: Multiple alerts may overwrite each other's notifications if sent within same millisecond (very rare but possible)
- Files: `app/src/main/java/com/stockflip/StockPriceUpdateWorker.kt` (line 138)
- Trigger: Two alert evaluations complete in same millisecond on same worker instance
- Workaround: Currently using System.currentTimeMillis().toInt() which can collide; should use atomic counter

## Security Considerations

**API Key Exposure in Logs:**
- Risk: Finnhub API key is logged in plaintext in debug logs (`Log.d(TAG, "API key loaded from BuildConfig, length: ${key.length}, first 5 chars: ${key.take(5)}...")`)
- Files: `app/src/main/java/com/stockflip/FinnhubService.kt` (line 32)
- Current mitigation: Only in debug builds; log shows only first 5 chars
- Recommendations: Remove all API key logging; use feature flags to gate expensive debug logging; implement request signing instead of query parameter tokens

**BuildConfig Contains Secrets:**
- Risk: API key written to BuildConfig.java which is committed to repo (if local.properties is accidentally committed)
- Files: `app/build.gradle` (lines 20-27)
- Current mitigation: Relies on .gitignore for local.properties; no verification that file is actually ignored
- Recommendations: Add git pre-commit hook to verify secrets files are not staged; use Gradle property encryption; consider server-side key retrieval

**Network Requests Using CookieJar:**
- Risk: Yahoo Finance implementation stores cookies in CookieManager with ACCEPT_ALL policy
- Files: `app/src/main/java/com/stockflip/YahooFinanceService.kt` (lines 84-87)
- Current mitigation: Cookies stored locally in app cache; but policy is overly permissive
- Recommendations: Use ACCEPT_ORIGINAL_SERVER policy instead; validate cookie domains

**PendingIntent Immutability:**
- Risk: PendingIntent in notification uses FLAG_UPDATE_CURRENT which can be hijacked
- Files: `app/src/main/java/com/stockflip/StockPriceUpdateWorker.kt` (line 122)
- Current mitigation: FLAG_IMMUTABLE is used (correctly)
- Recommendations: Good practice already in place; ensure all PendingIntents use FLAG_IMMUTABLE

## Performance Bottlenecks

**Synchronous Database Access in Refresh Loop:**
- Problem: `MainViewModel.refreshWatchItems()` fetches all watch items from database, then for each, makes a network call; if there are 20+ items, this is slow and blocks
- Files: `app/src/main/java/com/stockflip/MainViewModel.kt` (lines 144-200+)
- Cause: Sequential loading and refresh; no parallel fetching or pagination
- Improvement path: Batch network requests using async/await; implement pagination for large watch lists; cache most recent values server-side

**Metric History Full-Table Scans:**
- Problem: MetricHistoryRepository likely scans entire metric_history table for date ranges without proper indexes
- Files: `app/src/main/java/com/stockflip/repository/MetricHistoryRepository.kt`
- Cause: Basic query structure without filtering
- Improvement path: Ensure indexes on (symbol, metricType, date) are present; add query limits; implement time-series specific optimizations

**Full Array Adapter Updates on Text Change:**
- Problem: Stock search uses ArrayAdapter with full list refresh on each keystroke
- Files: `app/src/main/java/com/stockflip/MainActivity.kt` (search dialogs)
- Cause: No debouncing or incremental filtering
- Improvement path: Add 300ms debounce on search input; implement filtered list adapter instead of replacing entire list; use Flow-based search with lazy loading

**Layout Inflation in Adapters:**
- Problem: `WatchItemAdapter`, `GroupedWatchItemAdapter`, and `AlertAdapter` inflate layouts for every item conversion, even for items not currently visible
- Files: `app/src/main/java/com/stockflip/WatchItemAdapter.kt`, `app/src/main/java/com/stockflip/GroupedWatchItemAdapter.kt`, `app/src/main/java/com/stockflip/AlertAdapter.kt`
- Cause: Standard RecyclerView behavior without view pooling optimization
- Improvement path: Implement view type pooling; use data binding to reduce inflation cost; profile with Layout Inspector to measure impact

## Fragile Areas

**Alert Rule Evaluation Complex Logic:**
- Files: `app/src/main/java/com/stockflip/AlertEvaluator.kt`, `app/src/main/java/com/stockflip/AlertRule.kt`, `app/src/main/java/com/stockflip/AlertExpressionConverter.kt`
- Why fragile: Multiple alert types (PairSpread, SinglePrice, SingleDailyMove, SingleKeyMetric, Combined) with different evaluation logic; expression language parsing for combined alerts; null handling for missing market data
- Safe modification: Add unit tests for each alert rule type before modifying evaluation logic; use property-based testing for expression parsing; add integration tests with real market snapshots
- Test coverage: `AlertEvaluatorTest.kt` exists (311 lines) - good coverage; but combined alert expressions need more edge case testing

**WatchItem Data Model with Optional Fields:**
- Files: `app/src/main/java/com/stockflip/WatchItem.kt`
- Why fragile: Single data class represents 7 different watch types; each has different required/optional fields; display names use optional chaining with fallbacks
- Safe modification: Add validation in watchItem creation to ensure required fields are present for chosen type; use sealed class variants (PricePairWatchItem, PriceTargetWatchItem, etc.) instead of type unions; add constructor validation
- Test coverage: `WatchItemTest.kt` exists (90 lines) but doesn't test all type combinations

**MainActivity State Machine:**
- Files: `app/src/main/java/com/stockflip/MainActivity.kt`
- Why fragile: Manages two tabs (STOCKS, PAIRS), three ViewModels, multiple dialogs, auto-refresh state, and fragment back stack; switching tabs clears all dialogs and resets state
- Safe modification: Centralize tab state in MainViewModel; extract dialog state management to separate component; add explicit state transition tests; use StateFlow to represent all possible states
- Test coverage: `MainActivityTest.kt` (336 lines) provides some coverage but doesn't test all dialog and tab combinations

**MarketDataService Implementation Switching:**
- Files: `app/src/main/java/com/stockflip/YahooFinanceService.kt`, `app/src/main/java/com/stockflip/YahooMarketDataServiceImpl.kt`, `app/src/main/java/com/stockflip/repository/StockRepository.kt`
- Why fragile: Multiple implementations of MarketDataService; circuit breaker logic in YahooFinanceService; fallback to Finnhub for key metrics; no configuration for which implementation to use
- Safe modification: Use dependency injection (Hilt/Dagger) to configure implementations; add logging of which service is being used; test fallback paths explicitly
- Test coverage: FinnhubServiceTest and YahooFinanceServiceTest exist but mock implementations aren't tested together

## Scaling Limits

**Database Row Limits:**
- Current capacity: Watch items table supports ~100k rows comfortably; metric_history table may grow unbounded (new entry per symbol per metric type per day)
- Limit: metric_history could reach millions of rows if app tracks 50+ symbols for 5 years
- Scaling path: Implement data retention policies (delete entries older than 2 years); batch metric history inserts; add archival strategy

**API Rate Limiting:**
- Current capacity: Finnhub free tier allows 60 requests/minute; YahooFinance has no official limit but behaves unpredictably
- Limit: If watch items exceed 50, refresh cycle will likely hit rate limits
- Scaling path: Implement request batching; cache responses more aggressively (15+ min cache instead of per-request); use premium API tier; implement request queue with intelligent scheduling

**RecyclerView Performance:**
- Current capacity: Smooth scrolling at ~100 watch items
- Limit: >200 items causes frame drops and ANR risk
- Scaling path: Implement pagination/virtualization; use DiffUtil more aggressively; profile and optimize layout inflation

## Dependencies at Risk

**Retrofit 2.11.0 with Gson:**
- Risk: Older converter; potential JSON parsing vulnerabilities; no native support for serialization classes
- Impact: Custom JSON parsing fallback code in YahooMarketDataServiceImpl; type converter complexity for WatchType
- Migration plan: Consider Kotlinx Serialization for type safety; upgrade to latest Retrofit if new major versions are available

**OkHttp 4.12.0:**
- Risk: No critical vulnerabilities but not latest; HTTP client security depends on TLS version
- Impact: All network requests depend on this version
- Migration plan: Regular dependency updates; verify TLS 1.3 is enabled in deployment

**Room 2.6.1:**
- Risk: Database migrations are executed at runtime; no schema validation; corrupt migrations can crash app on startup
- Impact: All persisted data depends on migration chain
- Migration plan: Add pre-migration schema validation; test migrations on real devices; maintain offline migration testing

## Missing Critical Features

**No Offline Mode:**
- Problem: App requires live API calls to display current prices; cached data is shown but updating fails silently if network is offline
- Blocks: Users cannot view watch items on trains, flights, or areas with poor signal
- Recommendation: Cache last known prices; add offline indicator; queue price updates for when network returns

**No Alert Notification Confirmation:**
- Problem: When alert triggers and sends notification, there's no way to confirm user saw it or dismissed it
- Blocks: Cannot track which alerts were successfully communicated
- Recommendation: Add notification click tracking; persist notification history; add in-app notification center

**No Duplicate Watch Item Detection:**
- Problem: Users can create multiple identical watch items with same symbol and rule
- Blocks: Cluttered alert lists; confusing duplicate notifications
- Recommendation: Add uniqueness validation; suggest existing similar items during creation

**No Alert Edit History:**
- Problem: Once an alert is created, no record of what changed
- Blocks: Cannot debug why alert rules are different than expected
- Recommendation: Add audit log for watch item modifications with timestamps and old/new values

## Test Coverage Gaps

**MainActivity Dialog Combinations Untested:**
- What's not tested: Switching tabs while dialog is open; opening multiple dialogs in sequence; editing watch item and canceling
- Files: `app/src/main/java/com/stockflip/MainActivity.kt`
- Risk: Dialog state corruption; crashes when backing out of dialogs; data loss on canceled edits
- Priority: High - MainActivity is critical path; dialogs are frequently used

**Network Failure Scenarios Partially Tested:**
- What's not tested: What happens when YahooFinanceService crumb fetch times out (2 second limit); behavior when Finnhub rate limit (429) is triggered; behavior when JSON response is malformed
- Files: `app/src/main/java/com/stockflip/YahooFinanceService.kt`, `app/src/main/java/com/stockflip/FinnhubService.kt`
- Risk: Unhandled exceptions on network edge cases; UI stuck in Loading state; silent failures
- Priority: High - app reliability depends on network robustness

**Large Watch Item List Performance:**
- What's not tested: RecyclerView performance with 100+ items; memory usage growth; scroll jank
- Files: `app/src/main/java/com/stockflip/WatchItemAdapter.kt`
- Risk: ANR crashes on slower devices; poor user experience with large lists
- Priority: Medium - affects power users; not critical on day 1

**Combined Alert Expression Edge Cases:**
- What's not tested: Nested expressions with 4+ conditions; expressions with all AND/all OR; invalid expression syntax error handling
- Files: `app/src/main/java/com/stockflip/AlertExpression.kt`, `app/src/main/java/com/stockflip/AlertExpressionConverter.kt`
- Risk: Invalid expressions may evaluate unpredictably; parser may crash on malformed input
- Priority: Medium - affects power users creating complex rules

**Database Migration Testing:**
- What's not tested: All 8 migrations in sequence; data preservation through full migration path; recovery from partial migration
- Files: `app/src/main/java/com/stockflip/StockPairDatabase.kt`
- Risk: User data loss on app update; app may fail to open if migration fails
- Priority: Critical - users lose all watch items if migration fails

---

*Concerns audit: 2026-03-01*
