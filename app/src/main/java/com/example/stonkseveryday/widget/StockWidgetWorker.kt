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
            android.util.Log.i("StockWidgetWorker", "========== 開始更新小工具 (WorkManager) ==========")

            // Update all widget instances (both standard and compact)
            try {
                StockWidget().updateAll(applicationContext)
                android.util.Log.d("StockWidgetWorker", "✓ 完整小工具更新成功")
            } catch (e: Exception) {
                android.util.Log.e("StockWidgetWorker", "✗ 完整小工具更新失敗: ${e.message}", e)
            }

            try {
                CompactStockWidget().updateAll(applicationContext)
                android.util.Log.d("StockWidgetWorker", "✓ 緊湊小工具更新成功")
            } catch (e: Exception) {
                android.util.Log.e("StockWidgetWorker", "✗ 緊湊小工具更新失敗: ${e.message}", e)
            }

            android.util.Log.i("StockWidgetWorker", "========== 小工具更新完成 (WorkManager) ==========")
            Result.success()
        } catch (e: Exception) {
            android.util.Log.e("StockWidgetWorker", "========== 小工具更新失敗: ${e.message} ==========", e)
            Result.failure()
        }
    }

    companion object {
        const val WORK_NAME = "StockWidgetUpdateWork"
    }
}
