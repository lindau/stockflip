package com.stockflip

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.ArrayAdapter
import android.widget.Filter
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.edit
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.Lifecycle
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.checkbox.MaterialCheckBox
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.stockflip.databinding.ActivityMainBinding
import androidx.cardview.widget.CardView
import androidx.recyclerview.widget.ItemTouchHelper
import com.stockflip.ui.SwipeToDeleteCallback
import com.stockflip.backup.BackupManager
import com.stockflip.ui.builders.ConditionBuilderAdapter
import com.stockflip.ui.dialogs.focusInput
import com.stockflip.repository.SearchState
import com.stockflip.repository.StockRepository
import com.stockflip.viewmodel.StockSearchViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

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
    private val importFileLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri ?: return@registerForActivityResult
        lifecycleScope.launch {
            val json = contentResolver.openInputStream(uri)?.bufferedReader()?.readText() ?: return@launch
            showImportConfirmationDialog(json)
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

    private enum class MainTab {
        STOCKS,
        PAIRS,
        ALERTS
    }

    private var currentMainTab: MainTab = MainTab.STOCKS
    private var lastWatchItems: List<WatchItemUiState> = emptyList()

    /**
     * Initializes the activity's UI components and starts data loading.
     * Sets up the view binding, initializes UI elements, and requests necessary permissions.
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        onBackPressedDispatcher.addCallback(this, backPressCallback)
        initializeUI()
        initializeUpdates()
        requestPermissions()
        loadInitialData()
        handleDeepLinkIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleDeepLinkIntent(intent)
    }

    private fun handleDeepLinkIntent(intent: Intent?) {
        if (intent == null) return
        val pairWatchItemId = intent.getIntExtra(EXTRA_OPEN_PAIR_WATCH_ID, -1)
        if (pairWatchItemId != -1) {
            intent.removeExtra(EXTRA_OPEN_PAIR_WATCH_ID)
            navigateToPairDetail(pairWatchItemId)
            return
        }
        val ticker = intent.getStringExtra(EXTRA_OPEN_TICKER) ?: return
        intent.removeExtra(EXTRA_OPEN_TICKER)
        val companyName = intent.getStringExtra(EXTRA_OPEN_COMPANY)
        navigateToStockDetail(ticker, companyName)
    }

    private val backPressCallback = object : androidx.activity.OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {
            val backStackCount = supportFragmentManager.backStackEntryCount
            if (backStackCount > 0) {
                val belowName = if (backStackCount > 1)
                    supportFragmentManager.getBackStackEntryAt(backStackCount - 2).name
                else null

                if (belowName == null) {
                    // Stacken töms → återgår till STOCKS
                    currentMainTab = MainTab.STOCKS
                    val screenWidth = resources.displayMetrics.widthPixels.toFloat()
                    binding.swipeRefreshLayout.translationX = -screenWidth * 0.3f
                    binding.swipeRefreshLayout.animate().cancel()
                    binding.swipeRefreshLayout.animate()
                        .translationX(0f)
                        .setDuration(280)
                        .setInterpolator(android.view.animation.DecelerateInterpolator())
                        .start()
                    binding.swipeRefreshLayout.visibility = View.VISIBLE
                    binding.addPairButton.visibility = View.VISIBLE
                    showWatchItemSuccess(lastWatchItems)
                } else if (belowName == "pairs") {
                    // Återgår till PairsFragment
                    currentMainTab = MainTab.PAIRS
                    binding.addPairButton.visibility = View.VISIBLE
                }
                // else: återgår till ett annat fragment (alerts etc.) — currentMainTab redan korrekt

                supportFragmentManager.popBackStack()
                updateToolbarForCurrentTab()
            } else {
                isEnabled = false
                onBackPressedDispatcher.onBackPressed()
                isEnabled = true
            }
        }
    }

    private fun initializeUI() {
        setupRecyclerView()
        setupSwipeToDelete()
        setupObservers()
        setupSwipeRefresh()
        setupAddButton()
        setupToolbar()
        setupBottomNavigation()
        showStocksToolbar()
    }

    private fun setupBottomNavigation() {
        // Pill-indikator: primaryContainer (teal-ton) för tydligare active state
        binding.bottomNavigation.itemActiveIndicatorColor =
            androidx.core.content.ContextCompat.getColorStateList(this, R.color.selector_bottom_nav_indicator)
        binding.bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.menu_stocks -> {
                    val previousTab = currentMainTab
                    currentMainTab = MainTab.STOCKS
                    binding.swipeRefreshLayout.visibility = View.VISIBLE
                    binding.addPairButton.visibility = View.VISIBLE
                    supportFragmentManager.popBackStack(
                        null,
                        androidx.fragment.app.FragmentManager.POP_BACK_STACK_INCLUSIVE
                    )
                    showStocksToolbar()
                    val screenWidth = resources.displayMetrics.widthPixels.toFloat()
                    if (previousTab == MainTab.ALERTS || previousTab == MainTab.PAIRS) {
                        // Samma animation för båda: swipeRefreshLayout glider in från vänster
                        binding.swipeRefreshLayout.translationX = -screenWidth * 0.3f
                        binding.swipeRefreshLayout.animate().cancel()
                        binding.swipeRefreshLayout.animate()
                            .translationX(0f)
                            .setDuration(280)
                            .setInterpolator(android.view.animation.DecelerateInterpolator())
                            .start()
                    }
                    showWatchItemSuccess(lastWatchItems)
                    true
                }
                R.id.menu_pairs -> {
                    val previousTab = currentMainTab
                    currentMainTab = MainTab.PAIRS
                    binding.addPairButton.visibility = View.VISIBLE
                    showPairsToolbar()
                    val screenWidth = resources.displayMetrics.widthPixels.toFloat()
                    when (previousTab) {
                        MainTab.STOCKS -> {
                            // Samma animation som STOCKS→ALERTS: swipeRefreshLayout 30% ut, PairsFragment glider in från höger
                            binding.swipeRefreshLayout.animate().cancel()
                            binding.swipeRefreshLayout.animate()
                                .translationX(-screenWidth * 0.3f)
                                .setDuration(150)
                                .setInterpolator(android.view.animation.AccelerateInterpolator())
                                .withEndAction {
                                    binding.swipeRefreshLayout.visibility = View.GONE
                                    binding.swipeRefreshLayout.translationX = 0f
                                    supportFragmentManager.beginTransaction()
                                        .setCustomAnimations(R.anim.slide_in_right, 0, R.anim.slide_in_left, R.anim.slide_out_right)
                                        .replace(R.id.fragmentContainer, PairsFragment())
                                        .addToBackStack("pairs")
                                        .commit()
                                }
                                .start()
                        }
                        MainTab.ALERTS -> {
                            // ALERTS→PAIRS: poppa AlertsFragment, PairsFragment visas med popEnter-animation
                            supportFragmentManager.popBackStack()
                        }
                        MainTab.PAIRS -> {
                            // Redan på PAIRS, inget att göra
                        }
                    }
                    true
                }
                R.id.menu_alerts -> {
                    val previousTab = currentMainTab
                    currentMainTab = MainTab.ALERTS
                    binding.addPairButton.visibility = View.GONE
                    showAlertsToolbar()
                    val screenWidth = resources.displayMetrics.widthPixels.toFloat()
                    if (previousTab == MainTab.PAIRS) {
                        // swipeRefreshLayout redan GONE — visa AlertsFragment direkt ovanpå PairsFragment
                        supportFragmentManager.beginTransaction()
                            .setCustomAnimations(R.anim.slide_in_right, 0, R.anim.slide_in_left, R.anim.slide_out_right)
                            .replace(R.id.fragmentContainer, AlertsFragment())
                            .addToBackStack("alerts")
                            .commit()
                    } else {
                        // Från STOCKS: swipeRefreshLayout 30% ut, AlertsFragment glider in från höger
                        binding.swipeRefreshLayout.animate().cancel()
                        binding.swipeRefreshLayout.animate()
                            .translationX(-screenWidth * 0.3f)
                            .setDuration(150)
                            .setInterpolator(android.view.animation.AccelerateInterpolator())
                            .withEndAction {
                                binding.swipeRefreshLayout.visibility = View.GONE
                                binding.swipeRefreshLayout.translationX = 0f
                                supportFragmentManager.beginTransaction()
                                    .setCustomAnimations(R.anim.slide_in_right, 0, R.anim.slide_in_left, R.anim.slide_out_right)
                                    .replace(R.id.fragmentContainer, AlertsFragment())
                                    .addToBackStack("alerts")
                                    .commit()
                            }
                            .start()
                    }
                    true
                }
                else -> false
            }
        }
    }

    private fun setupToolbar() {
        binding.topAppBar.menu.clear()
        binding.topAppBar.inflateMenu(R.menu.main_menu)
        binding.topAppBar.menu.findItem(R.id.menu_version)?.title = "StockFlip v${BuildConfig.VERSION_NAME}"
        binding.topAppBar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.menu_sort_alphabetical -> {
                    when (currentMainTab) {
                        MainTab.ALERTS -> viewModel.setAlertSortMode(SortHelper.SortMode.ALPHABETICAL)
                        MainTab.PAIRS -> viewModel.setPairsSortMode(SortHelper.SortMode.ALPHABETICAL)
                        MainTab.STOCKS -> {
                            val adapter = binding.stockPairsList.adapter as? GroupedWatchItemAdapter
                            adapter?.setSortMode(SortHelper.SortMode.ALPHABETICAL)
                        }
                    }
                    Toast.makeText(this, "Sortering: Bokstavsordning", Toast.LENGTH_SHORT).show()
                    true
                }
                R.id.menu_sort_addition -> {
                    when (currentMainTab) {
                        MainTab.ALERTS -> viewModel.setAlertSortMode(SortHelper.SortMode.ADDITION_ORDER)
                        MainTab.PAIRS -> viewModel.setPairsSortMode(SortHelper.SortMode.ADDITION_ORDER)
                        MainTab.STOCKS -> {
                            val adapter = binding.stockPairsList.adapter as? GroupedWatchItemAdapter
                            adapter?.setSortMode(SortHelper.SortMode.ADDITION_ORDER)
                        }
                    }
                    Toast.makeText(this, "Sortering: Tilläggsordning", Toast.LENGTH_SHORT).show()
                    true
                }
                R.id.menu_theme -> {
                    showThemeDialog()
                    true
                }
                R.id.menu_export -> {
                    lifecycleScope.launch {
                        val json = viewModel.exportData()
                        BackupManager.shareFile(this@MainActivity, json)
                    }
                    true
                }
                R.id.menu_import -> {
                    importFileLauncher.launch(arrayOf("application/json", "text/plain"))
                    true
                }
                R.id.menu_help -> {
                    openHelpScreen()
                    true
                }
                else -> false
            }
        }
    }

    private fun showThemeDialog() {
        val prefs = getSharedPreferences("settings", MODE_PRIVATE)
        val current = prefs.getInt("night_mode", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        val options = arrayOf("Systemtema", "Ljust", "Mörkt")
        val modes = intArrayOf(
            AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM,
            AppCompatDelegate.MODE_NIGHT_NO,
            AppCompatDelegate.MODE_NIGHT_YES
        )
        val checkedItem = modes.indexOfFirst { it == current }.coerceAtLeast(0)

        AlertDialog.Builder(this)
            .setTitle("Tema")
            .setSingleChoiceItems(options, checkedItem) { dialog, which ->
                val selected = modes[which]
                prefs.edit { putInt("night_mode", selected) }
                AppCompatDelegate.setDefaultNightMode(selected)
                dialog.dismiss()
            }
            .setNegativeButton("Avbryt", null)
            .show()
    }

    private fun showImportConfirmationDialog(json: String) {
        val data = try {
            BackupManager.importFromJson(json)
        } catch (e: Exception) {
            Toast.makeText(
                this,
                getString(R.string.import_error, e.message ?: "Okänt fel"),
                Toast.LENGTH_LONG
            ).show()
            return
        }
        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.import_confirm_title))
            .setMessage(getString(R.string.import_confirm_message, data.watchItems.size, data.stockPairs.size))
            .setPositiveButton("Importera") { _, _ ->
                lifecycleScope.launch {
                    val result = viewModel.importData(json)
                    when (result) {
                        is MainViewModel.ImportResult.Success -> {
                            Toast.makeText(
                                this@MainActivity,
                                getString(R.string.import_success, result.watchCount, result.pairCount),
                                Toast.LENGTH_LONG
                            ).show()
                            viewModel.loadWatchItems()
                            viewModel.loadStockPairs()
                        }
                        is MainViewModel.ImportResult.Error -> {
                            Toast.makeText(
                                this@MainActivity,
                                getString(R.string.import_error, result.message),
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                }
            }
            .setNegativeButton(getString(R.string.dialog_button_cancel), null)
            .show()
    }

    private fun openHelpScreen() {
        binding.swipeRefreshLayout.visibility = View.GONE
        binding.addPairButton.visibility = View.GONE
        binding.topAppBar.title = "Hjälp"
        binding.topAppBar.navigationIcon = androidx.appcompat.content.res.AppCompatResources.getDrawable(
            this,
            R.drawable.ic_arrow_back
        )
        binding.topAppBar.setNavigationOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }
        binding.topAppBar.menu.findItem(R.id.menu_sort)?.isVisible = false
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, HelpFragment())
            .addToBackStack("help")
            .commit()
    }

    private fun openAddStock() {
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

    private fun updateToolbarForCurrentTab() {
        when (currentMainTab) {
            MainTab.STOCKS -> showStocksToolbar()
            MainTab.PAIRS -> showPairsToolbar()
            MainTab.ALERTS -> showAlertsToolbar()
        }
    }

    private fun showStocksToolbar() {
        binding.topAppBar.title = getString(R.string.tab_stocks)
        binding.topAppBar.navigationIcon = null
        binding.topAppBar.menu.findItem(R.id.menu_sort)?.isVisible = true
    }

    private fun showPairsToolbar() {
        binding.topAppBar.title = getString(R.string.tab_pairs)
        binding.topAppBar.navigationIcon = null
        binding.topAppBar.menu.findItem(R.id.menu_sort)?.isVisible = true
    }

    private fun showAlertsToolbar() {
        binding.topAppBar.title = getString(R.string.tab_alerts)
        binding.topAppBar.navigationIcon = null
        binding.topAppBar.menu.findItem(R.id.menu_sort)?.isVisible = true
    }

    private fun initializeUpdates() {
        StockPriceUpdater.startPeriodicUpdate(this)
    }

    private fun requestPermissions() {
        StockPriceUpdater.requestBatteryOptimizationExemption(this)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestNotificationPermission()
        }
    }

    private fun loadInitialData() {
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
    private fun updateLastUpdateTime() {
        val currentTime = SimpleDateFormat(TIME_FORMAT, Locale.getDefault()).format(Date())
        binding.lastUpdateTime.text = getString(R.string.last_updated, currentTime)
        Log.d(TAG, "Updated last update time to $currentTime")
    }

    /**
     * Sets up observers for UI state changes.
     * Handles loading, success, and error states.
     */
    private fun setupObservers() {
        Log.d(TAG, "Setting up observers for watchItemUiState")
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                Log.d(TAG, "Starting to collect from watchItemUiState StateFlow (lifecycle: STARTED)")
                viewModel.watchItemUiState.collect { state: UiState<List<WatchItemUiState>> ->
                    when (state) {
                        is UiState.Loading -> {
                            Log.d(TAG, "=== UI STATE: Loading ===")
                        }
                        is UiState.Success -> {
                            Log.d(TAG, "=== UI STATE: Success with ${state.data.size} items ===")
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
    internal fun handleWatchItemUiState(state: UiState<List<WatchItemUiState>>) {
        when (state) {
            is UiState.Loading -> showLoading()
            is UiState.Success -> showWatchItemSuccess(state.data)
            is UiState.Error -> showError(state.message)
        }
    }

    private fun showLoading() {
        Log.d(TAG, "Showing loading state")
        val isOnAlertsTab = binding.bottomNavigation.selectedItemId == R.id.menu_alerts
        if (!isOnAlertsTab && currentMainTab != MainTab.PAIRS) {
            binding.swipeRefreshLayout.isRefreshing = true
        }
    }

    private fun showWatchItemSuccess(data: List<WatchItemUiState>) {
        Log.d(TAG, "=== showWatchItemSuccess() called with ${data.size} watch items ===")

        lastWatchItems = data

        // PAIRS och ALERTS hanteras av sina egna fragment — uppdatera bara för STOCKS
        if (currentMainTab != MainTab.STOCKS) {
            binding.swipeRefreshLayout.isRefreshing = false
            return
        }

        val filteredData = data.filter { it.item.watchType !is WatchType.PricePair }
        Log.d(TAG, "Filtered watch items for STOCKS tab: ${filteredData.size}")

        binding.swipeRefreshLayout.isRefreshing = false
        Log.d(TAG, "Refresh indicator hidden")

        val adapter = binding.stockPairsList.adapter as? GroupedWatchItemAdapter
        if (adapter != null) {
            Log.d(TAG, "Adapter found, calling submitGroupedList with ${filteredData.size} items")
            adapter.submitGroupedList(filteredData)
            Log.d(TAG, "submitGroupedList called successfully")
        } else {
            Log.e(TAG, "CRITICAL: Adapter is null! Cannot update UI")
        }

        val showEmpty = filteredData.isEmpty()
        binding.emptyStateContainer.visibility = if (showEmpty) View.VISIBLE else View.GONE
        if (showEmpty) {
            binding.emptyStateTitle.text = getString(R.string.stocks_empty_title)
            binding.emptyStateSubtitle.text = getString(R.string.stocks_empty_subtitle)
        }
    }

    private fun showError(message: String) {
        Log.e(TAG, "Error state: $message")
        binding.swipeRefreshLayout.isRefreshing = false
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    private fun setupRecyclerView() {
        Log.d(TAG, "Setting up RecyclerView")
        binding.stockPairsList.layoutManager = LinearLayoutManager(this)
        binding.stockPairsList.adapter = GroupedWatchItemAdapter(
            onToggleActive = {},
            onReactivate = {},
            onDeleteClick = { item: WatchItem -> handleDeleteClick(item) },
            onEditClick = { item: WatchItem -> handleEditClick(item) },
            onItemClick = { item: WatchItem -> handleItemClick(item) }
        )
    }

    private fun setupSwipeToDelete() {
        val callback = SwipeToDeleteCallback(
            context = this,
            canSwipe = { position ->
                val adapter = binding.stockPairsList.adapter as? GroupedWatchItemAdapter ?: return@SwipeToDeleteCallback false
                val item = adapter.currentList.getOrNull(position) ?: return@SwipeToDeleteCallback false
                item !is GroupedListItem.Header && item !is GroupedListItem.GroupSeparator
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
                        adapter.notifyItemChanged(position)
                        if (item.item.watchType is WatchType.PricePair) {
                            showDeleteConfirmationDialog(item.item)
                        } else if (item.groupPosition == com.stockflip.ui.theme.GroupPosition.ONLY) {
                            // Enda bevakning för symbol — ta bort hela symbolen
                            val symbol = item.item.ticker ?: return@SwipeToDeleteCallback
                            showDeleteStockDialog(symbol, 1)
                        } else {
                            // En av flera bevakningar — ta bort bara denna enskilda bevakning
                            showDeleteSingleWatchDialog(item.item)
                        }
                    }
                    is GroupedListItem.Header -> {}
                    is GroupedListItem.GroupSeparator -> {}
                }
            },
            onSwipedRight = { position ->
                val adapter = binding.stockPairsList.adapter as? GroupedWatchItemAdapter
                    ?: return@SwipeToDeleteCallback
                val listItem = adapter.currentList.getOrNull(position)
                    ?: return@SwipeToDeleteCallback
                adapter.notifyItemChanged(position) // snap back
                val (symbol, companyName) = when (listItem) {
                    is GroupedListItem.MultipleWatchesWrapper ->
                        Pair(listItem.symbol, null)
                    is GroupedListItem.WatchItemWrapper ->
                        Pair(listItem.item.ticker ?: listItem.item.ticker1, listItem.item.companyName)
                    else -> return@SwipeToDeleteCallback
                }
                if (symbol == null) return@SwipeToDeleteCallback
                binding.stockPairsList.postDelayed({
                    navigateToStockDetail(symbol, companyName)
                }, 120)
            }
        )
        ItemTouchHelper(callback).attachToRecyclerView(binding.stockPairsList)
    }

    private fun showDeleteStockDialog(symbol: String, watchCount: Int) {
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

    private fun showDeleteSingleWatchDialog(watchItem: WatchItem) {
        val label = watchItem.companyName ?: watchItem.ticker ?: "bevakning"
        MaterialAlertDialogBuilder(this)
            .setTitle("Ta bort bevakning")
            .setMessage("Ta bort denna bevakning för $label?")
            .setPositiveButton("Ta bort") { _, _ ->
                lifecycleScope.launch {
                    try {
                        viewModel.deleteWatchItem(watchItem)
                        Toast.makeText(this@MainActivity, "Bevakning borttagen", Toast.LENGTH_SHORT).show()
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
    private fun handleItemClick(item: WatchItem) {
        when (currentMainTab) {
            MainTab.STOCKS -> {
                val symbol = item.ticker ?: return
                navigateToStockDetail(symbol, item.companyName)
            }
            MainTab.PAIRS -> {
                if (item.watchType is WatchType.PricePair) {
                    navigateToPairDetail(item.id)
                } else {
                    handleEditClick(item)
                }
            }
            MainTab.ALERTS -> { /* No-op, managed by AlertsFragment */ }
        }
    }

    /**
     * Navigerar till StockDetailFragment för en aktie.
     */
    private fun navigateToStockDetail(symbol: String, companyName: String? = null) {
        val fragment = StockDetailFragment.newInstance(symbol, companyName)
        
        supportFragmentManager.beginTransaction()
            .setCustomAnimations(
                R.anim.slide_in_right,
                R.anim.slide_out_left,
                R.anim.slide_in_left,
                R.anim.slide_out_right
            )
            .replace(R.id.fragmentContainer, fragment)
            .addToBackStack("stock_detail")
            .commit()
        
        // FAB döljs direkt; swipeRefreshLayout döljs efter animationen (280ms) så
        // att huvudinnehållet syns bakom det inkommande kortet under transition.
        binding.addPairButton.visibility = View.GONE
        binding.swipeRefreshLayout.postDelayed({
            binding.swipeRefreshLayout.visibility = View.GONE
        }, 300L)
        
        // Dölj sorteringsmenyn
        binding.topAppBar.menu.findItem(R.id.menu_sort)?.isVisible = false
    }

    internal fun navigateToStockDetailFromAlerts(symbol: String, companyName: String?) {
        navigateToStockDetail(symbol, companyName)
    }

    internal fun showEditDialogFromAlerts(item: WatchItem) {
        handleEditClick(item)
    }

    internal fun navigateToStockDetailFromPairs(symbol: String, companyName: String?) {
        navigateToStockDetail(symbol, companyName)
    }

    internal fun navigateToPairDetailFromPairs(watchItemId: Int) {
        navigateToPairDetail(watchItemId)
    }

    internal fun showEditDialogFromPairs(item: WatchItem) {
        handleEditClick(item)
    }

    private fun navigateToPairDetail(watchItemId: Int) {
        val fragment = PairDetailFragment.newInstance(watchItemId)

        supportFragmentManager.beginTransaction()
            .setCustomAnimations(
                R.anim.slide_in_right,
                R.anim.slide_out_left,
                R.anim.slide_in_left,
                R.anim.slide_out_right
            )
            .replace(R.id.fragmentContainer, fragment)
            .addToBackStack("pair_detail")
            .commit()

        binding.addPairButton.visibility = View.GONE
        binding.swipeRefreshLayout.postDelayed({
            binding.swipeRefreshLayout.visibility = View.GONE
        }, 300L)

        binding.topAppBar.menu.findItem(R.id.menu_sort)?.isVisible = false
    }

    private fun handleDeleteClick(item: WatchItem) {
        Log.d(TAG, "Delete clicked for watch item: ${item.getDisplayName()}")
        showDeleteConfirmationDialog(item)
    }

    private fun handleEditClick(item: WatchItem) {
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

    private fun setupAddButton() {
        // Neutralisera style-driven tint som Material FAB applicerar via setImageTintList()
        // under initialisering — utan detta skriver colorOnPrimaryContainer (teal) över ikonfärgen.
        binding.addPairButton.imageTintList = null
        binding.addPairButton.setOnClickListener {
            when (currentMainTab) {
                MainTab.STOCKS -> {
                    openAddStock()
                }
                MainTab.PAIRS -> {
                    showAddStockPairDialog()
                }
                MainTab.ALERTS -> { /* No-op, button hidden */ }
            }
        }
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

                if (selectedStock1 != null && selectedStock2 != null) {
                    val priceDifference = priceDifferenceStr.parseDecimal() ?: 0.0
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
                    Toast.makeText(this, "Välj båda aktier", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Avbryt", null)
            .show().also { dialog ->
                dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
                focusInput(ticker1Input, selectAll = false)
            }
    }

    /**
     * Shows a dialog for adding a new price target watch.
     * Handles user input validation and API calls for stock information.
     * 
     * @param prefillSymbol Optional symbol to prefill in the ticker input field
     */
    private fun showAddPriceTargetDialog(prefillSymbol: String? = null) {
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
                    val targetPrice = targetPriceStr.parseDecimal()

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
            .show().also { dialog ->
                dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
                focusInput(tickerInput, selectAll = !tickerInput.text.isNullOrEmpty())
            }
    }

    /**
     * Shows a dialog for adding a new key metrics watch.
     * Handles user input validation and API calls for stock information.
     * 
     * @param prefillSymbol Optional symbol to prefill in the ticker input field
     */
    private fun showAddKeyMetricsDialog(prefillSymbol: String? = null) {
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
                    val targetValue = targetValueStr.parseDecimal()

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
            .show().also { dialog ->
                dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
                focusInput(tickerInput, selectAll = !tickerInput.text.isNullOrEmpty())
            }
    }

    /**
     * Shows a dialog for adding a new ATH-based watch.
     * Handles user input validation and API calls for stock information.
     */
    @Suppress("unused")
    private fun showAddATHBasedDialog() {
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
                        else -> WatchType.DropType.ABSOLUTE
                    }
                    val dropValue = dropValueStr.parseDecimal()

                    if (dropValue != null && dropValue > 0) {
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
            .show().also { dialog ->
                dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
                focusInput(tickerInput, selectAll = !tickerInput.text.isNullOrEmpty())
            }
    }

    /**
     * Shows a dialog for adding a new combined alert.
     * Allows users to create alerts with multiple conditions combined with AND/OR.
     */
    private fun showAddCombinedAlertDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_add_combined_alert, null)
        val symbolInput = dialogView.findViewById<MaterialAutoCompleteTextView>(R.id.symbolInput)
        val conditionsRecyclerView = dialogView.findViewById<RecyclerView>(R.id.conditionsRecyclerView)
        val addConditionButton = dialogView.findViewById<MaterialButton>(R.id.addConditionButton)
        val previewText = dialogView.findViewById<TextView>(R.id.previewText)

        // Setup RecyclerView
        conditionsRecyclerView.layoutManager = LinearLayoutManager(this)
        
        // Create condition adapter (lateinit to use in lambdas)
        lateinit var conditionAdapter: ConditionBuilderAdapter
        
        conditionAdapter = ConditionBuilderAdapter(
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
                    it.value.parseDecimal() != null 
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
    private fun showEditCombinedAlertDialog(watchItem: WatchItem) {
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
        val conditionsRecyclerView = dialogView.findViewById<RecyclerView>(R.id.conditionsRecyclerView)
        val addConditionButton = dialogView.findViewById<MaterialButton>(R.id.addConditionButton)
        val previewText = dialogView.findViewById<TextView>(R.id.previewText)

        // Setup RecyclerView
        conditionsRecyclerView.layoutManager = LinearLayoutManager(this)
        
        // Create condition adapter with existing conditions
        lateinit var conditionAdapter: ConditionBuilderAdapter
        
        conditionAdapter = ConditionBuilderAdapter(
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
                    it.value.parseDecimal() != null 
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
                    }
                    
                    conditions.add(conditionData)
                    true
                }
                is AlertExpression.And -> {
                    // Rekursivt extrahera från vänster och höger
                    if (!extractRules(expr.left, null)) return false
                    
                    // För höger sida, använd AND som operator
                    extractRules(expr.right, "OCH")
                }
                is AlertExpression.Or -> {
                    // Rekursivt extrahera från vänster och höger
                    if (!extractRules(expr.left, null)) return false
                    
                    // För höger sida, använd OR som operator
                    extractRules(expr.right, "ELLER")
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
        conditions[0].operator = null
        
        return Pair(currentSymbol, conditions.toList())
    }

    /**
     * Updates the preview text showing the current expression.
     */
    private fun updatePreview(
        adapter: ConditionBuilderAdapter,
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
        conditions: List<ConditionBuilderAdapter.ConditionData>
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
    private fun buildAlertRule(symbol: String, condition: ConditionBuilderAdapter.ConditionData): AlertRule? {
        val value = condition.value.parseDecimal() ?: return null
        
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
                        val filterResults = FilterResults()
                        filterResults.values = mutableListOf<StockSearchResult>()
                        filterResults.count = 0
                        return filterResults
                    }

                    @Suppress("UNCHECKED_CAST")
                    override fun publishResults(constraint: CharSequence?, results: FilterResults?) {
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

                if (finalTicker1.isNotEmpty() && finalTicker2.isNotEmpty()) {
                    val priceDifference = priceDifferenceStr.parseDecimal() ?: 0.0
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
                    Toast.makeText(this, "Välj båda aktier", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Avbryt", null)
            .show().also { dialog ->
                dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
                focusInput(priceDifferenceInput)
            }
    }

    /**
     * Shows a dialog for editing an existing price target watch.
     */
    private fun showEditPriceTargetDialog(item: WatchItem) {
        if (item.watchType !is WatchType.PriceTarget) return
        val currencySymbol = CurrencyHelper.getCurrencySymbol(CurrencyHelper.getCurrencyFromSymbol(item.ticker ?: ""))
        val priceTarget = item.watchType
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_add_price_target, null)
        val tickerInput = dialogView.findViewById<MaterialAutoCompleteTextView>(R.id.tickerInput).apply { setText(item.ticker) }
        val targetPriceInput = dialogView.findViewById<TextInputEditText>(R.id.targetPriceInput).apply { setText(priceTarget.targetPrice.toString()) }
        (targetPriceInput.parent as? TextInputLayout)?.hint = "Målpris ($currencySymbol)"
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
                    val targetPrice = targetPriceStr.parseDecimal()

                    if (targetPrice != null && targetPrice > 0) {
                        val direction = priceTarget.direction
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
            .show().also { dialog ->
                dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
                focusInput(targetPriceInput)
            }
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
                        else -> keyMetrics.metricType
                    }
                    val targetValue = targetValueStr.parseDecimal()

                    if (targetValue != null && targetValue > 0) {
                        val direction = keyMetrics.direction
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
            .show().also { dialog ->
                dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
                focusInput(targetValueInput)
            }
    }

    /**
     * Shows a dialog for editing an existing ATH-based watch.
     */
    private fun showEditATHBasedDialog(item: WatchItem) {
        if (item.watchType !is WatchType.ATHBased) return
        val currencySymbol = CurrencyHelper.getCurrencySymbol(CurrencyHelper.getCurrencyFromSymbol(item.ticker ?: ""))
        val absoluteLabel = "Absolut ($currencySymbol)"
        val athBased = item.watchType
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_add_ath_based, null)
        val tickerInput = dialogView.findViewById<MaterialAutoCompleteTextView>(R.id.tickerInput).apply { setText(item.ticker) }
        val dropTypeInput = dialogView.findViewById<MaterialAutoCompleteTextView>(R.id.dropTypeInput).apply {
            setText(when (athBased.dropType) {
                WatchType.DropType.PERCENTAGE -> "Procent"
                WatchType.DropType.ABSOLUTE -> absoluteLabel
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
        val dropTypes = arrayOf("Procent", absoluteLabel)
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
                        else -> WatchType.DropType.ABSOLUTE
                    }
                    val dropValue = dropValueStr.parseDecimal()

                    if (dropValue != null && dropValue > 0) {
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
            .show().also { dialog ->
                dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
                focusInput(dropValueInput)
            }
    }

    private fun showEditPriceRangeDialog(item: WatchItem) {
        val currency = CurrencyHelper.getCurrencyFromSymbol(item.ticker ?: "")
        com.stockflip.ui.dialogs.showEditPriceRangeDialog(
            context = this,
            item = item,
            currency = currency,
            onUpdate = { minPrice, maxPrice ->
                lifecycleScope.launch {
                    try {
                        binding.progressBar.visibility = View.VISIBLE
                        val updatedItem = item.copy(watchType = WatchType.PriceRange(minPrice, maxPrice))
                        viewModel.updateWatchItem(updatedItem)
                        viewModel.refreshWatchItems()
                        updateLastUpdateTime()
                        binding.progressBar.visibility = View.GONE
                        Toast.makeText(this@MainActivity, getString(R.string.toast_price_range_updated), Toast.LENGTH_SHORT).show()
                    } catch (e: Exception) {
                        binding.progressBar.visibility = View.GONE
                        Toast.makeText(this@MainActivity, getString(R.string.toast_watch_update_failed, e.message.orEmpty()), Toast.LENGTH_LONG).show()
                    }
                }
            },
            onDelete = null
        )
    }

    private fun showEditDailyMoveDialog(item: WatchItem) {
        if (item.watchType !is WatchType.DailyMove) return
        val dailyMove = item.watchType
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_add_daily_move, null)
        val tickerInput = dialogView.findViewById<MaterialAutoCompleteTextView?>(R.id.tickerInput)
        val tickerInputLayout = tickerInput?.parent as? TextInputLayout
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
                    val threshold = thresholdStr.parseDecimal()
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
            .show().also { dialog ->
                dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
                focusInput(thresholdInput)
            }
    }

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

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
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
        /** Intent extra: watch item id to open in PairDetailFragment (from pair notification deep link) */
        const val EXTRA_OPEN_PAIR_WATCH_ID = "extra_open_pair_watch_id"
        /** Intent extra: ticker to open in StockDetailFragment (from notification deep link) */
        const val EXTRA_OPEN_TICKER = "extra_open_ticker"
        /** Intent extra: company name for the ticker (optional, for display) */
        const val EXTRA_OPEN_COMPANY = "extra_open_company"
    }
} 
