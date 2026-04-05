package com.kishan.attendmate.ui.widget

import android.content.Context
import androidx.work.*
import java.util.concurrent.TimeUnit

object WidgetSyncScheduler {
    private const val WORK_NAME = "WidgetUpdatePeriodicWork"

    /**
     * Set up a PeriodicWorkRequest to update the widget data in the background.
     * Call this inside your Application class (onCreate) or MainActivity (onCreate).
     */
    fun schedulePeriodicUpdate(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        // Minimum periodic interval on Android is 15 minutes.
        val workRequest = PeriodicWorkRequestBuilder<WidgetUpdateWorker>(
            15, TimeUnit.MINUTES
        )
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            workRequest
        )
    }

    /**
     * Enqueues a one-time work request to sync the widget immediately.
     */
    fun triggerManualUpdate(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
            
        val workRequest = OneTimeWorkRequestBuilder<WidgetUpdateWorker>()
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(context).enqueue(workRequest)
    }
}
