package com.example.stonkseveryday.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.*
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.layout.*
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.color.ColorProvider
import androidx.glance.preview.ExperimentalGlancePreviewApi
import androidx.glance.preview.Preview
import com.example.stonkseveryday.MainActivity
import com.example.stonkseveryday.data.database.StockDatabase
import com.example.stonkseveryday.data.model.StockSummary
import com.example.stonkseveryday.data.model.TransactionType
import com.example.stonkseveryday.data.repository.StockRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class StockWidget : GlanceAppWidget() {

    companion object {
        private const val AUTO_REFRESH_INTERVAL = 10 * 60 * 1000L // 10 分鐘
        private var isAutoRefreshing = false

        /**
         * 執行背景自動刷新
         * 靜默刷新，不顯示 Toast
         */
        suspend fun performBackgroundRefresh(context: Context) {
            // 避免重複執行
            if (isAutoRefreshing) {
                android.util.Log.d("StockWidget", "自動刷新已在進行中，跳過")
                return
            }

            isAutoRefreshing = true

            try {
                android.util.Log.i("StockWidget", "========== 開始自動刷新 ==========")

                // 取得所有持股的股票代碼
                val database = StockDatabase.getDatabase(context)
                val dao = database.stockTransactionDao()
                val repository = StockRepository(dao, context)

                val allTransactions = dao.getAllTransactions().first()
                val holdings = mutableMapOf<String, MutableList<com.example.stonkseveryday.data.model.StockTransaction>>()
                allTransactions.forEach { tx ->
                    holdings.getOrPut(tx.stockCode) { mutableListOf() }.add(tx)
                }

                val stockCodes = holdings.keys.toList()
                android.util.Log.i("StockWidget", "自動刷新 ${stockCodes.size} 支股票")

                // 強制刷新每支股票的價格
                var successCount = 0
                var failCount = 0
                for (stockCode in stockCodes) {
                    try {
                        val price = repository.getStockPrice(stockCode, forceRefresh = true)
                        if (price != null && !price.isStale) {
                            successCount++
                        } else {
                            failCount++
                        }
                    } catch (e: Exception) {
                        failCount++
                        android.util.Log.w("StockWidget", "$stockCode 自動刷新失敗: ${e.message}")
                    }
                }

                android.util.Log.i("StockWidget", "自動刷新完成：成功 $successCount 支，失敗 $failCount 支")

                // 只有在至少有一支股票更新成功時，才更新刷新時間
                if (successCount > 0) {
                    val prefs = context.getSharedPreferences("widget_prefs", Context.MODE_PRIVATE)
                    prefs.edit().putLong("last_refresh_time", System.currentTimeMillis()).apply()
                    android.util.Log.d("StockWidget", "✓ 已更新刷新時間（自動刷新）")

                    // 清除快取，強制重新計算
                    WidgetDataCache.getData(context, forceRefresh = true)

                    // 更新所有小工具
                    StockWidget().updateAll(context)
                    CompactStockWidget().updateAll(context)
                    android.util.Log.d("StockWidget", "✓ 小工具已更新")
                }

                android.util.Log.i("StockWidget", "========== 自動刷新完成 ==========")

            } catch (e: Exception) {
                android.util.Log.e("StockWidget", "自動刷新異常: ${e.message}", e)
            } finally {
                isAutoRefreshing = false
            }
        }
    }

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        // 先檢查 widget 是否仍然存在（提前過濾無效的 widget ID）
        try {
            val manager = androidx.glance.appwidget.GlanceAppWidgetManager(context)
            val appWidgetId = manager.getAppWidgetId(id)
            val appWidgetManager = android.appwidget.AppWidgetManager.getInstance(context)
            val widgetInfo = appWidgetManager.getAppWidgetInfo(appWidgetId)

            if (widgetInfo == null) {
                android.util.Log.w("StockWidget", "Widget ID $appWidgetId no longer exists, throwing exception to trigger cleanup")
                // 拋出 IllegalArgumentException 讓 Glance 框架知道這個 widget 無效
                throw IllegalArgumentException("Widget ID $appWidgetId is no longer valid")
            }
        } catch (e: IllegalArgumentException) {
            // 重新拋出 IllegalArgumentException，讓系統知道這個 widget 無效
            throw e
        } catch (e: Exception) {
            android.util.Log.e("StockWidget", "Failed to check widget existence: ${e.message}", e)
            throw e
        }

        // 檢查是否需要自動刷新（小工具顯示時）
        val prefs = context.getSharedPreferences("widget_prefs", Context.MODE_PRIVATE)
        val lastRefreshTime = prefs.getLong("last_refresh_time", 0L)
        val now = System.currentTimeMillis()
        val timeSinceLastRefresh = now - lastRefreshTime

        if (lastRefreshTime > 0 && timeSinceLastRefresh >= AUTO_REFRESH_INTERVAL) {
            android.util.Log.i(
                "StockWidget",
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
                WidgetContent(
                    dailyProfitLoss = data.dailyProfitLoss,
                    totalProfitLoss = data.totalProfitLoss,
                    totalAssets = data.totalAssets,
                    lastRefreshTime = lastRefreshTime,
                    isCompact = false
                )
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            // Widget 在渲染過程中被取消，這是正常現象（app 更新時）
            android.util.Log.d("StockWidget", "Widget cancelled: ${e.message}")
            throw e  // CancellationException 必須重新拋出
        } catch (e: Exception) {
            // 捕獲所有其他錯誤
            android.util.Log.e("StockWidget", "Error updating widget: ${e.javaClass.simpleName} - ${e.message}", e)
            // Widget 存在但更新失敗，嘗試顯示預設值
            try {
                provideContent {
                    WidgetContent(
                        dailyProfitLoss = 0.0,
                        totalProfitLoss = 0.0,
                        totalAssets = 0.0,
                        lastRefreshTime = 0L,  // 0L 會顯示為 "--/-- --:--"
                        isCompact = false
                    )
                }
            } catch (fallbackError: Exception) {
                // Fallback 也失敗了，記錄錯誤
                android.util.Log.e("StockWidget", "Failed to show fallback content: ${fallbackError.message}", fallbackError)
            }
        }
    }

}

