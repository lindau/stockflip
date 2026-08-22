package com.stockflip

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "insider_transactions",
    indices = [
        Index(value = ["symbol", "acceptedAtMillis"]),
        Index(value = ["accessionNumber"])
    ]
)
data class InsiderTransactionEntity(
    @PrimaryKey val id: String,
    val symbol: String,
    val cik: String,
    val accessionNumber: String,
    val reportingOwner: String,
    val relationship: String?,
    val transactionDate: String,
    val shares: Double?,
    val pricePerShare: Double?,
    val estimatedValue: Double?,
    val securityTitle: String?,
    val filingDate: String?,
    val acceptedAtMillis: Long?,
    val transactionType: String = "BUY",
    val storedAtMillis: Long = System.currentTimeMillis()
)
