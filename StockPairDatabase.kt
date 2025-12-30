import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [StockPairEntity::class, StockWatchEntity::class], version = 3)
@TypeConverters(WatchCriteriaConverter::class)
abstract class StockPairDatabase : RoomDatabase() {
    abstract fun stockPairDao(): StockPairDao
    abstract fun stockWatchDao(): StockWatchDao

    companion object {
        @Volatile
        private var INSTANCE: StockPairDatabase? = null

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE stock_watches ADD COLUMN watchCriteria TEXT")
            }
        }

        fun getDatabase(context: Context): StockPairDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    StockPairDatabase::class.java,
                    "stock_pair_database"
                )
                .addMigrations(MIGRATION_2_3)
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
