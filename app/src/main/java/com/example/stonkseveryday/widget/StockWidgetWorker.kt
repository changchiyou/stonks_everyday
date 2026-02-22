package com.example.stonkseveryday.widget

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.updateAll
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

class StockWidgetWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            // Update all widget instances
            StockWidget().updateAll(applicationContext)
            Result.success()
        } catch (e: Exception) {
            Result.failure()
        }
    }

    companion object {
        const val WORK_NAME = "StockWidgetUpdateWork"
    }
}
