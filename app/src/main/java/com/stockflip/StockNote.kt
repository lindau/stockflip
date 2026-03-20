package com.stockflip

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "stock_notes")
data class StockNote(
    @PrimaryKey val ticker: String,
    val note: String
)
