package com.stockflip

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

/**
 * Periodically pulls podcast-derived stock recommendations from the
 * developer's own podcast-analysis service and attaches them to whichever
 * tickers are already being watched. Same shape as InsiderTransactionWorker
 * (a per-ticker list of externally-sourced structured events, refreshed by
 * WorkManager) -- see PodcastAnalysisService's doc comment for why this is
 * a personal-only feature, inert by default in any other build.
 */
class PodcastObservationWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        if (BuildConfig.PODCAST_ANALYSIS_BASE_URL.isBlank()) {
            Log.d(TAG, "PODCAST_ANALYSIS_BASE_URL not set, skipping (this is expected for anyone but the developer)")
            return Result.success()
        }
        if (!PodcastObservationSettings.isEnabled(applicationContext)) {
            Log.d(TAG, "Podcast-analysis sync disabled in settings, skipping")
            return Result.success()
        }

        val database = StockPairDatabase.getDatabase(applicationContext)
        val watchItemDao = database.watchItemDao()
        val observationDao = database.podcastObservationDao()

        val watchedTickers = watchItemDao.getAllWatchItems()
            .flatMap { listOfNotNull(it.ticker, it.ticker1, it.ticker2) }
            .map { it.uppercase() }
            .toSet()
        if (watchedTickers.isEmpty()) {
            Log.d(TAG, "No watched tickers, skipping")
            return Result.success()
        }

        try {
            val byTicker = PodcastAnalysisService().getObservationsByTicker()
            val matchedTickers = watchedTickers.filter { byTicker.containsKey(it) }
            val entities = matchedTickers
                .mapNotNull { ticker -> byTicker[ticker]?.let { ticker to it } }
                .flatMap { (ticker, mentions) ->
                    mentions.mapNotNull { it.toEntity(ticker, companyName = null) }
                }
            if (entities.isNotEmpty()) {
                observationDao.insertAll(entities)
                Log.d(TAG, "Stored ${entities.size} podcast observation(s) for ${watchedTickers.size} watched ticker(s)")
            }
            PodcastObservationSettings.recordSyncResult(
                context = applicationContext,
                watchedTickerCount = watchedTickers.size,
                matchedTickerCount = matchedTickers.size,
                storedCount = entities.size,
                error = null
            )
        } catch (e: Exception) {
            // Background sync against a personal, sometimes-unreachable homelab
            // endpoint -- never fail the whole worker over a network hiccup.
            Log.w(TAG, "Failed to sync podcast observations: ${e.message}")
            PodcastObservationSettings.recordSyncResult(
                context = applicationContext,
                watchedTickerCount = watchedTickers.size,
                matchedTickerCount = 0,
                storedCount = 0,
                error = e.message ?: e.javaClass.simpleName
            )
        }

        return Result.success()
    }

    companion object {
        private const val TAG = "PodcastObservationWorker"
    }
}
