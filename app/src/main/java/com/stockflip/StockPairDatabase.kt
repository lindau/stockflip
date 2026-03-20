package com.stockflip

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.room.TypeConverters
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [StockPair::class, WatchItem::class, MetricHistoryEntity::class, TriggerHistoryEntity::class, StockNote::class],
    version = 10,
    exportSchema = true
)
@TypeConverters(WatchTypeConverter::class)
abstract class StockPairDatabase : RoomDatabase() {
    abstract fun stockPairDao(): StockPairDao
    abstract fun watchItemDao(): WatchItemDao
    abstract fun metricHistoryDao(): MetricHistoryDao
    abstract fun triggerHistoryDao(): TriggerHistoryDao
    abstract fun stockNoteDao(): StockNoteDao

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

        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Lägg till spam-skyddsfält i watch_items tabellen
                db.execSQL("""
                    ALTER TABLE watch_items 
                    ADD COLUMN lastTriggeredDate TEXT
                """)
                db.execSQL("""
                    ALTER TABLE watch_items 
                    ADD COLUMN isTriggered INTEGER NOT NULL DEFAULT 0
                """)
                db.execSQL("""
                    ALTER TABLE watch_items 
                    ADD COLUMN isActive INTEGER NOT NULL DEFAULT 1
                """)
            }
        }

        private val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                try {
                    // Kontrollera om tabellen redan finns (för säkerhets skull)
                    val cursor = db.query("SELECT name FROM sqlite_master WHERE type='table' AND name='metric_history'")
                    val tableExists = cursor.moveToFirst()
                    cursor.close()
                    
                    if (!tableExists) {
                        // Skapa metric_history tabell för Fas 2 - historisk nyckeltal-data
                        db.execSQL("""
                            CREATE TABLE metric_history (
                                id TEXT PRIMARY KEY NOT NULL,
                                symbol TEXT NOT NULL,
                                metricType TEXT NOT NULL,
                                date INTEGER NOT NULL,
                                value REAL NOT NULL
                            )
                        """)
                    }
                    
                    // Skapa index för snabbare queries (IF NOT EXISTS hanteras av SQLite)
                    try {
                        db.execSQL("""
                            CREATE INDEX IF NOT EXISTS index_metric_history_symbol_metricType 
                            ON metric_history(symbol, metricType)
                        """)
                    } catch (e: Exception) {
                        // Index kan redan finnas, ignorera fel
                    }
                    
                    try {
                        db.execSQL("""
                            CREATE INDEX IF NOT EXISTS index_metric_history_date 
                            ON metric_history(date)
                        """)
                    } catch (e: Exception) {
                        // Index kan redan finnas, ignorera fel
                    }
                } catch (e: Exception) {
                    // Om något går fel, försök skapa tabellen ändå
                    android.util.Log.e("StockPairDatabase", "Error in migration 7->8: ${e.message}", e)
                    try {
                        db.execSQL("""
                            CREATE TABLE IF NOT EXISTS metric_history (
                                id TEXT PRIMARY KEY NOT NULL,
                                symbol TEXT NOT NULL,
                                metricType TEXT NOT NULL,
                                date INTEGER NOT NULL,
                                value REAL NOT NULL
                            )
                        """)
                    } catch (e2: Exception) {
                        android.util.Log.e("StockPairDatabase", "Failed to create metric_history table: ${e2.message}", e2)
                    }
                }
            }
        }

        private val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "CREATE TABLE IF NOT EXISTS `stock_notes` (`ticker` TEXT NOT NULL, `note` TEXT NOT NULL, PRIMARY KEY(`ticker`))"
                )
            }
        }

        private val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS trigger_history (
                        id TEXT NOT NULL PRIMARY KEY,
                        watchItemId INTEGER NOT NULL,
                        triggeredAt INTEGER NOT NULL
                    )
                """)
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_trigger_history_watchItemId` ON `trigger_history` (`watchItemId`)")
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_trigger_history_triggeredAt` ON `trigger_history` (`triggeredAt`)")
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
                .addMigrations(MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10)
                .fallbackToDestructiveMigrationOnDowngrade()
                .addCallback(object : RoomDatabase.Callback() {
                    override fun onOpen(db: SupportSQLiteDatabase) {
                        super.onOpen(db)
                        // Säkerställ att metric_history-tabellen finns även om migrationen misslyckades
                        ensureMetricHistoryTableExists(db)
                    }
                })
                .build()
                INSTANCE = instance
                instance
            }
        }
        
        /**
         * Säkerställer att metric_history-tabellen finns.
         * Anropas vid databas-öppning för att hantera edge cases där migrationen misslyckades.
         */
        private fun ensureMetricHistoryTableExists(db: SupportSQLiteDatabase) {
            try {
                val cursor = db.query("SELECT name FROM sqlite_master WHERE type='table' AND name='metric_history'")
                val tableExists = cursor.moveToFirst()
                cursor.close()
                
                if (!tableExists) {
                    android.util.Log.w("StockPairDatabase", "metric_history table missing, creating it now")
                    db.execSQL("""
                        CREATE TABLE IF NOT EXISTS metric_history (
                            id TEXT PRIMARY KEY NOT NULL,
                            symbol TEXT NOT NULL,
                            metricType TEXT NOT NULL,
                            date INTEGER NOT NULL,
                            value REAL NOT NULL
                        )
                    """)
                    db.execSQL("""
                        CREATE INDEX IF NOT EXISTS index_metric_history_symbol_metricType 
                        ON metric_history(symbol, metricType)
                    """)
                    db.execSQL("""
                        CREATE INDEX IF NOT EXISTS index_metric_history_date 
                        ON metric_history(date)
                    """)
                    android.util.Log.d("StockPairDatabase", "metric_history table created successfully")
                }
            } catch (e: Exception) {
                android.util.Log.e("StockPairDatabase", "Error ensuring metric_history table exists: ${e.message}", e)
            }
        }
    }
} 