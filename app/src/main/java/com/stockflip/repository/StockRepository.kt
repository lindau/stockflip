package com.stockflip.repository

import android.util.Log
import com.stockflip.StockSearchResult
import com.stockflip.YahooFinanceService
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.catch
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

class StockRepository {
    private val TAG = "StockRepository"
    
    private data class CacheEntry(
        val results: List<StockSearchResult>,
        val timestamp: Long
    )

    private val cache = ConcurrentHashMap<String, CacheEntry>()
    private val cacheTTL = TimeUnit.MINUTES.toMillis(5) // 5 minutes TTL
    
    fun searchStocks(query: String): Flow<SearchState> = flow {
        emit(SearchState.Loading)
        
        try {
            Log.d(TAG, "Searching for query: $query")
            
            // Check cache first
            val cachedEntry = cache[query]
            if (cachedEntry != null && !isCacheExpired(cachedEntry.timestamp)) {
                Log.d(TAG, "Cache hit for query: $query")
                emit(SearchState.Success(cachedEntry.results))
                return@flow
            }
            
            // Prepare query - handle both ticker and name searches
            val searchQuery = when {
                query.contains(".ST", ignoreCase = true) -> query.uppercase()
                query.contains("-", ignoreCase = true) -> "${query.uppercase()}.ST"
                query.all { it.isLetterOrDigit() } -> query.uppercase() // Handle pure ticker searches
                else -> query
            }
            
            Log.d(TAG, "Modified search query: $searchQuery")
            
            // Perform search
            val results = YahooFinanceService.searchStocks(searchQuery)
            Log.d(TAG, "Received ${results.size} results from YahooFinanceService")
            
            // Enhanced sorting logic that handles both ticker and name searches
            val upperQuery = query.uppercase()
            val sortedResults = results.sortedWith(
                compareByDescending<StockSearchResult> { 
                    // First priority: Exact ticker matches
                    it.symbol == upperQuery || 
                    it.symbol == "$upperQuery.ST" || 
                    it.symbol.removeSuffix(".ST") == upperQuery ||
                    it.symbol.replace("-", "") == upperQuery.replace("-", "")
                }
                .thenByDescending { it.isSwedish } // Second priority: Swedish stocks
                .thenByDescending { 
                    // Third priority: Starts with matches (ticker)
                    it.symbol.startsWith(upperQuery) || 
                    it.symbol.removeSuffix(".ST").startsWith(upperQuery) ||
                    it.symbol.replace("-", "").startsWith(upperQuery.replace("-", ""))
                }
                .thenByDescending {
                    // Fourth priority: Name matches
                    it.name.equals(query, ignoreCase = true) ||
                    it.name.startsWith(query, ignoreCase = true) ||
                    it.name.contains(query, ignoreCase = true)
                }
                .thenBy { it.symbol } // Finally sort alphabetically by symbol
            )

            Log.d(TAG, "Sorted results: ${sortedResults.map { "${it.symbol} (${it.name})" }}")

            // Cache results
            cache[query] = CacheEntry(sortedResults, System.currentTimeMillis())
            
            emit(SearchState.Success(sortedResults))
        } catch (e: Exception) {
            Log.e(TAG, "Error searching stocks: ${e.message}", e)
            emit(SearchState.Error("Failed to search stocks: ${e.message}", query))
        }
    }.catch { e ->
        Log.e(TAG, "Error in search flow: ${e.message}", e)
        emit(SearchState.Error("An unexpected error occurred", ""))
    }

    private fun isCacheExpired(timestamp: Long): Boolean {
        return System.currentTimeMillis() - timestamp > cacheTTL
    }

    fun clearCache() {
        cache.clear()
    }

    // Periodically clean old cache entries
    fun cleanExpiredCache() {
        val currentTime = System.currentTimeMillis()
        cache.entries.removeIf { (_, entry) ->
            isCacheExpired(entry.timestamp)
        }
    }
} 