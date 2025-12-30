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

// Modified StockPriceUpdater.kt
class StockPriceUpdater(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val database = StockPairDatabase.getDatabase(applicationContext)
        val stockPairs = database.stockPairDao().getAllStockPairs().first()
        val stockWatches = database.stockWatchDao().getAllStockWatches().first()
        
        stockPairs.forEach { pair ->
            try {
                val priceA = YahooFinanceService.getStockPrice(pair.stockA)
                val priceB = YahooFinanceService.getStockPrice(pair.stockB)

                if (priceA != null && priceB != null) {
                    val priceDifference = abs(priceA - priceB)
                    if (priceDifference >= pair.priceDifference) {
                        createNotification(pair.id, pair.stockA, pair.stockB, priceA, priceB, "Arbitrage Opportunity")
                    } else if (pair.notifyWhenEqual && priceDifference == 0.0) {
                        createNotification(pair.id, pair.stockA, pair.stockB, priceA, priceB, "Prices Are Equal")
                    }
                }
            } catch (e: Exception) {
                Log.e("StockPriceUpdater", "Error updating prices for pair ${pair.id}", e)
            }
        }

        stockWatches.forEach { watch ->
            try {
                val criteria = watch.watchCriteria
                if (criteria == null) {
                    // Backward compatibility: handle old format
                    val currentPrice = YahooFinanceService.getStockPrice(watch.symbol)
                    val ath = if (watch.ath > 0) watch.ath else YahooFinanceService.getATH(watch.symbol)

                    if (currentPrice != null && ath != null && ath > 0) {
                        val effectiveAth = if (currentPrice > ath) currentPrice else ath
                        val diff = effectiveAth - currentPrice
                        val isTriggered = if (watch.isPercentage) {
                            (diff / effectiveAth * 100) >= watch.dropValue
                        } else {
                            diff >= watch.dropValue
                        }

                        if (watch.notifyOnTrigger && isTriggered) {
                            createNotificationForWatch(watch, currentPrice, effectiveAth, "ATH Drop Alert: ${watch.symbol}")
                        }
                    }
                } else {
                    // New flexible criteria system
                    checkWatchCriteria(watch, criteria)
                }
            } catch (e: Exception) {
                Log.e("StockPriceUpdater", "Error updating watch for ${watch.symbol}", e)
            }
        }

        Result.success()
    }

    private fun createNotification(id: Int, stockA: String, stockB: String, priceA: Double, priceB: Double, title: String) {
        val message = "$stockA: $priceA, $stockB: $priceB"
        showNotification(id, title, message)
    }

    private fun checkWatchCriteria(watch: StockWatchEntity, criteria: WatchCriteria) {
        val currentPrice = YahooFinanceService.getStockPrice(watch.symbol) ?: return
        
        val isTriggered = when (criteria) {
            is WatchCriteria.PriceTargetCriteria -> {
                when (criteria.comparison) {
                    ComparisonType.ABOVE -> currentPrice >= criteria.threshold
                    ComparisonType.BELOW -> currentPrice <= criteria.threshold
                }
            }
            is WatchCriteria.PERatioCriteria -> {
                val peRatio = YahooFinanceService.getPERatio(watch.symbol) ?: return
                when (criteria.comparison) {
                    ComparisonType.ABOVE -> peRatio >= criteria.threshold
                    ComparisonType.BELOW -> peRatio <= criteria.threshold
                }
            }
            is WatchCriteria.PSRatioCriteria -> {
                val psRatio = YahooFinanceService.getPSRatio(watch.symbol) ?: return
                when (criteria.comparison) {
                    ComparisonType.ABOVE -> psRatio >= criteria.threshold
                    ComparisonType.BELOW -> psRatio <= criteria.threshold
                }
            }
            is WatchCriteria.ATHDropCriteria -> {
                val ath = if (watch.ath > 0) watch.ath else YahooFinanceService.getATH(watch.symbol) ?: return
                val effectiveAth = if (currentPrice > ath) currentPrice else ath
                val dropPercentage = ((effectiveAth - currentPrice) / effectiveAth) * 100
                dropPercentage >= criteria.dropPercentage
            }
            is WatchCriteria.DailyHighDropCriteria -> {
                val dailyHigh = YahooFinanceService.getDailyHigh(watch.symbol) ?: return
                val effectiveDailyHigh = if (currentPrice > dailyHigh) currentPrice else dailyHigh
                val dropPercentage = ((effectiveDailyHigh - currentPrice) / effectiveDailyHigh) * 100
                dropPercentage >= criteria.dropPercentage
            }
        }

        if (watch.notifyOnTrigger && isTriggered) {
            val (title, message) = when (criteria) {
                is WatchCriteria.PriceTargetCriteria -> {
                    val direction = if (criteria.comparison == ComparisonType.ABOVE) "överstigit" else "understigit"
                    "Prisnivå Alert: ${watch.symbol}" to 
                        "${watch.symbol} har $direction ${criteria.threshold}. Nuvarande pris: $currentPrice"
                }
                is WatchCriteria.PERatioCriteria -> {
                    val peRatio = YahooFinanceService.getPERatio(watch.symbol) ?: return
                    val direction = if (criteria.comparison == ComparisonType.ABOVE) "överstigit" else "understigit"
                    "P/E Alert: ${watch.symbol}" to 
                        "${watch.symbol} P/E-tal har $direction ${criteria.threshold}. Nuvarande P/E: $peRatio"
                }
                is WatchCriteria.PSRatioCriteria -> {
                    val psRatio = YahooFinanceService.getPSRatio(watch.symbol) ?: return
                    val direction = if (criteria.comparison == ComparisonType.ABOVE) "överstigit" else "understigit"
                    "P/S Alert: ${watch.symbol}" to 
                        "${watch.symbol} P/S-tal har $direction ${criteria.threshold}. Nuvarande P/S: $psRatio"
                }
                is WatchCriteria.ATHDropCriteria -> {
                    val ath = if (watch.ath > 0) watch.ath else YahooFinanceService.getATH(watch.symbol) ?: return
                    val effectiveAth = if (currentPrice > ath) currentPrice else ath
                    val dropPercentage = ((effectiveAth - currentPrice) / effectiveAth) * 100
                    "ATH Drop Alert: ${watch.symbol}" to 
                        "${watch.symbol} har fallit ${String.format("%.2f", dropPercentage)}% från ATH ($effectiveAth). Nuvarande pris: $currentPrice"
                }
                is WatchCriteria.DailyHighDropCriteria -> {
                    val dailyHigh = YahooFinanceService.getDailyHigh(watch.symbol) ?: return
                    val effectiveDailyHigh = if (currentPrice > dailyHigh) currentPrice else dailyHigh
                    val dropPercentage = ((effectiveDailyHigh - currentPrice) / effectiveDailyHigh) * 100
                    "Dagshögsta Drop Alert: ${watch.symbol}" to 
                        "${watch.symbol} har fallit ${String.format("%.2f", dropPercentage)}% från dagshögsta ($effectiveDailyHigh). Nuvarande pris: $currentPrice"
                }
            }
            showNotification(watch.id + 10000, title, message)
        }
    }

    private fun createNotificationForWatch(watch: StockWatchEntity, currentPrice: Double, ath: Double, title: String) {
        val valStr = if (watch.isPercentage) "${watch.dropValue}%" else "${watch.dropValue}"
        val message = "Price: $currentPrice (ATH: $ath). Dropped > $valStr"
        showNotification(watch.id + 10000, title, message) // Offset ID to avoid collision
    }

    private fun showNotification(notificationId: Int, title: String, message: String) {
        val channelId = "stock_flip_channel"
        val notificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "Stock Updates", NotificationManager.IMPORTANCE_DEFAULT)
            notificationManager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(applicationContext, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info) // Fallback icon
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        notificationManager.notify(notificationId, notification)
    }

    companion object {
        fun startPeriodicUpdate(context: Context) {
             val request = PeriodicWorkRequestBuilder<StockPriceUpdater>(15, TimeUnit.MINUTES).build()
             WorkManager.getInstance(context).enqueueUniquePeriodicWork("StockPriceUpdater", ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}