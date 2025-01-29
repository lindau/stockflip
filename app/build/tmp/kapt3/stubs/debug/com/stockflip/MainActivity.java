package com.stockflip;

/**
 * Main activity for the StockFlip application.
 * Handles the UI for displaying, adding, editing, and deleting stock pairs.
 * Manages real-time stock price updates and user notifications.
 */
@kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000t\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\u0010\u000e\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\b\n\u0002\u0018\u0002\n\u0002\b\u0004\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0010\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0002\u0010 \n\u0002\b\u0006\n\u0002\u0018\u0002\n\u0002\b\f\n\u0002\u0018\u0002\n\u0002\b\u0014\u0018\u0000 O2\u00020\u0001:\u0002OPB\u0005\u00a2\u0006\u0002\u0010\u0002J\u0013\u0010\u001e\u001a\b\u0012\u0004\u0012\u00020\u000e0\u001fH\u0000\u00a2\u0006\u0002\b J\u0010\u0010!\u001a\u00020\"2\u0006\u0010#\u001a\u00020$H\u0002J\u0010\u0010%\u001a\u00020\"2\u0006\u0010#\u001a\u00020$H\u0002J!\u0010&\u001a\u00020\"2\u0012\u0010\'\u001a\u000e\u0012\n\u0012\b\u0012\u0004\u0012\u00020$0)0(H\u0000\u00a2\u0006\u0002\b*J\b\u0010+\u001a\u00020\"H\u0002J\b\u0010,\u001a\u00020\"H\u0002J\b\u0010-\u001a\u00020\"H\u0002J\u0012\u0010.\u001a\u00020\"2\b\u0010/\u001a\u0004\u0018\u000100H\u0014J\b\u00101\u001a\u00020\"H\u0014J\b\u00102\u001a\u00020\"H\u0014J\b\u00103\u001a\u00020\"H\u0014J\u0006\u00104\u001a\u00020\"J\b\u00105\u001a\u00020\"H\u0002J\b\u00106\u001a\u00020\"H\u0002J\b\u00107\u001a\u00020\"H\u0002J\b\u00108\u001a\u00020\"H\u0002J\b\u00109\u001a\u00020\"H\u0002J\b\u0010:\u001a\u00020\"H\u0002J+\u0010;\u001a\u00020\"2\u0006\u0010<\u001a\u00020=2\f\u0010>\u001a\b\u0012\u0004\u0012\u00020\u000e0\u001f2\u0006\u0010\u0019\u001a\u00020\u0011H\u0000\u00a2\u0006\u0002\b?J\b\u0010@\u001a\u00020\"H\u0002J\r\u0010A\u001a\u00020\"H\u0000\u00a2\u0006\u0002\bBJ\u0010\u0010C\u001a\u00020\"2\u0006\u0010#\u001a\u00020$H\u0002J\u0010\u0010D\u001a\u00020\"2\u0006\u0010#\u001a\u00020$H\u0002J\u0010\u0010E\u001a\u00020\"2\u0006\u0010F\u001a\u00020\u0007H\u0002J\b\u0010G\u001a\u00020\"H\u0002J\u0016\u0010H\u001a\u00020\"2\f\u0010I\u001a\b\u0012\u0004\u0012\u00020$0)H\u0002J\r\u0010J\u001a\u00020\"H\u0000\u00a2\u0006\u0002\bKJ\b\u0010L\u001a\u00020\"H\u0002J\b\u0010M\u001a\u00020\"H\u0002J\b\u0010N\u001a\u00020\"H\u0002R\u000e\u0010\u0003\u001a\u00020\u0004X\u0082.\u00a2\u0006\u0002\n\u0000R\u001c\u0010\u0005\u001a\u0010\u0012\f\u0012\n \b*\u0004\u0018\u00010\u00070\u00070\u0006X\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u000e\u0010\t\u001a\u00020\nX\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u0010\u0010\u000b\u001a\u0004\u0018\u00010\fX\u0082\u000e\u00a2\u0006\u0002\n\u0000R\u0010\u0010\r\u001a\u0004\u0018\u00010\u000eX\u0082\u000e\u00a2\u0006\u0002\n\u0000R\u0010\u0010\u000f\u001a\u0004\u0018\u00010\u000eX\u0082\u000e\u00a2\u0006\u0002\n\u0000R\u001b\u0010\u0010\u001a\u00020\u00118BX\u0082\u0084\u0002\u00a2\u0006\f\n\u0004\b\u0014\u0010\u0015\u001a\u0004\b\u0012\u0010\u0013R\u001b\u0010\u0016\u001a\u00020\u00118BX\u0082\u0084\u0002\u00a2\u0006\f\n\u0004\b\u0018\u0010\u0015\u001a\u0004\b\u0017\u0010\u0013R\u001b\u0010\u0019\u001a\u00020\u001a8BX\u0082\u0084\u0002\u00a2\u0006\f\n\u0004\b\u001d\u0010\u0015\u001a\u0004\b\u001b\u0010\u001c\u00a8\u0006Q"}, d2 = {"Lcom/stockflip/MainActivity;", "Landroidx/appcompat/app/AppCompatActivity;", "()V", "binding", "Lcom/stockflip/databinding/ActivityMainBinding;", "notificationPermissionLauncher", "Landroidx/activity/result/ActivityResultLauncher;", "", "kotlin.jvm.PlatformType", "priceUpdateReceiver", "Lcom/stockflip/PriceUpdateReceiver;", "refreshJob", "Lkotlinx/coroutines/Job;", "selectedStock1", "Lcom/stockflip/StockSearchResult;", "selectedStock2", "stockSearchViewModel1", "Lcom/stockflip/viewmodel/StockSearchViewModel;", "getStockSearchViewModel1", "()Lcom/stockflip/viewmodel/StockSearchViewModel;", "stockSearchViewModel1$delegate", "Lkotlin/Lazy;", "stockSearchViewModel2", "getStockSearchViewModel2", "stockSearchViewModel2$delegate", "viewModel", "Lcom/stockflip/MainViewModel;", "getViewModel", "()Lcom/stockflip/MainViewModel;", "viewModel$delegate", "createStockAdapter", "Landroid/widget/ArrayAdapter;", "createStockAdapter$app_debug", "handleDeleteClick", "", "pair", "Lcom/stockflip/StockPair;", "handleEditClick", "handleUiState", "state", "Lcom/stockflip/UiState;", "", "handleUiState$app_debug", "initializeUI", "initializeUpdates", "loadInitialData", "onCreate", "savedInstanceState", "Landroid/os/Bundle;", "onDestroy", "onPause", "onResume", "refreshPrices", "registerPriceUpdateReceiver", "requestNotificationPermission", "requestPermissions", "setupAddButton", "setupObservers", "setupRecyclerView", "setupStockSearch", "input", "Lcom/google/android/material/textfield/MaterialAutoCompleteTextView;", "adapter", "setupStockSearch$app_debug", "setupSwipeRefresh", "showAddStockPairDialog", "showAddStockPairDialog$app_debug", "showDeleteConfirmationDialog", "showEditStockPairDialog", "showError", "message", "showLoading", "showSuccess", "data", "startAutoRefresh", "startAutoRefresh$app_debug", "stopAutoRefresh", "unregisterPriceUpdateReceiver", "updateLastUpdateTime", "Companion", "Quadruple", "app_debug"})
public final class MainActivity extends androidx.appcompat.app.AppCompatActivity {
    @org.jetbrains.annotations.NotNull()
    private final kotlin.Lazy viewModel$delegate = null;
    private com.stockflip.databinding.ActivityMainBinding binding;
    @org.jetbrains.annotations.NotNull()
    private final com.stockflip.PriceUpdateReceiver priceUpdateReceiver = null;
    @org.jetbrains.annotations.Nullable()
    private kotlinx.coroutines.Job refreshJob;
    @org.jetbrains.annotations.Nullable()
    private com.stockflip.StockSearchResult selectedStock1;
    @org.jetbrains.annotations.Nullable()
    private com.stockflip.StockSearchResult selectedStock2;
    @org.jetbrains.annotations.NotNull()
    private final androidx.activity.result.ActivityResultLauncher<java.lang.String> notificationPermissionLauncher = null;
    @org.jetbrains.annotations.NotNull()
    private final kotlin.Lazy stockSearchViewModel1$delegate = null;
    @org.jetbrains.annotations.NotNull()
    private final kotlin.Lazy stockSearchViewModel2$delegate = null;
    
