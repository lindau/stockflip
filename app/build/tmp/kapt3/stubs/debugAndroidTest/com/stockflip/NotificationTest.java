package com.stockflip;

@org.junit.runner.RunWith(value = androidx.test.ext.junit.runners.AndroidJUnit4.class)
@kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u00006\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u0002\n\u0000\n\u0002\u0010\u0011\n\u0002\u0018\u0002\n\u0002\b\u0007\b\u0007\u0018\u00002\u00020\u0001B\u0005\u00a2\u0006\u0002\u0010\u0002J\b\u0010\u000b\u001a\u00020\fH\u0002J\u0013\u0010\r\u001a\b\u0012\u0004\u0012\u00020\u000f0\u000eH\u0002\u00a2\u0006\u0002\u0010\u0010J\b\u0010\u0011\u001a\u00020\fH\u0007J\b\u0010\u0012\u001a\u00020\fH\u0007J\b\u0010\u0013\u001a\u00020\fH\u0007J\b\u0010\u0014\u001a\u00020\fH\u0007J\b\u0010\u0015\u001a\u00020\fH\u0007R\u000e\u0010\u0003\u001a\u00020\u0004X\u0082.\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0005\u001a\u00020\u0006X\u0082.\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0007\u001a\u00020\bX\u0082.\u00a2\u0006\u0002\n\u0000R\u000e\u0010\t\u001a\u00020\nX\u0082.\u00a2\u0006\u0002\n\u0000\u00a8\u0006\u0016"}, d2 = {"Lcom/stockflip/NotificationTest;", "", "()V", "context", "Landroid/content/Context;", "notificationManager", "Landroid/app/NotificationManager;", "testDatabase", "Lcom/stockflip/StockPairDatabase;", "workManager", "Landroidx/work/WorkManager;", "createNotificationChannel", "", "getActiveNotifications", "", "Landroid/service/notification/StatusBarNotification;", "()[Landroid/service/notification/StatusBarNotification;", "setup", "testNoNotificationWhenConditionsNotMet", "testNotificationChannelCreation", "testWorkerSendsNotificationWhenPriceDifferenceReached", "testWorkerSendsNotificationWhenPricesEqual", "app_debugAndroidTest"})
public final class NotificationTest {
    private android.content.Context context;
    private android.app.NotificationManager notificationManager;
    private com.stockflip.StockPairDatabase testDatabase;
    private androidx.work.WorkManager workManager;
    
    public NotificationTest() {
        super();
    }
    
    @org.junit.Before()
    public final void setup() {
    }
    
    private final void createNotificationChannel() {
    }
    
    @org.junit.Test()
    public final void testNotificationChannelCreation() {
    }
    
    @org.junit.Test()
    public final void testWorkerSendsNotificationWhenPricesEqual() {
    }
    
    @org.junit.Test()
    public final void testWorkerSendsNotificationWhenPriceDifferenceReached() {
    }
    
    @org.junit.Test()
    public final void testNoNotificationWhenConditionsNotMet() {
    }
    
    private final android.service.notification.StatusBarNotification[] getActiveNotifications() {
        return null;
    }
}