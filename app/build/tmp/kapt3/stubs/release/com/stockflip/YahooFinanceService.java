package com.stockflip;

@kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000J\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0010\u000e\n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0004\n\u0002\u0010\u0006\n\u0000\n\u0002\u0010\u000b\n\u0002\b\u0004\n\u0002\u0010 \n\u0002\u0018\u0002\n\u0002\b\u0002\b\u00c6\u0002\u0018\u00002\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0002J\u0018\u0010\u0010\u001a\u0004\u0018\u00010\u00042\u0006\u0010\u0011\u001a\u00020\u0004H\u0086@\u00a2\u0006\u0002\u0010\u0012J\u0018\u0010\u0013\u001a\u0004\u0018\u00010\u00142\u0006\u0010\u0011\u001a\u00020\u0004H\u0086@\u00a2\u0006\u0002\u0010\u0012J(\u0010\u0015\u001a\u00020\u00162\u0006\u0010\u0017\u001a\u00020\u00042\u0006\u0010\u0011\u001a\u00020\u00042\u0006\u0010\u0018\u001a\u00020\u00042\u0006\u0010\u0019\u001a\u00020\u0004H\u0002J\u001c\u0010\u001a\u001a\b\u0012\u0004\u0012\u00020\u001c0\u001b2\u0006\u0010\u001d\u001a\u00020\u0004H\u0087@\u00a2\u0006\u0002\u0010\u0012R\u000e\u0010\u0003\u001a\u00020\u0004X\u0082T\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0005\u001a\u00020\u0004X\u0082T\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0006\u001a\u00020\u0004X\u0082T\u00a2\u0006\u0002\n\u0000R\u0016\u0010\u0007\u001a\n \t*\u0004\u0018\u00010\b0\bX\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u000e\u0010\n\u001a\u00020\u000bX\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u000e\u0010\f\u001a\u00020\rX\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u0016\u0010\u000e\u001a\n \t*\u0004\u0018\u00010\u000f0\u000fX\u0082\u0004\u00a2\u0006\u0002\n\u0000\u00a8\u0006\u001e"}, d2 = {"Lcom/stockflip/YahooFinanceService;", "", "()V", "BASE_URL", "", "SEARCH_URL", "TAG", "api", "Lcom/stockflip/YahooFinanceApi;", "kotlin.jvm.PlatformType", "client", "Lokhttp3/OkHttpClient;", "loggingInterceptor", "Lokhttp3/logging/HttpLoggingInterceptor;", "retrofit", "Lretrofit2/Retrofit;", "getCompanyName", "symbol", "(Ljava/lang/String;Lkotlin/coroutines/Continuation;)Ljava/lang/Object;", "getStockPrice", "", "isValidStock", "", "quoteType", "name", "typeDisp", "searchStocks", "", "Lcom/stockflip/StockSearchResult;", "query", "app_release"})
public final class YahooFinanceService {
    @org.jetbrains.annotations.NotNull()
    private static final java.lang.String TAG = "YahooFinanceService";
    @org.jetbrains.annotations.NotNull()
    private static final java.lang.String BASE_URL = "https://query1.finance.yahoo.com/";
    @org.jetbrains.annotations.NotNull()
    private static final java.lang.String SEARCH_URL = "https://query1.finance.yahoo.com/v1/finance/search";
    @org.jetbrains.annotations.NotNull()
    private static final okhttp3.logging.HttpLoggingInterceptor loggingInterceptor = null;
    @org.jetbrains.annotations.NotNull()
    private static final okhttp3.OkHttpClient client = null;
    private static final retrofit2.Retrofit retrofit = null;
    private static final com.stockflip.YahooFinanceApi api = null;
    @org.jetbrains.annotations.NotNull()
    public static final com.stockflip.YahooFinanceService INSTANCE = null;
    
    private YahooFinanceService() {
        super();
    }
    
    @org.jetbrains.annotations.Nullable()
    public final java.lang.Object getStockPrice(@org.jetbrains.annotations.NotNull()
    java.lang.String symbol, @org.jetbrains.annotations.NotNull()
    kotlin.coroutines.Continuation<? super java.lang.Double> $completion) {
        return null;
    }
    
    @org.jetbrains.annotations.Nullable()
    public final java.lang.Object getCompanyName(@org.jetbrains.annotations.NotNull()
    java.lang.String symbol, @org.jetbrains.annotations.NotNull()
    kotlin.coroutines.Continuation<? super java.lang.String> $completion) {
        return null;
    }
    
    @kotlin.jvm.JvmStatic()
    @org.jetbrains.annotations.Nullable()
    public static final java.lang.Object searchStocks(@org.jetbrains.annotations.NotNull()
    java.lang.String query, @org.jetbrains.annotations.NotNull()
    kotlin.coroutines.Continuation<? super java.util.List<com.stockflip.StockSearchResult>> $completion) {
        return null;
    }
    
    private final boolean isValidStock(java.lang.String quoteType, java.lang.String symbol, java.lang.String name, java.lang.String typeDisp) {
        return false;
    }
}