    /**
     * Tag for logging purposes
     */
    @org.jetbrains.annotations.NotNull()
    private static final java.lang.String TAG = "MainActivity";
    
    /**
     * Interval for automatic price refresh in milliseconds
     */
    private static final long AUTO_REFRESH_INTERVAL = 60000L;
    
    /**
     * Format pattern for time display
     */
    @org.jetbrains.annotations.NotNull()
    private static final java.lang.String TIME_FORMAT = "HH:mm:ss";
    @org.jetbrains.annotations.NotNull()
    public static final com.stockflip.MainActivity.Companion Companion = null;
    
    public MainActivity() {
        super();
    }
    
    private final com.stockflip.MainViewModel getViewModel() {
        return null;
    }
    
    private final com.stockflip.viewmodel.StockSearchViewModel getStockSearchViewModel1() {
        return null;
    }
    
    private final com.stockflip.viewmodel.StockSearchViewModel getStockSearchViewModel2() {
        return null;
    }
    
    /**
     * Initializes the activity's UI components and starts data loading.
     * Sets up the view binding, initializes UI elements, and requests necessary permissions.
     */
    @java.lang.Override()
    protected void onCreate(@org.jetbrains.annotations.Nullable()
    android.os.Bundle savedInstanceState) {
    }
    
    @java.lang.Override()
    protected void onDestroy() {
    }
    
