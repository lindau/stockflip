package com.stockflip;

@kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000D\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0010\t\n\u0002\b\u0002\n\u0002\u0010\b\n\u0002\b\u0002\n\u0002\u0010\u000e\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0010\u000b\n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\b\u00c6\u0002\u0018\u00002\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0002J\u0006\u0010\u0011\u001a\u00020\u0004J\u0006\u0010\u0012\u001a\u00020\u0013J\u001a\u0010\u0014\u001a\u00020\u00132\u0006\u0010\u0015\u001a\u00020\u00072\n\u0010\u0016\u001a\u00060\u0017j\u0002`\u0018R\u000e\u0010\u0003\u001a\u00020\u0004X\u0086T\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0005\u001a\u00020\u0004X\u0086T\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0006\u001a\u00020\u0007X\u0086T\u00a2\u0006\u0002\n\u0000R\u000e\u0010\b\u001a\u00020\u0004X\u0086T\u00a2\u0006\u0002\n\u0000R\u000e\u0010\t\u001a\u00020\nX\u0082T\u00a2\u0006\u0002\n\u0000R\u0016\u0010\u000b\u001a\n \r*\u0004\u0018\u00010\f0\fX\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u0016\u0010\u000e\u001a\n \r*\u0004\u0018\u00010\f0\fX\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u0016\u0010\u000f\u001a\n \r*\u0004\u0018\u00010\u00100\u0010X\u0082\u0004\u00a2\u0006\u0002\n\u0000\u00a8\u0006\u0019"}, d2 = {"Lcom/stockflip/StockMarketScheduler;", "", "()V", "AFTER_HOURS_INTERVAL_MINUTES", "", "MARKET_HOURS_INTERVAL_MINUTES", "MAX_RETRY_ATTEMPTS", "", "RETRY_DELAY_MINUTES", "TAG", "", "marketClose", "Ljava/time/LocalTime;", "kotlin.jvm.PlatformType", "marketOpen", "stockholmZone", "Ljava/time/ZoneId;", "getUpdateInterval", "isMarketOpen", "", "shouldRetry", "attempt", "error", "Ljava/lang/Exception;", "Lkotlin/Exception;", "app_debug"})
public final class StockMarketScheduler {
    @org.jetbrains.annotations.NotNull()
    private static final java.lang.String TAG = "StockMarketScheduler";
    private static final java.time.ZoneId stockholmZone = null;
    private static final java.time.LocalTime marketOpen = null;
    private static final java.time.LocalTime marketClose = null;
    public static final long MARKET_HOURS_INTERVAL_MINUTES = 1L;
    public static final long AFTER_HOURS_INTERVAL_MINUTES = 60L;
    public static final int MAX_RETRY_ATTEMPTS = 3;
    public static final long RETRY_DELAY_MINUTES = 1L;
    @org.jetbrains.annotations.NotNull()
    public static final com.stockflip.StockMarketScheduler INSTANCE = null;
    
    private StockMarketScheduler() {
        super();
    }
    
    public final boolean isMarketOpen() {
        return false;
    }
    
    public final long getUpdateInterval() {
        return 0L;
    }
    
    public final boolean shouldRetry(int attempt, @org.jetbrains.annotations.NotNull()
    java.lang.Exception error) {
        return false;
    }
}