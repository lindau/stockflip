package com.stockflip.viewmodel;

@kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u00008\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0010\u000e\n\u0000\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0010\u0002\n\u0002\b\u0004\b\u0016\u0018\u00002\u00020\u0001B\r\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u00a2\u0006\u0002\u0010\u0004J\b\u0010\u0010\u001a\u00020\u0011H\u0014J\u0006\u0010\u0012\u001a\u00020\u0011J\u000e\u0010\u0013\u001a\u00020\u00112\u0006\u0010\u0014\u001a\u00020\u0006R\u000e\u0010\u0005\u001a\u00020\u0006X\u0082D\u00a2\u0006\u0002\n\u0000R\u0014\u0010\u0007\u001a\b\u0012\u0004\u0012\u00020\t0\bX\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0002\u001a\u00020\u0003X\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u0010\u0010\n\u001a\u0004\u0018\u00010\u000bX\u0082\u000e\u00a2\u0006\u0002\n\u0000R\u0017\u0010\f\u001a\b\u0012\u0004\u0012\u00020\t0\r\u00a2\u0006\b\n\u0000\u001a\u0004\b\u000e\u0010\u000f\u00a8\u0006\u0015"}, d2 = {"Lcom/stockflip/viewmodel/StockSearchViewModel;", "Landroidx/lifecycle/ViewModel;", "repository", "Lcom/stockflip/repository/StockRepository;", "(Lcom/stockflip/repository/StockRepository;)V", "TAG", "", "_searchState", "Lkotlinx/coroutines/flow/MutableStateFlow;", "Lcom/stockflip/repository/SearchState;", "searchJob", "Lkotlinx/coroutines/Job;", "searchState", "Lkotlinx/coroutines/flow/StateFlow;", "getSearchState", "()Lkotlinx/coroutines/flow/StateFlow;", "onCleared", "", "retry", "search", "query", "app_debug"})
public class StockSearchViewModel extends androidx.lifecycle.ViewModel {
    @org.jetbrains.annotations.NotNull()
    private final com.stockflip.repository.StockRepository repository = null;
    @org.jetbrains.annotations.NotNull()
    private final java.lang.String TAG = "StockSearchViewModel";
    @org.jetbrains.annotations.NotNull()
    private final kotlinx.coroutines.flow.MutableStateFlow<com.stockflip.repository.SearchState> _searchState = null;
    @org.jetbrains.annotations.NotNull()
    private final kotlinx.coroutines.flow.StateFlow<com.stockflip.repository.SearchState> searchState = null;
    @org.jetbrains.annotations.Nullable()
    private kotlinx.coroutines.Job searchJob;
    
    public StockSearchViewModel(@org.jetbrains.annotations.NotNull()
    com.stockflip.repository.StockRepository repository) {
        super();
    }
    
    @org.jetbrains.annotations.NotNull()
    public final kotlinx.coroutines.flow.StateFlow<com.stockflip.repository.SearchState> getSearchState() {
        return null;
    }
    
    public final void search(@org.jetbrains.annotations.NotNull()
    java.lang.String query) {
    }
    
    public final void retry() {
    }
    
    @java.lang.Override()
    protected void onCleared() {
    }
}