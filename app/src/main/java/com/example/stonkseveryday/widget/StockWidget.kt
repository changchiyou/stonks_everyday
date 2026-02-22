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

        provideContent {
            WidgetContent(
                dailyProfitLoss = dailyProfitLoss,
                totalProfitLoss = summary.totalProfitLoss,
                totalAssets = summary.totalAssets,
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
 * 格式化金額，使用 K 表示千
 */
private fun formatAmount(amount: Double, useShortFormat: Boolean = false): String {
    return if (useShortFormat && Math.abs(amount) >= 1000) {
        val kAmount = amount / 1000
        String.format("%.1fK", kAmount)
    } else {
        val currencyFormat = NumberFormat.getCurrencyInstance(java.util.Locale.TAIWAN)
        currencyFormat.format(amount)
    }
}

@Composable
fun WidgetContent(
        dailyProfitLoss: Double,
        totalProfitLoss: Double,
        totalAssets: Double,
        isCompact: Boolean = false
    ) {
        val currencyFormat = NumberFormat.getCurrencyInstance(java.util.Locale.TAIWAN)

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
                // Title with refresh action
                Row(
                    modifier = GlanceModifier
                        .fillMaxWidth()
                        .clickable(actionRunCallback<RefreshWidgetCallback>()),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isCompact) "SE" else "Stonks Everyday",
                        style = TextStyle(
                            fontSize = if (isCompact) 11.sp else 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = ColorProvider(day = Color.Black, night = Color.White)
                        )
                    )
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
                                ColorProvider(day = Color(0xFF2E7D32), night = Color(0xFF4CAF50))
                            } else {
                                ColorProvider(day = Color(0xFFC62828), night = Color(0xFFEF5350))
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
                                    ColorProvider(day = Color(0xFF2E7D32), night = Color(0xFF4CAF50))
                                } else {
                                    ColorProvider(day = Color(0xFFC62828), night = Color(0xFFEF5350))
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
 */
class RefreshWidgetCallback : androidx.glance.appwidget.action.ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: androidx.glance.action.ActionParameters
    ) {
        // 觸發 Widget 更新
        StockWidget().update(context, glanceId)
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
        isCompact = false
    )
}
