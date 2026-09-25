package com.stockflip

import com.stockflip.testutil.FakeMarketDataService
import com.stockflip.testutil.InMemoryPodcastObservationDao
import com.stockflip.testutil.InMemoryStockNoteDao
import com.stockflip.testutil.InMemoryStockPairDao
import com.stockflip.testutil.InMemoryWatchItemDao
import com.stockflip.testutil.MainDispatcherRule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Misslyckade användaråtgärder ska ge en engångshändelse (actionError) och
 * lämna listtillståndet orört, så att vyerna inte töms eller visar fel tomtillstånd.
 * runBlocking istället för runTest: MainViewModel kör en while(true)-loop (se CLAUDE.md).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class MainViewModelActionErrorTest {
    @get:Rule
    val mainDispatcherRule: MainDispatcherRule = MainDispatcherRule()

    private val existingItem = WatchItem(
        id = 1,
        watchType = WatchType.PriceTarget(targetPrice = 250.0, direction = WatchType.PriceDirection.ABOVE),
        ticker = "VOLV-B.ST",
        companyName = "Volvo B"
    )

    /** Delegerar till InMemoryWatchItemDao men kastar vid skrivningar när failWrites är satt. */
    private class FailingWatchItemDao(
        private val delegate: WatchItemDao
    ) : WatchItemDao by delegate {
        var failWrites: Boolean = false

        override suspend fun insertWatchItem(item: WatchItem) {
            if (failWrites) throw IllegalStateException("disk full")
            delegate.insertWatchItem(item)
        }

        override suspend fun update(item: WatchItem) {
            if (failWrites) throw IllegalStateException("disk full")
            delegate.update(item)
        }

        override suspend fun deleteWatchItem(item: WatchItem) {
            if (failWrites) throw IllegalStateException("disk full")
            delegate.deleteWatchItem(item)
        }

        override suspend fun deleteBySymbol(symbol: String) {
            if (failWrites) throw IllegalStateException("disk full")
            delegate.deleteBySymbol(symbol)
        }
    }

    private fun createViewModel(dao: WatchItemDao) = MainViewModel(
        InMemoryStockPairDao(emptyList()),
        dao,
        FakeMarketDataService(pricesBySymbol = mapOf("VOLV-B.ST" to 300.0)),
        InMemoryStockNoteDao(),
        InMemoryPodcastObservationDao()
    )

    /** Samlar actionError i en Unconfined-coroutine så att prenumerationen finns innan åtgärden körs. */
    private fun CoroutineScope.collectActionErrors(viewModel: MainViewModel, into: MutableList<String>): Job =
        launch(Dispatchers.Unconfined) { viewModel.actionError.collect { into.add(it) } }

    private fun assertListUnchanged(viewModel: MainViewModel) {
        val state = viewModel.watchItemUiState.value
        assertTrue("Listtillståndet ska förbli Success, var $state", state is UiState.Success)
        val ids = (state as UiState.Success).data.map { it.item.id }
        assertEquals(listOf(existingItem.id), ids)
    }

    @Test
    fun `failed delete keeps list state and emits Swedish action error`() = runBlocking {
        val dao = FailingWatchItemDao(InMemoryWatchItemDao(listOf(existingItem)))
        val viewModel = createViewModel(dao)
        viewModel.loadWatchItems(forceShowStaleData = true)
        val errors = mutableListOf<String>()
        val collector = collectActionErrors(viewModel, errors)

        dao.failWrites = true
        viewModel.deleteWatchItem(existingItem)

        assertListUnchanged(viewModel)
        assertEquals(listOf("Kunde inte ta bort bevakningen"), errors)
        collector.cancel()
    }

    @Test
    fun `failed add, toggle, update and symbol delete keep list state`() = runBlocking {
        val dao = FailingWatchItemDao(InMemoryWatchItemDao(listOf(existingItem)))
        val viewModel = createViewModel(dao)
        viewModel.loadWatchItems(forceShowStaleData = true)
        val errors = mutableListOf<String>()
        val collector = collectActionErrors(viewModel, errors)

        dao.failWrites = true
        viewModel.addWatchItem(existingItem.copy(id = 2))
        viewModel.toggleWatchItemActive(existingItem, false)
        viewModel.updateWatchItem(existingItem)
        viewModel.deleteStockBySymbol("VOLV-B.ST")

        assertListUnchanged(viewModel)
        assertEquals(
            listOf(
                "Kunde inte lägga till bevakningen",
                "Kunde inte uppdatera bevakningen",
                "Kunde inte uppdatera bevakningen",
                "Kunde inte ta bort bevakningarna"
            ),
            errors
        )
        collector.cancel()
    }

    @Test
    fun `failed reactivate rethrows without replacing list or emitting action error`() = runBlocking {
        val dao = FailingWatchItemDao(InMemoryWatchItemDao(listOf(existingItem)))
        val viewModel = createViewModel(dao)
        viewModel.loadWatchItems(forceShowStaleData = true)
        val errors = mutableListOf<String>()
        val collector = collectActionErrors(viewModel, errors)

        dao.failWrites = true
        try {
            viewModel.reactivateWatchItem(existingItem)
            fail("reactivateWatchItem ska kasta vidare så att anroparen kan visa felet")
        } catch (expected: IllegalStateException) {
            // Förväntat
        }

        assertListUnchanged(viewModel)
        assertTrue(errors.isEmpty())
        collector.cancel()
    }

    @Test
    fun `successful delete removes item without action error`() = runBlocking {
        val dao = FailingWatchItemDao(InMemoryWatchItemDao(listOf(existingItem)))
        val viewModel = createViewModel(dao)
        viewModel.loadWatchItems(forceShowStaleData = true)
        val errors = mutableListOf<String>()
        val collector = collectActionErrors(viewModel, errors)

        viewModel.deleteWatchItem(existingItem)

        val state = viewModel.watchItemUiState.value
        assertTrue(state is UiState.Success)
        assertFalse((state as UiState.Success).data.any { it.item.id == existingItem.id })
        assertTrue(errors.isEmpty())
        collector.cancel()
    }
}
