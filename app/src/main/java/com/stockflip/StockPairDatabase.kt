package com.stockflip

import android.content.Context
import androidx.room.withTransaction
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.room.TypeConverters
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import java.io.File

@Database(
    entities = [StockPair::class, WatchItem::class, MetricHistoryEntity::class, TriggerHistoryEntity::class, StockNote::class],
    version = 11,
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
        private const val LEGACY_DATABASE_NAME = "stock_pair_database"
        private const val ENCRYPTED_DATABASE_NAME = "stock_pair_database_secure"
        private const val CREATE_METRIC_HISTORY_TABLE_SQL = """
            CREATE TABLE IF NOT EXISTS metric_history (
                id TEXT PRIMARY KEY NOT NULL,
                symbol TEXT NOT NULL,
                metricType TEXT NOT NULL,
                date INTEGER NOT NULL,
                value REAL NOT NULL
            )
        """

        private const val CREATE_METRIC_HISTORY_SYMBOL_METRIC_INDEX_SQL = """
            CREATE INDEX IF NOT EXISTS index_metric_history_symbol_metricType
            ON metric_history(symbol, metricType)
        """

        private const val CREATE_METRIC_HISTORY_DATE_INDEX_SQL = """
            CREATE INDEX IF NOT EXISTS index_metric_history_date
            ON metric_history(date)
        """

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
                createMetricHistorySchema(db)
            }
        }

        private val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `stock_notes` (`ticker` TEXT NOT NULL, `note` TEXT NOT NULL, PRIMARY KEY(`ticker`))"
                )
            }
        }

        private val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                createMetricHistorySchema(db)
            }
        }

        private val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS trigger_history (
                        id TEXT NOT NULL PRIMARY KEY,
                        watchItemId INTEGER NOT NULL,
                        triggeredAt INTEGER NOT NULL
                    )
                """)
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_trigger_history_watchItemId` ON `trigger_history` (`watchItemId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_trigger_history_triggeredAt` ON `trigger_history` (`triggeredAt`)")
            }
        }

        @Volatile
        private var INSTANCE: StockPairDatabase? = null

        fun getDatabase(context: Context): StockPairDatabase {
            val appContext = context.applicationContext
            AppSecurityManager.init(appContext)

            return INSTANCE ?: synchronized(this) {
                migrateLegacyDatabaseIfNeeded(appContext)
                val instance = buildDatabase(
                    context = appContext,
                    databaseName = ENCRYPTED_DATABASE_NAME,
                    encrypted = true
                )
                INSTANCE = instance
                instance
            }
        }

        private fun migrateLegacyDatabaseIfNeeded(context: Context) {
            val legacyDb = context.getDatabasePath(LEGACY_DATABASE_NAME)
            if (!legacyDb.exists()) {
                return
            }

            val encryptedDb = context.getDatabasePath(ENCRYPTED_DATABASE_NAME)
            if (encryptedDb.exists()) {
                context.deleteDatabase(ENCRYPTED_DATABASE_NAME)
                deleteDatabaseSidecars(encryptedDb)
            }

            runBlocking(Dispatchers.IO) {
                val legacy = buildDatabase(context, LEGACY_DATABASE_NAME, encrypted = false)
                val encrypted = buildDatabase(context, ENCRYPTED_DATABASE_NAME, encrypted = true)

                try {
                    migrateData(legacy, encrypted)
                } finally {
                    legacy.close()
                    encrypted.close()
                }
            }

            context.deleteDatabase(LEGACY_DATABASE_NAME)
            deleteDatabaseSidecars(legacyDb)
        }

        private suspend fun migrateData(
            legacy: StockPairDatabase,
            encrypted: StockPairDatabase
        ) {
            val stockPairs = legacy.stockPairDao().getAllStockPairs()
            val watchItems = legacy.watchItemDao().getAllWatchItems()
            val metricHistory = legacy.metricHistoryDao().getAllEntries()
            val triggerHistory = legacy.triggerHistoryDao().getAllEntries()
            val notes = legacy.stockNoteDao().getAllNotes()

            encrypted.withTransaction {
                stockPairs.forEach { encrypted.stockPairDao().insertStockPair(it) }
                watchItems.forEach { encrypted.watchItemDao().insertWatchItem(it) }
                metricHistory.forEach { encrypted.metricHistoryDao().insertMetricHistory(it) }
                triggerHistory.forEach { encrypted.triggerHistoryDao().insert(it) }
                notes.forEach { encrypted.stockNoteDao().upsert(it) }
            }
        }

        private fun buildDatabase(
            context: Context,
            databaseName: String,
            encrypted: Boolean
        ): StockPairDatabase {
            val builder = Room.databaseBuilder(
                context,
                StockPairDatabase::class.java,
                databaseName
            ).addMigrations(
                MIGRATION_4_5,
                MIGRATION_5_6,
                MIGRATION_6_7,
                MIGRATION_7_8,
                MIGRATION_8_9,
                MIGRATION_9_10,
                MIGRATION_10_11
            ).fallbackToDestructiveMigrationOnDowngrade(false)

            if (encrypted) {
                builder.openHelperFactory(DatabaseEncryptionManager.createFactory(context))
            }

            return builder.build()
        }

        private fun createMetricHistorySchema(db: SupportSQLiteDatabase) {
            db.execSQL(CREATE_METRIC_HISTORY_TABLE_SQL)
            db.execSQL(CREATE_METRIC_HISTORY_SYMBOL_METRIC_INDEX_SQL)
            db.execSQL(CREATE_METRIC_HISTORY_DATE_INDEX_SQL)
        }

        private fun deleteDatabaseSidecars(databaseFile: File) {
            File(databaseFile.path + "-wal").delete()
            File(databaseFile.path + "-shm").delete()
            File(databaseFile.path + "-journal").delete()
        }
    }
} 
