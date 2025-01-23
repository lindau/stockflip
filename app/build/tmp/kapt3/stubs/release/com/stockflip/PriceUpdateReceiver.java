package com.stockflip;

@kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000:\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0010\u000e\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u0006\n\u0002\b\u0002\n\u0002\u0010\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u000b\n\u0002\b\u0002\u0018\u0000 \u00122\u00020\u0001:\u0001\u0012B\u0005\u00a2\u0006\u0002\u0010\u0002J \u0010\u0003\u001a\u00020\u00042\u0006\u0010\u0005\u001a\u00020\u00062\u0006\u0010\u0007\u001a\u00020\b2\u0006\u0010\t\u001a\u00020\bH\u0002J\u0018\u0010\n\u001a\u00020\u000b2\u0006\u0010\f\u001a\u00020\r2\u0006\u0010\u000e\u001a\u00020\u000fH\u0016J \u0010\u0010\u001a\u00020\u00112\u0006\u0010\u0005\u001a\u00020\u00062\u0006\u0010\u0007\u001a\u00020\b2\u0006\u0010\t\u001a\u00020\bH\u0002\u00a8\u0006\u0013"}, d2 = {"Lcom/stockflip/PriceUpdateReceiver;", "Landroid/content/BroadcastReceiver;", "()V", "buildNotificationMessage", "", "pair", "Lcom/stockflip/StockPair;", "price1", "", "price2", "onReceive", "", "context", "Landroid/content/Context;", "intent", "Landroid/content/Intent;", "shouldNotify", "", "Companion", "app_release"})
public final class PriceUpdateReceiver extends android.content.BroadcastReceiver {
    @org.jetbrains.annotations.NotNull()
    private static final java.lang.String TAG = "PriceUpdateReceiver";
    private static final double PRICE_EQUALITY_THRESHOLD = 0.01;
    @org.jetbrains.annotations.NotNull()
    public static final java.lang.String ACTION_PRICE_UPDATE = "com.stockflip.ACTION_PRICE_UPDATE";
    @org.jetbrains.annotations.NotNull()
    public static final java.lang.String ACTION_PRICES_UPDATED = "com.stockflip.ACTION_PRICES_UPDATED";
    @org.jetbrains.annotations.NotNull()
    public static final com.stockflip.PriceUpdateReceiver.Companion Companion = null;
    
    public PriceUpdateReceiver() {
        super();
    }
    
    @java.lang.Override()
    public void onReceive(@org.jetbrains.annotations.NotNull()
    android.content.Context context, @org.jetbrains.annotations.NotNull()
    android.content.Intent intent) {
    }
    
    private final boolean shouldNotify(com.stockflip.StockPair pair, double price1, double price2) {
        return false;
    }
    
    private final java.lang.String buildNotificationMessage(com.stockflip.StockPair pair, double price1, double price2) {
        return null;
    }
    
    @kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000\"\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0010\u000e\n\u0002\b\u0002\n\u0002\u0010\u0006\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0000\b\u0086\u0003\u0018\u00002\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0002J\u0006\u0010\t\u001a\u00020\nR\u000e\u0010\u0003\u001a\u00020\u0004X\u0086T\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0005\u001a\u00020\u0004X\u0086T\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0006\u001a\u00020\u0007X\u0082T\u00a2\u0006\u0002\n\u0000R\u000e\u0010\b\u001a\u00020\u0004X\u0082T\u00a2\u0006\u0002\n\u0000\u00a8\u0006\u000b"}, d2 = {"Lcom/stockflip/PriceUpdateReceiver$Companion;", "", "()V", "ACTION_PRICES_UPDATED", "", "ACTION_PRICE_UPDATE", "PRICE_EQUALITY_THRESHOLD", "", "TAG", "createIntentFilter", "Landroid/content/IntentFilter;", "app_release"})
    public static final class Companion {
        
        private Companion() {
            super();
        }
        
        @org.jetbrains.annotations.NotNull()
        public final android.content.IntentFilter createIntentFilter() {
            return null;
        }
    }
}