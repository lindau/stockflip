# Coding Conventions

**Analysis Date:** 2026-03-01

## Naming Patterns

**Files:**
- Classes: PascalCase with functional suffix (e.g., `MainActivity.kt`, `AlertEvaluator.kt`, `StockRepository.kt`)
- Test classes: Append `Test` suffix (e.g., `FinnhubServiceTest.kt`, `AlertEvaluatorTest.kt`)
- Kotlin source files named after primary class they contain
- Compose components: PascalCase (e.g., `MetricRow.kt`, `PriceRangeCard.kt`)
- UI subdirectories by type: `components/`, `theme/`, `builders/`, `presets/`, `dialogs/`

**Functions:**
- Public functions: camelCase (e.g., `getStockPrice()`, `loadWatchItems()`, `setupRecyclerView()`)
- Private functions: camelCase with verb prefix (e.g., `evaluatePairSpread()`, `performSearch()`, `observeSearchState()`)
- Suspend functions follow same naming: `suspend fun loadStockPairs()`, `suspend fun refreshWatchItems()`
- Test functions: backtick-wrapped descriptive names (e.g., `` `evaluate PairSpread should return true when spread is reached` ``)
- Composable functions: Simple noun/descriptor without `Composable` suffix (e.g., `MetricRow()`, `PriceRangeCard()`)

**Variables:**
- camelCase for all variables (e.g., `currentPrice`, `lastTriggeredDate`, `isRefreshing`)
- Private fields with underscore prefix for mutable state: `private val _uiState = MutableStateFlow<...>()`
- Boolean properties start with `is` or `has`: `isActive`, `isTriggered`, `hasKeyMetrics`, `isFastMoved`
- Constants: SCREAMING_SNAKE_CASE in `companion object` (e.g., `private const val TAG = "MainViewModel"`)
- Backing fields use underscore: `_uiState` (private), `uiState` (public read-only)

**Types:**
- Data classes: PascalCase (e.g., `WatchItem`, `AlertRule`, `MarketSnapshot`)
- Sealed classes for variants: `sealed class WatchType` with nested `object`/`class` variants
- Type aliases: PascalCase (e.g., `data class StockPair(...)`)
- Enum variants: SCREAMING_SNAKE_CASE (e.g., `PE_RATIO`, `PS_RATIO`, `DIVIDEND_YIELD`)

## Code Style

**Formatting:**
- Kotlin standard formatting (IDE default)
- 4-space indentation
- Column limit: not strictly enforced; pragmatic line wrapping at ~100-120 chars
- Blank lines between logical sections within functions
- No linting framework detected (.editorconfig, ktlint, detekt not configured)

**Linting:**
- No active linting configuration found
- Code style enforced by IDE defaults and manual review
- Exception handling and logging patterns are checked

## Import Organization

**Order:**
1. Android framework imports (`android.*`)
2. AndroidX imports (`androidx.*`)
3. Kotlin standard library imports (`kotlin.*`, `kotlinx.*`)
4. Third-party libraries (Compose, Retrofit, Room, etc.)
5. Project imports (`com.stockflip.*`)

**Pattern:**
```kotlin
import android.util.Log
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import com.squareup.retrofit2.http.GET
import com.stockflip.WatchItem
```

**Path Aliases:**
- No custom import aliases detected
- Fully qualified package imports used throughout

## Error Handling

**Patterns:**
- Try-catch blocks with appropriate scope (not overly broad)
- Exception messages logged with `Log.e(TAG, "Error message: ${e.message}")`
- Stack traces logged when needed: `Log.e(TAG, "Error...", e)`
- UI state updated on error: `_uiState.value = UiState.Error("Failed to...")`
- Errors propagated through StateFlow/sealed class results: `UiState.Error`, `SearchState.Error`
- Null-safety with elvis operator: `price ?: return false`
- Collections checked before use: `if (items.isNotEmpty())`

**Null Handling:**
```kotlin
// Typical pattern
val price = yahooFinanceService.getStockPrice(item.ticker)
if (price != null && price2 != null) {
    // Use prices
} else {
    Log.w(TAG, "Could not get prices for ${item.ticker}, keeping existing")
    return item
}
```

**Examples:**
- `MainViewModel.kt` lines 46-82: Try-catch with state updates
- `AlertEvaluator.kt` lines 26-38: Safe pattern matching with null checks
- `StockRepository.kt` lines 106-117: Network error handling with user-facing messages

## Logging

**Framework:** Android `Log` class (imported as `import android.util.Log`)

**Constants:** Each class defines `TAG` in companion object:
```kotlin
companion object {
    private const val TAG = "ClassName"
}
```