    @java.lang.Override()
    protected void onResume() {
    }
    
    @java.lang.Override()
    protected void onPause() {
    }
    
    private final void initializeUI() {
    }
    
    private final void initializeUpdates() {
    }
    
    private final void requestPermissions() {
    }
    
    private final void loadInitialData() {
    }
    
    private final void registerPriceUpdateReceiver() {
    }
    
    private final void unregisterPriceUpdateReceiver() {
    }
    
    /**
     * Starts automatic refresh of stock prices.
     * Cancels any existing refresh job before starting a new one.
     */
    public final void startAutoRefresh$app_debug() {
    }
    
    /**
     * Stops the automatic refresh of stock prices.
     * Cancels the refresh job if it exists.
     */
    private final void stopAutoRefresh() {
    }
    
    /**
     * Updates the last update time display in the UI.
     * Uses the TIME_FORMAT pattern defined in companion object.
     */
    private final void updateLastUpdateTime() {
    }
    
    /**
     * Sets up observers for UI state changes.
     * Handles loading, success, and error states.
     */
    private final void setupObservers() {
    }
    
    /**
     * Handles different UI states and updates the view accordingly.
     *
     * @param state The current UI state to handle
     */
    public final void handleUiState$app_debug(@org.jetbrains.annotations.NotNull()
    com.stockflip.UiState<? extends java.util.List<com.stockflip.StockPair>> state) {
    }
    
    private final void showLoading() {
    }
    
    private final void showSuccess(java.util.List<com.stockflip.StockPair> data) {
    }
    
    private final void showError(java.lang.String message) {
    }
    
    private final void setupRecyclerView() {
    }
    
    private final void handleDeleteClick(com.stockflip.StockPair pair) {
    }
    
