# Testing Patterns

**Analysis Date:** 2026-03-01

## Test Framework

**Runner:**
- JUnit 4 (configured in `app/build.gradle` as `testImplementation 'junit:junit:4.13.2'`)
- Espresso for UI testing (Android instrumentation tests)
- Robolectric for unit tests with Android dependencies
- Config: No dedicated config file; uses Android Gradle plugin defaults

**Assertion Library:**
- JUnit Assert: `assertEquals()`, `assertTrue()`, `assertFalse()`
- Hamcrest matchers: `containsString()`, `not()`, `TypeSafeMatcher`
- Turbine for Flow testing: `flow.test { awaitItem(), awaitComplete() }`
- Custom matchers: `hasDropDownItems()` in `MainActivityTest.kt`

**Run Commands:**
```bash
# Run all unit tests
./gradlew test

# Run instrumentation/Android tests
./gradlew connectedAndroidTest

# Run specific test class
./gradlew test --tests "com.stockflip.AlertEvaluatorTest"

# Run with detailed output
./gradlew test --info
```

## Test File Organization

**Location:**
- Unit tests: `app/src/test/java/com/stockflip/` (standard JUnit)
- Android instrumentation tests: `app/src/androidTest/java/com/stockflip/` (Espresso)
- Shared test utilities: `app/src/test/java/com/stockflip/testutil/`

**Naming:**
- Test files: `[ClassName]Test.kt`
- Example: `AlertEvaluatorTest.kt` tests `AlertEvaluator.kt`
- Test utilities: Descriptive names (e.g., `MainDispatcherRule.kt`, `InMemoryDaos.kt`)

**Structure:**
```
app/src/test/java/com/stockflip/
├── AlertEvaluatorTest.kt
├── FinnhubServiceTest.kt
├── MarketSnapshotTest.kt
├── YahooFinanceServiceTest.kt
├── repository/
│   └── StockRepositoryTest.kt
├── viewmodel/
│   └── StockSearchViewModelTest.kt
├── usecase/
│   └── UpdateStockPairsPricesUseCaseTest.kt
└── testutil/
    ├── MainDispatcherRule.kt
    └── InMemoryDaos.kt

app/src/androidTest/java/com/stockflip/
├── MainActivityTest.kt
├── NotificationTest.kt
├── StockPriceUpdateWorkerTest.kt
└── TestStockPriceUpdateWorker.kt
```

## Test Structure

**Suite Organization:**
```kotlin
@OptIn(ExperimentalCoroutinesApi::class, ExperimentalTime::class)
class AlertEvaluatorTest {

    @Test
    fun `evaluate PairSpread should return true when spread is reached`() {
        // Given
        val rule = AlertRule.PairSpread(...)
        val snapshot = MarketSnapshot.forPair(100.0, 90.0)

        // When
        val result = AlertEvaluator.evaluate(rule, snapshot)

        // Then
        assertTrue("Should trigger when spread is reached", result)
    }
}
```

**Patterns:**
- Given-When-Then structure (as comments)
- One assertion per test (usually)
- Descriptive test names as backtick-wrapped strings
- Multiple tests for positive/negative cases
- Setup done in `@Before` method when shared

**Test Utilities:**
- `@Before` for setup
- `@After` for cleanup
- `@Rule` for JUnit TestRules (e.g., `MainDispatcherRule`)
- `runTest { }` for coroutine tests
- `runBlocking { }` for blocking coroutine execution

## Mocking

**Framework:**
- MockK (configured as `testImplementation 'io.mockk:mockk:1.13.9'`)
- MockK agent: `testImplementation 'io.mockk:mockk-agent-jvm:1.13.9'`
- OkHttp MockWebServer: `testImplementation 'com.squareup.okhttp3:mockwebserver:4.12.0'`