/**
 * 格式化金額（與 app 內 MainScreen.formatCurrency 完全一致）
 * - >= 100,000,000: 顯示為 M (百萬)，.2f
 * - >= 10,000: 顯示為 K (千)，.1f
 * - < 10,000: 直接顯示整數
 */
private fun formatAmount(amount: Double): String {
    val absAmount = kotlin.math.abs(amount)
    val sign = if (amount < 0) "-" else ""

    return when {
        absAmount >= 100_000_000 -> String.format("%s$%.2fM", sign, absAmount / 1_000_000)
        absAmount >= 10_000 -> String.format("%s$%.1fK", sign, absAmount / 1_000)
        else -> String.format("%s$%.0f", sign, absAmount)
    }
}

/**
 * 格式化金額（精簡版，完全不含符號）
 * 用於精簡小工具，避免換行
 *
 * 注意：正負號透過顏色區分（紅色=正，綠色=負），所以這裡只顯示數值
 */
private fun formatAmountCompact(amount: Double): String {
    val absAmount = kotlin.math.abs(amount)

    return when {
        // >= 1億：顯示為 M (百萬)，最多 4 位數字 + "M" = 5 字元
        absAmount >= 100_000_000 -> String.format("%.1fM", absAmount / 1_000_000)
        // >= 1萬：顯示為 K (千)，最多 5 位數字 + "K" = 6 字元
        absAmount >= 10_000 -> String.format("%.1fK", absAmount / 1_000)
        // < 1萬：直接顯示整數，最多 4 位數字
        else -> String.format("%.0f", absAmount)
    }
}

/**
 * 格式化刷新時間為絕對時間（日期+時間，精確到分鐘）
 * 格式：MM/dd HH:mm（例如：02/23 14:35）
 * 如果 timestamp 為 0，返回 "--/-- --:--" 表示無資料
 */
private fun formatRefreshTime(timestamp: Long): String {
    return when (timestamp) {
        0L -> "--/-- --:--"
        else -> {
            val dateFormat = SimpleDateFormat("MM/dd HH:mm", Locale.getDefault())
            dateFormat.format(Date(timestamp))
        }
    }
}

/**
 * 根據金額數值智能計算緊湊型小工具的字體大小
 * 避免數字過大時換行
 *
 * 緊湊型小工具的寬度有限（約 110dp），需要根據文字長度動態調整字體
 *
 * @param amount 金額數值
 * @return 字體大小（sp）
 */
