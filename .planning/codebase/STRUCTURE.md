# Codebase Structure

**Analysis Date:** 2026-03-01

## Directory Layout

```
StockFlip/
├── app/
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/com/stockflip/
│   │   │   │   ├── (Root package - core domain & data models)
│   │   │   │   ├── repository/          # Data access abstraction layer
│   │   │   │   ├── ui/                  # UI layer (Fragments, Compose, theme)
│   │   │   │   │   ├── components/      # Reusable Compose components
│   │   │   │   │   │   ├── cards/       # Card components for watch types
│   │   │   │   │   │   └── dialogs/     # Dialog components
│   │   │   │   │   ├── presets/         # UI preset configurations
│   │   │   │   │   ├── theme/           # Material theme definitions
│   │   │   │   │   └── SwipeToDeleteCallback.kt
│   │   │   │   ├── viewmodel/           # ViewModel classes
│   │   │   │   ├── usecase/             # Business logic use cases
│   │   │   │   └── workers/             # Background job workers
│   │   │   ├── res/
│   │   │   │   ├── layout/              # XML layouts for Activities/Fragments
│   │   │   │   ├── values/              # Strings, colors, dimens
│   │   │   │   ├── drawable/            # Vector drawables, icons
│   │   │   │   ├── menu/                # Menu definitions
│   │   │   │   └── xml/                 # XML configs (work manager schedule)
│   │   │   └── AndroidManifest.xml
│   │   ├── test/
│   │   │   └── java/com/stockflip/
│   │   │       ├── repository/          # Repository unit tests
│   │   │       ├── usecase/             # Use case unit tests
│   │   │       ├── viewmodel/           # ViewModel unit tests
│   │   │       ├── testutil/            # Test helpers (FakeMarketDataService, etc.)
│   │   │       └── *Test.kt files       # Individual test classes
│   │   └── androidTest/
│   │       └── java/com/stockflip/      # Instrumented Android tests
│   ├── build.gradle
│   └── build/                           # Generated build artifacts
├── gradle/                              # Gradle wrapper
├── build.gradle
├── settings.gradle
├── gradlew
├── local.properties                    # API keys (not committed)
└── .planning/
    └── codebase/                        # This directory (GSD documentation)
```

## Directory Purposes

**`app/src/main/java/com/stockflip/` (Root Package):**
- Purpose: Core domain models, entities, services, and main activities
- Contains: Entity classes (WatchItem, StockPair, MetricHistoryEntity), sealed classes (WatchType, AlertRule, UiState), services (MarketDataService implementations, FinnhubService, AlertEvaluator), Main activity and fragments, Adapters for RecyclerViews
- Key files: MainViewModel.kt, MainActivity.kt, AlertsFragment.kt, StockDetailFragment.kt, AddStockFragment.kt

**`app/src/main/java/com/stockflip/repository/`:**
- Purpose: Data access abstraction and caching logic
- Contains: StockRepository (search caching), MetricHistoryRepository, SearchState, StockSearchService interface and YahooStockSearchService implementation
- Key files: `StockRepository.kt`, `MetricHistoryRepository.kt`, `StockSearchService.kt`

**`app/src/main/java/com/stockflip/ui/`:**
- Purpose: User interface layer with fragments and compose components
- Contains: Fragment definitions, Compose UI components, theme and style definitions, dialog implementations
- Subdirectories: components/ (reusable UI units), theme/ (Material Design configuration), presets/ (UI constant configurations)

**`app/src/main/java/com/stockflip/ui/components/`:**
- Purpose: Reusable Compose components and adapters
- Contains: Cards (PriceTargetCard, PriceRangeCard, DailyMoveCard, etc.), helper components (StatusStripe, StockSummaryRow), adapters (ConditionBuilderAdapter, GroupedWatchItemAdapter)
- Key files: `cards/PriceTargetCard.kt`, `cards/PriceRangeCard.kt`, `cards/DailyMoveCard.kt`, `cards/CombinedAlertCard.kt`

**`app/src/main/java/com/stockflip/ui/components/dialogs/`:**
- Purpose: Dialog implementations for editing watch items
- Contains: EditAlertDialog.kt for modal editing of watch item conditions

