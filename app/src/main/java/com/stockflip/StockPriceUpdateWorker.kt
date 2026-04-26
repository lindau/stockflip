package com.stockflip

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlinx.coroutines.delay
import androidx.work.WorkManager
import com.stockflip.repository.TriggerHistoryRepository
import com.stockflip.usecase.UpdateStockPairsPricesUseCase
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class StockPriceUpdateWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    private data class TriggerNotificationPayload(
        val title: String,
        val message: String
    )

    override suspend fun doWork(): Result {
        var attempt = 1
        
        while (true) {
            try {
                Log.d(TAG, "Starting price update (attempt $attempt)")
                val database = StockPairDatabase.getDatabase(applicationContext)
                val stockPairDao = database.stockPairDao()
                val yahooFinanceService = YahooFinanceService
                val useCase = UpdateStockPairsPricesUseCase(stockPairDao, yahooFinanceService)
                val updatedPairs: List<StockPair> = useCase.executeUpdateStockPairsPrices()
                val updatedCount: Int = updatedPairs.size
                updatedPairs.forEach { pair: StockPair ->
                    val price1: Double = pair.currentPrice1
                    val price2: Double = pair.currentPrice2
                    if (shouldNotify(pair, price1, price2)) {
                        val title = "Stock Price Alert"
                        val message = buildNotificationMessage(pair, price1, price2)
                        showNotification(title, message)
                    }
                }

                if (updatedCount > 0) {
                    Log.d(TAG, "Broadcasting price update completion for $updatedCount pairs")
                    val intent = Intent(ACTION_PRICES_UPDATED).apply {
                        `package` = applicationContext.packageName
                    }
                    applicationContext.sendBroadcast(intent)
                } else {
                    Log.w(TAG, "No prices were updated")
                }

                evaluateWatchItemAlerts(database, yahooFinanceService)

                return Result.success()
                
            } catch (e: Exception) {
                Log.e(TAG, "Price update worker failed: ${e.message}")
                
                if (StockMarketScheduler.shouldRetry(attempt, e)) {
                    attempt++
                    delay(StockMarketScheduler.RETRY_DELAY_MINUTES * 60 * 1000) // Convert minutes to milliseconds
                    continue
                }
                
                return Result.failure()
            }
        }
    }

    private fun shouldNotify(pair: StockPair, price1: Double, price2: Double): Boolean {
        // Don't notify if either price is 0 (not yet fetched)
        if (price1 == 0.0 || price2 == 0.0) {
            Log.d(TAG, "Skipping notification check for ${pair.ticker1}-${pair.ticker2} because prices are not yet fetched")
            return false
        }
        
        val shouldNotify = (pair.notifyWhenEqual && abs(price1 - price2) < PRICE_EQUALITY_THRESHOLD) ||
               (pair.priceDifference > 0 && abs(price1 - price2) >= pair.priceDifference)
        
        Log.d(TAG, """
            Notification check for stock pair:
            Price difference: ${abs(price1 - price2)}
            Notify when equal: ${pair.notifyWhenEqual}
            Price difference threshold: ${pair.priceDifference}
            Should notify: $shouldNotify
        """.trimIndent())
        
        return shouldNotify
    }

    private fun buildNotificationMessage(pair: StockPair, price1: Double, price2: Double): String {
        return when {
            pair.notifyWhenEqual && abs(price1 - price2) < PRICE_EQUALITY_THRESHOLD -> 
                "${pair.companyName1} and ${pair.companyName2} prices are now equal at ${String.format("%.2f", price1)} SEK"
            abs(price1 - price2) >= pair.priceDifference -> 
                "Price difference between ${pair.companyName1} and ${pair.companyName2} has reached ${String.format("%.2f", abs(price1 - price2))} SEK"
            else -> ""
        }
    }

    private fun showNotification(
        title: String,
        message: String,
        ticker: String? = null,
        companyName: String? = null,
        pairWatchItemId: Int? = null,
        watchItemId: Int? = null,
        triggerTitle: String? = null,
        triggerMessage: String? = null
    ) {
        val notificationToken = NotificationNavigationSecurity.issueToken()
        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            if (pairWatchItemId != null) {
                putExtra(MainActivity.EXTRA_OPEN_PAIR_WATCH_ID, pairWatchItemId)
            } else if (ticker != null) {
                putExtra(MainActivity.EXTRA_OPEN_TICKER, ticker)
                putExtra(MainActivity.EXTRA_OPEN_COMPANY, companyName)
                watchItemId?.let { putExtra(MainActivity.EXTRA_OPEN_WATCH_ID, it) }
            }
            putExtra(MainActivity.EXTRA_TRIGGER_TITLE, triggerTitle ?: title)
            putExtra(MainActivity.EXTRA_TRIGGER_MESSAGE, triggerMessage ?: message)
            putExtra(MainActivity.EXTRA_NOTIFICATION_TOKEN, notificationToken)
        }

        val pendingIntent = PendingIntent.getActivity(
            applicationContext,
            pairWatchItemId ?: (ticker?.hashCode() ?: 0),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val publicVersion = NotificationCompat.Builder(applicationContext, StockPriceUpdater.CHANNEL_ID)
            .setContentTitle(applicationContext.getString(R.string.app_name))
            .setContentText("Öppna appen för att visa bevakningsdetaljer")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        val notification = NotificationCompat.Builder(applicationContext, StockPriceUpdater.CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(message)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setContentIntent(pendingIntent)  // Add the PendingIntent
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(publicVersion)
            .build()

        val notificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notificationId = System.currentTimeMillis().toInt()
        notificationManager.notify(notificationId, notification)
        Log.d(TAG, "Sent trigger notification")
    }

    private suspend fun evaluateWatchItemAlerts(
        database: StockPairDatabase,
        marketDataService: MarketDataService
    ) {
        val watchItemDao = database.watchItemDao()
        val triggerHistoryRepository = TriggerHistoryRepository(database.triggerHistoryDao())
        val activeItems = watchItemDao.getAllWatchItems().filter { it.isActive }
        if (activeItems.isEmpty()) return

        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

        // Collect unique tickers and which key metrics are needed per ticker
        val tickers = mutableSetOf<String>()
        val keyMetricNeeds = mutableMapOf<String, MutableSet<WatchType.MetricType>>()
        for (item in activeItems) {
            item.ticker?.let { tickers.add(it) }
            item.ticker1?.let { tickers.add(it) }
            item.ticker2?.let { tickers.add(it) }
            if (item.watchType is WatchType.KeyMetrics) {
                val ticker = item.ticker ?: continue
                val metricType = item.watchType.metricType
                keyMetricNeeds.getOrPut(ticker) { mutableSetOf() }.add(metricType)
            }
        }

        // Fetch market snapshots for all tickers
        val snapshots = mutableMapOf<String, MarketSnapshot>()
        for (ticker in tickers) {
            try {
                val price = marketDataService.getStockPrice(ticker)
                val prevClose = marketDataService.getPreviousClose(ticker)
                val week52High = marketDataService.getATH(ticker)
                val metricsMap = mutableMapOf<AlertRule.KeyMetricType, Double>()
                keyMetricNeeds[ticker]?.forEach { metricType ->
                    val alertMetricType = when (metricType) {
                        WatchType.MetricType.PE_RATIO -> AlertRule.KeyMetricType.PE_RATIO
                        WatchType.MetricType.PS_RATIO -> AlertRule.KeyMetricType.PS_RATIO
                        WatchType.MetricType.DIVIDEND_YIELD -> AlertRule.KeyMetricType.DIVIDEND_YIELD
                    }
                    marketDataService.getKeyMetric(ticker, metricType)?.let { metricsMap[alertMetricType] = it }
                }
                snapshots[ticker] = MarketSnapshot.forSingleStock(price, prevClose, week52High, metricsMap)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to fetch market snapshot: ${e.message}")
            }
        }

        // Evaluate each active watch item
        for (item in activeItems) {
            try {
                if (!item.canTrigger(today)) continue
                val triggered = evaluateWatchItem(item, snapshots) ?: continue
                if (triggered) {
                    val payload = buildTriggerNotificationPayload(item, snapshots)
                    when (item.watchType) {
                        is WatchType.PricePair -> {
                            showNotification(
                                title = payload.title,
                                message = payload.message,
                                pairWatchItemId = item.id,
                                triggerTitle = payload.title,
                                triggerMessage = payload.message
                            )
                        }
                        else -> {
                            val ticker = item.ticker ?: item.ticker1
                            showNotification(
                                title = payload.title,
                                message = payload.message,
                                ticker = ticker,
                                companyName = item.companyName,
                                watchItemId = item.id,
                                triggerTitle = payload.title,
                                triggerMessage = payload.message
                            )
                        }
                    }
                    val triggeredItem = item.markAsTriggered(today)
                    val updatedItem = when (item.watchType) {
                        is WatchType.PriceTarget, is WatchType.ATHBased ->
                            triggeredItem.copy(isActive = false)
                        else -> triggeredItem
                    }
                    watchItemDao.update(updatedItem)
                    triggerHistoryRepository.record(item.id)
                    Log.d(TAG, "Watch item triggered")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to evaluate watch item: ${e.message}")
            }
        }
    }

    private fun evaluateWatchItem(
        item: WatchItem,
        snapshots: Map<String, MarketSnapshot>
    ): Boolean? {
        return when (item.watchType) {
            is WatchType.Combined -> {
                val expression = AlertRuleConverter.toAlertExpression(item) ?: return null
                ExpressionEvaluator.evaluateExpression(expression, snapshots)
            }
            else -> {
                val rule = AlertRuleConverter.toAlertRule(item) ?: return null
                when (rule) {
                    is AlertRule.PairSpread -> {
                        val snapshotA = snapshots[rule.symbolA] ?: return null
                        val snapshotB = snapshots[rule.symbolB] ?: return null
                        AlertEvaluator.evaluate(rule, snapshotA, snapshotB)
                    }
                    is AlertRule.SinglePrice -> {
                        val snapshot = snapshots[rule.symbol] ?: return null
                        AlertEvaluator.evaluate(rule, snapshot)
                    }
                    is AlertRule.SingleDrawdownFromHigh -> {
                        val snapshot = snapshots[rule.symbol] ?: return null
                        AlertEvaluator.evaluate(rule, snapshot)
                    }
                    is AlertRule.SingleDailyMove -> {
                        val snapshot = snapshots[rule.symbol] ?: return null
                        AlertEvaluator.evaluate(rule, snapshot)
                    }
                    is AlertRule.SingleKeyMetric -> {
                        val snapshot = snapshots[rule.symbol] ?: return null
                        AlertEvaluator.evaluate(rule, snapshot)
                    }
                }
            }
        }
    }

    private fun buildTriggerNotificationPayload(
        item: WatchItem,
        snapshots: Map<String, MarketSnapshot>
    ): TriggerNotificationPayload {
        return when (val watchType = item.watchType) {
            is WatchType.PriceTarget -> {
                val ticker = item.ticker ?: ""
                val price = snapshots[ticker]?.lastPrice
                val directionText = if (watchType.direction == WatchType.PriceDirection.ABOVE) "nådde" else "föll till"
                TriggerNotificationPayload(
                    title = "${item.companyName ?: ticker} $directionText ${formatPrice(price ?: watchType.targetPrice)}",
                    message = "Målpris ${formatPrice(watchType.targetPrice)} har nåtts."
                )
            }

            is WatchType.ATHBased -> {
                val ticker = item.ticker ?: ""
                val snapshot = snapshots[ticker]
                when (watchType.dropType) {
                    WatchType.DropType.PERCENTAGE -> {
                        val drawdown = snapshot?.let {
                            val currentPrice = it.lastPrice
                            val week52High = it.week52High
                            if (currentPrice != null && week52High != null && week52High > 0.0) {
                                ((week52High - currentPrice) / week52High) * 100
                            } else null
                        } ?: watchType.dropValue
                        TriggerNotificationPayload(
                            title = "${item.companyName ?: ticker} har fallit ${CurrencyHelper.formatDecimal(drawdown)} %",
                            message = "Din drawdown-nivå på ${CurrencyHelper.formatDecimal(watchType.dropValue)} % är nådd."
                        )
                    }

                    WatchType.DropType.ABSOLUTE -> {
                        val drop = snapshot?.let {
                            val currentPrice = it.lastPrice
                            val week52High = it.week52High
                            if (currentPrice != null && week52High != null) {
                                week52High - currentPrice
                            } else null
                        } ?: watchType.dropValue
                        TriggerNotificationPayload(
                            title = "${item.companyName ?: ticker} har tappat ${formatPrice(drop)} från toppen",
                            message = "Din drawdown-nivå på ${formatPrice(watchType.dropValue)} är nådd."
                        )
                    }
                }
            }

            is WatchType.KeyMetrics -> {
                val ticker = item.ticker ?: ""
                val metricType = when (watchType.metricType) {
                    WatchType.MetricType.PE_RATIO -> AlertRule.KeyMetricType.PE_RATIO
                    WatchType.MetricType.PS_RATIO -> AlertRule.KeyMetricType.PS_RATIO
                    WatchType.MetricType.DIVIDEND_YIELD -> AlertRule.KeyMetricType.DIVIDEND_YIELD
                }
                val currentValue = snapshots[ticker]?.keyMetrics?.get(metricType) ?: watchType.targetValue
                val metricLabel = when (watchType.metricType) {
                    WatchType.MetricType.PE_RATIO -> "P/E"
                    WatchType.MetricType.PS_RATIO -> "P/S"
                    WatchType.MetricType.DIVIDEND_YIELD -> "utdelning"
                }
                val relation = if (watchType.direction == WatchType.PriceDirection.ABOVE) "över" else "under"
                TriggerNotificationPayload(
                    title = "${item.companyName ?: ticker}: $metricLabel ${formatMetricValue(watchType.metricType, currentValue)}",
                    message = "$metricLabel är nu $relation din nivå ${formatMetricValue(watchType.metricType, watchType.targetValue)}."
                )
            }

            is WatchType.DailyMove -> {
                val ticker = item.ticker ?: ""
                val move = snapshots[ticker]?.getDailyChangePercent() ?: watchType.percentThreshold
                TriggerNotificationPayload(
                    title = "${item.companyName ?: ticker} rör sig ${signedPercent(move)} idag",
                    message = "Din dagsrörelselarm på ${CurrencyHelper.formatDecimal(watchType.percentThreshold)} % har triggat."
                )
            }

            is WatchType.PriceRange -> {
                val ticker = item.ticker ?: ""
                val price = snapshots[ticker]?.lastPrice
                TriggerNotificationPayload(
                    title = "${item.companyName ?: ticker} är nu inom ${formatPrice(watchType.minPrice)}-${formatPrice(watchType.maxPrice)}",
                    message = "Aktuellt pris är ${formatPrice(price ?: watchType.minPrice)}."
                )
            }

            is WatchType.PricePair -> {
                val snapshotA = item.ticker1?.let { snapshots[it] }
                val snapshotB = item.ticker2?.let { snapshots[it] }
                val spread = if (snapshotA?.lastPrice != null && snapshotB?.lastPrice != null) {
                    abs(snapshotA.lastPrice - snapshotB.lastPrice)
                } else {
                    watchType.priceDifference
                }
                TriggerNotificationPayload(
                    title = "${item.companyName1 ?: item.ticker1}/${item.companyName2 ?: item.ticker2} spread ${formatPrice(spread)}",
                    message = "Ditt parlarm har triggat${if (watchType.notifyWhenEqual && spread < PRICE_EQUALITY_THRESHOLD) " vid lika priser" else ""}."
                )
            }

            is WatchType.Combined -> {
                TriggerNotificationPayload(
                    title = "Kombinerat larm triggat",
                    message = item.getDisplayName()
                )
            }
        }
    }

    private fun formatPrice(value: Double): String = "${CurrencyHelper.formatDecimal(value)} SEK"

    private fun formatMetricValue(metricType: WatchType.MetricType, value: Double): String {
        return when (metricType) {
            WatchType.MetricType.DIVIDEND_YIELD -> "${CurrencyHelper.formatDecimal(value)}%"
            else -> CurrencyHelper.formatDecimal(value)
        }
    }

    private fun signedPercent(value: Double): String {
        val sign = if (value >= 0) "+" else ""
        return "$sign${CurrencyHelper.formatDecimal(value)} %"
    }

    companion object {
        private const val TAG = "StockPriceUpdateWorker"
        private const val PRICE_EQUALITY_THRESHOLD = 0.01
        const val ACTION_PRICES_UPDATED = "com.stockflip.ACTION_PRICES_UPDATED"
    }
} 