private fun calculateCompactFontSize(amount: Double): Int {
    val formattedText = formatAmountCompact(amount)
    val textLength = formattedText.length

    // 根據文字長度動態調整字體大小
    // 考慮負數會有 "-" 符號，需要更小的字體
    return when {
        textLength <= 3 -> 20  // 例如: "100", "1K"
        textLength <= 4 -> 18  // 例如: "1.2K", "1000"
        textLength <= 5 -> 16  // 例如: "12.3K", "-1.2K"
        textLength <= 6 -> 14  // 例如: "123.4K", "-12.3K"
        textLength <= 7 -> 12  // 例如: "1.2M", "-123.4K"
        textLength <= 8 -> 11  // 例如: "12.3M", "-1.2M"
        else -> 10             // 極端情況: "-12.3M" 或更大
    }
}

@Composable
fun WidgetContent(
        dailyProfitLoss: Double,
        totalProfitLoss: Double,
        totalAssets: Double,
        lastRefreshTime: Long = System.currentTimeMillis(),
        isCompact: Boolean = false
    ) {
        val refreshTimeText = formatRefreshTime(lastRefreshTime)

        Box(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(ColorProvider(day = Color(0xFFF5F5F5), night = Color(0xFF1E1E1E)))
                .padding(if (isCompact) 8.dp else 16.dp)
                // 精簡版：整個小工具都可點擊刷新
                .clickable(
                    if (isCompact) {
                        actionRunCallback<RefreshWidgetCallback>()
                    } else {
                        actionStartActivity<MainActivity>()  // 一般版背景點擊打開 app
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = GlanceModifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // 可點擊刷新區域：標題 + 今日損益（一般版上半部可刷新）
                Column(
                    modifier = if (!isCompact) {
                        GlanceModifier
                            .fillMaxWidth()
                            .clickable(actionRunCallback<RefreshWidgetCallback>())
                    } else {
                        GlanceModifier.fillMaxWidth()
                    },
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Title with refresh time
                    Row(
                        modifier = GlanceModifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (isCompact) {
                            // 精簡版：只顯示時間
                            Text(
                                text = refreshTimeText,
                                style = TextStyle(
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Normal,
                                    color = ColorProvider(day = Color.Gray, night = Color.LightGray)
                                )
                            )
                        } else {
                            // 一般版：標題 + 時間
                            Text(
                                text = "Stonks Everyday",
                                style = TextStyle(
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = ColorProvider(day = Color.Black, night = Color.White)
                                )
                            )
                            Spacer(modifier = GlanceModifier.width(8.dp))
                            Text(
                                text = refreshTimeText,
                                style = TextStyle(
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Normal,
                                    color = ColorProvider(day = Color.Gray, night = Color.LightGray)
                                )
                            )
                        }
                    }

                    Spacer(modifier = GlanceModifier.height(if (isCompact) 4.dp else 8.dp))

                    // Daily Profit/Loss (主要顯示)
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        if (!isCompact) {
                            Text(
                                text = "今日損益",
                                style = TextStyle(
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Normal,
                                    color = ColorProvider(day = Color.Gray, night = Color.LightGray)
                                )
                            )
                            Spacer(modifier = GlanceModifier.height(4.dp))
                        }

                        Text(
                            text = if (isCompact) formatAmountCompact(dailyProfitLoss) else formatAmount(dailyProfitLoss),
                            style = TextStyle(
                                fontSize = if (isCompact) calculateCompactFontSize(dailyProfitLoss).sp else 24.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (dailyProfitLoss >= 0) {
                                    // 賺錢：紅色（台股習慣）
                                    ColorProvider(day = Color(0xFFC62828), night = Color(0xFFEF5350))
                                } else {
                                    // 虧錢：綠色（台股習慣）
                                    ColorProvider(day = Color(0xFF2E7D32), night = Color(0xFF4CAF50))
                                }
                            )
                        )
                    }
                }

                if (!isCompact) {
                    Spacer(modifier = GlanceModifier.height(12.dp))

                    // 分隔線來 highlight 可刷新區域
                    Box(
                        modifier = GlanceModifier
                            .fillMaxWidth()
                            .height(2.dp)
                            .background(ColorProvider(day = Color(0xFF1976D2), night = Color(0xFF42A5F5))),
                        content = {}
                    )

                    Spacer(modifier = GlanceModifier.height(12.dp))
                }

                // Additional Info (only in large mode) - 點擊打開 app
                if (!isCompact) {
                    Row(
                    modifier = GlanceModifier
                        .fillMaxWidth()
                        .clickable(actionStartActivity<MainActivity>()),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = GlanceModifier.defaultWeight(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "預估總市值",
                            style = TextStyle(
                                fontSize = 10.sp,
                                color = ColorProvider(day = Color.Gray, night = Color.LightGray)
                            )
                        )
                        Spacer(modifier = GlanceModifier.height(2.dp))
                        Text(
                            text = formatAmount(totalAssets),
                            style = TextStyle(
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = ColorProvider(day = Color.Black, night = Color.White)
                            )
                        )
                    }

                    Column(
                        modifier = GlanceModifier.defaultWeight(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "總未實現損益",
                            style = TextStyle(
                                fontSize = 10.sp,
                                color = ColorProvider(day = Color.Gray, night = Color.LightGray)
                            )
                        )
                        Spacer(modifier = GlanceModifier.height(2.dp))
                        Text(
                            text = formatAmount(totalProfitLoss),
                            style = TextStyle(
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (totalProfitLoss >= 0) {
                                    // 賺錢：紅色（台股習慣）
                                    ColorProvider(day = Color(0xFFC62828), night = Color(0xFFEF5350))
                                } else {
                                    // 虧錢：綠色（台股習慣）
                                    ColorProvider(day = Color(0xFF2E7D32), night = Color(0xFF4CAF50))
                                }
                            )
                        )
                    }
                }
            }
        }
    }
}