**Patterns:**
- Debug logs: `Log.d(TAG, "Loading data")` - for flow/state transitions
- Error logs: `Log.e(TAG, "Error: ${e.message}")` - for exceptions with stack trace when needed
- Warning logs: `Log.w(TAG, "Unexpected state")` - for recoverable issues
- Verbose context in logs: include ticker, item count, timestamps
- Section markers: `Log.d(TAG, "=== START operation ===")` and `Log.d(TAG, "=== END operation ===")` in long operations (e.g., `refreshWatchItems()`)

**Usage Examples:**
- `MainViewModel.kt` lines 27-41: Loading flow logging
- `AlertEvaluator.kt`: Detailed logging in evaluation chain
- Error messages always include relevant context (ticker, values, operation)

## Comments

**When to Comment:**
- Complex business logic explanation above function/block
- Non-obvious algorithm choices
- Workarounds for platform limitations (e.g., Room kapt issues in `build.gradle`)
- Important state transitions or side effects
- Do NOT comment obvious code

**JSDoc/KDoc:**
- Used for public functions and data classes in business logic
- Format: `/** description */` for single-line, multi-line for complex
- Parameter documentation included when behavior is non-obvious
- Example from `WatchItem.kt`:
  ```kotlin
  /**
   * Generic watch item that can represent different types of stock watches.
   * Replaces StockPair to support multiple watch types.
   */
  @Entity(tableName = "watch_items")
  ```

**Comment Style:**
- Use `//` for inline comments
- Use `/* */` for block comments (rare)
- Swedish comments in business logic explanations (e.g., "Spam protection fields (enligt PRD: ...)")

## Function Design

**Size:**
- Most functions 20-50 lines
- Longer functions (150+ lines) like `refreshWatchItems()` used when processing multiple similar branches within single logical operation
- Extracted when logic is reused or separate concern

**Parameters:**
- Named parameters preferred for readability
- Modifier parameters placed last in Composables
- Optional parameters use defaults: `modifier: Modifier = Modifier`, `showPrice: Boolean = true`
- No builder patterns; direct parameter lists

**Return Values:**
- Single responsibility per function return type
- StateFlow for UI state: `StateFlow<UiState<T>>`
- Flow for streams: `Flow<SearchState>`
- Sealed classes for union types: `sealed class UiState<T>`
- Suspend functions return exact type needed (not wrapped in State)
- `null` returned when value unavailable (common in service functions)

**Suspend Functions:**
- Used for long-running operations (database, network)
- Called from `viewModelScope.launch` in ViewModels
- Example pattern:
  ```kotlin
  suspend fun loadWatchItems() {
      try {
          // operation
      } catch (e: Exception) {
          // error handling
      }
  }
  ```

## Module Design

**Exports:**
- Classes are top-level in their files
- No barrel files (no `index.kt`)
- Data classes and sealed classes defined alongside their usage or in dedicated files

**Visibility:**
- Classes default to internal within package
- Data classes that are DTOs/models are public
- Service classes are public
- DAO/Repository classes are public for injection
- Helper functions marked `private` when not needed outside file

**Dependency Injection:**
- Constructor injection in ViewModels
- Example from `MainViewModel.kt`:
  ```kotlin
  class MainViewModel(
      private val stockPairDao: StockPairDao,
      private val watchItemDao: WatchItemDao,
      private val yahooFinanceService: MarketDataService
  ) : ViewModel()
  ```

## File Organization

**Typical Structure:**
```kotlin
package com.stockflip

// Standard library imports
import android.util.Log
import androidx.lifecycle.ViewModel

// AndroidX imports
import androidx.lifecycle.viewModelScope

// Third-party
import kotlinx.coroutines.flow.StateFlow

// Project imports
import com.stockflip.WatchItem

// Main class/object
class/object/sealed class ClassName

// Companion object with constants
companion object {
    private const val TAG = "ClassName"
}

// Private helper functions at end
private fun helperFunction() { }
```

## Kotlin-Specific Patterns

**Data Classes:**
- Used for entities: `@Entity data class WatchItem(...)`
- Used for sealed class variants
- Include copy() method automatically generated
- Example from `WatchItem.kt`: 304-line data class with @Ignore fields for computed values

**Scope Functions:**
- `.let` and `.apply` used sparingly
- Direct variable assignment preferred
- Example: `val updatedItem = item.withCurrentPrices(price1, price2)`

**Extension Functions:**
- Used for utility methods on existing types
- Named consistently with regular functions
- Example: `item.withCurrentPrices()`, `item.getDisplayName()`

**Sealed Classes:**
- Used for state management: `sealed class UiState<T>`
- Used for alert type variants: `sealed class AlertRule`
- Pattern allows exhaustive when() expressions

---

*Convention analysis: 2026-03-01*