**`app/src/main/java/com/stockflip/ui/theme/`:**
- Purpose: Material 3 theme configuration
- Contains: Color.kt, Shape.kt, Type.kt, Theme.kt
- Key files: All Material Design tokens defined here

**`app/src/main/java/com/stockflip/ui/presets/`:**
- Purpose: Preset configurations and constants for UI state
- Contains: MetricPresets.kt with predefined metric values
- Key files: `MetricPresets.kt`

**`app/src/main/java/com/stockflip/viewmodel/`:**
- Purpose: ViewModels for state management
- Contains: StockSearchViewModel for search operations
- Key files: `StockSearchViewModel.kt`

**`app/src/main/java/com/stockflip/usecase/`:**
- Purpose: Business logic orchestration for specific operations
- Contains: UpdateStockPairsPricesUseCase (fetches and updates prices)
- Key files: `UpdateStockPairsPricesUseCase.kt`

**`app/src/main/java/com/stockflip/workers/`:**
- Purpose: Background workers for WorkManager scheduled tasks
- Contains: MetricHistoryUpdateWorker for periodic metric data collection
- Key files: `MetricHistoryUpdateWorker.kt`

**`app/src/main/res/layout/`:**
- Purpose: XML layout files for Activities and Fragments
- Contains: activity_main.xml, fragment_alerts.xml, fragment_add_stock.xml, fragment_stock_detail.xml, and dialog XML files
- Key files: `activity_main.xml`, `fragment_alerts.xml`, `item_watch_item.xml`, `dialog_add_price_target.xml`

**`app/src/main/res/values/`:**
- Purpose: String, dimension, color, and style resources
- Contains: strings.xml (localized text), dimens.xml (spacing constants), attrs.xml (custom attributes)

**`app/src/main/res/drawable/`:**
- Purpose: Vector drawables and icon resources
- Contains: SVG icons (ic_add.xml, ic_delete.xml, ic_search.xml, ic_paid.xml, etc.)

**`app/src/test/`:**
- Purpose: Unit tests (JVM only, no Android framework)
- Contains: AlertEvaluatorTest, WatchTypeConverterTest, AlertRuleConverterTest, StockRepositoryTest, StockSearchViewModelTest, YahooFinanceServiceTest, UpdateStockPairsPricesUseCaseTest, WatchItemTest, MarketSnapshotTest
- Test utilities: FakeMarketDataService.kt, InMemoryDaos.kt, MainDispatcherRule.kt

**`app/src/androidTest/`:**
- Purpose: Instrumented Android tests (need device/emulator)
- Contains: Tests that require Android framework access

## Key File Locations

**Entry Points:**
- `app/src/main/java/com/stockflip/MainActivity.kt`: Main activity hosting all fragments, bottom navigation controller
- `app/src/main/java/com/stockflip/StockFlipApplication.kt`: Application class, WorkManager initialization
- `app/src/main/AndroidManifest.xml`: Application manifest with activities, services, permissions

**Core Configuration:**
- `app/build.gradle`: Dependencies, build configuration, Room schema export, API key loading
- `local.properties`: Local configuration (contains FINNHUB_API_KEY, not committed)
- `app/src/main/res/values/strings.xml`: UI strings
- `app/src/main/res/values/colors.xml`: Color definitions

**Database:**
- `app/src/main/java/com/stockflip/StockPairDatabase.kt`: Room database definition with migrations
- `app/src/main/java/com/stockflip/StockPairDao.kt`: DAO for StockPair entity
- `app/src/main/java/com/stockflip/WatchItemDao.kt`: DAO for WatchItem entity
- `app/src/main/java/com/stockflip/MetricHistoryDao.kt`: DAO for MetricHistoryEntity

**Core Models:**
- `app/src/main/java/com/stockflip/WatchItem.kt`: Main watch item entity with polymorphic WatchType field
- `app/src/main/java/com/stockflip/WatchType.kt`: Sealed class for watch item type variants
- `app/src/main/java/com/stockflip/AlertRule.kt`: Sealed class for alert evaluation rules
- `app/src/main/java/com/stockflip/StockPair.kt`: Legacy pair watch entity
- `app/src/main/java/com/stockflip/MetricHistoryEntity.kt`: Historical metric data storage

