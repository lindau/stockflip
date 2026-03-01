package com.stockflip

import android.app.Application
import android.content.Context
import android.view.ContextThemeWrapper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import com.stockflip.databinding.ActivityMainBinding
import com.stockflip.repository.SearchState
import com.stockflip.repository.StockRepository
import com.stockflip.StockSearchResult
import com.stockflip.viewmodel.StockSearchViewModel
import io.mockk.*
import io.mockk.impl.annotations.MockK
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLog
import org.robolectric.shadows.ShadowToast
import kotlin.time.ExperimentalTime

@ExperimentalCoroutinesApi
@ExperimentalTime
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [34], manifest = Config.NONE)
@Ignore("Needs rewrite: ViewBinding/ViewModelProvider mocking is brittle in Robolectric with targetSdk 35.")
class MainActivityTest {
    @MockK
    private lateinit var application: Application
    
    @MockK
    private lateinit var context: Context
    
    @MockK
    private lateinit var stockPairDao: StockPairDao
    
    @MockK
    private lateinit var yahooFinanceService: YahooFinanceService
    
    @MockK
    private lateinit var mainViewModel: MainViewModel
    
    @MockK
    private lateinit var stockSearchViewModel: StockSearchViewModel
    
    private lateinit var binding: ActivityMainBinding
    private lateinit var rootView: ConstraintLayout
    private lateinit var mockBuilder: MaterialAlertDialogBuilder
    private lateinit var uiStateFlow: MutableStateFlow<UiState<List<StockPair>>>
    private lateinit var layoutInflater: LayoutInflater
    private lateinit var lifecycle: Lifecycle
    private lateinit var activity: MainActivity
    private lateinit var scenario: ActivityScenario<MainActivity>
    private lateinit var repository: StockRepository
    private val testDispatcher = StandardTestDispatcher()
    private lateinit var database: StockPairDatabase
    private lateinit var dao: StockPairDao
    private lateinit var dao2: StockPairDao
    private lateinit var binding2: ActivityMainBinding
    private lateinit var rootView2: ConstraintLayout
    private lateinit var layoutInflater2: LayoutInflater
    private lateinit var lifecycle2: Lifecycle
    private lateinit var activity2: MainActivity