**Patterns:**
```kotlin
// Mock a dependency
private lateinit var repository: StockRepository

@Before
fun setup() {
    repository = mockk()  // Create mock
}

// Setup mock behavior
coEvery { repository.searchStocks(any(), any()) } returns results

// Verify calls were made
coVerify(exactly = 1) { stockSearchService.searchStocks(any(), any()) }

// Mock static Log class
@Before
fun setup() {
    mockkStatic(Log::class)
    every { Log.d(any(), any()) } returns 0
    every { Log.e(any(), any()) } returns 0
}

@After
fun tearDown() {
    unmockkStatic(Log::class)
}
```

**What to Mock:**
- External service interfaces: `StockSearchService`, `MarketDataService`, `YahooFinanceService`
- Database DAOs: `StockPairDao`, `WatchItemDao`
- Static utilities when needed: `Log` class in tests (to suppress output)
- Flow providers: `repository.searchStocks()` returns mocked Flow

**What NOT to Mock:**
- Sealed classes (use actual instances): `AlertRule.PairSpread(...)`
- Data classes (use actual instances): `MarketSnapshot.forPair(...)`
- Business logic classes that are under test: `AlertEvaluator`, `StockRepository`
- Suspend functions being tested (but mock their dependencies)

**Examples from Tests:**
- `StockRepositoryTest.kt` lines 53-58: Mocking network service
- `StockSearchViewModelTest.kt` lines 48-60: Mocking repository with Flow
- `AlertEvaluatorTest.kt`: No mocking (pure logic testing with real objects)

## Fixtures and Factories

**Test Data:**
Factory methods on companion objects or helper functions:
```kotlin
// From MarketSnapshot (used in tests)
val snapshot = MarketSnapshot.forPair(100.0, 90.0)  // Diff = 10.0
val snapshot = MarketSnapshot.forSingleStock(
    lastPrice = 90.0,
    previousClose = null,
    week52High = 100.0
)
```

Inline test data creation:
```kotlin
// From StockRepositoryTest
val results = listOf(
    StockSearchResult("VOLV-B.ST", "Volvo B (Stockholmsbörsen)", true)
)
```

**Location:**
- Fixtures in `app/src/test/java/com/stockflip/testutil/`
- Example: `InMemoryDaos.kt` - provides in-memory database instances for testing
- Factory methods on domain classes: `MarketSnapshot.forPair()`, `MarketSnapshot.forSingleStock()`
- Inline in test files when specific to single test

## Coverage

**Requirements:**
- No coverage thresholds enforced in build configuration
- Coverage measured manually when needed
- Focus on critical paths: alert evaluation, data loading, error handling

**View Coverage:**
```bash
# Generate coverage report (requires plugin)
./gradlew testDebugUnitTestCoverage

# View HTML report
open app/build/reports/coverage/debug/index.html
```

## Test Types

**Unit Tests:**
- Scope: Single class in isolation (e.g., `AlertEvaluator`, `StockRepository`)
- Location: `app/src/test/java/com/stockflip/`
- Approach: Mock external dependencies, test business logic directly
- Example: `AlertEvaluatorTest.kt` (13 tests for evaluation logic)
- Runs without Android context (Robolectric when needed)

**Integration Tests:**
- Scope: Multiple components (ViewModel + Repository, Repository + Service)
- Location: `app/src/test/java/com/stockflip/` (use Robolectric when needed)
- Approach: Mock network calls, test data flow through layers
- Example: `StockRepositoryTest.kt` tests caching + sorting + error handling

**Android/UI Tests (Instrumentation):**
- Scope: Activities and UI interactions
- Location: `app/src/androidTest/java/com/stockflip/`
- Framework: Espresso
- Example: `MainActivityTest.kt` - tests search input, dropdown visibility
- Annotation: `@RunWith(AndroidJUnit4::class)`, `@LargeTest`

**Coroutine Tests:**
- Framework: `kotlinx-coroutines-test`
- Pattern: `runTest { }` for structured concurrency tests
- MainDispatcherRule: Configures test dispatcher for all tests in class
- Example from `StockSearchViewModelTest.kt`:
  ```kotlin
  @get:Rule
  val mainDispatcherRule: MainDispatcherRule = MainDispatcherRule()

  @Test
  fun `test successful search`() = runTest {
      // Test coroutine code
  }
  ```

