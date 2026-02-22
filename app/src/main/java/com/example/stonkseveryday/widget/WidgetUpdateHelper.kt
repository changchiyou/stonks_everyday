package com.example.stonkseveryday.widget

import android.content.Context
import androidx.work.*
import java.util.concurrent.TimeUnit

object WidgetUpdateHelper {

    fun scheduleWidgetUpdates(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val updateRequest = PeriodicWorkRequestBuilder<StockWidgetWorker>(
            15, TimeUnit.MINUTES
        )
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            StockWidgetWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            updateRequest
        )
    }

    fun cancelWidgetUpdates(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(StockWidgetWorker.WORK_NAME)
    }
}
