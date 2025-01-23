// StockPairDatabase.kt
@Database(entities = [StockPairEntity::class], version = 1)
abstract class StockPairDatabase : RoomDatabase() {
    abstract fun stockPairDao(): StockPairDao

    companion object {
        @Volatile
        private var INSTANCE: StockPairDatabase? = null

        fun getDatabase(context: Context): StockPairDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    StockPairDatabase::class.java,
                    "stock_pair_database"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
