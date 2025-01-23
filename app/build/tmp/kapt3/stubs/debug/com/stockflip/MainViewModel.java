package com.stockflip;

@kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000:\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0010 \n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0010\u0002\n\u0002\b\b\u0018\u0000 \u00182\u00020\u0001:\u0001\u0018B\u0015\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u0012\u0006\u0010\u0004\u001a\u00020\u0005\u00a2\u0006\u0002\u0010\u0006J\u000e\u0010\u0010\u001a\u00020\u00112\u0006\u0010\u0012\u001a\u00020\u000bJ\u000e\u0010\u0013\u001a\u00020\u00112\u0006\u0010\u0012\u001a\u00020\u000bJ\b\u0010\u0014\u001a\u00020\u0011H\u0002J\u000e\u0010\u0015\u001a\u00020\u0011H\u0086@\u00a2\u0006\u0002\u0010\u0016J\u000e\u0010\u0017\u001a\u00020\u00112\u0006\u0010\u0012\u001a\u00020\u000bR \u0010\u0007\u001a\u0014\u0012\u0010\u0012\u000e\u0012\n\u0012\b\u0012\u0004\u0012\u00020\u000b0\n0\t0\bX\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0002\u001a\u00020\u0003X\u0082\u0004\u00a2\u0006\u0002\n\u0000R#\u0010\f\u001a\u0014\u0012\u0010\u0012\u000e\u0012\n\u0012\b\u0012\u0004\u0012\u00020\u000b0\n0\t0\r\u00a2\u0006\b\n\u0000\u001a\u0004\b\u000e\u0010\u000fR\u000e\u0010\u0004\u001a\u00020\u0005X\u0082\u0004\u00a2\u0006\u0002\n\u0000\u00a8\u0006\u0019"}, d2 = {"Lcom/stockflip/MainViewModel;", "Landroidx/lifecycle/ViewModel;", "stockPairDao", "Lcom/stockflip/StockPairDao;", "yahooFinanceService", "Lcom/stockflip/YahooFinanceService;", "(Lcom/stockflip/StockPairDao;Lcom/stockflip/YahooFinanceService;)V", "_uiState", "Lkotlinx/coroutines/flow/MutableStateFlow;", "Lcom/stockflip/UiState;", "", "Lcom/stockflip/StockPair;", "uiState", "Lkotlinx/coroutines/flow/StateFlow;", "getUiState", "()Lkotlinx/coroutines/flow/StateFlow;", "addStockPair", "", "stockPair", "deleteStockPair", "loadStockPairs", "refreshStockPairs", "(Lkotlin/coroutines/Continuation;)Ljava/lang/Object;", "updateStockPair", "Companion", "app_debug"})
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
    
    private final void loadStockPairs() {
    }
    
    @org.jetbrains.annotations.Nullable()
    public final java.lang.Object refreshStockPairs(@org.jetbrains.annotations.NotNull()
    kotlin.coroutines.Continuation<? super kotlin.Unit> $completion) {
        return null;
    }
    
    public final void addStockPair(@org.jetbrains.annotations.NotNull()
    com.stockflip.StockPair stockPair) {
    }
    
    public final void deleteStockPair(@org.jetbrains.annotations.NotNull()
    com.stockflip.StockPair stockPair) {
    }
    
    public final void updateStockPair(@org.jetbrains.annotations.NotNull()
    com.stockflip.StockPair stockPair) {
    }
    
    @kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000\u0012\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0010\u000e\n\u0000\b\u0086\u0003\u0018\u00002\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0002R\u000e\u0010\u0003\u001a\u00020\u0004X\u0082T\u00a2\u0006\u0002\n\u0000\u00a8\u0006\u0005"}, d2 = {"Lcom/stockflip/MainViewModel$Companion;", "", "()V", "TAG", "", "app_debug"})
    public static final class Companion {
        
        private Companion() {
            super();
        }
    }
}