**Flow/Stream Tests:**
- Framework: Turbine (configured as `testImplementation 'app.cash.turbine:turbine:1.0.0'`)
- Pattern: `flow.test { awaitItem(), awaitComplete() }`
- Example from `StockRepositoryTest.kt`:
  ```kotlin
  repository.searchStocks(query).test {
      assertEquals(SearchState.Loading, awaitItem())
      assertEquals(SearchState.Success(results), awaitItem())
      awaitComplete()
  }
  ```

## Common Patterns

**Async Testing:**
```kotlin
// Coroutine test
@Test
fun `test async operation`() = runTest {
    val result = viewModel.loadData()  // suspend function
    advanceUntilIdle()
    assertEquals(expected, result)
}

// Flow test
@Test
fun `test flow emission`() = runTest {
    repository.searchStocks(query).test {
        assertEquals(SearchState.Loading, awaitItem())
        assertEquals(SearchState.Success(results), awaitItem())
        awaitComplete()
    }
}
```

**Error Testing:**
```kotlin
// Exception thrown by mock
coEvery { stockSearchService.searchStocks(any(), any()) } throws Exception("Network error")

// Verify error state
repository.searchStocks(query).test {
    assertEquals(SearchState.Loading, awaitItem())
    val error = awaitItem() as SearchState.Error
    assertEquals("Network error", error.message)
    awaitComplete()
}

// From AlertEvaluatorTest - test with null values
@Test
fun `evaluate SingleDrawdownFromHigh should return false when week52High is null`() {
    val rule = AlertRule.SingleDrawdownFromHigh(...)
    val snapshot = MarketSnapshot.forSingleStock(90.0, null, null)

    val result = AlertEvaluator.evaluate(rule, snapshot)

    assertFalse("Should not trigger when week52High is null", result)
}
```

**State Testing:**
```kotlin
// Test state transitions in ViewModel
@Test
fun `loadWatchItems sets Loading state then Success`() = runTest {
    viewModel.loadWatchItems()
    advanceUntilIdle()

    val state = viewModel.watchItemUiState.value
    assertTrue(state is UiState.Success)
}
```

**API/Network Testing:**
```kotlin
// Using MockWebServer
val mockWebServer = MockWebServer()
mockWebServer.enqueue(MockResponse().setBody(jsonResponse))
mockWebServer.start()

val retrofit = Retrofit.Builder()
    .baseUrl(mockWebServer.url("/").toString())
    .build()
```

## Test Comments

**API Key Requirements:**
- FinnhubService tests require `FINNHUB_API_KEY` in `local.properties`
- Tests gracefully handle missing API key (print warnings, don't fail)
- Example from `FinnhubServiceTest.kt`:
  ```kotlin
  @Before
  fun checkApiKey() {
      println("Running FinnhubService tests. Make sure FINNHUB_API_KEY is set in local.properties")
  }

  @Test
  fun `getKeyMetric should return PE ratio...`() = runBlocking {
      val peRatio = FinnhubService.getKeyMetric(symbol, metricType)
      if (peRatio != null) {
          assertTrue("P/E ratio should be greater than 0", peRatio > 0.0)
      } else {
          println("⚠ P/E ratio not available...")
      }
  }
  ```

## Debugging Tests

**Enable Logging in Tests:**
```kotlin
@Before
fun setup() {
    // For Robolectric tests
    ShadowLog.stream = System.out

    // Mock Log to suppress framework logs
    mockkStatic(Log::class)
    every { Log.d(any(), any()) } returns 0
}
```

**Test Output:**
- Tests print diagnostic output (✓ checkmarks, ⚠ warnings) when APIs are available
- Failures show assertion messages from `assertTrue/assertEquals`
- Gradle test task shows summary of passed/failed tests

---

*Testing analysis: 2026-03-01*
