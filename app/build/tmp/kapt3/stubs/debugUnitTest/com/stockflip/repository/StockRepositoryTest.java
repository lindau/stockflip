package com.stockflip.repository;

@kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000\u001e\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u0002\n\u0002\u0018\u0002\n\u0002\b\f\u0018\u00002\u00020\u0001B\u0005\u00a2\u0006\u0002\u0010\u0002J\f\u0010\u0005\u001a\u00060\u0006j\u0002`\u0007H\u0007J\f\u0010\b\u001a\u00060\u0006j\u0002`\u0007H\u0007J\f\u0010\t\u001a\u00060\u0006j\u0002`\u0007H\u0007J\f\u0010\n\u001a\u00060\u0006j\u0002`\u0007H\u0007J\f\u0010\u000b\u001a\u00060\u0006j\u0002`\u0007H\u0007J\f\u0010\f\u001a\u00060\u0006j\u0002`\u0007H\u0007J\b\u0010\r\u001a\u00020\u0006H\u0007J\b\u0010\u000e\u001a\u00020\u0006H\u0007J\f\u0010\u000f\u001a\u00060\u0006j\u0002`\u0007H\u0007J\f\u0010\u0010\u001a\u00060\u0006j\u0002`\u0007H\u0007J\f\u0010\u0011\u001a\u00060\u0006j\u0002`\u0007H\u0007J\f\u0010\u0012\u001a\u00060\u0006j\u0002`\u0007H\u0007R\u000e\u0010\u0003\u001a\u00020\u0004X\u0082.\u00a2\u0006\u0002\n\u0000\u00a8\u0006\u0013"}, d2 = {"Lcom/stockflip/repository/StockRepositoryTest;", "", "()V", "repository", "Lcom/stockflip/repository/StockRepository;", "partial search returns relevant results", "", "Lkotlinx/coroutines/test/TestResult;", "search handles empty results", "search handles network error", "search prioritizes Swedish stocks", "search returns cached results within TTL", "search returns fresh results after cache expiry", "setup", "tearDown", "test search error handling", "test search with empty query", "test search with short query", "test successful search", "app_debugUnitTest"})
@kotlin.OptIn(markerClass = {kotlinx.coroutines.ExperimentalCoroutinesApi.class, kotlin.time.ExperimentalTime.class})
public final class StockRepositoryTest {
    private com.stockflip.repository.StockRepository repository;
    
    public StockRepositoryTest() {
        super();
    }
    
    @org.junit.Before()
    public final void setup() {
    }
    
    @org.junit.After()
    public final void tearDown() {
    }
}