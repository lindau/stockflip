package com.stockflip

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/**
 * Ren logik som inte kräver Android Keystore. HMAC-round-trip testas i den instrumenterade
 * [NotificationNavigationSecurityInstrumentedTest] eftersom Robolectric 4.11.1 saknar
 * AndroidKeyStore-providern (samma begränsning som gör att BackupManagerTest bara testar
 * osignerad import).
 */
@RunWith(RobolectricTestRunner::class)
class NotificationNavigationSecurityTest {

    @Before
    fun setUp() {
        AppSecurityManager.init(RuntimeEnvironment.getApplication())
    }

    @Test
    fun `null and blank tokens are rejected without touching the keystore`() {
        val dest = NotificationDestination.Stock("AAPL", 5)

        assertFalse(NotificationNavigationSecurity.verifyToken(dest, null))
        assertFalse(NotificationNavigationSecurity.verifyToken(dest, ""))
        assertFalse(NotificationNavigationSecurity.verifyToken(dest, "   "))
    }

    @Test
    fun `malformed token is rejected without throwing`() {
        val dest = NotificationDestination.Stock("AAPL", 5)

        assertFalse(NotificationNavigationSecurity.verifyToken(dest, "not-base64!!"))
    }

    @Test
    fun `canonical payload is unambiguous per destination`() {
        assertEquals("v1|pair|3", NotificationNavigationSecurity.canonicalPayload(NotificationDestination.PairWatch(3)))
        assertEquals("v1|stock|AAPL|5", NotificationNavigationSecurity.canonicalPayload(NotificationDestination.Stock("AAPL", 5)))
        assertEquals("v1|stock|AAPL|0", NotificationNavigationSecurity.canonicalPayload(NotificationDestination.Stock("AAPL", null)))
        assertEquals("v1|alerts|7", NotificationNavigationSecurity.canonicalPayload(NotificationDestination.AlertList(7)))

        // Stock utan watch-id får inte kollidera med AlertList(0).
        assertNotEquals(
            NotificationNavigationSecurity.canonicalPayload(NotificationDestination.Stock("AAPL", null)),
            NotificationNavigationSecurity.canonicalPayload(NotificationDestination.AlertList(0))
        )
    }
}