/**
 * Widget 更新回調
 * 真正刷新數據：呼叫 API 更新 database，然後更新 widget 顯示
 */
class RefreshWidgetCallback : androidx.glance.appwidget.action.ActionCallback {
    companion object {
        private var isRefreshing = false
    }

    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: androidx.glance.action.ActionParameters
    ) {
        // 手動刷新：不設防抖，用戶點擊就立即執行
        // 只檢查是否已經在刷新中，避免重複執行
        if (isRefreshing) {
            android.util.Log.d("RefreshWidgetCallback", "已經在刷新中，跳過")
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                android.widget.Toast.makeText(
                    context,
                    "正在刷新中，請稍候...",
                    android.widget.Toast.LENGTH_SHORT
                ).show()
            }
            return
        }

        isRefreshing = true

        // 顯示 Toast 提示用戶正在刷新
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            android.widget.Toast.makeText(
                context,
                "正在刷新股價...",
                android.widget.Toast.LENGTH_SHORT
            ).show()
        }

        try {
            android.util.Log.i("RefreshWidgetCallback", "========== 開始刷新股價 ==========")

            // 1. 取得所有持股的股票代碼
            val database = StockDatabase.getDatabase(context)
            val dao = database.stockTransactionDao()
            val repository = com.example.stonkseveryday.data.repository.StockRepository(dao, context)

            val allTransactions = dao.getAllTransactions().first()
            val holdings = mutableMapOf<String, MutableList<com.example.stonkseveryday.data.model.StockTransaction>>()
            allTransactions.forEach { tx ->
                holdings.getOrPut(tx.stockCode) { mutableListOf() }.add(tx)
            }

            val stockCodes = holdings.keys.toList()
            android.util.Log.i("RefreshWidgetCallback", "需要更新 ${stockCodes.size} 支股票: $stockCodes")

            // 2. 強制刷新每支股票的價格（跳過快取）
            var successCount = 0
            var failCount = 0
            for (stockCode in stockCodes) {
                try {
                    android.util.Log.d("RefreshWidgetCallback", "正在更新 $stockCode...")
                    val price = repository.getStockPrice(stockCode, forceRefresh = true)
                    if (price != null && !price.isStale) {
                        successCount++
                        android.util.Log.d("RefreshWidgetCallback", "✓ $stockCode 股價更新成功: ${price.currentPrice}")
                    } else {
                        failCount++
                        android.util.Log.w("RefreshWidgetCallback", "✗ $stockCode 股價更新失敗或使用舊資料")
                    }
                } catch (e: Exception) {
                    failCount++
                    android.util.Log.e("RefreshWidgetCallback", "✗ $stockCode 股價更新異常: ${e.message}", e)
                }
            }

            android.util.Log.i("RefreshWidgetCallback", "股價更新完成：成功 $successCount 支，失敗 $failCount 支")

            // 3. 只有在至少有一支股票更新成功時，才更新刷新時間
            if (successCount > 0) {
                val prefs = context.getSharedPreferences("widget_prefs", Context.MODE_PRIVATE)
                prefs.edit().putLong("last_refresh_time", System.currentTimeMillis()).apply()
                android.util.Log.d("RefreshWidgetCallback", "✓ 已更新刷新時間（成功更新 $successCount 支股票）")
            } else {
                android.util.Log.w("RefreshWidgetCallback", "✗ 沒有股票更新成功，不更新刷新時間")
            }

            // 4. 清除快取，強制重新計算
            android.util.Log.d("RefreshWidgetCallback", "清除小工具資料快取")
            WidgetDataCache.getData(context, forceRefresh = true)

            // 5. 直接更新所有小工具（不使用 WorkManager，避免延遲）
            android.util.Log.d("RefreshWidgetCallback", "開始更新所有小工具...")
            try {
                StockWidget().updateAll(context)
                android.util.Log.d("RefreshWidgetCallback", "✓ 完整小工具更新完成")
            } catch (e: Exception) {
                android.util.Log.e("RefreshWidgetCallback", "✗ 完整小工具更新失敗: ${e.message}", e)
            }

            try {
                CompactStockWidget().updateAll(context)
                android.util.Log.d("RefreshWidgetCallback", "✓ 緊湊小工具更新完成")
            } catch (e: Exception) {
                android.util.Log.e("RefreshWidgetCallback", "✗ 緊湊小工具更新失敗: ${e.message}", e)
            }

            android.util.Log.i("RefreshWidgetCallback", "========== 刷新完成 ==========")

            // 顯示刷新結果
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                val message = if (successCount > 0) {
                    "刷新完成：成功 $successCount 支股票"
                } else {
                    "刷新失敗：無法更新股價"
                }
                android.widget.Toast.makeText(
                    context,
                    message,
                    android.widget.Toast.LENGTH_SHORT
                ).show()
            }

        } catch (e: Exception) {
            android.util.Log.e("RefreshWidgetCallback", "========== 刷新失敗: ${e.message} ==========", e)

            // 顯示錯誤訊息
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                android.widget.Toast.makeText(
                    context,
                    "刷新失敗：${e.message}",
                    android.widget.Toast.LENGTH_SHORT
                ).show()
            }
        } finally {
            isRefreshing = false
        }
    }
}

