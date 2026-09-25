package com.stockflip

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/**
 * Minnescache med livslängd per post och "single-flight": samtidiga anrop för samma nyckel
 * delar på ETT pågående nätverksanrop i stället för att vart och ett göra sitt eget.
 *
 * Bara lyckade (icke-null) värden sparas, så ett misslyckat anrop försöks igen nästa gång.
 * [clock] kan bytas ut i tester.
 */
class SingleFlightCache<K : Any, V : Any>(
    private val clock: () -> Long = System::currentTimeMillis
) {
    private data class Entry<V>(val value: V, val expiresAt: Long)

    private val lock = Any()
    private val entries = HashMap<K, Entry<V>>()
    private val inFlight = HashMap<K, CompletableDeferred<V?>>()

    suspend fun getOrLoad(key: K, ttlMs: Long, load: suspend () -> V?): V? {
        val now = clock()
        var pending: CompletableDeferred<V?>? = null
        val owned: CompletableDeferred<V?>? = synchronized(lock) {
            entries[key]?.let { entry ->
                if (entry.expiresAt > now) return entry.value
                entries.remove(key)
            }
            val existing = inFlight[key]
            if (existing != null) {
                pending = existing
                null
            } else {
                CompletableDeferred<V?>().also { inFlight[key] = it }
            }
        }

        if (owned == null) {
            return try {
                pending!!.await()
            } catch (e: CancellationException) {
                // Den som hämtade avbröts (t.ex. lämnade skärmen) — hämta själv om vi fortfarande är aktiva.
                currentCoroutineContext().ensureActive()
                getOrLoad(key, ttlMs, load)
            }
        }

        try {
            val value = load()
            synchronized(lock) {
                if (value != null) entries[key] = Entry(value, clock() + ttlMs)
                inFlight.remove(key)
            }
            owned.complete(value)
            return value
        } catch (e: Throwable) {
            synchronized(lock) { inFlight.remove(key) }
            owned.completeExceptionally(e)
            throw e
        }
    }
}
