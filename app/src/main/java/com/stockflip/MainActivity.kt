package com.stockflip

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
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
import com.stockflip.ui.dialogs.WatchItemEditor
import com.stockflip.ui.dialogs.focusInput
import com.stockflip.repository.SearchState
import com.stockflip.repository.StockRepository
import com.stockflip.viewmodel.StockSearchViewModel
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
            val json = try {
                readImportedBackupJson(uri) ?: return@launch
            } catch (e: IllegalArgumentException) {
                Toast.makeText(this@MainActivity, e.message ?: "Backupfilen kunde inte läsas", Toast.LENGTH_LONG).show()
                return@launch
            } catch (e: Exception) {
                Toast.makeText(this@MainActivity, "Backupfilen kunde inte läsas", Toast.LENGTH_LONG).show()
                return@launch
            }
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

    private enum class OverviewMode {
        CASES,
        STOCKS
    }

    private var currentMainTab: MainTab = MainTab.STOCKS
    private var overviewMode: OverviewMode = OverviewMode.CASES
    private var lastWatchItems: List<WatchItemUiState> = emptyList()
    private var detailSyncJob: Job? = null
    private val watchItemEditor by lazy {
        WatchItemEditor(
            context = this,
            scope = lifecycleScope,
            stockSearchViewModel = stockSearchViewModel,
            stockSearchViewModel2 = stockSearchViewModel2,
            allowSymbolEditing = true,
            createStockAdapter = { createStockAdapter() },
            setupStockSearch = { input, adapter, searchViewModel, includeCrypto ->
                setupStockSearch(input, adapter, searchViewModel, includeCrypto)
            },
            onUpdateWatchItem = { updatedItem ->
                binding.progressBar.visibility = View.VISIBLE
                try {
                    viewModel.updateWatchItem(updatedItem)
                    updateLastUpdateTime()
                } finally {
                    binding.progressBar.visibility = View.GONE
                }
            },
            onDeleteRequested = { watchItem ->
                showDeleteConfirmationDialog(watchItem)
            },
            currentCurrencyFor = { item ->
                CurrencyHelper.getCurrencyFromSymbol(item.ticker ?: "")
            }
        )
    }

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
        val hasProtectedExtras = intent.hasExtra(EXTRA_OPEN_PAIR_WATCH_ID) ||
            intent.hasExtra(EXTRA_OPEN_TICKER) ||
            intent.hasExtra(EXTRA_OPEN_WATCH_ID) ||
            intent.hasExtra(EXTRA_TRIGGER_TITLE) ||
            intent.hasExtra(EXTRA_TRIGGER_MESSAGE)
        if (!hasProtectedExtras) return

        val notificationToken = intent.getStringExtra(EXTRA_NOTIFICATION_TOKEN)
        if (!NotificationNavigationSecurity.consumeToken(notificationToken)) {
            Log.w(TAG, "Rejected navigation intent without a valid notification token")
            clearProtectedIntentExtras(intent)
            return
        }

        val triggerTitle = intent.getStringExtra(EXTRA_TRIGGER_TITLE)
        val triggerMessage = intent.getStringExtra(EXTRA_TRIGGER_MESSAGE)
        val pairWatchItemId = intent.getIntExtra(EXTRA_OPEN_PAIR_WATCH_ID, -1)
        if (pairWatchItemId != -1) {
            clearProtectedIntentExtras(intent)
            navigateToPairDetail(pairWatchItemId, triggerTitle, triggerMessage, openedFromNotification = true)
            return
        }
        val ticker = intent.getStringExtra(EXTRA_OPEN_TICKER) ?: return
        val watchItemId = intent.getIntExtra(EXTRA_OPEN_WATCH_ID, -1).takeIf { it > 0 }
        clearProtectedIntentExtras(intent)
        val companyName = intent.getStringExtra(EXTRA_OPEN_COMPANY)
        navigateToStockDetail(ticker, companyName, watchItemId, triggerTitle, triggerMessage, openedFromNotification = true)
    }

    private fun clearProtectedIntentExtras(intent: Intent) {
        intent.removeExtra(EXTRA_OPEN_PAIR_WATCH_ID)
        intent.removeExtra(EXTRA_OPEN_TICKER)
        intent.removeExtra(EXTRA_OPEN_WATCH_ID)
        intent.removeExtra(EXTRA_OPEN_COMPANY)
        intent.removeExtra(EXTRA_TRIGGER_TITLE)
        intent.removeExtra(EXTRA_TRIGGER_MESSAGE)
        intent.removeExtra(EXTRA_NOTIFICATION_TOKEN)
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
                    binding.overviewModeScroll.visibility = View.VISIBLE
                    binding.addPairButton.visibility = View.GONE
                    showWatchItemSuccess(lastWatchItems)
                } else if (belowName == "pairs") {
                    // Återgår till PairsFragment
                    currentMainTab = MainTab.PAIRS
                    binding.overviewModeScroll.visibility = View.GONE
                    binding.addPairButton.visibility = View.VISIBLE
                } else if (belowName == "alerts") {
                    currentMainTab = MainTab.ALERTS
                    binding.overviewModeScroll.visibility = View.GONE
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
        setupOverviewModeToggle()
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
                    binding.overviewModeScroll.visibility = View.VISIBLE
                    binding.addPairButton.visibility = View.GONE
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
                    binding.overviewModeScroll.visibility = View.GONE
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
                    binding.overviewModeScroll.visibility = View.GONE
                    binding.addPairButton.visibility = View.VISIBLE
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
                R.id.menu_version -> {
                    openChangelogScreen()
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

    private suspend fun readImportedBackupJson(uri: Uri): String? = withContext(Dispatchers.IO) {
        contentResolver.openAssetFileDescriptor(uri, "r")?.use { descriptor ->
            val fileLength = descriptor.length
            if (fileLength > MAX_IMPORT_BYTES) {
                throw IllegalArgumentException("Backupfilen är för stor. Maxstorlek är 1 MB.")
            }
        }

        contentResolver.openInputStream(uri)?.use { input ->
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            var totalBytes = 0

            while (true) {
                val bytesRead = input.read(buffer)
                if (bytesRead == -1) break

                totalBytes += bytesRead
                if (totalBytes > MAX_IMPORT_BYTES) {
                    throw IllegalArgumentException("Backupfilen är för stor. Maxstorlek är 1 MB.")
                }

                output.write(buffer, 0, bytesRead)
            }

            output.toString(Charsets.UTF_8.name())
        }
    }

    private fun openHelpScreen() {
        binding.swipeRefreshLayout.visibility = View.GONE
        binding.overviewModeScroll.visibility = View.GONE
        binding.addPairButton.visibility = View.GONE
        binding.topAppBar.title = "Hjälp"
        binding.topAppBar.navigationIcon = androidx.appcompat.content.res.AppCompatResources.getDrawable(
            this,
            R.drawable.ic_arrow_back
        )
        binding.topAppBar.setNavigationOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, MarkdownAssetFragment.newInstance("manual.md"))
            .addToBackStack("help")
            .commit()
    }

    private fun openChangelogScreen() {
        binding.swipeRefreshLayout.visibility = View.GONE
        binding.overviewModeScroll.visibility = View.GONE
        binding.addPairButton.visibility = View.GONE
        binding.topAppBar.title = "Ändringslogg"
        binding.topAppBar.navigationIcon = androidx.appcompat.content.res.AppCompatResources.getDrawable(
            this,
            R.drawable.ic_arrow_back
        )
        binding.topAppBar.setNavigationOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, MarkdownAssetFragment.newInstance("changelog.md"))
            .addToBackStack("changelog")
            .commit()
    }

    private fun openAddStock() {
        binding.swipeRefreshLayout.visibility = View.GONE
        binding.overviewModeScroll.visibility = View.GONE
        binding.addPairButton.visibility = View.GONE
        binding.topAppBar.title = getString(R.string.title_add_stock)
        binding.topAppBar.navigationIcon = androidx.appcompat.content.res.AppCompatResources.getDrawable(
            this,
            R.drawable.ic_arrow_back
        )
        binding.topAppBar.setNavigationOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }
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
        binding.addPairButton.visibility = View.GONE
        binding.overviewModeScroll.visibility = View.VISIBLE
    }

    private fun showPairsToolbar() {
        binding.topAppBar.title = getString(R.string.tab_pairs)
        binding.topAppBar.navigationIcon = null
        binding.addPairButton.visibility = View.VISIBLE
        binding.overviewModeScroll.visibility = View.GONE
    }

    private fun showAlertsToolbar() {
        binding.topAppBar.title = getString(R.string.tab_alerts)
        binding.topAppBar.navigationIcon = null
        binding.addPairButton.visibility = View.VISIBLE
        binding.overviewModeScroll.visibility = View.GONE
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

        // PAIRS och ALERTS hanteras av sina egna fragment — uppdatera bara för ÖVERSIKT
        if (currentMainTab != MainTab.STOCKS) {
            binding.swipeRefreshLayout.isRefreshing = false
            return
        }

        val filteredData = data.filter { it.item.watchType !is WatchType.PricePair }
        Log.d(TAG, "Filtered watch items for STOCKS tab: ${filteredData.size}")

        binding.swipeRefreshLayout.isRefreshing = false
        Log.d(TAG, "Refresh indicator hidden")

        renderStocksOverview(data)

        val showEmpty = filteredData.isEmpty()
        binding.emptyStateContainer.visibility = if (showEmpty) View.VISIBLE else View.GONE
        if (showEmpty) {
            binding.emptyStateTitle.text = getString(R.string.stocks_empty_title)
            binding.emptyStateSubtitle.text = getString(R.string.stocks_empty_subtitle)
        }
    }

    private fun showError(message: String) {
        Log.e(TAG, "Error state shown to user")
        binding.swipeRefreshLayout.isRefreshing = false
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    private fun setupOverviewModeToggle() {
        binding.overviewModeChipGroup.check(R.id.overviewModeCasesChip)
        binding.overviewModeChipGroup.setOnCheckedStateChangeListener { _, checkedIds ->
            overviewMode = when (checkedIds.firstOrNull()) {
                R.id.overviewModeStocksChip -> OverviewMode.STOCKS
                else -> OverviewMode.CASES
            }
            if (currentMainTab == MainTab.STOCKS) {
                showWatchItemSuccess(lastWatchItems)
            }
        }
    }

    private fun renderStocksOverview(filteredData: List<WatchItemUiState>) {
        val adapter = binding.stockPairsList.adapter as? GroupedWatchItemAdapter
        if (adapter == null) {
            Log.e(TAG, "CRITICAL: Adapter is null! Cannot update UI")
            return
        }

        when (overviewMode) {
            OverviewMode.CASES -> {
                Log.d(TAG, "Rendering overview case list with ${filteredData.size} items")
                adapter.submitOverviewList(filteredData)
            }
            OverviewMode.STOCKS -> {
                Log.d(TAG, "Rendering overview stock list with ${filteredData.size} items")
                adapter.submitStocksList(filteredData)
            }
        }
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
            // Översikt ska vara ett rent läsläge. Hantering sker i Mina case.
            canSwipe = { false },
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
	                    is GroupedListItem.OverviewSummary -> {
	                        adapter.notifyItemChanged(position)
	                    }
	                    is GroupedListItem.AlertsSummary -> {
	                        adapter.notifyItemChanged(position)
	                    }
	                    is GroupedListItem.StocksHeader -> {
	                        adapter.notifyItemChanged(position)
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
    private fun navigateToStockDetail(
        symbol: String,
        companyName: String? = null,
        highlightWatchItemId: Int? = null,
        triggerTitle: String? = null,
        triggerMessage: String? = null,
        openedFromNotification: Boolean = false
    ) {
        val fragment = StockDetailFragment.newInstance(
            symbol = symbol,
            companyName = companyName,
            highlightWatchItemId = highlightWatchItemId,
            triggerTitle = triggerTitle,
            triggerMessage = triggerMessage,
            openedFromNotification = openedFromNotification
        )
        
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
        binding.overviewModeScroll.visibility = View.GONE
        binding.swipeRefreshLayout.postDelayed({
            binding.swipeRefreshLayout.visibility = View.GONE
        }, 300L)
        
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

    private fun navigateToPairDetail(
        watchItemId: Int,
        triggerTitle: String? = null,
        triggerMessage: String? = null,
        openedFromNotification: Boolean = false
    ) {
        val fragment = PairDetailFragment.newInstance(
            watchItemId = watchItemId,
            triggerTitle = triggerTitle,
            triggerMessage = triggerMessage,
            openedFromNotification = openedFromNotification
        )

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
        binding.overviewModeScroll.visibility = View.GONE
        binding.swipeRefreshLayout.postDelayed({
            binding.swipeRefreshLayout.visibility = View.GONE
        }, 300L)
    }

    private fun handleDeleteClick(item: WatchItem) {
        Log.d(TAG, "Delete clicked for watch item")
        showDeleteConfirmationDialog(item)
    }

    private fun handleEditClick(item: WatchItem) {
        Log.d(TAG, "Edit clicked for watch item")
        watchItemEditor.showEditWatchItemDialog(item)
    }

    private fun setupAddButton() {
        // Neutralisera style-driven tint som Material FAB applicerar via setImageTintList()
        // under initialisering — utan detta skriver colorOnPrimaryContainer (teal) över ikonfärgen.
        binding.addPairButton.imageTintList = null
        binding.addPairButton.setOnClickListener {
            when (currentMainTab) {
                MainTab.STOCKS -> Unit
                MainTab.PAIRS -> {
                    showAddStockPairDialog()
                }
                MainTab.ALERTS -> {
                    openAddStock()
                }
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
        }

        ticker2Input.setOnItemClickListener { _, _, position, _ ->
            selectedStock2 = adapter2.getItem(position)
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

                    if (item.isCrypto) {
                        iconView.setImageResource(R.drawable.ic_crypto)
                    } else {
                        iconView.setImageResource(R.drawable.ic_stock)
                    }
                    iconView.visibility = View.VISIBLE
                }

                return view
            }

            override fun getFilter(): Filter {
                return object : Filter() {
                    override fun performFiltering(constraint: CharSequence?): FilterResults {
                        return FilterResults().apply {
                            values = mutableListOf<StockSearchResult>()
                            count = 0
                        }
                    }

                    override fun publishResults(constraint: CharSequence?, results: FilterResults?) {
                    }
                }
            }
        }
    }

    internal fun setupStockSearch(
        input: MaterialAutoCompleteTextView,
        adapter: ArrayAdapter<StockSearchResult>,
        viewModel: StockSearchViewModel,
        includeCrypto: Boolean = true
    ) {
        Log.d(TAG, "Setting up search for input: ${input.id} (includeCrypto: $includeCrypto)")

        input.threshold = 2
        input.setAdapter(adapter)

        lifecycleScope.launch {
            viewModel.searchState.collect { state ->
                when (state) {
                    is SearchState.Loading -> Unit
                    is SearchState.Success -> {
                        adapter.clear()
                        adapter.addAll(state.results)
                        adapter.notifyDataSetChanged()

                        if (state.results.isNotEmpty() && input.text.isNotEmpty()) {
                            input.post {
                                if (input.hasFocus()) {
                                    input.showDropDown()
                                }
                            }
                        }
                    }
                    is SearchState.Error -> {
                        adapter.clear()
                        adapter.notifyDataSetChanged()
                    }
                }
            }
        }

        var textChangeJob: Job? = null

        input.doAfterTextChanged { text ->
            textChangeJob?.cancel()

            if (text.isNullOrEmpty()) {
                adapter.clear()
                adapter.notifyDataSetChanged()
                input.dismissDropDown()
                return@doAfterTextChanged
            }

            textChangeJob = lifecycleScope.launch {
                delay(300)
                viewModel.search(text.toString(), includeCrypto)
            }
        }

        input.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus && input.text.isNotEmpty() && adapter.count > 0) {
                input.post { input.showDropDown() }
            }
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

    internal fun syncWatchItemsAfterDetailChange() {
        detailSyncJob?.cancel()
        detailSyncJob = lifecycleScope.launch {
            try {
                // Apply the DB change immediately, then enrich with fresh prices quietly.
                viewModel.loadWatchItems(forceShowStaleData = true)
                viewModel.refreshWatchItems(showLoading = false)
                updateLastUpdateTime()
            } catch (e: Exception) {
                Log.w(TAG, "Background sync after detail change failed: ${e.message}", e)
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
        /** Intent extra: watch item id for the triggered stock alert */
        const val EXTRA_OPEN_WATCH_ID = "extra_open_watch_id"
        /** Intent extra: company name for the ticker (optional, for display) */
        const val EXTRA_OPEN_COMPANY = "extra_open_company"
        /** Intent extra: human-readable trigger title for notification landing */
        const val EXTRA_TRIGGER_TITLE = "extra_trigger_title"
        /** Intent extra: human-readable trigger message for notification landing */
        const val EXTRA_TRIGGER_MESSAGE = "extra_trigger_message"
        /** Intent extra: one-time token proving the navigation intent came from our own notification PendingIntent */
        const val EXTRA_NOTIFICATION_TOKEN = "extra_notification_token"
        private const val MAX_IMPORT_BYTES = 1_048_576
    }
} 
