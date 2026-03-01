# Architecture

**Analysis Date:** 2026-03-01

## Pattern Overview

**Overall:** MVVM (Model-View-ViewModel) with Clean Architecture principles, layered with Repository and Use Case patterns.

**Key Characteristics:**
- Separation of concerns into UI, domain logic, and data layers
- Reactive state management using StateFlow and Coroutines
- Database persistence via Room with type converters for polymorphic types
- Fragment-based UI with ViewBinding and Jetpack Compose for newer components
- Alert evaluation separated into pure logic layer (AlertEvaluator object)
- Market data abstraction via MarketDataService interface with multiple implementations

## Layers

**Presentation Layer (UI):**
- Purpose: Display data, handle user interactions, manage UI state
- Location: `app/src/main/java/com/stockflip/` (Activities, Fragments) and `app/src/main/java/com/stockflip/ui/` (Compose components)
- Contains: MainActivity, Fragments (AlertsFragment, AddStockFragment, StockDetailFragment), Adapters, Compose components
- Depends on: ViewModels, Repository, UI theme/presets
- Used by: Android OS (lifecycle management)

**Presentation State Management (ViewModel Layer):**
- Purpose: Hold and expose UI state, manage lifecycle-aware coroutines
- Location: `app/src/main/java/com/stockflip/` (MainViewModel, StockDetailViewModel) and `app/src/main/java/com/stockflip/viewmodel/`
- Contains: ViewModels that expose StateFlow<UiState<T>>
- Depends on: DAO, Service, Repository, Use Cases
- Used by: Activities and Fragments

**Domain/Business Logic Layer:**
- Purpose: Pure logic for evaluating alerts, computing prices, determining market state
- Location: `app/src/main/java/com/stockflip/` (AlertEvaluator, WatchType, AlertRule, MarketSnapshot)
- Contains: AlertEvaluator (sealed class object), sealed classes for domain models
- Depends on: Data models only
- Used by: Repositories, Use Cases, Workers

**Use Case Layer:**
- Purpose: Orchestrate domain logic and data access for specific business operations
- Location: `app/src/main/java/com/stockflip/usecase/`
- Contains: UpdateStockPairsPricesUseCase and similar operation classes
- Depends on: DAO, MarketDataService, Domain models
- Used by: ViewModel, Workers, Service

**Repository Layer:**
- Purpose: Abstract data access and caching, provide single point of data source truth
- Location: `app/src/main/java/com/stockflip/repository/`
- Contains: StockRepository (with 5-minute caching), MetricHistoryRepository, SearchState
- Depends on: DAO, StockSearchService, MarketDataService
- Used by: ViewModel, UI layer, Search components

**Data Access Layer (DAO):**
- Purpose: Direct database access with Room
- Location: `app/src/main/java/com/stockflip/` (StockPairDao, WatchItemDao, MetricHistoryDao)
- Contains: Room DAO interfaces with Room queries
- Depends on: Room entities
- Used by: Repository, Use Cases, ViewModel

**Data Models Layer:**
- Purpose: Define entity structures for persistence and domain
- Location: `app/src/main/java/com/stockflip/` (StockPair, WatchItem, MetricHistoryEntity, AlertRule, WatchType)
- Contains: Room @Entity classes, sealed class models
- Depends on: Type converters
- Used by: All layers above

**External Data Layer:**
- Purpose: Fetch real-time market data from external APIs
- Location: `app/src/main/java/com/stockflip/` (MarketDataService, YahooFinanceService, YahooMarketDataServiceImpl, FinnhubService)
- Contains: Service interfaces and implementations for market data and search
- Depends on: Retrofit, external API clients
- Used by: Repository, Use Cases

**Background Processing Layer:**
- Purpose: Scheduled updates and workers
- Location: `app/src/main/java/com/stockflip/` (StockPriceUpdateWorker, MetricHistoryUpdateWorker) and `app/src/main/java/com/stockflip/workers/`
- Contains: CoroutineWorker implementations for periodic tasks
- Depends on: DAO, Service, Use Cases, AlertEvaluator
- Used by: WorkManager scheduler

## Data Flow

**Price Update Flow:**

1. StockPriceUpdateWorker (background) triggers periodically via WorkManager
2. Worker calls UpdateStockPairsPricesUseCase.executeUpdateStockPairsPrices()
3. Use Case fetches all StockPair entities via StockPairDao.getAllStockPairs()
4. Use Case calls MarketDataService.getStockPrice() for each ticker
5. Updated prices are persisted via StockPairDao.update()
6. Updated pairs are returned to worker
7. Worker evaluates alert conditions against prices
8. Worker sends broadcast (ACTION_PRICES_UPDATED) and shows notifications
9. MainActivity observes broadcast and refreshes UI
10. MainViewModel emits updated uiState via StateFlow

**Watch Item Addition Flow:**

1. User inputs watch criteria in dialog (AddStockFragment)
2. Dialog builds WatchItem with appropriate WatchType (PriceTarget, KeyMetrics, etc.)
3. WatchItem is inserted via watchItemDao.insert()
4. MainViewModel observes database changes or reloads watch items
5. AlertAdapter renders WatchItem with appropriate card (PriceTargetCard, etc.)
6. WorkManager worker periodically evaluates watch item conditions
7. AlertEvaluator compares current MarketSnapshot against AlertRule derived from WatchItem
8. If condition triggers, notification is shown

**Search Flow:**

