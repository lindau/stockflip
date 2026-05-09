package com.stockflip.backup

import com.stockflip.WatchType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class BackupManagerTest {

    @Test
    fun `imports legacy unsigned backup`() {
        val data = BackupManager.importFromJson(legacyBackupJson())

        assertEquals(1, data.watchItems.size)
        assertEquals(1, data.stockPairs.size)
        val watchItem = data.watchItems.first()
        assertEquals("VOLV-B.ST", watchItem.ticker)
        assertEquals("Volvo B", watchItem.companyName)
        assertTrue(watchItem.isActive)
        assertFalse(watchItem.isTriggered)
        assertEquals(WatchType.PriceDirection.BELOW, (watchItem.watchType as WatchType.PriceTarget).direction)
    }

    @Test
    fun `imports backup signed by another installation`() {
        val data = BackupManager.importFromJson(
            legacyBackupJson(
                extraRootFields = """
                    ,"signatureVersion": 1
                    ,"signature": "old-installation-signature"
                """.trimIndent()
            )
        )

        assertEquals(1, data.watchItems.size)
        assertEquals("VOLV-B.ST", data.watchItems.first().ticker)
    }

    private fun legacyBackupJson(extraRootFields: String = ""): String {
        return """
            {
              "version": 1,
              "exportedAt": "2026-04-30T12:00:00",
              "watchItems": [
                {
                  "watchType": "PRICE_TARGET|100.0|BELOW",
                  "ticker": "VOLV-B.ST",
                  "companyName": "Volvo B",
                  "ticker1": null,
                  "ticker2": null,
                  "companyName1": null,
                  "companyName2": null,
                  "lastTriggeredDate": null,
                  "isTriggered": false
                }
              ],
              "stockPairs": [
                {
                  "ticker1": "INV-B.ST",
                  "ticker2": "LUND-B.ST",
                  "companyName1": "Investor B",
                  "companyName2": "Lundbergföretagen B",
                  "priceDifference": 5.0,
                  "notifyWhenEqual": true
                }
              ]
              $extraRootFields
            }
        """.trimIndent()
    }
}