**Business Logic:**
- `app/src/main/java/com/stockflip/AlertEvaluator.kt`: Pure alert evaluation logic
- `app/src/main/java/com/stockflip/MarketSnapshot.kt`: Market data value object
- `app/src/main/java/com/stockflip/usecase/UpdateStockPairsPricesUseCase.kt`: Price update orchestration

**ViewModels:**
- `app/src/main/java/com/stockflip/MainViewModel.kt`: Main ViewModel for watch items and stock pairs
- `app/src/main/java/com/stockflip/StockDetailViewModel.kt`: Detail view for single stock
- `app/src/main/java/com/stockflip/viewmodel/StockSearchViewModel.kt`: Search dropdown ViewModel

**Fragments:**
- `app/src/main/java/com/stockflip/MainActivity.kt`: Host activity
- `app/src/main/java/com/stockflip/AlertsFragment.kt`: Alerts/watch items list tab
- `app/src/main/java/com/stockflip/AddStockFragment.kt`: Add new watch item tab
- `app/src/main/java/com/stockflip/StockDetailFragment.kt`: Single stock detail view

**Services/External Data:**
- `app/src/main/java/com/stockflip/MarketDataService.kt`: Interface for market data access
- `app/src/main/java/com/stockflip/YahooFinanceService.kt`: Yahoo Finance implementation
- `app/src/main/java/com/stockflip/YahooMarketDataServiceImpl.kt`: MarketDataService adapter for Yahoo
- `app/src/main/java/com/stockflip/FinnhubService.kt`: Finnhub API implementation

**Repository:**
- `app/src/main/java/com/stockflip/repository/StockRepository.kt`: Stock search with caching
- `app/src/main/java/com/stockflip/repository/MetricHistoryRepository.kt`: Metric history data access

**Workers:**
- `app/src/main/java/com/stockflip/StockPriceUpdateWorker.kt`: Periodic price update worker
- `app/src/main/java/com/stockflip/workers/MetricHistoryUpdateWorker.kt`: Periodic metric history worker

**UI Components:**
- `app/src/main/java/com/stockflip/ui/components/cards/PriceTargetCard.kt`: Compose card for price target watches
- `app/src/main/java/com/stockflip/ui/components/cards/PriceRangeCard.kt`: Price range watch card
- `app/src/main/java/com/stockflip/ui/components/cards/DailyMoveCard.kt`: Daily move watch card
- `app/src/main/java/com/stockflip/ui/components/cards/CombinedAlertCard.kt`: Combined expression card
- `app/src/main/java/com/stockflip/ui/components/dialogs/EditAlertDialog.kt`: Modal dialog for editing watches

## Naming Conventions

**Files:**
- Kotlin files: PascalCase (e.g., `MainActivity.kt`, `WatchItem.kt`, `PriceTargetCard.kt`)
- XML layout files: snake_case with prefix for type (e.g., `activity_main.xml`, `dialog_add_price_target.xml`, `item_watch_item.xml`)
- XML drawable files: snake_case with ic_ prefix (e.g., `ic_add.xml`, `ic_delete.xml`)

**Directories:**
- Package directories: lowercase (e.g., `com/stockflip/repository/`, `com/stockflip/ui/components/`)
- Resource directories: lowercase with qualifiers (e.g., `values/`, `values-night/`, `drawable/`, `mipmap-xhdpi/`)

**Classes:**
- Activities: Suffix with Activity (e.g., `MainActivity`)
- Fragments: Suffix with Fragment (e.g., `AlertsFragment`, `AddStockFragment`)
- ViewModels: Suffix with ViewModel (e.g., `MainViewModel`, `StockSearchViewModel`)
- Adapters: Suffix with Adapter (e.g., `AlertAdapter`, `StockPairAdapter`, `ConditionBuilderAdapter`)
- Services/Implementations: Service name with optional Impl suffix (e.g., `YahooFinanceService`, `YahooMarketDataServiceImpl`)
- DAO interfaces: Suffix with Dao (e.g., `StockPairDao`, `WatchItemDao`)
- Workers: Suffix with Worker (e.g., `StockPriceUpdateWorker`)

**Properties:**
- Private properties: Leading underscore for data binding instances (e.g., `_binding`, `_uiState`)
- Immutable exposed properties: camelCase without underscore (e.g., `uiState`, `watchItemUiState`)
- Constants in companion object: UPPER_SNAKE_CASE (e.g., `TAG`, `PRICE_EQUALITY_THRESHOLD`)

