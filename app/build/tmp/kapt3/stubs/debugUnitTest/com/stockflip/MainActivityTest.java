package com.stockflip;

@org.junit.runner.RunWith(value = org.robolectric.RobolectricTestRunner.class)
@org.robolectric.annotation.Config(sdk = {33})
@kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000\u0086\u0001\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0010 \n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0007\b\u0007\u0018\u00002\u00020\u0001B\u0005\u00a2\u0006\u0002\u0010\u0002J\b\u0010%\u001a\u00020&H\u0007J\b\u0010\'\u001a\u00020&H\u0007J\f\u0010(\u001a\u00060&j\u0002`)H\u0007J\f\u0010*\u001a\u00060&j\u0002`)H\u0007J\f\u0010+\u001a\u00060&j\u0002`)H\u0007J\f\u0010,\u001a\u00060&j\u0002`)H\u0007J\f\u0010-\u001a\u00060&j\u0002`)H\u0007J\f\u0010.\u001a\u00060&j\u0002`)H\u0007J\f\u0010/\u001a\u00060&j\u0002`)H\u0007R\u000e\u0010\u0003\u001a\u00020\u0004X\u0082.\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0005\u001a\u00020\u0006X\u0082.\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0007\u001a\u00020\bX\u0082.\u00a2\u0006\u0002\n\u0000R\u000e\u0010\t\u001a\u00020\nX\u0082.\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u000b\u001a\u00020\fX\u0082.\u00a2\u0006\u0002\n\u0000R\u000e\u0010\r\u001a\u00020\u000eX\u0082.\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u000f\u001a\u00020\u0010X\u0082.\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0011\u001a\u00020\u0012X\u0082.\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0013\u001a\u00020\u0014X\u0082.\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0015\u001a\u00020\u0016X\u0082.\u00a2\u0006\u0002\n\u0000R\u0014\u0010\u0017\u001a\b\u0012\u0004\u0012\u00020\u00190\u0018X\u0082.\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u001a\u001a\u00020\u001bX\u0082.\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u001c\u001a\u00020\u001dX\u0082\u0004\u00a2\u0006\u0002\n\u0000R \u0010\u001e\u001a\u0014\u0012\u0010\u0012\u000e\u0012\n\u0012\b\u0012\u0004\u0012\u00020\"0!0 0\u001fX\u0082.\u00a2\u0006\u0002\n\u0000R\u000e\u0010#\u001a\u00020$X\u0082.\u00a2\u0006\u0002\n\u0000\u00a8\u00060"}, d2 = {"Lcom/stockflip/MainActivityTest;", "", "()V", "application", "Landroid/app/Application;", "binding", "Lcom/stockflip/databinding/ActivityMainBinding;", "dao", "Lcom/stockflip/StockPairDao;", "database", "Lcom/stockflip/StockPairDatabase;", "layoutInflater", "Landroid/view/LayoutInflater;", "lifecycle", "Landroidx/lifecycle/Lifecycle;", "mainViewModel", "Lcom/stockflip/MainViewModel;", "mockBuilder", "Lcom/google/android/material/dialog/MaterialAlertDialogBuilder;", "repository", "Lcom/stockflip/repository/StockRepository;", "rootView", "Landroidx/constraintlayout/widget/ConstraintLayout;", "scenario", "Landroidx/test/core/app/ActivityScenario;", "Lcom/stockflip/MainActivity;", "stockSearchViewModel", "Lcom/stockflip/viewmodel/StockSearchViewModel;", "testDispatcher", "Lkotlinx/coroutines/test/TestDispatcher;", "uiStateFlow", "Lkotlinx/coroutines/flow/MutableStateFlow;", "Lcom/stockflip/UiState;", "", "Lcom/stockflip/StockPair;", "yahooFinanceService", "Lcom/stockflip/YahooFinanceService;", "setup", "", "tearDown", "test UI state handling", "Lkotlinx/coroutines/test/TestResult;", "test add stock pair dialog validation", "test auto refresh functionality", "test refresh prices", "test refresh prices handles error", "test stock adapter creation", "test stock search setup", "app_debugUnitTest"})
@kotlin.OptIn(markerClass = {kotlinx.coroutines.ExperimentalCoroutinesApi.class})
public final class MainActivityTest {
    private androidx.test.core.app.ActivityScenario<com.stockflip.MainActivity> scenario;
    private com.stockflip.repository.StockRepository repository;
    private com.stockflip.MainViewModel mainViewModel;
    private com.stockflip.viewmodel.StockSearchViewModel stockSearchViewModel;
    @org.jetbrains.annotations.NotNull()
    private final kotlinx.coroutines.test.TestDispatcher testDispatcher = null;
    private kotlinx.coroutines.flow.MutableStateFlow<com.stockflip.UiState<java.util.List<com.stockflip.StockPair>>> uiStateFlow;
    private com.google.android.material.dialog.MaterialAlertDialogBuilder mockBuilder;
    private android.app.Application application;
    private com.stockflip.StockPairDatabase database;
    private com.stockflip.StockPairDao dao;
    private com.stockflip.YahooFinanceService yahooFinanceService;
    private com.stockflip.databinding.ActivityMainBinding binding;
    private androidx.constraintlayout.widget.ConstraintLayout rootView;
    private android.view.LayoutInflater layoutInflater;
    private androidx.lifecycle.Lifecycle lifecycle;
    
    public MainActivityTest() {
        super();
    }
    
    @org.junit.Before()
    public final void setup() {
    }
    
    @org.junit.After()
    public final void tearDown() {
    }
}