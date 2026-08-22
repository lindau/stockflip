package com.stockflip

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SecInsiderTransactionServiceTest {
    private val service = SecInsiderTransactionService()

    @Test
    fun `parseOwnershipXml returns both purchase and sale transactions`() {
        val transactions = service.parseOwnershipXml(
            symbol = "AAPL",
            cik = "0000320193",
            accessionNumber = "0000320193-26-000001",
            filingDate = "2026-05-08",
            acceptedAtMillis = 1_777_777_777_000,
            xml = sampleOwnershipXml
        )

        assertEquals(2, transactions.size)

        val purchase = transactions.first()
        assertEquals("AAPL", purchase.symbol)
        assertEquals("Jane Insider", purchase.reportingOwner)
        assertEquals("Verkställande direktör", purchase.relationship)
        assertEquals("2026-05-07", purchase.transactionDate)
        assertEquals(100.0, purchase.shares ?: 0.0, 0.0)
        assertEquals(150.25, purchase.pricePerShare ?: 0.0, 0.0)
        assertEquals(15_025.0, purchase.estimatedValue ?: 0.0, 0.0)
        assertEquals(InsiderTransactionType.BUY, purchase.transactionType)
        assertTrue(purchase.id.startsWith("0000320193-26-000001:0"))

        val sale = transactions[1]
        assertEquals(50.0, sale.shares ?: 0.0, 0.0)
        assertEquals(160.00, sale.pricePerShare ?: 0.0, 0.0)
        assertEquals(InsiderTransactionType.SELL, sale.transactionType)
    }

    private val sampleOwnershipXml = """
        <?xml version="1.0"?>
        <ownershipDocument>
            <reportingOwner>
                <reportingOwnerId>
                    <rptOwnerName>Jane Insider</rptOwnerName>
                </reportingOwnerId>
                <reportingOwnerRelationship>
                    <isDirector>0</isDirector>
                    <isOfficer>1</isOfficer>
                    <officerTitle>Verkställande direktör</officerTitle>
                    <isTenPercentOwner>0</isTenPercentOwner>
                </reportingOwnerRelationship>
            </reportingOwner>
            <nonDerivativeTable>
                <nonDerivativeTransaction>
                    <securityTitle><value>Common Stock</value></securityTitle>
                    <transactionDate><value>2026-05-07</value></transactionDate>
                    <transactionCoding><transactionCode>P</transactionCode></transactionCoding>
                    <transactionAmounts>
                        <transactionShares><value>100</value></transactionShares>
                        <transactionPricePerShare><value>150.25</value></transactionPricePerShare>
                        <transactionAcquiredDisposedCode><value>A</value></transactionAcquiredDisposedCode>
                    </transactionAmounts>
                </nonDerivativeTransaction>
                <nonDerivativeTransaction>
                    <securityTitle><value>Common Stock</value></securityTitle>
                    <transactionDate><value>2026-05-07</value></transactionDate>
                    <transactionCoding><transactionCode>S</transactionCode></transactionCoding>
                    <transactionAmounts>
                        <transactionShares><value>50</value></transactionShares>
                        <transactionPricePerShare><value>160.00</value></transactionPricePerShare>
                        <transactionAcquiredDisposedCode><value>D</value></transactionAcquiredDisposedCode>
                    </transactionAmounts>
                </nonDerivativeTransaction>
            </nonDerivativeTable>
        </ownershipDocument>
    """.trimIndent()
}
