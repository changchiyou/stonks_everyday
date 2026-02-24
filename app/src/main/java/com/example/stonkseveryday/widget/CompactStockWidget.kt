package com.example.stonkseveryday.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.glance.GlanceId
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.provideContent
import androidx.glance.preview.ExperimentalGlancePreviewApi
import androidx.glance.preview.Preview
import com.example.stonkseveryday.data.database.StockDatabase
import com.example.stonkseveryday.data.model.TransactionType
import com.example.stonkseveryday.data.repository.StockRepository
import kotlinx.coroutines.flow.first

/**
 * 緊湊型股票小工具 (1x2)
 * 只顯示當日損益
 */
class CompactStockWidget : GlanceAppWidget() {

    companion object {
        private const val AUTO_REFRESH_INTERVAL = 10 * 60 * 1000L // 10 分鐘
    }

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        // 先檢查 widget 是否仍然存在（提前過濾無效的 widget ID）
        try {
            val manager = androidx.glance.appwidget.GlanceAppWidgetManager(context)
            val appWidgetId = manager.getAppWidgetId(id)
            val appWidgetManager = android.appwidget.AppWidgetManager.getInstance(context)
            val widgetInfo = appWidgetManager.getAppWidgetInfo(appWidgetId)

            if (widgetInfo == null) {
                android.util.Log.w("CompactStockWidget", "Widget ID $appWidgetId no longer exists, throwing exception to trigger cleanup")
                // 拋出 IllegalArgumentException 讓 Glance 框架知道這個 widget 無效
                throw IllegalArgumentException("Widget ID $appWidgetId is no longer valid")
            }
        } catch (e: IllegalArgumentException) {
            // 重新拋出 IllegalArgumentException，讓系統知道這個 widget 無效
            throw e
        } catch (e: Exception) {
            android.util.Log.e("CompactStockWidget", "Failed to check widget existence: ${e.message}", e)
            throw e
        }

        // 檢查是否需要自動刷新（小工具顯示時）
        val prefs = context.getSharedPreferences("widget_prefs", Context.MODE_PRIVATE)
        val lastRefreshTime = prefs.getLong("last_refresh_time", 0L)
        val now = System.currentTimeMillis()
        val timeSinceLastRefresh = now - lastRefreshTime

        if (lastRefreshTime > 0 && timeSinceLastRefresh >= AUTO_REFRESH_INTERVAL) {
            android.util.Log.i(
                "CompactStockWidget",
                "自動刷新觸發：距上次刷新 ${timeSinceLastRefresh / 1000 / 60} 分鐘"
            )
            // 使用 WorkManager 啟動背景刷新任務（避免阻塞 UI）
            val workRequest = androidx.work.OneTimeWorkRequestBuilder<AutoRefreshWorker>()
                .build()
            androidx.work.WorkManager.getInstance(context).enqueue(workRequest)
        }

        // Widget 存在，嘗試更新
        try {
            // 使用快取避免多個小工具同時計算
            val data = WidgetDataCache.getData(context, forceRefresh = false)

            // 每次都從 SharedPreferences 讀取最新的刷新時間（不快取）
            val prefs = context.getSharedPreferences("widget_prefs", Context.MODE_PRIVATE)
            val lastRefreshTime = prefs.getLong("last_refresh_time", System.currentTimeMillis())

            provideContent {
                // 使用 WidgetContent，但傳入 isCompact = true
                WidgetContent(
                    dailyProfitLoss = data.dailyProfitLoss,
                    totalProfitLoss = 0.0,  // 不顯示
                    totalAssets = 0.0,       // 不顯示
                    lastRefreshTime = lastRefreshTime,
                    isCompact = true
                )
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            // Widget 在渲染過程中被取消，這是正常現象（app 更新時）
            android.util.Log.d("CompactStockWidget", "Widget cancelled: ${e.message}")
            throw e  // CancellationException 必須重新拋出
        } catch (e: Exception) {
            // 捕獲所有其他錯誤
            android.util.Log.e("CompactStockWidget", "Error updating widget: ${e.javaClass.simpleName} - ${e.message}", e)
            // Widget 存在但更新失敗，嘗試顯示預設值
            try {
                provideContent {
                    WidgetContent(
                        dailyProfitLoss = 0.0,
                        totalProfitLoss = 0.0,
                        totalAssets = 0.0,
                        lastRefreshTime = 0L,  // 0L 會顯示為 "--/-- --:--"
                        isCompact = true
                    )
                }
            } catch (fallbackError: Exception) {
                // Fallback 也失敗了，記錄錯誤
                android.util.Log.e("CompactStockWidget", "Failed to show fallback content: ${fallbackError.message}", fallbackError)
            }
        }
    }
}

class CompactStockWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = CompactStockWidget()

    override fun onReceive(context: android.content.Context, intent: android.content.Intent) {
        super.onReceive(context, intent)

        // 當 app 更新後，強制重新載入所有 widget
        if (android.content.Intent.ACTION_MY_PACKAGE_REPLACED == intent.action) {
            android.util.Log.i("CompactStockWidgetReceiver", "App 更新完成，強制更新所有緊湊 widget")

            // 獲取所有屬於此 Provider 的 widget IDs
            val appWidgetManager = android.appwidget.AppWidgetManager.getInstance(context)
            val componentName = android.content.ComponentName(context, CompactStockWidgetReceiver::class.java)
            val appWidgetIds = appWidgetManager.getAppWidgetIds(componentName)

            android.util.Log.i("CompactStockWidgetReceiver", "找到 ${appWidgetIds.size} 個緊湊 widget 需要更新")

            // 手動觸發更新（呼叫 onUpdate）
            onUpdate(context, appWidgetManager, appWidgetIds)
        }
    }
}

/**
 * 緊湊型 Widget 預覽 - 獲利狀態
 */
@OptIn(ExperimentalGlancePreviewApi::class)
@Preview(widthDp = 110, heightDp = 110)
@Composable
fun CompactStockWidgetPreview() {
    WidgetContent(
        dailyProfitLoss = 1250.0,
        totalProfitLoss = 0.0,
        totalAssets = 0.0,
        lastRefreshTime = System.currentTimeMillis() - 180000, // 3 分鐘前
        isCompact = true
    )
}

/**
 * 緊湊型 Widget 預覽 - 虧損狀態
 */
@OptIn(ExperimentalGlancePreviewApi::class)
@Preview(widthDp = 110, heightDp = 110)
@Composable
fun CompactStockWidgetNegativePreview() {
    WidgetContent(
        dailyProfitLoss = -3500.0,
        totalProfitLoss = 0.0,
        totalAssets = 0.0,
        lastRefreshTime = System.currentTimeMillis() - 7200000, // 2 小時前
        isCompact = true
    )
}
