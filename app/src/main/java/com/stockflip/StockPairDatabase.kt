package com.stockflip

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.room.TypeConverters
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [StockPair::class, WatchItem::class],
    version = 6,
    exportSchema = true
)
@TypeConverters(WatchTypeConverter::class)
abstract class StockPairDatabase : RoomDatabase() {
    abstract fun stockPairDao(): StockPairDao
    abstract fun watchItemDao(): WatchItemDao

    companion object {
        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Create new table with ticker and company name fields
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS stock_pairs_new (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        ticker1 TEXT NOT NULL,
                        ticker2 TEXT NOT NULL,
                        companyName1 TEXT NOT NULL,
                        companyName2 TEXT NOT NULL,
                        priceDifference REAL NOT NULL DEFAULT 0.0,
                        notifyWhenEqual INTEGER NOT NULL DEFAULT 0
                    )
                """)

                // Copy data from old table to new table, using stockName fields as both ticker and company name temporarily
                db.execSQL("""
                    INSERT INTO stock_pairs_new (id, ticker1, ticker2, companyName1, companyName2, priceDifference, notifyWhenEqual)
                    SELECT id, stockName1, stockName2, stockName1, stockName2, priceDifference, notifyWhenEqual
                    FROM stock_pairs
                """)

                // Drop old table
                db.execSQL("DROP TABLE stock_pairs")

                // Rename new table to original name
                db.execSQL("ALTER TABLE stock_pairs_new RENAME TO stock_pairs")
            }
        }

        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Create new watch_items table
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS watch_items (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        watchType TEXT NOT NULL,
                        ticker1 TEXT,
                        ticker2 TEXT,
                        companyName1 TEXT,
                        companyName2 TEXT,
                        ticker TEXT,
                        companyName TEXT
                    )
                """)

                // Migrate existing stock_pairs to watch_items
                db.execSQL("""
                    INSERT INTO watch_items (id, watchType, ticker1, ticker2, companyName1, companyName2, ticker, companyName)
                    SELECT 
                        id,
                        'PRICE_PAIR|' || priceDifference || '|' || notifyWhenEqual,
                        ticker1,
                        ticker2,
                        companyName1,
                        companyName2,
                        NULL,
                        NULL
                    FROM stock_pairs
                """)
            }
        }

        @Volatile
        private var INSTANCE: StockPairDatabase? = null

        fun getDatabase(context: Context): StockPairDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    StockPairDatabase::class.java,
                    "stock_pair_database"
                )
                .addMigrations(MIGRATION_4_5, MIGRATION_5_6)
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
} 