    private final void handleEditClick(com.stockflip.StockPair pair) {
    }
    
    private final void setupAddButton() {
    }
    
    /**
     * Shows a dialog for adding a new stock pair.
     * Handles user input validation and API calls for stock information.
     */
    public final void showAddStockPairDialog$app_debug() {
    }
    
    /**
     * Creates an adapter for displaying stock search results.
     * The adapter handles both the input field display and dropdown items.
     *
     * @return ArrayAdapter<StockSearchResult> configured for displaying stock search results
     */
    @org.jetbrains.annotations.NotNull()
    public final android.widget.ArrayAdapter<com.stockflip.StockSearchResult> createStockAdapter$app_debug() {
        return null;
    }
    
    /**
     * Sets up the stock search functionality for an input field.
     * Configures the input field with debounced search, dropdown display,
     * and result handling through the ViewModel.
     *
     * @param input The AutoCompleteTextView to set up search for
     * @param adapter The adapter to display search results
     * @param viewModel The ViewModel handling the search logic
     */
    public final void setupStockSearch$app_debug(@org.jetbrains.annotations.NotNull()
    com.google.android.material.textfield.MaterialAutoCompleteTextView input, @org.jetbrains.annotations.NotNull()
    android.widget.ArrayAdapter<com.stockflip.StockSearchResult> adapter, @org.jetbrains.annotations.NotNull()
    com.stockflip.viewmodel.StockSearchViewModel viewModel) {
    }
    
    /**
     * Shows a dialog for editing an existing stock pair.
     * Handles validation and updates through the ViewModel.
     *
     * @param pair The StockPair to edit
     */
    private final void showEditStockPairDialog(com.stockflip.StockPair pair) {
    }
    
    private final void setupSwipeRefresh() {
    }
    
    public final void refreshPrices() {
    }
    
    private final void requestNotificationPermission() {
    }
    
    /**
     * Shows a confirmation dialog for deleting a stock pair.
     * Handles the deletion through the ViewModel if confirmed.
     *
     * @param pair The StockPair to delete
     */
    private final void showDeleteConfirmationDialog(com.stockflip.StockPair pair) {
    }
    
    @kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000\u001a\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0010\t\n\u0000\n\u0002\u0010\u000e\n\u0002\b\u0002\b\u0086\u0003\u0018\u00002\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0002R\u000e\u0010\u0003\u001a\u00020\u0004X\u0082T\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0005\u001a\u00020\u0006X\u0082T\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0007\u001a\u00020\u0006X\u0082T\u00a2\u0006\u0002\n\u0000\u00a8\u0006\b"}, d2 = {"Lcom/stockflip/MainActivity$Companion;", "", "()V", "AUTO_REFRESH_INTERVAL", "", "TAG", "", "TIME_FORMAT", "app_debug"})
    public static final class Companion {
        
        private Companion() {
            super();
        }
    }
    
