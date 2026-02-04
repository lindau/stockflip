package com.stockflip.testutil

import com.stockflip.StockPair
import com.stockflip.StockPairDao
import com.stockflip.WatchItem
import com.stockflip.WatchItemDao
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class InMemoryStockPairDao(
    initialPairs: List<StockPair> = emptyList()
) : StockPairDao {
    private val state: MutableStateFlow<List<StockPair>> = MutableStateFlow(initialPairs)

    override suspend fun getAllStockPairs(): List<StockPair> = state.value

    override suspend fun insertStockPair(pair: StockPair) {
        state.value = state.value + pair
    }

    override suspend fun update(pair: StockPair) {
        state.value = state.value.map { existing: StockPair ->
            if (existing.id == pair.id) pair else existing
        }
    }

    override suspend fun deleteStockPair(pair: StockPair) {
        state.value = state.value.filterNot { it.id == pair.id }
    }
}

class InMemoryWatchItemDao(
    initialItems: List<WatchItem> = emptyList()
) : WatchItemDao {
    private val state: MutableStateFlow<List<WatchItem>> = MutableStateFlow(initialItems)

    override suspend fun getAllWatchItems(): List<WatchItem> = state.value

    override fun getAllWatchItemsFlow(): Flow<List<WatchItem>> = state

    override suspend fun insertWatchItem(item: WatchItem) {
        state.value = state.value + item
    }

    override suspend fun update(item: WatchItem) {
        state.value = state.value.map { existing: WatchItem ->
            if (existing.id == item.id) item else existing
        }
    }

    override suspend fun deleteWatchItem(item: WatchItem) {
        state.value = state.value.filterNot { it.id == item.id }
    }

    override suspend fun getWatchItemById(id: Int): WatchItem? = state.value.firstOrNull { it.id == id }
}

