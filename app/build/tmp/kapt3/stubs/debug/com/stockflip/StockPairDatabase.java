package com.stockflip;

@kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000\u0014\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\b\'\u0018\u0000 \u00052\u00020\u0001:\u0001\u0005B\u0005\u00a2\u0006\u0002\u0010\u0002J\b\u0010\u0003\u001a\u00020\u0004H&\u00a8\u0006\u0006"}, d2 = {"Lcom/stockflip/StockPairDatabase;", "Landroidx/room/RoomDatabase;", "()V", "stockPairDao", "Lcom/stockflip/StockPairDao;", "Companion", "app_debug"})
@androidx.room.Database(entities = {com.stockflip.StockPair.class}, version = 5, exportSchema = true)
public abstract class StockPairDatabase extends androidx.room.RoomDatabase {
    @org.jetbrains.annotations.NotNull()
    private static final androidx.room.migration.Migration MIGRATION_4_5 = null;
    @kotlin.jvm.Volatile()
    @org.jetbrains.annotations.Nullable()
    private static volatile com.stockflip.StockPairDatabase INSTANCE;
    @org.jetbrains.annotations.NotNull()
    public static final com.stockflip.StockPairDatabase.Companion Companion = null;
    
    public StockPairDatabase() {
        super();
    }
    
    @org.jetbrains.annotations.NotNull()
    public abstract com.stockflip.StockPairDao stockPairDao();
    
    @kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000 \n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0000\b\u0086\u0003\u0018\u00002\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0002J\u000e\u0010\u0007\u001a\u00020\u00042\u0006\u0010\b\u001a\u00020\tR\u0010\u0010\u0003\u001a\u0004\u0018\u00010\u0004X\u0082\u000e\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0005\u001a\u00020\u0006X\u0082\u0004\u00a2\u0006\u0002\n\u0000\u00a8\u0006\n"}, d2 = {"Lcom/stockflip/StockPairDatabase$Companion;", "", "()V", "INSTANCE", "Lcom/stockflip/StockPairDatabase;", "MIGRATION_4_5", "Landroidx/room/migration/Migration;", "getDatabase", "context", "Landroid/content/Context;", "app_debug"})
    public static final class Companion {
        
        private Companion() {
            super();
        }
        
        @org.jetbrains.annotations.NotNull()
        public final com.stockflip.StockPairDatabase getDatabase(@org.jetbrains.annotations.NotNull()
        android.content.Context context) {
            return null;
        }
    }
}