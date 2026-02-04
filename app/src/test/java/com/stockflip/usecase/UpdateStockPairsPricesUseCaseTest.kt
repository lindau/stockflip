package com.stockflip.usecase

import com.stockflip.StockPair
import com.stockflip.testutil.FakeMarketDataService
import com.stockflip.testutil.InMemoryStockPairDao
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class UpdateStockPairsPricesUseCaseTest {
    @Test
    fun `executeUpdateStockPairsPrices updates pairs when both prices available`() = runTest {
        val stockPairDao = InMemoryStockPairDao(
            listOf(
                StockPair(
                    id = 1,
                    ticker1 = "VOLV-B.ST",
                    ticker2 = "ASSA-B.ST",
                    companyName1 = "Volvo B",
                    companyName2 = "Assa Abloy B",
                    priceDifference = 10.0,
                    notifyWhenEqual = false
                )
            )
        )
        val marketDataService = FakeMarketDataService(
            pricesBySymbol = mapOf(
                "VOLV-B.ST" to 300.0,
                "ASSA-B.ST" to 280.0
            )
        )
        val useCase = UpdateStockPairsPricesUseCase(stockPairDao, marketDataService)

        val updatedPairs = useCase.executeUpdateStockPairsPrices()

        assertEquals(1, updatedPairs.size)
        assertEquals(300.0, updatedPairs[0].currentPrice1, 0.0001)
        assertEquals(280.0, updatedPairs[0].currentPrice2, 0.0001)
    }
}

