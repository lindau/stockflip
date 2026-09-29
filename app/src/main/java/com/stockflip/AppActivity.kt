package com.stockflip

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.edit
import androidx.navigation.compose.rememberNavController
import com.stockflip.repository.StockRepository
import com.stockflip.ui.market.MarketRoute
import com.stockflip.ui.pairdetail.PairDetailRoute
import com.stockflip.ui.settings.DocumentScreen
import com.stockflip.ui.settings.SettingsRoute
import com.stockflip.ui.settings.ThemeMode
import com.stockflip.ui.settings.UpdateDialog
import com.stockflip.ui.settings.UpdateFlow
import com.stockflip.viewmodel.StockSearchViewModel
import com.stockflip.repository.MetricHistoryRepository
import com.stockflip.repository.TriggerHistoryRepository
import com.stockflip.ui.nav.AppShell
import com.stockflip.ui.nav.Routes
import com.stockflip.ui.nav.TopLevelTab
import com.stockflip.ui.nav.navigateToTab
import com.stockflip.ui.stockdetail.StockDetailRoute
import com.stockflip.ui.theme.StockFlipTheme
import com.stockflip.ui.watchlist.WatchlistRoute

/**
 * Nya skalet: hostar [AppShell] (Bevakningar · Marknad · Inställningar + aktiedetalj).
 * Ersätter `MainActivity` när alla skärmar är portade; tills dess är den inte launcher.
 * Notis-deep-links kräver samma HMAC-token som `MainActivity` ([NotificationNavigationSecurity]).
 */
class AppActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels {
        object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                val db = StockPairDatabase.getDatabase(applicationContext)
                @Suppress("UNCHECKED_CAST")
                return MainViewModel(db.stockPairDao(), db.watchItemDao(), YahooFinanceService, db.stockNoteDao(), db.podcastObservationDao()) as T
            }
        }
    }

    private val searchViewModel: StockSearchViewModel by viewModels {
        object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return StockSearchViewModel(StockRepository()) as T
            }
        }
    }

    // Eagerligt fält: AppUpdateInstaller registrerar en ActivityResult-launcher som måste
    // finnas före STARTED (samma skäl som i MainActivity).
    private val updateFlow: UpdateFlow = UpdateFlow(AppUpdateInstaller(this) { this.updateFlow.resumeAfterSettingsReturn() })

    private var themeMode by mutableStateOf(ThemeMode.System)

    /** Sätts av en verifierad uppdateringsnotis; förbrukas när Inställningar visats. */
    private var pendingUpdateCheck by mutableStateOf(false)

    /** Rutt att navigera till efter en verifierad notis; nollställs när den förbrukats. */
    private var pendingRoute by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        themeMode = ThemeMode.fromPref(
            getSharedPreferences("settings", MODE_PRIVATE).getInt("night_mode", ThemeMode.System.prefValue)
        )
        if (savedInstanceState == null) pendingRoute = routeFromIntent(intent)
        setContent {
            StockFlipTheme(darkTheme = themeMode.forcedDark ?: isSystemInDarkTheme()) {
                val navController = rememberNavController()
                val snackbar = remember { SnackbarHostState() }
                LaunchedEffect(Unit) { updateFlow.checkOnStartup() }
                LaunchedEffect(Unit) { updateFlow.messages.collect { snackbar.showSnackbar(it) } }
                LaunchedEffect(pendingUpdateCheck) {
                    if (pendingUpdateCheck) {
                        pendingUpdateCheck = false
                        updateFlow.checkManually()
                    }
                }
                updateFlow.release?.let { release ->
                    UpdateDialog(
                        release = release,
                        onInstall = { lifecycleScope.launch { updateFlow.downloadAndInstall(release) } },
                        onSkip = { updateFlow.skip(release) },
                        onDismiss = { updateFlow.dismiss() },
                    )
                }
                LaunchedEffect(pendingRoute) {
                    pendingRoute?.let {
                        navController.navigate(it)
                        pendingRoute = null
                    }
                }
                AppShell(
                    navController = navController,
                    snackbarHostState = snackbar,
                    watchlist = {
                        WatchlistRoute(
                            viewModel = viewModel,
                            snackbarHostState = snackbar,
                            onOpenStock = { navController.navigate(Routes.stockDetail(it)) },
                            onOpenPair = { navController.navigate(Routes.pairDetail(it)) },
                            onAddWatch = { navController.navigateToTab(TopLevelTab.Market) },
                        )
                    },
                    market = {
                        MarketRoute(
                            viewModel = searchViewModel,
                            onOpenStock = { navController.navigate(Routes.stockDetail(it)) },
                        )
                    },
                    settings = {
                        SettingsRoute(
                            viewModel = viewModel,
                            themeMode = themeMode,
                            onThemeChange = ::saveThemeMode,
                            updateFlow = updateFlow,
                            snackbarHostState = snackbar,
                            onOpenDocument = { navController.navigate(Routes.document(it)) },
                        )
                    },
                    pairDetail = { id, onBack ->
                        PairDetailRoute(
                            viewModel = pairDetailViewModel(id),
                            snackbarHostState = snackbar,
                            onSaveEdit = { viewModel.updateWatchItem(it) },
                            onBack = onBack,
                        )
                    },
                    document = { asset, onBack -> DocumentScreen(asset, onBack) },
                    stockDetail = { symbol, onBack ->
                        StockDetailRoute(
                            viewModel = stockDetailViewModel(symbol),
                            snackbarHostState = snackbar,
                            onBack = onBack,
                        )
                    },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        routeFromIntent(intent)?.let { pendingRoute = it }
    }

    private fun pairDetailViewModel(watchItemId: Int): PairDetailViewModel {
        val db = StockPairDatabase.getDatabase(applicationContext)
        val factory = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return PairDetailViewModel(db.watchItemDao(), YahooFinanceService, watchItemId, TriggerHistoryRepository(db.triggerHistoryDao())) as T
            }
        }
        return ViewModelProvider(this, factory)["pair-$watchItemId", PairDetailViewModel::class.java]
    }

    private fun saveThemeMode(mode: ThemeMode) {
        themeMode = mode
        getSharedPreferences("settings", MODE_PRIVATE).edit { putInt("night_mode", mode.prefValue) }
        // Håller övriga AppCompat-aktiviteter (gamla skalet) i takt med valet.
        AppCompatDelegate.setDefaultNightMode(mode.prefValue)
    }

    private fun stockDetailViewModel(symbol: String): StockDetailViewModel {
        val db = StockPairDatabase.getDatabase(applicationContext)
        val podcastDao = try { db.podcastObservationDao() } catch (e: Exception) { null }
        val factory = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return StockDetailViewModel(
                    db.watchItemDao(),
                    YahooFinanceService,
                    symbol,
                    TriggerHistoryRepository(db.triggerHistoryDao()),
                    db.stockNoteDao(),
                    MetricHistoryRepository(db.metricHistoryDao()),
                    db.insiderTransactionDao(),
                    podcastDao,
                    viewModel.lastKnownQuote(symbol),
                ) as T
            }
        }
        // Egen nyckel per symbol så att varje aktie får sin egen ViewModel.
        return ViewModelProvider(this, factory)["stock-$symbol", StockDetailViewModel::class.java]
    }

    /**
     * Samma regler som `MainActivity.handleDeepLinkIntent`: ordningen pair → stock → alerts → update
     * måste matcha notis-producenterna, och utan giltig token avvisas intentet. Skyddade extras
     * rensas alltid. Aktiedetalj öppnas direkt; övriga mål landar på Bevakningar.
     */
    private fun routeFromIntent(intent: Intent?): String? {
        if (intent == null) return null
        val pairId = intent.getIntExtra(AppActivity.EXTRA_OPEN_PAIR_WATCH_ID, -1)
        val ticker = intent.getStringExtra(AppActivity.EXTRA_OPEN_TICKER)
        val watchId = intent.getIntExtra(AppActivity.EXTRA_OPEN_WATCH_ID, -1).takeIf { it > 0 }
        val updateVersion = intent.getStringExtra(AppActivity.EXTRA_OPEN_UPDATE_VERSION)
        val protectedIntent = pairId != -1 || ticker != null || watchId != null || updateVersion != null ||
            intent.hasExtra(AppActivity.EXTRA_TRIGGER_TITLE) || intent.hasExtra(AppActivity.EXTRA_TRIGGER_MESSAGE)
        if (!protectedIntent) return null

        val token = intent.getStringExtra(AppActivity.EXTRA_NOTIFICATION_TOKEN)
        val destination: NotificationDestination? = when {
            pairId != -1 -> NotificationDestination.PairWatch(pairId)
            ticker != null -> NotificationDestination.Stock(ticker, watchId)
            watchId != null -> NotificationDestination.AlertList(watchId)
            updateVersion != null -> NotificationDestination.AppUpdate(updateVersion)
            else -> null
        }
        val verified = destination != null && NotificationNavigationSecurity.verifyToken(destination, token)
        clearProtectedExtras(intent)
        if (!verified) {
            Log.w("AppActivity", "Rejected navigation intent without a valid notification token")
            return null
        }
        return when (destination) {
            is NotificationDestination.Stock -> Routes.stockDetail(destination.ticker)
            is NotificationDestination.PairWatch -> Routes.pairDetail(destination.pairWatchItemId)
            is NotificationDestination.AppUpdate -> {
                pendingUpdateCheck = true
                Routes.SETTINGS
            }
            else -> Routes.WATCHLIST
        }
    }

    private fun clearProtectedExtras(intent: Intent) {
        listOf(
            AppActivity.EXTRA_OPEN_PAIR_WATCH_ID, AppActivity.EXTRA_OPEN_TICKER, AppActivity.EXTRA_OPEN_WATCH_ID,
            AppActivity.EXTRA_OPEN_COMPANY, AppActivity.EXTRA_OPEN_INSIDER_TRANSACTION_ID,
            AppActivity.EXTRA_OPEN_UPDATE_VERSION, AppActivity.EXTRA_TRIGGER_TITLE,
            AppActivity.EXTRA_TRIGGER_MESSAGE, AppActivity.EXTRA_NOTIFICATION_TOKEN,
        ).forEach(intent::removeExtra)
    }

    companion object {
        /** Intent extra: watch item id to open in PairDetailFragment (from pair notification deep link) */
        const val EXTRA_OPEN_PAIR_WATCH_ID = "extra_open_pair_watch_id"
        /** Intent extra: ticker to open in StockDetailFragment (from notification deep link) */
        const val EXTRA_OPEN_TICKER = "extra_open_ticker"
        /** Intent extra: watch item id for the triggered stock alert */
        const val EXTRA_OPEN_WATCH_ID = "extra_open_watch_id"
        /** Intent extra: company name for the ticker (optional, for display) */
        const val EXTRA_OPEN_COMPANY = "extra_open_company"
        /** Intent extra: insider transaction id to highlight when opening from an insider notification */
        const val EXTRA_OPEN_INSIDER_TRANSACTION_ID = "extra_open_insider_transaction_id"
        /** Intent extra: version name to open the update confirmation dialog for (from update notification deep link) */
        const val EXTRA_OPEN_UPDATE_VERSION = "extra_open_update_version"
        /** Intent extra: human-readable trigger title for notification landing */
        const val EXTRA_TRIGGER_TITLE = "extra_trigger_title"
        /** Intent extra: human-readable trigger message for notification landing */
        const val EXTRA_TRIGGER_MESSAGE = "extra_trigger_message"
        /** Intent extra: one-time token proving the navigation intent came from our own notification PendingIntent */
        const val EXTRA_NOTIFICATION_TOKEN = "extra_notification_token"
    }
}
