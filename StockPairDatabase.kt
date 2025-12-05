@Database(entities = [StockPairEntity::class, StockWatchEntity::class], version = 2)
abstract class StockPairDatabase : RoomDatabase() {
    abstract fun stockPairDao(): StockPairDao
    abstract fun stockWatchDao(): StockWatchDao

    companion object {
        @Volatile
        private var INSTANCE: StockPairDatabase? = null

        fun getDatabase(context: Context): StockPairDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    StockPairDatabase::class.java,
                    "stock_pair_database"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
