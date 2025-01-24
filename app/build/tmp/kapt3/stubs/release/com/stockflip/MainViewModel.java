package com.stockflip;

@kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000:\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0010 \n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0010\u0002\n\u0002\b\t\u0018\u0000 \u00192\u00020\u0001:\u0001\u0019B\u0015\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u0012\u0006\u0010\u0004\u001a\u00020\u0005\u00a2\u0006\u0002\u0010\u0006J\u0016\u0010\u0010\u001a\u00020\u00112\u0006\u0010\u0012\u001a\u00020\u000bH\u0086@\u00a2\u0006\u0002\u0010\u0013J\u0016\u0010\u0014\u001a\u00020\u00112\u0006\u0010\u0012\u001a\u00020\u000bH\u0086@\u00a2\u0006\u0002\u0010\u0013J\u000e\u0010\u0015\u001a\u00020\u0011H\u0086@\u00a2\u0006\u0002\u0010\u0016J\u000e\u0010\u0017\u001a\u00020\u0011H\u0086@\u00a2\u0006\u0002\u0010\u0016J\u0016\u0010\u0018\u001a\u00020\u00112\u0006\u0010\u0012\u001a\u00020\u000bH\u0086@\u00a2\u0006\u0002\u0010\u0013R \u0010\u0007\u001a\u0014\u0012\u0010\u0012\u000e\u0012\n\u0012\b\u0012\u0004\u0012\u00020\u000b0\n0\t0\bX\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0002\u001a\u00020\u0003X\u0082\u0004\u00a2\u0006\u0002\n\u0000R#\u0010\f\u001a\u0014\u0012\u0010\u0012\u000e\u0012\n\u0012\b\u0012\u0004\u0012\u00020\u000b0\n0\t0\r\u00a2\u0006\b\n\u0000\u001a\u0004\b\u000e\u0010\u000fR\u000e\u0010\u0004\u001a\u00020\u0005X\u0082\u0004\u00a2\u0006\u0002\n\u0000\u00a8\u0006\u001a"}, d2 = {"Lcom/stockflip/MainViewModel;", "Landroidx/lifecycle/ViewModel;", "stockPairDao", "Lcom/stockflip/StockPairDao;", "yahooFinanceService", "Lcom/stockflip/YahooFinanceService;", "(Lcom/stockflip/StockPairDao;Lcom/stockflip/YahooFinanceService;)V", "_uiState", "Lkotlinx/coroutines/flow/MutableStateFlow;", "Lcom/stockflip/UiState;", "", "Lcom/stockflip/StockPair;", "uiState", "Lkotlinx/coroutines/flow/StateFlow;", "getUiState", "()Lkotlinx/coroutines/flow/StateFlow;", "addStockPair", "", "stockPair", "(Lcom/stockflip/StockPair;Lkotlin/coroutines/Continuation;)Ljava/lang/Object;", "deleteStockPair", "loadStockPairs", "(Lkotlin/coroutines/Continuation;)Ljava/lang/Object;", "refreshStockPairs", "updateStockPair", "Companion", "app_release"})
public final class MainViewModel extends androidx.lifecycle.ViewModel {
    @org.jetbrains.annotations.NotNull()
    private final com.stockflip.StockPairDao stockPairDao = null;
    @org.jetbrains.annotations.NotNull()
    private final com.stockflip.YahooFinanceService yahooFinanceService = null;
    @org.jetbrains.annotations.NotNull()
    private final kotlinx.coroutines.flow.MutableStateFlow<com.stockflip.UiState<java.util.List<com.stockflip.StockPair>>> _uiState = null;
    @org.jetbrains.annotations.NotNull()
    private final kotlinx.coroutines.flow.StateFlow<com.stockflip.UiState<java.util.List<com.stockflip.StockPair>>> uiState = null;
    @org.jetbrains.annotations.NotNull()
    private static final java.lang.String TAG = "MainViewModel";
    @org.jetbrains.annotations.NotNull()
    public static final com.stockflip.MainViewModel.Companion Companion = null;
    
    public MainViewModel(@org.jetbrains.annotations.NotNull()
    com.stockflip.StockPairDao stockPairDao, @org.jetbrains.annotations.NotNull()
    com.stockflip.YahooFinanceService yahooFinanceService) {
        super();
    }
    
    @org.jetbrains.annotations.NotNull()
    public final kotlinx.coroutines.flow.StateFlow<com.stockflip.UiState<java.util.List<com.stockflip.StockPair>>> getUiState() {
        return null;
    }
    
    @org.jetbrains.annotations.Nullable()
    public final java.lang.Object loadStockPairs(@org.jetbrains.annotations.NotNull()
    kotlin.coroutines.Continuation<? super kotlin.Unit> $completion) {
        return null;
    }
    
    @org.jetbrains.annotations.Nullable()
    public final java.lang.Object refreshStockPairs(@org.jetbrains.annotations.NotNull()
    kotlin.coroutines.Continuation<? super kotlin.Unit> $completion) {
        return null;
    }
    
    @org.jetbrains.annotations.Nullable()
    public final java.lang.Object addStockPair(@org.jetbrains.annotations.NotNull()
    com.stockflip.StockPair stockPair, @org.jetbrains.annotations.NotNull()
    kotlin.coroutines.Continuation<? super kotlin.Unit> $completion) {
        return null;
    }
    
    @org.jetbrains.annotations.Nullable()
    public final java.lang.Object deleteStockPair(@org.jetbrains.annotations.NotNull()
    com.stockflip.StockPair stockPair, @org.jetbrains.annotations.NotNull()
    kotlin.coroutines.Continuation<? super kotlin.Unit> $completion) {
        return null;
    }
    
    @org.jetbrains.annotations.Nullable()
    public final java.lang.Object updateStockPair(@org.jetbrains.annotations.NotNull()
    com.stockflip.StockPair stockPair, @org.jetbrains.annotations.NotNull()
    kotlin.coroutines.Continuation<? super kotlin.Unit> $completion) {
        return null;
    }
    
    @kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000\u0012\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0010\u000e\n\u0000\b\u0086\u0003\u0018\u00002\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0002R\u000e\u0010\u0003\u001a\u00020\u0004X\u0082T\u00a2\u0006\u0002\n\u0000\u00a8\u0006\u0005"}, d2 = {"Lcom/stockflip/MainViewModel$Companion;", "", "()V", "TAG", "", "app_release"})
    public static final class Companion {
        
        private Companion() {
            super();
        }
    }
}