    @Before
    fun setup() {
        MockKAnnotations.init(this)
        mockkStatic(Toast::class)
        mockkStatic(ViewModelProvider::class)
        mockkStatic(StockPairDatabase::class)
        
        // Set up logging
        ShadowLog.stream = System.out
        
        // Set up UI state flow
        uiStateFlow = MutableStateFlow(UiState.Loading)
        every { mainViewModel.uiState } returns uiStateFlow
        
        // Inflate a real binding; view fields are final and not mock-friendly
        val themedContext: Context = ContextThemeWrapper(ApplicationProvider.getApplicationContext(), R.style.Theme_StockFlip)
        layoutInflater = LayoutInflater.from(themedContext)
        binding = ActivityMainBinding.inflate(layoutInflater)
        rootView = binding.root
        
        // Mock ViewModelProvider.Factory for MainViewModel
        val mainViewModelFactory = mockk<ViewModelProvider.Factory>()
        every { mainViewModelFactory.create(MainViewModel::class.java) } returns mainViewModel
        every { ViewModelProvider(any<androidx.lifecycle.ViewModelStoreOwner>(), mainViewModelFactory)[MainViewModel::class.java] } returns mainViewModel
        
        // Mock ViewModelProvider.Factory for StockSearchViewModel
        val stockSearchViewModelFactory = mockk<ViewModelProvider.Factory>()
        every { stockSearchViewModelFactory.create(StockSearchViewModel::class.java) } returns stockSearchViewModel
        every { ViewModelProvider(any<androidx.lifecycle.ViewModelStoreOwner>(), stockSearchViewModelFactory)[StockSearchViewModel::class.java] } returns stockSearchViewModel
        
        // Mock database
        val database = mockk<StockPairDatabase>()
        every { StockPairDatabase.getDatabase(any()) } returns database
        every { database.stockPairDao() } returns stockPairDao
        
        // Mock activity
        activity = spyk(MainActivity())
        every { activity getProperty "binding" } returns binding
        
        // Mock notification permission launcher
        val notificationLauncher = mockk<ActivityResultLauncher<String>>(relaxed = true)
        every { activity getProperty "notificationPermissionLauncher" } returns notificationLauncher
        
        // Mock price update receiver
        val priceUpdateReceiver = mockk<PriceUpdateReceiver>(relaxed = true)
        every { activity getProperty "priceUpdateReceiver" } returns priceUpdateReceiver
        
        // Mock MaterialAlertDialogBuilder
        mockBuilder = mockk(relaxed = true)
        mockkConstructor(MaterialAlertDialogBuilder::class)
        every { anyConstructed<MaterialAlertDialogBuilder>().setView(any<View>()) } returns mockBuilder
        every { anyConstructed<MaterialAlertDialogBuilder>().setTitle(any<CharSequence>()) } returns mockBuilder
        every { anyConstructed<MaterialAlertDialogBuilder>().setPositiveButton(any<CharSequence>(), any()) } returns mockBuilder
        every { anyConstructed<MaterialAlertDialogBuilder>().setNegativeButton(any<CharSequence>(), any()) } returns mockBuilder
        every { anyConstructed<MaterialAlertDialogBuilder>().show() } returns mockk()
        
        // Mock layout inflater
        layoutInflater = mockk(relaxed = true)
        every { ActivityMainBinding.inflate(layoutInflater) } returns binding
        every { ActivityMainBinding.inflate(any<LayoutInflater>()) } returns binding
        
        // Mock StockPriceUpdater
        mockkObject(StockPriceUpdater)
        every { StockPriceUpdater.startPeriodicUpdate(any()) } just Runs
        every { StockPriceUpdater.requestBatteryOptimizationExemption(any()) } just Runs
        
        // Mock activity
        lifecycle = mockk(relaxed = true)
        mockkConstructor(MainActivity::class)
        every { anyConstructed<MainActivity>().layoutInflater } returns layoutInflater
        every { anyConstructed<MainActivity>().getSystemService(Context.LAYOUT_INFLATER_SERVICE) } returns layoutInflater
        every { anyConstructed<MainActivity>().applicationContext } returns application
        every { anyConstructed<MainActivity>().lifecycle } returns lifecycle
        
        // Mock ViewModelProvider.Factory for MainActivity
        val mainActivityViewModelFactory = mockk<ViewModelProvider.Factory>(relaxed = true) {
            every { create(MainViewModel::class.java) } returns mainViewModel
        }
        every { anyConstructed<MainActivity>().defaultViewModelProviderFactory } returns mainActivityViewModelFactory
        
        // Launch activity
        try {
            scenario = ActivityScenario.launch(MainActivity::class.java)
            scenario.moveToState(Lifecycle.State.RESUMED)
        } catch (e: Exception) {
            throw RuntimeException("Failed to launch activity: ${e.message}", e)
        }
    }

    @After
    fun tearDown() {
        try {
            scenario.close()
        } catch (e: Exception) {
            // Ignore activity close errors
        }
        
        Dispatchers.resetMain()
        unmockkStatic(Log::class)
        unmockkStatic(ActivityMainBinding::class)
        unmockkObject(ViewModelProvider.AndroidViewModelFactory.Companion)
        unmockkObject(StockPairDatabase.Companion)
        unmockkObject(YahooFinanceService)
        unmockkObject(StockPriceUpdater)
        unmockkConstructor(MaterialAlertDialogBuilder::class)
        unmockkConstructor(PriceUpdateReceiver::class)
        unmockkConstructor(MainActivity::class)
        unmockkAll()
    }