1. User types in AutoCompleteTextView
2. Query flows to StockSearchViewModel.searchStocks()
3. ViewModel calls StockRepository.searchStocks()
4. Repository checks 5-minute cache for matching query
5. If cached, emits SearchState.Success immediately
6. If not cached, calls StockSearchService.searchStocks() (YahooStockSearchService)
7. Results are sorted (exact ticker match > Swedish stocks > alphabetical)
8. Results cached with timestamp
9. ViewModel emits SearchState.Success(results)
10. Adapter updates AutoCompleteTextView dropdown

**State Management Pattern:**

```
UI State: UiState<T> (Loading | Success<T> | Error)
  ↑
ViewModel emits via StateFlow
  ↑
Repository provides data
  ↑
DAO/Service queries database/API
```

## Key Abstractions

**AlertRule (Sealed Class):**
- Purpose: Represents all types of alert conditions in a type-safe manner
- Examples: `AlertRule.PairSpread`, `AlertRule.SinglePrice`, `AlertRule.SingleDrawdownFromHigh`, `AlertRule.SingleDailyMove`, `AlertRule.SingleKeyMetric`
- Pattern: Sealed class with data class variants for each alert type
- Location: `app/src/main/java/com/stockflip/AlertRule.kt`

**WatchType (Sealed Class):**
- Purpose: Polymorphic watch item type that can be stored as JSON in Room database
- Examples: `WatchType.PricePair`, `WatchType.PriceTarget`, `WatchType.KeyMetrics`, `WatchType.ATHBased`, `WatchType.PriceRange`, `WatchType.DailyMove`, `WatchType.Combined`
- Pattern: Sealed class with nested enum types for directions and metric types
- Location: `app/src/main/java/com/stockflip/WatchType.kt`
- Persistence: Converted to/from JSON via WatchTypeConverter for Room

**MarketSnapshot:**
- Purpose: Immutable value object representing a point-in-time market data snapshot
- Contains: lastPrice, previousClose, week52High, keyMetrics map
- Pattern: Data class with factory methods (forSingleStock, forPair)
- Location: `app/src/main/java/com/stockflip/MarketSnapshot.kt`

**UiState<T> (Sealed Class):**
- Purpose: Type-safe representation of async operation states
- Variants: Loading, Success<T>, Error
- Pattern: Sealed class for exhaustive when expressions
- Location: `app/src/main/java/com/stockflip/UiState.kt`

**MarketDataService (Interface):**
- Purpose: Abstract market data source for testability and future extensibility
- Implementations: YahooMarketDataServiceImpl, FinnhubService
- Pattern: Dependency injection via constructor, singleton usage
- Location: `app/src/main/java/com/stockflip/MarketDataService.kt`

**AlertEvaluator (Object/Singleton):**
- Purpose: Pure function container for evaluating all alert types
- Public method: evaluate(rule: AlertRule, snapshotA: MarketSnapshot, snapshotB: MarketSnapshot?): Boolean
- Pattern: Object (Kotlin singleton) with no mutable state
- Location: `app/src/main/java/com/stockflip/AlertEvaluator.kt`

## Entry Points

**MainActivity:**
- Location: `app/src/main/java/com/stockflip/MainActivity.kt`
- Triggers: App launch (android:name=".MainActivity" in manifest)
- Responsibilities: Host Activity for fragments (AlertsFragment, AddStockFragment, StockDetailFragment), manage bottom navigation, request notification permissions, coordinate refresh broadcasts

**StockFlipApplication:**
- Location: `app/src/main/java/com/stockflip/StockFlipApplication.kt`
- Triggers: Process startup (android:name=".StockFlipApplication" in manifest)
- Responsibilities: Initialize WorkManager with debug logging level

**StockPriceUpdateWorker:**
- Location: `app/src/main/java/com/stockflip/StockPriceUpdateWorker.kt`
- Triggers: WorkManager periodic job (scheduled via StockMarketScheduler)
- Responsibilities: Fetch updated stock prices, evaluate alerts, show notifications, schedule next update based on market hours

**MetricHistoryUpdateWorker:**
- Location: `app/src/main/java/com/stockflip/workers/MetricHistoryUpdateWorker.kt`
- Triggers: WorkManager periodic job for metric history tracking
- Responsibilities: Fetch and store historical key metrics data

## Error Handling

**Strategy:** Exception catching at boundary layers with fallback to error states or defaults.

**Patterns:**
- Try-catch in ViewModel.loadStockPairs() → emits UiState.Error with message
- Try-catch in repository searchStocks() → emits SearchState.Error
- Try-catch in workers → returns Result.failure() and retries based on StockMarketScheduler.shouldRetry()
- Try-catch in AlertEvaluator → returns false (does not trigger alert) on evaluation error
- Log.e() throughout for debugging

## Cross-Cutting Concerns

**Logging:**
- Pattern: Log.d() for debug info, Log.w() for warnings, Log.e() for errors
- TAG constants defined in companion objects
- Example: `private const val TAG = "MainViewModel"`

**Validation:**
- WatchType and AlertRule have init {} blocks for invariant checking
- Example: `PriceRange.init { require(minPrice < maxPrice) }`
- MarketSnapshot prevents null price arithmetic

**Authentication:**
- Finnhub API key stored in buildConfig field (from local.properties)
- OkHttp interceptors available but not currently implemented in codebase
- Search services (Yahoo) use public endpoints without authentication

**Database Transactions:**
- Room handles single insert/update in coroutine context
- Batch operations may use explicit transaction if needed
- No explicit transaction demarcation visible in current code

**Concurrency:**
- Coroutines used throughout (viewModelScope, lifecycleScope)
- WorkManager provides background thread pool
- No explicit synchronization needed (StateFlow is thread-safe)
- Database access is serialized through Room's internal queue

---

*Architecture analysis: 2026-03-01*
