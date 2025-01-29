package com.stockflip

import android.app.NotificationManager
import android.content.Context
import android.service.notification.StatusBarNotification
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.*
import androidx.work.Configuration
import androidx.work.ListenableWorker
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.testing.WorkManagerTestInitHelper
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.abs

@RunWith(AndroidJUnit4::class)
class NotificationTest {
    private lateinit var context: Context
    private lateinit var notificationManager: NotificationManager
    private lateinit var testDatabase: StockPairDatabase
    private lateinit var workManager: WorkManager

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        
        // Clear any existing notifications
        notificationManager.cancelAll()
        
        // Initialize WorkManager for testing with custom configuration
        val config = Configuration.Builder()
            .setMinimumLoggingLevel(android.util.Log.DEBUG)
            .setWorkerFactory(object : WorkerFactory() {
                override fun createWorker(
                    appContext: Context,
                    workerClassName: String,
                    workerParameters: WorkerParameters
                ): ListenableWorker? {
                    return when (workerClassName) {
                        TestStockPriceUpdateWorker::class.java.name ->
                            TestStockPriceUpdateWorker(appContext, workerParameters)
                        else -> null
                    }
                }
            })
            .build()
            
        WorkManagerTestInitHelper.initializeTestWorkManager(context, config)
        workManager = WorkManager.getInstance(context)
        
        // Initialize test database
        testDatabase = StockPairDatabase.getDatabase(context)
        
        // Clear database before each test
        runBlocking {
            testDatabase.clearAllTables()
        }
        
        // Create notification channel
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        val channel = android.app.NotificationChannel(
            StockPriceUpdater.CHANNEL_ID,
            "Stock Price Alerts",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Notifications for stock price alerts"
            enableLights(true)
            enableVibration(true)
            setShowBadge(true)
        }
        notificationManager.createNotificationChannel(channel)
    }

    @Test
    fun testNotificationChannelCreation() {
        // Verify channel exists
        val channel = notificationManager.getNotificationChannel(StockPriceUpdater.CHANNEL_ID)
        assertNotNull("Notification channel should be created", channel)
        assertEquals("Channel name should be correct", "Stock Price Alerts", channel?.name)
        assertEquals("Channel importance should be high", NotificationManager.IMPORTANCE_HIGH, channel?.importance)
    }

    @Test
    fun testWorkerSendsNotificationWhenPricesEqual() = runBlocking {
        try {
            // Create a test stock pair with equal prices
            val stockPair = StockPair(
                id = 1,
                ticker1 = "TEST1",
                ticker2 = "TEST2",
                companyName1 = "Test Company 1",
                companyName2 = "Test Company 2",
                notifyWhenEqual = true
            ).withCurrentPrices(100.0, 100.0)

            // Insert test data into database
            testDatabase.stockPairDao().insertStockPair(stockPair)

            // Create work request
            val request = OneTimeWorkRequestBuilder<TestStockPriceUpdateWorker>().build()
            
            // Enqueue and wait for result
            workManager.enqueue(request).result.get()
            
            // Wait briefly for notification to be posted
            Thread.sleep(1000)

            // Verify notification was sent
            val notifications = getActiveNotifications()
            assertTrue("Should have at least one notification", notifications.isNotEmpty())
            
            val notification = notifications.first()
            val text = notification.notification.extras.getString(android.app.Notification.EXTRA_TEXT)
            assertTrue("Notification should mention equal prices", 
                text?.contains("prices are now equal") == true)
        } catch (e: Exception) {
            fail("Test failed with exception: ${e.message}")
        }
    }

    @Test
    fun testWorkerSendsNotificationWhenPriceDifferenceReached() = runBlocking {
        try {
            // Create a test stock pair with price difference threshold
            val stockPair = StockPair(
                id = 2,
                ticker1 = "TEST3",
                ticker2 = "TEST4",
                companyName1 = "Test Company 3",
                companyName2 = "Test Company 4",
                priceDifference = 50.0
            ).withCurrentPrices(150.0, 100.0)

            // Insert test data into database
            testDatabase.stockPairDao().insertStockPair(stockPair)

            // Create work request
            val request = OneTimeWorkRequestBuilder<TestStockPriceUpdateWorker>().build()
            
            // Enqueue and wait for result
            workManager.enqueue(request).result.get()
            
            // Wait briefly for notification to be posted
            Thread.sleep(1000)

            // Verify notification was sent
            val notifications = getActiveNotifications()
            assertTrue("Should have at least one notification", notifications.isNotEmpty())
            
            val notification = notifications.first()
            val text = notification.notification.extras.getString(android.app.Notification.EXTRA_TEXT)
            assertTrue("Notification should mention price difference", 
                text?.contains("difference") == true)
        } catch (e: Exception) {
            fail("Test failed with exception: ${e.message}")
        }
    }

    @Test
    fun testNoNotificationWhenConditionsNotMet() = runBlocking {
        try {
            // Create a test stock pair with no notification conditions met
            val stockPair = StockPair(
                id = 3,
                ticker1 = "TEST5",
                ticker2 = "TEST6",
                companyName1 = "Test Company 5",
                companyName2 = "Test Company 6",
                priceDifference = 100.0,
                notifyWhenEqual = false
            ).withCurrentPrices(50.0, 75.0)

            // Insert test data into database
            testDatabase.stockPairDao().insertStockPair(stockPair)

            // Create work request
            val request = OneTimeWorkRequestBuilder<TestStockPriceUpdateWorker>().build()
            
            // Enqueue and wait for result
            workManager.enqueue(request).result.get()
            
            // Wait briefly to ensure no notifications are posted
            Thread.sleep(1000)

            // Verify no notification was sent
            val notifications = getActiveNotifications()
            assertTrue("Should have no notifications", notifications.isEmpty())
        } catch (e: Exception) {
            fail("Test failed with exception: ${e.message}")
        }
    }

    private fun getActiveNotifications(): Array<StatusBarNotification> {
        return notificationManager.activeNotifications
    }
} 