    @Test
    fun `test stock search setup`() = runTest {
        scenario.onActivity { activity ->
            // Mock dependencies
            val adapter = mockk<ArrayAdapter<StockSearchResult>>(relaxed = true)
            val input = mockk<MaterialAutoCompleteTextView>(relaxed = true)
            
            // Set up expectations
            every { input.id } returns 1
            every { input.threshold = any() } just Runs
            every { input.setAdapter(any()) } just Runs
            every { input.text } returns mockk {
                every { isNotEmpty() } returns true
                every { toString() } returns "AAPL"
            }
            every { input.hasFocus() } returns true
            every { input.post(any()) } answers { firstArg<Runnable>().run(); true }
            every { input.showDropDown() } just Runs
            
            val searchState = MutableStateFlow<SearchState>(
                SearchState.Success(listOf(
                    StockSearchResult("AAPL", "Apple Inc.", false)
                ))
            )
            every { stockSearchViewModel.searchState } returns searchState
            
            // Execute
            activity.setupStockSearch(input, adapter, stockSearchViewModel)
            
            // Verify
            verify { input.threshold = 2 }
            verify { input.setAdapter(adapter) }
        }
    }

    @Test
    fun `test add stock pair dialog validation`() = runTest {
        scenario.onActivity { activity ->
            // Execute
            activity.showAddStockPairDialog()
            
            // Verify dialog is shown
            verify { anyConstructed<MaterialAlertDialogBuilder>().setTitle("Add Stock Pair") }
            verify { anyConstructed<MaterialAlertDialogBuilder>().show() }
        }
    }

    @Test
    fun `test UI state handling`() = runTest {
        scenario.onActivity { activity ->
            // Test loading state
            uiStateFlow.value = UiState.Loading
            testDispatcher.scheduler.advanceUntilIdle()
            
            // Test success state
            val stockPair = StockPair(
                ticker1 = "AAPL.ST",
                ticker2 = "GOOGL.ST",
                companyName1 = "Apple Inc",
                companyName2 = "Google Inc",
                priceDifference = 10.0,
                notifyWhenEqual = false
            )
            uiStateFlow.value = UiState.Success(listOf(stockPair))
            testDispatcher.scheduler.advanceUntilIdle()
            
            // Test error state
            uiStateFlow.value = UiState.Error("Test error")
            testDispatcher.scheduler.advanceUntilIdle()
            
            // Verify error toast is shown
            verify { Toast.makeText(any(), "Test error", Toast.LENGTH_LONG).show() }
        }
    }

    @Test
    fun `test stock adapter creation`() = runTest {
        scenario.onActivity { activity ->
            val adapter = activity.createStockAdapter()
            
            assertNotNull(adapter)
            
            // Test adapter with sample data
            val testItem = StockSearchResult("AAPL", "Apple Inc.", false)
            adapter.add(testItem)
            
            assertEquals(1, adapter.count)
            assertEquals(testItem, adapter.getItem(0))
        }
    }

    @Test
    fun `test refresh prices`() = runTest {
        scenario.onActivity { activity ->
            // Mock successful refresh
            coEvery { mainViewModel.refreshStockPairs() } just Runs
            
            // Execute refresh
            activity.refreshPrices()
            
            // Advance time to allow coroutine to complete
            testDispatcher.scheduler.advanceUntilIdle()
            
            // Verify refresh was called
            coVerify { mainViewModel.refreshStockPairs() }
        }
    }

    @Test
    fun `test refresh prices handles error`() = runTest {
        scenario.onActivity { activity ->
            // Mock refresh failure
            coEvery { mainViewModel.refreshStockPairs() } throws Exception("Network error")
            
            // Execute refresh
            activity.refreshPrices()
            
            // Advance time to allow coroutine to complete
            testDispatcher.scheduler.advanceUntilIdle()
            
            // Verify error toast is shown
            verify { Toast.makeText(any(), any<String>(), Toast.LENGTH_LONG).show() }
        }
    }
} 
