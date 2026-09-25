package com.stockflip

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Test

class SingleFlightCacheTest {

    @Test
    fun `concurrent callers share one load`() = runTest {
        val cache = SingleFlightCache<String, Int>()
        val gate = CompletableDeferred<Unit>()
        var loads = 0

        val results = List(5) {
            async { cache.getOrLoad("A", ttlMs = 60_000) { loads++; gate.await(); 42 } }
        }
        runCurrent()
        gate.complete(Unit)

        assertEquals(List(5) { 42 }, results.awaitAll())
        assertEquals(1, loads)
    }

    @Test
    fun `value is reused within ttl and reloaded after`() = runTest {
        var now = 0L
        val cache = SingleFlightCache<String, Int>(clock = { now })
        var loads = 0

        assertEquals(1, cache.getOrLoad("A", ttlMs = 1_000) { ++loads })
        now = 999
        assertEquals(1, cache.getOrLoad("A", ttlMs = 1_000) { ++loads })
        now = 1_000
        assertEquals(2, cache.getOrLoad("A", ttlMs = 1_000) { ++loads })
    }

    @Test
    fun `different keys are cached separately`() = runTest {
        val cache = SingleFlightCache<Pair<String, ChartPeriod>, String>()
        assertEquals("dag", cache.getOrLoad("A" to ChartPeriod.DAY, 60_000) { "dag" })
        assertEquals("år", cache.getOrLoad("A" to ChartPeriod.YEAR, 60_000) { "år" })
        assertEquals("dag", cache.getOrLoad("A" to ChartPeriod.DAY, 60_000) { "ny" })
    }

    @Test
    fun `null result is not cached`() = runTest {
        val cache = SingleFlightCache<String, Int>()
        assertNull(cache.getOrLoad("A", 60_000) { null })
        assertEquals(7, cache.getOrLoad("A", 60_000) { 7 })
    }

    @Test
    fun `failure is propagated and not cached`() = runTest {
        val cache = SingleFlightCache<String, Int>()
        try {
            cache.getOrLoad("A", 60_000) { throw IllegalStateException("nät") }
            fail("Förväntade undantag")
        } catch (expected: IllegalStateException) {
            // Förväntat
        }
        assertEquals(3, cache.getOrLoad("A", 60_000) { 3 })
    }

    @Test
    fun `waiter loads itself when the owner is cancelled`() = runTest {
        val cache = SingleFlightCache<String, Int>()
        val ownerGate = CompletableDeferred<Unit>()

        val owner = launch { cache.getOrLoad("A", 60_000) { ownerGate.await(); 1 } }
        runCurrent()
        val waiter = async { cache.getOrLoad("A", 60_000) { 2 } }
        runCurrent()

        owner.cancel()
        runCurrent()

        assertEquals(2, waiter.await())
    }
}
