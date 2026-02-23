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
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class StockWidget : GlanceAppWidget() {

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

        // Get all transactions for total summary
        val allTransactions = repository.allTransactions.first()
        val summary = calculateQuickSummary(allTransactions)

        // 儲存最後刷新時間
        val prefs = context.getSharedPreferences("widget_prefs", Context.MODE_PRIVATE)
        prefs.edit().putLong("last_refresh_time", System.currentTimeMillis()).apply()

        provideContent {
            WidgetContent(
                dailyProfitLoss = dailyProfitLoss,
                totalProfitLoss = summary.totalProfitLoss,
                totalAssets = summary.totalAssets,
                lastRefreshTime = System.currentTimeMillis(),
                isCompact = false
            )
        }
    }

    private fun calculateQuickSummary(transactions: List<com.example.stonkseveryday.data.model.StockTransaction>): StockSummary {
        val holdings = mutableMapOf<String, Pair<Int, Double>>()

        transactions.forEach { tx ->
            val (quantity, cost) = holdings.getOrDefault(tx.stockCode, Pair(0, 0.0))
            when (tx.transactionType) {
                TransactionType.BUY -> {
                    holdings[tx.stockCode] = Pair(
                        quantity + tx.quantity,
                        cost + tx.totalAmount
                    )
                }
                TransactionType.SELL -> {
                    holdings[tx.stockCode] = Pair(
                        quantity - tx.quantity,
                        cost - tx.totalAmount
                    )
                }
            }
        }

        val totalCost = holdings.values.sumOf { (_, cost) -> cost }
        val totalProfitLoss = holdings.values.sumOf { (quantity, cost) ->
            if (quantity > 0) {
                val averageCost = cost / quantity
                val currentPrice = averageCost * 1.05
                (currentPrice - averageCost) * quantity
            } else 0.0
        }
        val totalAssets = totalCost + totalProfitLoss

        return StockSummary(
            totalAssets = totalAssets,
            netAssets = totalAssets,
            todayProfitLoss = 0.0,
            todayProfitLossPercent = 0.0,
            totalProfitLoss = totalProfitLoss,
            totalProfitLossPercent = if (totalCost > 0) (totalProfitLoss / totalCost) * 100 else 0.0,
            holdings = emptyList()
        )
    }
}

/**
 * 格式化金額
 * - 緊湊模式：>= 1000 使用 K/M 格式
 * - 一般模式：>= 1,000,000 使用 M 格式，>= 10,000 使用 K 格式
 * - 移除不必要的 .00 後綴
 */
private fun formatAmount(amount: Double, useShortFormat: Boolean = false): String {
    val absAmount = Math.abs(amount)
    val sign = if (amount < 0) "-" else ""

    return if (useShortFormat) {
        // 緊湊模式：適用於小 widget
        when {
            absAmount >= 1_000_000 -> String.format("%s%.1fM", sign, absAmount / 1_000_000)
            absAmount >= 1_000 -> String.format("%s%.1fK", sign, absAmount / 1_000)
            else -> String.format("%s%.0f", sign, absAmount)
        }
    } else {
        // 一般模式：適用於主畫面和大 widget
        when {
            absAmount >= 10_000_000 -> String.format("%s%.2fM", sign, absAmount / 1_000_000)
            absAmount >= 1_000_000 -> String.format("%s%.1fM", sign, absAmount / 1_000_000)
            absAmount >= 100_000 -> String.format("%s%.1fK", sign, absAmount / 1_000)
            absAmount >= 1_000 -> String.format("%s%.0fK", sign, absAmount / 1_000)
            else -> String.format("%s%.0f", sign, absAmount)
        }
    }
}

/**
 * 格式化刷新時間為相對時間（精確到分鐘）
 */
