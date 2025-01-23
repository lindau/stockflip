package com.stockflip

import android.content.Context
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.*
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.testing.WorkManagerTestInitHelper
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class StockPriceUpdateWorkerTest {
    private lateinit var context: Context
    private lateinit var workManager: WorkManager

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        val config = Configuration.Builder()
            .setMinimumLoggingLevel(Log.DEBUG)
            .build()
        WorkManagerTestInitHelper.initializeTestWorkManager(context, config)
        workManager = WorkManager.getInstance(context)
    }

    @Test
    fun testWorkerExecutes() = runBlocking {
        // Create test worker
        val worker = TestListenableWorkerBuilder<StockPriceUpdateWorker>(context).build()
        
        // Run the worker
        val result = worker.doWork()
        
        // Verify result
        assertEquals(ListenableWorker.Result.success(), result)
    }

    @Test
    fun testPeriodicWorkIsScheduled() {
        // Start periodic updates
        StockPriceUpdater.startPeriodicUpdate(context)

        // Get scheduled work info
        val workInfos = workManager.getWorkInfosForUniqueWork(
            StockPriceUpdater.WORK_NAME_PERIODIC
        ).get()

        // Verify work is scheduled
        assertFalse(workInfos.isEmpty())
        assertEquals(WorkInfo.State.ENQUEUED, workInfos[0].state)
    }

    @Test
    fun testImmediateWorkIsScheduled() {
        // Start periodic updates (which includes immediate work)
        StockPriceUpdater.startPeriodicUpdate(context)

        // Get scheduled work info
        val workInfos = workManager.getWorkInfosForUniqueWork(
            StockPriceUpdater.WORK_NAME_IMMEDIATE
        ).get()

        // Verify work is scheduled
        assertFalse(workInfos.isEmpty())
        assertTrue(workInfos[0].state in listOf(WorkInfo.State.ENQUEUED, WorkInfo.State.RUNNING))
    }

    @Test
    fun testWorkerSendsBroadcast() = runBlocking {
        var broadcastReceived = false
        
        // Register test broadcast receiver
        val receiver = PriceUpdateReceiver {
            broadcastReceived = true
        }
        context.registerReceiver(
            receiver,
            PriceUpdateReceiver.createIntentFilter(),
            Context.RECEIVER_NOT_EXPORTED
        )

        // Create and run test worker
        val worker = TestListenableWorkerBuilder<StockPriceUpdateWorker>(context).build()
        worker.doWork()

        // Verify broadcast was received
        assertTrue(broadcastReceived)

        // Cleanup
        context.unregisterReceiver(receiver)
    }
} 