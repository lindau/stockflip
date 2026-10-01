package com.stockflip

import com.stockflip.viewmodel.MoversViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MoversViewModelTest {

    private class FakeService(var result: List<MarketMover>?) : MarketMoversService {
        val requests = mutableListOf<Pair<MoverMarket, MoverList>>()
        override suspend fun getMarketMovers(market: MoverMarket, list: MoverList, count: Int): List<MarketMover>? {
            requests += market to list
            return result
        }
    }

    private val mover = MarketMover("VOLV-B.ST", "Volvo B", 250.0, 1.5, 1000L, "SEK")

    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `load ger Success med hämtad lista`() {
        val service = FakeService(listOf(mover))
        val viewModel = MoversViewModel(service)

        viewModel.load(MoverMarket.SWEDEN, MoverList.GAINERS)

        assertEquals(UiState.Success(listOf(mover)), viewModel.state.value)
        assertEquals(listOf(MoverMarket.SWEDEN to MoverList.GAINERS), service.requests)
    }

    @Test
    fun `null från källan ger Error och retry hämtar samma lista igen`() {
        val service = FakeService(null)
        val viewModel = MoversViewModel(service)

        viewModel.load(MoverMarket.US, MoverList.LOSERS)
        assertTrue(viewModel.state.value is UiState.Error)

        service.result = listOf(mover)
        viewModel.retry()

        assertEquals(UiState.Success(listOf(mover)), viewModel.state.value)
        assertEquals(listOf(MoverMarket.US to MoverList.LOSERS, MoverMarket.US to MoverList.LOSERS), service.requests)
    }
}