private fun formatRefreshTime(timestamp: Long): String {
    val now = System.currentTimeMillis()
    val diffMinutes = ((now - timestamp) / 60000).toInt()

    return when {
        diffMinutes == 0 -> "剛剛"
        diffMinutes < 60 -> "${diffMinutes}分鐘前"
        else -> {
            val hours = diffMinutes / 60
            val minutes = diffMinutes % 60
            if (minutes == 0) "${hours}小時前"
            else "${hours}小時${minutes}分鐘前"
        }
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
        val currencyFormat = NumberFormat.getCurrencyInstance(java.util.Locale.TAIWAN)
        val refreshTimeText = formatRefreshTime(lastRefreshTime)

        Box(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(ColorProvider(day = Color(0xFFF5F5F5), night = Color(0xFF1E1E1E)))
                .padding(if (isCompact) 8.dp else 16.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = GlanceModifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Title with refresh action and refresh time
                Row(
                    modifier = GlanceModifier
                        .fillMaxWidth()
                        .clickable(actionRunCallback<RefreshWidgetCallback>()),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isCompact) {
                        // 精簡版：只顯示刷新時間
                        Text(
                            text = refreshTimeText,
                            style = TextStyle(
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Normal,
                                color = ColorProvider(day = Color.Gray, night = Color.LightGray)
                            )
                        )
                    } else {
                        // 一般版：標題 + 刷新時間
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
                    modifier = GlanceModifier.clickable(actionStartActivity<MainActivity>()),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (!isCompact) {
                        Text(
                            text = "當日損益",
                            style = TextStyle(
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Normal,
                                color = ColorProvider(day = Color.Gray, night = Color.LightGray)
                            )
                        )
                        Spacer(modifier = GlanceModifier.height(4.dp))
                    }

                    Text(
                        text = if (isCompact) formatAmount(dailyProfitLoss, true) else currencyFormat.format(dailyProfitLoss),
                        style = TextStyle(
                            fontSize = if (isCompact) 20.sp else 24.sp,
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

                if (!isCompact) {
                    Spacer(modifier = GlanceModifier.height(12.dp))

                    // Divider
                    Box(
                        modifier = GlanceModifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(ColorProvider(day = Color.LightGray, night = Color.DarkGray)),
                        content = {}
                    )

                    Spacer(modifier = GlanceModifier.height(12.dp))
                }

                // Additional Info (only in large mode)
                if (!isCompact) {
                    Row(
                    modifier = GlanceModifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = GlanceModifier.defaultWeight(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "總資產",
                            style = TextStyle(
                                fontSize = 10.sp,
                                color = ColorProvider(day = Color.Gray, night = Color.LightGray)
                            )
                        )
                        Spacer(modifier = GlanceModifier.height(2.dp))
                        Text(
                            text = currencyFormat.format(totalAssets),
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
                            text = "總損益",
                            style = TextStyle(
                                fontSize = 10.sp,
                                color = ColorProvider(day = Color.Gray, night = Color.LightGray)
                            )
                        )
                        Spacer(modifier = GlanceModifier.height(2.dp))
                        Text(
                            text = currencyFormat.format(totalProfitLoss),
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
 * 使用 GlanceAppWidgetManager 來更新正確的 widget
 */
class RefreshWidgetCallback : androidx.glance.appwidget.action.ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: androidx.glance.action.ActionParameters
    ) {
        // 取得 widget 的實際類型並更新
        val manager = androidx.glance.appwidget.GlanceAppWidgetManager(context)
        val appWidgetId = manager.getAppWidgetId(glanceId)

        // 檢查這個 glanceId 屬於哪個 widget
        val compactIds = manager.getGlanceIds(CompactStockWidget::class.java)
        val standardIds = manager.getGlanceIds(StockWidget::class.java)

        when (glanceId) {
            in compactIds -> CompactStockWidget().update(context, glanceId)
            in standardIds -> StockWidget().update(context, glanceId)
            else -> StockWidget().update(context, glanceId) // 預設
        }
    }
}

class StockWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = StockWidget()
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
