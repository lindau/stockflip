package com.stockflip.viewmodel;

@kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000*\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u0002\n\u0002\u0018\u0002\n\u0002\b\n\u0018\u00002\u00020\u0001:\u0001\u0014B\u0005\u00a2\u0006\u0002\u0010\u0002J\f\u0010\t\u001a\u00060\nj\u0002`\u000bH\u0007J\f\u0010\f\u001a\u00060\nj\u0002`\u000bH\u0007J\f\u0010\r\u001a\u00060\nj\u0002`\u000bH\u0007J\f\u0010\u000e\u001a\u00060\nj\u0002`\u000bH\u0007J\f\u0010\u000f\u001a\u00060\nj\u0002`\u000bH\u0007J\b\u0010\u0010\u001a\u00020\nH\u0007J\f\u0010\u0011\u001a\u00060\nj\u0002`\u000bH\u0007J\b\u0010\u0012\u001a\u00020\nH\u0007J\f\u0010\u0013\u001a\u00060\nj\u0002`\u000bH\u0007R\u000e\u0010\u0003\u001a\u00020\u0004X\u0082.\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0005\u001a\u00020\u0006X\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0007\u001a\u00020\bX\u0082.\u00a2\u0006\u0002\n\u0000\u00a8\u0006\u0015"}, d2 = {"Lcom/stockflip/viewmodel/StockSearchViewModelTest;", "", "()V", "repository", "Lcom/stockflip/repository/StockRepository;", "testDispatcher", "Lkotlinx/coroutines/test/TestDispatcher;", "viewModel", "Lcom/stockflip/viewmodel/StockSearchViewModel;", "cleanup cancels ongoing jobs", "", "Lkotlinx/coroutines/test/TestResult;", "debounce prevents rapid searches", "empty query emits no results", "error in search shows error state", "retry mechanism works on error", "setup", "single character query emits no results", "tearDown", "valid query returns results", "TestStockSearchViewModel", "app_releaseUnitTest"})
@kotlin.OptIn(markerClass = {kotlinx.coroutines.ExperimentalCoroutinesApi.class, kotlin.time.ExperimentalTime.class})
public final class StockSearchViewModelTest {
    private com.stockflip.viewmodel.StockSearchViewModel viewModel;
    private com.stockflip.repository.StockRepository repository;
    @org.jetbrains.annotations.NotNull()
    private final kotlinx.coroutines.test.TestDispatcher testDispatcher = null;
    
    public StockSearchViewModelTest() {
        super();
    }
    
    @org.junit.Before()
    public final void setup() {
    }
    
    @org.junit.After()
    public final void tearDown() {
    }
    
    @kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000\u0018\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0010\u0002\n\u0000\b\u0002\u0018\u00002\u00020\u0001B\r\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u00a2\u0006\u0002\u0010\u0004J\u0006\u0010\u0005\u001a\u00020\u0006\u00a8\u0006\u0007"}, d2 = {"Lcom/stockflip/viewmodel/StockSearchViewModelTest$TestStockSearchViewModel;", "Lcom/stockflip/viewmodel/StockSearchViewModel;", "repository", "Lcom/stockflip/repository/StockRepository;", "(Lcom/stockflip/repository/StockRepository;)V", "cleanup", "", "app_releaseUnitTest"})
    static final class TestStockSearchViewModel extends com.stockflip.viewmodel.StockSearchViewModel {
        
        public TestStockSearchViewModel(@org.jetbrains.annotations.NotNull()
        com.stockflip.repository.StockRepository repository) {
            super(null);
        }
        
        public final void cleanup() {
        }
    }
}