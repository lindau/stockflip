package com.stockflip

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * HMAC-round-trip för notis-navigeringstoken. Kräver riktig Android Keystore → instrumenterad.
 */
@RunWith(AndroidJUnit4::class)
class NotificationNavigationSecurityInstrumentedTest {

    @Before
    fun setUp() {
        AppSecurityManager.init(ApplicationProvider.getApplicationContext())
    }

    @Test
    fun tokenRoundTripsForSameStockDestination() {
        val dest = NotificationDestination.Stock("AAPL", 5)
        val token = NotificationNavigationSecurity.issueToken(dest)

        assertTrue(NotificationNavigationSecurity.verifyToken(dest, token))
        // Icke-konsumerande: samma token fungerar igen.
        assertTrue(NotificationNavigationSecurity.verifyToken(dest, token))
    }

    @Test
    fun tokenRejectedForDifferentWatchItemId() {
        val token = NotificationNavigationSecurity.issueToken(NotificationDestination.Stock("AAPL", 5))

        assertFalse(NotificationNavigationSecurity.verifyToken(NotificationDestination.Stock("AAPL", 6), token))
    }

    @Test
    fun tokenRejectedForDifferentTicker() {
        val token = NotificationNavigationSecurity.issueToken(NotificationDestination.Stock("AAPL", 5))

        assertFalse(NotificationNavigationSecurity.verifyToken(NotificationDestination.Stock("MSFT", 5), token))
    }

    @Test
    fun tokenRejectedAcrossDestinationTypes() {
        val token = NotificationNavigationSecurity.issueToken(NotificationDestination.PairWatch(3))

        assertFalse(NotificationNavigationSecurity.verifyToken(NotificationDestination.Stock("AAPL", null), token))
    }

    @Test
    fun stockWithoutWatchIdDoesNotCollideWithAlertList() {
        val stockToken = NotificationNavigationSecurity.issueToken(NotificationDestination.Stock("AAPL", null))

        assertFalse(NotificationNavigationSecurity.verifyToken(NotificationDestination.AlertList(0), stockToken))
    }

    @Test
    fun issueTokenIsDeterministic() {
        val dest = NotificationDestination.AlertList(7)

        assertEquals(
            NotificationNavigationSecurity.issueToken(dest),
            NotificationNavigationSecurity.issueToken(dest)
        )
    }

    @Test
    fun tokenStaysValidAfterSecretReloadedFromStorage() {
        val dest = NotificationDestination.Stock("VOLV-B.ST", 12)
        val token = NotificationNavigationSecurity.issueToken(dest)

        // Simulera process-omstart mot samma persisterade secure store.
        AppSecurityManager.init(ApplicationProvider.getApplicationContext())

        assertTrue(NotificationNavigationSecurity.verifyToken(dest, token))
    }

    @Test
    fun notificationAndBackupSignaturesDifferForSamePayload() {
        val payload = "v1|stock|AAPL|5"

        // Domänseparation: notis-signaturen ska inte valideras som en backup-signatur.
        assertFalse(
            AppSecurityManager.verifyBackupPayload(payload, AppSecurityManager.signNotificationPayload(payload))
        )
    }
}
