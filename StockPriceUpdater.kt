package com.stockflip

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit
import kotlin.math.abs

/**
 * Worker som utvärderar alert-regler och skickar notifieringar.
 * 
 * Refaktorerad enligt PRD Fas 1 att använda AlertEvaluator för all villkorslogik.
 * Stödjer både par-bevakningar (StockPair) och single-stock bevakningar (WatchItem).
 */
class StockPriceUpdater(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    private val TAG = "StockPriceUpdater"
    private val today = WatchItem.getTodayDateString()

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val database = StockPairDatabase.getDatabase(applicationContext)
            
            // Hämta alla alerts
            val stockPairs = database.stockPairDao().getAllStockPairs()
            val watchItems = database.watchItemDao().getAllWatchItems()
            
            Log.d(TAG, "Evaluating ${stockPairs.size} pairs and ${watchItems.size} watch items")
            
            // Utvärdera par-bevakningar (befintlig funktionalitet måste fortsätta fungera)
            stockPairs.forEach { pair ->
                try {
                    evaluatePairAlert(pair, database)
                } catch (e: Exception) {
                    Log.e(TAG, "Error evaluating pair ${pair.id}: ${e.message}", e)
                }
            }
            
            // Utvärdera single-stock alerts (WatchItems)
            watchItems.forEach { watchItem ->
                try {
                    evaluateWatchItemAlert(watchItem, database)
                } catch (e: Exception) {
                    Log.e(TAG, "Error evaluating watch item ${watchItem.id}: ${e.message}", e)
                }
            }
            
            // Backward compatibility: hantera gamla StockWatchEntity (WatchCriteria systemet)
            // Detta kan tas bort när allt är migrerat till WatchItem
            try {
                val stockWatches = database.stockWatchDao().getAllStockWatches().first()
                stockWatches.forEach { watch ->
                    try {
                        evaluateLegacyWatch(watch, database)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error evaluating legacy watch ${watch.id}: ${e.message}", e)
                    }
                }
            } catch (e: Exception) {
                // Om stockWatchDao inte finns, ignorera
                Log.d(TAG, "Legacy watch system not available: ${e.message}")
            }
            
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Error in doWork: ${e.message}", e)
            Result.failure()
        }
    }

    /**
     * Utvärderar par-bevakning (StockPair).
     * Detta är befintlig funktionalitet som måste fortsätta fungera exakt som tidigare.
     */
    private suspend fun evaluatePairAlert(pair: StockPair, database: StockPairDatabase) {
        val priceA = YahooFinanceService.getStockPrice(pair.ticker1) ?: return
        val priceB = YahooFinanceService.getStockPrice(pair.ticker2) ?: return
        
        val alertRule = AlertRuleConverter.toAlertRule(pair)
        val snapshotA = MarketSnapshot.forPair(priceA, priceB)
        val snapshotB = MarketSnapshot.forPair(priceB, priceA)
        
        val shouldTrigger = AlertEvaluator.evaluate(alertRule, snapshotA, snapshotB)
        
        if (shouldTrigger) {
            val title = "Aktiepar Alert"
            val message = buildPairNotificationMessage(pair, priceA, priceB)
            showNotification(pair.id, title, message)
        }
    }

    /**
     * Utvärderar single-stock alert (WatchItem).
     * Använder AlertEvaluator och spam-skydd.
     * Stödjer både enskilda AlertRule och kombinerade AlertExpression.
     */
    private suspend fun evaluateWatchItemAlert(watchItem: WatchItem, database: StockPairDatabase) {
        // Kontrollera spam-skydd
        if (!watchItem.canTrigger(today)) {
            Log.d(TAG, "Skipping watch item ${watchItem.id} due to spam protection")
            return
        }
        
        // Hantera Combined WatchType separat
        if (watchItem.watchType is WatchType.Combined) {
            evaluateCombinedAlert(watchItem, database)
            return
        }
        
        // Konvertera WatchItem till AlertRule
        val alertRule = AlertRuleConverter.toAlertRule(watchItem) ?: return
        
        // Hämta marknadsdata baserat på alert-typ
        val snapshot = when (alertRule) {
            is AlertRule.PairSpread -> {
                // Par-alerts hanteras i evaluatePairAlert
                return
            }
            is AlertRule.SinglePrice -> {
                createSingleStockSnapshot(alertRule.symbol)
            }
            is AlertRule.SingleDrawdownFromHigh -> {
                createSingleStockSnapshot(alertRule.symbol, includeWeek52High = true)
            }
            is AlertRule.SingleDailyMove -> {
                createSingleStockSnapshot(alertRule.symbol, includeDailyChange = true)
            }
            is AlertRule.SingleKeyMetric -> {
                createSingleStockSnapshot(alertRule.symbol, includeKeyMetrics = true, metricType = alertRule.metricType)
            }
        } ?: return
        
        // Utvärdera alert-regel
        val shouldTrigger = AlertEvaluator.evaluate(alertRule, snapshot)
        
        if (shouldTrigger) {
            // Markera som triggad
            val updatedWatchItem = watchItem.markAsTriggered(today)
            database.watchItemDao().update(updatedWatchItem)
            
            // Skicka notis
            val (title, message) = buildNotificationMessage(watchItem, alertRule, snapshot)
            showNotification(watchItem.id + 10000, title, message)
            
            Log.d(TAG, "Alert triggered for watch item ${watchItem.id}: $title")
        }
    }

    /**
     * Utvärderar kombinerat alert (Combined WatchType).
     * Använder ExpressionEvaluator för att evaluera AlertExpression med flera villkor.
     */
    private suspend fun evaluateCombinedAlert(watchItem: WatchItem, database: StockPairDatabase) {
        val combined = watchItem.watchType as? WatchType.Combined ?: return
        val expression = combined.expression
        
        // Hämta alla symboler som behövs för uttrycket
        val symbols = expression.getSymbols()
        if (symbols.isEmpty()) {
            Log.w(TAG, "Combined alert ${watchItem.id} has no symbols")
            return
        }
        
        Log.d(TAG, "Evaluating combined alert ${watchItem.id} with symbols: $symbols")
        
        // Skapa MarketSnapshot för varje symbol
        val snapshots = mutableMapOf<String, MarketSnapshot>()
        
        for (symbol in symbols) {
            // För kombinerade alerts behöver vi all data (pris, 52w high, daily change, key metrics)
            val snapshot = createCompleteSnapshot(symbol) ?: continue
            snapshots[symbol] = snapshot
        }
        
        // Validera att alla nödvändiga snapshots finns
        if (!ExpressionEvaluator.validateSnapshots(expression, snapshots)) {
            Log.w(TAG, "Missing snapshots for combined alert ${watchItem.id}")
            return
        }
        
        // Utvärdera uttrycket
        val shouldTrigger = ExpressionEvaluator.evaluateExpression(expression, snapshots)
        
        if (shouldTrigger) {
            // Markera som triggad
            val updatedWatchItem = watchItem.markAsTriggered(today)
            database.watchItemDao().update(updatedWatchItem)
            
            // Skicka notis
            val title = "Kombinerat Alert: ${watchItem.getDisplayName()}"
            val message = "Kombinerat larm triggat: ${expression.getDescription()}"
            showNotification(watchItem.id + 10000, title, message)
            
            Log.d(TAG, "Combined alert triggered for watch item ${watchItem.id}: $title")
        }
    }

    /**
     * Skapar en komplett MarketSnapshot med all tillgänglig data.
     * Används för kombinerade alerts som kan behöva olika typer av data.
     */
    private suspend fun createCompleteSnapshot(symbol: String): MarketSnapshot? {
        val lastPrice = YahooFinanceService.getStockPrice(symbol) ?: return null
        val previousClose = YahooFinanceService.getPreviousClose(symbol)
        val week52High = YahooFinanceService.getATH(symbol) // getATH hämtar faktiskt 52w high
        
        // Hämta alla nyckeltal
        val keyMetrics = mutableMapOf<AlertRule.KeyMetricType, Double>()
        
        // Försök hämta P/E
        val peValue = YahooFinanceService.getKeyMetric(symbol, WatchType.MetricType.PE_RATIO)
        if (peValue != null) {
            keyMetrics[AlertRule.KeyMetricType.PE_RATIO] = peValue
        }
        
        // Försök hämta P/S
        val psValue = YahooFinanceService.getKeyMetric(symbol, WatchType.MetricType.PS_RATIO)
        if (psValue != null) {
            keyMetrics[AlertRule.KeyMetricType.PS_RATIO] = psValue
        }
        
        // Försök hämta Dividend Yield
        val yieldValue = YahooFinanceService.getKeyMetric(symbol, WatchType.MetricType.DIVIDEND_YIELD)
        if (yieldValue != null) {
            keyMetrics[AlertRule.KeyMetricType.DIVIDEND_YIELD] = yieldValue
        }
        
        return MarketSnapshot.forSingleStock(
            lastPrice = lastPrice,
            previousClose = previousClose,
            week52High = week52High,
            keyMetrics = keyMetrics
        )
    }

    /**
     * Skapar MarketSnapshot för single-stock alert.
     */
    private suspend fun createSingleStockSnapshot(
        symbol: String,
        includeWeek52High: Boolean = false,
        includeDailyChange: Boolean = false,
        includeKeyMetrics: Boolean = false,
        metricType: AlertRule.KeyMetricType? = null
    ): MarketSnapshot? {
        val lastPrice = YahooFinanceService.getStockPrice(symbol) ?: return null
        
        val previousClose = if (includeDailyChange) {
            YahooFinanceService.getPreviousClose(symbol)
        } else {
            null
        }
        
        val week52High = if (includeWeek52High) {
            YahooFinanceService.getATH(symbol) // getATH hämtar faktiskt 52w high
        } else {
            null
        }
        
        val keyMetrics = if (includeKeyMetrics && metricType != null) {
            val watchTypeMetric = when (metricType) {
                AlertRule.KeyMetricType.PE_RATIO -> WatchType.MetricType.PE_RATIO
                AlertRule.KeyMetricType.PS_RATIO -> WatchType.MetricType.PS_RATIO
                AlertRule.KeyMetricType.DIVIDEND_YIELD -> WatchType.MetricType.DIVIDEND_YIELD
            }
            val metricValue = YahooFinanceService.getKeyMetric(symbol, watchTypeMetric)
            if (metricValue != null) {
                mapOf(metricType to metricValue)
            } else {
                emptyMap()
            }
        } else {
            emptyMap()
        }
        
        return MarketSnapshot.forSingleStock(
            lastPrice = lastPrice,
            previousClose = previousClose,
            week52High = week52High,
            keyMetrics = keyMetrics
        )
    }

    /**
     * Bygger notismeddelande för WatchItem.
     */
    private fun buildNotificationMessage(
        watchItem: WatchItem,
        alertRule: AlertRule,
        snapshot: MarketSnapshot
    ): Pair<String, String> {
        val symbol = watchItem.ticker ?: watchItem.ticker1 ?: "Unknown"
        val companyName = watchItem.companyName ?: watchItem.companyName1 ?: symbol
        
        return when (alertRule) {
            is AlertRule.SinglePrice -> {
                val currentPrice = snapshot.lastPrice ?: 0.0
                when (alertRule.comparisonType) {
                    AlertRule.PriceComparisonType.BELOW -> {
                        "Pris Alert: $symbol" to 
                            "$companyName har nått ${alertRule.priceLimit} SEK. Nuvarande pris: ${String.format("%.2f", currentPrice)} SEK"
                    }
                    AlertRule.PriceComparisonType.ABOVE -> {
                        "Pris Alert: $symbol" to 
                            "$companyName har överstigit ${alertRule.priceLimit} SEK. Nuvarande pris: ${String.format("%.2f", currentPrice)} SEK"
                    }
                    AlertRule.PriceComparisonType.WITHIN_RANGE -> {
                        val maxPrice = alertRule.maxPrice ?: alertRule.priceLimit
                        "Prisintervall Alert: $symbol" to 
                            "$companyName är nu inom intervallet ${alertRule.priceLimit}-${maxPrice} SEK. Nuvarande pris: ${String.format("%.2f", currentPrice)} SEK"
                    }
                }
            }
            is AlertRule.SingleDrawdownFromHigh -> {
                val currentPrice = snapshot.lastPrice ?: 0.0
                val week52High = snapshot.week52High ?: 0.0
                val effectiveHigh = if (currentPrice > week52High) currentPrice else week52High
                when (alertRule.dropType) {
                    AlertRule.DrawdownDropType.PERCENTAGE -> {
                        val dropPercent = if (effectiveHigh > 0) {
                            ((effectiveHigh - currentPrice) / effectiveHigh) * 100
                        } else {
                            0.0
                        }
                        "52w High Drop Alert: $symbol" to 
                            "$companyName har fallit ${String.format("%.2f", dropPercent)}% från 52-veckors högsta (${String.format("%.2f", effectiveHigh)} SEK). Nuvarande pris: ${String.format("%.2f", currentPrice)} SEK"
                    }
                    AlertRule.DrawdownDropType.ABSOLUTE -> {
                        val dropAbsolute = effectiveHigh - currentPrice
                        "52w High Drop Alert: $symbol" to 
                            "$companyName har fallit ${String.format("%.2f", dropAbsolute)} SEK från 52-veckors högsta (${String.format("%.2f", effectiveHigh)} SEK). Nuvarande pris: ${String.format("%.2f", currentPrice)} SEK"
                    }
                }
            }
            is AlertRule.SingleDailyMove -> {
                val dailyChange = snapshot.getDailyChangePercent() ?: 0.0
                val direction = when (alertRule.direction) {
                    AlertRule.DailyMoveDirection.UP -> "upp"
                    AlertRule.DailyMoveDirection.DOWN -> "ned"
                    AlertRule.DailyMoveDirection.BOTH -> if (dailyChange >= 0) "upp" else "ned"
                }
                "Dagsrörelse Alert: $symbol" to 
                    "$companyName har rört sig ${String.format("%.2f", abs(dailyChange))}% $direction idag. Nuvarande förändring: ${String.format("%.2f", dailyChange)}%"
            }
            is AlertRule.SingleKeyMetric -> {
                val currentMetricValue = snapshot.keyMetrics[alertRule.metricType] ?: 0.0
                val metricName = when (alertRule.metricType) {
                    AlertRule.KeyMetricType.PE_RATIO -> "P/E"
                    AlertRule.KeyMetricType.PS_RATIO -> "P/S"
                    AlertRule.KeyMetricType.DIVIDEND_YIELD -> "Direktavkastning"
                }
                val direction = when (alertRule.direction) {
                    AlertRule.PriceComparisonType.ABOVE -> "överstigit"
                    AlertRule.PriceComparisonType.BELOW -> "understigit"
                    AlertRule.PriceComparisonType.WITHIN_RANGE -> "" // Stöds inte för KeyMetrics
                }
                val unit = if (alertRule.metricType == AlertRule.KeyMetricType.DIVIDEND_YIELD) "%" else ""
                "Nyckeltal Alert: $symbol" to 
                    "$companyName har $direction ${alertRule.targetValue}$unit för $metricName. Nuvarande värde: ${String.format("%.2f", currentMetricValue)}$unit"
            }
            is AlertRule.PairSpread -> {
                // Hanteras i buildPairNotificationMessage
                "" to ""
            }
        }
    }

    /**
     * Bygger notismeddelande för par-bevakning.
     */
    private fun buildPairNotificationMessage(
        pair: StockPair,
        priceA: Double,
        priceB: Double
    ): String {
        val priceDiff = abs(priceA - priceB)
        return when {
            pair.notifyWhenEqual && priceDiff < 0.01 -> 
                "${pair.companyName1} och ${pair.companyName2} har samma pris: ${String.format("%.2f", priceA)} SEK"
            priceDiff >= pair.priceDifference -> 
                "Prisdifferens mellan ${pair.companyName1} och ${pair.companyName2} har nått ${String.format("%.2f", priceDiff)} SEK"
            else -> ""
        }
    }

    /**
     * Backward compatibility: hantera gamla StockWatchEntity (WatchCriteria systemet).
     * Detta kan tas bort när allt är migrerat till WatchItem.
     */
    private suspend fun evaluateLegacyWatch(
        watch: StockWatchEntity,
        database: StockPairDatabase
    ) {
        // Denna logik behålls för backward compatibility
        // Men vi rekommenderar att migrera till WatchItem
        val criteria = watch.watchCriteria ?: return
        
        val currentPrice = YahooFinanceService.getStockPrice(watch.symbol) ?: return
        
        val isTriggered = when (criteria) {
            is WatchCriteria.PriceTargetCriteria -> {
                when (criteria.comparison) {
                    ComparisonType.ABOVE -> currentPrice >= criteria.threshold
                    ComparisonType.BELOW -> currentPrice <= criteria.threshold
                }
            }
            is WatchCriteria.ATHDropCriteria -> {
                val ath = if (watch.ath > 0) watch.ath else YahooFinanceService.getATH(watch.symbol) ?: return false
                val effectiveAth = if (currentPrice > ath) currentPrice else ath
                val dropPercentage = ((effectiveAth - currentPrice) / effectiveAth) * 100
                dropPercentage >= criteria.dropPercentage
            }
            else -> false // Andra typer hanteras inte här
        }
        
        if (watch.notifyOnTrigger && isTriggered) {
            val title = "Legacy Alert: ${watch.symbol}"
            val message = "${watch.symbol} har triggat alert. Nuvarande pris: $currentPrice"
            showNotification(watch.id + 20000, title, message)
        }
    }

    private fun showNotification(notificationId: Int, title: String, message: String) {
        if (title.isEmpty() || message.isEmpty()) {
            return
        }
        
        val channelId = "stock_flip_channel"
        val notificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "Stock Updates", NotificationManager.IMPORTANCE_DEFAULT)
            notificationManager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(applicationContext, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        notificationManager.notify(notificationId, notification)
    }

    companion object {
        fun startPeriodicUpdate(context: Context) {
            val request = PeriodicWorkRequestBuilder<StockPriceUpdater>(15, TimeUnit.MINUTES).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "StockPriceUpdater",
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }
    }
}
