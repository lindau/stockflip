package com.stockflip.testutil

import com.stockflip.StockNote
import com.stockflip.StockNoteDao
import com.stockflip.StockPair
import com.stockflip.StockPairDao
import com.stockflip.TriggerHistoryDao
import com.stockflip.TriggerHistoryEntity
import com.stockflip.WatchItem
import com.stockflip.WatchItemDao
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

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

    override fun getWatchItemsBySymbolFlow(symbol: String): Flow<List<WatchItem>> =
        state.map { list ->
            list.filter { it.ticker == symbol || it.ticker1 == symbol || it.ticker2 == symbol }
        }

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

    override suspend fun deleteBySymbol(symbol: String) {
        state.value = state.value.filterNot {
            it.ticker == symbol || it.ticker1 == symbol || it.ticker2 == symbol
        }
    }

    override suspend fun getWatchItemById(id: Int): WatchItem? = state.value.firstOrNull { it.id == id }
}

class InMemoryStockNoteDao : StockNoteDao {
    private val state: MutableStateFlow<Map<String, StockNote>> = MutableStateFlow(emptyMap())

    override fun getByTickerFlow(ticker: String): Flow<StockNote?> =
        state.map { it[ticker] }

    override suspend fun upsert(note: StockNote) {
        state.value = state.value + (note.ticker to note)
    }

    override suspend fun deleteByTicker(ticker: String) {
        state.value = state.value - ticker
    }
}

class InMemoryTriggerHistoryDao : TriggerHistoryDao {
    private val entries = mutableListOf<TriggerHistoryEntity>()

    override suspend fun insert(entity: TriggerHistoryEntity) {
        if (entries.none { it.id == entity.id }) entries.add(entity)
    }

    override suspend fun getLatest(id: Int, limit: Int): List<TriggerHistoryEntity> =
        entries.filter { it.watchItemId == id }
            .sortedByDescending { it.triggeredAt }
            .take(limit)

    override suspend fun deleteOlderThan(before: Long) {
        entries.removeAll { it.triggeredAt < before }
    }
}