/**
 * 自動刷新 Worker
 * 用於背景靜默刷新，不顯示 Toast
 */
class AutoRefreshWorker(
    context: Context,
    params: androidx.work.WorkerParameters
) : androidx.work.CoroutineWorker(context, params) {

    override suspend fun doWork(): androidx.work.ListenableWorker.Result {
        return try {
            StockWidget.performBackgroundRefresh(applicationContext)
            androidx.work.ListenableWorker.Result.success()
        } catch (e: Exception) {
            android.util.Log.e("AutoRefreshWorker", "自動刷新失敗", e)
            androidx.work.ListenableWorker.Result.failure()
        }
    }
}

class StockWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = StockWidget()

    override fun onReceive(context: android.content.Context, intent: android.content.Intent) {
        super.onReceive(context, intent)

        // 當 app 更新後，強制重新載入所有 widget
        if (android.content.Intent.ACTION_MY_PACKAGE_REPLACED == intent.action) {
            android.util.Log.i("StockWidgetReceiver", "App 更新完成，強制更新所有 widget")

            // 獲取所有屬於此 Provider 的 widget IDs
            val appWidgetManager = android.appwidget.AppWidgetManager.getInstance(context)
            val componentName = android.content.ComponentName(context, StockWidgetReceiver::class.java)
            val appWidgetIds = appWidgetManager.getAppWidgetIds(componentName)

            android.util.Log.i("StockWidgetReceiver", "找到 ${appWidgetIds.size} 個 widget 需要更新")

            // 手動觸發更新（呼叫 onUpdate）
            onUpdate(context, appWidgetManager, appWidgetIds)
        }
    }
}

/**
 * Widget 預覽 - 標準尺寸，獲利狀態
 */
@OptIn(ExperimentalGlancePreviewApi::class)
@Preview(widthDp = 180, heightDp = 120)
@Composable
fun StockWidgetPreview() {
    WidgetContent(
        dailyProfitLoss = 1250.0,
        totalProfitLoss = 15320.0,
        totalAssets = 125000.0,
        lastRefreshTime = System.currentTimeMillis() - 300000, // 5 分鐘前
        isCompact = false
    )
}

/**
 * Widget 預覽 - 大尺寸，虧損狀態
 */
@OptIn(ExperimentalGlancePreviewApi::class)
@Preview(widthDp = 250, heightDp = 150)
@Composable
fun StockWidgetLargePreview() {
    WidgetContent(
        dailyProfitLoss = -850.0,
        totalProfitLoss = -3200.0,
        totalAssets = 98000.0,
        lastRefreshTime = System.currentTimeMillis() - 3600000, // 1 小時前
        isCompact = false
    )
}
