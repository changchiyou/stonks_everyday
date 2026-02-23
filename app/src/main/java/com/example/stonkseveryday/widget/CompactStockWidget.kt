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

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val dao = StockDatabase.getDatabase(context).stockTransactionDao()
        val repository = StockRepository(dao, context)

        // Get today's transactions
        val todayTransactions = repository.todayTransactions.first()

        // Calculate daily profit/loss
        val dailyProfitLoss = todayTransactions.sumOf { tx ->
            when (tx.transactionType) {
                TransactionType.SELL -> tx.totalAmount
                TransactionType.BUY -> -tx.totalAmount
            }
        }

        // 讀取或儲存最後刷新時間（與一般版本共用）
        val prefs = context.getSharedPreferences("widget_prefs", android.content.Context.MODE_PRIVATE)
        val lastRefreshTime = prefs.getLong("last_refresh_time", System.currentTimeMillis())
        prefs.edit().putLong("last_refresh_time", System.currentTimeMillis()).apply()

        provideContent {
            // 使用 WidgetContent，但傳入 isCompact = true
            WidgetContent(
                dailyProfitLoss = dailyProfitLoss,
                totalProfitLoss = 0.0,  // 不顯示
                totalAssets = 0.0,       // 不顯示
                lastRefreshTime = lastRefreshTime,
                isCompact = true
            )
        }
    }
}

class CompactStockWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = CompactStockWidget()
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