**Methods/Functions:**
- camelCase starting with lowercase (e.g., `loadStockPairs()`, `refreshStockPairs()`, `canTrigger()`)

## Where to Add New Code

**New Feature (e.g., new watch type):**
- Primary code: `app/src/main/java/com/stockflip/WatchType.kt` (add new sealed subclass)
- Business logic: `app/src/main/java/com/stockflip/AlertEvaluator.kt` (add evaluation method)
- UI rendering: `app/src/main/java/com/stockflip/ui/components/cards/` (create new card component)
- Dialog: `app/src/main/java/com/stockflip/ui/components/dialogs/` (create dialog if needed)
- Tests: `app/src/test/java/com/stockflip/` (unit tests for logic)

**New ViewModel:**
- Implementation: `app/src/main/java/com/stockflip/viewmodel/YourViewModel.kt` or in root package if associated with main activity
- State model: Define `StateFlow<UiState<YourDataType>>` in the ViewModel
- Test: `app/src/test/java/com/stockflip/viewmodel/YourViewModelTest.kt`

**New Fragment:**
- Implementation: `app/src/main/java/com/stockflip/YourFragment.kt`
- Layout: `app/src/main/res/layout/fragment_your_feature.xml`
- If adding navigation tab: Update MainActivity bottom navigation menu

**New Service/Integration:**
- Interface: `app/src/main/java/com/stockflip/YourService.kt` (if creating abstraction)
- Implementation: `app/src/main/java/com/stockflip/YourServiceImpl.kt` or `YourExternalService.kt`
- Dependency injection: Pass via constructor to ViewModel or Use Case

**New Use Case:**
- Implementation: `app/src/main/java/com/stockflip/usecase/YourUseCase.kt`
- Constructor dependencies: Inject DAO and service instances
- Return type: Match use case pattern (List, Unit, or domain model)
- Test: `app/src/test/java/com/stockflip/usecase/YourUseCaseTest.kt`

**New Worker (background task):**
- Implementation: `app/src/main/java/com/stockflip/workers/YourWorker.kt`
- Extend CoroutineWorker (not Worker)
- Override doWork() suspend function
- Return Result.success(), Result.failure(), or Result.retry()
- Scheduling: Add to StockMarketScheduler or MainActivity initialization

**New UI Component (Composable):**
- Card component: `app/src/main/java/com/stockflip/ui/components/cards/YourCard.kt`
- Dialog component: `app/src/main/java/com/stockflip/ui/components/dialogs/YourDialog.kt`
- Helper component: `app/src/main/java/com/stockflip/ui/components/YourComponent.kt`
- Signature: Include modifiers, callbacks, and preview function
- Theme: Use MaterialTheme colors and typography from `app/src/main/java/com/stockflip/ui/theme/`

**Utilities/Helpers:**
- Formatting: `app/src/main/java/com/stockflip/` (e.g., `CurrencyHelper.kt`, `CountryFlagHelper.kt`, `SortHelper.kt`)
- Converters: `app/src/main/java/com/stockflip/` (e.g., `WatchTypeConverter.kt`, `AlertRuleConverter.kt`)

## Special Directories

**`app/build/`:**
- Purpose: Generated build artifacts
- Generated: Yes (gradle build output)
- Committed: No (.gitignore)
- Contains: Compiled classes, resources, generated Room code, data binding classes

**`app/schemas/`:**
- Purpose: Room database schema JSON files for migration verification
- Generated: Yes (kapt from @Database)
- Committed: Yes (tracked for schema versioning)
- Contains: JSON schema for each database version number

**`app/src/main/res/xml/`:**
- Purpose: XML configuration files for WorkManager and other system services
- Contains: WorkManager periodic job definitions (e.g., work_request_schedule.xml if needed)

**`.planning/codebase/`:**
- Purpose: GSD documentation directory
- Generated: No (manually created by GSD mappers)
- Committed: Yes (tracked for team reference)
- Contains: ARCHITECTURE.md, STRUCTURE.md, CONVENTIONS.md, TESTING.md, CONCERNS.md, STACK.md, INTEGRATIONS.md

---

*Structure analysis: 2026-03-01*
