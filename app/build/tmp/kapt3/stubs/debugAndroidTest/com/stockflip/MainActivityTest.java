package com.stockflip;

@org.junit.runner.RunWith(value = androidx.test.ext.junit.runners.AndroidJUnit4.class)
@androidx.test.filters.LargeTest()
@kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000>\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u0002\n\u0002\b\b\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0006\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\t\n\u0002\b\u0002\n\u0002\u0010\u000e\n\u0000\b\u0007\u0018\u00002\u00020\u0001B\u0005\u00a2\u0006\u0002\u0010\u0002J\b\u0010\u0006\u001a\u00020\u0007H\u0007J\b\u0010\b\u001a\u00020\u0007H\u0007J\b\u0010\t\u001a\u00020\u0007H\u0007J\b\u0010\n\u001a\u00020\u0007H\u0007J\b\u0010\u000b\u001a\u00020\u0007H\u0007J\b\u0010\f\u001a\u00020\u0007H\u0007J\b\u0010\r\u001a\u00020\u0007H\u0007J\b\u0010\u000e\u001a\u00020\u0007H\u0007J\u000e\u0010\u000f\u001a\b\u0012\u0004\u0012\u00020\u00110\u0010H\u0002J\u000e\u0010\u0012\u001a\b\u0012\u0004\u0012\u00020\u00110\u0010H\u0002J\b\u0010\u0013\u001a\u00020\u0007H\u0007J\b\u0010\u0014\u001a\u00020\u0007H\u0007J\b\u0010\u0015\u001a\u00020\u0007H\u0007J\b\u0010\u0016\u001a\u00020\u0007H\u0007J\u0010\u0010\u0017\u001a\u00020\u00182\u0006\u0010\u0019\u001a\u00020\u001aH\u0002J\u001c\u0010\u001b\u001a\b\u0012\u0004\u0012\u00020\u00110\u00102\f\u0010\u001c\u001a\b\u0012\u0004\u0012\u00020\u001d0\u0010H\u0002R\u0014\u0010\u0003\u001a\b\u0012\u0004\u0012\u00020\u00050\u0004X\u0082.\u00a2\u0006\u0002\n\u0000\u00a8\u0006\u001e"}, d2 = {"Lcom/stockflip/MainActivityTest;", "", "()V", "scenario", "Landroidx/test/core/app/ActivityScenario;", "Lcom/stockflip/MainActivity;", "dropDown_dismissedOnFocusLoss", "", "dropDown_handlesNoResults", "dropDown_initiallyNotVisible", "dropDown_notShownForEmptyInput", "dropDown_notShownForSingleCharacter", "dropDown_showsForValidSearch", "dropDown_showsLoadingState", "dropDown_showsSwedishStocksFirst", "hasDropDownItems", "Lorg/hamcrest/Matcher;", "Landroid/view/View;", "hasLoadingIndicator", "searchInput_canEnterText", "searchInput_initiallyEmpty", "searchInput_isDisplayed", "setup", "waitFor", "Landroidx/test/espresso/ViewAction;", "millis", "", "withFirstDropDownItem", "matcher", "", "app_debugAndroidTest"})
public final class MainActivityTest {
    private androidx.test.core.app.ActivityScenario<com.stockflip.MainActivity> scenario;
    
    public MainActivityTest() {
        super();
    }
    
    @org.junit.Before()
    public final void setup() {
    }
    
    @org.junit.Test()
    public final void searchInput_isDisplayed() {
    }
    
    @org.junit.Test()
    public final void searchInput_initiallyEmpty() {
    }
    
    @org.junit.Test()
    public final void searchInput_canEnterText() {
    }
    
    @org.junit.Test()
    public final void dropDown_initiallyNotVisible() {
    }
    
    @org.junit.Test()
    public final void dropDown_showsForValidSearch() {
    }
    
    @org.junit.Test()
    public final void dropDown_notShownForSingleCharacter() {
    }
    
    @org.junit.Test()
    public final void dropDown_notShownForEmptyInput() {
    }
    
    @org.junit.Test()
    public final void dropDown_showsSwedishStocksFirst() {
    }
    
    @org.junit.Test()
    public final void dropDown_dismissedOnFocusLoss() {
    }
    
    @org.junit.Test()
    public final void dropDown_showsLoadingState() {
    }
    
    @org.junit.Test()
    public final void dropDown_handlesNoResults() {
    }
    
    private final androidx.test.espresso.ViewAction waitFor(long millis) {
        return null;
    }
    
    private final org.hamcrest.Matcher<android.view.View> hasDropDownItems() {
        return null;
    }
    
    private final org.hamcrest.Matcher<android.view.View> hasLoadingIndicator() {
        return null;
    }
    
    private final org.hamcrest.Matcher<android.view.View> withFirstDropDownItem(org.hamcrest.Matcher<java.lang.String> matcher) {
        return null;
    }
}