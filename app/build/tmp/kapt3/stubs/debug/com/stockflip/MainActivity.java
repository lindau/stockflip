package com.stockflip;

@kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000<\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0005\n\u0002\u0010\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\t\n\u0002\u0018\u0002\n\u0002\b\u0004\u0018\u0000 \u001f2\u00020\u0001:\u0001\u001fB\u0005\u00a2\u0006\u0002\u0010\u0002J\u0012\u0010\u000f\u001a\u00020\u00102\b\u0010\u0011\u001a\u0004\u0018\u00010\u0012H\u0014J\b\u0010\u0013\u001a\u00020\u0010H\u0014J\b\u0010\u0014\u001a\u00020\u0010H\u0014J\b\u0010\u0015\u001a\u00020\u0010H\u0002J\b\u0010\u0016\u001a\u00020\u0010H\u0002J\b\u0010\u0017\u001a\u00020\u0010H\u0002J\b\u0010\u0018\u001a\u00020\u0010H\u0002J\b\u0010\u0019\u001a\u00020\u0010H\u0002J\u0010\u0010\u001a\u001a\u00020\u00102\u0006\u0010\u001b\u001a\u00020\u001cH\u0002J\u0010\u0010\u001d\u001a\u00020\u00102\u0006\u0010\u001b\u001a\u00020\u001cH\u0002J\b\u0010\u001e\u001a\u00020\u0010H\u0002R\u000e\u0010\u0003\u001a\u00020\u0004X\u0082.\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0005\u001a\u00020\u0006X\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0007\u001a\u00020\bX\u0082.\u00a2\u0006\u0002\n\u0000R\u001b\u0010\t\u001a\u00020\n8BX\u0082\u0084\u0002\u00a2\u0006\f\n\u0004\b\r\u0010\u000e\u001a\u0004\b\u000b\u0010\f\u00a8\u0006 "}, d2 = {"Lcom/stockflip/MainActivity;", "Landroidx/appcompat/app/AppCompatActivity;", "()V", "binding", "Lcom/stockflip/databinding/ActivityMainBinding;", "priceUpdateReceiver", "Lcom/stockflip/PriceUpdateReceiver;", "stockPriceAlarmManager", "Lcom/stockflip/StockPriceAlarmManager;", "viewModel", "Lcom/stockflip/MainViewModel;", "getViewModel", "()Lcom/stockflip/MainViewModel;", "viewModel$delegate", "Lkotlin/Lazy;", "onCreate", "", "savedInstanceState", "Landroid/os/Bundle;", "onPause", "onResume", "setupFab", "setupObservers", "setupRecyclerView", "setupSwipeRefresh", "showAddStockPairDialog", "showDeleteConfirmationDialog", "pair", "Lcom/stockflip/StockPair;", "showEditStockPairDialog", "updateLastUpdateTime", "Companion", "app_debug"})
public final class MainActivity extends androidx.appcompat.app.AppCompatActivity {
    @org.jetbrains.annotations.NotNull()
    private final kotlin.Lazy viewModel$delegate = null;
    private com.stockflip.databinding.ActivityMainBinding binding;
    private com.stockflip.StockPriceAlarmManager stockPriceAlarmManager;
    @org.jetbrains.annotations.NotNull()
    private final com.stockflip.PriceUpdateReceiver priceUpdateReceiver = null;
    @org.jetbrains.annotations.NotNull()
    private static final java.lang.String TAG = "MainActivity";
    @org.jetbrains.annotations.NotNull()
    public static final com.stockflip.MainActivity.Companion Companion = null;
    
    public MainActivity() {
        super();
    }
    
    private final com.stockflip.MainViewModel getViewModel() {
        return null;
    }
    
    @java.lang.Override()
    protected void onCreate(@org.jetbrains.annotations.Nullable()
    android.os.Bundle savedInstanceState) {
    }
    
    @java.lang.Override()
    protected void onResume() {
    }
    
    @java.lang.Override()
    protected void onPause() {
    }
    
    private final void setupObservers() {
    }
    
    private final void setupRecyclerView() {
    }
    
    private final void setupSwipeRefresh() {
    }
    
    private final void updateLastUpdateTime() {
    }
    
    private final void setupFab() {
    }
    
    private final void showAddStockPairDialog() {
    }
    
    private final void showDeleteConfirmationDialog(com.stockflip.StockPair pair) {
    }
    
    private final void showEditStockPairDialog(com.stockflip.StockPair pair) {
    }
    
    @kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000\u0012\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0010\u000e\n\u0000\b\u0086\u0003\u0018\u00002\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0002R\u000e\u0010\u0003\u001a\u00020\u0004X\u0082T\u00a2\u0006\u0002\n\u0000\u00a8\u0006\u0005"}, d2 = {"Lcom/stockflip/MainActivity$Companion;", "", "()V", "TAG", "", "app_debug"})
    public static final class Companion {
        
        private Companion() {
            super();
        }
    }
}