    @kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000$\n\u0002\u0018\u0002\n\u0002\b\u0004\n\u0002\u0010\u0000\n\u0002\b\u0012\n\u0002\u0010\u000b\n\u0002\b\u0002\n\u0002\u0010\b\n\u0000\n\u0002\u0010\u000e\n\u0000\b\u0082\b\u0018\u0000*\u0004\b\u0000\u0010\u0001*\u0004\b\u0001\u0010\u0002*\u0004\b\u0002\u0010\u0003*\u0004\b\u0003\u0010\u00042\u00020\u0005B%\u0012\u0006\u0010\u0006\u001a\u00028\u0000\u0012\u0006\u0010\u0007\u001a\u00028\u0001\u0012\u0006\u0010\b\u001a\u00028\u0002\u0012\u0006\u0010\t\u001a\u00028\u0003\u00a2\u0006\u0002\u0010\nJ\u000e\u0010\u0011\u001a\u00028\u0000H\u00c6\u0003\u00a2\u0006\u0002\u0010\fJ\u000e\u0010\u0012\u001a\u00028\u0001H\u00c6\u0003\u00a2\u0006\u0002\u0010\fJ\u000e\u0010\u0013\u001a\u00028\u0002H\u00c6\u0003\u00a2\u0006\u0002\u0010\fJ\u000e\u0010\u0014\u001a\u00028\u0003H\u00c6\u0003\u00a2\u0006\u0002\u0010\fJN\u0010\u0015\u001a\u001a\u0012\u0004\u0012\u00028\u0000\u0012\u0004\u0012\u00028\u0001\u0012\u0004\u0012\u00028\u0002\u0012\u0004\u0012\u00028\u00030\u00002\b\b\u0002\u0010\u0006\u001a\u00028\u00002\b\b\u0002\u0010\u0007\u001a\u00028\u00012\b\b\u0002\u0010\b\u001a\u00028\u00022\b\b\u0002\u0010\t\u001a\u00028\u0003H\u00c6\u0001\u00a2\u0006\u0002\u0010\u0016J\u0013\u0010\u0017\u001a\u00020\u00182\b\u0010\u0019\u001a\u0004\u0018\u00010\u0005H\u00d6\u0003J\t\u0010\u001a\u001a\u00020\u001bH\u00d6\u0001J\t\u0010\u001c\u001a\u00020\u001dH\u00d6\u0001R\u0013\u0010\u0006\u001a\u00028\u0000\u00a2\u0006\n\n\u0002\u0010\r\u001a\u0004\b\u000b\u0010\fR\u0013\u0010\t\u001a\u00028\u0003\u00a2\u0006\n\n\u0002\u0010\r\u001a\u0004\b\u000e\u0010\fR\u0013\u0010\u0007\u001a\u00028\u0001\u00a2\u0006\n\n\u0002\u0010\r\u001a\u0004\b\u000f\u0010\fR\u0013\u0010\b\u001a\u00028\u0002\u00a2\u0006\n\n\u0002\u0010\r\u001a\u0004\b\u0010\u0010\f\u00a8\u0006\u001e"}, d2 = {"Lcom/stockflip/MainActivity$Quadruple;", "A", "B", "C", "D", "", "first", "second", "third", "fourth", "(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)V", "getFirst", "()Ljava/lang/Object;", "Ljava/lang/Object;", "getFourth", "getSecond", "getThird", "component1", "component2", "component3", "component4", "copy", "(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)Lcom/stockflip/MainActivity$Quadruple;", "equals", "", "other", "hashCode", "", "toString", "", "app_debug"})
    static final class Quadruple<A extends java.lang.Object, B extends java.lang.Object, C extends java.lang.Object, D extends java.lang.Object> {
        private final A first = null;
        private final B second = null;
        private final C third = null;
        private final D fourth = null;
        
        public Quadruple(A first, B second, C third, D fourth) {
            super();
        }
        
        public final A getFirst() {
            return null;
        }
        
        public final B getSecond() {
            return null;
        }
        
        public final C getThird() {
            return null;
        }
        
        public final D getFourth() {
            return null;
        }
        
        public final A component1() {
            return null;
        }
        
        public final B component2() {
            return null;
        }
        
        public final C component3() {
            return null;
        }
        
        public final D component4() {
            return null;
        }
        
        @org.jetbrains.annotations.NotNull()
        public final com.stockflip.MainActivity.Quadruple<A, B, C, D> copy(A first, B second, C third, D fourth) {
            return null;
        }
        
        @java.lang.Override()
        public boolean equals(@org.jetbrains.annotations.Nullable()
        java.lang.Object other) {
            return false;
        }
        
        @java.lang.Override()
        public int hashCode() {
            return 0;
        }
        
        @java.lang.Override()
        @org.jetbrains.annotations.NotNull()
        public java.lang.String toString() {
            return null;
        }
    }
}