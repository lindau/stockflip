package com.stockflip

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.CheckBox
import android.widget.EditText
import android.widget.Filter
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.Lifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.checkbox.MaterialCheckBox
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import com.google.android.material.textfield.TextInputEditText
import com.stockflip.databinding.ActivityMainBinding
import kotlinx.coroutines.*
import java.text.SimpleDateFormat
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.*
import com.stockflip.viewmodel.StockSearchViewModel
import com.stockflip.repository.StockRepository
import com.stockflip.repository.SearchState
import com.stockflip.repository.MetricHistoryRepository
import com.stockflip.MetricHistoryService
import com.stockflip.ui.presets.MetricPresets
import com.stockflip.ui.presets.PresetType
import android.widget.ProgressBar
import androidx.cardview.widget.CardView
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.work.WorkManager
import com.stockflip.ui.SwipeToDeleteCallback

/**
 * Main activity for the StockFlip application.
 * Handles the UI for displaying, adding, editing, and deleting stock pairs.
 * Manages real-time stock price updates and user notifications.
 */
class MainActivity : AppCompatActivity() {
    private val viewModel: MainViewModel by viewModels {
        object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                val database = StockPairDatabase.getDatabase(applicationContext)
                @Suppress("UNCHECKED_CAST")
                return MainViewModel(database.stockPairDao(), database.watchItemDao(), YahooFinanceService) as T
            }
        }
    }
    private lateinit var binding: ActivityMainBinding
    private var selectedStock1: StockSearchResult? = null
    private var selectedStock2: StockSearchResult? = null
    private var selectedStock: StockSearchResult? = null
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (!isGranted) {
            Toast.makeText(this, "Notification permission is required for alerts", Toast.LENGTH_LONG).show()
        }
    }
    private val stockSearchViewModelFactory = object : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            @Suppress("UNCHECKED_CAST")
            return StockSearchViewModel(StockRepository()) as T
        }
    }

    private val stockSearchViewModel1: StockSearchViewModel by viewModels { stockSearchViewModelFactory }

    private val stockSearchViewModel2: StockSearchViewModel by viewModels { stockSearchViewModelFactory }

    private val stockSearchViewModel: StockSearchViewModel by viewModels {
        stockSearchViewModelFactory
    }

    // MetricHistoryService för att hämta historisk data
    private val metricHistoryService: MetricHistoryService by lazy {
        val database = StockPairDatabase.getDatabase(applicationContext)
        val repository = MetricHistoryRepository(database.metricHistoryDao())
        MetricHistoryService(repository)
    }

    private enum class MainTab {
        STOCKS,
        PAIRS
    }

    private var currentMainTab: MainTab = MainTab.STOCKS
    private var lastWatchItems: List<WatchItem> = emptyList()

    /**
     * Initializes the activity's UI components and starts data loading.
     * Sets up the view binding, initializes UI elements, and requests necessary permissions.
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        initializeUI()
        initializeUpdates()
        requestPermissions()
        loadInitialData()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (supportFragmentManager.backStackEntryCount > 0) {
            supportFragmentManager.popBackStack()
            binding.swipeRefreshLayout.visibility = View.VISIBLE
            binding.addPairButton.visibility = View.VISIBLE
            updateToolbarForCurrentTab()
        } else {
            @Suppress("DEPRECATION")
            super.onBackPressed()
        }
    }

    private fun initializeUI(): Unit {
        setupRecyclerView()
        setupSwipeToDelete()
        setupObservers()
        setupSwipeRefresh()
        setupAddButton()
        setupToolbar()
        setupBottomNavigation()
        showStocksToolbar()
    }

    private fun setupBottomNavigation(): Unit {
        binding.bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.menu_stocks -> {
                    currentMainTab = MainTab.STOCKS
                    binding.swipeRefreshLayout.visibility = View.VISIBLE
                    binding.addPairButton.visibility = View.VISIBLE
                    supportFragmentManager.popBackStack(
                        null,
                        androidx.fragment.app.FragmentManager.POP_BACK_STACK_INCLUSIVE
                    )
                    if (lastWatchItems.isNotEmpty()) {
                        showWatchItemSuccess(lastWatchItems)
                    }
                    showStocksToolbar()
                    true
                }
                R.id.menu_pairs -> {
                    currentMainTab = MainTab.PAIRS
                    binding.swipeRefreshLayout.visibility = View.VISIBLE
                    binding.addPairButton.visibility = View.VISIBLE
                    supportFragmentManager.popBackStack(
                        null,
                        androidx.fragment.app.FragmentManager.POP_BACK_STACK_INCLUSIVE
                    )
                    if (lastWatchItems.isNotEmpty()) {
                        showWatchItemSuccess(lastWatchItems)
                    }
                    showPairsToolbar()
                    true
                }
                R.id.menu_alerts -> {
                    binding.swipeRefreshLayout.visibility = View.GONE
                    binding.addPairButton.visibility = View.GONE
                    supportFragmentManager.beginTransaction()
                        .replace(R.id.fragmentContainer, AlertsFragment())
                        .addToBackStack("alerts")
                        .commit()
                    showAlertsToolbar()
                    true
                }
                else -> false
            }
        }
    }

    private fun setupToolbar() {
        binding.topAppBar.menu.clear()
        binding.topAppBar.inflateMenu(R.menu.main_menu)
        binding.topAppBar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.menu_sort_alphabetical -> {
                    val adapter = binding.stockPairsList.adapter as? GroupedWatchItemAdapter
                    adapter?.setSortMode(SortHelper.SortMode.ALPHABETICAL)
                    Toast.makeText(this, "Sortering: Bokstavsordning", Toast.LENGTH_SHORT).show()
                    true
                }
                R.id.menu_sort_addition -> {
                    val adapter = binding.stockPairsList.adapter as? GroupedWatchItemAdapter
                    adapter?.setSortMode(SortHelper.SortMode.ADDITION_ORDER)
                    Toast.makeText(this, "Sortering: Tilläggsordning", Toast.LENGTH_SHORT).show()
                    true
                }
                else -> false
            }
        }
    }

    private fun openAddStock(): Unit {
        binding.swipeRefreshLayout.visibility = View.GONE
        binding.addPairButton.visibility = View.GONE
        binding.topAppBar.title = getString(R.string.title_add_stock)
        binding.topAppBar.navigationIcon = androidx.appcompat.content.res.AppCompatResources.getDrawable(
            this,
            R.drawable.ic_arrow_back
        )
        binding.topAppBar.setNavigationOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }
        binding.topAppBar.menu.findItem(R.id.menu_sort)?.isVisible = false
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, AddStockFragment())
            .addToBackStack("add_stock")
            .commit()
    }

    private fun updateToolbarForCurrentTab(): Unit {
        when (currentMainTab) {
            MainTab.STOCKS -> showStocksToolbar()
            MainTab.PAIRS -> showPairsToolbar()
        }
    }

    private fun showStocksToolbar(): Unit {
        binding.topAppBar.title = getString(R.string.tab_stocks)
        binding.topAppBar.navigationIcon = null
        binding.topAppBar.menu.findItem(R.id.menu_sort)?.isVisible = true
    }

    private fun showPairsToolbar(): Unit {
        binding.topAppBar.title = getString(R.string.tab_pairs)
        binding.topAppBar.navigationIcon = null
        binding.topAppBar.menu.findItem(R.id.menu_sort)?.isVisible = true
    }

    private fun showAlertsToolbar(): Unit {
        binding.topAppBar.title = getString(R.string.tab_alerts)
        binding.topAppBar.navigationIcon = null
        binding.topAppBar.menu.findItem(R.id.menu_sort)?.isVisible = false
    }

    private fun initializeUpdates() {
        WorkManager.getInstance(this).cancelAllWork()
    }

    private fun requestPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestNotificationPermission()
        }
    }

    private fun loadInitialData(): Unit {
        lifecycleScope.launch {
            // Load from database first to show existing data quickly
            // This is safe for non-KeyMetrics items
            viewModel.loadWatchItems()
            // Then immediately refresh to get latest prices and key metrics
            // This ensures KeyMetrics values are loaded correctly
            viewModel.refreshWatchItems()
        }
    }

    /**
     * Updates the last update time display in the UI.
     * Uses the TIME_FORMAT pattern defined in companion object.
     */
    private fun updateLastUpdateTime(): Unit {
        val currentTime: String = SimpleDateFormat(TIME_FORMAT, Locale.getDefault()).format(Date())
        binding.lastUpdateTime.text = getString(R.string.last_updated, currentTime)
        Log.d(TAG, "Updated last update time to $currentTime")
    }

    /**
     * Sets up observers for UI state changes.
     * Handles loading, success, and error states.
     */
    private fun setupObservers(): Unit {
        Log.d(TAG, "Setting up observers for watchItemUiState")
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                Log.d(TAG, "Starting to collect from watchItemUiState StateFlow (lifecycle: STARTED)")
                viewModel.watchItemUiState.collect { state: UiState<List<WatchItem>> ->
                    when (state) {
                        is UiState.Loading -> {
                            Log.d(TAG, "=== UI STATE: Loading ===")
                        }
                        is UiState.Success -> {
                            Log.d(TAG, "=== UI STATE: Success with ${state.data.size} items ===")
                            val keyMetricsItems = state.data.filter { it.watchType is WatchType.KeyMetrics }
                            Log.d(TAG, "KeyMetrics items in UI state: ${keyMetricsItems.size}")
                            keyMetricsItems.forEach { item ->
                                Log.d(TAG, "KeyMetrics in UI: ${item.ticker}, value: ${item.currentMetricValue}")
                            }
                        }
                        is UiState.Error -> {
                            Log.d(TAG, "=== UI STATE: Error - ${state.message} ===")
                        }
                    }
                    handleWatchItemUiState(state)
                }
            }
        }
    }

    /**
     * Handles different UI states and updates the view accordingly.
     *
     * @param state The current UI state to handle
     */
    internal fun handleWatchItemUiState(state: UiState<List<WatchItem>>): Unit {
        when (state) {
            is UiState.Loading -> showLoading()
            is UiState.Success -> showWatchItemSuccess(state.data)
            is UiState.Error -> showError(state.message)
        }
    }

    private fun showLoading(): Unit {
        Log.d(TAG, "Showing loading state")
        val isOnAlertsTab = binding.bottomNavigation.selectedItemId == R.id.menu_alerts
        if (!binding.swipeRefreshLayout.isRefreshing && !isOnAlertsTab) {
            binding.progressBar.visibility = View.VISIBLE
        }
    }

    private fun showWatchItemSuccess(data: List<WatchItem>): Unit {
        Log.d(TAG, "=== showWatchItemSuccess() called with ${data.size} watch items ===")
        val keyMetricsItems = data.filter { it.watchType is WatchType.KeyMetrics }
        Log.d(TAG, "KeyMetrics items in showWatchItemSuccess: ${keyMetricsItems.size}")
        keyMetricsItems.forEach { item ->
            Log.d(TAG, "KeyMetrics item in showWatchItemSuccess: ${item.ticker}, currentMetricValue: ${item.currentMetricValue}")
        }

        lastWatchItems = data

        val filteredData: List<WatchItem> = when (currentMainTab) {
            MainTab.STOCKS -> data.filter { it.watchType !is WatchType.PricePair }
            MainTab.PAIRS -> data.filter { it.watchType is WatchType.PricePair }
        }
        Log.d(TAG, "Filtered watch items for tab $currentMainTab: ${filteredData.size}")

        binding.progressBar.visibility = View.GONE
        Log.d(TAG, "Progress bar hidden")
        
        val adapter = binding.stockPairsList.adapter as? GroupedWatchItemAdapter
        if (adapter != null) {
            Log.d(TAG, "Adapter found, calling submitGroupedList with ${filteredData.size} items")
            adapter.submitGroupedList(filteredData)
            Log.d(TAG, "submitGroupedList called successfully")
        } else {
            Log.e(TAG, "CRITICAL: Adapter is null! Cannot update UI")
        }
    }

    private fun showError(message: String): Unit {
        Log.e(TAG, "Error state: $message")
        binding.progressBar.visibility = View.GONE
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    private fun setupRecyclerView(): Unit {
        Log.d(TAG, "Setting up RecyclerView")
        binding.stockPairsList.layoutManager = LinearLayoutManager(this)
        binding.stockPairsList.adapter = GroupedWatchItemAdapter(
            onDeleteClick = { item: WatchItem -> handleDeleteClick(item) },
            onEditClick = { item: WatchItem -> handleEditClick(item) },
            onItemClick = { item: WatchItem -> handleItemClick(item) }
        )
    }

    private fun setupSwipeToDelete(): Unit {
        val callback = SwipeToDeleteCallback(
            context = this,
            canSwipe = { position ->
                val adapter = binding.stockPairsList.adapter as? GroupedWatchItemAdapter ?: return@SwipeToDeleteCallback false
                val item = adapter.currentList.getOrNull(position) ?: return@SwipeToDeleteCallback false
                item !is GroupedListItem.Header
            },
            onSwiped = { position ->
                val adapter = binding.stockPairsList.adapter as? GroupedWatchItemAdapter ?: return@SwipeToDeleteCallback
                val item = adapter.currentList.getOrNull(position) ?: return@SwipeToDeleteCallback
                when (item) {
                    is GroupedListItem.MultipleWatchesWrapper -> {
                        adapter.notifyItemChanged(position) // snap back while dialog shows
                        showDeleteStockDialog(item.symbol, item.watchCount)
                    }
                    is GroupedListItem.WatchItemWrapper -> {
                        if (item.item.watchType is WatchType.PricePair) {
                            adapter.notifyItemChanged(position)
                            showDeleteConfirmationDialog(item.item)
                        } else {
                            adapter.notifyItemChanged(position)
                            val symbol = item.item.ticker ?: return@SwipeToDeleteCallback
                            showDeleteStockDialog(symbol, 1)
                        }
                    }
                    is GroupedListItem.Header -> {}
                }
            }
        )
        ItemTouchHelper(callback).attachToRecyclerView(binding.stockPairsList)
    }

    private fun showDeleteStockDialog(symbol: String, watchCount: Int): Unit {
        val message = if (watchCount == 1)
            "Detta tar bort 1 bevakning för $symbol."
        else
            "Detta tar bort $watchCount bevakningar för $symbol."
        MaterialAlertDialogBuilder(this)
            .setTitle("Ta bort $symbol")
            .setMessage(message)
            .setPositiveButton("Ta bort") { _, _ ->
                lifecycleScope.launch {
                    try {
                        viewModel.deleteStockBySymbol(symbol)
                        Toast.makeText(this@MainActivity, "$symbol borttagen", Toast.LENGTH_SHORT).show()
                    } catch (e: Exception) {
                        Toast.makeText(this@MainActivity, "Kunde inte ta bort: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }
            .setNegativeButton("Avbryt", null)
            .show()
    }

    /**
     * Hanterar klick på WatchItem.
     * På aktierfliken navigeras till StockDetailFragment.
     * På bevakningsfliken öppnas redigeringsdialog.
     */
    private fun handleItemClick(item: WatchItem): Unit {
        when (currentMainTab) {
            MainTab.STOCKS -> {
                val symbol = item.ticker ?: return
                navigateToStockDetail(symbol, item.companyName)
            }
            MainTab.PAIRS -> handleEditClick(item)
        }
    }

    /**
     * Navigerar till StockDetailFragment för en aktie.
     */
    private fun navigateToStockDetail(symbol: String, companyName: String? = null): Unit {
        val fragment = StockDetailFragment.newInstance(symbol, companyName)
        
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, fragment)
            .addToBackStack("stock_detail")
            .commit()
        
        // Dölj RecyclerView och visa Fragment
        binding.swipeRefreshLayout.visibility = View.GONE
        binding.addPairButton.visibility = View.GONE
        
        // Dölj sorteringsmenyn
        binding.topAppBar.menu.findItem(R.id.menu_sort)?.isVisible = false
    }

    internal fun navigateToStockDetailFromAlerts(symbol: String, companyName: String?): Unit {
        navigateToStockDetail(symbol, companyName)
    }

    internal fun showEditDialogFromAlerts(item: WatchItem): Unit {
        handleEditClick(item)
    }

    private fun handleDeleteClick(item: WatchItem): Unit {
        Log.d(TAG, "Delete clicked for watch item: ${item.getDisplayName()}")
        showDeleteConfirmationDialog(item)
    }

    private fun handleEditClick(item: WatchItem): Unit {
        Log.d(TAG, "Edit clicked for watch item: ${item.getDisplayName()}")
        when (item.watchType) {
            is WatchType.PricePair -> showEditStockPairDialog(item)
            is WatchType.PriceTarget -> showEditPriceTargetDialog(item)
            is WatchType.PriceRange -> showEditPriceRangeDialog(item)
            is WatchType.KeyMetrics -> showEditKeyMetricsDialog(item)
            is WatchType.ATHBased -> showEditATHBasedDialog(item)
            is WatchType.DailyMove -> showEditDailyMoveDialog(item)
            is WatchType.Combined -> {
                showEditCombinedAlertDialog(item)
            }
        }
    }

    private fun setupAddButton(): Unit {
        binding.addPairButton.setOnClickListener {
            when (currentMainTab) {
                MainTab.STOCKS -> {
                    openAddStock()
                }
                MainTab.PAIRS -> {
                    showAddStockPairDialog()
                }
            }
        }
    }

    /**
     * Shows a dialog for selecting the type of watch to create.
     * This allows for easy extension with new watch types in the future.
     */
    private fun showWatchTypeSelectionDialog(): Unit {
        val watchTypes = listOf(
            "Aktiepar" to WatchType.PricePair(0.0, false),
            "Prisbevakning" to WatchType.PriceTarget(0.0, WatchType.PriceDirection.ABOVE),
            "Nyckeltal" to WatchType.KeyMetrics(WatchType.MetricType.PE_RATIO, 0.0, WatchType.PriceDirection.ABOVE),
            "ATH-bevakning" to WatchType.ATHBased(WatchType.DropType.PERCENTAGE, 0.0),
            "Kombinerat larm" to WatchType.Combined(AlertExpression.Single(AlertRule.SinglePrice("", AlertRule.PriceComparisonType.BELOW, 0.0))) // Placeholder - kommer inte användas
        )
        val watchTypeNames = watchTypes.map { it.first }.toTypedArray()

        MaterialAlertDialogBuilder(this)
            .setTitle("Välj bevakningstyp")
            .setItems(watchTypeNames) { _, which ->
                val selectedType = watchTypes[which].second
                when (selectedType) {
                    is WatchType.PricePair -> showAddStockPairDialog()
                    is WatchType.PriceTarget -> showAddPriceTargetDialog()
                    is WatchType.PriceRange -> {
                        Toast.makeText(this, "Skapa via aktiedetaljvy", Toast.LENGTH_SHORT).show()
                    }
                    is WatchType.KeyMetrics -> showAddKeyMetricsDialog()
                    is WatchType.ATHBased -> showAddATHBasedDialog()
                    is WatchType.DailyMove -> {
                        Toast.makeText(this, "Skapa via aktiedetaljvy", Toast.LENGTH_SHORT).show()
                    }
                    is WatchType.Combined -> {
                        showAddCombinedAlertDialog()
                    }
                }
            }
            .setNegativeButton("Avbryt", null)
            .show()
    }

    /**
     * Shows a dialog for adding a new stock pair.
     * Handles user input validation and API calls for stock information.
     */
    internal fun showAddStockPairDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_add_stock_pair, null)
        val ticker1Input = dialogView.findViewById<MaterialAutoCompleteTextView>(R.id.ticker1Input)
        val ticker2Input = dialogView.findViewById<MaterialAutoCompleteTextView>(R.id.ticker2Input)
        val priceDifferenceInput = dialogView.findViewById<TextInputEditText>(R.id.priceDifferenceInput)
        val notifyWhenEqualCheckbox = dialogView.findViewById<MaterialCheckBox>(R.id.notifyWhenEqualCheckbox)

        // Set up adapters
        val adapter1 = createStockAdapter()
        val adapter2 = createStockAdapter()
        ticker1Input.setAdapter(adapter1)
        ticker2Input.setAdapter(adapter2)

        // Set up search functionality with separate view models
        setupStockSearch(ticker1Input, adapter1, stockSearchViewModel1, includeCrypto = false)
        setupStockSearch(ticker2Input, adapter2, stockSearchViewModel2, includeCrypto = false)

        // Set up item click listeners
        ticker1Input.setOnItemClickListener { _, _, position, _ ->
            selectedStock1 = adapter1.getItem(position)
            Log.d(TAG, "Selected stock 1: $selectedStock1")
        }

        ticker2Input.setOnItemClickListener { _, _, position, _ ->
            selectedStock2 = adapter2.getItem(position)
            Log.d(TAG, "Selected stock 2: $selectedStock2")
        }

        MaterialAlertDialogBuilder(this)
            .setTitle("Lägg till aktiepar")
            .setView(dialogView)
            .setPositiveButton("Lägg till") { _, _ ->
                val priceDifferenceStr = priceDifferenceInput.text.toString()
                val notifyWhenEqual = notifyWhenEqualCheckbox.isChecked

                if (selectedStock1 != null && selectedStock2 != null && priceDifferenceStr.isNotEmpty()) {
                    val priceDifference = priceDifferenceStr.toDoubleOrNull() ?: 0.0
                    lifecycleScope.launch {
                        try {
                            binding.progressBar.visibility = View.VISIBLE
                            
                            val watchItem = WatchItem(
                                watchType = WatchType.PricePair(priceDifference, notifyWhenEqual),
                                ticker1 = selectedStock1!!.symbol,
                                ticker2 = selectedStock2!!.symbol,
                                companyName1 = selectedStock1!!.name,
                                companyName2 = selectedStock2!!.name
                            )
                            
                            viewModel.addWatchItem(watchItem)
                            binding.progressBar.visibility = View.GONE
                            Toast.makeText(this@MainActivity, "Aktiepar tillagt", Toast.LENGTH_SHORT).show()
                        } catch (e: Exception) {
                            binding.progressBar.visibility = View.GONE
                            Toast.makeText(this@MainActivity, "Kunde inte lägga till aktiepar: ${e.message}", Toast.LENGTH_LONG).show()
                        }
                    }
                } else {
                    Toast.makeText(this, "Välj båda aktier och ange prissskillnad", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Avbryt", null)
            .show()
    }

    /**
     * Shows a dialog for adding a new price target watch.
     * Handles user input validation and API calls for stock information.
     * 
     * @param prefillSymbol Optional symbol to prefill in the ticker input field
     */
    private fun showAddPriceTargetDialog(prefillSymbol: String? = null): Unit {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_add_price_target, null)
        val tickerInput = dialogView.findViewById<MaterialAutoCompleteTextView>(R.id.tickerInput)
        val targetPriceInput = dialogView.findViewById<TextInputEditText>(R.id.targetPriceInput)

        // Set up adapter for stock search
        val adapter = createStockAdapter()
        tickerInput.setAdapter(adapter)

        // Set up search functionality
        setupStockSearch(tickerInput, adapter, stockSearchViewModel, includeCrypto = true)

        // Prefill symbol if provided
        if (prefillSymbol != null) {
            tickerInput.setText(prefillSymbol, false)
            // Try to find and select the stock in the adapter
            for (i in 0 until adapter.count) {
                val item = adapter.getItem(i)
                if (item?.symbol == prefillSymbol) {
                    selectedStock = item
                    Log.d(TAG, "Prefilled stock: $selectedStock")
                    break
                }
            }
        }

        // Set up item click listener
        tickerInput.setOnItemClickListener { _, _, position, _ ->
            selectedStock = adapter.getItem(position)
            Log.d(TAG, "Selected stock: $selectedStock")
        }

        MaterialAlertDialogBuilder(this)
            .setTitle("Lägg till prisbevakning")
            .setView(dialogView)
            .setPositiveButton("Lägg till") { _, _ ->
                val targetPriceStr = targetPriceInput.text.toString()

                if (selectedStock != null && targetPriceStr.isNotEmpty()) {
                    val targetPrice = targetPriceStr.toDoubleOrNull()

                    if (targetPrice != null && targetPrice > 0) {
                        lifecycleScope.launch {
                            try {
                                binding.progressBar.visibility = View.VISIBLE

                                val watchItem = WatchItem(
                                    watchType = WatchType.PriceTarget(targetPrice, WatchType.PriceDirection.ABOVE),
                                    ticker = selectedStock!!.symbol,
                                    companyName = selectedStock!!.name
                                )

                                viewModel.addWatchItem(watchItem)
                                binding.progressBar.visibility = View.GONE
                                Toast.makeText(this@MainActivity, "Prisbevakning tillagd", Toast.LENGTH_SHORT).show()
                            } catch (e: Exception) {
                                binding.progressBar.visibility = View.GONE
                                Toast.makeText(this@MainActivity, "Kunde inte lägga till prisbevakning: ${e.message}", Toast.LENGTH_LONG).show()
                            }
                        }
                    } else {
                        Toast.makeText(this, "Ange ett giltigt målpris", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(this, "Välj aktie och ange målpris", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Avbryt", null)
            .show()
    }

    /**
     * Shows a dialog for adding a new key metrics watch.
     * Handles user input validation and API calls for stock information.
     * 
     * @param prefillSymbol Optional symbol to prefill in the ticker input field
     */
    private fun showAddKeyMetricsDialog(prefillSymbol: String? = null): Unit {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_add_key_metrics, null)
        val tickerInput = dialogView.findViewById<MaterialAutoCompleteTextView>(R.id.tickerInput)
        val metricTypeInput = dialogView.findViewById<MaterialAutoCompleteTextView>(R.id.metricTypeInput)
        val targetValueInput = dialogView.findViewById<TextInputEditText>(R.id.targetValueInput)

        // History UI elements - hidden
        val historyCard = dialogView.findViewById<CardView>(R.id.historyCard)
        historyCard.visibility = View.GONE

        // Set up adapter for stock search
        val adapter = createStockAdapter()
        tickerInput.setAdapter(adapter)
        setupStockSearch(tickerInput, adapter, stockSearchViewModel, includeCrypto = false)

        // Prefill symbol if provided
        if (prefillSymbol != null) {
            tickerInput.setText(prefillSymbol, false)
            // Try to find and select the stock in the adapter
            for (i in 0 until adapter.count) {
                val item = adapter.getItem(i)
                if (item?.symbol == prefillSymbol) {
                    selectedStock = item
                    Log.d(TAG, "Prefilled stock: $selectedStock")
                    break
                }
            }
        }

        var selectedMetricType: WatchType.MetricType? = null

        tickerInput.setOnItemClickListener { _, _, position, _ ->
            selectedStock = adapter.getItem(position)
            Log.d(TAG, "Selected stock: $selectedStock")
        }

        // Set up metric type dropdown
        val metricTypes = arrayOf("P/E-tal", "P/S-tal", "Utdelningsprocent")
        val metricTypeAdapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, metricTypes)
        metricTypeInput.setAdapter(metricTypeAdapter)
        metricTypeInput.setOnItemClickListener { _, _, position, _ ->
            selectedMetricType = when (position) {
                0 -> WatchType.MetricType.PE_RATIO
                1 -> WatchType.MetricType.PS_RATIO
                2 -> WatchType.MetricType.DIVIDEND_YIELD
                else -> null
            }
            Log.d(TAG, "Selected metric type: ${metricTypes[position]}")
        }

        MaterialAlertDialogBuilder(this)
            .setTitle("Lägg till nyckeltalsbevakning")
            .setView(dialogView)
            .setPositiveButton("Lägg till") { _, _ ->
                val tickerStr = tickerInput.text.toString().trim()
                val metricTypeStr = metricTypeInput.text.toString()
                val targetValueStr = targetValueInput.text.toString()

                val finalTicker = selectedStock?.symbol ?: tickerStr

                if (finalTicker.isNotEmpty() && metricTypeStr.isNotEmpty() && targetValueStr.isNotEmpty()) {
                    val metricType = when (metricTypeStr) {
                        "P/E-tal" -> WatchType.MetricType.PE_RATIO
                        "P/S-tal" -> WatchType.MetricType.PS_RATIO
                        "Utdelningsprocent" -> WatchType.MetricType.DIVIDEND_YIELD
                        else -> null
                    }
                    val targetValue = targetValueStr.toDoubleOrNull()

                    if (metricType != null && targetValue != null && targetValue > 0) {
                        lifecycleScope.launch {
                            try {
                                binding.progressBar.visibility = View.VISIBLE

                                val watchItem = WatchItem(
                                    watchType = WatchType.KeyMetrics(metricType, targetValue, WatchType.PriceDirection.ABOVE),
                                    ticker = finalTicker,
                                    companyName = selectedStock?.name
                                )

                                viewModel.addWatchItem(watchItem)
                                binding.progressBar.visibility = View.GONE
                                Toast.makeText(this@MainActivity, "Nyckeltalsbevakning tillagd", Toast.LENGTH_SHORT).show()
                            } catch (e: Exception) {
                                binding.progressBar.visibility = View.GONE
                                Toast.makeText(this@MainActivity, "Kunde inte lägga till nyckeltalsbevakning: ${e.message}", Toast.LENGTH_LONG).show()
                            }
                        }
                    } else {
                        Toast.makeText(this, "Ange giltiga värden för alla fält", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(this, "Fyll i alla fält", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Avbryt", null)
            .show()
    }

    /**
     * Shows a dialog for adding a new ATH-based watch.
     * Handles user input validation and API calls for stock information.
     */
    /**
     * Shows a dialog for adding a new ATH-based watch.
     * 
     * @param prefillSymbol Optional symbol to prefill in the ticker input field
     */
    private fun showAddATHBasedDialog(prefillSymbol: String? = null): Unit {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_add_ath_based, null)
        val tickerInput = dialogView.findViewById<MaterialAutoCompleteTextView>(R.id.tickerInput)
        val dropTypeInput = dialogView.findViewById<MaterialAutoCompleteTextView>(R.id.dropTypeInput)
        val dropValueInput = dialogView.findViewById<TextInputEditText>(R.id.dropValueInput)

        // Set up adapter for stock search
        val adapter = createStockAdapter()
        tickerInput.setAdapter(adapter)
        setupStockSearch(tickerInput, adapter, stockSearchViewModel, includeCrypto = true)

        tickerInput.setOnItemClickListener { _, _, position, _ ->
            selectedStock = adapter.getItem(position)
            Log.d(TAG, "Selected stock: $selectedStock")
        }

        // Set up drop type dropdown
        val dropTypes = arrayOf("Procent", "Absolut (SEK)")
        val dropTypeAdapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, dropTypes)
        dropTypeInput.setAdapter(dropTypeAdapter)
        dropTypeInput.setOnItemClickListener { _, _, position, _ ->
            Log.d(TAG, "Selected drop type: ${dropTypes[position]}")
        }

        MaterialAlertDialogBuilder(this)
            .setTitle("Lägg till ATH-bevakning")
            .setView(dialogView)
            .setPositiveButton("Lägg till") { _, _ ->
                val tickerStr = tickerInput.text.toString().trim()
                val dropTypeStr = dropTypeInput.text.toString()
                val dropValueStr = dropValueInput.text.toString()

                val finalTicker = selectedStock?.symbol ?: tickerStr

                if (finalTicker.isNotEmpty() && dropTypeStr.isNotEmpty() && dropValueStr.isNotEmpty()) {
                    val dropType = when (dropTypeStr) {
                        "Procent" -> WatchType.DropType.PERCENTAGE
                        "Absolut (SEK)" -> WatchType.DropType.ABSOLUTE
                        else -> null
                    }
                    val dropValue = dropValueStr.toDoubleOrNull()

                    if (dropType != null && dropValue != null && dropValue > 0) {
                        lifecycleScope.launch {
                            try {
                                binding.progressBar.visibility = View.VISIBLE

                                val watchItem = WatchItem(
                                    watchType = WatchType.ATHBased(dropType, dropValue),
                                    ticker = finalTicker,
                                    companyName = selectedStock?.name
                                )

                                viewModel.addWatchItem(watchItem)
                                binding.progressBar.visibility = View.GONE
                                Toast.makeText(this@MainActivity, "ATH-bevakning tillagd", Toast.LENGTH_SHORT).show()
                            } catch (e: Exception) {
                                binding.progressBar.visibility = View.GONE
                                Toast.makeText(this@MainActivity, "Kunde inte lägga till ATH-bevakning: ${e.message}", Toast.LENGTH_LONG).show()
                            }
                        }
                    } else {
                        Toast.makeText(this, "Ange giltiga värden för alla fält", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(this, "Fyll i alla fält", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Avbryt", null)
            .show()
    }

    /**
     * Shows a dialog for adding a new combined alert.
     * Allows users to create alerts with multiple conditions combined with AND/OR.
     */
    private fun showAddCombinedAlertDialog(): Unit {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_add_combined_alert, null)
        val symbolInput = dialogView.findViewById<MaterialAutoCompleteTextView>(R.id.symbolInput)
        val conditionsRecyclerView = dialogView.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.conditionsRecyclerView)
        val addConditionButton = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.addConditionButton)
        val previewText = dialogView.findViewById<TextView>(R.id.previewText)

        // Setup RecyclerView
        conditionsRecyclerView.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(this)
        
        // Create condition adapter (lateinit to use in lambdas)
        lateinit var conditionAdapter: com.stockflip.ui.builders.ConditionBuilderAdapter
        
        conditionAdapter = com.stockflip.ui.builders.ConditionBuilderAdapter(
            onConditionTypeChanged = { _, _ ->
                val symbol = symbolInput.text.toString()
                updatePreview(conditionAdapter, symbol, previewText)
            },
            onValueChanged = { _, _ ->
                val symbol = symbolInput.text.toString()
                updatePreview(conditionAdapter, symbol, previewText)
            },
            onOperatorChanged = { _, _ ->
                val symbol = symbolInput.text.toString()
                updatePreview(conditionAdapter, symbol, previewText)
            },
            onRemove = { position ->
                conditionAdapter.removeCondition(position)
                val symbol = symbolInput.text.toString()
                updatePreview(conditionAdapter, symbol, previewText)
            }
        )
        
        conditionsRecyclerView.adapter = conditionAdapter

        // Setup stock adapter for symbol input
        val stockAdapter = createStockAdapter()
        symbolInput.setAdapter(stockAdapter)
        
        // Set up search functionality
        setupStockSearch(symbolInput, stockAdapter, stockSearchViewModel, includeCrypto = true)
        
        symbolInput.setOnItemClickListener { _, _, itemPosition, _ ->
            val item = stockAdapter.getItem(itemPosition)
            val symbol = item?.symbol ?: symbolInput.text.toString()
            updatePreview(conditionAdapter, symbol, previewText)
        }

        // Add condition button
        addConditionButton.setOnClickListener {
            conditionAdapter.addCondition()
            val symbol = symbolInput.text.toString()
            updatePreview(conditionAdapter, symbol, previewText)
        }

        // Initial preview
        updatePreview(conditionAdapter, "", previewText)

        MaterialAlertDialogBuilder(this)
            .setTitle("Skapa kombinerat larm")
            .setView(dialogView)
            .setPositiveButton("Lägg till") { _, _ ->
                val symbol = symbolInput.text.toString().trim()
                val conditions = conditionAdapter.getConditions()
                
                // Validate symbol
                if (symbol.isEmpty()) {
                    Toast.makeText(this, "Välj en aktie", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                
                if (conditions.isEmpty()) {
                    Toast.makeText(this, "Lägg till minst ett villkor", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                
                // Validate all conditions
                val validConditions = conditions.filter { 
                    it.value.isNotEmpty() && 
                    it.value.toDoubleOrNull() != null 
                }
                
                if (validConditions.size != conditions.size) {
                    Toast.makeText(this, "Alla villkor måste ha giltigt värde", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                
                // Build AlertExpression
                val expression = buildAlertExpression(symbol, validConditions)
                if (expression == null) {
                    Toast.makeText(this, "Kunde inte skapa uttryck", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                
                lifecycleScope.launch {
                    try {
                        binding.progressBar.visibility = View.VISIBLE
                        
                        val watchItem = WatchItem(
                            watchType = WatchType.Combined(expression),
                            ticker = symbol,
                            companyName = null // Could be enhanced to fetch company name
                        )
                        
                        viewModel.addWatchItem(watchItem)
                        binding.progressBar.visibility = View.GONE
                        Toast.makeText(this@MainActivity, "Kombinerat larm tillagt", Toast.LENGTH_SHORT).show()
                    } catch (e: Exception) {
                        binding.progressBar.visibility = View.GONE
                        Toast.makeText(this@MainActivity, "Kunde inte lägga till kombinerat larm: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }
            .setNegativeButton("Avbryt", null)
            .show()
    }

    /**
     * Shows a dialog for editing an existing combined alert.
     */
    private fun showEditCombinedAlertDialog(watchItem: WatchItem): Unit {
        val combined = watchItem.watchType as? WatchType.Combined ?: return
        val expression = combined.expression
        
        // Dekomponera uttrycket till villkor
        val decompositionResult = decomposeExpression(expression)
        if (decompositionResult == null) {
            Toast.makeText(this, "Detta kombinerat larm kan inte redigeras (komplex struktur)", Toast.LENGTH_LONG).show()
            return
        }
        
        val (symbol, conditions) = decompositionResult
        
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_add_combined_alert, null)
        val symbolInput = dialogView.findViewById<MaterialAutoCompleteTextView>(R.id.symbolInput)
        val conditionsRecyclerView = dialogView.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.conditionsRecyclerView)
        val addConditionButton = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.addConditionButton)
        val previewText = dialogView.findViewById<TextView>(R.id.previewText)

        // Setup RecyclerView
        conditionsRecyclerView.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(this)
        
        // Create condition adapter with existing conditions
        lateinit var conditionAdapter: com.stockflip.ui.builders.ConditionBuilderAdapter
        
        conditionAdapter = com.stockflip.ui.builders.ConditionBuilderAdapter(
            onConditionTypeChanged = { _, _ ->
                val newSymbol = symbolInput.text.toString()
                updatePreview(conditionAdapter, newSymbol, previewText)
            },
            onValueChanged = { _, _ ->
                val newSymbol = symbolInput.text.toString()
                updatePreview(conditionAdapter, newSymbol, previewText)
            },
            onOperatorChanged = { _, _ ->
                val newSymbol = symbolInput.text.toString()
                updatePreview(conditionAdapter, newSymbol, previewText)
            },
            onRemove = { position ->
                conditionAdapter.removeCondition(position)
                val newSymbol = symbolInput.text.toString()
                updatePreview(conditionAdapter, newSymbol, previewText)
            }
        )
        
        // Lägg till befintliga villkor med värden
        conditionAdapter.setConditions(conditions)
        
        conditionsRecyclerView.adapter = conditionAdapter

        // Setup stock adapter for symbol input
        val stockAdapter = createStockAdapter()
        symbolInput.setAdapter(stockAdapter)
        symbolInput.setText(symbol, false)
        
        // Set up search functionality
        setupStockSearch(symbolInput, stockAdapter, stockSearchViewModel, includeCrypto = true)
        
        symbolInput.setOnItemClickListener { _, _, itemPosition, _ ->
            val item = stockAdapter.getItem(itemPosition)
            val newSymbol = item?.symbol ?: symbolInput.text.toString()
            updatePreview(conditionAdapter, newSymbol, previewText)
        }

        // Add condition button
        addConditionButton.setOnClickListener {
            conditionAdapter.addCondition()
            val newSymbol = symbolInput.text.toString()
            updatePreview(conditionAdapter, newSymbol, previewText)
        }

        // Initial preview
        updatePreview(conditionAdapter, symbol, previewText)

        MaterialAlertDialogBuilder(this)
            .setTitle("Redigera kombinerat larm")
            .setView(dialogView)
            .setPositiveButton("Spara") { _, _ ->
                val newSymbol = symbolInput.text.toString().trim()
                val newConditions = conditionAdapter.getConditions()
                
                // Validate symbol
                if (newSymbol.isEmpty()) {
                    Toast.makeText(this, "Välj en aktie", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                
                if (newConditions.isEmpty()) {
                    Toast.makeText(this, "Lägg till minst ett villkor", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                
                // Validate all conditions
                val validConditions = newConditions.filter { 
                    it.value.isNotEmpty() && 
                    it.value.toDoubleOrNull() != null 
                }
                
                if (validConditions.size != newConditions.size) {
                    Toast.makeText(this, "Alla villkor måste ha giltigt värde", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                
                // Build AlertExpression
                val newExpression = buildAlertExpression(newSymbol, validConditions)
                if (newExpression == null) {
                    Toast.makeText(this, "Kunde inte skapa uttryck", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                
                lifecycleScope.launch {
                    try {
                        binding.progressBar.visibility = View.VISIBLE
                        
                        val updatedWatchItem = watchItem.copy(
                            watchType = WatchType.Combined(newExpression),
                            ticker = newSymbol
                        )
                        
                        viewModel.updateWatchItem(updatedWatchItem)
                        binding.progressBar.visibility = View.GONE
                        Toast.makeText(this@MainActivity, "Kombinerat larm uppdaterat", Toast.LENGTH_SHORT).show()
                    } catch (e: Exception) {
                        binding.progressBar.visibility = View.GONE
                        Toast.makeText(this@MainActivity, "Kunde inte uppdatera kombinerat larm: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }
            .setNegativeButton("Avbryt", null)
            .show()
    }

    /**
     * Dekomponerar en AlertExpression till symbol och lista av villkor.
     * Fungerar bara för "flat" uttryck (alla AND eller alla OR, inga parenteser).
     * 
     * @return Pair av (symbol, lista av ConditionData) eller null om uttrycket är för komplext
     */
    private fun decomposeExpression(expression: AlertExpression): Pair<String, List<com.stockflip.ui.builders.ConditionBuilderAdapter.ConditionData>>? {
        val conditions = mutableListOf<com.stockflip.ui.builders.ConditionBuilderAdapter.ConditionData>()
        var currentSymbol: String? = null
        
        fun extractRules(expr: AlertExpression, operator: String? = null): Boolean {
            return when (expr) {
                is AlertExpression.Single -> {
                    val rule = expr.rule
                    val symbol = when (rule) {
                        is AlertRule.SinglePrice -> rule.symbol
                        is AlertRule.SingleDrawdownFromHigh -> rule.symbol
                        is AlertRule.SingleDailyMove -> rule.symbol
                        is AlertRule.SingleKeyMetric -> rule.symbol
                        is AlertRule.PairSpread -> return false // PairSpread stöds inte
                    }
                    
                    // Kontrollera att alla villkor använder samma aktie
                    if (currentSymbol == null) {
                        currentSymbol = symbol
                    } else if (currentSymbol != symbol) {
                        return false // Olika aktier, kan inte dekomponeras
                    }
                    
                    // Konvertera AlertRule till ConditionData
                    val conditionData = when (rule) {
                        is AlertRule.SinglePrice -> {
                            com.stockflip.ui.builders.ConditionBuilderAdapter.ConditionData(
                                conditionType = "Pris",
                                direction = if (rule.comparisonType == AlertRule.PriceComparisonType.ABOVE) "Över" else "Under",
                                value = rule.priceLimit.toString(),
                                operator = operator
                            )
                        }
                        is AlertRule.SingleDrawdownFromHigh -> {
                            com.stockflip.ui.builders.ConditionBuilderAdapter.ConditionData(
                                conditionType = "52w High Drop",
                                direction = "Över", // Drawdown är alltid "över" tröskel
                                value = rule.dropValue.toString(),
                                operator = operator
                            )
                        }
                        is AlertRule.SingleDailyMove -> {
                            com.stockflip.ui.builders.ConditionBuilderAdapter.ConditionData(
                                conditionType = "Dagsrörelse",
                                direction = "Över", // Används inte för dagsrörelse
                                value = rule.percentThreshold.toString(),
                                operator = operator
                            )
                        }
                        is AlertRule.SingleKeyMetric -> {
                            val conditionType = when (rule.metricType) {
                                AlertRule.KeyMetricType.PE_RATIO -> "P/E-tal"
                                AlertRule.KeyMetricType.PS_RATIO -> "P/S-tal"
                                AlertRule.KeyMetricType.DIVIDEND_YIELD -> "Utdelningsprocent"
                            }
                            com.stockflip.ui.builders.ConditionBuilderAdapter.ConditionData(
                                conditionType = conditionType,
                                direction = if (rule.direction == AlertRule.PriceComparisonType.ABOVE) "Över" else "Under",
                                value = rule.targetValue.toString(),
                                operator = operator
                            )
                        }
                        else -> return false
                    }
                    
                    conditions.add(conditionData)
                    true
                }
                is AlertExpression.And -> {
                    // Rekursivt extrahera från vänster och höger
                    val leftOk = extractRules(expr.left, null) // Första villkoret har ingen operator
                    if (!leftOk) return false
                    
                    // För höger sida, använd AND som operator
                    val rightOk = extractRules(expr.right, "OCH")
                    leftOk && rightOk
                }
                is AlertExpression.Or -> {
                    // Rekursivt extrahera från vänster och höger
                    val leftOk = extractRules(expr.left, null) // Första villkoret har ingen operator
                    if (!leftOk) return false
                    
                    // För höger sida, använd OR som operator
                    val rightOk = extractRules(expr.right, "ELLER")
                    leftOk && rightOk
                }
                is AlertExpression.Not -> {
                    false // NOT stöds inte för redigering
                }
            }
        }
        
        val success = extractRules(expression)
        if (!success || currentSymbol == null || conditions.isEmpty()) {
            return null
        }
        
        // Ta bort operator från första villkoret
        if (conditions.isNotEmpty()) {
            conditions[0].operator = null
        }
        
        return Pair(currentSymbol!!, conditions.toList())
    }

    /**
     * Updates the preview text showing the current expression.
     */
    private fun updatePreview(
        adapter: com.stockflip.ui.builders.ConditionBuilderAdapter,
        symbol: String,
        previewText: TextView
    ) {
        val conditions = adapter.getConditions()
        if (symbol.isEmpty()) {
            previewText.text = "Välj en aktie"
            previewText.setTextColor(getColor(android.R.color.darker_gray))
            return
        }
        
        if (conditions.isEmpty()) {
            previewText.text = "Lägg till minst ett villkor"
            previewText.setTextColor(getColor(android.R.color.darker_gray))
            return
        }
        
        val expression = buildAlertExpression(symbol, conditions)
        if (expression != null) {
            previewText.text = expression.getDescription()
            previewText.setTextColor(getColor(android.R.color.black))
        } else {
            previewText.text = "Ofullständiga villkor"
            previewText.setTextColor(getColor(android.R.color.holo_red_dark))
        }
    }

    /**
     * Builds an AlertExpression from a list of conditions with operators between them.
     */
    private fun buildAlertExpression(
        symbol: String,
        conditions: List<com.stockflip.ui.builders.ConditionBuilderAdapter.ConditionData>
    ): AlertExpression? {
        if (conditions.isEmpty()) return null
        
        // Convert conditions to AlertRules
        val rules = conditions.mapNotNull { condition ->
            buildAlertRule(symbol, condition)
        }
        
        if (rules.isEmpty()) return null
        
        // Start with first rule
        var expression: AlertExpression = AlertExpression.Single(rules.first())
        
        // Combine with remaining rules using their operators
        for (i in 1 until rules.size) {
            val nextExpression = AlertExpression.Single(rules[i])
            val operator = conditions[i].operator ?: "OCH"
            val isAnd = operator.contains("OCH")
            
            expression = if (isAnd) {
                AlertExpression.And(expression, nextExpression)
            } else {
                AlertExpression.Or(expression, nextExpression)
            }
        }
        
        return expression
    }

    /**
     * Builds an AlertRule from a ConditionData and symbol.
     */
    private fun buildAlertRule(symbol: String, condition: com.stockflip.ui.builders.ConditionBuilderAdapter.ConditionData): AlertRule? {
        val value = condition.value.toDoubleOrNull() ?: return null
        
        return when (condition.conditionType) {
            "Pris" -> {
                val comparisonType = when (condition.direction) {
                    "Över" -> AlertRule.PriceComparisonType.ABOVE
                    "Under" -> AlertRule.PriceComparisonType.BELOW
                    else -> return null
                }
                AlertRule.SinglePrice(symbol, comparisonType, value)
            }
            "P/E-tal" -> {
                val direction = when (condition.direction) {
                    "Över" -> AlertRule.PriceComparisonType.ABOVE
                    "Under" -> AlertRule.PriceComparisonType.BELOW
                    else -> return null
                }
                AlertRule.SingleKeyMetric(symbol, AlertRule.KeyMetricType.PE_RATIO, value, direction)
            }
            "P/S-tal" -> {
                val direction = when (condition.direction) {
                    "Över" -> AlertRule.PriceComparisonType.ABOVE
                    "Under" -> AlertRule.PriceComparisonType.BELOW
                    else -> return null
                }
                AlertRule.SingleKeyMetric(symbol, AlertRule.KeyMetricType.PS_RATIO, value, direction)
            }
            "Utdelningsprocent" -> {
                val direction = when (condition.direction) {
                    "Över" -> AlertRule.PriceComparisonType.ABOVE
                    "Under" -> AlertRule.PriceComparisonType.BELOW
                    else -> return null
                }
                AlertRule.SingleKeyMetric(symbol, AlertRule.KeyMetricType.DIVIDEND_YIELD, value, direction)
            }
            "52w High Drop" -> {
                // For now, assume percentage drop
                AlertRule.SingleDrawdownFromHigh(symbol, AlertRule.DrawdownDropType.PERCENTAGE, value)
            }
            "Dagsrörelse" -> {
                // For daily move, value is the percentage threshold
                AlertRule.SingleDailyMove(symbol, value, AlertRule.DailyMoveDirection.BOTH)
            }
            else -> null
        }
    }

    /**
     * Creates an adapter for displaying stock search results.
     * The adapter handles both the input field display and dropdown items.
     *
     * @return ArrayAdapter<StockSearchResult> configured for displaying stock search results
     */
    internal fun createStockAdapter(): ArrayAdapter<StockSearchResult> {
        return object : ArrayAdapter<StockSearchResult>(
            this,
            R.layout.dropdown_item_with_icon,
            mutableListOf()
        ) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                return createAdapterItemView(position, convertView, parent)
            }

            override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View {
                return createAdapterItemView(position, convertView, parent)
            }

            private fun createAdapterItemView(position: Int, convertView: View?, parent: ViewGroup): View {
                val view: View = convertView ?: LayoutInflater.from(context)
                    .inflate(R.layout.dropdown_item_with_icon, parent, false)
                
                val item: StockSearchResult? = getItem(position)
                if (item != null) {
                    val textView = view.findViewById<TextView>(R.id.text)
                    val iconView = view.findViewById<ImageView>(R.id.icon)
                    
                    textView.text = "${item.symbol} - ${item.name}"
                    
                    // Visa ikon baserat på typ
                    if (item.isCrypto) {
                        iconView.setImageResource(R.drawable.ic_crypto)
                        iconView.visibility = View.VISIBLE
                    } else {
                        iconView.setImageResource(R.drawable.ic_stock)
                        iconView.visibility = View.VISIBLE
                    }
                }
                
                return view
            }

            override fun getFilter(): Filter {
                return object : Filter() {
                    @Suppress("UNCHECKED_CAST")
                    override fun performFiltering(constraint: CharSequence?): FilterResults {
                        val filterResults: FilterResults = FilterResults()
                        filterResults.values = mutableListOf<StockSearchResult>()
                        filterResults.count = 0
                        return filterResults
                    }

                    @Suppress("UNCHECKED_CAST")
                    override fun publishResults(constraint: CharSequence?, results: FilterResults?): Unit {
                        // Do nothing - we handle filtering through the ViewModel
                    }
                }
            }
        }
    }

    /**
     * Sets up the stock search functionality for an input field.
     * Configures the input field with debounced search, dropdown display,
     * and result handling through the ViewModel.
     *
     * @param input The AutoCompleteTextView to set up search for
     * @param adapter The adapter to display search results
     * @param viewModel The ViewModel handling the search logic
     */
    internal fun setupStockSearch(
        input: MaterialAutoCompleteTextView, 
        adapter: ArrayAdapter<StockSearchResult>,
        viewModel: StockSearchViewModel,
        includeCrypto: Boolean = true
    ) {
        Log.d(TAG, "Setting up search for input: ${input.id} (includeCrypto: $includeCrypto)")
        
        // Ensure the input is set up correctly
        input.threshold = 2  // Start showing suggestions after 2 characters
        input.setAdapter(adapter)  // Make sure adapter is set
        
        // Create a separate coroutine scope for this input
        lifecycleScope.launch {
            viewModel.searchState
                .collect { state ->
                    Log.d(TAG, "Search state changed for input ${input.id}: $state")
                    when (state) {
                        is SearchState.Loading -> {
                            Log.d(TAG, "Loading state for input ${input.id}")
                        }
                        is SearchState.Success -> {
                            Log.d(TAG, "Success state for input ${input.id}, results: ${state.results}")
                            adapter.clear()
                            adapter.addAll(state.results)
                            adapter.notifyDataSetChanged()
                            
                            if (state.results.isNotEmpty() && input.text.isNotEmpty()) {
                                input.post {
                                    if (input.hasFocus()) {
                                        Log.d(TAG, "Showing dropdown for input ${input.id}")
                                        input.showDropDown()
                                    }
                                }
                            }
                        }
                        is SearchState.Error -> {
                            Log.e(TAG, "Error state for input ${input.id}: ${state.message}")
                            adapter.clear()
                            adapter.notifyDataSetChanged()
                        }
                    }
                }
        }

        var textChangeJob: Job? = null
        
        input.doAfterTextChanged { text ->
            Log.d(TAG, "Text changed in input ${input.id}: $text")
            textChangeJob?.cancel()
            
            if (text.isNullOrEmpty()) {
                Log.d(TAG, "Clearing adapter for input ${input.id}")
                adapter.clear()
                adapter.notifyDataSetChanged()
                input.dismissDropDown()
                return@doAfterTextChanged
            }
            
            textChangeJob = lifecycleScope.launch {
                delay(300) // Debounce time
                Log.d(TAG, "Searching for: $text")
                viewModel.search(text.toString(), includeCrypto)
            }
        }

        input.setOnFocusChangeListener { _, hasFocus ->
            Log.d(TAG, "Focus changed for input ${input.id}, hasFocus: $hasFocus")
            if (hasFocus && input.text.isNotEmpty() && adapter.count > 0) {
                Log.d(TAG, "Showing dropdown on focus gain")
                input.post { input.showDropDown() }
            }
        }
    }

    /**
     * Shows a dialog for editing an existing watch item.
     * Handles validation and updates through the ViewModel.
     *
     * @param item The WatchItem to edit
     */
    private fun showEditStockPairDialog(item: WatchItem) {
        if (item.watchType !is WatchType.PricePair) return
        val pricePair = item.watchType
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_add_stock_pair, null)
        val ticker1Input = dialogView.findViewById<MaterialAutoCompleteTextView>(R.id.ticker1Input).apply { setText(item.ticker1) }
        val ticker2Input = dialogView.findViewById<MaterialAutoCompleteTextView>(R.id.ticker2Input).apply { setText(item.ticker2) }
        val priceDifferenceInput = dialogView.findViewById<TextInputEditText>(R.id.priceDifferenceInput).apply { setText(pricePair.priceDifference.toString()) }
        val notifyWhenEqualCheckbox = dialogView.findViewById<MaterialCheckBox>(R.id.notifyWhenEqualCheckbox).apply { isChecked = pricePair.notifyWhenEqual }

        // Set up adapters
        val adapter1 = createStockAdapter()
        val adapter2 = createStockAdapter()
        ticker1Input.setAdapter(adapter1)
        ticker2Input.setAdapter(adapter2)

        // Set up search functionality
        setupStockSearch(ticker1Input, adapter1, stockSearchViewModel1, includeCrypto = false)
        setupStockSearch(ticker2Input, adapter2, stockSearchViewModel2, includeCrypto = false)

        // Set up item click listeners
        ticker1Input.setOnItemClickListener { _, _, position, _ ->
            selectedStock1 = adapter1.getItem(position)
            Log.d(TAG, "Selected stock 1: $selectedStock1")
        }

        ticker2Input.setOnItemClickListener { _, _, position, _ ->
            selectedStock2 = adapter2.getItem(position)
            Log.d(TAG, "Selected stock 2: $selectedStock2")
        }

        MaterialAlertDialogBuilder(this)
            .setTitle("Redigera aktiepar")
            .setView(dialogView)
            .setPositiveButton("Uppdatera") { _, _ ->
                val ticker1Str = ticker1Input.text.toString().trim()
                val ticker2Str = ticker2Input.text.toString().trim()
                val priceDifferenceStr = priceDifferenceInput.text.toString()
                val notifyWhenEqual = notifyWhenEqualCheckbox.isChecked

                val finalTicker1 = selectedStock1?.symbol ?: ticker1Str
                val finalTicker2 = selectedStock2?.symbol ?: ticker2Str

                if (finalTicker1.isNotEmpty() && finalTicker2.isNotEmpty() && priceDifferenceStr.isNotEmpty()) {
                    val priceDifference = priceDifferenceStr.toDoubleOrNull() ?: 0.0
                    lifecycleScope.launch {
                        try {
                            binding.progressBar.visibility = View.VISIBLE
                            
                            val updatedItem = item.copy(
                                watchType = WatchType.PricePair(priceDifference, notifyWhenEqual),
                                ticker1 = finalTicker1,
                                ticker2 = finalTicker2,
                                companyName1 = selectedStock1?.name ?: item.companyName1,
                                companyName2 = selectedStock2?.name ?: item.companyName2
                            )
                            
                            viewModel.updateWatchItem(updatedItem)
                            viewModel.refreshWatchItems()
                            updateLastUpdateTime()
                            binding.progressBar.visibility = View.GONE
                            Toast.makeText(this@MainActivity, "Aktiepar uppdaterat", Toast.LENGTH_SHORT).show()
                        } catch (e: Exception) {
                            binding.progressBar.visibility = View.GONE
                            Toast.makeText(this@MainActivity, "Kunde inte uppdatera aktiepar: ${e.message}", Toast.LENGTH_LONG).show()
                        }
                    }
                } else {
                    Toast.makeText(this, "Fyll i alla fält", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Avbryt", null)
            .show()
    }

    /**
     * Shows a dialog for editing an existing price target watch.
     */
    private fun showEditPriceTargetDialog(item: WatchItem) {
        if (item.watchType !is WatchType.PriceTarget) return
        val priceTarget = item.watchType
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_add_price_target, null)
        val tickerInput = dialogView.findViewById<MaterialAutoCompleteTextView>(R.id.tickerInput).apply { setText(item.ticker) }
        val targetPriceInput = dialogView.findViewById<TextInputEditText>(R.id.targetPriceInput).apply { setText(priceTarget.targetPrice.toString()) }
        // Set up adapter for stock search
        val adapter = createStockAdapter()
        tickerInput.setAdapter(adapter)
        setupStockSearch(tickerInput, adapter, stockSearchViewModel, includeCrypto = true)

        tickerInput.setOnItemClickListener { _, _, position, _ ->
            selectedStock = adapter.getItem(position)
            Log.d(TAG, "Selected stock: $selectedStock")
        }

        MaterialAlertDialogBuilder(this)
            .setTitle("Redigera prisbevakning")
            .setView(dialogView)
            .setPositiveButton("Uppdatera") { _, _ ->
                val tickerStr = tickerInput.text.toString().trim()
                val targetPriceStr = targetPriceInput.text.toString()

                val finalTicker = selectedStock?.symbol ?: tickerStr

                if (finalTicker.isNotEmpty() && targetPriceStr.isNotEmpty()) {
                    val targetPrice = targetPriceStr.toDoubleOrNull()

                    if (targetPrice != null && targetPrice > 0) {
                        val direction = if (item.currentPrice > 0.0 && item.currentPrice >= targetPrice)
                            WatchType.PriceDirection.BELOW else WatchType.PriceDirection.ABOVE
                        lifecycleScope.launch {
                            try {
                                binding.progressBar.visibility = View.VISIBLE

                                val updatedItem = item.copy(
                                    watchType = WatchType.PriceTarget(targetPrice, direction),
                                    ticker = finalTicker,
                                    companyName = selectedStock?.name ?: item.companyName
                                )

                                viewModel.updateWatchItem(updatedItem)
                                viewModel.refreshWatchItems()
                                updateLastUpdateTime()
                                binding.progressBar.visibility = View.GONE
                                Toast.makeText(this@MainActivity, "Prisbevakning uppdaterad", Toast.LENGTH_SHORT).show()
                            } catch (e: Exception) {
                                binding.progressBar.visibility = View.GONE
                                Toast.makeText(this@MainActivity, "Kunde inte uppdatera prisbevakning: ${e.message}", Toast.LENGTH_LONG).show()
                            }
                        }
                    } else {
                        Toast.makeText(this, "Ange ett giltigt målpris", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(this, "Fyll i alla fält", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Avbryt", null)
            .show()
    }

    /**
     * Shows a dialog for editing an existing key metrics watch.
     */
    private fun showEditKeyMetricsDialog(item: WatchItem) {
        if (item.watchType !is WatchType.KeyMetrics) return
        val keyMetrics = item.watchType
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_add_key_metrics, null)
        val tickerInput = dialogView.findViewById<MaterialAutoCompleteTextView>(R.id.tickerInput).apply { setText(item.ticker) }
        val metricTypeInput = dialogView.findViewById<MaterialAutoCompleteTextView>(R.id.metricTypeInput).apply {
            setText(when (keyMetrics.metricType) {
                WatchType.MetricType.PE_RATIO -> "P/E-tal"
                WatchType.MetricType.PS_RATIO -> "P/S-tal"
                WatchType.MetricType.DIVIDEND_YIELD -> "Utdelningsprocent"
            })
        }
        val targetValueInput = dialogView.findViewById<TextInputEditText>(R.id.targetValueInput).apply {
            setText(keyMetrics.targetValue.toString())
        }
        // History UI elements - hidden
        val historyCard = dialogView.findViewById<CardView>(R.id.historyCard)
        historyCard.visibility = View.GONE

        // Set up adapter for stock search
        val adapter = createStockAdapter()
        tickerInput.setAdapter(adapter)
        setupStockSearch(tickerInput, adapter, stockSearchViewModel, includeCrypto = false)

        tickerInput.setOnItemClickListener { _, _, position, _ ->
            selectedStock = adapter.getItem(position)
            Log.d(TAG, "Selected stock: $selectedStock")
        }

        // Set up metric type dropdown
        val metricTypes = arrayOf("P/E-tal", "P/S-tal", "Utdelningsprocent")
        val metricTypeAdapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, metricTypes)
        metricTypeInput.setAdapter(metricTypeAdapter)
        metricTypeInput.setOnItemClickListener { _, _, position, _ ->
            Log.d(TAG, "Selected metric type: ${metricTypes[position]}")
        }

        MaterialAlertDialogBuilder(this)
            .setTitle("Redigera nyckeltalsbevakning")
            .setView(dialogView)
            .setPositiveButton("Uppdatera") { _, _ ->
                val tickerStr = tickerInput.text.toString().trim()
                val metricTypeStr = metricTypeInput.text.toString()
                val targetValueStr = targetValueInput.text.toString()

                val finalTicker = selectedStock?.symbol ?: tickerStr

                if (finalTicker.isNotEmpty() && metricTypeStr.isNotEmpty() && targetValueStr.isNotEmpty()) {
                    val metricType = when (metricTypeStr) {
                        "P/E-tal" -> WatchType.MetricType.PE_RATIO
                        "P/S-tal" -> WatchType.MetricType.PS_RATIO
                        "Utdelningsprocent" -> WatchType.MetricType.DIVIDEND_YIELD
                        else -> null
                    }
                    val targetValue = targetValueStr.toDoubleOrNull()

                    if (metricType != null && targetValue != null && targetValue > 0) {
                        val direction = if (item.currentMetricValue > 0.0 && item.currentMetricValue >= targetValue)
                            WatchType.PriceDirection.BELOW else WatchType.PriceDirection.ABOVE
                        lifecycleScope.launch {
                            try {
                                binding.progressBar.visibility = View.VISIBLE
                                val updatedItem = item.copy(
                                    watchType = WatchType.KeyMetrics(metricType, targetValue, direction),
                                    ticker = finalTicker,
                                    companyName = selectedStock?.name ?: item.companyName
                                )
                                viewModel.updateWatchItem(updatedItem)
                                viewModel.refreshWatchItems()
                                updateLastUpdateTime()
                                binding.progressBar.visibility = View.GONE
                                Toast.makeText(this@MainActivity, "Nyckeltalsbevakning uppdaterad", Toast.LENGTH_SHORT).show()
                            } catch (e: Exception) {
                                binding.progressBar.visibility = View.GONE
                                Toast.makeText(this@MainActivity, "Kunde inte uppdatera nyckeltalsbevakning: ${e.message}", Toast.LENGTH_LONG).show()
                            }
                        }
                    } else {
                        Toast.makeText(this, "Ange giltiga värden för alla fält", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(this, "Fyll i alla fält", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Avbryt", null)
            .show()
    }

    /**
     * Shows a dialog for editing an existing ATH-based watch.
     */
    private fun showEditATHBasedDialog(item: WatchItem) {
        if (item.watchType !is WatchType.ATHBased) return
        val athBased = item.watchType
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_add_ath_based, null)
        val tickerInput = dialogView.findViewById<MaterialAutoCompleteTextView>(R.id.tickerInput).apply { setText(item.ticker) }
        val dropTypeInput = dialogView.findViewById<MaterialAutoCompleteTextView>(R.id.dropTypeInput).apply {
            setText(when (athBased.dropType) {
                WatchType.DropType.PERCENTAGE -> "Procent"
                WatchType.DropType.ABSOLUTE -> "Absolut (SEK)"
            })
        }
        val dropValueInput = dialogView.findViewById<TextInputEditText>(R.id.dropValueInput).apply {
            setText(athBased.dropValue.toString())
        }

        // Set up adapter for stock search
        val adapter = createStockAdapter()
        tickerInput.setAdapter(adapter)
        setupStockSearch(tickerInput, adapter, stockSearchViewModel, includeCrypto = true)

        tickerInput.setOnItemClickListener { _, _, position, _ ->
            selectedStock = adapter.getItem(position)
            Log.d(TAG, "Selected stock: $selectedStock")
        }

        // Set up drop type dropdown
        val dropTypes = arrayOf("Procent", "Absolut (SEK)")
        val dropTypeAdapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, dropTypes)
        dropTypeInput.setAdapter(dropTypeAdapter)
        dropTypeInput.setOnItemClickListener { _, _, position, _ ->
            Log.d(TAG, "Selected drop type: ${dropTypes[position]}")
        }

        MaterialAlertDialogBuilder(this)
            .setTitle("Redigera ATH-bevakning")
            .setView(dialogView)
            .setPositiveButton("Uppdatera") { _, _ ->
                val tickerStr = tickerInput.text.toString().trim()
                val dropTypeStr = dropTypeInput.text.toString()
                val dropValueStr = dropValueInput.text.toString()

                val finalTicker = selectedStock?.symbol ?: tickerStr

                if (finalTicker.isNotEmpty() && dropTypeStr.isNotEmpty() && dropValueStr.isNotEmpty()) {
                    val dropType = when (dropTypeStr) {
                        "Procent" -> WatchType.DropType.PERCENTAGE
                        "Absolut (SEK)" -> WatchType.DropType.ABSOLUTE
                        else -> null
                    }
                    val dropValue = dropValueStr.toDoubleOrNull()

                    if (dropType != null && dropValue != null && dropValue > 0) {
                        lifecycleScope.launch {
                            try {
                                binding.progressBar.visibility = View.VISIBLE

                                val updatedItem = item.copy(
                                    watchType = WatchType.ATHBased(dropType, dropValue),
                                    ticker = finalTicker,
                                    companyName = selectedStock?.name ?: item.companyName
                                )

                                viewModel.updateWatchItem(updatedItem)
                                viewModel.refreshWatchItems()
                                updateLastUpdateTime()
                                binding.progressBar.visibility = View.GONE
                                Toast.makeText(this@MainActivity, "ATH-bevakning uppdaterad", Toast.LENGTH_SHORT).show()
                            } catch (e: Exception) {
                                binding.progressBar.visibility = View.GONE
                                Toast.makeText(this@MainActivity, "Kunde inte uppdatera ATH-bevakning: ${e.message}", Toast.LENGTH_LONG).show()
                            }
                        }
                    } else {
                        Toast.makeText(this, "Ange giltiga värden för alla fält", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(this, "Fyll i alla fält", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Avbryt", null)
            .show()
    }

    private fun showEditPriceRangeDialog(item: WatchItem) {
        if (item.watchType !is WatchType.PriceRange) return
        val priceRange = item.watchType
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_add_price_range, null)
        val minPriceInput = dialogView.findViewById<TextInputEditText>(R.id.minPriceInput).apply {
            setText(priceRange.minPrice.toString())
        }
        val maxPriceInput = dialogView.findViewById<TextInputEditText>(R.id.maxPriceInput).apply {
            setText(priceRange.maxPrice.toString())
        }

        MaterialAlertDialogBuilder(this)
            .setTitle("Redigera prisintervall-bevakning")
            .setView(dialogView)
            .setPositiveButton("Uppdatera") { _, _ ->
                val minPriceStr = minPriceInput.text.toString()
                val maxPriceStr = maxPriceInput.text.toString()

                if (minPriceStr.isNotEmpty() && maxPriceStr.isNotEmpty()) {
                    val minPrice = minPriceStr.toDoubleOrNull()
                    val maxPrice = maxPriceStr.toDoubleOrNull()

                    if (minPrice != null && maxPrice != null && minPrice > 0 && maxPrice > minPrice) {
                        lifecycleScope.launch {
                            try {
                                binding.progressBar.visibility = View.VISIBLE
                                val updatedItem = item.copy(
                                    watchType = WatchType.PriceRange(minPrice, maxPrice)
                                )
                                viewModel.updateWatchItem(updatedItem)
                                viewModel.refreshWatchItems()
                                updateLastUpdateTime()
                                binding.progressBar.visibility = View.GONE
                                Toast.makeText(this@MainActivity, "Prisintervall-bevakning uppdaterad", Toast.LENGTH_SHORT).show()
                            } catch (e: Exception) {
                                binding.progressBar.visibility = View.GONE
                                Toast.makeText(this@MainActivity, "Kunde inte uppdatera bevakning: ${e.message}", Toast.LENGTH_LONG).show()
                            }
                        }
                    } else {
                        Toast.makeText(this, "Ange giltiga prisintervall (min < max)", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(this, "Fyll i alla fält", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Avbryt", null)
            .show()
    }

    private fun showEditDailyMoveDialog(item: WatchItem) {
        if (item.watchType !is WatchType.DailyMove) return
        val dailyMove = item.watchType
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_add_daily_move, null)
        val tickerInput = dialogView.findViewById<MaterialAutoCompleteTextView?>(R.id.tickerInput)
        val tickerInputLayout = tickerInput?.parent as? com.google.android.material.textfield.TextInputLayout
        tickerInputLayout?.visibility = View.GONE

        val thresholdInput = dialogView.findViewById<TextInputEditText>(R.id.thresholdInput).apply {
            setText(dailyMove.percentThreshold.toString())
        }
        val directionInput = dialogView.findViewById<MaterialAutoCompleteTextView>(R.id.directionInput).apply {
            setText(when (dailyMove.direction) {
                WatchType.DailyMoveDirection.UP -> "Upp"
                WatchType.DailyMoveDirection.DOWN -> "Ned"
                WatchType.DailyMoveDirection.BOTH -> "Båda"
            })
        }

        val directions = arrayOf("Upp", "Ned", "Båda")
        val directionAdapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, directions)
        directionInput.setAdapter(directionAdapter)

        MaterialAlertDialogBuilder(this)
            .setTitle("Redigera dagsrörelse-bevakning")
            .setView(dialogView)
            .setPositiveButton("Uppdatera") { _, _ ->
                val thresholdStr = thresholdInput.text.toString()
                val directionStr = directionInput.text.toString()

                if (thresholdStr.isNotEmpty() && directionStr.isNotEmpty()) {
                    val threshold = thresholdStr.toDoubleOrNull()
                    val direction = when (directionStr) {
                        "Upp" -> WatchType.DailyMoveDirection.UP
                        "Ned" -> WatchType.DailyMoveDirection.DOWN
                        "Båda" -> WatchType.DailyMoveDirection.BOTH
                        else -> WatchType.DailyMoveDirection.BOTH
                    }

                    if (threshold != null && threshold > 0) {
                        lifecycleScope.launch {
                            try {
                                binding.progressBar.visibility = View.VISIBLE
                                val updatedItem = item.copy(
                                    watchType = WatchType.DailyMove(threshold, direction)
                                )
                                viewModel.updateWatchItem(updatedItem)
                                viewModel.refreshWatchItems()
                                updateLastUpdateTime()
                                binding.progressBar.visibility = View.GONE
                                Toast.makeText(this@MainActivity, "Dagsrörelse-bevakning uppdaterad", Toast.LENGTH_SHORT).show()
                            } catch (e: Exception) {
                                binding.progressBar.visibility = View.GONE
                                Toast.makeText(this@MainActivity, "Kunde inte uppdatera bevakning: ${e.message}", Toast.LENGTH_LONG).show()
                            }
                        }
                    } else {
                        Toast.makeText(this, "Ange ett giltigt tröskelvärde", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(this, "Fyll i alla fält", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Avbryt", null)
            .show()
    }

    /**
     * Shows a detailed view dialog for a watch item where the user can view and edit all values.
     */
    private fun showWatchItemDetailDialog(item: WatchItem) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_watch_item_detail, null)
        
        // Set watch type chip
        val watchTypeChip = dialogView.findViewById<com.google.android.material.chip.Chip>(R.id.watchTypeChip)
        watchTypeChip.text = item.getWatchTypeDisplayName()

        when (item.watchType) {
            is WatchType.PricePair -> showPricePairDetail(dialogView, item)
            is WatchType.PriceTarget -> showPriceTargetDetail(dialogView, item)
            is WatchType.PriceRange -> {
                // PriceRange hanteras via StockDetailFragment
                Toast.makeText(this, "Redigera via aktiedetaljvy", Toast.LENGTH_SHORT).show()
            }
            is WatchType.KeyMetrics -> showKeyMetricsDetail(dialogView, item)
            is WatchType.ATHBased -> showATHBasedDetail(dialogView, item)
            is WatchType.DailyMove -> {
                // DailyMove hanteras via StockDetailFragment
                Toast.makeText(this, "Redigera via aktiedetaljvy", Toast.LENGTH_SHORT).show()
            }
            is WatchType.Combined -> {
                // Combined alerts navigeras till StockDetailFragment direkt
                // Denna kod kommer inte användas
            }
        }

        MaterialAlertDialogBuilder(this)
            .setTitle("Bevakningsdetaljer")
            .setView(dialogView)
            .setPositiveButton("Spara") { _, _ ->
                saveWatchItemChanges(dialogView, item)
            }
            .setNegativeButton("Avbryt", null)
            .show()
    }

    private fun showPricePairDetail(dialogView: android.view.View, item: WatchItem) {
        val pricePairDetails = dialogView.findViewById<android.view.View>(R.id.pricePairDetails)
        pricePairDetails.visibility = android.view.View.VISIBLE
        
        val detailStock1Name = dialogView.findViewById<TextView>(R.id.detailStock1Name)
        val detailStock1Price = dialogView.findViewById<TextView>(R.id.detailStock1Price)
        val detailStock2Name = dialogView.findViewById<TextView>(R.id.detailStock2Name)
        val detailStock2Price = dialogView.findViewById<TextView>(R.id.detailStock2Price)
        val detailPriceDifference = dialogView.findViewById<TextInputEditText>(R.id.detailPriceDifference)
        val detailNotifyWhenEqual = dialogView.findViewById<MaterialCheckBox>(R.id.detailNotifyWhenEqual)

        val pricePair = item.watchType as WatchType.PricePair

        detailStock1Name.text = "${item.companyName1 ?: item.ticker1} (${item.ticker1})"
        detailStock1Price.text = item.formatPrice1()
        detailStock2Name.text = "${item.companyName2 ?: item.ticker2} (${item.ticker2})"
        detailStock2Price.text = item.formatPrice2()
        detailPriceDifference.setText(pricePair.priceDifference.toString())
        detailNotifyWhenEqual.isChecked = pricePair.notifyWhenEqual
    }

    private fun showPriceTargetDetail(dialogView: android.view.View, item: WatchItem) {
        val priceTargetDetails = dialogView.findViewById<android.view.View>(R.id.priceTargetDetails)
        priceTargetDetails.visibility = android.view.View.VISIBLE
        
        val detailStockName = dialogView.findViewById<TextView>(R.id.detailStockName)
        val detailCurrentPrice = dialogView.findViewById<TextView>(R.id.detailCurrentPrice)
        val detailTargetPrice = dialogView.findViewById<TextInputEditText>(R.id.detailTargetPrice)

        val priceTarget = item.watchType as WatchType.PriceTarget

        detailStockName.text = "${item.companyName ?: item.ticker} (${item.ticker})"
        detailCurrentPrice.text = item.formatPrice()
        detailTargetPrice.setText(priceTarget.targetPrice.toString())
    }

    private fun showKeyMetricsDetail(dialogView: android.view.View, item: WatchItem) {
        val keyMetricsDetails = dialogView.findViewById<android.view.View>(R.id.keyMetricsDetails)
        keyMetricsDetails.visibility = android.view.View.VISIBLE
        
        val detailKeyMetricsStockName = dialogView.findViewById<TextView>(R.id.detailKeyMetricsStockName)
        val detailCurrentMetricValue = dialogView.findViewById<TextView>(R.id.detailCurrentMetricValue)
        val detailMetricType = dialogView.findViewById<MaterialAutoCompleteTextView>(R.id.detailMetricType)
        val detailTargetValue = dialogView.findViewById<TextInputEditText>(R.id.detailTargetValue)

        val keyMetrics = item.watchType as WatchType.KeyMetrics

        detailKeyMetricsStockName.text = "${item.companyName ?: item.ticker} (${item.ticker})"

        val metricTypeName = when (keyMetrics.metricType) {
            WatchType.MetricType.PE_RATIO -> "P/E-tal"
            WatchType.MetricType.PS_RATIO -> "P/S-tal"
            WatchType.MetricType.DIVIDEND_YIELD -> "Utdelningsprocent"
        }
        detailCurrentMetricValue.text = "$metricTypeName: ${item.formatMetricValue()}"

        val metricTypes = arrayOf("P/E-tal", "P/S-tal", "Utdelningsprocent")
        val metricTypeAdapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, metricTypes)
        detailMetricType.setAdapter(metricTypeAdapter)
        detailMetricType.setText(metricTypeName, false)

        detailTargetValue.setText(keyMetrics.targetValue.toString())
    }

    private fun showATHBasedDetail(dialogView: android.view.View, item: WatchItem) {
        val athBasedDetails = dialogView.findViewById<android.view.View>(R.id.athBasedDetails)
        athBasedDetails.visibility = android.view.View.VISIBLE
        
        val detailATHStockName = dialogView.findViewById<TextView>(R.id.detailATHStockName)
        val detailATHInfo = dialogView.findViewById<TextView>(R.id.detailATHInfo)
        val detailATHDropType = dialogView.findViewById<MaterialAutoCompleteTextView>(R.id.detailATHDropType)
        val detailATHDropValue = dialogView.findViewById<TextInputEditText>(R.id.detailATHDropValue)

        val athBased = item.watchType as WatchType.ATHBased

        detailATHStockName.text = "${item.companyName ?: item.ticker} (${item.ticker})"
        
        val athInfoText = if (item.currentATH > 0.0) {
            "ATH: ${priceFormat.format(item.currentATH)} SEK | Nedgång: ${item.formatATHDrop()}"
        } else {
            "ATH: Loading... | Nedgång: ${item.formatATHDrop()}"
        }
        detailATHInfo.text = athInfoText
        
        val dropTypes = arrayOf("Procent", "Absolut (SEK)")
        val dropTypeAdapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, dropTypes)
        detailATHDropType.setAdapter(dropTypeAdapter)
        detailATHDropType.setText(when (athBased.dropType) {
            WatchType.DropType.PERCENTAGE -> "Procent"
            WatchType.DropType.ABSOLUTE -> "Absolut (SEK)"
        }, false)
        
        detailATHDropValue.setText(athBased.dropValue.toString())
    }

    private fun saveWatchItemChanges(dialogView: android.view.View, item: WatchItem) {
        lifecycleScope.launch {
            try {
                binding.progressBar.visibility = View.VISIBLE

                val updatedItem = when (item.watchType) {
                    is WatchType.PricePair -> {
                        val priceDifferenceInput = dialogView.findViewById<TextInputEditText>(R.id.detailPriceDifference)
                        val notifyWhenEqualCheckbox = dialogView.findViewById<MaterialCheckBox>(R.id.detailNotifyWhenEqual)
                        
                        val priceDifference = priceDifferenceInput.text.toString().toDoubleOrNull() ?: 0.0
                        val notifyWhenEqual = notifyWhenEqualCheckbox.isChecked
                        
                        item.copy(
                            watchType = WatchType.PricePair(priceDifference, notifyWhenEqual)
                        )
                    }
                    is WatchType.PriceTarget -> {
                        val targetPriceInput = dialogView.findViewById<TextInputEditText>(R.id.detailTargetPrice)

                        val targetPrice = targetPriceInput.text.toString().toDoubleOrNull() ?: 0.0
                        val direction = if (item.currentPrice > 0.0 && item.currentPrice >= targetPrice)
                            WatchType.PriceDirection.BELOW else WatchType.PriceDirection.ABOVE

                        item.copy(
                            watchType = WatchType.PriceTarget(targetPrice, direction)
                        )
                    }
                    is WatchType.PriceRange -> {
                        // PriceRange hanteras via StockDetailFragment
                        item
                    }
                    is WatchType.DailyMove -> {
                        // DailyMove hanteras via StockDetailFragment
                        item
                    }
                    is WatchType.KeyMetrics -> {
                        val metricTypeInput = dialogView.findViewById<MaterialAutoCompleteTextView>(R.id.detailMetricType)
                        val targetValueInput = dialogView.findViewById<TextInputEditText>(R.id.detailTargetValue)

                        val metricType = when (metricTypeInput.text.toString()) {
                            "P/E-tal" -> WatchType.MetricType.PE_RATIO
                            "P/S-tal" -> WatchType.MetricType.PS_RATIO
                            "Utdelningsprocent" -> WatchType.MetricType.DIVIDEND_YIELD
                            else -> {
                                val keyMetrics = item.watchType as? WatchType.KeyMetrics
                                keyMetrics?.metricType ?: WatchType.MetricType.PE_RATIO
                            }
                        }
                        val targetValue = targetValueInput.text.toString().toDoubleOrNull() ?: 0.0
                        val direction = if (item.currentMetricValue > 0.0 && item.currentMetricValue >= targetValue)
                            WatchType.PriceDirection.BELOW else WatchType.PriceDirection.ABOVE

                        item.copy(
                            watchType = WatchType.KeyMetrics(metricType, targetValue, direction)
                        )
                    }
                    is WatchType.Combined -> {
                        // Combined alerts kan inte redigeras via denna dialog ännu
                        item
                    }
                    is WatchType.ATHBased -> {
                        val dropTypeInput = dialogView.findViewById<MaterialAutoCompleteTextView>(R.id.detailATHDropType)
                        val dropValueInput = dialogView.findViewById<TextInputEditText>(R.id.detailATHDropValue)
                        
                        val dropType = when (dropTypeInput.text.toString()) {
                            "Procent" -> WatchType.DropType.PERCENTAGE
                            "Absolut (SEK)" -> WatchType.DropType.ABSOLUTE
                            else -> {
                                val athBased = item.watchType as? WatchType.ATHBased
                                athBased?.dropType ?: WatchType.DropType.PERCENTAGE
                            }
                        }
                        val dropValue = dropValueInput.text.toString().toDoubleOrNull() ?: 0.0
                        
                        item.copy(
                            watchType = WatchType.ATHBased(dropType, dropValue)
                        )
                    }
                }

                viewModel.updateWatchItem(updatedItem)
                viewModel.refreshWatchItems()
                updateLastUpdateTime()
                binding.progressBar.visibility = View.GONE
                Toast.makeText(this@MainActivity, "Bevakning uppdaterad", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                binding.progressBar.visibility = View.GONE
                Toast.makeText(this@MainActivity, "Kunde inte uppdatera bevakning: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)


    private fun setupSwipeRefresh() {
        binding.swipeRefreshLayout.setOnRefreshListener {
            refreshPrices()
        }
    }

    fun refreshPrices() {
        lifecycleScope.launch {
            try {
                viewModel.refreshWatchItems()
                updateLastUpdateTime()
            } catch (e: Exception) {
                Toast.makeText(this@MainActivity, "Failed to refresh: ${e.message}", Toast.LENGTH_LONG).show()
            } finally {
                binding.swipeRefreshLayout.isRefreshing = false
            }
        }
    }

    private fun requestNotificationPermission() {
        if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    /**
     * Shows a confirmation dialog for deleting a watch item.
     * Handles the deletion through the ViewModel if confirmed.
     *
     * @param item The WatchItem to delete
     */
    private fun showDeleteConfirmationDialog(item: WatchItem) {
        MaterialAlertDialogBuilder(this)
            .setTitle("Ta bort bevakning")
            .setMessage("Är du säker på att du vill ta bort bevakningen ${item.getDisplayName()}?")
            .setPositiveButton("Ta bort") { _, _ ->
                lifecycleScope.launch {
                    try {
                        viewModel.deleteWatchItem(item)
                        Toast.makeText(this@MainActivity, "Bevakning borttagen", Toast.LENGTH_SHORT).show()
                    } catch (e: Exception) {
                        Toast.makeText(this@MainActivity, "Kunde inte ta bort bevakning: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }
            .setNegativeButton("Avbryt", null)
            .show()
    }

    companion object {
        /** Tag for logging purposes */
        private const val TAG = "MainActivity"
        /** Format pattern for time display */
        private const val TIME_FORMAT = "HH:mm:ss"
        /** Price format with Swedish locale */
        private val priceFormat = DecimalFormat("#,##0.00", DecimalFormatSymbols(Locale("sv", "SE")))
    }
} 
